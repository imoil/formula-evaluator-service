package com.imoil.formula.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * IoT 장치 식별자(deviceId)와 장치 내부 센서 인덱스(sensorIndex)의 복합 키.
 * 단일 센서 관리 체계의 일련번호(serialNumber)로 매핑하기 위한 고유 식별자로 사용됩니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSensorKey implements Serializable, Comparable<DeviceSensorKey> {

    private static final long serialVersionUID = 1L;

    /** IoT 장치 고유 번호/명 (예: DEVICE-001, TURBINE-01) */
    private String deviceId;

    /** 장치 내부 로컬 센서 인덱스 (0, 1, 2 ...) */
    private int sensorIndex;

    public static DeviceSensorKey of(String deviceId, int sensorIndex) {
        String normalizedDevice = (deviceId != null) ? deviceId.trim().toUpperCase(Locale.ROOT) : "DEVICE-DEFAULT";
        return new DeviceSensorKey(normalizedDevice, sensorIndex);
    }

    public String toCompositeKey() {
        return ((deviceId != null) ? deviceId.trim().toUpperCase(Locale.ROOT) : "DEVICE-DEFAULT") + "#" + sensorIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeviceSensorKey that)) return false;
        String thisDev = (deviceId != null) ? deviceId.trim().toUpperCase(Locale.ROOT) : "";
        String thatDev = (that.deviceId != null) ? that.deviceId.trim().toUpperCase(Locale.ROOT) : "";
        return sensorIndex == that.sensorIndex && Objects.equals(thisDev, thatDev);
    }

    @Override
    public int hashCode() {
        String thisDev = (deviceId != null) ? deviceId.trim().toUpperCase(Locale.ROOT) : "";
        return Objects.hash(thisDev, sensorIndex);
    }

    @Override
    public int compareTo(DeviceSensorKey o) {
        if (o == null) return 1;
        String thisDev = (deviceId != null) ? deviceId.trim().toUpperCase(Locale.ROOT) : "";
        String thatDev = (o.deviceId != null) ? o.deviceId.trim().toUpperCase(Locale.ROOT) : "";
        int devCompare = thisDev.compareTo(thatDev);
        if (devCompare != 0) return devCompare;
        return Integer.compare(this.sensorIndex, o.sensorIndex);
    }

    @Override
    public String toString() {
        return toCompositeKey();
    }
}
