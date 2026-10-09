import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { SensorMetadata, SensorDataPoint } from '../types/sensor'
import { fetchBackendSensors, fetchBackendSensorTimeSeries } from '../services/apiClient'
import { getAllSensors as getFallbackSensors, generateSensorTimeSeries as getFallbackTimeSeries } from '../services/sensorDataGenerator'

/**
 * Sensor Store
 * Primary Source of Truth: formula-api Spring Boot Backend (Mock DataSource)
 * Fallback: Client-side offline generator (when backend is unreachable)
 */
export const useSensorStore = defineStore('sensor', () => {
  const sensors = ref<SensorMetadata[]>([])
  const isLoading = ref<boolean>(false)
  const isBackendConnected = ref<boolean>(false)
  const errorMessage = ref<string | null>(null)

  // In-memory cache for loaded sensor time-series datasets
  const timeSeriesCache = new Map<string, SensorDataPoint[]>()

  const sensorMap = computed(() => {
    return new Map(sensors.value.map((s) => [s.id, s]))
  })

  const categories = computed(() => {
    return Array.from(new Set(sensors.value.map((s) => s.category)))
  })

  /**
   * Initialize and load 100 sensors metadata from backend
   */
  async function loadSensors(forceReload = false) {
    if (sensors.value.length > 0 && !forceReload) return

    isLoading.value = true
    errorMessage.value = null

    try {
      const data = await fetchBackendSensors()
      sensors.value = data
      isBackendConnected.value = true
    } catch (err: any) {
      console.warn('Backend sensor service unreachable, switching to offline fallback catalog:', err)
      sensors.value = getFallbackSensors()
      isBackendConnected.value = false
      errorMessage.value = 'Running in offline fallback mode (backend unreachable).'
    } finally {
      isLoading.value = false
    }
  }

  function getSensorById(id: string): SensorMetadata | undefined {
    return sensorMap.value.get(id)
  }

  /**
   * Fetch 1-hour time-series points for a specific sensor from backend
   */
  async function fetchTimeSeries(sensorId: string, durationSeconds = 3600): Promise<SensorDataPoint[]> {
    if (timeSeriesCache.has(sensorId)) {
      return timeSeriesCache.get(sensorId)!
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

      timeSeriesCache.set(sensorId, mapped)
      isBackendConnected.value = true
      return mapped
    } catch (err) {
      console.warn(`Failed to fetch time series for [${sensorId}] from backend, using fallback generator:`, err)
      const fallback = getFallbackTimeSeries(sensorId, durationSeconds).points
      timeSeriesCache.set(sensorId, fallback)
      return fallback
    }
  }

  return {
    sensors,
    categories,
    isLoading,
    isBackendConnected,
    errorMessage,
    loadSensors,
    getSensorById,
    fetchTimeSeries,
  }
})
