package com.imoil.formula.service;

import com.imoil.formula.domain.SensorData;
import io.questdb.client.Sender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestDBIngestionService {

    private final Sender sender;
    private static final String TABLE_NAME = "sensor_data";

    /**
     * 센서 데이터를 QuestDB 보낼 ILP 버퍼에 기록합니다.
     * 주의: 이 메서드는 동기식으로 즉각적 Flush를 요청하지 않습니다. 
     * 내부 버퍼가 차거나 수동으로 플러시할 때 통신이 시작됩니다.
     */
    public void ingest(SensorData data) {
        // 단일 Sender 빈을 여러 REST 스레드가 사용할 때의 버퍼 오프셋 꼬임(Non Thread-Safe) 방지
        synchronized (sender) {
            sender.table(TABLE_NAME)
                    .symbol("sensor_id", data.getSensorId()) // High-cardinality 인덱스
                    .longColumn("state", data.getState())
                    .doubleColumn("value", data.getValue())
                    .at(java.time.Instant.ofEpochMilli(data.getTimestamp()));
        }
    }

    /**
     * 여러 센서 데이터를 일괄적으로 버퍼에 쌓은 후 즉시 플러시(Flush) 합니다.
     * 네트워크 왕복을 아끼기 위한 배치 스트리밍에 유리합니다.
     *
     * @param dataList 배치의 단위인 리스트
     */
    public void ingestBatch(List<SensorData> dataList) {
        synchronized (sender) {
            for (SensorData data : dataList) {
                ingest(data);
            }
            // 버퍼에 쌓여 있는 데이터를 실질적으로 QuestDB 서버로 전송
            sender.flush();
        }
    }
}
