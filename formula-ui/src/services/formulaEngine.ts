import type {
  FormulaDefinition,
  FormulaDependencyInfo,
  SimulationResultPoint,
  SimulationMetrics,
} from '../types/formula'
import type { SensorDataPoint } from '../types/sensor'
import { generateSensorTimeSeries } from './sensorDataGenerator'

/**
 * Tokenize an expression and extract referenced variables
 */
export function extractReferences(
  expressionStr: string,
  allFormulas: FormulaDefinition[]
): {
  sensorIds: string[]
  formulaIds: string[]
} {
  // Built-in JS functions, keywords, operators, and window aggregation functions
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
    'value',
    'true',
    'false',
    'null',
    'undefined',
    'NaN',
    'Infinity',
    'Math',
  ])

  const knownFormulaIds = new Set(allFormulas.map((f) => f.id))

  // Match identifier tokens: letters, numbers, underscores (starting with letter or underscore)
  const identifierRegex = /[a-zA-Z_][a-zA-Z0-9_]*/g
  const tokens = expressionStr.match(identifierRegex) || []

  const sensorIds = new Set<string>()
  const formulaIds = new Set<string>()

  for (const token of tokens) {
    if (reservedWords.has(token)) continue

    // Detect sensor tokens (e.g. sensor_000, temp_sensor_1, SENSOR_01)
    if (/^(sensor_\d{3}|sensor_\d+|[A-Za-z0-9_]+_sensor|[A-Za-z0-9_]+_stream)$/i.test(token)) {
      sensorIds.add(token)
    } else if (knownFormulaIds.has(token)) {
      formulaIds.add(token)
    } else if (token.startsWith('sensor_')) {
      sensorIds.add(token)
    } else {
      // If it looks like a formula identifier (UPPER_CASE or defined formula)
      if (/^[A-Z0-9_]+$/.test(token) && knownFormulaIds.has(token)) {
        formulaIds.add(token)
      }
    }
  }

  return {
    sensorIds: Array.from(sensorIds),
    formulaIds: Array.from(formulaIds),
  }
}

/**
 * Perform static DAG analysis to find all upstream and downstream references and detect circular dependency cycles
 */
