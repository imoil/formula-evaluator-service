package com.imoil.formula.repository;

import com.imoil.formula.domain.SensorData;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class QuestDBQueryRepository {
    
    private final JdbcTemplate jdbcTemplate;

    /**
     * QuestDB 전용 시계열 집계 다운샘플링 확장 구문(SAMPLE BY)을 활용한 고속 추출 
     */
    public List<SensorData> fetchAggregatedData(String sensorId, String startTime, String endTime, String sampleBy) {
        
        // QuestDB는 PGWire를 통해 통신하므로 JdbcTemplate 활용 가능
        // FILL(PREV) 를 통해 비어있는 시간대 보간
        String sql = """
            SELECT
                timestamp,
                avg(value) as v,
                max(state) as s
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
        // QuestDBTimestamp -> Millis 보정
        long timestampMillis = rs.getTimestamp("timestamp").getTime();
        return SensorData.builder()
                .sensorId(sensorId)
                .timestamp(timestampMillis)
                .value(rs.getDouble("v"))
                .state(rs.getInt("s"))
                .build();
    }
}
