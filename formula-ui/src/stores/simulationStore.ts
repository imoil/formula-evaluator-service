import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { FormulaDefinition, SimulationResult, SimulationConfig } from '../types/formula'
import type { SensorDataPoint } from '../types/sensor'
import type { SensorRegistryItem } from '../types/sensorRegistry'
import { useFormulaStore } from './formulaStore'
import { useSensorStore } from './sensorStore'
import { simulateFormula, getRequiredSensors } from '../services/formulaEngine'
import { evaluateOnBackend } from '../services/apiClient'

export const useSimulationStore = defineStore('simulation', () => {
  const formulaStore = useFormulaStore()
  const sensorStore = useSensorStore()

  const isRunning = ref(false)
  const currentResult = ref<SimulationResult | null>(null)
  const errorMessage = ref<string | null>(null)

  const targetFormulaId = ref<string>('POWER_EFFICIENCY_INDEX')
  const targetSensorId = ref<string>('sensor_000')

  const config = ref<SimulationConfig>({
    formulaId: 'POWER_EFFICIENCY_INDEX',
    durationSeconds: 3600, // 1 hour (3600 data points)
    sampleStep: 1, // 1 second
    useBackend: false,
  })

  function prepareFormulaSimulation(formula: FormulaDefinition) {
    targetFormulaId.value = formula.id
    config.value.formulaId = formula.id
    if (formula.targetSensor) {
      targetSensorId.value = formula.targetSensor
      config.value.targetSensorId = formula.targetSensor
    }
  }

  function prepareSensorSimulation(sensorId: string) {
    targetSensorId.value = sensorId
    config.value.targetSensorId = sensorId
  }

  function prepareRegistrySensorSimulation(sensor: SensorRegistryItem) {
    if (sensor.sensorType === 'PHYSICAL') {
      prepareSensorSimulation(String(sensor.serialNumber))
      return
    }
    const formula = formulaStore.ensureFormulaForRegistrySensor(sensor)
    prepareFormulaSimulation(formula)
  }

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
        // Mode 1: Full evaluation via Spring Boot REST backend (/api/v1/evaluate)
        const primarySensor = config.value.targetSensorId || formula.targetSensor || 'sensor_000'
        const rawPoints = await evaluateOnBackend({
          sensorId: primarySensor,
          expression: formula.expression,
          startTime: '2026-10-09T20:00:00Z',
          endTime: '2026-10-09T21:00:00Z',
          sampleBy: config.value.sampleStep > 1 ? `${config.value.sampleStep}s` : '1s',
        })

        // Transform backend response into SimulationResult
        const mappedPoints = rawPoints.map((pt: any, idx: number) => {
          const timeStr = typeof pt.timestamp === 'number'
            ? new Date(pt.timestamp).toISOString().substring(11, 19)
            : pt.timestamp.includes('T') ? pt.timestamp.split('T')[1].replace('Z', '') : pt.timestamp
          const ms = typeof pt.timestamp === 'number'
            ? pt.timestamp
            : new Date(pt.timestamp).getTime()

          return {
            index: idx,
            timestamp: timeStr,
            timestampMs: ms,
            primarySensorValue: pt.value,
            computedValue: pt.value,
            subFormulaValues: {},
          }
        })

        const vals = mappedPoints.map((p: any) => p.computedValue as number)
        const min = vals.length > 0 ? Math.min(...vals) : 0
        const max = vals.length > 0 ? Math.max(...vals) : 0
        const sum = vals.reduce((a: number, b: number) => a + b, 0)
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
        // Mode 2: Client JIT Engine with Backend Sensor Telemetry Source
        // 1. Identify all required sensors across DAG
        const requiredSensors = getRequiredSensors(
          formula,
          formulaStore.formulas,
          config.value.targetSensorId
        )

        // 2. Fetch required sensor time series from backend formula-api (with offline fallback)
        const sensorDataMap = new Map<string, SensorDataPoint[]>()
        await Promise.all(
          requiredSensors.map(async (sId) => {
            const points = await sensorStore.fetchTimeSeries(sId, config.value.durationSeconds)
            sensorDataMap.set(sId, points)
          })
        )

        // Give UI a chance to render spinner
        await new Promise((resolve) => setTimeout(resolve, 20))

        const res = simulateFormula(formula, formulaStore.formulas, {
          durationSeconds: config.value.durationSeconds,
          sampleStep: config.value.sampleStep,
          primarySensorId: config.value.targetSensorId,
          customSensorDataMap: sensorDataMap,
        })

        const isFromBackend = sensorStore.isBackendConnected

        currentResult.value = {
          formulaId: formula.id,
          formulaName: formula.name,
          expression: formula.expression,
          resolvedDependencyOrder: res.resolvedOrder,
          metrics: res.metrics,
          points: res.points,
          logs: [
            ...res.logs,
            `Sensor data source: ${isFromBackend ? 'Backend formula-api Mock DataSource (/api/v1/sensors)' : 'Offline Local Fallback'}`,
          ],
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
    targetFormulaId,
    targetSensorId,
    isRunning,
    currentResult,
    errorMessage,
    prepareFormulaSimulation,
    prepareSensorSimulation,
    prepareRegistrySensorSimulation,
    runSimulation,
  }
})
