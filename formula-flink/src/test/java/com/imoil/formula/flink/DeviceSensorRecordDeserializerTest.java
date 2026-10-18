package com.imoil.formula.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imoil.formula.domain.SensorData;
import com.imoil.formula.domain.SensorItem;
import com.imoil.formula.domain.SensorSerialResolver;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceSensorRecordDeserializerTest {

    private SensorSerialResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SensorSerialResolver();
        SensorSerialResolver.setInstance(resolver);
    }

    @Test
    @DisplayName("Kafka Header와 JSON 센서 배열을 SensorData 스트림으로 단일 일련번호 매핑 역직렬화해야 한다")
    void testDeserializeHeaderAndArrayBodyWithSerialMapping() throws Exception {
        // Oracle 레지스트리에 사전 등록된 시나리오 매핑
        resolver.registerMapping("DEVICE-001", 5, 10005L);
        resolver.registerMapping("DEVICE-001", 99, 10099L);

        DeviceSensorRecordDeserializer deserializer = new DeviceSensorRecordDeserializer(resolver);

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
        assertThat(first.getSensorId()).isEqualTo("10005");
        assertThat(first.getDeviceId()).isEqualTo("DEVICE-001");
        assertThat(first.getSensorIndex()).isEqualTo(5);
        assertThat(first.getTimestamp()).isEqualTo(timestamp);
        assertThat(first.getValue()).isEqualTo(45.2);
        assertThat(first.getState()).isEqualTo(0);

        SensorData second = collected.get(1);
        assertThat(second.getSensorId()).isEqualTo("10099");
        assertThat(second.getDeviceId()).isEqualTo("DEVICE-001");
        assertThat(second.getSensorIndex()).isEqualTo(99);
        assertThat(second.getTimestamp()).isEqualTo(timestamp);
        assertThat(second.getValue()).isEqualTo(0.0);
        assertThat(second.getState()).isEqualTo(1); // "down" -> 1
    }

    @Test
    @DisplayName("서로 다른 장치에서 동일한 sensorIndex 0을 전송해도 단일 일련번호 체계에서 충돌 없이 분리 매핑되어야 한다")
    void testPreventCollisionAcrossMultipleDevices() throws Exception {
        resolver.registerMapping("DEVICE-001", 0, 10001L);
        resolver.registerMapping("DEVICE-002", 0, 10101L);

        DeviceSensorRecordDeserializer deserializer = new DeviceSensorRecordDeserializer(resolver);
        ObjectMapper mapper = new ObjectMapper();

        // 1. DEVICE-001 의 sensorIndex 0
        RecordHeaders headers1 = new RecordHeaders();
        headers1.add(new RecordHeader("deviceId", "DEVICE-001".getBytes(StandardCharsets.UTF_8)));
        ConsumerRecord<byte[], byte[]> record1 = new ConsumerRecord<>(
                "sensor-data", 0, 1L, 1000L,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME,
                0, 0, "DEVICE-001".getBytes(StandardCharsets.UTF_8),
                mapper.writeValueAsBytes(List.of(SensorItem.builder().sensorIndex(0).value(25.0).status("normal").build())),
                headers1, java.util.Optional.empty()
        );

        // 2. DEVICE-002 의 sensorIndex 0 (동일한 인덱스)
        RecordHeaders headers2 = new RecordHeaders();
        headers2.add(new RecordHeader("deviceId", "DEVICE-002".getBytes(StandardCharsets.UTF_8)));
        ConsumerRecord<byte[], byte[]> record2 = new ConsumerRecord<>(
                "sensor-data", 0, 2L, 1000L,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME,
                0, 0, "DEVICE-002".getBytes(StandardCharsets.UTF_8),
                mapper.writeValueAsBytes(List.of(SensorItem.builder().sensorIndex(0).value(85.0).status("normal").build())),
                headers2, java.util.Optional.empty()
        );

        List<SensorData> results = new ArrayList<>();
        Collector<SensorData> collector = new Collector<>() {
            @Override
            public void collect(SensorData record) {
                results.add(record);
            }
            @Override
            public void close() {}
        };

        deserializer.deserialize(record1, collector);
        deserializer.deserialize(record2, collector);

        assertThat(results).hasSize(2);
        // DEVICE-001 의 sensorIndex 0 -> 10001
        assertThat(results.get(0).getSensorId()).isEqualTo("10001");
        assertThat(results.get(0).getDeviceId()).isEqualTo("DEVICE-001");
        assertThat(results.get(0).getSensorIndex()).isEqualTo(0);
        assertThat(results.get(0).getValue()).isEqualTo(25.0);

        // DEVICE-002 의 sensorIndex 0 -> 10101
        assertThat(results.get(1).getSensorId()).isEqualTo("10101");
        assertThat(results.get(1).getDeviceId()).isEqualTo("DEVICE-002");
        assertThat(results.get(1).getSensorIndex()).isEqualTo(0);
        assertThat(results.get(1).getValue()).isEqualTo(85.0);

        // 두 센서 ID는 절대로 같지 않아야 함 (키 충돌 원천 해결)
        assertThat(results.get(0).getSensorId()).isNotEqualTo(results.get(1).getSensorId());
    }

    @Test
    @DisplayName("Envelope 형태의 JSON({header, body})도 정상적으로 단일 일련번호로 역직렬화해야 한다")
    void testDeserializeEnvelopeJson() throws Exception {
        resolver.registerMapping("DEVICE-002", 12, 10112L);
        DeviceSensorRecordDeserializer deserializer = new DeviceSensorRecordDeserializer(resolver);

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
        assertThat(collected.get(0).getSensorId()).isEqualTo("10112");
        assertThat(collected.get(0).getDeviceId()).isEqualTo("DEVICE-002");
        assertThat(collected.get(0).getSensorIndex()).isEqualTo(12);
        assertThat(collected.get(0).getTimestamp()).isEqualTo(1729153000000L);
        assertThat(collected.get(0).getValue()).isEqualTo(78.4);
    }

    @Test
    @DisplayName("모아서 전송하지 않고 1초마다 개별 전송된 단일 SensorItem JSON 레코드 및 LocalDateTime 헤더 역직렬화 검증")
    void testDeserializeSingleSensorItemWithLocalDateTimeHeader() throws Exception {
        resolver.registerMapping("DEVICE-003", 7, 10207L);
        DeviceSensorRecordDeserializer deserializer = new DeviceSensorRecordDeserializer(resolver);

        String deviceId = "DEVICE-003";
        java.time.LocalDateTime localNow = java.time.LocalDateTime.of(2026, 10, 18, 22, 45, 0);
        String localTimeStr = localNow.toString();
        long expectedMillis = localNow.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("deviceId", deviceId.getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader("timestamp", localTimeStr.getBytes(StandardCharsets.UTF_8)));

        // 단일 SensorItem JSON: { "sensorIndex": 7, "value": 88.5, "status": "normal" }
        String singleItemJson = "{\"sensorIndex\":7,\"value\":88.5,\"status\":\"normal\"}";

        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>(
                "sensor-data",
                0,
                102L,
                expectedMillis,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME,
                0,
                0,
                "DEVICE-003:7".getBytes(StandardCharsets.UTF_8),
                singleItemJson.getBytes(StandardCharsets.UTF_8),
                headers,
                java.util.Optional.empty()
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
        SensorData result = collected.get(0);
        assertThat(result.getSensorId()).isEqualTo("10207");
        assertThat(result.getDeviceId()).isEqualTo("DEVICE-003");
        assertThat(result.getSensorIndex()).isEqualTo(7);
        assertThat(result.getTimestamp()).isEqualTo(expectedMillis);
        assertThat(result.getValue()).isEqualTo(88.5);
        assertThat(result.getState()).isEqualTo(0);
        assertThat(result.toLocalDateTime()).isEqualTo(localNow);
    }
}
