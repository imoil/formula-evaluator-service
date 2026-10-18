package com.imoil.formula.service;

import com.imoil.formula.datasource.TimeSeriesQueryCriteria;
import com.imoil.formula.domain.SensorData;
import com.imoil.formula.registry.entity.SensorRegistryEntity;
import com.imoil.formula.registry.repository.SensorRegistryRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 100개의 가상 센서 카탈로그 및 Oracle 19c SENSOR_REGISTRY 영속성 센서와 통합된
 * 1시간(3,600초) 분량 정규분포 시계열 데이터 생성 서비스.
 * Controller와 Mock DataSource Adapter에서 공통으로 활용됩니다.
 */
@Slf4j
@Service
public class MockSensorCatalogService {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SensorMeta {
        private String id;
        private String name;
        private String category;
        private String unit;
        private double mean;
        private double stdDev;
        private Double minClamp;
        private Double maxClamp;
        private String description;
        private Long serialNumber;
        private String deviceId;
        private Integer deviceSensorIndex;
        private String sensorType; // PHYSICAL or FORMULA
    }

    private record CategorySpec(
            String unit,
            double minMean,
            double maxMean,
            double stdRatio,
            double registryMean,
            double registryStdDev
    ) {}

    private static final CategorySpec DEFAULT_SPEC =
            new CategorySpec("%", 35.0, 75.0, 0.05, 50.0, 2.5);

    private static final Map<String, CategorySpec> CATEGORY_SPECS = Map.of(
            "temperature", new CategorySpec("°C", 45.0, 110.0, 0.05, 65.0, 3.0),
            "pressure", new CategorySpec("bar", 20.0, 180.0, 0.04, 100.0, 4.0),
            "vibration", new CategorySpec("mm/s", 2.5, 28.0, 0.12, 12.0, 1.2),
            "electrical", new CategorySpec("V", 210.0, 480.0, 0.02, 220.0, 5.0),
            "flow", new CategorySpec("L/min", 15.0, 95.0, 0.06, 45.0, 2.0),
            "mechanical", new CategorySpec("RPM", 900.0, 3200.0, 0.03, 50.0, 2.5),
            "efficiency", new CategorySpec("%", 70.0, 98.0, 0.03, 88.0, 2.0)
    );

    private final Map<String, SensorMeta> sensorCatalog = new LinkedHashMap<>();
    private static final String[] CATEGORIES = {
            "Temperature", "Pressure", "Vibration", "Electrical", "Flow", "Mechanical", "Environmental"
    };

    @Autowired(required = false)
    private SensorRegistryRepository sensorRegistryRepository;

    public MockSensorCatalogService() {
        initSensorCatalog();
    }

    private static CategorySpec specForCategory(String category) {
        if (category == null) {
            return DEFAULT_SPEC;
        }
        return CATEGORY_SPECS.getOrDefault(category.trim().toLowerCase(Locale.ROOT), DEFAULT_SPEC);
    }

    private void initSensorCatalog() {
        for (int i = 0; i < 100; i++) {
            String id = String.format("sensor_%03d", i);
            String cat = CATEGORIES[i % CATEGORIES.length];
            CategorySpec spec = specForCategory(cat);

            Random rand = new Random((long) (i + 1) * 7331L);
            double mean = Math.round((spec.minMean() + rand.nextDouble() * (spec.maxMean() - spec.minMean())) * 10.0) / 10.0;
            double stdDev = Math.max(0.1, Math.round(mean * spec.stdRatio() * (0.8 + rand.nextDouble() * 0.4) * 100.0) / 100.0);

            sensorCatalog.put(id, SensorMeta.builder()
                    .id(id)
                    .name(cat + " Sensor #" + String.format("%03d", i))
                    .category(cat)
                    .unit(spec.unit())
                    .mean(mean)
                    .stdDev(stdDev)
                    .minClamp(Math.max(0.0, mean - stdDev * 4))
                    .maxClamp(mean + stdDev * 4)
                    .description("High-frequency IoT " + cat.toLowerCase() + " telemetry with N(" + mean + ", " + stdDev + "²)")
                    .build());
        }
    }

    /**
     * Oracle 19c SENSOR_REGISTRY의 영속성 센서들과 기본 가상 센서 카탈로그를 통합하여 반환
     */
    public List<SensorMeta> getAllSensorCatalog() {
        List<SensorMeta> merged = new ArrayList<>();

        // 1. Oracle 19c SENSOR_REGISTRY 센서들을 우선적으로 통합
        if (sensorRegistryRepository != null) {
            try {
                List<SensorRegistryEntity> entities = sensorRegistryRepository.findAll();
                for (SensorRegistryEntity entity : entities) {
                    merged.add(convertEntityToMeta(entity));
                }
            } catch (Exception e) {
                log.debug("Could not fetch entities from SensorRegistryRepository: {}", e.getMessage());
            }
        }

        // 2. 기존 기본 카탈로그 병합 (아이디 중복 방지)
        Set<String> existingIds = new HashSet<>();
        for (SensorMeta m : merged) {
            existingIds.add(m.getId());
        }

        for (SensorMeta m : sensorCatalog.values()) {
            if (!existingIds.contains(m.getId())) {
                merged.add(m);
            }
        }

        return merged;
    }

