import type { FormulaDefinition, FormulaDependencyInfo, SimulationResultPoint, SimulationMetrics } from '../types/formula'
import type { SensorDataPoint } from '../types/sensor'
import { generateSensorTimeSeries } from './sensorDataGenerator'

/**
 * Extract tokens representing sensors and potential formula IDs from an expression.
 */
export function extractReferences(
  expression: string,
  allFormulas: FormulaDefinition[]
): {
  sensorIds: string[]
  formulaIds: string[]
} {
  const formulaIdSet = new Set(allFormulas.map((f) => f.id))
  const foundSensors = new Set<string>()
  const foundFormulas = new Set<string>()

  // Match identifiers (alphanumeric + underscore)
  const identifierRegex = /\b[a-zA-Z_][a-zA-Z0-9_]*\b/g
  const reservedWords = new Set([
    'window_avg',
    'window_max',
    'window_min',
    'abs',
    'sqrt',
    'round',
    'floor',
    'ceil',
    'min',
    'max',
    'pow',
    'log',
    'sin',
    'cos',
    'true',
    'false',
    'null',
    'NaN',
    'Infinity',
    'value',
    'state',
  ])

  let match: RegExpExecArray | null
  while ((match = identifierRegex.exec(expression)) !== null) {
    const token = match[0]
    if (reservedWords.has(token)) {
      continue
    }

    if (/^sensor_\d{1,3}$/.test(token)) {
      // Normalize sensor_01 -> sensor_001 if needed
      const numPart = token.replace('sensor_', '')
      const paddedId = `sensor_${numPart.padStart(3, '0')}`
      foundSensors.add(paddedId)
    } else if (formulaIdSet.has(token)) {
      foundFormulas.add(token)
    }
  }

  return {
    sensorIds: Array.from(foundSensors),
    formulaIds: Array.from(foundFormulas),
  }
}

/**
 * Analyze dependencies for a formula, checking for cycles and finding topological order.
 */
export function analyzeFormulaDependencies(
  formulaId: string,
  allFormulas: FormulaDefinition[]
): FormulaDependencyInfo {
  const formulaMap = new Map<string, FormulaDefinition>(allFormulas.map((f) => [f.id, f]))
  const targetFormula = formulaMap.get(formulaId)

  if (!targetFormula) {
    return {
      referencedSensors: [],
      referencedFormulas: [],
      dependentFormulas: [],
      hasCycle: false,
    }
  }

  const { sensorIds, formulaIds } = extractReferences(targetFormula.expression, allFormulas)

  // Find which formulas depend on THIS formula
  const dependentFormulas: string[] = []
  for (const f of allFormulas) {
    if (f.id === formulaId) continue
    const refs = extractReferences(f.expression, allFormulas)
    if (refs.formulaIds.includes(formulaId)) {
      dependentFormulas.push(f.id)
    }
  }

  // Detect cycle using DFS
  const visited = new Set<string>()
  const recStack = new Set<string>()
  let cyclePath: string[] | undefined
  let hasCycle = false

  function dfs(currId: string, path: string[]): boolean {
    visited.add(currId)
    recStack.add(currId)

    const currFormula = formulaMap.get(currId)
    if (currFormula) {
      const childRefs = extractReferences(currFormula.expression, allFormulas)
      for (const childId of childRefs.formulaIds) {
        if (!visited.has(childId)) {
          if (dfs(childId, [...path, childId])) return true
        } else if (recStack.has(childId)) {
          hasCycle = true
          cyclePath = [...path, childId]
          return true
        }
      }
    }

    recStack.delete(currId)
    return false
  }

  dfs(formulaId, [formulaId])

  return {
    referencedSensors: sensorIds,
    referencedFormulas: formulaIds,
    dependentFormulas,
    hasCycle,
    cyclePath,
  }
}

/**
 * Topologically sort all formula dependencies required to evaluate targetFormula.
 * Returns order such that dependencies appear before the formula that uses them.
 */
export function getEvaluationOrder(
  targetFormulaId: string,
  allFormulas: FormulaDefinition[]
): { order: string[]; error?: string } {
  const formulaMap = new Map<string, FormulaDefinition>(allFormulas.map((f) => [f.id, f]))
  const visited = new Set<string>()
  const visiting = new Set<string>()
  const order: string[] = []

  function visit(id: string): string | null {
    if (visiting.has(id)) {
      return `Circular reference detected involving formula: ${id}`
    }
    if (!visited.has(id)) {
      visiting.add(id)
      const formula = formulaMap.get(id)
      if (formula) {
        const { formulaIds } = extractReferences(formula.expression, allFormulas)
        for (const depId of formulaIds) {
          const err = visit(depId)
          if (err) return err
        }
      }
      visiting.delete(id)
      visited.add(id)
      order.push(id)
    }
    return null
  }

  const error = visit(targetFormulaId)
  if (error) {
    return { order: [], error }
  }

  return { order }
}

