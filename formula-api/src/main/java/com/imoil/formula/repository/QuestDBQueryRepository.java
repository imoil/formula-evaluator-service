package com.imoil.formula.repository;

import com.imoil.formula.domain.SensorData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Repository
@RequiredArgsConstructor
public class QuestDBQueryRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private static final Pattern SAMPLE_BY_PATTERN = Pattern.compile("^[0-9]+(s|m|h|d)$");

    /**
     * QuestDB 전용 시계열 집계 다운샘플링 확장 구문(SAMPLE BY)을 활용한 고속 추출 
     */
    public List<SensorData> fetchAggregatedData(String sensorId, String startTime, String endTime, String sampleBy) {
        // SQL 인젝션 방어: sampleBy 포맷 검증 (예: 1s, 5m, 1h, 1d)
        if (sampleBy == null || !SAMPLE_BY_PATTERN.matcher(sampleBy).matches()) {
            log.warn("Invalid sampleBy value: {}. Falling back to default '1m'.", sampleBy);
            sampleBy = "1m";
        }
        
        // QuestDB는 PGWire를 통해 통신하므로 JdbcTemplate 활용 가능
        // FILL(PREV) 를 통해 비어있는 시간대 보관
        // has_inaccurate_data 컬럼이 boolean 타입이므로, 그룹 연산 시 정수형으로 변환 후 max를 사용하여
        // 하나라도 부정확한 데이터가 포함되어 있다면 1(true)이 되도록 처리합니다.
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

        // PreparedStatement Bind 방식을 통해 SQL 인젝션 방지
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
