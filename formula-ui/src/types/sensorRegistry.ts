/**
 * Oracle 19c 기반 통합 센서 일련번호 관리 타입 정의
 */

export type SensorType = 'PHYSICAL' | 'FORMULA';
export type SensorStatus = 'ACTIVE' | 'INACTIVE' | 'MAINTENANCE';

export interface SensorRegistryItem {
  serialNumber: number;
  sensorCode: string;
  sensorName: string;
  sensorType: SensorType;
  category: string;
  unit: string;
  deviceId?: string | null;
  deviceSensorIndex?: number | null;
  formulaExpression?: string | null;
  description?: string | null;
  status: SensorStatus;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateSensorPayload {
  sensorCode?: string;
  sensorName: string;
  sensorType: SensorType;
  category: string;
  unit: string;
  deviceId?: string;
  deviceSensorIndex?: number;
  formulaExpression?: string;
  description?: string;
  status?: SensorStatus;
}

export interface UpdateSensorPayload {
  sensorName?: string;
  category?: string;
  unit?: string;
  formulaExpression?: string;
  description?: string;
  status?: SensorStatus;
}

export interface DeviceSyncPayload {
  deviceId: string;
  sensorCount: number;
  skipExisting?: boolean;
}
