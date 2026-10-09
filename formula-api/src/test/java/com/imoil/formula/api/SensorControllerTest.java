package com.imoil.formula.api;

import com.imoil.formula.service.MockSensorCatalogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SensorController.class)
@Import(MockSensorCatalogService.class)
class SensorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/sensors 호출 시 100개의 샘플 센서 목록이 반환된다")
    void testGetAllSensors() throws Exception {
        mockMvc.perform(get("/api/v1/sensors")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(100)))
                .andExpect(jsonPath("$[0].id", is("sensor_000")))
                .andExpect(jsonPath("$[99].id", is("sensor_099")));
    }

    @Test
    @DisplayName("GET /api/v1/sensors/{sensorId} 호출 시 특정 센서 메타데이터가 반환된다")
    void testGetSensorMeta() throws Exception {
        mockMvc.perform(get("/api/v1/sensors/sensor_000")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("sensor_000")))
                .andExpect(jsonPath("$.category", is("Temperature")))
                .andExpect(jsonPath("$.unit", is("°C")));
    }

    @Test
    @DisplayName("GET /api/v1/sensors/{sensorId}/timeseries 호출 시 1시간(3,600개) 시계열 데이터가 반환된다")
    void testGetSensorTimeSeries() throws Exception {
        mockMvc.perform(get("/api/v1/sensors/sensor_001/timeseries")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3600)))
                .andExpect(jsonPath("$[0].sensorId", is("sensor_001")))
                .andExpect(jsonPath("$[0].value", notNullValue()));
    }
}
