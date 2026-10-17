package com.imoil.formula.config;

import com.imoil.formula.datasource.*;
import com.imoil.formula.repository.QuestDBQueryRepository;
import com.imoil.formula.service.MockSensorCatalogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * application.yml 설정(evaluation.datasource.type)에 따라 
 * 주입될 시계열 데이터 소스 어댑터 빈을 결정하는 구성 클래스.
 *
 * 지원 유형: QUESTDB, RDBMS, STREAMING, MOCK
 * 기본값: QUESTDB (누락 시에도 QUESTDB 어댑터 자동 등록)
 */
@Slf4j
@Configuration
public class TimeSeriesDataSourceConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "evaluation.datasource.type", havingValue = "QUESTDB", matchIfMissing = true)
    public TimeSeriesDataSourceAdapter questDbDataSourceAdapter(QuestDBQueryRepository questDBQueryRepository) {
        log.info("Configuring TimeSeriesDataSourceAdapter as [QUESTDB]");
        return new QuestDBDataSourceAdapter(questDBQueryRepository);
    }

    @Bean
    @ConditionalOnProperty(name = "evaluation.datasource.type", havingValue = "RDBMS")
    public TimeSeriesDataSourceAdapter rdbmsDataSourceAdapter(JdbcTemplate jdbcTemplate) {
        log.info("Configuring TimeSeriesDataSourceAdapter as [RDBMS (Oracle/PostgreSQL)]");
        return new RdbmsDataSourceAdapter(jdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "evaluation.datasource.type", havingValue = "STREAMING")
    public TimeSeriesDataSourceAdapter streamingBufferDataSourceAdapter() {
        log.info("Configuring TimeSeriesDataSourceAdapter as [STREAMING (NATS/In-Memory Buffer)]");
        return new StreamingBufferDataSourceAdapter();
    }

    @Bean
    @ConditionalOnProperty(name = "evaluation.datasource.type", havingValue = "MOCK")
    public TimeSeriesDataSourceAdapter mockDataSourceAdapter(MockSensorCatalogService catalogService) {
        log.info("Configuring TimeSeriesDataSourceAdapter as [MOCK (100 Sample Sensors, Normal Distribution)]");
        return new MockNormalDistributionDataSourceAdapter(catalogService);
    }
}
