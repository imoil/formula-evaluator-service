package com.imoil.formula.registry.dto;

import com.imoil.formula.registry.domain.SensorStatus;
import com.imoil.formula.registry.domain.SensorType;
import com.imoil.formula.registry.entity.SensorRegistryEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorRegistryDto {
    private Long serialNumber;
    private String sensorCode;
    private String sensorName;
    private SensorType sensorType;
    private String category;
    private String unit;
    private String deviceId;
    private Integer deviceSensorIndex;
    private String formulaExpression;
    private String description;
    private SensorStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SensorRegistryDto fromEntity(SensorRegistryEntity entity) {
        if (entity == null) {
            return null;
        }
        return SensorRegistryDto.builder()
                .serialNumber(entity.getSerialNumber())
                .sensorCode(entity.getSensorCode())
                .sensorName(entity.getSensorName())
                .sensorType(entity.getSensorType())
                .category(entity.getCategory())
                .unit(entity.getUnit())
                .deviceId(entity.getDeviceId())
                .deviceSensorIndex(entity.getDeviceSensorIndex())
                .formulaExpression(entity.getFormulaExpression())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
