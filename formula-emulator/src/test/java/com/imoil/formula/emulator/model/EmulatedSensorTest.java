package com.imoil.formula.emulator.model;

import com.imoil.formula.domain.SensorItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class EmulatedSensorTest {

    @Test
    @DisplayName("센서 변동 주기가 도래하기 전에는 null을 반환하고, 주기 도래 시 변경된 SensorItem을 반환해야 한다")
    void testSensorTickInterval() {
        Random rand = new Random(12345);
        long baseTime = 1000000L;
        EmulatedSensor sensor = new EmulatedSensor(42, 1, 10, 0.0, baseTime, rand);

        // 최초 틱 시점(0ms 경과): 아직 주기(최소 1초)가 도래하지 않음
        SensorItem itemImmediate = sensor.evaluateTick(baseTime, rand);
        assertThat(itemImmediate).isNull();

        // 11초 경과 시점: 최대 주기가 10초이므로 반드시 변동 발생
        SensorItem itemLater = sensor.evaluateTick(baseTime + 11000L, rand);
        assertThat(itemLater).isNotNull();
        assertThat(itemLater.getSensorIndex()).isEqualTo(42);
        assertThat(itemLater.getStatus()).isEqualTo("normal");
    }

    @Test
    @DisplayName("downProbability에 의해 센서 상태가 down으로 전이되고, 값은 0.0이 되어야 한다")
    void testSensorDownState() {
        Random rand = new Random(42);
        long baseTime = 1000000L;
        // downProbability 100% 설정
        EmulatedSensor sensor = new EmulatedSensor(1, 1, 2, 1.0, baseTime, rand);

        SensorItem item = sensor.evaluateTick(baseTime + 5000L, rand);
        assertThat(item).isNotNull();
        assertThat(item.getStatus()).isEqualTo("down");
        assertThat(item.getValue()).isEqualTo(0.0);
    }
}
