package com.imoil.formula.datasource;

import com.imoil.formula.domain.SensorData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 단계별 Unit Test 3: 어댑터 데이터 공급 계층 검증
 */
class DataSourceAdapterUnitTest {

    @Test
    @DisplayName("StreamingBufferDataSourceAdapter는 스트림으로 유입된 데이터를 보관하고 기준 조건에 맞게 공급한다")
    void testStreamingBufferAdapter() {
        StreamingBufferDataSourceAdapter adapter = new StreamingBufferDataSourceAdapter();
        assertThat(adapter.getDataSourceType()).isEqualTo(DataSourceType.STREAMING);

        // 스트림 유입 시뮬레이션
        adapter.pushStreamData(SensorData.builder().sensorId("sensor_01").timestamp(100L).value(10.0).build());
        adapter.pushStreamData(SensorData.builder().sensorId("sensor_01").timestamp(200L).value(20.0).build());
        adapter.pushStreamData(SensorData.builder().sensorId("sensor_02").timestamp(300L).value(99.0).build());

        // sensor_01에 대해 조회
        TimeSeriesQueryCriteria criteria = TimeSeriesQueryCriteria.builder()
                .sensorId("sensor_01")
                .limit(10)
                .build();

        List<SensorData> result = adapter.fetchTimeSeriesData(criteria);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getValue()).isEqualTo(10.0);
        assertThat(result.get(1).getValue()).isEqualTo(20.0);
    }
}
