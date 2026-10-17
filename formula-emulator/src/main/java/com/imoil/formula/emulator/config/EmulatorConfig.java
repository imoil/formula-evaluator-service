package com.imoil.formula.emulator.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IoT 센서 에뮬레이터 실행 설정
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmulatorConfig {

    /** 장치명 */
    @Builder.Default
    private String deviceId = "DEVICE-001";

    /** 전체 에뮬레이션 센서 개수 (기본 1000개) */
    @Builder.Default
    private int sensorCount = 1000;

    /** 센서별 최소 변동 주기 (초) */
    @Builder.Default
    private int minIntervalSeconds = 1;

    /** 센서별 최대 변동 주기 (초) */
    @Builder.Default
    private int maxIntervalSeconds = 10;

    /** Kafka 전송 주기 (밀리초, 기본 1000ms = 1초) */
    @Builder.Default
    private long emitIntervalMillis = 1000L;

    /** Kafka 브로커 주소 */
    @Builder.Default
    private String kafkaBootstrapServers = "localhost:9092";

    /** Kafka 전송 토픽 */
    @Builder.Default
    private String kafkaTopic = "sensor-data";

    /** 센서 'down' 상태 발생 확률 (예: 0.01 = 1%) */
    @Builder.Default
    private double downProbability = 0.01;

    /**
     * CLI 파라미터 파싱
     */
    public static EmulatorConfig fromArgs(String[] args) {
        EmulatorConfig config = EmulatorConfig.builder().build();
        if (args == null) {
            return config;
        }

        for (String arg : args) {
            if (arg.startsWith("--device-id=")) {
                config.setDeviceId(arg.substring("--device-id=".length()));
            } else if (arg.startsWith("--sensor-count=")) {
                config.setSensorCount(Integer.parseInt(arg.substring("--sensor-count=".length())));
            } else if (arg.startsWith("--min-interval=")) {
                config.setMinIntervalSeconds(Integer.parseInt(arg.substring("--min-interval=".length())));
            } else if (arg.startsWith("--max-interval=")) {
                config.setMaxIntervalSeconds(Integer.parseInt(arg.substring("--max-interval=".length())));
            } else if (arg.startsWith("--emit-interval-ms=")) {
                config.setEmitIntervalMillis(Long.parseLong(arg.substring("--emit-interval-ms=".length())));
            } else if (arg.startsWith("--kafka-servers=")) {
                config.setKafkaBootstrapServers(arg.substring("--kafka-servers=".length()));
            } else if (arg.startsWith("--kafka-topic=")) {
                config.setKafkaTopic(arg.substring("--kafka-topic=".length()));
            } else if (arg.startsWith("--down-probability=")) {
                config.setDownProbability(Double.parseDouble(arg.substring("--down-probability=".length())));
            }
        }
        return config;
    }
}
