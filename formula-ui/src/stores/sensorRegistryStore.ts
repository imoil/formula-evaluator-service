import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type {
  SensorRegistryItem,
  SensorType,
  CreateSensorPayload,
  UpdateSensorPayload,
  DeviceSyncPayload,
} from '../types/sensorRegistry';
import {
  sensorRegistryApi,
  getDefaultPhysicalSeedPayloads,
  getDefaultFormulaSeedPayloads,
} from '../services/sensorRegistryApi';

export const useSensorRegistryStore = defineStore('sensorRegistry', () => {
  const sensors = ref<SensorRegistryItem[]>([]);
  const isLoading = ref<boolean>(false);
  const syncingRules = ref<boolean>(false);
  const isBackendConnected = ref<boolean>(false);
  const errorMessage = ref<string | null>(null);
  const hasEnsuredDefaultSeed = ref<boolean>(false);

  const searchQuery = ref<string>('');
  const typeFilter = ref<SensorType | 'ALL'>('ALL');
  const deviceFilter = ref<string>('ALL');
  const nextSerialPreview = ref<number>(10001);

  // Getters
  const totalCount = computed(() => sensors.value.length);
  const physicalCount = computed(
    () => sensors.value.filter((s) => s.sensorType === 'PHYSICAL').length
  );
  const formulaCount = computed(
    () => sensors.value.filter((s) => s.sensorType === 'FORMULA').length
  );

  const availableDevices = computed(() => {
    const set = new Set<string>();
    sensors.value.forEach((s) => {
      if (s.deviceId) set.add(s.deviceId);
    });
    return Array.from(set).sort();
  });

  const isFilterActive = computed(() => {
    const rawQuery = searchQuery.value ?? '';
    const hasSearch = typeof rawQuery === 'string' && rawQuery.trim() !== '';
    const hasType = typeFilter.value !== 'ALL';
    const hasDevice = deviceFilter.value !== 'ALL';
    return hasSearch || hasType || hasDevice;
  });

  const filteredSensors = computed(() => {
    const rawQuery = searchQuery.value ?? '';
    const q = typeof rawQuery === 'string' ? rawQuery.trim().toLowerCase() : '';

    return sensors.value.filter((s) => {
      if (typeFilter.value !== 'ALL' && s.sensorType !== typeFilter.value) {
        return false;
      }
      if (deviceFilter.value !== 'ALL' && s.deviceId !== deviceFilter.value) {
        return false;
      }
      if (q !== '') {
        const matchesSerial = s.serialNumber.toString().includes(q);
        const matchesCode = s.sensorCode.toLowerCase().includes(q);
        const matchesName = s.sensorName.toLowerCase().includes(q);
        const matchesCategory = s.category.toLowerCase().includes(q);
        const matchesDevice = s.deviceId?.toLowerCase().includes(q) ?? false;
        const matchesFormula = s.formulaExpression?.toLowerCase().includes(q) ?? false;

        return (
          matchesSerial ||
          matchesCode ||
          matchesName ||
          matchesCategory ||
          matchesDevice ||
          matchesFormula
        );
      }
      return true;
    });
  });

  function resetFilters() {
    searchQuery.value = '';
    typeFilter.value = 'ALL';
    deviceFilter.value = 'ALL';
  }

  function clearSearchQuery() {
    searchQuery.value = '';
  }

  // Actions
  async function fetchSensors() {
    isLoading.value = true;
    errorMessage.value = null;
    try {
      let res = await sensorRegistryApi.fetchSensors();

      if (res.isBackend && !hasEnsuredDefaultSeed.value) {
        hasEnsuredDefaultSeed.value = true;
        const hasPhysical = res.data.some((s) => s.sensorType === 'PHYSICAL');
        const hasFormula = res.data.some((s) => s.sensorType === 'FORMULA');

        if (!hasPhysical || !hasFormula) {
          if (!hasPhysical) {
            for (const payload of getDefaultPhysicalSeedPayloads()) {
              try {
                await sensorRegistryApi.createSensor(payload);
              } catch {
                // ignore duplicate seed error
              }
            }
          }
          if (!hasFormula) {
            for (const payload of getDefaultFormulaSeedPayloads()) {
              try {
                await sensorRegistryApi.createSensor(payload);
              } catch {
                // ignore duplicate seed error
              }
            }
          }
          res = await sensorRegistryApi.fetchSensors();
        }
      }

      sensors.value = res.data;
      isBackendConnected.value = res.isBackend;

      // Update next serial preview
      await fetchNextSerial();
    } catch (err: unknown) {
      errorMessage.value = err instanceof Error ? err.message : '센서 목록 조회 실패';
    } finally {
      isLoading.value = false;
    }
  }

  async function fetchNextSerial() {
    try {
      const next = await sensorRegistryApi.getNextSerialPreview();
      nextSerialPreview.value = next;
    } catch {
      // ignore
    }
  }

  async function createSensor(payload: CreateSensorPayload): Promise<SensorRegistryItem> {
    isLoading.value = true;
    errorMessage.value = null;
    try {
      const created = await sensorRegistryApi.createSensor(payload);
      sensors.value.push(created);
      await fetchNextSerial();
      return created;
    } catch (err: unknown) {
      errorMessage.value = err instanceof Error ? err.message : '센서 등록 실패';
      throw err;
    } finally {
      isLoading.value = false;
    }
  }

  async function duplicateFormulaSensor(
    sensorOrSerial: SensorRegistryItem | number
  ): Promise<SensorRegistryItem> {
    const sensor =
      typeof sensorOrSerial === 'number'
        ? sensors.value.find((s) => s.serialNumber === sensorOrSerial)
        : sensorOrSerial;
    if (!sensor || sensor.sensorType !== 'FORMULA') {
      throw new Error('Only formula sensors can be duplicated as formulas.');
    }
    const baseCode = sensor.sensorCode.replace(/[^A-Za-z0-9_-]/g, '_');
    let suffix = 1;
    let candidateCode = `${baseCode}_COPY`;
    while (sensors.value.some((s) => s.sensorCode.toUpperCase() === candidateCode.toUpperCase())) {
      suffix++;
      candidateCode = `${baseCode}_COPY_${suffix}`;
    }
    return createSensor({
      sensorCode: candidateCode,
      sensorName: `${sensor.sensorName} (Copy)`,
      sensorType: 'FORMULA',
      category: sensor.category ?? undefined,
      unit: sensor.unit ?? undefined,
      formulaExpression: sensor.formulaExpression || 'value * 1.0',
      description: sensor.description ?? undefined,
      status: sensor.status,
    });
  }

  async function updateSensor(
    serialNumber: number,
    payload: UpdateSensorPayload
  ): Promise<SensorRegistryItem> {
    isLoading.value = true;
    errorMessage.value = null;
    try {
      const updated = await sensorRegistryApi.updateSensor(serialNumber, payload);
      const idx = sensors.value.findIndex((s) => s.serialNumber === serialNumber);
      if (idx !== -1) {
        sensors.value[idx] = updated;
      }
      return updated;
    } catch (err: unknown) {
      errorMessage.value = err instanceof Error ? err.message : '센서 수정 실패';
      throw err;
    } finally {
      isLoading.value = false;
    }
  }

  async function deleteSensor(serialNumber: number): Promise<void> {
    isLoading.value = true;
    errorMessage.value = null;
    try {
      await sensorRegistryApi.deleteSensor(serialNumber);
      sensors.value = sensors.value.filter((s) => s.serialNumber !== serialNumber);
      await fetchNextSerial();
    } catch (err: unknown) {
      errorMessage.value = err instanceof Error ? err.message : '센서 삭제 실패';
      throw err;
    } finally {
      isLoading.value = false;
    }
  }

  async function syncDeviceSensors(payload: DeviceSyncPayload): Promise<SensorRegistryItem[]> {
    isLoading.value = true;
    errorMessage.value = null;
    try {
      const added = await sensorRegistryApi.syncDeviceSensors(payload);
      await fetchSensors();
      return added;
    } catch (err: unknown) {
      errorMessage.value = err instanceof Error ? err.message : 'IoT 장치 동기화 실패';
      throw err;
    } finally {
      isLoading.value = false;
    }
  }

  /**
   * DB에 등록된 활성 수식 센서 룰을 Kafka rule-data 토픽을 거쳐 Flink in-memory BroadcastState로 일괄 동기화
   */
  async function syncRulesToFlink(): Promise<{ syncedCount: number; message: string }> {
    syncingRules.value = true;
    errorMessage.value = null;
    try {
      const res = await sensorRegistryApi.syncRulesToFlink();
      return res;
    } catch (err: unknown) {
      errorMessage.value = err instanceof Error ? err.message : 'Flink 룰 동기화 실패';
      throw err;
    } finally {
      syncingRules.value = false;
    }
  }

  return {
    sensors,
    isLoading,
    syncingRules,
    isBackendConnected,
    errorMessage,
    searchQuery,
    typeFilter,
    deviceFilter,
    nextSerialPreview,
    totalCount,
    physicalCount,
    formulaCount,
    availableDevices,
    filteredSensors,
    isFilterActive,
    fetchSensors,
    fetchNextSerial,
    createSensor,
    duplicateFormulaSensor,
    updateSensor,
    deleteSensor,
    syncDeviceSensors,
    syncRulesToFlink,
    resetFilters,
    clearSearchQuery,
  };
});

