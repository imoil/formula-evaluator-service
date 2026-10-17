import type {
  SensorRegistryItem,
  CreateSensorPayload,
  UpdateSensorPayload,
  DeviceSyncPayload,
} from '../types/sensorRegistry';

const API_BASE_URL = 'http://localhost:8080/api/v1/registry';
const STORAGE_KEY = 'formula_oracle_sensor_registry';
const SERIAL_SEQ_KEY = 'formula_oracle_serial_seq';
const TIMEOUT_MS = 800; // 빠른 오프라인 Fallback 전환을 위한 제한시간

// Environment-safe storage helper (supports Node/Vitest test environment & browser)
const memoryStorage: Record<string, string> = {};
const safeStorage = {
  getItem(key: string): string | null {
    if (typeof window !== 'undefined' && window.localStorage) {
      return window.localStorage.getItem(key);
    }
    return memoryStorage[key] || null;
  },
  setItem(key: string, value: string): void {
    if (typeof window !== 'undefined' && window.localStorage) {
      window.localStorage.setItem(key, value);
    } else {
      memoryStorage[key] = value;
    }
  },
  removeItem(key: string): void {
    if (typeof window !== 'undefined' && window.localStorage) {
      window.localStorage.removeItem(key);
    } else {
      delete memoryStorage[key];
    }
  },
  clear(): void {
    if (typeof window !== 'undefined' && window.localStorage) {
      window.localStorage.clear();
    } else {
      Object.keys(memoryStorage).forEach((k) => delete memoryStorage[k]);
    }
  },
};

