package com.imoil.formula.registry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSyncRequest {
    /** IoT 장치 식별자 (예: DEVICE-001) */
    private String deviceId;

    /** 등록할 센서 개수 (기본 100개, 에뮬레이터 디바이스 센서 범위) */
    @Builder.Default
    private int sensorCount = 100;

    /** 기존 장치 센서가 이미 존재할 때 건너뛸지 여부 (기본 true) */
    @Builder.Default
    private boolean skipExisting = true;
}
