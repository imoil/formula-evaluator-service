package com.imoil.formula.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 개별 IoT 센서 변동 정보 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorItem {
    /** 유니크한 센서 인덱스 (0 ~ 999 등) */
    private int sensorIndex;

    /** 센서 변동 측정값 */
    private double value;

    /** 센서 상태 ("normal", "down") */
    private String status;
}
