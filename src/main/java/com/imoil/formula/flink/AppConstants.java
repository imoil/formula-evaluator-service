package com.imoil.formula.flink;

public final class AppConstants {
    private AppConstants() {}

    public static final String KAFKA_BOOTSTRAP_SERVERS_DEFAULT = "localhost:9092";
    public static final String KAFKA_TOPIC_SENSOR_DATA_DEFAULT = "sensor-data";
    public static final String KAFKA_TOPIC_RULE_DATA_DEFAULT = "rule-data";
    public static final String KAFKA_GROUP_ID_SENSOR_DEFAULT = "flink-sensor-group";
    public static final String KAFKA_GROUP_ID_RULE_DEFAULT = "flink-rule-group";

    public static final long CHECKPOINT_INTERVAL_MS = 10000L;
    public static final int TOLERABLE_CHECKPOINT_FAILURE_NUMBER = 2;

    public static final long RETENTION_TIME_MINUTES_DEFAULT = 30L;
    public static final String QUESTDB_URL_DEFAULT = "http::addr=localhost:9000;auto_flush_interval=1000;auto_flush_rows=100000;";
}
