package com.imoil.formula.registry.controller;

import com.imoil.formula.registry.domain.SensorType;
import com.imoil.formula.registry.dto.CreateSensorRequest;
import com.imoil.formula.registry.dto.DeviceSyncRequest;
import com.imoil.formula.registry.dto.SensorRegistryDto;
import com.imoil.formula.registry.dto.UpdateSensorRequest;
import com.imoil.formula.registry.service.SensorRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * IoT 장치 및 수식 센서의 일련번호 및 메타데이터 관리를 위한 REST 컨트롤러.
 * Oracle 19c 데이터베이스 연동.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/registry")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SensorRegistryController {

    private final SensorRegistryService registryService;

    /**
     * 센서 목록 조회 (타입, 장치 ID, 키워드 검색 지원)
     */
    @GetMapping("/sensors")
    public ResponseEntity<List<SensorRegistryDto>> getSensors(
            @RequestParam(required = false) SensorType type,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(registryService.getSensors(type, deviceId, keyword));
    }

    /**
     * 특정 일련번호의 센서 상세 조회
     */
    @GetMapping("/sensors/{serialNumber}")
    public ResponseEntity<SensorRegistryDto> getSensorBySerial(@PathVariable Long serialNumber) {
        return ResponseEntity.ok(registryService.getSensorBySerial(serialNumber));
    }

    /**
     * 신규 센서 등록 (자동 일련번호 채번)
     */
    @PostMapping("/sensors")
    public ResponseEntity<SensorRegistryDto> createSensor(@RequestBody CreateSensorRequest request) {
        SensorRegistryDto created = registryService.createSensor(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 센서 정보 수정
     */
    @PutMapping("/sensors/{serialNumber}")
    public ResponseEntity<SensorRegistryDto> updateSensor(
            @PathVariable Long serialNumber,
            @RequestBody UpdateSensorRequest request) {
        SensorRegistryDto updated = registryService.updateSensor(serialNumber, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * 센서 삭제
     */
    @DeleteMapping("/sensors/{serialNumber}")
    public ResponseEntity<Map<String, Object>> deleteSensor(@PathVariable Long serialNumber) {
        registryService.deleteSensor(serialNumber);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "deletedSerialNumber", serialNumber,
                "message", "센서가 성공적으로 삭제되었습니다."
        ));
    }

    /**
     * formula-emulator IoT 장치의 센서들을 일괄 등록/동기화 (충돌 방지 일련번호 자동 부여)
     */
    @PostMapping("/devices/sync")
    public ResponseEntity<List<SensorRegistryDto>> syncDeviceSensors(@RequestBody DeviceSyncRequest request) {
        List<SensorRegistryDto> synced = registryService.syncDeviceSensors(request);
        return ResponseEntity.ok(synced);
    }

    /**
     * 등록된 모든 IoT 장치 목록 조회
     */
    @GetMapping("/devices")
    public ResponseEntity<List<String>> getRegisteredDevices() {
        return ResponseEntity.ok(registryService.getRegisteredDeviceIds());
    }

    /**
     * 다음에 부여될 일련번호 프리뷰
     */
    @GetMapping("/next-serial")
    public ResponseEntity<Map<String, Object>> getNextSerialPreview() {
        Long nextSerial = registryService.getNextSerialNumberPreview();
        return ResponseEntity.ok(Map.of("nextSerialNumber", nextSerial));
    }
}
