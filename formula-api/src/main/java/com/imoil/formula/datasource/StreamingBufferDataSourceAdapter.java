package com.imoil.formula.datasource;

import com.imoil.formula.domain.SensorData;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * NATS / Kafka / MQTT 등 실시간 스트리밍 버퍼 어댑터
 */
@Slf4j
public class StreamingBufferDataSourceAdapter implements TimeSeriesDataSourceAdapter {

    private final Map<String, Deque<SensorData>> streamBuffer = new ConcurrentHashMap<>();
    private static final int MAX_BUFFER_CAPACITY = 2000;

    @Override
    public DataSourceType getDataSourceType() {
        return DataSourceType.STREAMING;
    }

    public void pushStreamData(SensorData data) {
        if (data == null || data.getSensorId() == null) {
            return;
        }
        streamBuffer.computeIfAbsent(data.getSensorId(), k -> new ConcurrentLinkedDeque<>()).addLast(data);
        Deque<SensorData> deque = streamBuffer.get(data.getSensorId());
        while (deque.size() > MAX_BUFFER_CAPACITY) {
            deque.pollFirst();
        }
    }

    @Override
    public List<SensorData> fetchTimeSeriesData(TimeSeriesQueryCriteria criteria) {
        log.info("Fetching time series from Streaming Buffer for sensor: {}", criteria.getSensorId());
        Deque<SensorData> deque = streamBuffer.get(criteria.getSensorId());
        if (deque == null || deque.isEmpty()) {
            return Collections.emptyList();
        }

        return deque.stream()
                .limit(criteria.getLimit())
                .collect(Collectors.toList());
    }
}
