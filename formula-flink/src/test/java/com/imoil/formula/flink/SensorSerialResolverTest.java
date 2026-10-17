package com.imoil.formula.flink;

import com.imoil.formula.domain.DeviceSensorKey;
import com.imoil.formula.domain.SensorSerialResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensorSerialResolverTest {

    private SensorSerialResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SensorSerialResolver();
    }

    @Test
    @DisplayName("사전 등록된 장치 센서 인덱스는 정확히 해당 일련번호로 매핑되어야 한다")
    void testRegisteredMapping() {
        resolver.registerMapping("DEVICE-001", 0, 10001L);
        resolver.registerMapping("DEVICE-001", 1, 10002L);
        resolver.registerMapping("DEVICE-002", 0, 10101L);

        assertThat(resolver.resolveSerialNumber("DEVICE-001", 0)).isEqualTo(10001L);
        assertThat(resolver.resolveSerialNumber("DEVICE-001", 1)).isEqualTo(10002L);
        assertThat(resolver.resolveSerialNumber("DEVICE-002", 0)).isEqualTo(10101L);

        assertThat(resolver.resolveSensorId("DEVICE-001", 0)).isEqualTo("10001");
        assertThat(resolver.resolvePrefixedSensorId("DEVICE-001", 0)).isEqualTo("SN_10001");
    }

    @Test
    @DisplayName("대소문자 및 공백이 포함된 장치 식별자도 동일한 키로 정규화되어 조회되어야 한다")
    void testCaseInsensitiveNormalization() {
        resolver.registerMapping("Device-001", 42, 10042L);

        assertThat(resolver.resolveSerialNumber("device-001", 42)).isEqualTo(10042L);
        assertThat(resolver.resolveSerialNumber("  DEVICE-001  ", 42)).isEqualTo(10042L);
    }

    @Test
    @DisplayName("미등록 장치라도 결정론적 오프셋을 통해 서로 다른 장치 간 충돌이 발생하지 않아야 한다")
    void testDeterministicFallbackPreventsCollision() {
        // DEVICE-001 vs DEVICE-002 의 동일한 센서 인덱스 0
        long serialDev1 = resolver.resolveSerialNumber("DEVICE-001", 0);
        long serialDev2 = resolver.resolveSerialNumber("DEVICE-002", 0);
        long serialDev3 = resolver.resolveSerialNumber("DEVICE-003", 0);

        assertThat(serialDev1).isEqualTo(10001L);
        assertThat(serialDev2).isEqualTo(11001L);
        assertThat(serialDev3).isEqualTo(12001L);

        assertThat(serialDev1).isNotEqualTo(serialDev2);
        assertThat(serialDev2).isNotEqualTo(serialDev3);
    }

    @Test
    @DisplayName("일괄 범위 등록이 정상 동작해야 한다")
    void testRegisterDeviceRange() {
        resolver.registerDeviceRange("TURBINE-01", 50, 20001L);

        assertThat(resolver.resolveSerialNumber("TURBINE-01", 0)).isEqualTo(20001L);
        assertThat(resolver.resolveSerialNumber("TURBINE-01", 49)).isEqualTo(20050L);
        assertThat(resolver.getMappingsCount()).isEqualTo(50);
    }
}
