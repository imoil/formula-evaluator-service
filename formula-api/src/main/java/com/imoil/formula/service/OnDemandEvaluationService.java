package com.imoil.formula.service;

import com.imoil.formula.datasource.TimeSeriesDataSourceAdapter;
import com.imoil.formula.datasource.TimeSeriesQueryCriteria;
import com.imoil.formula.domain.SensorData;
import com.imoil.formula.engine.RuleEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnDemandEvaluationService {
    
    // 환경설정(application.yml)에 따라 DI 컨테이너에서 단일 주입되는 시계열 데이터 소스 어댑터
    private final TimeSeriesDataSourceAdapter timeSeriesDataSourceAdapter;
    private final RuleEngineService ruleEngineService;

    /**
     * 클라이언트 요청에 따라 시계열 데이터를 조회하고 동적 수식을 적용합니다.
     * 데이터가 QuestDB, Oracle, NATS 중 어디서 오는지 서비스 계층과 클라이언트는 알 필요가 없습니다.
     */
    public List<SensorData> evaluateOverTimeRange(String sensorId, String expression, 
                                                  String startTime, String endTime, String sampleBy) {
        
        TimeSeriesQueryCriteria criteria = TimeSeriesQueryCriteria.builder()
                .sensorId(sensorId)
                .startTime(startTime)
                .endTime(endTime)
                .sampleBy(sampleBy)
                .build();

        log.info("Fetching on-demand data via adapter [{}] for sensor={}, range=[{}, {}], sampleBy={}", 
                timeSeriesDataSourceAdapter.getDataSourceType(), sensorId, startTime, endTime, sampleBy);

        List<SensorData> rawData = timeSeriesDataSourceAdapter.fetchTimeSeriesData(criteria);
        
        List<SensorData> resultData = new ArrayList<>();
        // OutOfMemoryError 방지를 위해 윈도우 계산 이력은 최대 100개까지만 보관하도록 캡(Cap) 적용
        List<Double> windowHistory = new ArrayList<>();
        final int MAX_WINDOW_SIZE = 100;
        
        for (SensorData data : rawData) {
            windowHistory.add(data.getValue());
            if (windowHistory.size() > MAX_WINDOW_SIZE) {
                windowHistory.remove(0); // 가장 오래된 데이터 제거 (Sliding Window 역할)
            }
            
            Map<String, Object> env = new HashMap<>();
            env.put("value", data.getValue());
            env.put("state", data.getState());
            // 히스토리 배열을 넘김으로써 `window_avg(n)` 커스텀 윈도우 함수가 런타임에 처리되도록 함
            env.put("window_data", new ArrayList<>(windowHistory)); 
            
            try {
                // Rule 엔진 평가
                double computed = ruleEngineService.evaluate(expression, env);
                resultData.add(SensorData.builder()
                        .sensorId(sensorId + "_ondemand_eval")
                        .timestamp(data.getTimestamp())
                        .value(computed)
                        .state(data.getState())
                        .hasInaccurateData(data.isHasInaccurateData())
                        .build());
            } catch (Exception e) {
                log.warn("On-demand evaluation failed for sensor={}, rule={}. Skipping data point.", sensorId, expression);
            }
        }
        
        return resultData;
    }
}
