package com.imoil.formula.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QuestDBRetentionServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private QuestDBRetentionService retentionService;

    @Test
    void dropOldPartitions_ShouldHandleException() {
        // Mock to throw exception
        doThrow(new RuntimeException("Partition not found")).when(jdbcTemplate).execute(anyString());

        // Should not throw exception
        retentionService.dropOldPartitions();

        // Verify execute was called
        verify(jdbcTemplate).execute(anyString());
    }

    @Test
    void dropOldPartitions_ShouldExecuteCorrectSql() {
        // Given: Use a fixed point in time to avoid "midnight flake"
        LocalDateTime fixedNow = LocalDateTime.of(2025, 3, 1, 10, 0);
        LocalDateTime targetDate = fixedNow.minusDays(30);
        String expectedPartitionName = targetDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String expectedSql = "ALTER TABLE sensor_data DROP PARTITION LIST '" + expectedPartitionName + "'";

        try (MockedStatic<LocalDateTime> mockedLocalDateTime = mockStatic(LocalDateTime.class)) {
            mockedLocalDateTime.when(LocalDateTime::now).thenReturn(fixedNow);

            // When
            retentionService.dropOldPartitions();

            // Then
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).execute(sqlCaptor.capture());
            assertThat(sqlCaptor.getValue()).isEqualTo(expectedSql);
        }
    }
}
