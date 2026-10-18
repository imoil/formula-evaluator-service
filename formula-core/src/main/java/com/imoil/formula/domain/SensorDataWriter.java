package com.imoil.formula.domain;

import io.questdb.client.Sender;

import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 시계열 센서 데이터(SensorData)를 외부 TSDB(QuestDB ILP 등)에 기록하기 위한 도메인 스키마 규약 및 포트.
 * formula-api와 formula-flink 전반에서 테이블/컬럼명, 나노초 타임스탬프 변환 불변식 및
 * 운영(ILP Sender) / 독립 실행(No-Op) 어댑터를 단일화합니다.
 */
public interface SensorDataWriter extends AutoCloseable {

    String TABLE_SENSOR_DATA = "sensor_data";
    String COL_SENSOR_ID = "sensor_id";
    String COL_DEVICE_ID = "device_id";
    String COL_SENSOR_INDEX = "sensor_index";
    String COL_STATE = "state";
    String COL_VALUE = "value";
    String COL_HAS_INACCURATE_DATA = "has_inaccurate_data";

    long NANOS_PER_MILLI = 1_000_000L;

    static long toEpochNanos(long epochMillis) {
        return epochMillis * NANOS_PER_MILLI;
    }

    void write(SensorData data);

    default void writeBatch(List<SensorData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return;
        }
        for (SensorData data : dataList) {
            write(data);
        }
        flush();
    }

    default void flush() {
    }

    @Override
    default void close() {
    }

    /**
     * 외부 QuestDB 인프라가 없는 로컬 단독 실행/테스트 환경을 위한 No-Op 어댑터
     */
    static SensorDataWriter noOp() {
        return NoOpSensorDataWriter.INSTANCE;
    }

    /**
     * QuestDB 공식 ILP Sender를 감싸는 스레드 안전 어댑터
     */
    static SensorDataWriter forQuestDbSender(Sender sender) {
        if (sender == null) {
            return noOp();
        }
        return new QuestDbIlpSensorDataWriter(sender);
    }

    enum NoOpSensorDataWriter implements SensorDataWriter {
        INSTANCE;

        @Override
        public void write(SensorData data) {
            // no-op in standalone/mock mode
        }

        @Override
        public void writeBatch(List<SensorData> dataList) {
            // no-op in standalone/mock mode
        }
    }

    final class QuestDbIlpSensorDataWriter implements SensorDataWriter {
        private final Sender sender;

        public QuestDbIlpSensorDataWriter(Sender sender) {
            this.sender = sender;
        }

        @Override
        public void write(SensorData data) {
            if (data == null) {
                return;
            }
            synchronized (sender) {
                writeRowUnlocked(data);
            }
        }

        @Override
        public void writeBatch(List<SensorData> dataList) {
            if (dataList == null || dataList.isEmpty()) {
                return;
            }
            synchronized (sender) {
                for (SensorData data : dataList) {
                    if (data != null) {
                        writeRowUnlocked(data);
                    }
                }
                sender.flush();
            }
        }

        @Override
        public void flush() {
            synchronized (sender) {
                sender.flush();
            }
        }

        @Override
        public void close() {
            synchronized (sender) {
                sender.close();
            }
        }

        private void writeRowUnlocked(SensorData data) {
            sender.table(TABLE_SENSOR_DATA)
                    .symbol(COL_SENSOR_ID, data.getSensorId() != null ? data.getSensorId() : "");

            if (data.getDeviceId() != null && !data.getDeviceId().isBlank()) {
                sender.symbol(COL_DEVICE_ID, data.getDeviceId());
            }

            if (data.getSensorIndex() != null) {
                sender.symbol(COL_SENSOR_INDEX, String.valueOf(data.getSensorIndex()));
            }

            sender.longColumn(COL_STATE, data.getState())
                    .doubleColumn(COL_VALUE, data.getValue())
                    .boolColumn(COL_HAS_INACCURATE_DATA, data.isHasInaccurateData())
                    .at(toEpochNanos(data.getTimestamp()), ChronoUnit.NANOS);
        }
    }
}
