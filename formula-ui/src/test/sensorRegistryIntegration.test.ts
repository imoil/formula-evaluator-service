import { describe, it, expect, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useSensorRegistryStore } from '../stores/sensorRegistryStore';
import { useSensorStore } from '../stores/sensorStore';
import { localManager } from '../services/sensorRegistryApi';

describe('Unified Sensor Registry & On-Demand UI Integration', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localManager.reset();
  });

  it('integrates Oracle 19c registry sensors into the On-Demand sensor store catalog', async () => {
    const registryStore = useSensorRegistryStore();
    const sensorStore = useSensorStore();

    // 1. Fetch registry sensors
    await registryStore.fetchSensors();

    // 2. Load On-Demand sensor store
    await sensorStore.loadSensors(true);

    expect(sensorStore.sensors.length).toBeGreaterThanOrEqual(106);

    // Verify Oracle 19c serialized sensor exists in on-demand sensor list
    const serial10001 = sensorStore.getSensorById('10001');
    expect(serial10001).toBeDefined();
    expect(serial10001?.name).toContain('[#10001]');
    expect(serial10001?.category).toBe('Temperature');

    const serial10002 = sensorStore.getSensorById('10002');
    expect(serial10002).toBeDefined();
    expect(serial10002?.name).toContain('[#10002]');
    expect(serial10002?.category).toBe('Pressure');
  });

  it('generates 1-hour 3600-point time-series telemetry for Oracle serialized sensors', async () => {
    const sensorStore = useSensorStore();
    await sensorStore.loadSensors(true);

    // Fetch time-series for serialized sensor 10001
    const points = await sensorStore.fetchTimeSeries('10001', 3600);
    expect(points.length).toBe(3600);
    expect(points[0].sensorId).toBe('10001');
    expect(points[0].value).toBeGreaterThan(0);
    expect(points[0].timestampMs).toBeDefined();
  });

  it('reflects new sensor creations into On-Demand sensor store dynamically', async () => {
    const registryStore = useSensorRegistryStore();
    const sensorStore = useSensorStore();

    await registryStore.fetchSensors();
    await sensorStore.loadSensors(true);

    const initialTotal = sensorStore.sensors.length;

    // Create a new physical sensor in registry
    const created = await registryStore.createSensor({
      sensorName: 'High-Pressure Turbine #10',
      sensorType: 'PHYSICAL',
      category: 'Pressure',
      unit: 'bar',
      deviceId: 'TURBINE-01',
      deviceSensorIndex: 10,
    });

    // Verify it is dynamically accessible in On-Demand sensorStore
    const inStore = sensorStore.getSensorById(String(created.serialNumber));
    expect(inStore).toBeDefined();
    expect(inStore?.name).toContain('High-Pressure Turbine #10');
    expect(sensorStore.sensors.length).toBe(initialTotal + 1);
  });
});
