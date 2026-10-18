package com.imoil.formula.api;

import com.imoil.formula.datasource.QuestDBDataSourceAdapter;
import com.imoil.formula.datasource.TimeSeriesDataSourceAdapter;
import com.imoil.formula.datasource.TimeSeriesQueryCriteria;
import com.imoil.formula.domain.SensorData;
import com.imoil.formula.service.MockSensorCatalogService;
import com.imoil.formula.service.MockSensorCatalogService.SensorMeta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 프론트엔드 UI 대시보드 및 온디맨드 수식 평가를 위한 센서 메타데이터 및 샘플 시계열 데이터 조회 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/sensors")
@CrossOrigin(origins = "*", exposedHeaders = {"X-Data-Source"})
public class SensorController {

    private final MockSensorCatalogService catalogService;
    private final TimeSeriesDataSourceAdapter timeSeriesDataSourceAdapter;
    private final String questDbUrl;

    @Autowired
    public SensorController(
            MockSensorCatalogService catalogService,
            @Autowired(required = false) TimeSeriesDataSourceAdapter timeSeriesDataSourceAdapter,
            @Value("${questdb.client.url:http::addr=localhost:9000;}") String questDbUrl) {
        this.catalogService = catalogService;
        this.timeSeriesDataSourceAdapter = timeSeriesDataSourceAdapter;
        this.questDbUrl = questDbUrl;
    }

    /**
     * QuestDB 연결 상태 및 Web Console 접근 URL을 반환합니다.
     */
    @GetMapping("/questdb/status")
    public ResponseEntity<Map<String, Object>> getQuestDbStatus() {
        boolean connected = false;
        String webConsoleUrl = "http://localhost:9000";
        if (questDbUrl != null && questDbUrl.contains("addr=")) {
            String addrPart = questDbUrl.substring(questDbUrl.indexOf("addr=") + 5);
            int semi = addrPart.indexOf(';');
            if (semi > 0) {
                addrPart = addrPart.substring(0, semi);
            }
            webConsoleUrl = "http://" + addrPart;
        }

        if (timeSeriesDataSourceAdapter instanceof QuestDBDataSourceAdapter questDbAdapter) {
            connected = questDbAdapter.isConnected();
        }

        // 소켓 기반 빠른 포트 연결 검증 (ILP/Web 콘솔 포트 9000 확인)
        if (!connected) {
            try {
                URI uri = URI.create(webConsoleUrl);
                String host = uri.getHost() != null ? uri.getHost() : "localhost";
                int port = uri.getPort() > 0 ? uri.getPort() : 9000;
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, port), 400);
                    connected = true;
                }
            } catch (Exception ignored) {
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", connected ? "ONLINE" : "OFFLINE",
                "connected", connected,
                "webConsoleUrl", webConsoleUrl
        ));
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
     * 특정 센서의 원본 시계열 데이터를 반환합니다.
     * 활성화된 TimeSeriesDataSourceAdapter(QuestDB)를 우선 조회하고, 데이터가 없거나 오프라인인 경우 합성 카탈로그로 폴백합니다.
     * 응답 헤더 X-Data-Source(QUESTDB 또는 MOCK)를 통해 데이터 원천을 명시합니다.
     */
    @GetMapping("/{sensorId}/timeseries")
    public ResponseEntity<List<SensorData>> getSensorTimeSeries(
            @PathVariable String sensorId,
            @RequestParam(defaultValue = "1s") String sampleBy,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "false") boolean latest,
            @RequestParam(defaultValue = "3600") int limit) {

        // 1. 실시간 최신 포인트 단건/소량 요청 처리
        if (latest) {
            if (timeSeriesDataSourceAdapter instanceof QuestDBDataSourceAdapter questDbAdapter) {
                try {
                    List<SensorData> latestPoints = questDbAdapter.fetchLatestPoints(sensorId, limit > 0 ? limit : 1);
                    if (latestPoints != null && !latestPoints.isEmpty()) {
                        return ResponseEntity.ok()
                                .header("X-Data-Source", "QUESTDB")
                                .body(latestPoints);
                    }
                } catch (Exception ignored) {
                }
            }
            return ResponseEntity.ok()
                    .header("X-Data-Source", "MOCK")
                    .body(List.of(catalogService.generateLatestPoint(sensorId)));
        }

        // 2. 시간 범위 또는 히스토리 시계열 데이터 조회
        TimeSeriesQueryCriteria criteria = TimeSeriesQueryCriteria.builder()
                .sensorId(sensorId)
                .sampleBy(sampleBy)
                .startTime(startTime)
                .endTime(endTime)
                .limit(limit)
                .build();

        if (timeSeriesDataSourceAdapter != null) {
            try {
                List<SensorData> adapterPoints = timeSeriesDataSourceAdapter.fetchTimeSeriesData(criteria);
                if (adapterPoints != null && !adapterPoints.isEmpty()) {
                    return ResponseEntity.ok()
                            .header("X-Data-Source", "QUESTDB")
                            .body(adapterPoints);
                }
                if (timeSeriesDataSourceAdapter instanceof QuestDBDataSourceAdapter questDbAdapter) {
                    List<SensorData> latestPoints = questDbAdapter.fetchLatestPoints(sensorId, limit);
                    if (latestPoints != null && !latestPoints.isEmpty()) {
                        return ResponseEntity.ok()
                                .header("X-Data-Source", "QUESTDB")
                                .body(latestPoints);
                    }
                }
            } catch (Exception ignored) {
                // 어댑터 미연결 또는 테이블 부재 시 합성 시계열 데이터로 안전하게 폴백
            }
        }

        List<SensorData> points = catalogService.generateTimeSeries(criteria);
        return ResponseEntity.ok()
                .header("X-Data-Source", "MOCK")
                .body(points);
    }
}
