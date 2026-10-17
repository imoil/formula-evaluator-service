package com.imoil.formula.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imoil.formula.domain.DynamicRule;
import com.imoil.formula.domain.SensorData;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * Flink Streaming Job 메인 클래스
 */
public class StreamingEvaluatorJob {

    public static void main(String[] args) throws Exception {
        ParameterTool parameters = ParameterTool.fromArgs(args);
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.getConfig().setGlobalJobParameters(parameters);
        
        // 메모리 폭발 방지 및 파일 병합을 위한 RocksDB State Backend 통합 활성화
        env.setStateBackend(new EmbeddedRocksDBStateBackend(true)); // Incremental Checkpoint 활성화
        env.enableCheckpointing(AppConstants.CHECKPOINT_INTERVAL_MS); // 주기적 체크포인트 (장애 복구 보장용)
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(AppConstants.TOLERABLE_CHECKPOINT_FAILURE_NUMBER);

        String kafkaBootstrapServers = parameters.get(AppConstants.ARG_KAFKA_BOOTSTRAP_SERVERS, AppConstants.KAFKA_BOOTSTRAP_SERVERS_DEFAULT);
        
        // Sensor Data Kafka Source
        KafkaSource<String> sensorKafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBootstrapServers)
                .setTopics(parameters.get(AppConstants.ARG_KAFKA_TOPIC_SENSOR, AppConstants.KAFKA_TOPIC_SENSOR_DATA_DEFAULT))
                .setGroupId(parameters.get(AppConstants.ARG_KAFKA_GROUP_SENSOR, AppConstants.KAFKA_GROUP_ID_SENSOR_DEFAULT))
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // Rule 메타데이터 Kafka Source
        KafkaSource<String> ruleKafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBootstrapServers)
                .setTopics(parameters.get(AppConstants.ARG_KAFKA_TOPIC_RULE, AppConstants.KAFKA_TOPIC_RULE_DATA_DEFAULT))
                .setGroupId(parameters.get(AppConstants.ARG_KAFKA_GROUP_RULE, AppConstants.KAFKA_GROUP_ID_RULE_DEFAULT))
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<String> sensorStrings = env.fromSource(sensorKafkaSource, WatermarkStrategy.noWatermarks(), AppConstants.SOURCE_NAME_SENSOR);
        DataStream<String> ruleStrings = env.fromSource(ruleKafkaSource, WatermarkStrategy.noWatermarks(), AppConstants.SOURCE_NAME_RULE);

        ObjectMapper mapper = new ObjectMapper();

        DataStream<SensorData> sensorStream = sensorStrings.map(json -> mapper.readValue(json, SensorData.class));
        DataStream<DynamicRule> ruleStream = ruleStrings.map(json -> mapper.readValue(json, DynamicRule.class));

        // 브로드캐스트 상태 디스크립터
        MapStateDescriptor<String, DynamicRule> ruleStateDescriptor = new MapStateDescriptor<>(
                AppConstants.STATE_DESC_RULES, String.class, DynamicRule.class);

        // 룰 스트림 브로드캐스트
        BroadcastStream<DynamicRule> broadcastRuleStream = ruleStream.broadcast(ruleStateDescriptor);

        // 분산 처리와 상태 저장을 위한 센서 ID 기반 KeyBy 및 브로드캐스트 연결
        long retentionTimeMinutes = parameters.getLong(AppConstants.ARG_RETENTION_TIME_MINUTES, AppConstants.RETENTION_TIME_MINUTES_DEFAULT);
        DataStream<SensorData> evaluatedStream = sensorStream
                .keyBy(SensorData::getSensorId) 
                .connect(broadcastRuleStream)
                .process(new RuleBroadcastProcessFunction(retentionTimeMinutes));

        // ILP 프로토콜을 사용한 QuestDB 최종 적재 Sink 연동결합 (초당 수백만 건 수용을 위한 버퍼/비동기 조건 명시)
        String questdbUrl = parameters.get(AppConstants.ARG_QUESTDB_URL, AppConstants.QUESTDB_URL_DEFAULT);
        evaluatedStream.addSink(new QuestDbIlpSink(questdbUrl));

        env.execute(AppConstants.JOB_NAME);
    }
}
