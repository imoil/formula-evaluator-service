package com.imoil.formula.registry.domain;

import java.util.Map;

/**
 * 센서 구분:
 * - PHYSICAL: formula-emulator IoT 디바이스가 제공하는 물리 센서 (deviceId, deviceSensorIndex 보유)
 * - FORMULA: 물리 센서 또는 다른 수식 센서들을 조합하여 정의한 수식 센서
 */
public enum SensorType {
    PHYSICAL,
    FORMULA;

    private static final String[] DEFAULT_CATEGORIES = {
            "Temperature", "Pressure", "Vibration", "Electrical", "Flow", "Mechanical", "Environmental"
    };

    private static final Map<String, String> DEFAULT_UNITS = Map.of(
            "temperature", "°C",
            "pressure", "bar",
            "vibration", "mm/s",
            "electrical", "kW",
            "flow", "m³/h",
            "mechanical", "RPM",
            "environmental", "%"
    );

    public static String defaultCategoryForIndex(int index) {
        return DEFAULT_CATEGORIES[Math.floorMod(index, DEFAULT_CATEGORIES.length)];
    }

    public static String defaultUnitForCategory(String category) {
        if (category == null) {
            return "%";
        }
        return DEFAULT_UNITS.getOrDefault(category.trim().toLowerCase(java.util.Locale.ROOT), "%");
    }
}