    private SensorMeta convertEntityToMeta(SensorRegistryEntity entity) {
        String id = entity.getSerialNumber() != null ? String.valueOf(entity.getSerialNumber()) : entity.getSensorCode();
        String cat = entity.getCategory() != null ? entity.getCategory() : "General";
        String unit = entity.getUnit() != null ? entity.getUnit() : "";

        CategorySpec spec = specForCategory(cat);
        double mean = spec.registryMean();
        double stdDev = spec.registryStdDev();

        return SensorMeta.builder()
                .id(id)
                .name(entity.getSensorName())
                .category(cat)
                .unit(unit)
                .mean(mean)
                .stdDev(stdDev)
                .minClamp(Math.max(0.0, mean - stdDev * 4))
                .maxClamp(mean + stdDev * 4)
                .description(entity.getDescription() != null ? entity.getDescription() : "Oracle 19c Registered Sensor")
                .serialNumber(entity.getSerialNumber())
                .deviceId(entity.getDeviceId())
                .deviceSensorIndex(entity.getDeviceSensorIndex())
                .sensorType(entity.getSensorType() != null ? entity.getSensorType().name() : "PHYSICAL")
                .build();
    }

    public SensorMeta getSensorMeta(String sensorId) {
        // 1. Oracle 19c SENSOR_REGISTRY 조회 시도 (일련번호 또는 센서코드)
        if (sensorRegistryRepository != null && sensorId != null) {
            try {
                Optional<Long> parsedSerial = com.imoil.formula.domain.SensorSerialResolver.tryParseSerialNumber(sensorId);
                if (parsedSerial.isPresent()) {
                    Optional<SensorRegistryEntity> ent = sensorRegistryRepository.findBySerialNumber(parsedSerial.get());
                    if (ent.isPresent()) {
                        return convertEntityToMeta(ent.get());
                    }
                }

                Optional<SensorRegistryEntity> byCode = sensorRegistryRepository.findBySensorCode(sensorId);
                if (byCode.isPresent()) {
                    return convertEntityToMeta(byCode.get());
                }
            } catch (Exception e) {
                log.debug("Sensor lookup in registry failed: {}", e.getMessage());
            }
        }

        // 2. 기본 mock 카탈로그 조회
        return sensorCatalog.getOrDefault(sensorId, SensorMeta.builder()
                .id(sensorId)
                .name("Generic Sensor " + sensorId)
                .category("Mechanical")
                .unit("units")
                .mean(100.0)
                .stdDev(5.0)
                .minClamp(0.0)
                .maxClamp(200.0)
                .description("Fallback mock sensor")
                .build());
    }

    public List<SensorData> generateTimeSeries(TimeSeriesQueryCriteria criteria) {
        String sensorId = criteria.getSensorId();
        SensorMeta meta = getSensorMeta(sensorId);

        int durationSeconds = criteria.getLimit() > 0 ? criteria.getLimit() : 3600;
        long baseTimeMs = parseStartTime(criteria.getStartTime(), durationSeconds);

        long seed = 42L;
        for (char c : sensorId.toCharArray()) {
            seed = seed * 31L + c;
        }
        Random rand = new Random(seed);

        List<SensorData> points = new ArrayList<>(durationSeconds);
        double subtleDrift = 0.0;

        for (int s = 0; s < durationSeconds; s++) {
            long timestampMs = baseTimeMs + (long) s * 1000L;

            subtleDrift += (rand.nextDouble() - 0.5) * (meta.getStdDev() * 0.05);
            double base = meta.getMean() + subtleDrift;
            double noise = rand.nextGaussian() * meta.getStdDev();
            double val = base + noise;

            if (meta.getMinClamp() != null && val < meta.getMinClamp()) {
                val = meta.getMinClamp();
            }
            if (meta.getMaxClamp() != null && val > meta.getMaxClamp()) {
                val = meta.getMaxClamp();
            }

            val = Math.round(val * 100.0) / 100.0;

            int state = (s > 1000 && s < 1015) ? 1 : 0;
            boolean inaccurate = (s > 2500 && s < 2510);

            points.add(SensorData.builder()
                    .sensorId(sensorId)
                    .deviceId(meta.getDeviceId())
                    .sensorIndex(meta.getDeviceSensorIndex())
                    .timestamp(timestampMs)
                    .value(val)
                    .state(state)
                    .hasInaccurateData(inaccurate)
                    .build());
        }

        return points;
    }

    /**
     * 특정 센서의 현재 시각 기준 단일 실시간 가상 데이터 포인트를 생성합니다.
     */
    public SensorData generateLatestPoint(String sensorId) {
        SensorMeta meta = getSensorMeta(sensorId);
        long now = System.currentTimeMillis();
        Random rand = new Random();
        double val = meta.getMean() + rand.nextGaussian() * meta.getStdDev();
        if (meta.getMinClamp() != null && val < meta.getMinClamp()) {
            val = meta.getMinClamp();
        }
        if (meta.getMaxClamp() != null && val > meta.getMaxClamp()) {
            val = meta.getMaxClamp();
        }
        val = Math.round(val * 100.0) / 100.0;

        return SensorData.builder()
                .sensorId(sensorId)
                .deviceId(meta.getDeviceId())
                .sensorIndex(meta.getDeviceSensorIndex())
                .timestamp(now)
                .value(val)
                .state(0)
                .hasInaccurateData(false)
                .build();
    }

    private long parseStartTime(String startTime, int durationSeconds) {
        if (startTime != null && !startTime.isBlank()) {
            try {
                String trimmed = startTime.trim();
                if (trimmed.endsWith("Z") || trimmed.contains("+")) {
                    return java.time.Instant.parse(trimmed).toEpochMilli();
                }
                java.time.LocalDateTime ldt;
                if (trimmed.contains("T")) {
                    ldt = java.time.LocalDateTime.parse(trimmed);
                } else if (trimmed.contains(" ")) {
                    ldt = java.time.LocalDateTime.parse(trimmed, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                } else {
                    ldt = java.time.LocalDateTime.parse(trimmed, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
                return ldt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (Exception ignored) {
            }
        }
        return java.time.LocalDateTime.now().minusSeconds(durationSeconds)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}

