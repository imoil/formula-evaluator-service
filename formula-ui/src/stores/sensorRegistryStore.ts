import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type {
  SensorRegistryItem,
  SensorType,
  CreateSensorPayload,
  UpdateSensorPayload,
  DeviceSyncPayload,
} from '../types/sensorRegistry';
import { sensorRegistryApi } from '../services/sensorRegistryApi';

export const useSensorRegistryStore = defineStore('sensorRegistry', () => {
  const sensors = ref<SensorRegistryItem[]>([]);
  const isLoading = ref<boolean>(false);
  const syncingRules = ref<boolean>(false);
  const isBackendConnected = ref<boolean>(false);
  const errorMessage = ref<string | null>(null);

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

  const filteredSensors = computed(() => {
    return sensors.value.filter((s) => {
      if (typeFilter.value !== 'ALL' && s.sensorType !== typeFilter.value) {
        return false;
      }
      if (deviceFilter.value !== 'ALL' && s.deviceId !== deviceFilter.value) {
        return false;
      }
      if (searchQuery.value.trim() !== '') {
        const q = searchQuery.value.toLowerCase().trim();
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

  // Actions
  async function fetchSensors() {
    isLoading.value = true;
    errorMessage.value = null;
    try {
      const res = await sensorRegistryApi.fetchSensors({
        type: typeFilter.value !== 'ALL' ? typeFilter.value : undefined,
        deviceId: deviceFilter.value !== 'ALL' ? deviceFilter.value : undefined,
        keyword: searchQuery.value || undefined,
      });
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
    fetchSensors,
    fetchNextSerial,
    createSensor,
    updateSensor,
    deleteSensor,
    syncDeviceSensors,
    syncRulesToFlink,
  };
});
