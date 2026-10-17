package com.imoil.formula.registry.dto;

import com.imoil.formula.registry.domain.SensorStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSensorRequest {
    private String sensorName;
    private String category;
    private String unit;
    private String formulaExpression;
    private String description;
    private SensorStatus status;
}
