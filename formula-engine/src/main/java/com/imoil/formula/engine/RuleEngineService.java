package com.imoil.formula.engine;

import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.imoil.formula.domain.SensorData;
import com.imoil.formula.domain.SensorSerialResolver;
import com.imoil.formula.engine.functions.AbstractWindowFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RuleEngineService {

    public static final int DEFAULT_MAX_WINDOW_SIZE = 100;

    private final AviatorEvaluatorInstance evaluator;
    private final Set<String> validatedExpressions = ConcurrentHashMap.newKeySet();

    /**
     * 기본 생성자: Spring 컨텍스트 없이도 보안 샌드박스 및 윈도우 함수가 내장된 엔진을 생성합니다.
     */
    public RuleEngineService() {
        this(RuleEngineConfig.createDefaultEvaluator());
    }

    @Autowired
    public RuleEngineService(AviatorEvaluatorInstance evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * 수식이 윈도우 집계 함수(window_, previous_)를 사용하는지 판별합니다.
     */
    public static boolean usesWindow(String expressionText) {
        return expressionText != null
                && (expressionText.contains("window_") || expressionText.contains("previous_"));
    }

    /**
     * 수식을 1회 보안 검증하고 캐시에 등록합니다.
     */
    public void validateOnce(String expressionText) {
        if (expressionText == null || !validatedExpressions.contains(expressionText)) {
            ExpressionValidator.validate(expressionText);
            validatedExpressions.add(expressionText);
        }
    }

    /**
     * 런타임 수식과 컨텍스트(Map)를 기반으로 결과를 도출합니다.
     * 표현식은 최초 1회 검증 후 내부적으로 컴파일되어 캐싱됩니다. (GC 및 정규식 오버헤드 최소화)
     */
    public double evaluate(String expressionText, Map<String, Object> env) {
        validateOnce(expressionText);
        Expression expression = evaluator.compile(expressionText, true); // true = Use Cache
        Object result = expression.execute(env);

        if (result instanceof Number) {
            return ((Number) result).doubleValue();
        }

        throw new IllegalArgumentException("The evaluation result could not be cast to a numeric value.");
    }

    /**
     * 도메인 SensorData 단건과 슬라이딩 윈도우 히스토리를 받아 수식을 평가합니다.
     * value, state, 윈도우 히스토리 및 단일 일련번호 표현("10001", "P_10001", 하위 호환 "SN_10001")을 자동으로 바인딩합니다.
     */
    public double evaluatePoint(String expressionText, SensorData data, List<Double> windowHistory) {
        Map<String, Object> env = new HashMap<>(8);
        populateEnv(env, data, windowHistory);
        return evaluate(expressionText, env);
    }

    /**
     * 시계열 데이터 리스트 전체에 대해 슬라이딩 윈도우(최대 100개 캡) 및 환경 맵 재사용을 적용하여
     * 파생 시계열 데이터를 일괄 산출합니다.
     */
    public List<SensorData> evaluateSeries(String expressionText, List<SensorData> rawSeries, String derivedSensorId) {
        validateOnce(expressionText);
        if (rawSeries == null || rawSeries.isEmpty()) {
            return List.of();
        }

        List<SensorData> resultData = new ArrayList<>(rawSeries.size());
        LinkedList<Double> windowHistory = new LinkedList<>();
        Map<String, Object> env = new HashMap<>(8);
        AbstractWindowFunction.bindWindowHistory(env, windowHistory);

        for (SensorData data : rawSeries) {
            windowHistory.add(data.getValue());
            if (windowHistory.size() > DEFAULT_MAX_WINDOW_SIZE) {
                windowHistory.removeFirst();
            }

            populateEnv(env, data, windowHistory);

            try {
                double computed = evaluate(expressionText, env);
                String outId = (derivedSensorId != null && !derivedSensorId.isBlank())
                        ? derivedSensorId
                        : data.getSensorId() + "_ondemand_eval";
                resultData.add(SensorData.builder()
                        .sensorId(outId)
                        .timestamp(data.getTimestamp())
                        .value(computed)
                        .state(data.getState())
                        .hasInaccurateData(data.isHasInaccurateData())
                        .build());
            } catch (Exception ignored) {
                // 개별 데이터 포인트 평가 실패 시 해당 포인트 건너뜀
            }
        }

        return resultData;
    }

    /**
     * SensorData로부터 평가 컨텍스트 맵(value, state, 윈도우 히스토리, 10001, P_10001, SN_10001)을 채웁니다.
     */
    public static void populateEnv(Map<String, Object> env, SensorData data, List<Double> windowHistory) {
        if (env == null || data == null) {
            return;
        }
        env.put("value", data.getValue());
        env.put("state", data.getState());
        AbstractWindowFunction.bindWindowHistory(env, windowHistory);
        SensorSerialResolver.bindSerialIdentifiers(env, data.getSensorId(), data.getValue());
    }
}

