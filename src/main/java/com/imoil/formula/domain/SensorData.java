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
    
    // 발생 시각 (마이크로초 단위, microseconds since epoch)
    private long timestamp;
    
    // 센서 측정 값
    private double value;
    
    // 상태 값 (0~3 범위)
    private int state;
}
