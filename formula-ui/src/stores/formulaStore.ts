import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { FormulaDefinition } from '../types/formula'
import type { SensorRegistryItem } from '../types/sensorRegistry'
import { analyzeFormulaDependencies, extractPrimarySensorId } from '../services/formulaEngine'
import { useSensorRegistryStore } from './sensorRegistryStore'

const LOCAL_STORAGE_KEY = 'formula_evaluator_definitions_v1'

const DEFAULT_FORMULAS: FormulaDefinition[] = [
  {
    id: 'TEMP_FAHRENHEIT',
    name: 'Exhaust Temp (°F)',
    category: 'Thermal',
    targetSensor: '10001',
    expression: 'P_10001 * 1.8 + 32',
    description: 'Converts primary temperature sensor from Celsius to Fahrenheit.',
    tags: ['Conversion', 'Thermal'],
    createdAt: '2026-10-09 10:00:00',
    updatedAt: '2026-10-09 10:00:00',
  },
  {
    id: 'SMOOTHED_TEMP',
    name: '10s Sliding Average Temp',
    category: 'Thermal',
    targetSensor: '10001',
    expression: 'window_avg(10)',
    description: 'Noise-reduced temperature using 10-second sliding window average.',
    tags: ['Window', 'Smoothing'],
    createdAt: '2026-10-09 10:05:00',
    updatedAt: '2026-10-09 10:05:00',
  },
  {
    id: 'PRESSURE_DELTA',
    name: 'Hydraulic Differential Pressure',
    category: 'Pressure',
    targetSensor: '10002',
    expression: 'abs(P_10002 - P_10004)',
    description: 'Absolute difference between discharge pressure (#10002) and intake pressure (#10004).',
    tags: ['Delta', 'Hydraulics'],
    createdAt: '2026-10-09 10:10:00',
    updatedAt: '2026-10-09 10:10:00',
  },
  {
    id: 'NORMALIZED_HEAT',
    name: 'Normalized Heat Quotient (Calls TEMP_FAHRENHEIT)',
    category: 'Composite',
    targetSensor: '10001',
    expression: '(TEMP_FAHRENHEIT - 32) / 1.8 * 0.95',
    description: 'Composite metric demonstrating calling sub-formula [TEMP_FAHRENHEIT] directly.',
    tags: ['Composite', 'Thermal'],
    createdAt: '2026-10-09 10:15:00',
    updatedAt: '2026-10-09 10:15:00',
  },
  {
    id: 'POWER_EFFICIENCY_INDEX',
    name: 'Overall Plant Efficiency Index (Calls NORMALIZED_HEAT & SMOOTHED_TEMP)',
    category: 'Composite',
    targetSensor: '10001',
    expression: '(NORMALIZED_HEAT > 60 ? NORMALIZED_HEAT * 1.15 : NORMALIZED_HEAT) + (SMOOTHED_TEMP * 0.2)',
    description: 'Deep composite formula referencing multiple sub-formulas [NORMALIZED_HEAT, SMOOTHED_TEMP] and conditional logic.',
    tags: ['Composite', 'KPI', 'Plant'],
    createdAt: '2026-10-09 10:20:00',
    updatedAt: '2026-10-09 10:20:00',
  },
]

export function toCleanFormulaId(rawCode: string): string {
  return rawCode.trim().toUpperCase().replace(/[^A-Z0-9_]/g, '_')
}

export function mapRegistrySensorToFormula(sensor: SensorRegistryItem): FormulaDefinition {
  const cleanId = toCleanFormulaId(sensor.sensorCode || `FML_${sensor.serialNumber}`)
  const targetSensor = extractPrimarySensorId(sensor.formulaExpression, '10001')
  return {
    id: cleanId,
    serialNumber: sensor.serialNumber,
    sensorCode: sensor.sensorCode,
    name: sensor.sensorName,
    category: sensor.category || 'Efficiency',
    targetSensor,
    expression: sensor.formulaExpression || 'value * 1.0',
    description: sensor.description || `Oracle Registered Formula Sensor #${sensor.serialNumber}`,
    tags: ['Registry', `P_${sensor.serialNumber}`],
    createdAt: sensor.createdAt || new Date().toISOString(),
    updatedAt: sensor.updatedAt || new Date().toISOString(),
  }
}

