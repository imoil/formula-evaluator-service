package com.imoil.formula.emulator.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imoil.formula.domain.SensorItem;
import com.imoil.formula.emulator.config.EmulatorConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 지연 없이 초당 1회 배치 전송을 수행하는 고성능 Kafka Producer
 */
@Slf4j
public class SensorKafkaProducer implements Closeable {

    public static final String HEADER_DEVICE_ID = "deviceId";
    public static final String HEADER_TIMESTAMP = "timestamp";

    private final Producer<String, String> producer;
    private final String topic;
    private final ObjectMapper objectMapper;
    private final AtomicLong sentMessageCount = new AtomicLong(0);
    private final AtomicLong failedMessageCount = new AtomicLong(0);

    public SensorKafkaProducer(EmulatorConfig config) {
        this(config, createDefaultProducer(config));
    }

    public SensorKafkaProducer(EmulatorConfig config, Producer<String, String> producer) {
        this.topic = config.getKafkaTopic();
        this.objectMapper = new ObjectMapper();
        this.producer = producer;
    }

    public static Producer<String, String> createDefaultProducer(EmulatorConfig config) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // 1초 단위 전송 시 지연을 방지하기 위한 핵심 프로듀서 튜닝
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.LINGER_MS_CONFIG, 0); // 버퍼 지연 없이 즉시 발송
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 1000); // 블로킹 상한 1초
        props.put(ProducerConfig.RETRIES_CONFIG, 1);

        return new KafkaProducer<>(props);
    }

    /**
     * 비동기 논블로킹(Non-blocking Async) 방식으로 카프카에 전송하여 1초 타이머 지연을 방지합니다.
     *
     * @param deviceId   장치명 (헤더에 포함)
     * @param timestamp  전송 시각 (헤더에 포함)
     * @param changedSensors 변동된 센서 목록 (바디에 JSON 배열로 직렬화)
     */
    public void sendSensorBatchAsync(String deviceId, long timestamp, List<SensorItem> changedSensors) {
        try {
            // 1. 헤더 구성: 장치명, 전송 시간
            RecordHeaders headers = new RecordHeaders();
            headers.add(new RecordHeader(HEADER_DEVICE_ID, deviceId.getBytes(StandardCharsets.UTF_8)));
            headers.add(new RecordHeader(HEADER_TIMESTAMP, String.valueOf(timestamp).getBytes(StandardCharsets.UTF_8)));

            // 2. 바디 구성: 변동된 센서들의 JSON 배열
            String bodyJson = objectMapper.writeValueAsString(changedSensors);

            // 3. Kafka ProducerRecord 생성 (Key: deviceId, Value: JSON 배열 body)
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    topic,
                    null,
                    timestamp,
                    deviceId,
                    bodyJson,
                    headers
            );

            // 4. 비동기 전송 및 콜백 등록 (전송 완료를 기다리지 않고 즉시 반환하여 틱 지연 차단)
            Callback callback = (metadata, exception) -> {
                if (exception != null) {
                    failedMessageCount.incrementAndGet();
                    log.error("Kafka send failed for device: {} at timestamp: {}", deviceId, timestamp, exception);
                } else {
                    long total = sentMessageCount.incrementAndGet();
                    if (total % 10 == 0 || log.isDebugEnabled()) {
                        log.info("Sent Kafka batch #{}: device={}, sensors={}, partition={}, offset={}",
                                total, deviceId, changedSensors.size(), metadata.partition(), metadata.offset());
                    }
                }
            };

            producer.send(record, callback);

        } catch (Exception e) {
            failedMessageCount.incrementAndGet();
            log.error("Error creating/sending Kafka message for device: {}", deviceId, e);
        }
    }

    public long getSentMessageCount() {
        return sentMessageCount.get();
    }

    public long getFailedMessageCount() {
        return failedMessageCount.get();
    }

    @Override
    public void close() {
        try {
            producer.flush();
            producer.close();
            log.info("SensorKafkaProducer closed. Total sent: {}, failed: {}",
                    sentMessageCount.get(), failedMessageCount.get());
        } catch (Exception e) {
            log.warn("Exception closing Producer", e);
        }
    }
}
