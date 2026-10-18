package com.imoil.formula.datasource;

import com.imoil.formula.domain.SensorData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * QuestDB 전용 시계열 데이터 소스 어댑터.
 * SAMPLE BY 정규식 검증, PGWire SQL 질의 및 ResultSet 매핑을 단일 모듈 내부로 캡슐화합니다.
 */
@Slf4j
@RequiredArgsConstructor
public class QuestDBDataSourceAdapter implements TimeSeriesDataSourceAdapter {

    private static final Pattern SAMPLE_BY_PATTERN = Pattern.compile("^[1-9][0-9]*(?:s|m|h|d|w|ms|M|y)$");

    private final JdbcTemplate jdbcTemplate;

    @Override
    public DataSourceType getDataSourceType() {
        return DataSourceType.QUESTDB;
    }

    @Override
    public List<SensorData> fetchTimeSeriesData(TimeSeriesQueryCriteria criteria) {
        log.debug("Fetching time series from QuestDB for sensor: {}", criteria.getSensorId());
        String sampleBy = (criteria.getSampleBy() != null && !criteria.getSampleBy().isBlank())
                ? criteria.getSampleBy()
                : "1s";
        String startTime = (criteria.getStartTime() != null && !criteria.getStartTime().isBlank())
                ? criteria.getStartTime()
                : "2026-10-09T20:00:00Z";
        String endTime = (criteria.getEndTime() != null && !criteria.getEndTime().isBlank())
                ? criteria.getEndTime()
                : "2026-10-09T21:00:00Z";
        return fetchAggregatedData(
                criteria.getSensorId(),
                startTime,
                endTime,
                sampleBy
        );
    }

    /**
     * QuestDB 전용 시계열 집계 다운샘플링 확장 구문(SAMPLE BY)을 활용한 고속 추출
     */
    public List<SensorData> fetchAggregatedData(String sensorId, String startTime, String endTime, String sampleBy) {
        // SQL 인젝션 방지: SAMPLE BY 구문은 PreparedStatement Bind가 불가능하므로 엄격한 화이트리스트 검증 수행
        if (sampleBy == null || !SAMPLE_BY_PATTERN.matcher(sampleBy).matches()) {
            throw new IllegalArgumentException("Invalid sampleBy duration format: " + sampleBy);
        }

        String sql = """
            SELECT
                timestamp,
                avg(value) as v,
                max(state) as s,
                max(cast(has_inaccurate_data as int)) as inaccurate
            FROM sensor_data
            WHERE sensor_id = ?
              AND timestamp BETWEEN ? AND ?
            SAMPLE BY %s FILL(PREV)
            """.formatted(sampleBy);

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> mapRowToSensorData(rs, sensorId),
                sensorId, startTime, endTime
        );
    }

    private SensorData mapRowToSensorData(ResultSet rs, String sensorId) throws SQLException {
        long timestampMillis = rs.getTimestamp("timestamp").getTime();
        boolean hasInaccurateData = false;
        try {
            hasInaccurateData = rs.getInt("inaccurate") > 0;
        } catch (SQLException e) {
            // 컬럼이 없는 경우 무시
        }
        return SensorData.builder()
                .sensorId(sensorId)
                .timestamp(timestampMillis)
                .value(rs.getDouble("v"))
                .state(rs.getInt("s"))
                .hasInaccurateData(hasInaccurateData)
                .build();
    }
}

