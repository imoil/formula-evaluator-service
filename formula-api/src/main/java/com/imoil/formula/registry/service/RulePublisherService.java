package com.imoil.formula.registry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imoil.formula.domain.DynamicRule;
import com.imoil.formula.registry.domain.SensorStatus;
import com.imoil.formula.registry.domain.SensorType;
import com.imoil.formula.registry.entity.SensorRegistryEntity;
import com.imoil.formula.registry.repository.SensorRegistryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 관리자 동적 룰(DynamicRule)을 카프카 rule-data 토픽으로 발행하고,
 * Oracle 19c SENSOR_REGISTRY의 활성 수식 센서들을 Flink BroadcastState로 일괄 동기화하는 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RulePublisherService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SensorRegistryRepository sensorRegistryRepository;

    @Value("${formula.rules.topic:rule-data}")
    private String ruleTopic;

    @Value("${formula.rules.sync-on-startup:true}")
    private boolean syncOnStartup;

    // 수식 내 단일 일련번호 체계(SN_10001 또는 10001) 탐지용 정규식 패턴
    private static final Pattern SENSOR_REF_PATTERN = Pattern.compile("\\b(?:SN_)?(\\d{4,6})\\b");

    /**
     * 단일 DynamicRule을 JSON으로 직렬화한 후 rule-data 토픽으로 발행합니다.
     * key = rule.getTargetSensorId()
     *
     * @param rule 발행할 동적 룰
     */
    public void publishRule(DynamicRule rule) {
        if (rule == null || rule.getTargetSensorId() == null) {
            log.warn("Cannot publish dynamic rule: rule or targetSensorId is null");
            return;
        }

        try {
            String jsonPayload = objectMapper.writeValueAsString(rule);
            kafkaTemplate.send(ruleTopic, rule.getTargetSensorId(), jsonPayload);
            log.info("Successfully published DynamicRule to Kafka: topic={}, key={}, ruleId={}, derivedSensorId={}",
                    ruleTopic, rule.getTargetSensorId(), rule.getRuleId(), rule.getDerivedSensorId());
        } catch (Exception e) {
            log.error("Failed to publish DynamicRule [id={}] to Kafka topic {}: {}",
                    rule.getRuleId(), ruleTopic, e.getMessage(), e);
        }
    }

    /**
     * SensorRegistryEntity(FORMULA 타입)를 DynamicRule 객체로 변환하여 카프카로 발행합니다.
     *
     * @param entity 수식 센서 엔티티
     * @return 변환 및 발행된 DynamicRule (비수식 센서이거나 수식 누락 시 null)
     */
    public DynamicRule publishFormulaSensor(SensorRegistryEntity entity) {
        if (entity == null || entity.getFormulaExpression() == null || entity.getFormulaExpression().isBlank()) {
            return null;
        }

        String targetSensorId = resolveTargetSensorId(entity);
        String expression = entity.getFormulaExpression().trim();
        boolean usesWindow = expression.contains("window_") || expression.contains("previous_");
        String derivedSensorId = String.valueOf(entity.getSerialNumber());
        String ruleId = (entity.getSensorCode() != null && !entity.getSensorCode().isBlank())
                ? "rule_" + entity.getSensorCode()
                : "rule_" + entity.getSerialNumber();

        DynamicRule dynamicRule = DynamicRule.builder()
                .ruleId(ruleId)
                .targetSensorId(targetSensorId)
                .expression(expression)
                .usesWindow(usesWindow)
                .derivedSensorId(derivedSensorId)
                .build();

        publishRule(dynamicRule);
        return dynamicRule;
    }

    /**
     * 단일 진실 공급원(SSOT)인 Oracle DB의 모든 활성(ACTIVE) 수식 센서를 일괄 조회하여
     * 카프카 rule-data 토픽으로 순차 발행합니다.
     *
     * @return 동기화된 룰 수
     */
    public int syncAllFormulaRules() {
        List<SensorRegistryEntity> activeFormulas =
                sensorRegistryRepository.findBySensorTypeAndStatus(SensorType.FORMULA, SensorStatus.ACTIVE);

        int publishedCount = 0;
        for (SensorRegistryEntity entity : activeFormulas) {
            try {
                DynamicRule rule = publishFormulaSensor(entity);
                if (rule != null) {
                    publishedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to sync formula sensor #{} to Kafka: {}", entity.getSerialNumber(), e.getMessage());
            }
        }

        log.info("Bulk Dynamic Rule synchronization completed: {} active rules published to topic '{}'",
                publishedCount, ruleTopic);
        return publishedCount;
    }

    /**
     * 애플리케이션 시작 완료 시 DB 최신 수식 룰을 카프카로 자동 1회 발행하여 Flink 메모리 초기화 보장
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (syncOnStartup) {
            log.info("Performing on-startup dynamic rule synchronization with Kafka (topic={})...", ruleTopic);
            try {
                int count = syncAllFormulaRules();
                log.info("Startup dynamic rule sync completed: {} rules synchronized.", count);
            } catch (Exception e) {
                log.warn("Startup dynamic rule sync encountered an issue: {}", e.getMessage());
            }
        }
    }

    /**
     * 수식 표현식 또는 엔티티 속성으로부터 대상 센서 일련번호(targetSensorId)를 추출합니다.
     */
    public String resolveTargetSensorId(SensorRegistryEntity entity) {
        String expr = entity.getFormulaExpression();
        if (expr != null) {
            Matcher m = SENSOR_REF_PATTERN.matcher(expr);
            if (m.find()) {
                return m.group(0);
            }
        }
        if (entity.getDeviceId() != null && !entity.getDeviceId().isBlank()) {
            return entity.getDeviceId();
        }
        return "10001";
    }
}
