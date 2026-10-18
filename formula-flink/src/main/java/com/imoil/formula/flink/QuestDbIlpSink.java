package com.imoil.formula.flink;

import com.imoil.formula.domain.SensorData;
import com.imoil.formula.domain.SensorDataWriter;
import io.questdb.client.Sender;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

/**
 * 비동기 배치 처리를 지원하는 QuestDB ILP 공식 클라이언트 기반의 커스텀 Sink.
 * 도메인 포트 {@link SensorDataWriter} 어댑터를 통해 스키마 및 나노초 변환 규약을 공유합니다.
 */
public class QuestDbIlpSink extends RichSinkFunction<SensorData> {

    private transient SensorDataWriter writer;
    private final String url;

    public QuestDbIlpSink(String url) {
        this.url = url;
    }

    @Override
    public void open(Configuration parameters) {
        this.writer = SensorDataWriter.forQuestDbSender(Sender.fromConfig(url));
    }

    @Override
    public void invoke(SensorData value, Context context) {
        writer.write(value);
    }

    @Override
    public void close() {
        if (writer != null) {
            writer.flush(); // 클러스터 닫힐 때 잔여 버퍼 플러시
            writer.close();
        }
    }
}
