export interface FormulaDefinition {
  id: string
  name: string
  description?: string
  expression: string
  category: string
  targetSensor?: string // Primary sensor ID if applicable (e.g., 'sensor_001')
  tags?: string[]
  createdAt: string
  updatedAt: string
}

export interface FormulaDependencyInfo {
  referencedSensors: string[]
  referencedFormulas: string[]
  dependentFormulas: string[] // Formulas that call this formula
  hasCycle: boolean
  cyclePath?: string[]
}

export interface SimulationConfig {
  formulaId: string
  expression?: string // Can override or test ad-hoc expression
  targetSensorId?: string
  durationSeconds: number // e.g. 3600 (1 hour), 1800 (30 min), 600 (10 min)
  sampleStep: number // 1 for 1s, 5 for 5s, etc.
  useBackend: boolean
}

export interface SimulationResultPoint {
  index: number
  timestamp: string
  timestampMs: number
  primarySensorValue?: number
  computedValue: number
  subFormulaValues: Record<string, number>
}

export interface SimulationMetrics {
  totalPoints: number
  executionTimeMs: number
  min: number
  max: number
  avg: number
  stdDev: number
  nanCount: number
}

export interface SimulationResult {
  formulaId: string
  formulaName: string
  expression: string
  resolvedDependencyOrder: string[]
  metrics: SimulationMetrics
  points: SimulationResultPoint[]
  logs: string[]
  dataSource: 'CLIENT_ENGINE' | 'BACKEND_API'
}
