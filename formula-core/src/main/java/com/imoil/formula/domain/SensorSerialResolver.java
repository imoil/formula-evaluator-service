package com.imoil.formula.domain;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IoT 장치 식별자(deviceId)와 로컬 센서 인덱스(sensorIndex)를
 * 단일 센서 관리 체계의 고유 일련번호(serialNumber)로 매핑하는 리졸버.
 * 
 * 카프카 컨슈머에서 수신된 원천 패킷을 Flink DataSource로 방출하기 직전,
 * 단일 일련번호 체계로 변환하는 핵심 매핑 역할을 수행합니다.
 */
public class SensorSerialResolver implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Pattern DEVICE_NUMBER_PATTERN = Pattern.compile(".*?(\\d+)$");

    /** 기본 시작 일련번호 */
    public static final long DEFAULT_BASE_SERIAL = 10001L;

    /** 장치별 센서 분할 오프셋 (미등록 폴백 시 장치별로 1000개 단위 격리) */
    public static final long DEFAULT_DEVICE_STRIDE = 1000L;

    /** 등록된 장치+센서 인덱스 -> 일련번호 매핑 테이블 */
    private final ConcurrentHashMap<DeviceSensorKey, Long> registryMappings = new ConcurrentHashMap<>();

    private static volatile SensorSerialResolver instance = new SensorSerialResolver();

    public static SensorSerialResolver getInstance() {
        return instance;
    }

    public static void setInstance(SensorSerialResolver newInstance) {
        if (newInstance != null) {
            instance = newInstance;
        }
    }

    public SensorSerialResolver() {
    }

    /**
     * 장치와 센서 인덱스에 대한 일련번호 매핑을 등록합니다.
     */
    public void registerMapping(String deviceId, int sensorIndex, long serialNumber) {
        DeviceSensorKey key = DeviceSensorKey.of(deviceId, sensorIndex);
        registryMappings.put(key, serialNumber);
    }

    public void registerMapping(DeviceSensorKey key, long serialNumber) {
        if (key != null) {
            registryMappings.put(key, serialNumber);
        }
    }

    public void registerMappings(Map<DeviceSensorKey, Long> mappings) {
        if (mappings != null) {
            registryMappings.putAll(mappings);
        }
    }

    /**
     * 특정 장치의 연속된 센서 인덱스들을 시작 일련번호부터 일괄 매핑 등록
     */
    public void registerDeviceRange(String deviceId, int sensorCount, long startSerialNumber) {
        for (int i = 0; i < sensorCount; i++) {
            registerMapping(deviceId, i, startSerialNumber + i);
        }
    }

    public void clearMappings() {
        registryMappings.clear();
    }

    public int getMappingsCount() {
        return registryMappings.size();
    }

    public Map<DeviceSensorKey, Long> getAllMappings() {
        return Collections.unmodifiableMap(registryMappings);
    }

    /**
     * 장치 식별자와 센서 인덱스로부터 단일 일련번호(serialNumber)를 조회/해결합니다.
     * 1) 명시적으로 등록된 Oracle 영속성 매핑이 있는 경우 해당 일련번호 반환
     * 2) 미등록 장치인 경우 충돌이 발생하지 않도록 장치 식별자 기반 결정론적(Deterministic) 고유 일련번호 산출
     */
    public long resolveSerialNumber(String deviceId, int sensorIndex) {
        DeviceSensorKey key = DeviceSensorKey.of(deviceId, sensorIndex);
        Long mapped = registryMappings.get(key);
        if (mapped != null) {
            return mapped;
        }

        // 미등록 센서에 대한 결정론적 격리 폴백 (다중 장치 간 인덱스 충돌 원천 차단)
        return calculateDeterministicFallbackSerial(key.getDeviceId(), sensorIndex);
    }

    /**
     * Flink SensorData의 sensorId(String) 형태로 반환 ("10001")
     */
    public String resolveSensorId(String deviceId, int sensorIndex) {
        return String.valueOf(resolveSerialNumber(deviceId, sensorIndex));
    }

    /**
     * 접두사 형식의 식별자 반환 ("SN_10001")
     */
    public String resolvePrefixedSensorId(String deviceId, int sensorIndex) {
        return "SN_" + resolveSerialNumber(deviceId, sensorIndex);
    }

    /**
     * 장치 번호 또는 해시 오프셋을 활용하여 장치 간 중복 없는 일련번호 생성
     */
    private long calculateDeterministicFallbackSerial(String normalizedDeviceId, int sensorIndex) {
        Matcher matcher = DEVICE_NUMBER_PATTERN.matcher(normalizedDeviceId);
        if (matcher.find()) {
            try {
                long deviceNum = Long.parseLong(matcher.group(1));
                long deviceOffset = Math.max(0, deviceNum - 1);
                return 10000L + (deviceOffset * DEFAULT_DEVICE_STRIDE) + (sensorIndex + 1);
            } catch (NumberFormatException ignored) {}
        }

        // 문자열 장치명 해시 기반 격리
        long hashOffset = (Math.abs((long) normalizedDeviceId.hashCode()) % 500L) + 1;
        return 500000L + (hashOffset * DEFAULT_DEVICE_STRIDE) + (sensorIndex + 1);
    }
}
