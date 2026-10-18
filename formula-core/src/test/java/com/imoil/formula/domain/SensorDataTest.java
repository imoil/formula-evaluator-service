package com.imoil.formula.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class SensorDataTest {

    @Test
    @DisplayName("SensorData의 LocalDateTime 상호 변환 및 로컬 시간 무결성 검증")
    void testLocalDateTimeConversion() {
        LocalDateTime localNow = LocalDateTime.of(2026, 10, 18, 22, 45, 0);
        ZoneId zoneId = ZoneId.systemDefault();

        SensorData data = SensorData.builder()
                .sensorId("10001")
                .value(24.5)
                .state(0)
                .build();

        data.fromLocalDateTime(localNow, zoneId);
        assertThat(data.getTimestamp()).isEqualTo(localNow.atZone(zoneId).toInstant().toEpochMilli());

        LocalDateTime retrieved = data.toLocalDateTime(zoneId);
        assertThat(retrieved).isEqualTo(localNow);

        // default system zone helper
        data.fromLocalDateTime(localNow);
        assertThat(data.toLocalDateTime()).isEqualTo(localNow);
    }

    @Test
    @DisplayName("SensorData의 deviceId 및 sensorIndex 필드 할당 및 일관성 검증")
    void testDeviceAndSensorIndexFields() {
        SensorData data = SensorData.builder()
                .sensorId("10042")
                .deviceId("DEVICE-001")
                .sensorIndex(42)
                .value(99.5)
                .state(0)
                .build();

        assertThat(data.getDeviceId()).isEqualTo("DEVICE-001");
        assertThat(data.getSensorIndex()).isEqualTo(42);
        assertThat(data.getSensorId()).isEqualTo("10042");

        assertThat(SensorDataWriter.COL_DEVICE_ID).isEqualTo("device_id");
        assertThat(SensorDataWriter.COL_SENSOR_INDEX).isEqualTo("sensor_index");
    }
}
