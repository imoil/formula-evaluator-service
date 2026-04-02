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
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * Flink Streaming Job 메인 클래스
 */
public class StreamingEvaluatorJob {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // 메모리 폭발 방지 및 파일 병합을 위한 RocksDB State Backend 통합 활성화
        env.setStateBackend(new EmbeddedRocksDBStateBackend(true)); // Incremental Checkpoint 활성화
        env.enableCheckpointing(10000); // 10초 주기 체크포인트 (장애 복구 보장용)
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(2);
        
        // Sensor Data Kafka Source
        KafkaSource<String> sensorKafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers("localhost:9092")
                .setTopics("sensor-data")
                .setGroupId("flink-sensor-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // Rule 메타데이터 Kafka Source
        KafkaSource<String> ruleKafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers("localhost:9092")
                .setTopics("rule-data")
                .setGroupId("flink-rule-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<String> sensorStrings = env.fromSource(sensorKafkaSource, WatermarkStrategy.noWatermarks(), "Sensor Data Source");
        DataStream<String> ruleStrings = env.fromSource(ruleKafkaSource, WatermarkStrategy.noWatermarks(), "Rule Source");

        ObjectMapper mapper = new ObjectMapper();

        DataStream<SensorData> sensorStream = sensorStrings.map(json -> mapper.readValue(json, SensorData.class));
        DataStream<DynamicRule> ruleStream = ruleStrings.map(json -> mapper.readValue(json, DynamicRule.class));

        // 브로드캐스트 상태 디스크립터
        MapStateDescriptor<String, DynamicRule> ruleStateDescriptor = new MapStateDescriptor<>(
                "DynamicRules", String.class, DynamicRule.class);

        // 룰 스트림 브로드캐스트
        BroadcastStream<DynamicRule> broadcastRuleStream = ruleStream.broadcast(ruleStateDescriptor);

        // 분산 처리와 상태 저장을 위한 센서 ID 기반 KeyBy 및 브로드캐스트 연결
        DataStream<SensorData> evaluatedStream = sensorStream
                .keyBy(SensorData::getSensorId) 
                .connect(broadcastRuleStream)
                .process(new RuleBroadcastProcessFunction());

        // ILP 프로토콜을 사용한 QuestDB 최종 적재 Sink 연동결합
        evaluatedStream.addSink(new QuestDbIlpSink("http::addr=localhost:9000;"));

        env.execute("Formula Evaluator Streaming Job");
    }
}
