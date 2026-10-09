package com.imoil.formula.api;

import com.imoil.formula.domain.SensorData;
import com.imoil.formula.service.OnDemandEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/evaluate")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class OnDemandEvaluationController {

    private final OnDemandEvaluationService evaluationService;

    /**
     * 사용자가 대시보드나 온디맨드로 특정 기간의 데이터를 조회하며 동적 수식을 입힐 때 호출됩니다.
     * 클라이언트는 내부 데이터 소스(QuestDB, Oracle, NATS 등)의 존재를 전혀 알 필요 없이
     * 순수하게 대상 센서 ID, 수식, 기간 정보만 전달합니다.
     */
    @GetMapping
    public ResponseEntity<List<SensorData>> evaluateOnDemand(
            @RequestParam String sensorId,
            @RequestParam String expression,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(defaultValue = "1m") String sampleBy) {
        
        List<SensorData> result = evaluationService.evaluateOverTimeRange(sensorId, expression, startTime, endTime, sampleBy);
        return ResponseEntity.ok(result);
    }
}
