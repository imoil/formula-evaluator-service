package com.imoil.formula.datasource;

import com.imoil.formula.domain.SensorData;
import java.util.List;

/**
 * 다양한 이기종 데이터 소스(QuestDB, Oracle/RDBMS, NATS 스트리밍 버퍼 등)로부터
 * 시계열 센서 데이터를 조회하기 위한 최상위 추상화 인터페이스
 */
public interface TimeSeriesDataSourceAdapter {

    /**
     * 어댑터가 지원하는 데이터 소스 유형 반환
     */
    DataSourceType getDataSourceType();

    /**
     * 특정 질의 조건(센서 ID, 시간 범위, 샘플링 주기 등)에 맞게 시계열 데이터를 조회
     *
     * @param criteria 시계열 쿼리 조건 DTO
     * @return 정규화된 SensorData 리스트
     */
    List<SensorData> fetchTimeSeriesData(TimeSeriesQueryCriteria criteria);
}
