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

    @org.mockito.Spy
    private RuleEngineService ruleEngineService = new RuleEngineService();

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

        // when: 서비스 평가 메서드 실행 (RuleEngineService.evaluateSeries 위임)
        List<SensorData> results = evaluationService.evaluateOverTimeRange(
                "s1", "value * 2", "2026-04-01T00:00:00", "2026-04-01T01:00:00", "1m"
        );

        // then: 연산 결과 검증
        verify(ruleEngineService, times(1)).evaluateSeries(eq("value * 2"), any(), eq("s1_ondemand_eval"));
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getSensorId()).isEqualTo("s1_ondemand_eval");
        assertThat(results.get(0).getValue()).isEqualTo(20.0);
        assertThat(results.get(1).getValue()).isEqualTo(40.0);
        assertThat(results.get(2).getValue()).isEqualTo(60.0);
        assertThat(results.get(2).getState()).isEqualTo(1);
    }

    @Test
    @DisplayName("윈도우 히스토리가 누적되어 수식 엔진 내부 슬라이딩 윈도우로 올바르게 평가된다")
    void testSlidingWindowContextPassedToRuleEngine() {
        SensorData d1 = SensorData.builder().sensorId("s1").timestamp(1000L).value(5.0).build();
        SensorData d2 = SensorData.builder().sensorId("s1").timestamp(2000L).value(15.0).build();

        given(timeSeriesDataSourceAdapter.getDataSourceType()).willReturn(DataSourceType.RDBMS);
        given(timeSeriesDataSourceAdapter.fetchTimeSeriesData(any(TimeSeriesQueryCriteria.class)))
                .willReturn(List.of(d1, d2));

        List<SensorData> results = evaluationService.evaluateOverTimeRange("s1", "window_avg(2)", "start", "end", "1m");

        verify(ruleEngineService, times(2)).evaluate(eq("window_avg(2)"), any());
        assertThat(results).hasSize(2);
        // 1번째 포인트: window=[5.0] -> avg=5.0
        assertThat(results.get(0).getValue()).isEqualTo(5.0);
        // 2번째 포인트: window=[5.0, 15.0] -> avg=10.0
        assertThat(results.get(1).getValue()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("실제 RuleEngineService와 결합 시 P_10001 및 레거시 SN_10001 일련번호 참조 수식이 온디맨드 경로에서 정상 평가된다")
    void testEvaluateSerialPrefixedFormulaWithRealEngine() {
        OnDemandEvaluationService realEvaluationService =
                new OnDemandEvaluationService(timeSeriesDataSourceAdapter, new RuleEngineService());

        SensorData d1 = SensorData.builder().sensorId("10001").timestamp(1000L).value(20.0).state(0).build();
        SensorData d2 = SensorData.builder().sensorId("10001").timestamp(2000L).value(30.0).state(0).build();

        given(timeSeriesDataSourceAdapter.getDataSourceType()).willReturn(DataSourceType.QUESTDB);
        given(timeSeriesDataSourceAdapter.fetchTimeSeriesData(any(TimeSeriesQueryCriteria.class)))
                .willReturn(List.of(d1, d2));

        List<SensorData> results = realEvaluationService.evaluateOverTimeRange(
                "10001", "P_10001 * 1.5 + window_avg(2)", "start", "end", "1s"
        );

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getValue()).isEqualTo(50.0); // 20 * 1.5 + 20
        assertThat(results.get(1).getValue()).isEqualTo(70.0); // 30 * 1.5 + 25

        // 하위 호환성: SN_10001 레거시 수식도 정상 평가
        List<SensorData> legacyResults = realEvaluationService.evaluateOverTimeRange(
                "10001", "SN_10001 * 1.5 + window_avg(2)", "start", "end", "1s"
        );
        assertThat(legacyResults).hasSize(2);
        assertThat(legacyResults.get(0).getValue()).isEqualTo(50.0);
        assertThat(legacyResults.get(1).getValue()).isEqualTo(70.0);
    }
}
