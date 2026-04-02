package com.imoil.formula.config;

import io.questdb.client.Sender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * QuestDB InfluxDB Line Protocol (ILP) Client Configuration.
 * 
 * Defines a shared Sender bean for highly concurrent and buffered insertions
 * avoiding JPA performance bottlenecks.
 */
@Slf4j
@Configuration
public class QuestDBConfig {

    @Value("${questdb.client.url}")
    private String questDbUrl;

    private Sender sender;

    @Bean
    public Sender questDbSender() {
        log.info("Initializing QuestDB ILP Sender with config: {}", questDbUrl);
        // Sender fromConfig builds the client instance connecting natively
        this.sender = Sender.fromConfig(questDbUrl);
        return this.sender;
    }

    @PreDestroy
    public void closeSender() {
        if (this.sender != null) {
            log.info("Closing QuestDB ILP Sender...");
            this.sender.close();
        }
    }
}
