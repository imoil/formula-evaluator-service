package com.imoil.formula.repository;

import com.imoil.formula.datasource.QuestDBDataSourceAdapter;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QuestDBQueryRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private QuestDBDataSourceAdapter repository;

    @BeforeEach
    void setUp() {
        repository = new QuestDBDataSourceAdapter(jdbcTemplate);
    }

    @Test
    void fetchAggregatedData_WithValidSampleBy_ShouldSucceed() {
        // Given
        String sensorId = "sensor1";
        String startTime = "2023-01-01T00:00:00Z";
        String endTime = "2023-01-01T01:00:00Z";
        String sampleBy = "1m";

        // When
        repository.fetchAggregatedData(sensorId, startTime, endTime, sampleBy);

        // Then
        verify(jdbcTemplate).query(
                anyString(),
                any(RowMapper.class),
                eq(sensorId), eq(startTime), eq(endTime)
        );
    }

    @Test
    void fetchAggregatedData_WithInvalidSampleBy_ShouldThrowException() {
        // Given
        String sensorId = "sensor1";
        String startTime = "2023-01-01T00:00:00Z";
        String endTime = "2023-01-01T01:00:00Z";
        String invalidSampleBy = "1m; DROP TABLE sensor_data";

        // When & Then
        assertThatThrownBy(() -> repository.fetchAggregatedData(sensorId, startTime, endTime, invalidSampleBy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sampleBy duration");
    }

    @Test
    void fetchAggregatedData_WithAnotherInvalidSampleBy_ShouldThrowException() {
        // Given
        String sensorId = "sensor1";
        String startTime = "2023-01-01T00:00:00Z";
        String endTime = "2023-01-01T01:00:00Z";
        String invalidSampleBy = "0m";

        // When & Then
        assertThatThrownBy(() -> repository.fetchAggregatedData(sensorId, startTime, endTime, invalidSampleBy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sampleBy duration");
    }

    @Test
    @DisplayName("Verify fix: sampleBy parameter is protected against SQL Injection")
    void testSampleBySqlInjectionProtection() {
        // Given
        String sensorId = "sensor1";
        String startTime = "2023-01-01T00:00:00.000Z";
        String endTime = "2023-01-01T01:00:00.000Z";
        String maliciousSampleBy = "1m) FILL(PREV); DROP TABLE sensor_data; --";

        // When & Then
        assertThatThrownBy(() -> repository.fetchAggregatedData(sensorId, startTime, endTime, maliciousSampleBy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sampleBy duration");
    }

    @Test
    @DisplayName("Verify fix: valid sampleBy works correctly and formats SQL")
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

    @Test
    void fetchAggregatedData_WithValidDurations_ShouldSucceed() {
        String[] validDurations = {"1s", "5m", "1h", "2d", "1w", "500ms", "1M", "1y"};
        for (String duration : validDurations) {
            repository.fetchAggregatedData("s1", "start", "end", duration);
        }
    }
}
