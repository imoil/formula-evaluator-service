package com.imoil.formula.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * IoT 장치 에뮬레이터 메시지 Envelope (Header + Body JSON Array)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSensorMessage {
    /** 장치명 및 전송 시간을 담은 헤더 */
    private DeviceMessageHeader header;

    /** 1초 동안 변동이 발생한 센서 목록 배열 */
    private List<SensorItem> body;
}
