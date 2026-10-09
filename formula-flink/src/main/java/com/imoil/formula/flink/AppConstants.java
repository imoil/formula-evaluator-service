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

    public static final String ARG_KAFKA_BOOTSTRAP_SERVERS = "kafka-bootstrap-servers";
    public static final String ARG_KAFKA_TOPIC_SENSOR = "kafka-topic-sensor";
    public static final String ARG_KAFKA_GROUP_SENSOR = "kafka-group-sensor";
    public static final String ARG_KAFKA_TOPIC_RULE = "kafka-topic-rule";
    public static final String ARG_KAFKA_GROUP_RULE = "kafka-group-rule";
    public static final String ARG_RETENTION_TIME_MINUTES = "retention-time-minutes";
    public static final String ARG_QUESTDB_URL = "questdb-url";

    public static final String SOURCE_NAME_SENSOR = "Sensor Data Source";
    public static final String SOURCE_NAME_RULE = "Rule Source";
    public static final String STATE_DESC_RULES = "DynamicRules";
    public static final String JOB_NAME = "Formula Evaluator Streaming Job";
}
