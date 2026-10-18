package com.imoil.formula.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SensorSerialResolverTest {

    private SensorSerialResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = SensorSerialResolver.getInstance();
        resolver.clearMappings();
    }

    @Test
    void testSerialIdConversionsFollowGlossary() {
        assertEquals("10001", SensorSerialResolver.toCanonicalSerialId("SN_10001"));
        assertEquals("10001", SensorSerialResolver.toCanonicalSerialId("10001"));
        assertEquals("SN_10001", SensorSerialResolver.toPrefixedOrCanonicalSerialId("10001"));
        assertEquals("10001", SensorSerialResolver.toPrefixedOrCanonicalSerialId("SN_10001"));
        assertNull(SensorSerialResolver.toPrefixedOrCanonicalSerialId("TEMP_RAW"));
    }

    @Test
    void testBindSerialIdentifiersPopulatesBothCanonicalAndPrefixedKeys() {
        Map<String, Object> env = new HashMap<>();
        SensorSerialResolver.bindSerialIdentifiers(env, "SN_10001", 42.5);

        assertEquals(42.5, env.get("10001"));
        assertEquals(42.5, env.get("SN_10001"));
    }

    @Test
    void testExtractTargetSensorId() {
        assertEquals("SN_10001", SensorSerialResolver.extractTargetSensorId("SN_10001 * 1.8 + 32", "10201"));
        assertEquals("10002", SensorSerialResolver.extractTargetSensorId("window_avg(10) + 10002", "10202"));
        assertEquals("10203", SensorSerialResolver.extractTargetSensorId("value * 2.0", "10203"));
    }

    @Test
    void testDeviceSensorKeyNormalizesDeviceIdAndResolvesDeterministicFallback() {
        DeviceSensorKey keyLower = new DeviceSensorKey(" device-001 ", 0);
        DeviceSensorKey keyUpper = new DeviceSensorKey("DEVICE-001", 0);
        assertEquals("DEVICE-001", keyLower.getDeviceId());
        assertEquals(keyUpper, keyLower);
        assertEquals(keyUpper.hashCode(), keyLower.hashCode());

        assertEquals(10001L, resolver.resolveSerialNumber("device-001", 0));
        assertEquals(11001L, resolver.resolveSerialNumber("device-002", 0));
    }

    @Test
    void testDynamicRuleCanonicalTargetAndOutputResolution() {
        DynamicRule rule = DynamicRule.builder()
                .ruleId("r1")
                .targetSensorId("SN_10001")
                .expression("value * 2")
                .derivedSensorId("SN_10201")
                .build();

        assertEquals("10001", rule.canonicalTargetSerialId());
        assertEquals("10201", rule.resolveOutputSensorId("10001"));

        DynamicRule fallbackRule = DynamicRule.builder()
                .ruleId("r2")
                .targetSensorId("10001")
                .expression("value + 1")
                .build();
        assertEquals("10001_derived", fallbackRule.resolveOutputSensorId("10001"));
    }
}
