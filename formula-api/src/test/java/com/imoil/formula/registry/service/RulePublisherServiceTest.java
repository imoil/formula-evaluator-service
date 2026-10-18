package com.imoil.formula.registry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imoil.formula.domain.DynamicRule;
import com.imoil.formula.registry.domain.SensorStatus;
import com.imoil.formula.registry.domain.SensorType;
import com.imoil.formula.registry.entity.SensorRegistryEntity;
import com.imoil.formula.registry.repository.SensorRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RulePublisherServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private SensorRegistryRepository sensorRegistryRepository;

    private ObjectMapper objectMapper;
    private RulePublisherService rulePublisherService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        rulePublisherService = new RulePublisherService(kafkaTemplate, objectMapper, sensorRegistryRepository);
        ReflectionTestUtils.setField(rulePublisherService, "ruleTopic", "rule-data");
        ReflectionTestUtils.setField(rulePublisherService, "syncOnStartup", false);
    }

    @Test
    @DisplayName("단일 DynamicRule 발행 시 지정된 rule-data 토픽 및 targetSensorId를 Key로 카프카에 전송한다")
    void testPublishRule() throws Exception {
        DynamicRule rule = DynamicRule.builder()
                .ruleId("rule_eff_10201")
                .targetSensorId("P_10001")
                .expression("value * 1.8 + 32.0")
                .derivedSensorId("10201")
                .usesWindow(false)
                .build();

        rulePublisherService.publishRule(rule);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(1)).send(eq("rule-data"), eq("P_10001"), payloadCaptor.capture());

        DynamicRule sentRule = objectMapper.readValue(payloadCaptor.getValue(), DynamicRule.class);
        assertThat(sentRule.getRuleId()).isEqualTo("rule_eff_10201");
        assertThat(sentRule.getTargetSensorId()).isEqualTo("P_10001");
        assertThat(sentRule.getDerivedSensorId()).isEqualTo("10201");
        assertThat(sentRule.getExpression()).isEqualTo("value * 1.8 + 32.0");
        assertThat(sentRule.isUsesWindow()).isFalse();
    }

    @Test
    @DisplayName("SensorRegistryEntity 수식 센서를 DynamicRule로 정상 변환 후 발행한다")
    void testPublishFormulaSensor() throws Exception {
        SensorRegistryEntity entity = SensorRegistryEntity.builder()
                .serialNumber(10005L)
                .sensorCode("FML-PUMP-EFF")
                .sensorName("Pump Efficiency Index")
                .sensorType(SensorType.FORMULA)
                .formulaExpression("P_10001 * 0.8 + window_avg(5)")
                .status(SensorStatus.ACTIVE)
                .build();

        DynamicRule published = rulePublisherService.publishFormulaSensor(entity);

        assertThat(published).isNotNull();
        assertThat(published.getDerivedSensorId()).isEqualTo("10005");
        assertThat(published.getTargetSensorId()).isEqualTo("P_10001");
        assertThat(published.getRuleId()).isEqualTo("rule_FML-PUMP-EFF");
        assertThat(published.isUsesWindow()).isTrue();

        verify(kafkaTemplate, times(1)).send(eq("rule-data"), eq("P_10001"), anyString());
    }

    @Test
    @DisplayName("DB에 등록된 모든 활성 수식 센서를 일괄 조회하여 Kafka로 동기화한다")
    void testSyncAllFormulaRules() {
        SensorRegistryEntity entity1 = SensorRegistryEntity.builder()
                .serialNumber(10005L)
                .sensorCode("FML-01")
                .sensorType(SensorType.FORMULA)
                .formulaExpression("P_10001 * 2.0")
                .status(SensorStatus.ACTIVE)
                .build();

        SensorRegistryEntity entity2 = SensorRegistryEntity.builder()
                .serialNumber(10006L)
                .sensorCode("FML-02")
                .sensorType(SensorType.FORMULA)
                .formulaExpression("P_10002 / 10.0")
                .status(SensorStatus.ACTIVE)
                .build();

        when(sensorRegistryRepository.findBySensorTypeAndStatus(SensorType.FORMULA, SensorStatus.ACTIVE))
                .thenReturn(List.of(entity1, entity2));

        int syncedCount = rulePublisherService.syncAllFormulaRules();

        assertThat(syncedCount).isEqualTo(2);
        verify(kafkaTemplate, times(2)).send(eq("rule-data"), anyString(), anyString());
    }

    @Test
    @DisplayName("수식 내 단일 일련번호 체계(P_10002, SN_10002 또는 10002)를 올바르게 resolve한다")
    void testResolveTargetSensorId() {
        SensorRegistryEntity e1 = SensorRegistryEntity.builder()
                .formulaExpression("P_10002 * 1.5 + 10")
                .build();
        assertThat(rulePublisherService.resolveTargetSensorId(e1)).isEqualTo("P_10002");

        // 하위 호환성: SN_ 접두사도 정상 resolve
        SensorRegistryEntity eLegacy = SensorRegistryEntity.builder()
                .formulaExpression("SN_10002 * 1.5 + 10")
                .build();
        assertThat(rulePublisherService.resolveTargetSensorId(eLegacy)).isEqualTo("SN_10002");

        SensorRegistryEntity e2 = SensorRegistryEntity.builder()
                .formulaExpression("10003 - 20")
                .build();
        assertThat(rulePublisherService.resolveTargetSensorId(e2)).isEqualTo("10003");

        SensorRegistryEntity e3 = SensorRegistryEntity.builder()
                .formulaExpression("value * 2")
                .deviceId("DEVICE-001")
                .build();
        assertThat(rulePublisherService.resolveTargetSensorId(e3)).isEqualTo("DEVICE-001");
    }
}
