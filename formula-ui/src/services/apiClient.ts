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

export interface FetchTimeSeriesOptions {
  sampleBy?: string
  startTime?: string
  endTime?: string
  limit?: number
  latest?: boolean
}

export interface QuestDbStatusResponse {
  status: 'ONLINE' | 'OFFLINE'
  connected: boolean
  webConsoleUrl: string
}

/**
 * 백엔드 /api/v1/sensors/{sensorId}/timeseries 엔드포인트에서 시계열 데이터를 가져옵니다.
 * X-Data-Source 응답 헤더(QUESTDB 또는 MOCK)를 감지하여 반환 배열 객체에 dataSource 속성을 주입합니다.
 */
export async function fetchBackendSensorTimeSeries(
  sensorId: string,
  options?: FetchTimeSeriesOptions
): Promise<SensorDataPoint[]> {
  const query = new URLSearchParams()
  if (options?.sampleBy) query.append('sampleBy', options.sampleBy)
  if (options?.startTime) query.append('startTime', options.startTime)
  if (options?.endTime) query.append('endTime', options.endTime)
  if (options?.latest !== undefined) query.append('latest', String(options.latest))
  if (options?.limit !== undefined) query.append('limit', String(options.limit))

  const queryString = query.toString() ? `?${query.toString()}` : ''
  const response = await fetch(
    `${API_BASE}/api/v1/sensors/${encodeURIComponent(sensorId)}/timeseries${queryString}`,
    {
      signal: AbortSignal.timeout(TIMEOUT_MS),
    }
  )
  if (!response.ok) {
    throw new Error(`Failed to fetch time series from backend (${response.status})`)
  }
  const data = await response.json()
  const dataSource =
    typeof response.headers?.get === 'function'
      ? response.headers.get('X-Data-Source') || 'QUESTDB'
      : 'QUESTDB'

  if (Array.isArray(data)) {
    ;(data as any).dataSource = dataSource
  }
  return data
}

/**
 * 백엔드 /api/v1/sensors/questdb/status 엔드포인트에서 QuestDB 연결 상태를 확인합니다.
 */
export async function fetchQuestDbStatus(): Promise<QuestDbStatusResponse> {
  try {
    const response = await fetch(`${API_BASE}/api/v1/sensors/questdb/status`, {
      signal: AbortSignal.timeout(TIMEOUT_MS),
    })
    if (response.ok) {
      return await response.json()
    }
  } catch {
    // Offline / fallback
  }
  return {
    status: 'OFFLINE',
    connected: false,
    webConsoleUrl: 'http://localhost:9000',
  }
}

/**
 * 실시간 변동 스트리밍을 위해 가장 최신의 단일 데이터 포인트를 조회합니다.
 */
export async function fetchBackendLatestPoint(sensorId: string): Promise<SensorDataPoint | null> {
  const points = await fetchBackendSensorTimeSeries(sensorId, { latest: true, limit: 1 })
  if (points && points.length > 0) {
    const pt = points[0]
    ;(pt as any).dataSource = (points as any).dataSource || 'QUESTDB'
    return pt
  }
  return null
}

