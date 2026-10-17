package com.imoil.formula.registry.domain;

/**
 * 센서 구분:
 * - PHYSICAL: formula-emulator IoT 디바이스가 제공하는 물리 센서 (deviceId, deviceSensorIndex 보유)
 * - FORMULA: 물리 센서 또는 다른 수식 센서들을 조합하여 정의한 수식 센서
 */
public enum SensorType {
    PHYSICAL,
    FORMULA
}
