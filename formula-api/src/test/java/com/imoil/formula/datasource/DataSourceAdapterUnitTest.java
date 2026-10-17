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

    @Test
    @DisplayName("pushStreamData는 null 객체나 sensorId가 null인 데이터를 무시한다")
    void testPushStreamDataWithNulls() {
        StreamingBufferDataSourceAdapter adapter = new StreamingBufferDataSourceAdapter();

        adapter.pushStreamData(null);
        adapter.pushStreamData(SensorData.builder().sensorId(null).timestamp(100L).value(10.0).build());

        TimeSeriesQueryCriteria criteria = TimeSeriesQueryCriteria.builder()
                .sensorId("sensor_01")
                .limit(10)
                .build();

        List<SensorData> result = adapter.fetchTimeSeriesData(criteria);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("pushStreamData는 MAX_BUFFER_CAPACITY(2000) 초과 시 가장 오래된 데이터를 버린다")
    void testPushStreamDataMaxBufferCapacity() {
        StreamingBufferDataSourceAdapter adapter = new StreamingBufferDataSourceAdapter();
        String sensorId = "sensor_capacity";

        // MAX_BUFFER_CAPACITY (2000) + 10 개의 데이터 푸시 (총 2010개)
        for (int i = 1; i <= 2010; i++) {
            adapter.pushStreamData(SensorData.builder()
                    .sensorId(sensorId)
                    .timestamp((long) i)
                    .value((double) i)
                    .build());
        }

        TimeSeriesQueryCriteria criteria = TimeSeriesQueryCriteria.builder()
                .sensorId(sensorId)
                .limit(3000)
                .build();

        List<SensorData> result = adapter.fetchTimeSeriesData(criteria);

        // 버퍼 크기는 MAX_BUFFER_CAPACITY (2000) 이어야 함
        assertThat(result).hasSize(2000);
        // 가장 오래된 10개(1~10)가 버려지고, 첫 요소는 11번째 데이터(timestamp=11)여야 함
        assertThat(result.get(0).getTimestamp()).isEqualTo(11L);
        assertThat(result.get(0).getValue()).isEqualTo(11.0);
        // 마지막 요소는 2010번째 데이터(timestamp=2010)여야 함
        assertThat(result.get(result.size() - 1).getTimestamp()).isEqualTo(2010L);
    }
}
