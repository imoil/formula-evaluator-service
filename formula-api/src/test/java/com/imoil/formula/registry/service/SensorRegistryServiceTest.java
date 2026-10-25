package com.imoil.formula.registry.service;

import com.imoil.formula.registry.domain.SensorType;
import com.imoil.formula.registry.dto.DeviceSyncRequest;
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
    @DisplayName("DB가 비어 있을 때 seedDefaultSensorsIfNeeded 호출 시 기본 물리/수식 센서가 정상 저장된다")
    void testSeedDefaultSensorsWhenEmpty() {
        when(repository.findBySensorType(SensorType.PHYSICAL)).thenReturn(Collections.emptyList());
        when(repository.findBySensorType(SensorType.FORMULA)).thenReturn(Collections.emptyList());
        lenient().when(repository.findAll()).thenReturn(Collections.emptyList());
        lenient().when(repository.existsBySensorCode(any())).thenReturn(false);
        lenient().when(repository.existsByDeviceIdAndDeviceSensorIndex(any(), any())).thenReturn(false);

        registryService.seedDefaultSensorsIfNeeded();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SensorRegistryEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository, times(2)).saveAll(captor.capture());

        List<List<SensorRegistryEntity>> savedBatches = captor.getAllValues();
        assertThat(savedBatches).hasSize(2);

        List<SensorRegistryEntity> physicalBatch = savedBatches.get(0);
        assertThat(physicalBatch).hasSize(4);
        assertThat(physicalBatch.get(0).getSensorType()).isEqualTo(SensorType.PHYSICAL);

        List<SensorRegistryEntity> formulaBatch = savedBatches.get(1);
        assertThat(formulaBatch).hasSize(5);
        assertThat(formulaBatch.get(0).getSensorType()).isEqualTo(SensorType.FORMULA);
    }

    @Test
    @DisplayName("이미 모든 물리/수식 센서가 존재할 때 seedDefaultSensorsIfNeeded 호출 시 saveAll이 실행되지 않는다")
    void testSeedDefaultSensorsWhenAlreadySeeded() {
        SensorRegistryEntity phy = SensorRegistryEntity.builder()
                .serialNumber(10001L)
                .sensorCode("PHY-DEVICE001-S000")
                .sensorType(SensorType.PHYSICAL)
                .build();
        SensorRegistryEntity fml = SensorRegistryEntity.builder()
                .serialNumber(10005L)
                .sensorCode("FML-PUMP-EFF-01")
                .sensorType(SensorType.FORMULA)
                .build();

        when(repository.findBySensorType(SensorType.PHYSICAL)).thenReturn(List.of(phy));
        when(repository.findBySensorType(SensorType.FORMULA)).thenReturn(List.of(fml));

        registryService.seedDefaultSensorsIfNeeded();

        verify(repository, never()).saveAll(any());
    }

    @Test
    @DisplayName("syncDeviceSensors 호출 시 지정된 개수만큼 센서가 정상 동기화된다")
    void testSyncDeviceSensors() {
        lenient().when(repository.findAll()).thenReturn(Collections.emptyList());
        lenient().when(repository.existsBySensorCode(any())).thenReturn(false);
        lenient().when(repository.existsByDeviceIdAndDeviceSensorIndex(any(), any())).thenReturn(false);
        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceSyncRequest request = DeviceSyncRequest.builder()
                .deviceId("DEVICE-001")
                .sensorCount(5)
                .skipExisting(true)
                .build();

        var result = registryService.syncDeviceSensors(request);

        assertThat(result).hasSize(5);
        verify(repository, times(1)).saveAll(any());
    }
}
