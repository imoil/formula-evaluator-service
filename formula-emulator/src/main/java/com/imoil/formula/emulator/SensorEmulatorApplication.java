package com.imoil.formula.emulator;

import com.imoil.formula.emulator.config.EmulatorConfig;
import com.imoil.formula.emulator.engine.SensorDeviceEmulator;
import com.imoil.formula.emulator.kafka.SensorKafkaProducer;
import lombok.extern.slf4j.Slf4j;

/**
 * IoT 센서 장치 에뮬레이터 실행 메인 애플리케이션
 */
@Slf4j
public class SensorEmulatorApplication {

    public static void main(String[] args) {
        EmulatorConfig config = EmulatorConfig.fromArgs(args);

        log.info("==================================================");
        log.info("  Starting IoT Sensor Device Emulator");
        log.info("  Device ID           : {}", config.getDeviceId());
        log.info("  Sensor Count        : {}", config.getSensorCount());
        log.info("  Change Interval     : {}s ~ {}s", config.getMinIntervalSeconds(), config.getMaxIntervalSeconds());
        log.info("  Emit Interval       : {}ms", config.getEmitIntervalMillis());
        log.info("  Kafka Topic         : {}", config.getKafkaTopic());
        log.info("  Kafka Brokers       : {}", config.getKafkaBootstrapServers());
        log.info("  Down Probability    : {}", config.getDownProbability());
        log.info("==================================================");

        SensorKafkaProducer producer = new SensorKafkaProducer(config);
        SensorDeviceEmulator emulator = new SensorDeviceEmulator(config, producer);

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received, stopping emulator...");
            emulator.close();
            log.info("Emulator safely stopped.");
        }, "emulator-shutdown-hook"));

        emulator.start();

        // 메인 스레드 유지
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            log.info("Main thread interrupted, exiting...");
            Thread.currentThread().interrupt();
        }
    }
}
