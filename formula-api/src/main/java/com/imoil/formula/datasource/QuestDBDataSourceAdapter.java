package com.imoil.formula.datasource;

import com.imoil.formula.domain.SensorData;
import com.imoil.formula.repository.QuestDBQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * QuestDB 전용 시계열 데이터 소스 어댑터
 */
@Slf4j
@RequiredArgsConstructor
public class QuestDBDataSourceAdapter implements TimeSeriesDataSourceAdapter {

    private final QuestDBQueryRepository questDBQueryRepository;

    @Override
    public DataSourceType getDataSourceType() {
        return DataSourceType.QUESTDB;
    }

    @Override
    public List<SensorData> fetchTimeSeriesData(TimeSeriesQueryCriteria criteria) {
        log.debug("Fetching time series from QuestDB for sensor: {}", criteria.getSensorId());
        return questDBQueryRepository.fetchAggregatedData(
                criteria.getSensorId(),
                criteria.getStartTime(),
                criteria.getEndTime(),
                criteria.getSampleBy()
        );
    }
}
