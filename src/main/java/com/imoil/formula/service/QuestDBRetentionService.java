package com.imoil.formula.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * QuestDB의 장점인 파티션 삭제(DROP PARTITION)를 통해 
 * 오래된 시계열 데이터의 디스크 점유율을 자동으로 정리하여 상태를 최적화하는 보존 정책 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestDBRetentionService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 매일 새벽 3시에 실행되어, 30일이 지난 데이터 파티션을 시스템에서 날립니다.
     * QuestDB는 TRUNCATE보다 DROP PARTITION의 비용이 0에 가깝기 때문에 매우 효율적입니다.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void dropOldPartitions() {
        // 기준점: 30일 이전
        LocalDateTime targetDate = LocalDateTime.now().minusDays(30);
        String partitionName = targetDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        try {
            log.info("Executing cleanup target. Trying to drop QuestDB partition: '{}'", partitionName);
            // QuestDB 전용 DDL: 파티션 드롭 구문 사용 
            jdbcTemplate.execute("ALTER TABLE sensor_data DROP PARTITION LIST '" + partitionName + "'");
            log.info("Successfully dropped partition for '{}'.", partitionName);
        } catch (Exception e) {
            // 파티션이 아직 존재하지 않는 경우 등에 대한 예외를 허용
            log.warn("Notice during partition drop for '{}'. It may not exist. Reason: {}", partitionName, e.getMessage());
        }
    }
}