export const useFormulaStore = defineStore('formula', () => {
  const registryStore = useSensorRegistryStore()
  const customFormulas = ref<FormulaDefinition[]>([])
  const sessionAddedFormulas = ref<FormulaDefinition[]>([])

  function loadFromStorage() {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        const saved = window.localStorage.getItem(LOCAL_STORAGE_KEY)
        if (saved) {
          customFormulas.value = JSON.parse(saved)
          return
        }
      }
    } catch (e) {
      console.warn('Failed to load formulas from localStorage:', e)
    }
    customFormulas.value = [...DEFAULT_FORMULAS]
    saveToStorage()
  }

  function saveToStorage() {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        window.localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(customFormulas.value))
      }
    } catch (e) {
      console.error('Failed to persist formulas:', e)
    }
  }

  /**
   * Unified Sensor Registry (sensorRegistryStore.sensors 중 FORMULA 타입)와
   * 로컬 수식 정의를 단방향 반응형(computed)으로 통합
   */
  const formulas = computed<FormulaDefinition[]>(() => {
    if (registryStore.sensors.length === 0) {
      return customFormulas.value
    }

    const mergedMap = new Map<string, FormulaDefinition>()
    for (const item of registryStore.sensors) {
      if (item.sensorType === 'FORMULA') {
        const def = mapRegistrySensorToFormula(item)
        mergedMap.set(def.id, def)
      }
    }
    for (const f of sessionAddedFormulas.value) {
      if (!mergedMap.has(f.id)) {
        mergedMap.set(f.id, f)
      }
    }
    return Array.from(mergedMap.values())
  })

  const formulaMap = computed(() => {
    const map = new Map<string, FormulaDefinition>()
    for (const f of formulas.value) {
      map.set(f.id, f)
      if (f.sensorCode) {
        map.set(f.sensorCode, f)
      }
      if (f.serialNumber) {
        map.set(String(f.serialNumber), f)
        map.set(`P_${f.serialNumber}`, f)
        map.set(`SN_${f.serialNumber}`, f)
      }
    }
    return map
  })

  const formulaCategories = computed(() => {
    const set = new Set(formulas.value.map((f) => f.category))
    return Array.from(set).filter(Boolean)
  })

  function getFormulaById(id: string): FormulaDefinition | undefined {
    return formulaMap.value.get(id) || formulaMap.value.get(toCleanFormulaId(id))
  }

  function getDependencies(id: string) {
    const resolved = getFormulaById(id)
    return analyzeFormulaDependencies(resolved ? resolved.id : id, formulas.value)
  }

  function addFormula(definition: Omit<FormulaDefinition, 'createdAt' | 'updatedAt'>): FormulaDefinition {
    const cleanId = toCleanFormulaId(definition.id)
    if (formulaMap.value.has(cleanId)) {
      throw new Error(`Formula with ID "${cleanId}" already exists.`)
    }

    const now = new Date().toISOString().replace('T', ' ').substring(0, 19)
    const newFormula: FormulaDefinition = {
      ...definition,
      id: cleanId,
      createdAt: now,
      updatedAt: now,
    }

    customFormulas.value.push(newFormula)
    sessionAddedFormulas.value.push(newFormula)
    saveToStorage()
    return newFormula
  }

  function updateFormula(id: string, updates: Partial<FormulaDefinition>) {
    const index = customFormulas.value.findIndex((f) => f.id === id)
    const sessionIdx = sessionAddedFormulas.value.findIndex((f) => f.id === id)
    if (index === -1 && sessionIdx === -1 && !formulaMap.value.has(id)) {
      throw new Error(`Formula with ID "${id}" not found.`)
    }

    const now = new Date().toISOString().replace('T', ' ').substring(0, 19)
    if (index !== -1) {
      customFormulas.value[index] = {
        ...customFormulas.value[index],
        ...updates,
        updatedAt: now,
      }
    }
    if (sessionIdx !== -1) {
      sessionAddedFormulas.value[sessionIdx] = {
        ...sessionAddedFormulas.value[sessionIdx],
        ...updates,
        updatedAt: now,
      }
    }
    saveToStorage()
  }

  function deleteFormula(id: string) {
    const deps = getDependencies(id)
    if (deps.dependentFormulas.length > 0) {
      throw new Error(
        `Cannot delete "${id}". It is referenced by formulas: ${deps.dependentFormulas.join(', ')}`
      )
    }

    customFormulas.value = customFormulas.value.filter((f) => f.id !== id)
    sessionAddedFormulas.value = sessionAddedFormulas.value.filter((f) => f.id !== id)
    saveToStorage()
  }

  function duplicateFormula(id: string): FormulaDefinition {
    const source = getFormulaById(id)
    if (!source) throw new Error('Formula not found.')

    let counter = 1
    let newId = `${source.id}_COPY`
    while (formulaMap.value.has(newId)) {
      counter++
      newId = `${source.id}_COPY_${counter}`
    }

    return addFormula({
      ...source,
      id: newId,
      name: `${source.name} (Copy)`,
    })
  }

  function resetToDefaults() {
    customFormulas.value = [...DEFAULT_FORMULAS]
    sessionAddedFormulas.value = []
    saveToStorage()
  }

  function exportJson(): string {
    return JSON.stringify(formulas.value, null, 2)
  }

  function importJson(jsonString: string): { importedCount: number } {
    const parsed = JSON.parse(jsonString)
    if (!Array.isArray(parsed)) throw new Error('Invalid JSON format: expected an array.')

    let count = 0
    for (const item of parsed) {
      if (!item.id || !item.expression || !item.name) continue
      const existingIdx = customFormulas.value.findIndex((f) => f.id === item.id)
      if (existingIdx !== -1) {
        customFormulas.value[existingIdx] = item
      } else {
        customFormulas.value.push(item)
      }
      const sessionIdx = sessionAddedFormulas.value.findIndex((f) => f.id === item.id)
      if (sessionIdx !== -1) {
        sessionAddedFormulas.value[sessionIdx] = item
      } else {
        sessionAddedFormulas.value.push(item)
      }
      count++
    }
    saveToStorage()
    return { importedCount: count }
  }

  function ensureFormulaForRegistrySensor(sensor: SensorRegistryItem): FormulaDefinition {
    const existing =
      getFormulaById(`P_${sensor.serialNumber}`) ||
      getFormulaById(`SN_${sensor.serialNumber}`) ||
      getFormulaById(sensor.sensorCode) ||
      getFormulaById(toCleanFormulaId(sensor.sensorCode))

    if (existing) {
      const targetSensor = extractPrimarySensorId(sensor.formulaExpression, '10001')
      if (sensor.formulaExpression && existing.expression !== sensor.formulaExpression) {
        updateFormula(existing.id, {
          expression: sensor.formulaExpression,
          targetSensor,
        })
      }
      return getFormulaById(existing.id) || mapRegistrySensorToFormula(sensor)
    }

    const mapped = mapRegistrySensorToFormula(sensor)
    return addFormula({
      id: mapped.id,
      serialNumber: mapped.serialNumber,
      sensorCode: mapped.sensorCode,
      name: mapped.name,
      expression: mapped.expression,
      category: mapped.category,
      targetSensor: mapped.targetSensor,
      description: mapped.description,
    })
  }

  // Initialize store
  loadFromStorage()

  return {
    formulas,
    formulaMap,
    formulaCategories,
    getFormulaById,
    getDependencies,
    addFormula,
    updateFormula,
    deleteFormula,
    duplicateFormula,
    ensureFormulaForRegistrySensor,
    resetToDefaults,
    exportJson,
    importJson,
  }
})