function getInitialSeedData(): SensorRegistryItem[] {
  return [
    {
      serialNumber: 10001,
      sensorCode: 'PHY-DEVICE001-S000',
      sensorName: '[DEVICE-001] Main Bearing Temperature',
      sensorType: 'PHYSICAL',
      category: 'Temperature',
      unit: '°C',
      deviceId: 'DEVICE-001',
      deviceSensorIndex: 0,
      description: 'IoT Emulator Device-001 Primary Bearing Thermocouple',
      status: 'ACTIVE',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
    {
      serialNumber: 10002,
      sensorCode: 'PHY-DEVICE001-S001',
      sensorName: '[DEVICE-001] Discharge Pressure',
      sensorType: 'PHYSICAL',
      category: 'Pressure',
      unit: 'bar',
      deviceId: 'DEVICE-001',
      deviceSensorIndex: 1,
      description: 'IoT Emulator Device-001 Outlet Gas Pressure',
      status: 'ACTIVE',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
    {
      serialNumber: 10003,
      sensorCode: 'PHY-DEVICE002-S000',
      sensorName: '[DEVICE-002] Auxiliary Bearing Temperature',
      sensorType: 'PHYSICAL',
      category: 'Temperature',
      unit: '°C',
      deviceId: 'DEVICE-002',
      deviceSensorIndex: 0,
      description: 'IoT Emulator Device-002 Secondary Bearing (Avoids index collision with Device-001)',
      status: 'ACTIVE',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
    {
      serialNumber: 10004,
      sensorCode: 'PHY-DEVICE002-S001',
      sensorName: '[DEVICE-002] Intake Pressure',
      sensorType: 'PHYSICAL',
      category: 'Pressure',
      unit: 'bar',
      deviceId: 'DEVICE-002',
      deviceSensorIndex: 1,
      description: 'IoT Emulator Device-002 Suction Line Pressure',
      status: 'ACTIVE',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
    {
      serialNumber: 10005,
      sensorCode: 'FML-PUMP-EFF-01',
      sensorName: 'Pump Differential Compression Ratio',
      sensorType: 'FORMULA',
      category: 'Efficiency',
      unit: 'ratio',
      formulaExpression: 'SN_10002 / (SN_10004 + 0.001)',
      description: 'Calculated formula sensor comparing discharge vs intake pressure using unified serial numbers',
      status: 'ACTIVE',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
    {
      serialNumber: 10006,
      sensorCode: 'FML-THERMAL-DELTA',
      sensorName: 'Dual-Device Thermal Delta',
      sensorType: 'FORMULA',
      category: 'Temperature',
      unit: '°C',
      formulaExpression: 'abs(SN_10001 - SN_10003)',
      description: 'Calculated delta between Device-001 and Device-002 bearing temperatures',
      status: 'ACTIVE',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
  ];
}

class LocalStorageRegistryManager {
  reset() {
    safeStorage.clear();
  }

  private getSensors(): SensorRegistryItem[] {
    const raw = safeStorage.getItem(STORAGE_KEY);
    if (!raw) {
      const initial = getInitialSeedData();
      this.saveSensors(initial);
      safeStorage.setItem(SERIAL_SEQ_KEY, '10007');
      return initial;
    }
    try {
      return JSON.parse(raw);
    } catch {
      return getInitialSeedData();
    }
  }

  private saveSensors(sensors: SensorRegistryItem[]) {
    safeStorage.setItem(STORAGE_KEY, JSON.stringify(sensors));
  }

  private nextSerial(): number {
    const current = parseInt(safeStorage.getItem(SERIAL_SEQ_KEY) || '10007', 10);
    safeStorage.setItem(SERIAL_SEQ_KEY, (current + 1).toString());
    return current;
  }

  getAll(type?: string, deviceId?: string, keyword?: string): SensorRegistryItem[] {
    let list = this.getSensors();
    if (type && type !== 'ALL') {
      list = list.filter((s) => s.sensorType === type);
    }
    if (deviceId && deviceId !== 'ALL') {
      list = list.filter((s) => s.deviceId === deviceId);
    }
    if (keyword && keyword.trim() !== '') {
      const q = keyword.toLowerCase().trim();
      list = list.filter(
        (s) =>
          s.sensorName.toLowerCase().includes(q) ||
          s.sensorCode.toLowerCase().includes(q) ||
          s.category.toLowerCase().includes(q) ||
          s.serialNumber.toString().includes(q) ||
          (s.deviceId && s.deviceId.toLowerCase().includes(q))
      );
    }
    return list;
  }

  getBySerial(serialNumber: number): SensorRegistryItem | undefined {
    return this.getSensors().find((s) => s.serialNumber === serialNumber);
  }

  create(payload: CreateSensorPayload): SensorRegistryItem {
    const sensors = this.getSensors();

    if (payload.sensorType === 'PHYSICAL') {
      const exists = sensors.some(
        (s) =>
          s.sensorType === 'PHYSICAL' &&
          s.deviceId === payload.deviceId &&
          s.deviceSensorIndex === payload.deviceSensorIndex
      );
      if (exists) {
        throw new Error(
          `해당 IoT 장치(${payload.deviceId})의 센서 인덱스(#${payload.deviceSensorIndex})는 이미 일련번호가 부여되어 있습니다.`
        );
      }
    }

    const serialNumber = this.nextSerial();
    const sensorCode =
      payload.sensorCode ||
      (payload.sensorType === 'PHYSICAL'
        ? `PHY-${(payload.deviceId || 'DEV').replace(/[^A-Za-z0-9]/g, '').toUpperCase()}-S${String(payload.deviceSensorIndex ?? 0).padStart(3, '0')}`
        : `FML-${serialNumber}`);

    const item: SensorRegistryItem = {
      serialNumber,
      sensorCode,
      sensorName: payload.sensorName,
      sensorType: payload.sensorType,
      category: payload.category || 'General',
      unit: payload.unit || '',
      deviceId: payload.sensorType === 'PHYSICAL' ? payload.deviceId : null,
      deviceSensorIndex: payload.sensorType === 'PHYSICAL' ? payload.deviceSensorIndex : null,
      formulaExpression: payload.sensorType === 'FORMULA' ? payload.formulaExpression : null,
      description: payload.description || '',
      status: payload.status || 'ACTIVE',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    sensors.push(item);
    this.saveSensors(sensors);
    return item;
  }

  update(serialNumber: number, payload: UpdateSensorPayload): SensorRegistryItem {
    const sensors = this.getSensors();
    const idx = sensors.findIndex((s) => s.serialNumber === serialNumber);
    if (idx === -1) {
      throw new Error(`센서 일련번호를 찾을 수 없습니다: ${serialNumber}`);
    }

    const current = sensors[idx];
    const updated: SensorRegistryItem = {
      ...current,
      sensorName: payload.sensorName ?? current.sensorName,
      category: payload.category ?? current.category,
      unit: payload.unit ?? current.unit,
      formulaExpression:
        current.sensorType === 'FORMULA' ? (payload.formulaExpression ?? current.formulaExpression) : null,
      description: payload.description ?? current.description,
      status: payload.status ?? current.status,
      updatedAt: new Date().toISOString(),
    };

    sensors[idx] = updated;
    this.saveSensors(sensors);
    return updated;
  }

  delete(serialNumber: number): void {
    const sensors = this.getSensors().filter((s) => s.serialNumber !== serialNumber);
    this.saveSensors(sensors);
  }

  syncDevice(payload: DeviceSyncPayload): SensorRegistryItem[] {
    const sensors = this.getSensors();
    const toAdd: SensorRegistryItem[] = [];
    const count = Math.min(payload.sensorCount || 10, 100);
    const categories = ['Temperature', 'Pressure', 'Vibration', 'Flow', 'Electrical', 'Mechanical', 'Environmental'];
    const units = ['°C', 'bar', 'mm/s', 'm³/h', 'kW', 'RPM', '%'];

    for (let i = 0; i < count; i++) {
      const exists = sensors.some(
        (s) =>
          s.sensorType === 'PHYSICAL' &&
          s.deviceId === payload.deviceId &&
          s.deviceSensorIndex === i
      );
      if (exists && payload.skipExisting !== false) {
        continue;
      }

      const serialNumber = this.nextSerial();
      const catIdx = i % categories.length;
      const category = categories[catIdx];
      const unit = units[catIdx];
      const sensorCode = `PHY-${payload.deviceId.replace(/[^A-Za-z0-9]/g, '').toUpperCase()}-S${String(i).padStart(3, '0')}`;

      const item: SensorRegistryItem = {
        serialNumber,
        sensorCode,
        sensorName: `[${payload.deviceId}] ${category} Sensor #${i}`,
        sensorType: 'PHYSICAL',
        category,
        unit,
        deviceId: payload.deviceId,
        deviceSensorIndex: i,
        description: `Auto-synced from IoT Emulator device '${payload.deviceId}' with collision-free serial number.`,
        status: 'ACTIVE',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      toAdd.push(item);
      sensors.push(item);
    }

    this.saveSensors(sensors);
    return toAdd;
  }

  getDevices(): string[] {
    const sensors = this.getSensors();
    const set = new Set<string>();
    sensors.forEach((s) => {
      if (s.deviceId) set.add(s.deviceId);
    });
    return Array.from(set).sort();
  }

  getNextSerialPreview(): number {
    const raw = safeStorage.getItem(SERIAL_SEQ_KEY);
    if (!raw) {
      this.getSensors(); // initializes
      return parseInt(safeStorage.getItem(SERIAL_SEQ_KEY) || '10007', 10);
    }
    return parseInt(raw, 10);
  }
}

export const localManager = new LocalStorageRegistryManager();

function isNetworkError(err: unknown): boolean {
  if (!err) return false;
  if (err instanceof Error && (err.name === 'TimeoutError' || err.name === 'AbortError')) return true;
  if (err instanceof TypeError && err.message.includes('fetch')) return true;
  const anyErr = err as { code?: string; errno?: number; message?: string; name?: string };
  if (anyErr.name === 'TimeoutError' || anyErr.name === 'AbortError') return true;
  if (anyErr.code === 'ECONNREFUSED' || anyErr.message?.includes('ECONNREFUSED')) return true;
  if (anyErr.message?.includes('Failed to fetch') || anyErr.message?.includes('fetch failed')) return true;
  return false;
}

export const sensorRegistryApi = {
  async fetchSensors(params?: {
    type?: string;
    deviceId?: string;
    keyword?: string;
  }): Promise<{ data: SensorRegistryItem[]; isBackend: boolean }> {
    try {
      const query = new URLSearchParams();
      if (params?.type && params.type !== 'ALL') query.append('type', params.type);
      if (params?.deviceId && params.deviceId !== 'ALL') query.append('deviceId', params.deviceId);
      if (params?.keyword) query.append('keyword', params.keyword);

      const res = await fetch(`${API_BASE_URL}/sensors?${query.toString()}`, {
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
      if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
      const data = await res.json();
      return { data, isBackend: true };
    } catch {
      return {
        data: localManager.getAll(params?.type, params?.deviceId, params?.keyword),
        isBackend: false,
      };
    }
  },

  async getSensorBySerial(serialNumber: number): Promise<SensorRegistryItem> {
    try {
      const res = await fetch(`${API_BASE_URL}/sensors/${serialNumber}`, {
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
      if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
      return await res.json();
    } catch {
      const item = localManager.getBySerial(serialNumber);
      if (!item) throw new Error(`센서 일련번호를 찾을 수 없습니다: ${serialNumber}`);
      return item;
    }
  },

  async createSensor(payload: CreateSensorPayload): Promise<SensorRegistryItem> {
    try {
      const res = await fetch(`${API_BASE_URL}/sensors`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
      if (!res.ok) {
        const errJson = await res.json().catch(() => ({}));
        throw new Error(errJson.message || `HTTP error ${res.status}`);
      }
      return await res.json();
    } catch (err: unknown) {
      if (isNetworkError(err)) {
        return localManager.create(payload);
      }
      throw err;
    }
  },

  async updateSensor(serialNumber: number, payload: UpdateSensorPayload): Promise<SensorRegistryItem> {
    try {
      const res = await fetch(`${API_BASE_URL}/sensors/${serialNumber}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
      if (!res.ok) {
        const errJson = await res.json().catch(() => ({}));
        throw new Error(errJson.message || `HTTP error ${res.status}`);
      }
      return await res.json();
    } catch (err: unknown) {
      if (isNetworkError(err)) {
        return localManager.update(serialNumber, payload);
      }
      throw err;
    }
  },

  async deleteSensor(serialNumber: number): Promise<void> {
    try {
      const res = await fetch(`${API_BASE_URL}/sensors/${serialNumber}`, {
        method: 'DELETE',
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
      if (!res.ok) {
        const errJson = await res.json().catch(() => ({}));
        throw new Error(errJson.message || `HTTP error ${res.status}`);
      }
    } catch (err: unknown) {
      if (isNetworkError(err)) {
        localManager.delete(serialNumber);
        return;
      }
      throw err;
    }
  },

  async syncDeviceSensors(payload: DeviceSyncPayload): Promise<SensorRegistryItem[]> {
    try {
      const res = await fetch(`${API_BASE_URL}/devices/sync`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
      if (!res.ok) {
        const errJson = await res.json().catch(() => ({}));
        throw new Error(errJson.message || `HTTP error ${res.status}`);
      }
      return await res.json();
    } catch (err: unknown) {
      if (isNetworkError(err)) {
        return localManager.syncDevice(payload);
      }
      throw err;
    }
  },

  async fetchDevices(): Promise<string[]> {
    try {
      const res = await fetch(`${API_BASE_URL}/devices`, {
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
      if (!res.ok) throw new Error(`HTTP error ${res.status}`);
      return await res.json();
    } catch {
      return localManager.getDevices();
    }
  },

  async getNextSerialPreview(): Promise<number> {
    try {
      const res = await fetch(`${API_BASE_URL}/next-serial`, {
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
      if (!res.ok) throw new Error(`HTTP error ${res.status}`);
      const data = await res.json();
      return data.nextSerialNumber;
    } catch {
      return localManager.getNextSerialPreview();
    }
  },
};
