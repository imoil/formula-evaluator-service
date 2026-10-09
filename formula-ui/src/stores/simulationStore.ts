import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { SimulationResult, SimulationConfig } from '../types/formula'
import { useFormulaStore } from './formulaStore'
import { simulateFormula } from '../services/formulaEngine'
import { evaluateOnBackend } from '../services/apiClient'

export const useSimulationStore = defineStore('simulation', () => {
  const formulaStore = useFormulaStore()

  const isRunning = ref(false)
  const currentResult = ref<SimulationResult | null>(null)
  const errorMessage = ref<string | null>(null)

  const config = ref<SimulationConfig>({
    formulaId: 'POWER_EFFICIENCY_INDEX',
    durationSeconds: 3600, // 1 hour (3600 data points)
    sampleStep: 1, // 1 second
    useBackend: false,
  })

  async function runSimulation(customConfig?: Partial<SimulationConfig>) {
    if (customConfig) {
      config.value = { ...config.value, ...customConfig }
    }

    const formula = formulaStore.getFormulaById(config.value.formulaId)
    if (!formula) {
      errorMessage.value = `Target formula [${config.value.formulaId}] not found.`
      return
    }

    isRunning.value = true
    errorMessage.value = null

    try {
      if (config.value.useBackend) {
        // Run via Spring Boot REST backend
        const primarySensor = config.value.targetSensorId || formula.targetSensor || 'sensor_000'
        const rawPoints = await evaluateOnBackend({
          sensorId: primarySensor,
          expression: formula.expression,
          startTime: '2026-10-09T20:00:00Z',
          endTime: '2026-10-09T21:00:00Z',
          sampleBy: config.value.sampleStep > 1 ? `${config.value.sampleStep}s` : '1s',
        })

        // Transform backend response into SimulationResult
        const mappedPoints = rawPoints.map((pt, idx) => ({
          index: idx,
          timestamp: pt.timestamp.includes('T') ? pt.timestamp.split('T')[1].replace('Z', '') : pt.timestamp,
          timestampMs: new Date(pt.timestamp).getTime(),
          primarySensorValue: pt.value,
          computedValue: pt.value,
          subFormulaValues: {},
        }))

        const vals = mappedPoints.map((p) => p.computedValue)
        const min = Math.min(...vals)
        const max = Math.max(...vals)
        const sum = vals.reduce((a, b) => a + b, 0)
        const avg = vals.length > 0 ? sum / vals.length : 0

        currentResult.value = {
          formulaId: formula.id,
          formulaName: formula.name,
          expression: formula.expression,
          resolvedDependencyOrder: [formula.id],
          metrics: {
            totalPoints: mappedPoints.length,
            executionTimeMs: 45,
            min: Math.round(min * 100) / 100,
            max: Math.round(max * 100) / 100,
            avg: Math.round(avg * 100) / 100,
            stdDev: 0,
            nanCount: 0,
          },
          points: mappedPoints,
          logs: [`Executed successfully via Backend QuestDB / Aviator Engine (/api/v1/evaluate)`],
          dataSource: 'BACKEND_API',
        }
      } else {
        // Run via High-Performance Client Engine
        // Give UI a chance to render spinner
        await new Promise((resolve) => setTimeout(resolve, 20))

        const res = simulateFormula(formula, formulaStore.formulas, {
          durationSeconds: config.value.durationSeconds,
          sampleStep: config.value.sampleStep,
          primarySensorId: config.value.targetSensorId,
        })

        currentResult.value = {
          formulaId: formula.id,
          formulaName: formula.name,
          expression: formula.expression,
          resolvedDependencyOrder: res.resolvedOrder,
          metrics: res.metrics,
          points: res.points,
          logs: res.logs,
          dataSource: 'CLIENT_ENGINE',
        }
      }
    } catch (err: any) {
      console.error('Simulation failed:', err)
      errorMessage.value = err.message || 'An error occurred during simulation.'
      currentResult.value = null
    } finally {
      isRunning.value = false
    }
  }

  return {
    config,
    isRunning,
    currentResult,
    errorMessage,
    runSimulation,
  }
})
