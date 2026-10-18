package com.imoil.formula.api;

import com.imoil.formula.datasource.TimeSeriesDataSourceAdapter;
import com.imoil.formula.datasource.TimeSeriesQueryCriteria;
import com.imoil.formula.domain.SensorData;
import com.imoil.formula.service.MockSensorCatalogService;
import com.imoil.formula.service.MockSensorCatalogService.SensorMeta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 프론트엔드 UI 대시보드 및 온디맨드 수식 평가를 위한 센서 메타데이터 및 샘플 시계열 데이터 조회 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/sensors")
@CrossOrigin(origins = "*")
public class SensorController {

    private final MockSensorCatalogService catalogService;
    private final TimeSeriesDataSourceAdapter timeSeriesDataSourceAdapter;

    @Autowired
    public SensorController(
            MockSensorCatalogService catalogService,
            @Autowired(required = false) TimeSeriesDataSourceAdapter timeSeriesDataSourceAdapter) {
        this.catalogService = catalogService;
        this.timeSeriesDataSourceAdapter = timeSeriesDataSourceAdapter;
    }

    /**
     * 100개의 정규분포 가상 센서 카탈로그 목록을 반환합니다.
     */
    @GetMapping
    public ResponseEntity<List<SensorMeta>> getAllSensors() {
        return ResponseEntity.ok(catalogService.getAllSensorCatalog());
    }

    /**
     * 특정 센서의 메타데이터(평균, 표준편차, 단위, 클램프 한계 등)를 반환합니다.
     */
    @GetMapping("/{sensorId}")
    public ResponseEntity<SensorMeta> getSensorMeta(@PathVariable String sensorId) {
        return ResponseEntity.ok(catalogService.getSensorMeta(sensorId));
    }

    /**
     * 특정 센서의 1시간(3,600초) 분량 원본 시계열 데이터를 반환합니다.
     * 활성화된 TimeSeriesDataSourceAdapter seam을 우선 조회하고, 데이터가 없거나 오프라인인 경우 합성 카탈로그로 폴백합니다.
     */
    @GetMapping("/{sensorId}/timeseries")
    public ResponseEntity<List<SensorData>> getSensorTimeSeries(
            @PathVariable String sensorId,
            @RequestParam(defaultValue = "1s") String sampleBy) {
        
        TimeSeriesQueryCriteria criteria = TimeSeriesQueryCriteria.builder()
                .sensorId(sensorId)
                .sampleBy(sampleBy)
                .limit(3600)
                .build();

        if (timeSeriesDataSourceAdapter != null) {
            try {
                List<SensorData> adapterPoints = timeSeriesDataSourceAdapter.fetchTimeSeriesData(criteria);
                if (adapterPoints != null && !adapterPoints.isEmpty()) {
                    return ResponseEntity.ok(adapterPoints);
                }
            } catch (Exception ignored) {
                // 어댑터 미연결 또는 테이블 부재 시 합성 시계열 데이터로 안전하게 폴백
            }
        }

        List<SensorData> points = catalogService.generateTimeSeries(criteria);
        return ResponseEntity.ok(points);
    }
}
