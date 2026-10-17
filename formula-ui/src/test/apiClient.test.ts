import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  checkBackendHealth,
  fetchBackendSensors,
  fetchBackendSensorTimeSeries,
  evaluateOnDemandBackend,
} from '../services/apiClient'

describe('apiClient', () => {
  const originalFetch = globalThis.fetch

  beforeEach(() => {
    vi.restoreAllMocks()
  })

  afterEach(() => {
    globalThis.fetch = originalFetch
  })

  describe('checkBackendHealth', () => {
    it('returns true when backend health check returns HTTP 200 OK', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
      } as Response)

      const isHealthy = await checkBackendHealth()
      expect(isHealthy).toBe(true)
      expect(globalThis.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/health'),
        expect.objectContaining({ signal: expect.any(AbortSignal) })
      )
    })

    it('returns false when backend health check returns non-200 status', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
      } as Response)

      const isHealthy = await checkBackendHealth()
      expect(isHealthy).toBe(false)
    })

    it('returns false when fetch throws an error (network failure / exception path)', async () => {
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))

      const isHealthy = await checkBackendHealth()
      expect(isHealthy).toBe(false)
    })
  })

  describe('fetchBackendSensors', () => {
    it('fetches sensor metadata list successfully', async () => {
      const mockSensors = [
        { id: '10001', name: 'Sensor 1', category: 'Temperature', unit: 'C', mean: 70, stdDev: 3, minClamp: 50, maxClamp: 90, description: 'Test' }
      ]
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => mockSensors,
      } as Response)

      const sensors = await fetchBackendSensors()
      expect(sensors).toEqual(mockSensors)
    })

    it('throws error when backend returns non-ok response', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
      } as Response)

      await expect(fetchBackendSensors()).rejects.toThrow('Failed to fetch sensors from backend (500)')
    })
  })

  describe('fetchBackendSensorTimeSeries', () => {
    it('fetches sensor time series points successfully', async () => {
      const mockPoints = [
        { sensorId: '10001', timestamp: '2026-10-17T00:00:00Z', value: 72.5, state: 0, hasInaccurateData: false }
      ]
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => mockPoints,
      } as Response)

      const points = await fetchBackendSensorTimeSeries('10001')
      expect(points).toEqual(mockPoints)
      expect(globalThis.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/sensors/10001/timeseries'),
        expect.any(Object)
      )
    })

    it('throws error when backend time series fetch fails', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
      } as Response)

      await expect(fetchBackendSensorTimeSeries('invalid-id')).rejects.toThrow('Failed to fetch sensor time series from backend (404)')
    })
  })

  describe('evaluateOnDemandBackend', () => {
    it('evaluates expression on backend successfully', async () => {
      const mockResult = [
        { sensorId: '10001', timestampMs: 1700000000000, value: 100, state: 0 }
      ]
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => mockResult,
      } as Response)

      const params = {
        sensorId: '10001',
        expression: 'TEMP_01 * 1.5',
        startTime: '2026-10-17T00:00:00Z',
        endTime: '2026-10-17T01:00:00Z',
      }
      const result = await evaluateOnDemandBackend(params)
      expect(result).toEqual(mockResult)
    })

    it('throws error with details when backend evaluation fails', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        text: async () => 'Invalid formula syntax',
      } as Response)

      const params = {
        sensorId: '10001',
        expression: 'INVALID *** FORMULA',
        startTime: '2026-10-17T00:00:00Z',
        endTime: '2026-10-17T01:00:00Z',
      }
      await expect(evaluateOnDemandBackend(params)).rejects.toThrow('Backend evaluation error (400): Invalid formula syntax')
    })
  })
})
