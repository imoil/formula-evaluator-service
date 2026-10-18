package com.imoil.formula.service;

import com.imoil.formula.datasource.TimeSeriesDataSourceAdapter;
import com.imoil.formula.datasource.TimeSeriesQueryCriteria;
import com.imoil.formula.domain.SensorData;
import com.imoil.formula.engine.RuleEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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

        ruleEngineService.validateOnce(expression);

        TimeSeriesQueryCriteria criteria = TimeSeriesQueryCriteria.builder()
                .sensorId(sensorId)
                .startTime(startTime)
                .endTime(endTime)
                .sampleBy(sampleBy)
                .build();

        log.info("Fetching on-demand data via adapter [{}] for sensor={}, range=[{}, {}], sampleBy={}",
                timeSeriesDataSourceAdapter.getDataSourceType(), sensorId, startTime, endTime, sampleBy);

        List<SensorData> rawData = timeSeriesDataSourceAdapter.fetchTimeSeriesData(criteria);
        return ruleEngineService.evaluateSeries(expression, rawData, sensorId + "_ondemand_eval");
    }
}
