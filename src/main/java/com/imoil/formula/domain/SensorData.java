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
    
    // 발생 시각 (밀리초 단위, milliseconds since epoch)
    private long timestamp;
    
    // 센서 측정 값
    private double value;
    
    // 상태 값 (0~3 범위)
    private int state;

    // 윈도우 함수 등 히스토리 데이터가 충분하지 않아 연산 결과가 부정확할 수 있는지 여부
    @Builder.Default
    private boolean hasInaccurateData = false;
}
