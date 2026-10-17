package com.imoil.formula.registry.dto;

import com.imoil.formula.registry.domain.SensorStatus;
import com.imoil.formula.registry.domain.SensorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSensorRequest {
    private String sensorCode;
    private String sensorName;
    private SensorType sensorType;
    private String category;
    private String unit;
    private String deviceId;
    private Integer deviceSensorIndex;
    private String formulaExpression;
    private String description;
    @Builder.Default
    private SensorStatus status = SensorStatus.ACTIVE;
}
