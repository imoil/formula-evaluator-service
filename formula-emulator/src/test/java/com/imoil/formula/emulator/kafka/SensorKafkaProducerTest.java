package com.imoil.formula.emulator.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imoil.formula.domain.SensorItem;
import com.imoil.formula.emulator.config.EmulatorConfig;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SensorKafkaProducerTest {

    @Test
    @DisplayName("Kafka ProducerRecord의 헤더에 장치명과 전송시간이 들어가고 body에는 센서 JSON 배열이 포함되어야 한다")
    void testSendSensorBatchAsyncFormat() throws Exception {
        EmulatorConfig config = EmulatorConfig.builder()
                .deviceId("DEVICE-TEST-99")
                .kafkaTopic("sensor-data")
                .build();

        MockProducer<String, String> mockProducer = new MockProducer<>(
                true,
                new StringSerializer(),
                new StringSerializer()
        );

        SensorKafkaProducer producer = new SensorKafkaProducer(config, mockProducer);

        long timestamp = 1729150000000L;
        List<SensorItem> sensors = List.of(
                SensorItem.builder().sensorIndex(0).value(23.5).status("normal").build(),
                SensorItem.builder().sensorIndex(7).value(101.2).status("normal").build(),
                SensorItem.builder().sensorIndex(99).value(0.0).status("down").build()
        );

        producer.sendSensorBatchAsync("DEVICE-TEST-99", timestamp, sensors);

        List<ProducerRecord<String, String>> history = mockProducer.history();
        assertThat(history).hasSize(1);

        ProducerRecord<String, String> record = history.get(0);
        assertThat(record.topic()).isEqualTo("sensor-data");
        assertThat(record.key()).isEqualTo("DEVICE-TEST-99");

        // 1. 헤더 검증
        Header deviceIdHeader = record.headers().lastHeader("deviceId");
        assertThat(deviceIdHeader).isNotNull();
        assertThat(new String(deviceIdHeader.value(), StandardCharsets.UTF_8)).isEqualTo("DEVICE-TEST-99");

        Header timestampHeader = record.headers().lastHeader("timestamp");
        assertThat(timestampHeader).isNotNull();
        assertThat(new String(timestampHeader.value(), StandardCharsets.UTF_8)).isEqualTo("1729150000000");

        // 2. 바디(JSON 배열) 검증
        String bodyJson = record.value();
        assertThat(bodyJson).startsWith("[").endsWith("]");

        ObjectMapper mapper = new ObjectMapper();
        List<SensorItem> deserialized = mapper.readValue(bodyJson, new TypeReference<>() {});
        assertThat(deserialized).hasSize(3);
        assertThat(deserialized.get(0).getSensorIndex()).isEqualTo(0);
        assertThat(deserialized.get(0).getValue()).isEqualTo(23.5);
        assertThat(deserialized.get(0).getStatus()).isEqualTo("normal");
        assertThat(deserialized.get(2).getStatus()).isEqualTo("down");

        producer.close();
    }

    @Test
    @DisplayName("모아서 전송하지 않고 1초마다 개별 센서 레코드를 전송할 때 헤더와 단일 JSON 바디가 정확히 생성되어야 한다")
    void testSendSensorAsyncFormat() throws Exception {
        EmulatorConfig config = EmulatorConfig.builder()
                .deviceId("DEVICE-TEST-100")
                .kafkaTopic("sensor-data")
                .build();

        MockProducer<String, String> mockProducer = new MockProducer<>(
                true,
                new StringSerializer(),
                new StringSerializer()
        );

        SensorKafkaProducer producer = new SensorKafkaProducer(config, mockProducer);

        long timestamp = 1729150000000L;
        SensorItem item = SensorItem.builder().sensorIndex(5).value(45.67).status("normal").build();

        producer.sendSensorAsync("DEVICE-TEST-100", timestamp, item);

        List<ProducerRecord<String, String>> history = mockProducer.history();
        assertThat(history).hasSize(1);

        ProducerRecord<String, String> record = history.get(0);
        assertThat(record.topic()).isEqualTo("sensor-data");
        assertThat(record.key()).isEqualTo("DEVICE-TEST-100:5");

        Header deviceIdHeader = record.headers().lastHeader("deviceId");
        assertThat(deviceIdHeader).isNotNull();
        assertThat(new String(deviceIdHeader.value(), StandardCharsets.UTF_8)).isEqualTo("DEVICE-TEST-100");

        Header sensorIndexHeader = record.headers().lastHeader("sensorIndex");
        assertThat(sensorIndexHeader).isNotNull();
        assertThat(new String(sensorIndexHeader.value(), StandardCharsets.UTF_8)).isEqualTo("5");

        // 단일 SensorItem JSON 객체
        String bodyJson = record.value();
        assertThat(bodyJson).startsWith("{").endsWith("}");

        ObjectMapper mapper = new ObjectMapper();
        SensorItem deserialized = mapper.readValue(bodyJson, SensorItem.class);
        assertThat(deserialized.getSensorIndex()).isEqualTo(5);
        assertThat(deserialized.getValue()).isEqualTo(45.67);
        assertThat(deserialized.getStatus()).isEqualTo("normal");

        producer.close();
    }
}
