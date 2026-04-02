package com.imoil.formula.service;

import com.imoil.formula.domain.SensorData;
import com.imoil.formula.engine.RuleEngineService;
import com.imoil.formula.repository.QuestDBQueryRepository;
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
    
    private final QuestDBQueryRepository queryRepository;
    private final RuleEngineService ruleEngineService;

    /**
     * 온디맨드 듀얼-패스 설계 : 데이터베이스 단에서 1차 집계를 통해 데이터의 페이로드를 줄이고, 
     * Spring 메모리 영역으로 적재 후 AviatorScript 엔진으로 로컬 처리합니다.
     */
    public List<SensorData> evaluateOverTimeRange(String sensorId, String expression, String startTime, String endTime, String sampleBy) {
        
        log.info("Fetching on-demand data for sensor={}, range=[{}, {}], sampleBy={}", sensorId, startTime, endTime, sampleBy);
        List<SensorData> rawData = queryRepository.fetchAggregatedData(sensorId, startTime, endTime, sampleBy);
        
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
                        .build());
            } catch (Exception e) {
                log.warn("On-demand evaluation failed for sensor={}, rule={}. Skipping data point.", sensorId, expression);
            }
        }
        
        return resultData;
    }
}
