import { describe, it, expect } from 'vitest'
import {
  extractReferences,
  analyzeFormulaDependencies,
  getEvaluationOrder,
  simulateFormula,
} from '../services/formulaEngine'
import type { FormulaDefinition } from '../types/formula'
import {
  getAllSensors,
  generateSensorTimeSeries,
} from '../services/sensorDataGenerator'

describe('100 Sample Sensors & Normal Distribution Telemetry', () => {
  it('should provide exactly 100 sensors in the catalog', () => {
    const sensors = getAllSensors()
    expect(sensors.length).toBe(100)
    expect(sensors[0].id).toBe('sensor_000')
    expect(sensors[99].id).toBe('sensor_099')
  })

  it('should generate 1-hour time series (3,600 data points per second)', () => {
    const ts = generateSensorTimeSeries('sensor_000', 3600)
    expect(ts.points.length).toBe(3600)
    expect(ts.sensorId).toBe('sensor_000')

    // Interval between point 0 and point 1 should be 1 second (1000ms)
    const diff = ts.points[1].timestampMs - ts.points[0].timestampMs
    expect(diff).toBe(1000)

    // Verify statistical normal distribution properties (Sample Mean ≈ Population Mean)
    const values = ts.points.map((p) => p.value)
    const sampleMean = values.reduce((a, b) => a + b, 0) / values.length
    expect(Math.abs(sampleMean - ts.metadata.mean)).toBeLessThan(ts.metadata.stdDev)
  })
})

describe('Formula Dependency Engine & Sub-Formula Composition', () => {
  const formulas: FormulaDefinition[] = [
    {
      id: 'BASE_TEMP_F',
      name: 'Temp F',
      expression: 'sensor_000 * 1.8 + 32',
      category: 'Thermal',
      createdAt: '',
      updatedAt: '',
    },
    {
      id: 'HEAT_INDEX',
      name: 'Heat Index',
      expression: 'BASE_TEMP_F * 1.05 + 10',
      category: 'Thermal',
      createdAt: '',
      updatedAt: '',
    },
    {
      id: 'COMPOSITE_METRIC',
      name: 'Composite Metric',
      expression: 'HEAT_INDEX + window_avg(5) - sensor_001',
      category: 'Composite',
      createdAt: '',
      updatedAt: '',
    },
  ]

  it('should correctly extract sensor and sub-formula references', () => {
    const refs = extractReferences('HEAT_INDEX + window_avg(5) - sensor_001', formulas)
    expect(refs.formulaIds).toContain('HEAT_INDEX')
    expect(refs.sensorIds).toContain('sensor_001')
  })

  it('should resolve DAG topological order for composite formulas calling sub-formulas', () => {
    const { order, error } = getEvaluationOrder('COMPOSITE_METRIC', formulas)
    expect(error).toBeUndefined()
    // BASE_TEMP_F must precede HEAT_INDEX, which must precede COMPOSITE_METRIC
    expect(order.indexOf('BASE_TEMP_F')).toBeLessThan(order.indexOf('HEAT_INDEX'))
    expect(order.indexOf('HEAT_INDEX')).toBeLessThan(order.indexOf('COMPOSITE_METRIC'))
  })

  it('should detect circular references between formulas', () => {
    const cyclicFormulas: FormulaDefinition[] = [
      {
        id: 'A',
        name: 'A',
        expression: 'B + 1',
        category: 'Test',
        createdAt: '',
        updatedAt: '',
      },
      {
        id: 'B',
        name: 'B',
        expression: 'A * 2',
        category: 'Test',
        createdAt: '',
        updatedAt: '',
      },
    ]

    const analysis = analyzeFormulaDependencies('A', cyclicFormulas)
    expect(analysis.hasCycle).toBe(true)

    const orderRes = getEvaluationOrder('A', cyclicFormulas)
    expect(orderRes.error).toContain('Circular reference detected')
  })

  it('should simulate formula execution across 3,600 points with sub-formula substitution', () => {
    const target = formulas[2] // COMPOSITE_METRIC
    const result = simulateFormula(target, formulas, {
      durationSeconds: 3600,
      sampleStep: 1,
      primarySensorId: 'sensor_000',
    })

    expect(result.points.length).toBe(3600)
    expect(result.resolvedOrder).toEqual(['BASE_TEMP_F', 'HEAT_INDEX', 'COMPOSITE_METRIC'])

    // First point should have evaluated sub-formula values recorded
    const pt0 = result.points[0]
    expect(pt0.subFormulaValues['BASE_TEMP_F']).toBeDefined()
    expect(pt0.subFormulaValues['HEAT_INDEX']).toBeDefined()
    expect(pt0.computedValue).toBeDefined()
    expect(Number.isFinite(pt0.computedValue)).toBe(true)
    expect(result.metrics.executionTimeMs).toBeGreaterThan(0)
  })
})
