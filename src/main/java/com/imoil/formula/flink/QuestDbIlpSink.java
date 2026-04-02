package com.imoil.formula.flink;

import com.imoil.formula.domain.SensorData;
import io.questdb.client.Sender;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

/**
 * 비동기 배치 처리를 지원하는 QuestDB ILP 공식 클라이언트 기반의 커스텀 Sink.
 */
public class QuestDbIlpSink extends RichSinkFunction<SensorData> {

    private transient Sender sender;
    private final String url;

    public QuestDbIlpSink(String url) {
        this.url = url;
    }

    @Override
    public void open(Configuration parameters) {
        this.sender = Sender.fromConfig(url);
    }

    @Override
    public void invoke(SensorData value, Context context) {
        sender.table("sensor_data")
                .symbol("sensor_id", value.getSensorId())
                .longColumn("state", value.getState())
                .doubleColumn("value", value.getValue())
                .at(java.time.Instant.ofEpochMilli(value.getTimestamp()));
    }

    @Override
    public void close() {
        if (sender != null) {
            sender.flush(); // 클러스터 닫힐 때 잔여 버퍼 플러시
            sender.close();
        }
    }
}
