package com.imoil.formula.service;

import com.imoil.formula.datasource.DataSourceType;
import com.imoil.formula.datasource.TimeSeriesDataSourceAdapter;
import com.imoil.formula.datasource.TimeSeriesQueryCriteria;
import com.imoil.formula.domain.SensorData;
import com.imoil.formula.engine.RuleEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 단계별 Unit Test 2: 비즈니스 서비스 계층 (어댑터 데이터 인출, 슬라이딩 윈도우 버퍼링, 수식 엔진 연계)
 */
@ExtendWith(MockitoExtension.class)
class OnDemandEvaluationServiceTest {

    @Mock
    private TimeSeriesDataSourceAdapter timeSeriesDataSourceAdapter;

    @Mock
    private RuleEngineService ruleEngineService;

    private OnDemandEvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        evaluationService = new OnDemandEvaluationService(timeSeriesDataSourceAdapter, ruleEngineService);
    }

    @Test
    @DisplayName("데이터 소스로부터 수신한 원본 시계열 데이터에 동적 수식을 적용하여 파생 데이터 리스트를 산출한다")
    void testEvaluateWithInjectedDataSource() {
        // given: 데이터 소스 어댑터가 반환할 3개의 샘플 센서 데이터
        SensorData d1 = SensorData.builder().sensorId("s1").timestamp(1000L).value(10.0).state(0).build();
        SensorData d2 = SensorData.builder().sensorId("s1").timestamp(2000L).value(20.0).state(0).build();
        SensorData d3 = SensorData.builder().sensorId("s1").timestamp(3000L).value(30.0).state(1).build();

        given(timeSeriesDataSourceAdapter.getDataSourceType()).willReturn(DataSourceType.QUESTDB);
        given(timeSeriesDataSourceAdapter.fetchTimeSeriesData(any(TimeSeriesQueryCriteria.class)))
                .willReturn(List.of(d1, d2, d3));

        // 수식 엔진 mock: 들어온 value에 2를 곱하는 결과 모의
        given(ruleEngineService.evaluate(eq("value * 2"), any())).willAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> env = invocation.getArgument(1);
            Double val = (Double) env.get("value");
            return val * 2.0;
        });

        // when: 서비스 평가 메서드 실행
        List<SensorData> results = evaluationService.evaluateOverTimeRange(
                "s1", "value * 2", "2026-04-01T00:00:00", "2026-04-01T01:00:00", "1m"
        );

        // then: 연산 결과 검증
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getSensorId()).isEqualTo("s1_ondemand_eval");
        assertThat(results.get(0).getValue()).isEqualTo(20.0);
        assertThat(results.get(1).getValue()).isEqualTo(40.0);
        assertThat(results.get(2).getValue()).isEqualTo(60.0);
        assertThat(results.get(2).getState()).isEqualTo(1);
    }

    @Test
    @DisplayName("윈도우 히스토리가 누적되어 window_data 컨텍스트로 수식 엔진에 올바르게 전달된다")
    void testSlidingWindowContextPassedToRuleEngine() {
        SensorData d1 = SensorData.builder().sensorId("s1").timestamp(1000L).value(5.0).build();
        SensorData d2 = SensorData.builder().sensorId("s1").timestamp(2000L).value(15.0).build();

        given(timeSeriesDataSourceAdapter.getDataSourceType()).willReturn(DataSourceType.RDBMS);
        given(timeSeriesDataSourceAdapter.fetchTimeSeriesData(any(TimeSeriesQueryCriteria.class)))
                .willReturn(List.of(d1, d2));
        given(ruleEngineService.evaluate(any(), any())).willReturn(10.0);

        evaluationService.evaluateOverTimeRange("s1", "window_avg(2)", "start", "end", "1m");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> envCaptor = ArgumentCaptor.forClass(Map.class);
        verify(ruleEngineService, times(2)).evaluate(eq("window_avg(2)"), envCaptor.capture());

        List<Map<String, Object>> allEnvs = envCaptor.getAllValues();
        // 1번째 호출 시 window_data = [5.0]
        @SuppressWarnings("unchecked")
        List<Double> window1 = (List<Double>) allEnvs.get(0).get("window_data");
        assertThat(window1).containsExactly(5.0);
        // 2번째 호출 시 window_data = [5.0, 15.0]
        @SuppressWarnings("unchecked")
        List<Double> window2 = (List<Double>) allEnvs.get(1).get("window_data");
        assertThat(window2).containsExactly(5.0, 15.0);
    }
}
