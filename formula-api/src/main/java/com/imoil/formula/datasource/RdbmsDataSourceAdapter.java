package com.imoil.formula.datasource;

import com.imoil.formula.domain.SensorData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Oracle / PostgreSQL / MySQL 등 범용 RDBMS 연동을 위한 시계열 어댑터
 */
@Slf4j
@RequiredArgsConstructor
public class RdbmsDataSourceAdapter implements TimeSeriesDataSourceAdapter {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public DataSourceType getDataSourceType() {
        return DataSourceType.RDBMS;
    }

    @Override
    public List<SensorData> fetchTimeSeriesData(TimeSeriesQueryCriteria criteria) {
        log.info("Fetching time series from RDBMS for sensor: {}, range=[{}, {}]", 
                criteria.getSensorId(), criteria.getStartTime(), criteria.getEndTime());

        String sql = """
            SELECT 
                sensor_id,
                timestamp,
                value,
                state,
                has_inaccurate_data
            FROM sensor_data_history
            WHERE sensor_id = ?
              AND timestamp BETWEEN ? AND ?
            ORDER BY timestamp ASC
            FETCH FIRST ? ROWS ONLY
            """;

        try {
            return jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> mapRowToSensorData(rs),
                    criteria.getSensorId(),
                    criteria.getStartTime(),
                    criteria.getEndTime(),
                    criteria.getLimit()
            );
        } catch (Exception e) {
            log.warn("Failed to query RDBMS history table (table might not exist yet): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private SensorData mapRowToSensorData(ResultSet rs) throws SQLException {
        long timestampMillis;
        try {
            timestampMillis = rs.getTimestamp("timestamp").getTime();
        } catch (Exception e) {
            timestampMillis = rs.getLong("timestamp");
        }

        return SensorData.builder()
                .sensorId(rs.getString("sensor_id"))
                .timestamp(timestampMillis)
                .value(rs.getDouble("value"))
                .state(rs.getInt("state"))
                .hasInaccurateData(rs.getBoolean("has_inaccurate_data"))
                .build();
    }
}
