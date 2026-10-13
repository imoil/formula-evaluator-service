package com.imoil.formula.service;

import com.imoil.formula.datasource.TimeSeriesQueryCriteria;
import com.imoil.formula.domain.SensorData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 100개의 가상 센서 카탈로그 및 1시간(3,600초) 분량 정규분포 시계열 데이터 생성 서비스.
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
        private double minClamp;
        private double maxClamp;
        private String description;
    }

    private final Map<String, SensorMeta> sensorCatalog = new LinkedHashMap<>();
    private static final String[] CATEGORIES = {
            "Temperature", "Pressure", "Vibration", "Electrical", "Flow", "Mechanical", "Environmental"
    };

    public MockSensorCatalogService() {
        initSensorCatalog();
    }

    private void initSensorCatalog() {
        for (int i = 0; i < 100; i++) {
            String id = String.format("sensor_%03d", i);
            String cat = CATEGORIES[i % CATEGORIES.length];

            String unit;
            double minMean;
            double maxMean;
            double stdRatio;

            switch (cat) {
                case "Temperature":
                    unit = "°C"; minMean = 45.0; maxMean = 110.0; stdRatio = 0.05;
                    break;
                case "Pressure":
                    unit = "bar"; minMean = 20.0; maxMean = 180.0; stdRatio = 0.04;
                    break;
                case "Vibration":
                    unit = "mm/s"; minMean = 2.5; maxMean = 28.0; stdRatio = 0.12;
                    break;
                case "Electrical":
                    unit = "V"; minMean = 210.0; maxMean = 480.0; stdRatio = 0.02;
                    break;
                case "Flow":
                    unit = "L/min"; minMean = 15.0; maxMean = 95.0; stdRatio = 0.06;
                    break;
                case "Mechanical":
                    unit = "RPM"; minMean = 900.0; maxMean = 3200.0; stdRatio = 0.03;
                    break;
                default:
                    unit = "%"; minMean = 35.0; maxMean = 75.0; stdRatio = 0.05;
                    break;
            }

            Random rand = new Random((long) (i + 1) * 7331L);
            double mean = Math.round((minMean + rand.nextDouble() * (maxMean - minMean)) * 10.0) / 10.0;
            double stdDev = Math.max(0.1, Math.round(mean * stdRatio * (0.8 + rand.nextDouble() * 0.4) * 100.0) / 100.0);

            sensorCatalog.put(id, SensorMeta.builder()
                    .id(id)
                    .name(cat + " Sensor #" + String.format("%03d", i))
                    .category(cat)
                    .unit(unit)
                    .mean(mean)
                    .stdDev(stdDev)
                    .minClamp(Math.max(0, mean - stdDev * 4))
                    .maxClamp(mean + stdDev * 4)
                    .description("High-frequency IoT " + cat.toLowerCase() + " telemetry with N(" + mean + ", " + stdDev + "²)")
                    .build());
        }
    }

    public List<SensorMeta> getAllSensorCatalog() {
        return new ArrayList<>(sensorCatalog.values());
    }

    public SensorMeta getSensorMeta(String sensorId) {
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
        long baseTimeMs = 1791576000000L; // 2026-10-09T20:00:00Z 기준

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
            subtleDrift *= 0.995;

            double rawVal = (meta.getMean() + subtleDrift) + rand.nextGaussian() * meta.getStdDev();
            if (rawVal < meta.getMinClamp()) rawVal = meta.getMinClamp();
            if (rawVal > meta.getMaxClamp()) rawVal = meta.getMaxClamp();

            double roundedVal = Math.round(rawVal * 1000.0) / 1000.0;

            points.add(SensorData.builder()
                    .sensorId(sensorId)
                    .timestamp(timestampMs)
                    .value(roundedVal)
                    .state(0)
                    .hasInaccurateData(false)
                    .build());
        }

        return points;
    }
}
