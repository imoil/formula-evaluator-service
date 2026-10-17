package com.imoil.formula.emulator.engine;

import com.imoil.formula.domain.SensorItem;
import com.imoil.formula.emulator.config.EmulatorConfig;
import com.imoil.formula.emulator.kafka.SensorKafkaProducer;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SensorDeviceEmulatorTest {

    @Test
    @DisplayName("1000개 센서 에뮬레이터에서 틱 실행 시 변동된 센서가 집계되어 카프카로 전송되어야 한다")
    void testEmulatorTickExecution() {
        EmulatorConfig config = EmulatorConfig.builder()
                .deviceId("DEVICE-001")
                .sensorCount(1000)
                .minIntervalSeconds(1)
                .maxIntervalSeconds(10)
                .emitIntervalMillis(1000L)
                .kafkaTopic("sensor-data")
                .build();

        MockProducer<String, String> mockProducer = new MockProducer<>(
                true,
                new StringSerializer(),
                new StringSerializer()
        );
        SensorKafkaProducer producer = new SensorKafkaProducer(config, mockProducer);
        SensorDeviceEmulator emulator = new SensorDeviceEmulator(config, producer);

        // 첫 번째 틱 실행 (약간의 초기 센서 변경 발생 가능)
        List<SensorItem> tick1 = emulator.tick();
        assertThat(emulator.getTickCount()).isEqualTo(1);

        // 10초 후 시점으로 틱을 수차례 실행하면 1000개 센서 중 변동된 센서들이 다수 발생함
        try {
            Thread.sleep(1100);
        } catch (InterruptedException ignored) {}

        List<SensorItem> tick2 = emulator.tick();
        assertThat(emulator.getTickCount()).isEqualTo(2);

        // 1000개 센서이고 주기가 1~10초이므로, 1초 이상 경과 시 변동된 센서 목록이 비어있지 않아야 함
        assertThat(tick2).isNotEmpty();
        assertThat(mockProducer.history()).isNotEmpty();

        ProducerRecord<String, String> lastRecord = mockProducer.history().get(mockProducer.history().size() - 1);
        assertThat(lastRecord.key()).isEqualTo("DEVICE-001");
        assertThat(lastRecord.value()).contains("sensorIndex");

        emulator.close();
    }
}
