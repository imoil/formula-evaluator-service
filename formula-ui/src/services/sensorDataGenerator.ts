import type { SensorMetadata, SensorDataPoint, SensorTimeSeries } from '../types/sensor'

// Mulberry32 seeded pseudo-random generator for fast deterministic sampling
function mulberry32(seed: number) {
  return function () {
    let t = (seed += 0x6d2b79f5)
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

// Box-Muller transform for normal distribution N(mean, stdDev^2)
function sampleNormal(random: () => number, mean: number, stdDev: number): number {
  let u1 = random()
  let u2 = random()
  // Guard against 0 for log
  while (u1 === 0) u1 = random()
  while (u2 === 0) u2 = random()

  const z0 = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2)
  return mean + z0 * stdDev
}

// Generate the 100 sensor catalog
const CATEGORIES: SensorMetadata['category'][] = [
  'Temperature',
  'Pressure',
  'Vibration',
  'Electrical',
  'Flow',
  'Mechanical',
  'Environmental',
]

const UNITS_MAP: Record<SensorMetadata['category'], { unit: string; minMean: number; maxMean: number; stdRatio: number }> = {
  Temperature: { unit: '°C', minMean: 45, maxMean: 110, stdRatio: 0.05 },
  Pressure: { unit: 'bar', minMean: 20, maxMean: 180, stdRatio: 0.04 },
  Vibration: { unit: 'mm/s', minMean: 2.5, maxMean: 28, stdRatio: 0.12 },
  Electrical: { unit: 'V', minMean: 210, maxMean: 480, stdRatio: 0.02 },
  Flow: { unit: 'L/min', minMean: 15, maxMean: 95, stdRatio: 0.06 },
  Mechanical: { unit: 'RPM', minMean: 900, maxMean: 3200, stdRatio: 0.03 },
  Environmental: { unit: '%', minMean: 35, maxMean: 75, stdRatio: 0.05 },
}

export const SENSOR_CATALOG: SensorMetadata[] = Array.from({ length: 100 }, (_, i) => {
  const sensorNum = i
  const id = `sensor_${String(sensorNum).padStart(3, '0')}`
  const cat = CATEGORIES[i % CATEGORIES.length]
  const config = UNITS_MAP[cat]

  // Deterministic mean and stdDev based on sensor index
  const seed = (i + 1) * 7331
  const rand = mulberry32(seed)
  const mean = Math.round((config.minMean + rand() * (config.maxMean - config.minMean)) * 10) / 10
  const stdDev = Math.max(0.1, Math.round(mean * config.stdRatio * (0.8 + rand() * 0.4) * 100) / 100)

  return {
    id,
    name: `${cat} Sensor #${String(sensorNum).padStart(3, '0')}`,
    category: cat,
    unit: config.unit,
    mean,
    stdDev,
    minClamp: Math.max(0, mean - stdDev * 4),
    maxClamp: mean + stdDev * 4,
    description: `High-frequency industrial ${cat.toLowerCase()} telemetry (N(${mean}, ${stdDev}²))`,
  }
})

// In-memory cache for generated series
const dataCache = new Map<string, SensorTimeSeries>()

// Base reference start time: fixed 1-hour interval for reproducible simulation
const BASE_START_TIME = new Date('2026-10-09T20:00:00Z').getTime()

/**
 * Generate 1-hour (3600 seconds) 1-second interval time series data for a specific sensor.
 */
export function generateSensorTimeSeries(sensorId: string, durationSeconds = 3600): SensorTimeSeries {
  const cacheKey = `${sensorId}_${durationSeconds}`
  if (dataCache.has(cacheKey)) {
    return dataCache.get(cacheKey)!
  }

  const metadata = SENSOR_CATALOG.find((s) => s.id === sensorId) || {
    id: sensorId,
    name: `Sensor ${sensorId}`,
    category: 'Mechanical',
    unit: 'units',
    mean: 100,
    stdDev: 5,
    description: 'Generic normal distribution sensor',
  }

  // Derive unique seed from sensor ID
  let seed = 42
  for (let i = 0; i < sensorId.length; i++) {
    seed = (seed * 31 + sensorId.charCodeAt(i)) >>> 0
  }
  const rand = mulberry32(seed)

  const points: SensorDataPoint[] = []
  let subtleDrift = 0

  for (let s = 0; s < durationSeconds; s++) {
    const timestampMs = BASE_START_TIME + s * 1000
    const date = new Date(timestampMs)
    const timeStr = date.toTimeString().split(' ')[0] // HH:mm:ss

    // Normal distribution with subtle smooth random-walk drift for physical realism
    subtleDrift += (rand() - 0.5) * (metadata.stdDev * 0.05)
    // Spring back toward mean to avoid unbounded divergence
    subtleDrift *= 0.995

    let rawVal = sampleNormal(rand, metadata.mean + subtleDrift, metadata.stdDev)
    if (metadata.minClamp !== undefined && rawVal < metadata.minClamp) rawVal = metadata.minClamp
    if (metadata.maxClamp !== undefined && rawVal > metadata.maxClamp) rawVal = metadata.maxClamp

    const roundedVal = Math.round(rawVal * 1000) / 1000

    points.push({
      timestamp: timeStr,
      timestampMs,
      sensorId,
      value: roundedVal,
      state: 0,
      hasInaccurateData: false,
    })
  }

  const result: SensorTimeSeries = {
    sensorId,
    metadata,
    points,
  }

  dataCache.set(cacheKey, result)
  return result
}

/**
 * Generate data for multiple sensors simultaneously (e.g. For composite formulas)
 */
export function getMultiSensorTimeSeries(sensorIds: string[], durationSeconds = 3600): Map<string, SensorTimeSeries> {
  const map = new Map<string, SensorTimeSeries>()
  for (const id of sensorIds) {
    map.set(id, generateSensorTimeSeries(id, durationSeconds))
  }
  return map
}

/**
 * Get all 100 sensor catalog entries
 */
export function getAllSensors(): SensorMetadata[] {
  return SENSOR_CATALOG
}

/**
 * Find sensor metadata by ID
 */
export function getSensorById(id: string): SensorMetadata | undefined {
  return SENSOR_CATALOG.find((s) => s.id === id)
}
