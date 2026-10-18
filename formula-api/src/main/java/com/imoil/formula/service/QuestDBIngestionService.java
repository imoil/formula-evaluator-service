package com.imoil.formula.service;

import com.imoil.formula.domain.SensorData;
import com.imoil.formula.domain.SensorDataWriter;
import io.questdb.client.Sender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuestDBIngestionService {

    private final SensorDataWriter sensorDataWriter;

    @Autowired
    public QuestDBIngestionService(SensorDataWriter sensorDataWriter) {
        this.sensorDataWriter = sensorDataWriter;
    }

    public QuestDBIngestionService(Sender sender) {
        this(SensorDataWriter.forQuestDbSender(sender));
    }

    /**
     * 센서 데이터를 QuestDB ILP 버퍼에 기록합니다.
     */
    public void ingest(SensorData data) {
        sensorDataWriter.write(data);
    }

    /**
     * 여러 센서 데이터를 일괄적으로 버퍼에 쌓은 후 즉시 플러시(Flush) 합니다.
     */
    public void ingestBatch(List<SensorData> dataList) {
        sensorDataWriter.writeBatch(dataList);
    }
}
