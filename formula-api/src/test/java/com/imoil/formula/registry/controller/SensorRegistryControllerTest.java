package com.imoil.formula.registry.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imoil.formula.registry.domain.SensorStatus;
import com.imoil.formula.registry.domain.SensorType;
import com.imoil.formula.registry.dto.CreateSensorRequest;
import com.imoil.formula.registry.dto.DeviceSyncRequest;
import com.imoil.formula.registry.dto.UpdateSensorRequest;
import com.imoil.formula.registry.service.SensorRegistryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SensorRegistryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SensorRegistryService registryService;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("물리 IoT 센서 등록 시 Oracle 시퀀스 기반 고유 일련번호가 자동 채번된다")
    void testCreatePhysicalSensor() throws Exception {
        CreateSensorRequest request = CreateSensorRequest.builder()
                .sensorName("DEVICE-001 Temperature Sensor #0")
                .sensorType(SensorType.PHYSICAL)
                .category("Temperature")
                .unit("°C")
                .deviceId("DEVICE-001")
                .deviceSensorIndex(0)
                .description("Turbine Bearing Temperature")
                .build();

        mockMvc.perform(post("/api/v1/registry/sensors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serialNumber", notNullValue()))
                .andExpect(jsonPath("$.sensorCode", containsString("PHY-DEVICE001-S000")))
                .andExpect(jsonPath("$.sensorType", is("PHYSICAL")))
                .andExpect(jsonPath("$.deviceId", is("DEVICE-001")))
                .andExpect(jsonPath("$.deviceSensorIndex", is(0)));
    }

    @Test
    @DisplayName("동일 IoT 장치의 동일 센서 인덱스 중복 등록 시 충돌 방지(409 Conflict)를 반환한다")
    void testDuplicatePhysicalSensorPrevented() throws Exception {
        CreateSensorRequest request = CreateSensorRequest.builder()
                .sensorName("PUMP-A Pressure Sensor #1")
                .sensorType(SensorType.PHYSICAL)
                .category("Pressure")
                .unit("bar")
                .deviceId("PUMP-A")
                .deviceSensorIndex(1)
                .build();

        // 1회차 등록 성공
        registryService.createSensor(request);

        // 2회차 동일 디바이스 및 인덱스로 등록 시도 -> 409 Conflict
        CreateSensorRequest dupRequest = CreateSensorRequest.builder()
                .sensorName("Duplicate PUMP-A Pressure Sensor #1")
                .sensorType(SensorType.PHYSICAL)
                .category("Pressure")
                .unit("bar")
                .deviceId("PUMP-A")
                .deviceSensorIndex(1)
                .build();

        mockMvc.perform(post("/api/v1/registry/sensors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("이미 고유 일련번호가 부여되어 등록되어 있습니다")));
    }

    @Test
    @DisplayName("수식 센서 등록 시 일관된 일련번호 체계가 부여되며 카프카로 동적 룰이 발행된다")
    void testCreateFormulaSensor() throws Exception {
        CreateSensorRequest request = CreateSensorRequest.builder()
                .sensorName("Calculated Cooling Efficiency")
                .sensorType(SensorType.FORMULA)
                .category("Efficiency")
                .unit("%")
                .formulaExpression("100 * (SN_10001 - SN_10002) / SN_10001")
                .description("Formula calculated from thermal delta")
                .build();

        mockMvc.perform(post("/api/v1/registry/sensors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serialNumber", notNullValue()))
                .andExpect(jsonPath("$.sensorType", is("FORMULA")))
                .andExpect(jsonPath("$.formulaExpression", is("100 * (SN_10001 - SN_10002) / SN_10001")));

        verify(kafkaTemplate, atLeastOnce()).send(eq("rule-data"), anyString(), anyString());
    }

    @Test
    @DisplayName("IoT 장치 센서 일괄 등록(Sync) 시 충돌 없이 대량의 센서에 일련번호가 연속 부여된다")
    void testSyncDeviceSensors() throws Exception {
        DeviceSyncRequest syncReq = DeviceSyncRequest.builder()
                .deviceId("DEVICE-002")
                .sensorCount(10)
                .skipExisting(true)
                .build();

        mockMvc.perform(post("/api/v1/registry/devices/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].deviceId", is("DEVICE-002")))
                .andExpect(jsonPath("$[0].deviceSensorIndex", is(0)))
                .andExpect(jsonPath("$[9].deviceSensorIndex", is(9)))
                .andExpect(jsonPath("$[0].serialNumber", notNullValue()));
    }

    @Test
    @DisplayName("센서 수정 및 삭제가 정상 동작한다")
    void testUpdateAndDeleteSensor() throws Exception {
        // 등록
        CreateSensorRequest createReq = CreateSensorRequest.builder()
                .sensorName("Initial Sensor")
                .sensorType(SensorType.PHYSICAL)
                .category("Flow")
                .unit("m³/h")
                .deviceId("FLOW-METER-01")
                .deviceSensorIndex(5)
                .build();

        var created = registryService.createSensor(createReq);
        Long serial = created.getSerialNumber();

        // 수정
        UpdateSensorRequest updateReq = UpdateSensorRequest.builder()
                .sensorName("Updated Flow Sensor Name")
                .unit("L/min")
                .status(SensorStatus.MAINTENANCE)
                .build();

        mockMvc.perform(put("/api/v1/registry/sensors/" + serial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensorName", is("Updated Flow Sensor Name")))
                .andExpect(jsonPath("$.unit", is("L/min")))
                .andExpect(jsonPath("$.status", is("MAINTENANCE")));

        // 삭제
        mockMvc.perform(delete("/api/v1/registry/sensors/" + serial))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.deletedSerialNumber", is(serial.intValue())));

        // 삭제 후 조회 실패 확인 (400 Bad Request)
        mockMvc.perform(get("/api/v1/registry/sensors/" + serial))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")));
    }

    @Test
    @DisplayName("GET /api/v1/registry/next-serial 호출 시 다음 부여될 일련번호를 반환한다")
    void testGetNextSerialPreview() throws Exception {
        mockMvc.perform(get("/api/v1/registry/next-serial"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextSerialNumber", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/registry/sensors/rules/sync 호출 시 활성 수식 센서들을 Flink Kafka 토픽으로 일괄 동기화한다")
    void testSyncRulesToFlink() throws Exception {
        // 수식 센서 등록
        CreateSensorRequest request = CreateSensorRequest.builder()
                .sensorName("Formula Sensor For Sync")
                .sensorType(SensorType.FORMULA)
                .category("Efficiency")
                .unit("%")
                .formulaExpression("SN_10001 * 0.95")
                .build();
        registryService.createSensor(request);

        mockMvc.perform(post("/api/v1/registry/sensors/rules/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncedCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.message", containsString("Successfully synchronized")));

        // 별칭 엔드포인트(/rules/sync)도 정상 동작
        mockMvc.perform(post("/api/v1/registry/rules/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncedCount", greaterThanOrEqualTo(1)));
    }
}
