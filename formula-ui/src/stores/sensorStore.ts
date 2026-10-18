import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { SensorMetadata, SensorDataPoint } from '../types/sensor'
import { fetchBackendSensors, fetchBackendSensorTimeSeries, fetchBackendLatestPoint } from '../services/apiClient'
import { getAllSensors as getFallbackSensors, generateSensorTimeSeries as getFallbackTimeSeries } from '../services/sensorDataGenerator'
import { useSensorRegistryStore } from './sensorRegistryStore'

/**
 * Sensor Store (Unified Catalog)
 * Integrates:
 * 1) Oracle 19c SENSOR_REGISTRY persistent sensors (IoT Physical & Formula Sensors with unique serial numbers)
 * 2) Authoritative formula-api backend telemetry service
 * 3) High-performance client-side simulation engine
 */
export const useSensorStore = defineStore('sensor', () => {
  const registryStore = useSensorRegistryStore()
  const baseSensors = ref<SensorMetadata[]>([])
  const isLoading = ref<boolean>(false)
  const isBackendConnected = ref<boolean>(false)
  const errorMessage = ref<string | null>(null)
  const sensorSourceMap = ref<Record<string, 'QUESTDB' | 'MOCK'>>({})

  // In-memory cache for loaded sensor time-series datasets
  const timeSeriesCache = new Map<string, SensorDataPoint[]>()

  /**
   * Oracle 19c SENSOR_REGISTRY 항목을 SensorMetadata 형식으로 변환
   */
  function mapRegistryToMetadata(item: any): SensorMetadata {
    const id = String(item.serialNumber || item.sensorCode)
    const cat = (item.category || 'Mechanical') as SensorMetadata['category']
    const isFormula = item.sensorType === 'FORMULA'

    let mean = 60.0
    let stdDev = 3.0
    if (cat === 'Temperature') { mean = 70.0; stdDev = 3.5 }
    else if (cat === 'Pressure') { mean = 110.0; stdDev = 4.2 }
    else if (cat === 'Vibration') { mean = 15.0; stdDev = 1.8 }
    else if (cat === 'Flow') { mean = 50.0; stdDev = 2.5 }
    else if (cat === 'Electrical') { mean = 230.0; stdDev = 6.0 }
    else if (cat === 'Efficiency') { mean = 85.0; stdDev = 2.0 }

    return {
      id,
      name: `[#${item.serialNumber}] ${item.sensorName}`,
      category: cat,
      unit: item.unit || '',
      mean,
      stdDev,
      minClamp: Math.max(0, mean - stdDev * 4),
      maxClamp: mean + stdDev * 4,
      description: isFormula
        ? `Oracle Formula Sensor [Serial #${item.serialNumber}]: ${item.formulaExpression}`
        : `Oracle Physical IoT Sensor [Serial #${item.serialNumber}] Device: ${item.deviceId} (Index #${item.deviceSensorIndex})`,
    }
  }

  /**
   * sensorRegistryStore.sensors와 baseSensors를 반응형(computed)으로 자동 병합
   */
  const sensors = computed<SensorMetadata[]>(() => {
    const mergedMap = new Map<string, SensorMetadata>()
    for (const item of registryStore.sensors) {
      const meta = mapRegistryToMetadata(item)
      mergedMap.set(meta.id, meta)
    }
    for (const s of baseSensors.value) {
      if (!mergedMap.has(s.id)) {
        mergedMap.set(s.id, s)
      }
    }
    return Array.from(mergedMap.values())
  })

  const sensorMap = computed(() => {
    return new Map(sensors.value.map((s) => [s.id, s]))
  })

  const categories = computed(() => {
    return Array.from(new Set(sensors.value.map((s) => s.category)))
  })

  const isTestEnv = typeof import.meta !== 'undefined' && import.meta.env?.MODE === 'test'

  /**
   * 백엔드 또는 로컬 폴백 센서 목록을 로드하여 반응형 레지스트리 카탈로그와 통합
   */
  async function loadSensors(forceReload = false) {
    if (baseSensors.value.length > 0 && !forceReload) return

    isLoading.value = true
    errorMessage.value = null

    if (isTestEnv) {
      isBackendConnected.value = false
      baseSensors.value = getFallbackSensors()
      isLoading.value = false
      return
    }

    try {
      const data = await fetchBackendSensors()
      baseSensors.value = data
      isBackendConnected.value = true
    } catch (err: any) {
      console.warn('Backend sensor service unreachable, using registry store + fallback generator:', err)
      isBackendConnected.value = false
      errorMessage.value = 'Running in offline fallback mode with Oracle Registry Local State.'
      baseSensors.value = getFallbackSensors()
    } finally {
      isLoading.value = false
    }
  }

  function getSensorById(id: string): SensorMetadata | undefined {
    return sensorMap.value.get(id)
  }

  /**
   * 특정 센서의 1시간 시계열 데이터 조회 (온디맨드 시뮬레이션 및 트렌드 차트용)
   * forceReload가 true인 경우 캐시를 무효화하고 최신 QuestDB/백엔드 데이터를 다시 로드합니다.
   */
  async function fetchTimeSeries(
    sensorId: string,
    durationSeconds = 3600,
    forceReload = false
  ): Promise<SensorDataPoint[]> {
    if (forceReload) {
      timeSeriesCache.delete(sensorId)
    }

    if (timeSeriesCache.has(sensorId)) {
      return timeSeriesCache.get(sensorId)!
    }

    if (isTestEnv) {
      const fallback = getFallbackTimeSeries(sensorId, durationSeconds).points
      timeSeriesCache.set(sensorId, fallback)
      sensorSourceMap.value[sensorId] = 'MOCK'
      return fallback
    }

    try {
      const rawPoints = await fetchBackendSensorTimeSeries(sensorId)
      const mapped: SensorDataPoint[] = rawPoints.map((p: any) => {
        const ms = typeof p.timestamp === 'number' ? p.timestamp : new Date(p.timestamp).getTime()
        const timeStr = typeof p.timestamp === 'number'
          ? new Date(p.timestamp).toISOString().substring(11, 19)
          : String(p.timestamp)

        return {
          sensorId: p.sensorId,
          timestampMs: ms,
          timestamp: timeStr,
          value: p.value,
          state: p.state ?? 0,
          hasInaccurateData: p.hasInaccurateData ?? false,
        }
      })

      const source = (rawPoints as any).dataSource === 'QUESTDB' ? 'QUESTDB' : 'MOCK'
      sensorSourceMap.value[sensorId] = source

      timeSeriesCache.set(sensorId, mapped)
      isBackendConnected.value = true
      return mapped
    } catch (err) {
      console.warn(`Failed to fetch time series for [${sensorId}] from backend, using fallback generator:`, err)
      const fallback = getFallbackTimeSeries(sensorId, durationSeconds).points
      timeSeriesCache.set(sensorId, fallback)
      sensorSourceMap.value[sensorId] = 'MOCK'
      return fallback
    }
  }

  /**
   * 센서 데이터의 원천(QUESTDB 또는 MOCK Fallback)을 반환합니다.
   */
  function getSensorSource(sensorId: string): 'QUESTDB' | 'MOCK' {
    return sensorSourceMap.value[sensorId] || 'MOCK'
  }

  /**
   * RealTime 모드를 위해 가장 최신의 단일 데이터 포인트를 조회하거나 합성합니다.
   */
  async function fetchLatestPoint(sensorId: string, lastValue?: number): Promise<SensorDataPoint> {
    if (!isTestEnv && isBackendConnected.value) {
      try {
        const pt = await fetchBackendLatestPoint(sensorId)
        if (pt) {
          const ms = typeof pt.timestamp === 'number' ? pt.timestamp : new Date(pt.timestamp).getTime()
          const timeStr = typeof pt.timestamp === 'number'
            ? new Date(pt.timestamp).toISOString().substring(11, 19)
            : String(pt.timestamp).substring(11, 19)
          const source = (pt as any).dataSource === 'QUESTDB' ? 'QUESTDB' : 'MOCK'
          sensorSourceMap.value[sensorId] = source
          return {
            sensorId: pt.sensorId,
            timestampMs: ms,
            timestamp: timeStr,
            value: pt.value,
            state: pt.state ?? 0,
            hasInaccurateData: pt.hasInaccurateData ?? false,
          }
        }
      } catch {
        // Fallback to local continuous generator below
      }
    }

    // Local / Offline fallback point generation
    sensorSourceMap.value[sensorId] = 'MOCK'
    const meta = getSensorById(sensorId)
    const mean = meta?.mean ?? 50.0
    const stdDev = meta?.stdDev ?? 3.0
    const minClamp = meta?.minClamp ?? 0.0
    const maxClamp = meta?.maxClamp ?? 200.0

    const now = new Date()
    const timeStr = now.toISOString().substring(11, 19)
    const noise = (Math.random() - 0.5) * stdDev * 0.4
    let val = lastValue != null ? lastValue + noise : mean + noise
    if (val < minClamp) val = minClamp
    if (val > maxClamp) val = maxClamp
    val = Math.round(val * 100.0) / 100.0

    return {
      sensorId,
      timestampMs: now.getTime(),
      timestamp: timeStr,
      value: val,
      state: 0,
      hasInaccurateData: false,
    }
  }

  return {
    sensors,
    categories,
    isLoading,
    isBackendConnected,
    errorMessage,
    sensorSourceMap,
    loadSensors,
    getSensorById,
    fetchTimeSeries,
    getSensorSource,
    fetchLatestPoint,
  }
})

