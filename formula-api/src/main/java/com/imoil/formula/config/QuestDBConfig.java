package com.imoil.formula.config;

import io.questdb.client.Sender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import java.lang.reflect.Proxy;

/**
 * QuestDB InfluxDB Line Protocol (ILP) Client Configuration.
 * 
 * Defines a shared Sender bean for highly concurrent and buffered insertions.
 * If QuestDB is not running (e.g. standalone local mock mode), provides a no-op fallback
 * sender to allow Spring Boot to start without external infrastructure.
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
        try {
            this.sender = Sender.fromConfig(questDbUrl);
            return this.sender;
        } catch (Exception e) {
            log.warn("Failed to connect to QuestDB ILP at {}: {}. Initializing no-op fallback Sender for standalone/mock mode.", questDbUrl, e.getMessage());
            this.sender = (Sender) Proxy.newProxyInstance(
                    Sender.class.getClassLoader(),
                    new Class<?>[]{Sender.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("hashCode".equals(name)) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(name)) {
                            return proxy == (args != null && args.length > 0 ? args[0] : null);
                        }
                        if ("toString".equals(name)) {
                            return "NoOpQuestDBSenderProxy";
                        }
                        if (Sender.class.isAssignableFrom(method.getReturnType())) {
                            return proxy;
                        }
                        return null;
                    });
            return this.sender;
        }
    }

    @PreDestroy
    public void closeSender() {
        if (this.sender != null) {
            try {
                log.info("Closing QuestDB ILP Sender...");
                this.sender.close();
            } catch (Exception e) {
                log.debug("Error closing Sender: {}", e.getMessage());
            }
        }
    }
}
