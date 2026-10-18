package com.imoil.formula.service;

import com.imoil.formula.datasource.TimeSeriesQueryCriteria;
import com.imoil.formula.domain.SensorData;
import com.imoil.formula.registry.entity.SensorRegistryEntity;
import com.imoil.formula.registry.domain.SensorType;
import com.imoil.formula.registry.repository.SensorRegistryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockSensorCatalogServiceTest {

    @Mock
    private SensorRegistryRepository sensorRegistryRepository;

    @InjectMocks
    private MockSensorCatalogService catalogService;

    @Test
    @DisplayName("Oracle 19c SENSOR_REGISTRY의 물리 센서 조회 시 deviceId와 deviceSensorIndex가 SensorData에 올바르게 주입된다")
    void testGenerateTimeSeriesWithRegistrySensor() {
        SensorRegistryEntity entity = SensorRegistryEntity.builder()
                .serialNumber(10001L)
                .sensorCode("TEMP_IN")
                .sensorName("Inlet Temperature")
                .category("Temperature")
                .unit("°C")
                .deviceId("DEVICE-001")
                .deviceSensorIndex(3)
                .sensorType(SensorType.PHYSICAL)
                .build();

        when(sensorRegistryRepository.findBySerialNumber(10001L)).thenReturn(Optional.of(entity));

        TimeSeriesQueryCriteria criteria = TimeSeriesQueryCriteria.builder()
                .sensorId("10001")
                .limit(10)
                .build();

        List<SensorData> series = catalogService.generateTimeSeries(criteria);
        assertThat(series).hasSize(10);
        SensorData first = series.get(0);
        assertThat(first.getSensorId()).isEqualTo("10001");
        assertThat(first.getDeviceId()).isEqualTo("DEVICE-001");
        assertThat(first.getSensorIndex()).isEqualTo(3);

        SensorData latest = catalogService.generateLatestPoint("10001");
        assertThat(latest.getSensorId()).isEqualTo("10001");
        assertThat(latest.getDeviceId()).isEqualTo("DEVICE-001");
        assertThat(latest.getSensorIndex()).isEqualTo(3);
    }
}
