package com.imoil.formula.emulator.engine;

import com.imoil.formula.domain.SensorItem;
import com.imoil.formula.emulator.config.EmulatorConfig;
import com.imoil.formula.emulator.kafka.SensorKafkaProducer;
import com.imoil.formula.emulator.model.EmulatedSensor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 1000개의 IoT 센서를 관리하며 매 1초마다 변동된 센서를 집계하여 지연 없이 카프카로 전송하는 에뮬레이터 엔진
 */
@Slf4j
public class SensorDeviceEmulator implements Closeable {

    private final EmulatorConfig config;
    private final SensorKafkaProducer kafkaProducer;
    private final List<EmulatedSensor> sensors;
    private final ScheduledExecutorService scheduler;
    private final Random random;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong tickCount = new AtomicLong(0);
    private final AtomicLong totalChangedSensorsCount = new AtomicLong(0);

    @Getter
    private volatile long maxObservedJitterMs = 0;

    private ScheduledFuture<?> scheduledFuture;
    private long expectedNextTickMs = 0;

    public SensorDeviceEmulator(EmulatorConfig config, SensorKafkaProducer kafkaProducer) {
        this.config = config;
        this.kafkaProducer = kafkaProducer;
        this.sensors = new ArrayList<>(config.getSensorCount());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sensor-emulator-ticker");
            t.setDaemon(true);
            return t;
        });
        this.random = new Random();

        initSensors();
    }

    private void initSensors() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < config.getSensorCount(); i++) {
            sensors.add(new EmulatedSensor(
                    i,
                    config.getMinIntervalSeconds(),
                    config.getMaxIntervalSeconds(),
                    config.getDownProbability(),
                    now,
                    random
            ));
        }
        log.info("Initialized {} emulated sensors for device: {}", sensors.size(), config.getDeviceId());
    }

    /**
     * 에뮬레이터 루프 시작 (1초 주기 고정 레이트 실행)
     */
    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            expectedNextTickMs = System.currentTimeMillis();
            scheduledFuture = scheduler.scheduleAtFixedRate(
                    this::safeTick,
                    0,
                    config.getEmitIntervalMillis(),
                    TimeUnit.MILLISECONDS
            );
            log.info("SensorDeviceEmulator started with interval {}ms for device {}",
                    config.getEmitIntervalMillis(), config.getDeviceId());
        }
    }

    private void safeTick() {
        try {
            tick();
        } catch (Throwable t) {
            log.error("Unexpected error in emulator tick", t);
        }
    }

    /**
     * 1초 단위 틱 수행:
     * 1. 틱 드리프트/지연 측정
     * 2. 변동된 센서 수집
     * 3. 카프카 비동기 전송
     */
    public List<SensorItem> tick() {
        long now = System.currentTimeMillis();

        // 지연(Jitter / Drift) 측정
        if (expectedNextTickMs > 0) {
            long jitter = Math.max(0, now - expectedNextTickMs);
            if (jitter > maxObservedJitterMs) {
                maxObservedJitterMs = jitter;
            }
            if (jitter > 100) {
                log.warn("Tick jitter detected: {} ms behind schedule", jitter);
            }
        }
        expectedNextTickMs = now + config.getEmitIntervalMillis();

        long tickStartNs = System.nanoTime();

        // 1. 1000개 센서 평가 및 변동된 센서 수집
        List<SensorItem> changedSensors = new ArrayList<>();
        for (EmulatedSensor sensor : sensors) {
            SensorItem item = sensor.evaluateTick(now, random);
            if (item != null) {
                changedSensors.add(item);
            }
        }

        // 2. 변동된 센서가 존재할 경우 카프카에 전송
        if (!changedSensors.isEmpty()) {
            kafkaProducer.sendSensorBatchAsync(config.getDeviceId(), now, changedSensors);
            totalChangedSensorsCount.addAndGet(changedSensors.size());
        }

        long currentTick = tickCount.incrementAndGet();
        long elapsedMicros = (System.nanoTime() - tickStartNs) / 1_000L;

        if (currentTick % 5 == 0 || log.isDebugEnabled()) {
            log.info("Tick #{}: {}/{} sensors changed, processed in {} \u00b5s (device: {})",
                    currentTick, changedSensors.size(), sensors.size(), elapsedMicros, config.getDeviceId());
        }

        return changedSensors;
    }

    public long getTickCount() {
        return tickCount.get();
    }

    public long getTotalChangedSensorsCount() {
        return totalChangedSensorsCount.get();
    }

    public boolean isRunning() {
        return running.get();
    }

    @Override
    public synchronized void close() {
        if (running.compareAndSet(true, false)) {
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
            }
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                scheduler.shutdownNow();
            }
            kafkaProducer.close();
            log.info("SensorDeviceEmulator stopped. Total ticks: {}, total changed sensor events: {}",
                    tickCount.get(), totalChangedSensorsCount.get());
        }
    }
}
