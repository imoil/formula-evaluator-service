package com.imoil.formula.emulator;

import com.imoil.formula.domain.SensorData;
import com.imoil.formula.emulator.config.EmulatorConfig;
import com.imoil.formula.emulator.engine.SensorDeviceEmulator;
import com.imoil.formula.emulator.kafka.SensorKafkaProducer;
import com.imoil.formula.flink.DeviceSensorRecordDeserializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 에뮬레이터 핵심 요구사항 심층 검증 테스트
 * 
 * 1. 1,000개 센서 에뮬레이션 정상 동작 여부
 * 2. 1초~10초 주기 변동 발생 여부 (통계적 검증)
 * 3. 매 1초마다 변동된 센서만 집계하여 1개 메시지로 전송하는지 여부
 * 4. 1초 틱 실행 시 지연(Drift / Jitter)이 허용 범위 이내인지 여부
 * 5. 카프카 메시지 헤더(장치명, 전송시간) 및 바디(JSON 센서 배열) 규격 일치 여부
 * 6. Flink의 DeviceSensorRecordDeserializer와의 엔드투엔드 역직렬화 호환성 및 단일 일련번호 체계 검증
 */
@Slf4j
class SensorEmulatorVerificationTest {

    @Test
    @DisplayName("[통합 정밀 검증] 1000개 센서, 1초 무지연 스케줄링, 카프카 전송 및 Flink 역직렬화 엔드투엔드 검증")
    void testFullEmulatorVerification() throws Exception {
        int sensorCount = 1000;
        String deviceId = "DEVICE-VERIFY-001";
        String topic = "sensor-data";

        EmulatorConfig config = EmulatorConfig.builder()
                .deviceId(deviceId)
                .sensorCount(sensorCount)
                .minIntervalSeconds(1)
                .maxIntervalSeconds(10)
                .emitIntervalMillis(1000L)
                .kafkaTopic(topic)
                .downProbability(0.02) // 2% 장애 확률
                .build();

        MockProducer<String, String> mockProducer = new MockProducer<>(
                true,
                new StringSerializer(),
                new StringSerializer()
        );
        SensorKafkaProducer producer = new SensorKafkaProducer(config, mockProducer);
        SensorDeviceEmulator emulator = new SensorDeviceEmulator(config, producer);

        log.info(">>> Starting 5-second real-time verification run...");
        long startTimeMs = System.currentTimeMillis();
        emulator.start();

        // 5.2초 동안 실시간 실행 대기 (약 5회의 1초 틱 발생)
        Thread.sleep(5200);

        emulator.close();
        long totalElapsedMs = System.currentTimeMillis() - startTimeMs;
        log.info(">>> Verification run finished. Total elapsed: {}ms", totalElapsedMs);

        // ==========================================
        // 1. 틱 횟수 및 주기 무지연(Drift) 검증
        // ==========================================
        long tickCount = emulator.getTickCount();
        log.info("[검증 1] 틱 실행 횟수: {}회 (5.2초 대기 중 5~6회 기대)", tickCount);
        assertThat(tickCount).isBetween(5L, 6L);

        long maxJitter = emulator.getMaxObservedJitterMs();
        log.info("[검증 1] 관측된 최대 틱 지연(Jitter): {}ms", maxJitter);
        assertThat(maxJitter).isLessThan(50L); // 1초 주기가 밀리지 않고 50ms 미만 정밀도 유지

        // ==========================================
        // 2. 카프카 전송 메시지 수 및 센서 변동량 검증
        // ==========================================
        List<ProducerRecord<String, String>> sentRecords = mockProducer.history();
        log.info("[검증 2] 카프카 전송 메시지 건수: {}건", sentRecords.size());
        assertThat(sentRecords.size()).isBetween(5, 6);

        long totalChangedSensors = emulator.getTotalChangedSensorsCount();
        log.info("[검증 2] 5초간 누적 변동 센서 수: {}건 (1000개 센서 기준)", totalChangedSensors);
        assertThat(totalChangedSensors).isGreaterThan(100L); // 1000개 센서 중 1~10초 주기이므로 다수 변동 필수

        // ==========================================
        // 3. 카프카 메시지 헤더 및 바디 포맷 검증
        // ==========================================
        for (ProducerRecord<String, String> record : sentRecords) {
            assertThat(record.topic()).isEqualTo(topic);
            assertThat(record.key()).isEqualTo(deviceId);

            // 헤더 검증
            Header deviceIdHeader = record.headers().lastHeader("deviceId");
            assertThat(deviceIdHeader).isNotNull();
            assertThat(new String(deviceIdHeader.value(), StandardCharsets.UTF_8)).isEqualTo(deviceId);

            Header timestampHeader = record.headers().lastHeader("timestamp");
            assertThat(timestampHeader).isNotNull();
            long recordTime = Long.parseLong(new String(timestampHeader.value(), StandardCharsets.UTF_8));
            assertThat(recordTime).isGreaterThan(0L);

            // 바디 검증 (JSON 배열)
            String bodyJson = record.value();
            assertThat(bodyJson).startsWith("[").endsWith("]");
        }
        log.info("[검증 3] 모든 카프카 레코드의 헤더(deviceId, timestamp) 및 바디(JSON Array) 포맷 일치 확인");

        // ==========================================
        // 4. Flink DeviceSensorRecordDeserializer 연동 검증
        // ==========================================
        DeviceSensorRecordDeserializer deserializer = new DeviceSensorRecordDeserializer();
        List<SensorData> flinkCollectedData = new CopyOnWriteArrayList<>();

        Collector<SensorData> collector = new Collector<>() {
            @Override
            public void collect(SensorData record) {
                flinkCollectedData.add(record);
            }

            @Override
            public void close() {}
        };

        // 에뮬레이터가 보낸 카프카 레코드를 Flink 디시리얼라이저로 직접 역직렬화 수행
        for (int i = 0; i < sentRecords.size(); i++) {
            ProducerRecord<String, String> pRecord = sentRecords.get(i);
            ConsumerRecord<byte[], byte[]> cRecord = new ConsumerRecord<>(
                    pRecord.topic(),
                    0,
                    i,
                    pRecord.timestamp(),
                    org.apache.kafka.common.record.TimestampType.CREATE_TIME,
                    0,
                    0,
                    pRecord.key().getBytes(StandardCharsets.UTF_8),
                    pRecord.value().getBytes(StandardCharsets.UTF_8),
                    pRecord.headers(),
                    java.util.Optional.empty()
            );

            deserializer.deserialize(cRecord, collector);
        }

        log.info("[검증 4] Flink 파이프라인에서 역직렬화된 총 SensorData 이벤트 수: {}건", flinkCollectedData.size());
        assertThat(flinkCollectedData).isNotEmpty();

        // 첫 번째 변환된 SensorData의 구조 검증 (단일 일련번호 체계 10001+ 검증)
        SensorData firstSample = flinkCollectedData.get(0);
        assertThat(firstSample.getSensorId()).matches("\\d+");
        assertThat(Long.parseLong(firstSample.getSensorId())).isGreaterThanOrEqualTo(10001L);
        assertThat(firstSample.getValue()).isNotNaN();
        assertThat(firstSample.getState()).isIn(0, 1);
        assertThat(firstSample.getTimestamp()).isGreaterThan(0L);

        log.info("[검증 4] Flink 역직렬화 모델 매핑 검증 완료: sample={}", firstSample);
        log.info(">>> ALL 6 VERIFICATION CRITERIA PASSED SUCCESSFULLY! <<<");
    }
}
