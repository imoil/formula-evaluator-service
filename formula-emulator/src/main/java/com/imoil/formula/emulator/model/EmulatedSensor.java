package com.imoil.formula.emulator.model;

import com.imoil.formula.domain.SensorItem;
import lombok.Getter;

import java.util.Random;

/**
 * 1초 ~ 10초 주기로 변동을 일으키는 가상 IoT 센서 엔티티
 */
public class EmulatedSensor {

    @Getter
    private final int sensorIndex;
    private final double baseMean;
    private final double stdDev;
    private final int minIntervalSeconds;
    private final int maxIntervalSeconds;
    private final double downProbability;

    private double currentValue;
    private String currentStatus;
    private long nextChangeTimeMs;

    public EmulatedSensor(int sensorIndex,
                          int minIntervalSeconds,
                          int maxIntervalSeconds,
                          double downProbability,
                          long initialTimeMs,
                          Random rand) {
        this.sensorIndex = sensorIndex;
        this.minIntervalSeconds = minIntervalSeconds;
        this.maxIntervalSeconds = maxIntervalSeconds;
        this.downProbability = downProbability;

        // 센서 유형별 베이스라인 및 변동 폭 생성 (온도, 압력, 전력, 진동 등 모의)
        this.baseMean = 20.0 + (sensorIndex % 100) * 1.5;
        this.stdDev = Math.max(0.2, (sensorIndex % 20) * 0.1);
        this.currentValue = Math.round((baseMean + rand.nextGaussian() * stdDev) * 1000.0) / 1000.0;
        this.currentStatus = "normal";

        // 최초 변동 시점: 0초 ~ maxIntervalSeconds 사이 무작위 분산으로 스파이크 방지
        int initialDelaySec = minIntervalSeconds + rand.nextInt(maxIntervalSeconds - minIntervalSeconds + 1);
        this.nextChangeTimeMs = initialTimeMs + (long) initialDelaySec * 1000L;
    }

    /**
     * 1초 틱 시점에 변동 여부를 검사하고, 변동이 발생했을 경우 SensorItem을 반환합니다.
     */
    public SensorItem evaluateTick(long nowMs, Random rand) {
        if (nowMs < nextChangeTimeMs) {
            return null;
        }

        // 1. 센서 상태 전이 (normal <-> down)
        if ("down".equals(currentStatus)) {
            // 다운 상태에서는 80% 확률로 정상 복구
            if (rand.nextDouble() < 0.8) {
                currentStatus = "normal";
            }
        } else {
            // 평상시 낮은 확률로 다운 발생
            if (rand.nextDouble() < downProbability) {
                currentStatus = "down";
            }
        }

        // 2. 센서 측정값 변동 (정규분포 랜덤 워크)
        if ("down".equals(currentStatus)) {
            // 센서 다운 시 0 또는 에러성 값
            currentValue = 0.0;
        } else {
            double delta = rand.nextGaussian() * stdDev;
            double nextVal = currentValue + delta;
            // 기준치 대비 과도한 발산 억제
            if (Math.abs(nextVal - baseMean) > stdDev * 5) {
                nextVal = baseMean + rand.nextGaussian() * stdDev;
            }
            currentValue = Math.round(nextVal * 1000.0) / 1000.0;
        }

        // 3. 다음 변동 시점 예약 (1초 ~ 10초 사이)
        int nextIntervalSec = minIntervalSeconds + rand.nextInt(maxIntervalSeconds - minIntervalSeconds + 1);
        this.nextChangeTimeMs = nowMs + (long) nextIntervalSec * 1000L;

        return SensorItem.builder()
                .sensorIndex(sensorIndex)
                .value(currentValue)
                .status(currentStatus)
                .build();
    }
}
