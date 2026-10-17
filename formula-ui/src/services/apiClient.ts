/**
 * formula-api Spring Boot Backend Client
 */
import type { SensorMetadata, SensorDataPoint } from '../types/sensor'

const API_BASE = typeof window !== 'undefined' && window.location?.origin
  ? ''
  : 'http://localhost:8080'

const TIMEOUT_MS = 800 // 빠른 오프라인 Fallback 전환을 위한 제한시간

export interface BackendEvaluationParams {
  sensorId: string
  expression: string
  startTime: string
  endTime: string
  sampleBy?: string
}

/**
 * 백엔드 /api/v1/evaluate 온디맨드 평가 엔드포인트를 호출합니다.
 */
export async function evaluateOnDemandBackend(params: BackendEvaluationParams): Promise<SensorDataPoint[]> {
  const query = new URLSearchParams({
    sensorId: params.sensorId,
    expression: params.expression,
    startTime: params.startTime,
    endTime: params.endTime,
    sampleBy: params.sampleBy || '1s',
  })

  const response = await fetch(`${API_BASE}/api/v1/evaluate?${query.toString()}`, {
    signal: AbortSignal.timeout(TIMEOUT_MS),
  })
  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`Backend evaluation error (${response.status}): ${errorText}`)
  }

  const data = await response.json()
  return data
}

// Alias for backwards compatibility
export const evaluateOnBackend = evaluateOnDemandBackend

/**
 * 백엔드 /api/v1/sensors 엔드포인트에서 100개 센서 및 Oracle 19c SENSOR_REGISTRY 통합 목록을 가져옵니다.
 */
export async function fetchBackendSensors(): Promise<SensorMetadata[]> {
  const response = await fetch(`${API_BASE}/api/v1/sensors`, {
    signal: AbortSignal.timeout(TIMEOUT_MS),
  })
  if (!response.ok) {
    throw new Error(`Failed to fetch sensors from backend (${response.status})`)
  }
  return await response.json()
}

/**
 * 백엔드 /api/v1/sensors/{sensorId}/timeseries 엔드포인트에서 1시간(3,600건) 시계열 데이터를 가져옵니다.
 */
export async function fetchBackendSensorTimeSeries(sensorId: string): Promise<SensorDataPoint[]> {
  const response = await fetch(`${API_BASE}/api/v1/sensors/${encodeURIComponent(sensorId)}/timeseries`, {
    signal: AbortSignal.timeout(TIMEOUT_MS),
  })
  if (!response.ok) {
    throw new Error(`Failed to fetch time series from backend (${response.status})`)
  }
  return await response.json()
}
