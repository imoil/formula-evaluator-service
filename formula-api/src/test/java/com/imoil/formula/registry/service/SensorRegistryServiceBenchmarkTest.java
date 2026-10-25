package com.imoil.formula.registry.service;

import com.imoil.formula.registry.domain.SensorType;
import com.imoil.formula.registry.dto.DeviceSyncRequest;
import com.imoil.formula.registry.entity.SensorRegistryEntity;
import com.imoil.formula.registry.repository.SensorRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SensorRegistryServiceBenchmarkTest {

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
    @DisplayName("seedDefaultSensorsIfNeeded Benchmark 및 DB Query Invocation Count 측정")
    void benchmarkSeedDefaultSensorsIfNeeded() {
        // Setup repository mocks
        when(repository.findBySensorType(SensorType.PHYSICAL)).thenReturn(Collections.emptyList());
        when(repository.findBySensorType(SensorType.FORMULA)).thenReturn(Collections.emptyList());
        lenient().when(repository.findAll()).thenReturn(Collections.emptyList());
        lenient().when(repository.existsBySensorCode(any())).thenReturn(false);
        lenient().when(repository.existsByDeviceIdAndDeviceSensorIndex(any(), any())).thenReturn(false);

        int iterations = 100;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            registryService.seedDefaultSensorsIfNeeded();
        }
        long durationNs = System.nanoTime() - start;
        double avgMs = (durationNs / 1_000_000.0) / iterations;

        System.out.println("=== Benchmark Result: seedDefaultSensorsIfNeeded ===");
        System.out.println("Iterations: " + iterations);
        System.out.println("Average Execution Time: " + String.format("%.4f", avgMs) + " ms");

        // Verify repository invocations per single execution
        reset(repository);
        when(repository.findBySensorType(SensorType.PHYSICAL)).thenReturn(Collections.emptyList());
        when(repository.findBySensorType(SensorType.FORMULA)).thenReturn(Collections.emptyList());
        lenient().when(repository.findAll()).thenReturn(Collections.emptyList());
        lenient().when(repository.existsBySensorCode(any())).thenReturn(false);
        lenient().when(repository.existsByDeviceIdAndDeviceSensorIndex(any(), any())).thenReturn(false);

        registryService.seedDefaultSensorsIfNeeded();

        int existsBySensorCodeCalls = mockingDetails(repository).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("existsBySensorCode"))
                .toList().size();
        int existsByDeviceIndexCalls = mockingDetails(repository).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("existsByDeviceIdAndDeviceSensorIndex"))
                .toList().size();
        int totalRepoCalls = mockingDetails(repository).getInvocations().size();

        System.out.println("existsBySensorCode calls per seed: " + existsBySensorCodeCalls);
        System.out.println("existsByDeviceIdAndDeviceSensorIndex calls per seed: " + existsByDeviceIndexCalls);
        System.out.println("Total Repository Method Calls per seed: " + totalRepoCalls);
    }

    @Test
    @DisplayName("syncDeviceSensors Benchmark 및 DB Query Invocation Count 측정 (100개 센서)")
    void benchmarkSyncDeviceSensors() {
        lenient().when(repository.findAll()).thenReturn(Collections.emptyList());
        lenient().when(repository.existsBySensorCode(any())).thenReturn(false);
        lenient().when(repository.existsByDeviceIdAndDeviceSensorIndex(any(), any())).thenReturn(false);
        when(repository.saveAll(any())).thenAnswer((Answer<Object>) invocation -> invocation.getArgument(0));

        DeviceSyncRequest request = DeviceSyncRequest.builder()
                .deviceId("BENCH-DEVICE")
                .sensorCount(100)
                .skipExisting(true)
                .build();

        int iterations = 100;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            registryService.syncDeviceSensors(request);
        }
        long durationNs = System.nanoTime() - start;
        double avgMs = (durationNs / 1_000_000.0) / iterations;

        System.out.println("=== Benchmark Result: syncDeviceSensors (100 sensors) ===");
        System.out.println("Iterations: " + iterations);
        System.out.println("Average Execution Time: " + String.format("%.4f", avgMs) + " ms");

        reset(repository);
        lenient().when(repository.findAll()).thenReturn(Collections.emptyList());
        lenient().when(repository.existsBySensorCode(any())).thenReturn(false);
        lenient().when(repository.existsByDeviceIdAndDeviceSensorIndex(any(), any())).thenReturn(false);
        when(repository.saveAll(any())).thenAnswer((Answer<Object>) invocation -> invocation.getArgument(0));

        registryService.syncDeviceSensors(request);

        int existsCalls = mockingDetails(repository).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().startsWith("existsBy"))
                .toList().size();
        int totalRepoCalls = mockingDetails(repository).getInvocations().size();

        System.out.println("existsBy... calls for syncing 100 sensors: " + existsCalls);
        System.out.println("Total Repository Method Calls for syncing 100 sensors: " + totalRepoCalls);
    }
}
