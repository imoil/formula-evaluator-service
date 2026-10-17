package com.imoil.formula.service;

import com.imoil.formula.domain.SensorData;
import io.questdb.client.Sender;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

public class QuestDBIngestionServiceBenchmark {

    @Test
    public void benchmarkIngestBatch() {
        // Mock Sender to avoid needing a real QuestDB instance
        Sender sender = mock(Sender.class, RETURNS_DEEP_STUBS);
        QuestDBIngestionService service = new QuestDBIngestionService(sender);

        int recordCount = 1000;
        List<SensorData> batch = new ArrayList<>();
        for (int i = 0; i < recordCount; i++) {
            batch.add(SensorData.builder()
                    .sensorId("s" + i)
                    .timestamp(System.currentTimeMillis())
                    .value(i)
                    .state(0)
                    .build());
        }

        // Warm up
        for (int i = 0; i < 2; i++) {
            service.ingestBatch(batch);
        }

        long start = System.nanoTime();
        int iterations = 5;
        for (int i = 0; i < iterations; i++) {
            service.ingestBatch(batch);
        }
        long end = System.nanoTime();

        System.out.println("Average time for ingestBatch (1k records): " + ((end - start) / iterations / 1_000_000.0) + " ms");
    }
}
