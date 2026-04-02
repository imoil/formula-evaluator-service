package com.imoil.formula.flink;

import com.imoil.formula.domain.DynamicRule;
import com.imoil.formula.domain.SensorData;
import com.imoil.formula.engine.RuleEngineConfig;
import com.imoil.formula.engine.RuleEngineService;
import com.imoil.formula.engine.functions.WindowAvgFunction;
import com.imoil.formula.engine.functions.WindowMaxFunction;
import org.apache.flink.api.common.state.BroadcastState;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Histogram;
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.util.Collector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 센서 이벤트 스트림과 동적 룰 브로드캐스트 스트림을 결합하여 
 * AviatorScript 엔진으로 런타임 평가를 수행합니다.
 */
public class RuleBroadcastProcessFunction extends KeyedBroadcastProcessFunction<String, SensorData, DynamicRule, SensorData> {
    
    private final MapStateDescriptor<String, DynamicRule> ruleStateDescriptor = 
        new MapStateDescriptor<>("DynamicRules", String.class, DynamicRule.class);
    
    // Key-value List State (RocksDB Backend 에 안전하게 저장됨)
    private transient ListState<Double> windowHistoryState;
    private transient ValueState<Long> ruleStartTimeState;
    private transient RuleEngineService ruleEngineService;
    
    // 옵저버빌리티(Observability) 및 성능 튜닝을 위한 상태 모니터링 메트릭스
    private transient Counter ruleHitCounter;
    private transient Counter ruleMissCounter;

    @Override
    public void open(Configuration parameters) {
        ListStateDescriptor<Double> stateDescriptor = new ListStateDescriptor<>("windowHistory", Double.class);
        
        // 메모리 폭발 누수(OOM) 방지 및 GC 최적화를 위한 RocksDB State TTL(수명) 부과
        org.apache.flink.api.common.state.StateTtlConfig ttlConfig = org.apache.flink.api.common.state.StateTtlConfig
                .newBuilder(java.time.Duration.ofMinutes(30))
                .setUpdateType(org.apache.flink.api.common.state.StateTtlConfig.UpdateType.OnCreateAndWrite)
                .setStateVisibility(org.apache.flink.api.common.state.StateTtlConfig.StateVisibility.NeverReturnExpired)
                .build();
                
        stateDescriptor.enableTimeToLive(ttlConfig);
        windowHistoryState = getRuntimeContext().getListState(stateDescriptor);
        
        ValueStateDescriptor<Long> ruleStartTimeDescriptor = new ValueStateDescriptor<>("ruleStartTime", Long.class);
        ruleStartTimeDescriptor.enableTimeToLive(ttlConfig);
        ruleStartTimeState = getRuntimeContext().getState(ruleStartTimeDescriptor);

        // Flink 클러스터 분산 워커에서 스프링 컨텍스트 없이 자체 생성하여 엔진 캐시 보장
        RuleEngineConfig config = new RuleEngineConfig();
        var evaluator = config.aviatorEvaluatorInstance(new WindowAvgFunction(), new WindowMaxFunction());
        ruleEngineService = new RuleEngineService(evaluator);
        
        // 메트릭 등록
        var metricGroup = getRuntimeContext().getMetricGroup();
        ruleHitCounter = metricGroup.counter("ruleEvaluationHits");
        ruleMissCounter = metricGroup.counter("ruleEvaluationMisses");
    }

    @Override
    public void processElement(SensorData value, ReadOnlyContext ctx, Collector<SensorData> out) throws Exception {
        // 1. 적용할 룰 조회
        DynamicRule rule = ctx.getBroadcastState(ruleStateDescriptor).get(value.getSensorId());
        
        boolean usesWindow = rule != null && rule.getExpression() != null && rule.getExpression().contains("window_");
        
        List<Double> history = new ArrayList<>();
        boolean hasInaccurateData = false;

        if (usesWindow) {
            // 윈도우 함수를 사용하는 경우에만 히스토리 유지
            windowHistoryState.add(value.getValue());
            windowHistoryState.get().forEach(history::add);

            Long ruleStartTime = ruleStartTimeState.value();
            if (ruleStartTime == null) {
                // 룰이 처음 적용되거나, state가 만료/초기화된 경우 현재 시간을 시작 시간으로 설정
                ruleStartTimeState.update(System.currentTimeMillis());
                hasInaccurateData = true;
            } else {
                // 룰 변경 시점으로부터 30분이 지나지 않았다면 히스토리 데이터가 불완전하다고 간주
                if (System.currentTimeMillis() - ruleStartTime < 30 * 60 * 1000) {
                    hasInaccurateData = true;
                }
            }
        } else {
            // 윈도우 함수를 사용하지 않는 경우 메모리 절약을 위해 히스토리 초기화 (유지할 필요 없음)
            windowHistoryState.clear();
            ruleStartTimeState.clear();
        }
        
        if (rule != null && rule.getExpression() != null) {
            Map<String, Object> env = new HashMap<>();
            env.put("value", value.getValue());
            env.put("state", value.getState());
            env.put("window_data", history); // 윈도우 커스텀 함수에 제공될 데이터
            
            try {
                double evaluatedResult = ruleEngineService.evaluate(rule.getExpression(), env);
                // 연산된 결과를 새롭게 내보냄
                SensorData resultData = SensorData.builder()
                        .sensorId(value.getSensorId() + "_derived")
                        .timestamp(value.getTimestamp())
                        .value(evaluatedResult)
                        .state(value.getState()) // 유지
                        .hasInaccurateData(hasInaccurateData) // 부정확 플래그
                        .build();
                out.collect(resultData);
                ruleHitCounter.inc(); // 평가 성공 지표 측정
            } catch (Exception e) {
                // 파싱 오류이거나 연산 오류일 경우 무시 (필요시 dead-letter topic 처리)
            }
        } else {
            ruleMissCounter.inc(); // 룰이 할당되지 않은 센서 빈도 지표
        }
        
        // 원본 센서 데이터도 최종 Sink로 넘기기 위해 수집
        out.collect(value);
    }

    @Override
    public void processBroadcastElement(DynamicRule rule, Context ctx, Collector<SensorData> out) throws Exception {
        BroadcastState<String, DynamicRule> broadcastState = ctx.getBroadcastState(ruleStateDescriptor);
        broadcastState.put(rule.getTargetSensorId(), rule);
    }
}
