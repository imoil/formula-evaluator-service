import { describe, it, expect, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useSensorRegistryStore } from '../stores/sensorRegistryStore';
import { useSensorStore } from '../stores/sensorStore';
import { useFormulaStore } from '../stores/formulaStore';
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

  it('unifies Formula Sensors in Unified Sensor Registry with formulaStore', async () => {
    const registryStore = useSensorRegistryStore();
    const formulaStore = useFormulaStore();

    await registryStore.fetchSensors();

    // All 5 default formula sensors should be present in registryStore and derived in formulaStore
    expect(registryStore.formulaCount).toBe(5);
    expect(formulaStore.formulas.length).toBe(5);
    expect(formulaStore.getFormulaById('FML-PUMP-EFF-01')).toBeDefined();
    expect(formulaStore.getFormulaById('TEMP_FAHRENHEIT')).toBeDefined();
    expect(formulaStore.getFormulaById('POWER_EFFICIENCY_INDEX')).toBeDefined();

    // Create a new formula sensor in Unified Sensor Registry
    const createdFormula = await registryStore.createSensor({
      sensorName: 'Custom Combined Thermal Index',
      sensorCode: 'CUSTOM_THERMAL_IDX',
      sensorType: 'FORMULA',
      category: 'Thermal',
      unit: 'idx',
      formulaExpression: 'TEMP_FAHRENHEIT * 0.5 + SN_10001',
      description: 'Derived formula using both sub-formula code and serial sensor',
    });

    expect(registryStore.formulaCount).toBe(6);
    expect(formulaStore.formulas.length).toBe(6);

    const derived = formulaStore.getFormulaById('CUSTOM_THERMAL_IDX');
    expect(derived).toBeDefined();
    expect(derived?.serialNumber).toBe(createdFormula.serialNumber);

    const deps = formulaStore.getDependencies(derived!.id);
    expect(deps.referencedFormulas).toContain('TEMP_FAHRENHEIT');
    expect(deps.referencedSensors).toContain('10001');

    // Duplicate formula sensor in Unified Sensor Registry
    const duplicated = await registryStore.duplicateFormulaSensor(createdFormula.serialNumber);
    expect(duplicated.sensorType).toBe('FORMULA');
    expect(registryStore.formulaCount).toBe(7);
    expect(formulaStore.formulas.length).toBe(7);
  });
});
