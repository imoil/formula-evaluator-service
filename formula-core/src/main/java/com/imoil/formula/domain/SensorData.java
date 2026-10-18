package com.imoil.formula.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IoT Sensor Data Domain Model
 * Maps to the QuestDB 'sensor_data' table
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorData {
    private String sensorId;

    // IoT 장치 식별자 (QuestDB symbol: device_id)
    private String deviceId;

    // 장치 내 센서 인덱스 (QuestDB symbol: sensor_index)
    private Integer sensorIndex;
    
    // 발생 시각 (밀리초 단위, milliseconds since epoch)
    private long timestamp;
    
    // 센서 측정 값
    private double value;
    
    // 상태 값 (0~3 범위)
    private int state;

    // 윈도우 함수 등 히스토리 데이터가 충분하지 않아 연산 결과가 부정확할 수 있는지 여부
    @Builder.Default
    private boolean hasInaccurateData = false;

    /**
     * 로컬 일시(LocalDateTime)로 발생 시각 변환 (시스템 기본 로컬 타임존 기준)
     */
    public java.time.LocalDateTime toLocalDateTime() {
        return toLocalDateTime(java.time.ZoneId.systemDefault());
    }

    /**
     * 지정한 타임존 기준 로컬 일시(LocalDateTime)로 발생 시각 변환
     */
    public java.time.LocalDateTime toLocalDateTime(java.time.ZoneId zoneId) {
        java.time.ZoneId zone = (zoneId != null) ? zoneId : java.time.ZoneId.systemDefault();
        return java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(this.timestamp), zone);
    }

    /**
     * 로컬 일시(LocalDateTime)를 기준으로 타임스탬프 설정 (시스템 기본 로컬 타임존 기준)
     */
    public void fromLocalDateTime(java.time.LocalDateTime localDateTime) {
        fromLocalDateTime(localDateTime, java.time.ZoneId.systemDefault());
    }

    /**
     * 지정한 타임존 기준 로컬 일시(LocalDateTime)로 타임스탬프 설정
     */
    public void fromLocalDateTime(java.time.LocalDateTime localDateTime, java.time.ZoneId zoneId) {
        if (localDateTime != null) {
            java.time.ZoneId zone = (zoneId != null) ? zoneId : java.time.ZoneId.systemDefault();
            this.timestamp = localDateTime.atZone(zone).toInstant().toEpochMilli();
        }
    }
}