export function analyzeFormulaDependencies(
  formulaId: string,
  allFormulas: FormulaDefinition[]
): FormulaDependencyInfo {
  const formulaMap = new Map<string, FormulaDefinition>(allFormulas.map((f) => [f.id, f]))
  const target = formulaMap.get(formulaId)

  if (!target) {
    return {
      referencedSensors: [],
      referencedFormulas: [],
      dependentFormulas: [],
      hasCycle: false,
    }
  }

  const { sensorIds, formulaIds } = extractReferences(target.expression, allFormulas)

  // Find all formulas that directly depend on (call) this formula
  const dependentFormulas: string[] = []
  for (const f of allFormulas) {
    if (f.id === formulaId) continue
    const refs = extractReferences(f.expression, allFormulas)
    if (refs.formulaIds.includes(formulaId)) {
      dependentFormulas.push(f.id)
    }
  }

  // Detect circular dependency using DFS cycle detection
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
): {
  order: string[]
  error?: string
} {
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
 * Collect all required sensor IDs for evaluating a formula and its dependency tree
 */
export function getRequiredSensors(
  targetFormula: FormulaDefinition,
  allFormulas: FormulaDefinition[],
  primarySensorId?: string
): string[] {
  const { order } = getEvaluationOrder(targetFormula.id, allFormulas)
  const formulaMap = new Map<string, FormulaDefinition>(allFormulas.map((f) => [f.id, f]))
  const requiredSensors = new Set<string>()

  const primary = primarySensorId || targetFormula.targetSensor || 'sensor_000'
  requiredSensors.add(primary)

  for (const id of order) {
    const f = formulaMap.get(id)
    if (f) {
      if (f.targetSensor) requiredSensors.add(f.targetSensor)
      const refs = extractReferences(f.expression, allFormulas)
      refs.sensorIds.forEach((s) => requiredSensors.add(s))
    }
  }

  return Array.from(requiredSensors)
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
    customSensorDataMap?: Map<string, SensorDataPoint[]>
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

  // 3. Load sensor time series: Use backend provided dataset if available, fallback to generator
  const sensorDataMap = new Map<string, SensorDataPoint[]>()
  for (const sId of requiredSensors) {
    if (options.customSensorDataMap && options.customSensorDataMap.has(sId)) {
      sensorDataMap.set(sId, options.customSensorDataMap.get(sId)!)
    } else {
      const ts = generateSensorTimeSeries(sId, durationSeconds)
      sensorDataMap.set(sId, ts.points)
    }
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
  for (const id of order) {
    historyBuffers.set(id, [])
  }

  const resultPoints: SimulationResultPoint[] = []
  let totalEvaluated = 0
  let nanCount = 0

  const primarySensorSeries = sensorDataMap.get(primarySensorId) || []
  const availableLength = Math.min(
    durationSeconds,
    ...Array.from(sensorDataMap.values()).map((p) => p.length)
  )

  // 6. Step through time series at 1-second interval
  for (let t = 0; t < availableLength; t++) {
    // Only capture points according to sampleStep resolution
    const shouldRecord = t % sampleStep === 0

    // Construct evaluation context for this time step
    const ctx: Record<string, any> = {}

    // Inject sensor telemetry values for current second
    for (const sId of requiredSensors) {
      const series = sensorDataMap.get(sId)
      const val = series && series[t] ? series[t].value : 0
      ctx[sId] = val

      // Update sliding window buffer
      const buf = historyBuffers.get(sId)!
      buf.push(val)
      if (buf.length > 100) buf.shift()
    }

    // Default 'value' alias maps to primary sensor
    const primaryPt = primarySensorSeries[t]
    const primarySensorVal = primaryPt ? primaryPt.value : 0
    ctx.value = primarySensorVal

    // Window aggregation helpers
    ctx.__window_avg = (windowSize: number) => {
      const buf = historyBuffers.get(primarySensorId) || []
      const slice = buf.slice(-Math.min(buf.length, windowSize))
      if (slice.length === 0) return 0
      const sum = slice.reduce((acc, v) => acc + v, 0)
      return sum / slice.length
    }

    ctx.__window_max = (windowSize: number) => {
      const buf = historyBuffers.get(primarySensorId) || []
      const slice = buf.slice(-Math.min(buf.length, windowSize))
      return slice.length > 0 ? Math.max(...slice) : 0
    }

    ctx.__window_min = (windowSize: number) => {
      const buf = historyBuffers.get(primarySensorId) || []
      const slice = buf.slice(-Math.min(buf.length, windowSize))
      return slice.length > 0 ? Math.min(...slice) : 0
    }

    // Evaluate formulas in topological order
    const intermediateValues: Record<string, number> = {}

    for (const formulaId of order) {
      const fn = compiledFns.get(formulaId)
      if (fn) {
        try {
          const res = fn(ctx)
          const numRes = typeof res === 'number' && !isNaN(res) ? res : 0
          ctx[formulaId] = numRes
          intermediateValues[formulaId] = numRes

          // Update formula's own history buffer
          const fBuf = historyBuffers.get(formulaId)!
          fBuf.push(numRes)
          if (fBuf.length > 100) fBuf.shift()
        } catch {
          ctx[formulaId] = 0
          intermediateValues[formulaId] = 0
          nanCount++
        }
      }
    }

    const finalOutput = intermediateValues[targetFormula.id] ?? 0
    if (isNaN(finalOutput)) nanCount++

    if (shouldRecord) {
      const timeLabel = primaryPt
        ? primaryPt.timestamp
        : new Date(1791576000000 + t * 1000).toISOString().substring(11, 19)

      resultPoints.push({
        index: t,
        timestamp: timeLabel,
        timestampMs: primaryPt ? primaryPt.timestampMs : 1791576000000 + t * 1000,
        primarySensorValue: primarySensorVal,
        computedValue: Math.round(finalOutput * 1000) / 1000,
        subFormulaValues: intermediateValues,
      })
    }

    totalEvaluated++
  }

  const executionTimeMs = Math.round((performance.now() - startTimeMs) * 10) / 10

  // 7. Compute aggregate statistical metrics
  const values = resultPoints.map((p) => p.computedValue)
  const min = values.length > 0 ? Math.min(...values) : 0
  const max = values.length > 0 ? Math.max(...values) : 0
  const sum = values.reduce((acc, v) => acc + v, 0)
  const avg = values.length > 0 ? sum / values.length : 0

  // Standard deviation
  const variance =
    values.length > 0
      ? values.reduce((acc, v) => acc + Math.pow(v - avg, 2), 0) / values.length
      : 0
  const stdDev = Math.sqrt(variance)

  const metrics: SimulationMetrics = {
    totalPoints: resultPoints.length,
    executionTimeMs,
    min: Math.round(min * 100) / 100,
    max: Math.round(max * 100) / 100,
    avg: Math.round(avg * 100) / 100,
    stdDev: Math.round(stdDev * 100) / 100,
    nanCount,
  }

  return {
    points: resultPoints,
    metrics,
    resolvedOrder: order,
    logs,
  }
}
