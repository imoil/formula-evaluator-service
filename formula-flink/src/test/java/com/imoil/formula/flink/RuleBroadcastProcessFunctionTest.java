package com.imoil.formula.flink;

import com.imoil.formula.domain.DynamicRule;
import com.imoil.formula.domain.SensorData;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.streaming.util.ProcessFunctionTestHarnesses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBroadcastProcessFunctionTest {

    @Test
    @DisplayName("단일 일련번호 체계(10001 / SN_10001)로 브로드캐스트된 룰과 실시간 스트림 센서가 정상 매칭되어 평가되어야 한다")
    void testRuleEvaluationWithUnifiedSerialNumber() throws Exception {
        RuleBroadcastProcessFunction function = new RuleBroadcastProcessFunction(30);

        var harness = ProcessFunctionTestHarnesses.forKeyedBroadcastProcessFunction(
                function,
                SensorData::getSensorId,
                BasicTypeInfo.STRING_TYPE_INFO,
                RuleBroadcastProcessFunction.RULE_STATE_DESCRIPTOR
        );

        harness.open();

        // 1. 단일 일련번호 10001에 대한 실시간 수식 룰 브로드캐스트 ("SN_10001" 타깃)
        //    파생 수식 센서 일련번호로 "10201" 지정
        DynamicRule rule = DynamicRule.builder()
                .ruleId("rule_eff_10201")
                .targetSensorId("SN_10001")
                .expression("value * 2.0 + 10.0")
                .derivedSensorId("10201")
                .usesWindow(false)
                .build();

        harness.processBroadcastElement(rule, 1000L);

        // 2. 카프카 컨슈머로부터 리졸버를 거쳐 일련번호 "10001"로 변환된 SensorData 유입
        SensorData rawSensorData = SensorData.builder()
                .sensorId("10001")
                .timestamp(1005L)
                .value(45.0)
                .state(0)
                .build();

        harness.processElement(rawSensorData, 1005L);

        // 3. 결과 방출 검증: 원본 센서(10001) + 수식 연산 파생 센서(10201)
        List<SensorData> output = harness.extractOutputValues();
        assertThat(output).hasSize(2);

        // 첫 번째 방출: 수식 계산된 파생 센서 (45.0 * 2.0 + 10.0 = 100.0)
        SensorData derived = output.get(0);
        assertThat(derived.getSensorId()).isEqualTo("10201");
        assertThat(derived.getValue()).isEqualTo(100.0);
        assertThat(derived.getState()).isEqualTo(0);

        // 두 번째 방출: 원본 센서 데이터 (10001)
        SensorData raw = output.get(1);
        assertThat(raw.getSensorId()).isEqualTo("10001");
        assertThat(raw.getValue()).isEqualTo(45.0);

        harness.close();
    }

    @Test
    @DisplayName("룰 타깃이 순수 숫자 일련번호(10002)이고 스트림이 10002일 때도 정상 매칭되어야 한다")
    void testNumericSerialNumberMatching() throws Exception {
        RuleBroadcastProcessFunction function = new RuleBroadcastProcessFunction(30);

        var harness = ProcessFunctionTestHarnesses.forKeyedBroadcastProcessFunction(
                function,
                SensorData::getSensorId,
                BasicTypeInfo.STRING_TYPE_INFO,
                RuleBroadcastProcessFunction.RULE_STATE_DESCRIPTOR
        );

        harness.open();

        DynamicRule rule = DynamicRule.builder()
                .ruleId("rule_temp_10002")
                .targetSensorId("10002")
                .expression("value + 5.5")
                .usesWindow(false)
                .build();

        harness.processBroadcastElement(rule, 2000L);

        SensorData data = SensorData.builder()
                .sensorId("10002")
                .timestamp(2010L)
                .value(20.0)
                .state(0)
                .build();

        harness.processElement(data, 2010L);

        List<SensorData> output = harness.extractOutputValues();
        assertThat(output).hasSize(2);

        SensorData derived = output.get(0);
        assertThat(derived.getSensorId()).isEqualTo("10002_derived");
        assertThat(derived.getValue()).isEqualTo(25.5);

        harness.close();
    }
}
