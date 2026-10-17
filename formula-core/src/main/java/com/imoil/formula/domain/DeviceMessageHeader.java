package com.imoil.formula.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IoT 디바이스 카프카 메시지 헤더 모델
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMessageHeader {
    /** 장치명 (예: DEVICE-001) */
    private String deviceId;

    /** 전송 시간 (epoch milliseconds) */
    private long timestamp;
}
