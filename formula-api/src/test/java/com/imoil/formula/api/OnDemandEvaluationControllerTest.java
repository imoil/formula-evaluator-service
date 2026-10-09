package com.imoil.formula.api;

import com.imoil.formula.domain.SensorData;
import com.imoil.formula.service.OnDemandEvaluationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 단계별 Unit Test 1: 웹 계층 (HTTP 요청 매핑, 파라미터 바인딩, JSON 응답 변환 검증)
 */
@WebMvcTest(OnDemandEvaluationController.class)
class OnDemandEvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OnDemandEvaluationService evaluationService;

    @Test
    @DisplayName("HTTP GET /api/v1/evaluate 호출 시 서비스 결과를 JSON 형식으로 올바르게 반환한다")
    void testEvaluateEndpointSuccess() throws Exception {
        // given: 서비스 계층에서 반환할 샘플 센서 데이터
        SensorData mockResult = SensorData.builder()
                .sensorId("sensor_temp_ondemand_eval")
                .timestamp(1710000000000L)
                .value(45.5)
                .state(0)
                .hasInaccurateData(false)
                .build();

        given(evaluationService.evaluateOverTimeRange(
                "sensor_temp",
                "value * 1.5",
                "2026-03-01T00:00:00",
                "2026-03-01T01:00:00",
                "1m"
        )).willReturn(List.of(mockResult));

        // when & then: MockMvc 요청 수행 및 JSON 프로퍼티 assertion
        mockMvc.perform(get("/api/v1/evaluate")
                        .param("sensorId", "sensor_temp")
                        .param("formula", "value * 1.5")
                        .param("startTime", "2026-03-01T00:00:00")
                        .param("endTime", "2026-03-01T01:00:00")
                        .param("resolution", "1m")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sensorId", is("sensor_temp_ondemand_eval")))
                .andExpect(jsonPath("$[0].timestamp", is(1710000000000L)))
                .andExpect(jsonPath("$[0].value", is(45.5)))
                .andExpect(jsonPath("$[0].state", is(0)))
                .andExpect(jsonPath("$[0].hasInaccurateData", is(false)));
    }
}
