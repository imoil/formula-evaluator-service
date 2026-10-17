package com.imoil.formula.service;

import com.imoil.formula.datasource.DataSourceType;
import com.imoil.formula.datasource.TimeSeriesDataSourceAdapter;
import com.imoil.formula.datasource.TimeSeriesQueryCriteria;
import com.imoil.formula.domain.SensorData;
import com.imoil.formula.engine.RuleEngineService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OnDemandEvaluationServiceBenchmark {

    @Test
    public void benchmarkEvaluateOverTimeRange() {
        int recordCount = 100_000;
        List<SensorData> rawData = new ArrayList<>(recordCount);
        for (int i = 0; i < recordCount; i++) {
            rawData.add(SensorData.builder()
                    .sensorId("s1")
                    .timestamp(1000L + i * 1000L)
                    .value(10.0 + (i % 100))
                    .state(0)
                    .build());
        }

        TimeSeriesDataSourceAdapter adapter = new TimeSeriesDataSourceAdapter() {
            @Override
            public DataSourceType getDataSourceType() {
                return DataSourceType.QUESTDB;
            }

            @Override
            public List<SensorData> fetchTimeSeriesData(TimeSeriesQueryCriteria criteria) {
                return rawData;
            }
        };

        RuleEngineService ruleEngine = new RuleEngineService(null) {
            @Override
            public double evaluate(String expressionText, Map<String, Object> env) {
                // Access map values to simulate actual usage
                Object val = env.get("value");
                Object win = env.get("window_data");
                return val != null ? ((Number) val).doubleValue() * 2.0 : 0.0;
            }
        };

        OnDemandEvaluationService service = new OnDemandEvaluationService(adapter, ruleEngine);

        // Warmup
        for (int i = 0; i < 5; i++) {
            service.evaluateOverTimeRange("s1", "value * 2", "start", "end", "1m");
        }

        // Benchmark run
        int iterations = 20;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            service.evaluateOverTimeRange("s1", "value * 2", "start", "end", "1m");
        }
        long end = System.nanoTime();

        double avgMs = (end - start) / (double) iterations / 1_000_000.0;
        System.out.println("Average time for evaluateOverTimeRange (" + recordCount + " records): " + avgMs + " ms");
    }
}
