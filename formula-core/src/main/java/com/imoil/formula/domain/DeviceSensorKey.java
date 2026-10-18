package com.imoil.formula.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * IoT 장치 식별자(deviceId)와 장치 내부 센서 인덱스(sensorIndex)의 복합 키.
 * 단일 센서 관리 체계의 일련번호(serialNumber)로 매핑하기 위한 고유 식별자로 사용됩니다.
 */
@Getter
public class DeviceSensorKey implements Serializable, Comparable<DeviceSensorKey> {

    private static final long serialVersionUID = 1L;
    public static final String DEFAULT_DEVICE_ID = "DEVICE-DEFAULT";

    /** IoT 장치 고유 번호/명 (항상 trim + 대문자 정규화 상태로 보관) */
    private String deviceId = DEFAULT_DEVICE_ID;

    /** 장치 내부 로컬 센서 인덱스 (0, 1, 2 ...) */
    @Setter
    private int sensorIndex;

    public DeviceSensorKey() {
        this.deviceId = DEFAULT_DEVICE_ID;
    }

    @Builder
    public DeviceSensorKey(String deviceId, int sensorIndex) {
        this.deviceId = normalizeDeviceId(deviceId);
        this.sensorIndex = sensorIndex;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = normalizeDeviceId(deviceId);
    }

    public static String normalizeDeviceId(String rawDeviceId) {
        return (rawDeviceId != null && !rawDeviceId.trim().isEmpty())
                ? rawDeviceId.trim().toUpperCase(Locale.ROOT)
                : DEFAULT_DEVICE_ID;
    }

    public static DeviceSensorKey of(String deviceId, int sensorIndex) {
        return new DeviceSensorKey(deviceId, sensorIndex);
    }

    /**
     * "DEVICE-001#5" 또는 "DEVICE-001:5" 형태의 복합 식별자를 파싱합니다.
     */
    public static java.util.Optional<DeviceSensorKey> tryParseComposite(String compositeId) {
        if (compositeId == null) {
            return java.util.Optional.empty();
        }
        int sepIdx = compositeId.indexOf('#');
        if (sepIdx < 0) {
            sepIdx = compositeId.indexOf(':');
        }
        if (sepIdx <= 0 || sepIdx >= compositeId.length() - 1) {
            return java.util.Optional.empty();
        }
        try {
            String dev = compositeId.substring(0, sepIdx);
            int idx = Integer.parseInt(compositeId.substring(sepIdx + 1).trim());
            return java.util.Optional.of(DeviceSensorKey.of(dev, idx));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    public String toCompositeKey() {
        return deviceId + "#" + sensorIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeviceSensorKey that)) return false;
        return sensorIndex == that.sensorIndex
                && Objects.equals(this.deviceId, that.deviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.deviceId, this.sensorIndex);
    }

    @Override
    public int compareTo(DeviceSensorKey o) {
        if (o == null) return 1;
        int devCompare = this.deviceId.compareTo(o.deviceId);
        if (devCompare != 0) return devCompare;
        return Integer.compare(this.sensorIndex, o.sensorIndex);
    }

    @Override
    public String toString() {
        return toCompositeKey();
    }
}
