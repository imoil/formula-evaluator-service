package com.imoil.formula.service;

import com.imoil.formula.domain.SensorData;
import io.questdb.client.Sender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
public class QuestDBIngestionServiceTest {

    @Container
    static final GenericContainer<?> questDB = new GenericContainer<>(DockerImageName.parse("questdb/questdb:9.3.4"))
            .withExposedPorts(9000, 9009, 8812)
            .waitingFor(Wait.forHttp("/").forPort(9000));

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.3"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("questdb.client.url", () -> String.format("http::addr=%s:%d;", questDB.getHost(), questDB.getMappedPort(9000)));
        registry.add("spring.datasource.url", () -> String.format("jdbc:postgresql://%s:%d/qdb", questDB.getHost(), questDB.getMappedPort(8812)));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private QuestDBIngestionService ingestionService;

    @Autowired
    private Sender sender; // 스프링 컨텍스트 상의 Sender 의존성 검증용

    @Test
    void testBulkIngestion() throws Exception {
        int recordCount = 10000;
        List<SensorData> batch = new ArrayList<>();
        // 단위: 밀리초 (Milliseconds)
        long currentMillis = Instant.now().toEpochMilli();

        for (int i = 0; i < recordCount; i++) {
            batch.add(SensorData.builder()
                    .sensorId("sensor_" + (i % 10))
                    .timestamp(currentMillis + i) // 1밀리초 씩 증가 (데이터 순서화)
                    .value(Math.random() * 100)
                    .state(i % 4) // 0~3까지의 state 할당
                    .build());
        }

        // 배치 삽입 수행 - ILP 프로토콜 특성 상 In-memory buffer에 쓰고 Flash 수행
        ingestionService.ingestBatch(batch);

        // QuestDB 엔진이 비동기 커밋을 통해 디스크에 기록하는 시간적 지연 대기
        Thread.sleep(2000);

        // JDBC Wire 프로토콜을 통해 저장된 데이터 수 집계 및 올바르게 Count가 반영되었는지 검증
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/qdb", questDB.getHost(), questDB.getMappedPort(8812));
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "admin", "quest");
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery("SELECT count(*) FROM sensor_data");
            assertThat(rs.next()).isTrue();
            long count = rs.getLong(1);
            
            // 10000건 삽입이 정상적으로 되었는지 단언 (Assert)
            assertThat(count).isEqualTo(recordCount);
        }
    }
}
