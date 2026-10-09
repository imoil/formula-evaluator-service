package com.imoil.formula.datasource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 온디맨드 데이터 소스 조회를 위한 통합 요청 파라미터 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesQueryCriteria {
    private String sensorId;
    private String startTime;
    private String endTime;
    @Builder.Default
    private String sampleBy = "1m";
    @Builder.Default
    private int limit = 1000;
}
