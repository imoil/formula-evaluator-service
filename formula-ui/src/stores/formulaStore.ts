import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { FormulaDefinition, FormulaDependencyInfo } from '../types/formula'
import { analyzeFormulaDependencies } from '../services/formulaEngine'

const LOCAL_STORAGE_KEY = 'formula_evaluator_definitions_v1'

const DEFAULT_FORMULAS: FormulaDefinition[] = [
  {
    id: 'TEMP_FAHRENHEIT',
    name: 'Exhaust Temp (°F)',
    category: 'Thermal',
    targetSensor: 'sensor_000',
    expression: 'sensor_000 * 1.8 + 32',
    description: 'Converts primary temperature sensor from Celsius to Fahrenheit.',
    tags: ['Conversion', 'Thermal'],
    createdAt: '2026-10-09 10:00:00',
    updatedAt: '2026-10-09 10:00:00',
  },
  {
    id: 'SMOOTHED_TEMP',
    name: '10s Sliding Average Temp',
    category: 'Thermal',
    targetSensor: 'sensor_000',
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
    targetSensor: 'sensor_001',
    expression: 'abs(sensor_001 - sensor_008)',
    description: 'Absolute difference between line inlet pressure (sensor_001) and return line pressure (sensor_008).',
    tags: ['Delta', 'Hydraulics'],
    createdAt: '2026-10-09 10:10:00',
    updatedAt: '2026-10-09 10:10:00',
  },
  {
    id: 'NORMALIZED_HEAT',
    name: 'Normalized Heat Quotient (Calls TEMP_FAHRENHEIT)',
    category: 'Composite',
    targetSensor: 'sensor_000',
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
    targetSensor: 'sensor_000',
    expression: '(NORMALIZED_HEAT > 60 ? NORMALIZED_HEAT * 1.15 : NORMALIZED_HEAT) + (SMOOTHED_TEMP * 0.2)',
    description: 'Deep composite formula referencing multiple sub-formulas [NORMALIZED_HEAT, SMOOTHED_TEMP] and conditional logic.',
    tags: ['Composite', 'KPI', 'Plant'],
    createdAt: '2026-10-09 10:20:00',
    updatedAt: '2026-10-09 10:20:00',
  },
]

export const useFormulaStore = defineStore('formula', () => {
  const formulas = ref<FormulaDefinition[]>([])

  function loadFromStorage() {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        const saved = window.localStorage.getItem(LOCAL_STORAGE_KEY)
        if (saved) {
          formulas.value = JSON.parse(saved)
          return
        }
      }
    } catch (e) {
      console.warn('Failed to load formulas from localStorage:', e)
    }
    formulas.value = [...DEFAULT_FORMULAS]
    saveToStorage()
  }

  function saveToStorage() {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        window.localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(formulas.value))
      }
    } catch (e) {
      console.error('Failed to persist formulas:', e)
    }
  }

  const formulaMap = computed(() => {
    return new Map(formulas.value.map((f) => [f.id, f]))
  })

  const formulaCategories = computed(() => {
    const set = new Set(formulas.value.map((f) => f.category))
    return Array.from(set).filter(Boolean)
  })

  function getFormulaById(id: string): FormulaDefinition | undefined {
    return formulaMap.value.get(id)
  }

  function getDependencies(id: string): FormulaDependencyInfo {
    return analyzeFormulaDependencies(id, formulas.value)
  }

  function addFormula(definition: Omit<FormulaDefinition, 'createdAt' | 'updatedAt'>): FormulaDefinition {
    // Check ID conflict
    const cleanId = definition.id.trim().toUpperCase().replace(/[^A-Z0-9_]/g, '_')
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

    formulas.value.push(newFormula)
    saveToStorage()
    return newFormula
  }

  function updateFormula(id: string, updates: Partial<FormulaDefinition>) {
    const index = formulas.value.findIndex((f) => f.id === id)
    if (index === -1) {
      throw new Error(`Formula with ID "${id}" not found.`)
    }

    const now = new Date().toISOString().replace('T', ' ').substring(0, 19)
    formulas.value[index] = {
      ...formulas.value[index],
      ...updates,
      updatedAt: now,
    }
    saveToStorage()
  }

  function deleteFormula(id: string) {
    // Check if other formulas depend on it
    const deps = getDependencies(id)
    if (deps.dependentFormulas.length > 0) {
      throw new Error(
        `Cannot delete "${id}". It is referenced by formulas: ${deps.dependentFormulas.join(', ')}`
      )
    }

    formulas.value = formulas.value.filter((f) => f.id !== id)
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
    formulas.value = [...DEFAULT_FORMULAS]
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
      const existingIdx = formulas.value.findIndex((f) => f.id === item.id)
      if (existingIdx !== -1) {
        formulas.value[existingIdx] = item
      } else {
        formulas.value.push(item)
      }
      count++
    }
    saveToStorage()
    return { importedCount: count }
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
    resetToDefaults,
    exportJson,
    importJson,
  }
})
