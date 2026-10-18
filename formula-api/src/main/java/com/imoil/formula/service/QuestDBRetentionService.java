package com.imoil.formula.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * QuestDB의 장점인 파티션 삭제(DROP PARTITION)를 통해 
 * 오래된 시계열 데이터의 디스크 점유율을 자동으로 정리하여 상태를 최적화하는 보존 정책 서비스입니다.
 */
@Slf4j
@Service
public class QuestDBRetentionService {

    private final JdbcTemplate jdbcTemplate;
    private final String questDbHttpUrl;
    private final HttpClient httpClient;

    @Autowired
    public QuestDBRetentionService(
            @Autowired(required = false) @Qualifier("questDbJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Value("${questdb.client.url:http::addr=localhost:9000;}") String questDbUrl) {
        this.jdbcTemplate = jdbcTemplate;
        String httpUrl = "http://localhost:9000";
        if (questDbUrl != null && questDbUrl.contains("addr=")) {
            String addrPart = questDbUrl.substring(questDbUrl.indexOf("addr=") + 5);
            int semi = addrPart.indexOf(';');
            if (semi > 0) {
                addrPart = addrPart.substring(0, semi);
            }
            httpUrl = "http://" + addrPart;
        }
        this.questDbHttpUrl = httpUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public QuestDBRetentionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.questDbHttpUrl = null;
        this.httpClient = null;
    }

    /**
     * 매일 새벽 3시에 실행되어, 30일이 지난 데이터 파티션을 시스템에서 날립니다.
     * QuestDB는 TRUNCATE보다 DROP PARTITION의 비용이 0에 가깝기 때문에 매우 효율적입니다.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void dropOldPartitions() {
        // 기준점: 30일 이전
        LocalDateTime targetDate = LocalDateTime.now().minusDays(30);
        String partitionName = targetDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // SQL 인젝션 방지를 위한 파티션 명칭 검증 (yyyy-MM-dd 형식)
        if (!partitionName.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            log.error("Invalid partition name format: '{}'. Aborting drop partition.", partitionName);
            return;
        }

        String ddl = "ALTER TABLE sensor_data DROP PARTITION LIST '" + partitionName + "'";
        try {
            log.info("Executing cleanup target. Trying to drop QuestDB partition: '{}'", partitionName);
            if (isQuestDbCompatibleJdbc(jdbcTemplate)) {
                jdbcTemplate.execute(ddl);
                log.info("Successfully dropped partition via JDBC for '{}'.", partitionName);
            } else if (questDbHttpUrl != null && !questDbHttpUrl.isBlank() && httpClient != null) {
                try {
                    String encoded = URLEncoder.encode(ddl, StandardCharsets.UTF_8);
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(questDbHttpUrl + "/exec?query=" + encoded))
                            .timeout(Duration.ofSeconds(3))
                            .GET()
                            .build();
                    HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        log.info("Successfully dropped QuestDB partition via HTTP for '{}'.", partitionName);
                    }
                } catch (Exception e) {
                    log.debug("QuestDB HTTP drop partition failed for '{}': {}", partitionName, e.getMessage());
                }
            }
        } catch (Exception e) {
            // 파티션이 아직 존재하지 않는 경우 등에 대한 예외를 허용
            log.warn("Notice during partition drop for '{}'. It may not exist. Reason: {}", partitionName, e.getMessage());
        }
    }

    private boolean isQuestDbCompatibleJdbc(JdbcTemplate template) {
        if (template == null) {
            return false;
        }
        try {
            if (template.getDataSource() != null) {
                try (var conn = template.getDataSource().getConnection()) {
                    String dbProduct = conn.getMetaData().getDatabaseProductName();
                    if (dbProduct != null && dbProduct.toLowerCase().contains("oracle")) {
                        log.warn("Detected Oracle DataSource injected into QuestDBRetentionService. Skipping DDL execution against Oracle.");
                        return false;
                    }
                }
            }
        } catch (Exception e) {
            // Mock jdbcTemplate in unit tests will throw NPE or not have connection
            return true;
        }
        return true;
    }
}
