import { describe, it, expect } from 'vitest'
import { simulateFormula } from '../services/formulaEngine'
import type { FormulaDefinition } from '../types/formula'

describe('Formula Engine Performance Benchmark', () => {
  it('measures simulation performance over large time series dataset', { timeout: 30000 }, () => {
    // Construct a multi-sensor, multi-formula graph
    const formulas: FormulaDefinition[] = [
      {
        id: 'F1',
        name: 'Formula 1',
        expression: 'sensor_000 * 1.5 + 10',
        category: 'Thermal',
        createdAt: '',
        updatedAt: '',
      },
      {
        id: 'F2',
        name: 'Formula 2',
        expression: 'F1 * 0.9 + sensor_001',
        category: 'Thermal',
        createdAt: '',
        updatedAt: '',
      },
      {
        id: 'F3',
        name: 'Formula 3',
        expression: 'F2 + window_avg(10) - sensor_002',
        category: 'Composite',
        createdAt: '',
        updatedAt: '',
      },
      {
        id: 'F4',
        name: 'Formula 4',
        expression: 'F3 * 1.1 + sensor_003',
        category: 'Composite',
        createdAt: '',
        updatedAt: '',
      },
      {
        id: 'F5',
        name: 'Formula 5',
        expression: 'F4 - window_max(5) + sensor_004',
        category: 'Composite',
        createdAt: '',
        updatedAt: '',
      },
    ]

    const targetFormula = formulas[4] // F5

    // Warmup
    simulateFormula(targetFormula, formulas, {
      durationSeconds: 1000,
      sampleStep: 1,
      primarySensorId: 'sensor_000',
    })

    // Benchmark run: 86,400 seconds (1 day)
    const iterations = 5
    const durationSeconds = 86400
    const times: number[] = []

    for (let i = 0; i < iterations; i++) {
      const start = performance.now()
      const result = simulateFormula(targetFormula, formulas, {
        durationSeconds,
        sampleStep: 1,
        primarySensorId: 'sensor_000',
      })
      const elapsed = performance.now() - start
      times.push(elapsed)
      expect(result.points.length).toBe(durationSeconds)
    }

    const avgTime = times.reduce((a, b) => a + b, 0) / times.length
    console.log(`[BENCHMARK BASELINE] Average execution time for ${durationSeconds} points across ${iterations} runs: ${avgTime.toFixed(2)} ms`)
  })
})
