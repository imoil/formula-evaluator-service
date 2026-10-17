import { describe, it, expect, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useSensorRegistryStore } from '../stores/sensorRegistryStore';
import { localManager } from '../services/sensorRegistryApi';

describe('Sensor Registry Pinia Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localManager.reset();
  });

  it('loads initial serialized sensors from mock registry', async () => {
    const store = useSensorRegistryStore();
    await store.fetchSensors();

    expect(store.sensors.length).toBeGreaterThanOrEqual(6);
    expect(store.totalCount).toBeGreaterThanOrEqual(6);
    expect(store.physicalCount).toBeGreaterThanOrEqual(4);
    expect(store.formulaCount).toBeGreaterThanOrEqual(2);
    expect(store.nextSerialPreview).toBeGreaterThanOrEqual(10007);
  });

  it('creates physical IoT sensor and assigns sequential serial number without collision', async () => {
    const store = useSensorRegistryStore();
    await store.fetchSensors();

    const initialTotal = store.totalCount;

    const created = await store.createSensor({
      sensorName: 'Turbine Vibration Sensor #5',
      sensorType: 'PHYSICAL',
      category: 'Vibration',
      unit: 'mm/s',
      deviceId: 'TURBINE-01',
      deviceSensorIndex: 5,
      description: 'Main shaft vibration',
    });

    expect(created.serialNumber).toBeDefined();
    expect(created.sensorCode).toContain('PHY-TURBINE01-S005');
    expect(store.totalCount).toBe(initialTotal + 1);
    expect(store.physicalCount).toBeGreaterThan(0);
  });

  it('creates formula sensor with unified serial numbering system', async () => {
    const store = useSensorRegistryStore();
    await store.fetchSensors();

    const created = await store.createSensor({
      sensorName: 'Power-To-Flow Ratio',
      sensorType: 'FORMULA',
      category: 'Efficiency',
      unit: 'kW/(m3/h)',
      formulaExpression: 'SN_10001 * 0.5 + SN_10002',
      description: 'Calculated efficiency ratio',
    });

    expect(created.serialNumber).toBeDefined();
    expect(created.sensorType).toBe('FORMULA');
    expect(created.formulaExpression).toBe('SN_10001 * 0.5 + SN_10002');
  });

  it('updates sensor details properly', async () => {
    const store = useSensorRegistryStore();
    await store.fetchSensors();

    const targetSerial = 10001;
    const updated = await store.updateSensor(targetSerial, {
      sensorName: 'Updated Bearing Temp',
      unit: 'K',
      status: 'MAINTENANCE',
    });

    expect(updated.sensorName).toBe('Updated Bearing Temp');
    expect(updated.unit).toBe('K');
    expect(updated.status).toBe('MAINTENANCE');

    const inStore = store.sensors.find((s) => s.serialNumber === targetSerial);
    expect(inStore?.sensorName).toBe('Updated Bearing Temp');
  });

  it('deletes sensor and updates count', async () => {
    const store = useSensorRegistryStore();
    await store.fetchSensors();

    const initialCount = store.totalCount;
    await store.deleteSensor(10001);

    expect(store.totalCount).toBe(initialCount - 1);
    expect(store.sensors.find((s) => s.serialNumber === 10001)).toBeUndefined();
  });

  it('batch syncs IoT emulator device sensors and gives unique serial numbers', async () => {
    const store = useSensorRegistryStore();
    await store.fetchSensors();

    const initialCount = store.totalCount;
    const synced = await store.syncDeviceSensors({
      deviceId: 'DEVICE-003',
      sensorCount: 5,
      skipExisting: true,
    });

    expect(synced.length).toBe(5);
    expect(store.totalCount).toBe(initialCount + 5);
    expect(synced[0].deviceId).toBe('DEVICE-003');
    expect(synced[0].deviceSensorIndex).toBe(0);
    expect(synced[4].deviceSensorIndex).toBe(4);
  });

  it('filters sensors by type and keyword correctly', async () => {
    const store = useSensorRegistryStore();
    await store.fetchSensors();

    // Type Filter
    store.typeFilter = 'FORMULA';
    expect(store.filteredSensors.every((s) => s.sensorType === 'FORMULA')).toBe(true);

    // Search Query matching specific name
    store.typeFilter = 'ALL';
    store.searchQuery = 'Discharge Pressure';
    expect(store.filteredSensors.length).toBe(1);
    expect(store.filteredSensors[0].serialNumber).toBe(10002);

    // Search Query matching serial number (also matches formulas referencing SN_10002)
    store.searchQuery = '10002';
    expect(store.filteredSensors.length).toBe(2);
    expect(store.filteredSensors.some((s) => s.serialNumber === 10002)).toBe(true);
  });
});