/**
 * Safe expression evaluation context
 */
function createEvalFunction(expressionStr: string): (ctx: Record<string, any>) => number {
  // Transpile custom expressions to JavaScript:
  // 1. Replace power operator ^ with **
  // 2. Map window functions: window_avg(n) -> ctx.__window_avg(n)
  let transformed = expressionStr
    .replace(/\bwindow_avg\s*\(\s*(\d+)\s*\)/g, 'ctx.__window_avg($1)')
    .replace(/\bwindow_max\s*\(\s*(\d+)\s*\)/g, 'ctx.__window_max($1)')
    .replace(/\bwindow_min\s*\(\s*(\d+)\s*\)/g, 'ctx.__window_min($1)')
    .replace(/\babs\s*\(/g, 'Math.abs(')
    .replace(/\bsqrt\s*\(/g, 'Math.sqrt(')
    .replace(/\bround\s*\(/g, 'Math.round(')
    .replace(/\bfloor\s*\(/g, 'Math.floor(')
    .replace(/\bceil\s*\(/g, 'Math.ceil(')
    .replace(/\bmin\s*\(/g, 'Math.min(')
    .replace(/\bmax\s*\(/g, 'Math.max(')
    .replace(/\bpow\s*\(/g, 'Math.pow(')
    .replace(/\blog\s*\(/g, 'Math.log(')
    .replace(/\bsin\s*\(/g, 'Math.sin(')
    .replace(/\bcos\s*\(/g, 'Math.cos(')

  // Use Function constructor with 'ctx' argument
  const fnBody = `
    with (ctx) {
      return (${transformed});
    }
  `

  return new Function('ctx', fnBody) as (ctx: Record<string, any>) => number
}

/**
 * Execute simulation across a time series dataset
 */
export function simulateFormula(
  targetFormula: FormulaDefinition,
  allFormulas: FormulaDefinition[],
  options: {
    durationSeconds?: number
    sampleStep?: number
    primarySensorId?: string
  } = {}
): {
  points: SimulationResultPoint[]
  metrics: SimulationMetrics
  resolvedOrder: string[]
  logs: string[]
} {
  const durationSeconds = options.durationSeconds || 3600
  const sampleStep = Math.max(1, options.sampleStep || 1)
  const logs: string[] = []

  const startTimeMs = performance.now()

  // 1. Determine evaluation order
  const { order, error } = getEvaluationOrder(targetFormula.id, allFormulas)
  if (error) {
    throw new Error(error)
  }
  logs.push(`Evaluation DAG order: ${order.join(' -> ')}`)

  // 2. Collect all required sensors across the whole formula graph
  const formulaMap = new Map<string, FormulaDefinition>(allFormulas.map((f) => [f.id, f]))
  const requiredSensors = new Set<string>()

  // Add target sensor if explicitly defined
  const primarySensorId = options.primarySensorId || targetFormula.targetSensor || 'sensor_000'
  requiredSensors.add(primarySensorId)

  for (const id of order) {
    const f = formulaMap.get(id)
    if (f) {
      if (f.targetSensor) requiredSensors.add(f.targetSensor)
      const refs = extractReferences(f.expression, allFormulas)
      refs.sensorIds.forEach((s) => requiredSensors.add(s))
    }
  }

  logs.push(`Loaded ${requiredSensors.size} sensor telemetry stream(s): [${Array.from(requiredSensors).join(', ')}]`)

  // 3. Load or generate sensor time series
  const sensorDataMap = new Map<string, SensorDataPoint[]>()
  for (const sId of requiredSensors) {
    const ts = generateSensorTimeSeries(sId, durationSeconds)
    sensorDataMap.set(sId, ts.points)
  }

  // 4. Precompile evaluation functions
  const compiledFns = new Map<string, (ctx: Record<string, any>) => number>()
  for (const id of order) {
    const f = formulaMap.get(id)
    if (f) {
      try {
        compiledFns.set(id, createEvalFunction(f.expression))
      } catch (err: any) {
        throw new Error(`Syntax error in formula [${id}]: ${err.message}`)
      }
    }
  }

  // 5. Sliding window history buffers (keep last 100 points for window functions)
  const historyBuffers = new Map<string, number[]>()
  for (const sId of requiredSensors) {
    historyBuffers.set(sId, [])
  }
  // Also track history for target formula in case formula references itself or sub-formulas
  for (const id of order) {
    historyBuffers.set(id, [])
  }

  const points: SimulationResultPoint[] = []
  let sum = 0
  let min = Number.POSITIVE_INFINITY
  let max = Number.NEGATIVE_INFINITY
  let nanCount = 0
  const computedValues: number[] = []

  const totalSteps = Math.floor(durationSeconds / sampleStep)
  const primarySensorPoints = sensorDataMap.get(primarySensorId)!

  for (let step = 0; step < totalSteps; step++) {
    const sIndex = step * sampleStep
    const basePoint = primarySensorPoints[sIndex] || primarySensorPoints[0]

    // Construct context
    const ctx: Record<string, any> = {
      value: basePoint.value,
      state: basePoint.state,
      timestamp: basePoint.timestamp,
    }

    // Populate current sensor values and update sensor history buffers
    for (const [sId, sPoints] of sensorDataMap.entries()) {
      const p = sPoints[sIndex]
      const val = p ? p.value : 0
      ctx[sId] = val

      const buf = historyBuffers.get(sId)!
      buf.push(val)
      if (buf.length > 100) buf.shift()
    }

    // Bind window functions using the primary sensor's buffer
    const primaryBuf = historyBuffers.get(primarySensorId) || []
    ctx.__window_avg = (n: number) => {
      const slice = primaryBuf.slice(-Math.min(n, primaryBuf.length))
      if (slice.length === 0) return ctx.value
      return slice.reduce((a, b) => a + b, 0) / slice.length
    }
    ctx.__window_max = (n: number) => {
      const slice = primaryBuf.slice(-Math.min(n, primaryBuf.length))
      if (slice.length === 0) return ctx.value
      return Math.max(...slice)
    }
    ctx.__window_min = (n: number) => {
      const slice = primaryBuf.slice(-Math.min(n, primaryBuf.length))
      if (slice.length === 0) return ctx.value
      return Math.min(...slice)
    }

    // Evaluate sub-formulas in topological order
    const subFormulaVals: Record<string, number> = {}

    for (const fId of order) {
      const evalFn = compiledFns.get(fId)
      if (evalFn) {
        try {
          const res = Number(evalFn(ctx))
          ctx[fId] = res // Available for subsequent formulas in the DAG!
          subFormulaVals[fId] = res

          const buf = historyBuffers.get(fId)
          if (buf) {
            buf.push(res)
            if (buf.length > 100) buf.shift()
          }
        } catch (e: any) {
          ctx[fId] = Number.NaN
          subFormulaVals[fId] = Number.NaN
        }
      }
    }

    const finalVal = ctx[targetFormula.id] ?? Number.NaN
    const cleanFinalVal = Number.isFinite(finalVal) ? Math.round(finalVal * 1000) / 1000 : 0

    if (Number.isNaN(finalVal)) {
      nanCount++
    } else {
      sum += cleanFinalVal
      if (cleanFinalVal < min) min = cleanFinalVal
      if (cleanFinalVal > max) max = cleanFinalVal
      computedValues.push(cleanFinalVal)
    }

    points.push({
      index: step,
      timestamp: basePoint.timestamp,
      timestampMs: basePoint.timestampMs,
      primarySensorValue: basePoint.value,
      computedValue: cleanFinalVal,
      subFormulaValues: subFormulaVals,
    })
  }

  const executionTimeMs = Math.round((performance.now() - startTimeMs) * 100) / 100
  const count = computedValues.length
  const avg = count > 0 ? Math.round((sum / count) * 1000) / 1000 : 0

  // Calculate standard deviation
  let varianceSum = 0
  for (const v of computedValues) {
    varianceSum += Math.pow(v - avg, 2)
  }
  const stdDev = count > 0 ? Math.round(Math.sqrt(varianceSum / count) * 1000) / 1000 : 0

  if (min === Number.POSITIVE_INFINITY) min = 0
  if (max === Number.NEGATIVE_INFINITY) max = 0

  logs.push(`Completed simulation in ${executionTimeMs}ms (${points.length} points processed)`)

  return {
    points,
    metrics: {
      totalPoints: points.length,
      executionTimeMs,
      min,
      max,
      avg,
      stdDev,
      nanCount,
    },
    resolvedOrder: order,
    logs,
  }
}
