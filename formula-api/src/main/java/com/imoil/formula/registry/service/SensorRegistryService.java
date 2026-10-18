package com.imoil.formula.registry.service;

import com.imoil.formula.domain.SensorSerialResolver;
import com.imoil.formula.registry.domain.SensorStatus;
import com.imoil.formula.registry.domain.SensorType;
import com.imoil.formula.registry.dto.CreateSensorRequest;
import com.imoil.formula.registry.dto.DeviceSyncRequest;
import com.imoil.formula.registry.dto.SensorRegistryDto;
import com.imoil.formula.registry.dto.UpdateSensorRequest;
import com.imoil.formula.registry.entity.SensorRegistryEntity;
import com.imoil.formula.registry.repository.SensorRegistryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * IoT 장치 센서와 수식 센서를 위한 통합 일련번호 관리 서비스.
 * Oracle 19c SENSOR_REGISTRY 테이블 및 시퀀스와 연동하고,
 * Flink 및 온디맨드 처리를 위한 SensorSerialResolver 및 카프카 rule-data와 동기화합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorRegistryService {

    private final SensorRegistryRepository repository;
    private final RulePublisherService rulePublisherService;

    @PostConstruct
    public void initResolverMappings() {
        try {
            List<SensorRegistryEntity> physicalSensors = repository.findBySensorType(SensorType.PHYSICAL);
            for (SensorRegistryEntity e : physicalSensors) {
                if (e.getDeviceId() != null && e.getDeviceSensorIndex() != null && e.getSerialNumber() != null) {
                    SensorSerialResolver.getInstance().registerMapping(
                            e.getDeviceId(), e.getDeviceSensorIndex(), e.getSerialNumber());
                }
            }
            log.info("Initialized SensorSerialResolver with {} registered physical sensors.", physicalSensors.size());
        } catch (Exception e) {
            log.debug("Could not pre-populate SensorSerialResolver mappings: {}", e.getMessage());
        }
    }

    /**
     * 센서 목록 조회 (검색 및 필터)
     */
    @Transactional(readOnly = true)
    public List<SensorRegistryDto> getSensors(SensorType type, String deviceId, String keyword) {
        String trimmedKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        String trimmedDevice = (deviceId != null && !deviceId.trim().isEmpty()) ? deviceId.trim() : null;

        return repository.searchSensors(type, trimmedDevice, trimmedKeyword)
                .stream()
                .map(SensorRegistryDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 일련번호로 센서 단건 조회
     */
    @Transactional(readOnly = true)
    public SensorRegistryDto getSensorBySerial(Long serialNumber) {
        return repository.findBySerialNumber(serialNumber)
                .map(SensorRegistryDto::fromEntity)
                .orElseThrow(() -> new IllegalArgumentException("센서 일련번호를 찾을 수 없습니다: " + serialNumber));
    }

    /**
     * 단일 센서 등록 (물리 센서 or 수식 센서)
     */
    @Transactional
    public SensorRegistryDto createSensor(CreateSensorRequest request) {
        if (request.getSensorType() == null) {
            throw new IllegalArgumentException("센서 타입(PHYSICAL 또는 FORMULA)은 필수 항목입니다.");
        }
        if (request.getSensorName() == null || request.getSensorName().trim().isEmpty()) {
            throw new IllegalArgumentException("센서 이름은 필수 항목입니다.");
        }

        if (request.getSensorType() == SensorType.PHYSICAL) {
            if (request.getDeviceId() == null || request.getDeviceId().trim().isEmpty()) {
                throw new IllegalArgumentException("물리 센서는 IoT 장치 식별자(deviceId)가 필수입니다.");
            }
            if (request.getDeviceSensorIndex() == null) {
                throw new IllegalArgumentException("물리 센서는 장치 내 센서 인덱스(deviceSensorIndex)가 필수입니다.");
            }
            if (repository.existsByDeviceIdAndDeviceSensorIndex(request.getDeviceId(), request.getDeviceSensorIndex())) {
                throw new IllegalStateException(String.format(
                        "해당 IoT 장치(%s)의 센서 인덱스(#%d)는 이미 고유 일련번호가 부여되어 등록되어 있습니다. 충돌 방지를 위해 중복 등록이 차단되었습니다.",
                        request.getDeviceId(), request.getDeviceSensorIndex()
                ));
            }
        } else if (request.getSensorType() == SensorType.FORMULA) {
            if (request.getFormulaExpression() == null || request.getFormulaExpression().trim().isEmpty()) {
                throw new IllegalArgumentException("수식 센서는 평가 계산식(formulaExpression)이 필수입니다.");
            }
        }

        String sensorCode = request.getSensorCode();
        if (sensorCode == null || sensorCode.trim().isEmpty()) {
            if (request.getSensorType() == SensorType.PHYSICAL) {
                sensorCode = String.format("PHY-%s-S%03d",
                        request.getDeviceId().replaceAll("[^A-Za-z0-9]", "").toUpperCase(),
                        request.getDeviceSensorIndex());
            } else {
                sensorCode = String.format("FML-%d", System.currentTimeMillis() % 1000000);
            }
        }

        // 센서 코드 중복 방지
        if (repository.existsBySensorCode(sensorCode)) {
            sensorCode = sensorCode + "-" + (System.currentTimeMillis() % 1000);
        }

        SensorRegistryEntity entity = SensorRegistryEntity.builder()
                .sensorCode(sensorCode)
                .sensorName(request.getSensorName().trim())
                .sensorType(request.getSensorType())
                .category(request.getCategory() != null ? request.getCategory().trim() : "General")
                .unit(request.getUnit() != null ? request.getUnit().trim() : "")
                .deviceId(request.getSensorType() == SensorType.PHYSICAL ? request.getDeviceId().trim() : null)
                .deviceSensorIndex(request.getSensorType() == SensorType.PHYSICAL ? request.getDeviceSensorIndex() : null)
                .formulaExpression(request.getSensorType() == SensorType.FORMULA ? request.getFormulaExpression().trim() : null)
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : SensorStatus.ACTIVE)
                .build();

        SensorRegistryEntity saved = repository.save(entity);
        log.info("새 센서 등록 완료 - 일련번호: #{}, 코드: {}, 타입: {}",
                saved.getSerialNumber(), saved.getSensorCode(), saved.getSensorType());

        // Flink 스트리밍 리졸버 동기화
        if (saved.getSensorType() == SensorType.PHYSICAL && saved.getDeviceId() != null && saved.getDeviceSensorIndex() != null) {
            SensorSerialResolver.getInstance().registerMapping(
                    saved.getDeviceId(), saved.getDeviceSensorIndex(), saved.getSerialNumber());
        }

        // 수식 센서인 경우 실시간으로 Kafka rule-data 토픽으로 동적 룰 자동 발행
        if (saved.getSensorType() == SensorType.FORMULA && saved.getStatus() == SensorStatus.ACTIVE) {
            try {
                rulePublisherService.publishFormulaSensor(saved);
            } catch (Exception e) {
                log.warn("새 수식 센서 #{} 동적 룰 카프카 발행 실패: {}", saved.getSerialNumber(), e.getMessage());
            }
        }

        return SensorRegistryDto.fromEntity(saved);
    }

    /**
     * 센서 수정
     */
    @Transactional
    public SensorRegistryDto updateSensor(Long serialNumber, UpdateSensorRequest request) {
        SensorRegistryEntity entity = repository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new IllegalArgumentException("수정할 센서 일련번호를 찾을 수 없습니다: " + serialNumber));

        if (request.getSensorName() != null && !request.getSensorName().trim().isEmpty()) {
            entity.setSensorName(request.getSensorName().trim());
        }
        if (request.getCategory() != null) {
            entity.setCategory(request.getCategory().trim());
        }
        if (request.getUnit() != null) {
            entity.setUnit(request.getUnit().trim());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        if (entity.getSensorType() == SensorType.FORMULA && request.getFormulaExpression() != null) {
            entity.setFormulaExpression(request.getFormulaExpression().trim());
        }

        SensorRegistryEntity updated = repository.save(entity);
        log.info("센서 정보 수정 완료 - 일련번호: #{}", serialNumber);

        // 수식 센서이고 활성 상태인 경우 Kafka rule-data 토픽으로 룰 갱신 발행
        if (updated.getSensorType() == SensorType.FORMULA && updated.getStatus() == SensorStatus.ACTIVE) {
            try {
                rulePublisherService.publishFormulaSensor(updated);
            } catch (Exception e) {
                log.warn("수식 센서 #{} 갱신 룰 카프카 발행 실패: {}", serialNumber, e.getMessage());
            }
        }

        return SensorRegistryDto.fromEntity(updated);
    }

    /**
     * 센서 삭제
     */
    @Transactional
    public void deleteSensor(Long serialNumber) {
        SensorRegistryEntity entity = repository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new IllegalArgumentException("삭제할 센서 일련번호를 찾을 수 없습니다: " + serialNumber));
        repository.delete(entity);
        log.info("센서 삭제 완료 - 일련번호: #{}", serialNumber);
    }

    /**
     * formula-emulator IoT 장치의 센서들을 스캔하여 일괄 등록 (일련번호 자동 채번)
     */
    @Transactional
    public List<SensorRegistryDto> syncDeviceSensors(DeviceSyncRequest request) {
        String deviceId = request.getDeviceId();
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("IoT 장치 식별자(deviceId)는 필수입니다.");
        }
        deviceId = deviceId.trim();
        int count = Math.max(1, Math.min(request.getSensorCount(), 1000));

        List<SensorRegistryEntity> toSave = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            if (request.isSkipExisting() && repository.existsByDeviceIdAndDeviceSensorIndex(deviceId, i)) {
                continue;
            }

            String category = SensorType.defaultCategoryForIndex(i);
            String unit = SensorType.defaultUnitForCategory(category);
            String sensorCode = String.format("PHY-%s-S%03d",
                    deviceId.replaceAll("[^A-Za-z0-9]", "").toUpperCase(), i);

            // 이미 존재하는 sensorCode는 건너뛰거나 재생성
            if (repository.existsBySensorCode(sensorCode)) {
                sensorCode = sensorCode + "_" + i;
            }

            SensorRegistryEntity entity = SensorRegistryEntity.builder()
                    .sensorCode(sensorCode)
                    .sensorName(String.format("[%s] %s Sensor #%d", deviceId, category, i))
                    .sensorType(SensorType.PHYSICAL)
                    .category(category)
                    .unit(unit)
                    .deviceId(deviceId)
                    .deviceSensorIndex(i)
                    .description(String.format("Auto-synced from IoT Emulator device '%s' with unique serial number.", deviceId))
                    .status(SensorStatus.ACTIVE)
                    .build();

            toSave.add(entity);
        }

        if (toSave.isEmpty()) {
            log.info("장치 {}에 대해 추가 동기화할 새로운 센서가 없습니다.", deviceId);
            return List.of();
        }

        List<SensorRegistryEntity> saved = repository.saveAll(toSave);
        log.info("IoT 장치 {}의 센서 {}개 일괄 동기화 및 일련번호 부여 완료", deviceId, saved.size());

        // Flink 스트리밍 리졸버 동기화
        for (SensorRegistryEntity e : saved) {
            if (e.getDeviceId() != null && e.getDeviceSensorIndex() != null && e.getSerialNumber() != null) {
                SensorSerialResolver.getInstance().registerMapping(
                        e.getDeviceId(), e.getDeviceSensorIndex(), e.getSerialNumber());
            }
        }

        return saved.stream()
                .map(SensorRegistryDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 등록된 모든 IoT 장치 목록 반환
     */
    @Transactional(readOnly = true)
    public List<String> getRegisteredDeviceIds() {
        return repository.findDistinctDeviceIds();
    }

    /**
     * 다음 일련번호 시퀀스 값 미리보기
     */
    @Transactional(readOnly = true)
    public Long getNextSerialNumberPreview() {
        return repository.findMaxSerialNumber() + 1;
    }
}
