import type { SensorDataPoint, SensorMetadata } from '../types/sensor'

export interface BackendEvaluateParams {
  sensorId: string
  expression: string
  startTime: string
  endTime: string
  sampleBy?: string
}

export async function evaluateOnBackend(params: BackendEvaluateParams): Promise<SensorDataPoint[]> {
  const query = new URLSearchParams({
    sensorId: params.sensorId,
    expression: params.expression,
    startTime: params.startTime,
    endTime: params.endTime,
    sampleBy: params.sampleBy || '1s',
  })

  const response = await fetch(`/api/v1/evaluate?${query.toString()}`)
  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`Backend evaluation error (${response.status}): ${errorText}`)
  }

  const data = await response.json()
  return data
}

/**
 * 백엔드 /api/v1/sensors 엔드포인트에서 100개 센서 카탈로그 목록을 가져옵니다.
 */
export async function fetchBackendSensors(): Promise<SensorMetadata[]> {
  const response = await fetch('/api/v1/sensors')
  if (!response.ok) {
    throw new Error(`Failed to fetch sensors from backend (${response.status})`)
  }
  return await response.json()
}

/**
 * 백엔드 /api/v1/sensors/{sensorId}/timeseries 엔드포인트에서 1시간(3,600건) 시계열 데이터를 가져옵니다.
 */
export async function fetchBackendSensorTimeSeries(sensorId: string): Promise<SensorDataPoint[]> {
  const response = await fetch(`/api/v1/sensors/${encodeURIComponent(sensorId)}/timeseries`)
  if (!response.ok) {
    throw new Error(`Failed to fetch sensor time series from backend (${response.status})`)
  }
  return await response.json()
}

export async function checkBackendHealth(): Promise<boolean> {
  try {
    const res = await fetch('/api/v1/sensors', { method: 'GET' })
    return res.status === 200
  } catch {
    return false
  }
}
