package com.imoil.formula.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imoil.formula.domain.SensorData;
import com.imoil.formula.domain.SensorItem;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceSensorRecordDeserializerTest {

    @Test
    @DisplayName("Kafka Header와 JSON 센서 배열 바디를 SensorData 스트림으로 정상 분해/역직렬화해야 한다")
    void testDeserializeHeaderAndArrayBody() throws Exception {
        DeviceSensorRecordDeserializer deserializer = new DeviceSensorRecordDeserializer();

        String deviceId = "DEVICE-001";
        long timestamp = 1729152000000L;

        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("deviceId", deviceId.getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader("timestamp", String.valueOf(timestamp).getBytes(StandardCharsets.UTF_8)));

        List<SensorItem> items = List.of(
                SensorItem.builder().sensorIndex(5).value(45.2).status("normal").build(),
                SensorItem.builder().sensorIndex(99).value(0.0).status("down").build()
        );

        ObjectMapper mapper = new ObjectMapper();
        byte[] bodyBytes = mapper.writeValueAsBytes(items);

        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>(
                "sensor-data",
                0,
                100L,
                timestamp,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME,
                0,
                0,
                deviceId.getBytes(StandardCharsets.UTF_8),
                bodyBytes,
                headers,
                java.util.Optional.empty()
        );

        List<SensorData> collected = new ArrayList<>();
        Collector<SensorData> collector = new Collector<>() {
            @Override
            public void collect(SensorData record) {
                collected.add(record);
            }

            @Override
            public void close() {}
        };

        deserializer.deserialize(record, collector);

        assertThat(collected).hasSize(2);

        SensorData first = collected.get(0);
        assertThat(first.getSensorId()).isEqualTo("sensor_005");
        assertThat(first.getTimestamp()).isEqualTo(timestamp);
        assertThat(first.getValue()).isEqualTo(45.2);
        assertThat(first.getState()).isEqualTo(0);

        SensorData second = collected.get(1);
        assertThat(second.getSensorId()).isEqualTo("sensor_099");
        assertThat(second.getTimestamp()).isEqualTo(timestamp);
        assertThat(second.getValue()).isEqualTo(0.0);
        assertThat(second.getState()).isEqualTo(1); // "down" -> 1
    }

    @Test
    @DisplayName("Envelope 형태의 JSON({header, body})도 정상적으로 호환 역직렬화해야 한다")
    void testDeserializeEnvelopeJson() throws Exception {
        DeviceSensorRecordDeserializer deserializer = new DeviceSensorRecordDeserializer();

        String jsonEnvelope = """
                {
                  "header": {
                    "deviceId": "DEVICE-002",
                    "timestamp": 1729153000000
                  },
                  "body": [
                    { "sensorIndex": 12, "value": 78.4, "status": "normal" }
                  ]
                }
                """;

        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>(
                "sensor-data",
                0,
                101L,
                "key".getBytes(StandardCharsets.UTF_8),
                jsonEnvelope.getBytes(StandardCharsets.UTF_8)
        );

        List<SensorData> collected = new ArrayList<>();
        deserializer.deserialize(record, new Collector<>() {
            @Override
            public void collect(SensorData record) {
                collected.add(record);
            }

            @Override
            public void close() {}
        });

        assertThat(collected).hasSize(1);
        assertThat(collected.get(0).getSensorId()).isEqualTo("sensor_012");
        assertThat(collected.get(0).getTimestamp()).isEqualTo(1729153000000L);
        assertThat(collected.get(0).getValue()).isEqualTo(78.4);
    }
}
