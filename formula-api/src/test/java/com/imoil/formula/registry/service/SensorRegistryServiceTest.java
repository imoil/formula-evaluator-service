package com.imoil.formula.registry.service;

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

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorRegistryServiceTest {

    @Mock
    private SensorRegistryRepository repository;

    @Mock
    private RulePublisherService rulePublisherService;

    private SensorRegistryService registryService;

    @BeforeEach
    void setUp() {
        registryService = new SensorRegistryService(repository, rulePublisherService);
    }

    @Test
    @DisplayName("시딩 필요 시 existsBy... 반복 쿼리 없이 findAll()로 1회 일괄 조회하여 N+1 방지")
    void testSeedDefaultSensorsIfNeeded_NoNPlusOneQueries() {
        // Given: PHYSICAL 및 FORMULA 센서가 DB에 전혀 없는 상태
        when(repository.findBySensorType(SensorType.PHYSICAL)).thenReturn(Collections.emptyList());
        when(repository.findBySensorType(SensorType.FORMULA)).thenReturn(Collections.emptyList());

        // findAll() 호출 시 빈 리스트 반환
        when(repository.findAll()).thenReturn(Collections.emptyList());

        // When
        registryService.seedDefaultSensorsIfNeeded();

        // Then: existsBySensorCode 및 existsByDeviceIdAndDeviceSensorIndex는 전혀 호출되지 않아야 함
        verify(repository, never()).existsBySensorCode(any());
        verify(repository, never()).existsByDeviceIdAndDeviceSensorIndex(any(), anyInt());

        // findAll()은 1회 호출
        verify(repository, times(1)).findAll();

        // saveAll은 PHYSICAL 시딩 1회, FORMULA 시딩 1회 총 2회 호출
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SensorRegistryEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository, times(2)).saveAll(captor.capture());

        List<List<SensorRegistryEntity>> savedBatches = captor.getAllValues();
        assertThat(savedBatches.get(0)).isNotEmpty(); // Physical default specs
        assertThat(savedBatches.get(1)).isNotEmpty(); // Formula default specs
    }

    @Test
    @DisplayName("일부 기존 센서가 findAll() 결과로 존재하는 경우 중복 건너뛰고 시딩")
    void testSeedDefaultSensorsIfNeeded_WithExistingEntities() {
        when(repository.findBySensorType(SensorType.PHYSICAL)).thenReturn(Collections.emptyList());
        when(repository.findBySensorType(SensorType.FORMULA)).thenReturn(Collections.emptyList());

        // 이미 PHY-DEVICE001-S000 코드가 존재하는 엔티티 mock
        SensorRegistryEntity existingPhysical = SensorRegistryEntity.builder()
                .sensorCode("PHY-DEVICE001-S000")
                .deviceId("DEVICE-001")
                .deviceSensorIndex(0)
                .build();

        when(repository.findAll()).thenReturn(List.of(existingPhysical));

        // When
        registryService.seedDefaultSensorsIfNeeded();

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SensorRegistryEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository, times(2)).saveAll(captor.capture());

        List<SensorRegistryEntity> savedPhysicals = captor.getAllValues().get(0);
        // PHY-DEVICE001-S000 은 이미 존재하므로 저장 대상에서 제외 (기존 4개 중 3개 저장)
        assertThat(savedPhysicals).extracting(SensorRegistryEntity::getSensorCode)
                .doesNotContain("PHY-DEVICE001-S000");
    }
}
