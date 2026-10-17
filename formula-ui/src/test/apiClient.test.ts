import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import {
  evaluateOnBackend,
  evaluateOnDemandBackend,
  fetchBackendSensors,
  fetchBackendSensorTimeSeries,
  type BackendEvaluationParams,
} from '../services/apiClient'
import type { SensorDataPoint, SensorMetadata } from '../types/sensor'

describe('apiClient service', () => {
  const originalFetch = globalThis.fetch

  beforeEach(() => {
    vi.restoreAllMocks()
  })

  afterEach(() => {
    globalThis.fetch = originalFetch
  })

  describe('evaluateOnDemandBackend & evaluateOnBackend', () => {
    const validParams: BackendEvaluationParams = {
      sensorId: '10001',
      expression: 'val + 10',
      startTime: '2025-01-01T00:00:00Z',
      endTime: '2025-01-01T01:00:00Z',
      sampleBy: '5m',
    }

    const mockPoints: SensorDataPoint[] = [
      {
        timestamp: '2025-01-01T00:00:00Z',
        timestampMs: 1735689600000,
        sensorId: '10001',
        value: 25.5,
        state: 0,
        hasInaccurateData: false,
      },
      {
        timestamp: '2025-01-01T00:05:00Z',
        timestampMs: 1735689900000,
        sensorId: '10001',
        value: 26.0,
        state: 0,
        hasInaccurateData: false,
      },
    ]

    it('successfully calls evaluate endpoint with explicit sampleBy and returns data', async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => mockPoints,
      })
      globalThis.fetch = fetchMock

      const result = await evaluateOnDemandBackend(validParams)

      expect(fetchMock).toHaveBeenCalledTimes(1)
      const [url, options] = fetchMock.mock.calls[0]
      expect(url).toContain('/api/v1/evaluate?')

      const parsedUrl = new URL(url as string, 'http://localhost')
      expect(parsedUrl.searchParams.get('sensorId')).toBe('10001')
      expect(parsedUrl.searchParams.get('expression')).toBe('val + 10')
      expect(parsedUrl.searchParams.get('startTime')).toBe('2025-01-01T00:00:00Z')
      expect(parsedUrl.searchParams.get('endTime')).toBe('2025-01-01T01:00:00Z')
      expect(parsedUrl.searchParams.get('sampleBy')).toBe('5m')
      expect(options?.signal).toBeDefined()

      expect(result).toEqual(mockPoints)
    })

    it('defaults sampleBy to "1s" when omitted or empty', async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => mockPoints,
      })
      globalThis.fetch = fetchMock

      const paramsWithoutSampleBy: BackendEvaluationParams = {
        sensorId: '10002',
        expression: 'val * 2',
        startTime: '2025-01-01T00:00:00Z',
        endTime: '2025-01-01T00:10:00Z',
      }

      const result = await evaluateOnDemandBackend(paramsWithoutSampleBy)

      const [url] = fetchMock.mock.calls[0]
      const parsedUrl = new URL(url as string, 'http://localhost')
      expect(parsedUrl.searchParams.get('sampleBy')).toBe('1s')
      expect(result).toEqual(mockPoints)
    })

    it('throws error with status and text when backend returns non-ok response', async () => {
      const errorResponseBody = 'Invalid expression syntax near "val +"'
      const fetchMock = vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        text: async () => errorResponseBody,
      })
      globalThis.fetch = fetchMock

      await expect(evaluateOnDemandBackend(validParams)).rejects.toThrow(
        `Backend evaluation error (400): ${errorResponseBody}`
      )
    })

    it('propagates network or fetch errors', async () => {
      const fetchMock = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
      globalThis.fetch = fetchMock

      await expect(evaluateOnDemandBackend(validParams)).rejects.toThrow('Failed to fetch')
    })

    it('provides evaluateOnBackend as an exact alias for evaluateOnDemandBackend', async () => {
      expect(evaluateOnBackend).toBe(evaluateOnDemandBackend)

      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => mockPoints,
      })
      globalThis.fetch = fetchMock

      const result = await evaluateOnBackend(validParams)
      expect(result).toEqual(mockPoints)
    })
  })

  describe('fetchBackendSensors', () => {
    const mockSensors: SensorMetadata[] = [
      {
        id: '10001',
        name: 'Temp Sensor 1',
        category: 'Temperature',
        unit: '°C',
        mean: 25,
        stdDev: 2,
        description: 'Test Sensor Description',
      },
    ]

    it('fetches backend sensors successfully', async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => mockSensors,
      })
      globalThis.fetch = fetchMock

      const result = await fetchBackendSensors()

      expect(fetchMock).toHaveBeenCalledTimes(1)
      const [url] = fetchMock.mock.calls[0]
      expect(url).toContain('/api/v1/sensors')
      expect(result).toEqual(mockSensors)
    })

    it('throws error when backend responds with error status', async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
      })
      globalThis.fetch = fetchMock

      await expect(fetchBackendSensors()).rejects.toThrow(
        'Failed to fetch sensors from backend (500)'
      )
    })
  })

  describe('fetchBackendSensorTimeSeries', () => {
    const mockTimeSeries: SensorDataPoint[] = [
      {
        timestamp: '2025-01-01T00:00:00Z',
        timestampMs: 1735689600000,
        value: 10.0,
        sensorId: 'sensor/123#1',
        state: 0,
        hasInaccurateData: false,
      },
    ]

    it('fetches time series data with encoded sensor ID', async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => mockTimeSeries,
      })
      globalThis.fetch = fetchMock

      const sensorId = 'sensor/123#1'
      const result = await fetchBackendSensorTimeSeries(sensorId)

      expect(fetchMock).toHaveBeenCalledTimes(1)
      const [url] = fetchMock.mock.calls[0]
      expect(url).toContain(`/api/v1/sensors/${encodeURIComponent(sensorId)}/timeseries`)
      expect(result).toEqual(mockTimeSeries)
    })

    it('throws error when time series endpoint returns non-ok status', async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
      })
      globalThis.fetch = fetchMock

      await expect(fetchBackendSensorTimeSeries('99999')).rejects.toThrow(
        'Failed to fetch time series from backend (404)'
      )
    })
  })
})
