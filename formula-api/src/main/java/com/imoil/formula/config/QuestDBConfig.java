package com.imoil.formula.config;

import com.imoil.formula.domain.SensorDataWriter;
import io.questdb.client.Sender;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QuestDB InfluxDB Line Protocol (ILP) Client Configuration.
 *
 * Defines a shared {@link SensorDataWriter} bean for highly concurrent and buffered insertions.
 * If QuestDB is not running (e.g. standalone local mock mode), provides a clean No-Op fallback
 * writer to allow Spring Boot to start without external infrastructure or reflection proxies.
 */
@Slf4j
@Configuration
public class QuestDBConfig {

    @Value("${questdb.client.url}")
    private String questDbUrl;

    private SensorDataWriter sensorDataWriter;

    @Bean
    public SensorDataWriter sensorDataWriter() {
        log.info("Initializing QuestDB ILP SensorDataWriter with config: {}", questDbUrl);
        try {
            this.sensorDataWriter = SensorDataWriter.forQuestDbSender(Sender.fromConfig(questDbUrl));
        } catch (Exception e) {
            log.warn("Failed to connect to QuestDB ILP at {}: {}. Initializing no-op SensorDataWriter for standalone/mock mode.",
                    questDbUrl, e.getMessage());
            this.sensorDataWriter = SensorDataWriter.noOp();
        }
        return this.sensorDataWriter;
    }

    @PreDestroy
    public void closeWriter() {
        if (this.sensorDataWriter != null) {
            try {
                log.info("Closing QuestDB SensorDataWriter...");
                this.sensorDataWriter.close();
            } catch (Exception e) {
                log.debug("Error closing SensorDataWriter: {}", e.getMessage());
            }
        }
    }
}
