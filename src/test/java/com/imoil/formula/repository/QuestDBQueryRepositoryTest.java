package com.imoil.formula.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QuestDBQueryRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private QuestDBQueryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new QuestDBQueryRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("Verify fix: sampleBy parameter is protected against SQL Injection")
    void testSampleBySqlInjectionProtection() {
        // Given
        String sensorId = "sensor1";
        String startTime = "2023-01-01T00:00:00.000Z";
        String endTime = "2023-01-01T01:00:00.000Z";
        // Malicious sampleBy input
        String maliciousSampleBy = "1m) FILL(PREV); DROP TABLE sensor_data; --";

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            repository.fetchAggregatedData(sensorId, startTime, endTime, maliciousSampleBy)
        );
    }

    @Test
    @DisplayName("Verify fix: invalid sampleBy throws IllegalArgumentException")
    void testInvalidSampleByThrowsException() {
        // Given
        String sensorId = "sensor1";
        String startTime = "2023-01-01T00:00:00.000Z";
        String endTime = "2023-01-01T01:00:00.000Z";
        String maliciousSampleBy = "1m) FILL(PREV); DROP TABLE sensor_data; --";

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            repository.fetchAggregatedData(sensorId, startTime, endTime, maliciousSampleBy)
        );
    }

    @Test
    @DisplayName("Verify fix: valid sampleBy works correctly")
    void testValidSampleByWorks() {
        // Given
        String sensorId = "sensor1";
        String startTime = "2023-01-01T00:00:00.000Z";
        String endTime = "2023-01-01T01:00:00.000Z";
        String validSampleBy = "5m";

        // When
        repository.fetchAggregatedData(sensorId, startTime, endTime, validSampleBy);

        // Then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), anyString(), anyString(), anyString());
        assertThat(sqlCaptor.getValue()).contains("SAMPLE BY 5m FILL(PREV)");
    }
}
