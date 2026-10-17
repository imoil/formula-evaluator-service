package com.imoil.formula.repository;

import com.imoil.formula.domain.SensorData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestDBQueryRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ResultSet resultSet;

    private QuestDBQueryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new QuestDBQueryRepository(jdbcTemplate);
    }

    @SuppressWarnings("unchecked")
    @Test
    void mapRowToSensorData_ShouldReturnFalse_WhenInaccurateColumnThrowsSQLException() throws SQLException {
        // Arrange
        String sensorId = "sensor1";
        long now = System.currentTimeMillis();
        when(resultSet.getTimestamp("timestamp")).thenReturn(new Timestamp(now));
        when(resultSet.getDouble("v")).thenReturn(10.5);
        when(resultSet.getInt("s")).thenReturn(1);
        when(resultSet.getInt("inaccurate")).thenThrow(new SQLException("Column not found"));

        ArgumentCaptor<RowMapper<SensorData>> rowMapperCaptor = ArgumentCaptor.forClass(RowMapper.class);

        // Act
        repository.fetchAggregatedData(sensorId, "2023-01-01T00:00:00Z", "2023-01-01T01:00:00Z", "1h");

        // Capture the RowMapper from the jdbcTemplate.query call
        // The signature is query(String, RowMapper, Object...)
        verify(jdbcTemplate).query(anyString(), rowMapperCaptor.capture(), any(), any(), any());
        RowMapper<SensorData> rowMapper = rowMapperCaptor.getValue();

        SensorData result = rowMapper.mapRow(resultSet, 0);

        // Assert
        assertThat(result.getSensorId()).isEqualTo(sensorId);
        assertThat(result.getTimestamp()).isEqualTo(now);
        assertThat(result.getValue()).isEqualTo(10.5);
        assertThat(result.getState()).isEqualTo(1);
        assertThat(result.isHasInaccurateData()).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    void mapRowToSensorData_ShouldReturnTrue_WhenInaccurateColumnIsPositive() throws SQLException {
        // Arrange
        String sensorId = "sensor1";
        long now = System.currentTimeMillis();
        when(resultSet.getTimestamp("timestamp")).thenReturn(new Timestamp(now));
        when(resultSet.getDouble("v")).thenReturn(10.5);
        when(resultSet.getInt("s")).thenReturn(1);
        when(resultSet.getInt("inaccurate")).thenReturn(1);

        ArgumentCaptor<RowMapper<SensorData>> rowMapperCaptor = ArgumentCaptor.forClass(RowMapper.class);

        // Act
        repository.fetchAggregatedData(sensorId, "2023-01-01T00:00:00Z", "2023-01-01T01:00:00Z", "1h");

        // Capture the RowMapper
        verify(jdbcTemplate).query(anyString(), rowMapperCaptor.capture(), any(), any(), any());
        RowMapper<SensorData> rowMapper = rowMapperCaptor.getValue();

        SensorData result = rowMapper.mapRow(resultSet, 0);

        // Assert
        assertThat(result.isHasInaccurateData()).isTrue();
    }
}
