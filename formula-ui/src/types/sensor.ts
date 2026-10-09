export interface SensorMetadata {
  id: string
  name: string
  category: 'Temperature' | 'Pressure' | 'Vibration' | 'Electrical' | 'Flow' | 'Mechanical' | 'Environmental'
  unit: string
  mean: number
  stdDev: number
  minClamp?: number
  maxClamp?: number
  description: string
}

export interface SensorDataPoint {
  timestamp: string // ISO 8601 string or HH:mm:ss
  timestampMs: number
  sensorId: string
  value: number
  state: number
  hasInaccurateData: boolean
}

export interface SensorTimeSeries {
  sensorId: string
  metadata: SensorMetadata
  points: SensorDataPoint[]
}
