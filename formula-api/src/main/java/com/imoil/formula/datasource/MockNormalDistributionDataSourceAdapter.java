package com.imoil.formula.datasource;

import com.imoil.formula.domain.SensorData;
import com.imoil.formula.service.MockSensorCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Mock 데이터 소스 어댑터 (100개 센서 1시간 정규분포 시계열 데이터 제공).
 * TimeSeriesDataSourceConfig에서 @ConditionalOnProperty로 등록되어 주입됩니다.
 */
@Slf4j
@RequiredArgsConstructor
public class MockNormalDistributionDataSourceAdapter implements TimeSeriesDataSourceAdapter {

    private final MockSensorCatalogService catalogService;

    @Override
    public DataSourceType getDataSourceType() {
        return DataSourceType.MOCK;
    }

    @Override
    public List<SensorData> fetchTimeSeriesData(TimeSeriesQueryCriteria criteria) {
        log.info("Fetching TimeSeriesData via MockNormalDistributionDataSourceAdapter for sensor: {}", criteria.getSensorId());
        return catalogService.generateTimeSeries(criteria);
    }
}
