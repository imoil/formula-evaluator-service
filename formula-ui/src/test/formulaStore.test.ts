import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useFormulaStore } from '../stores/formulaStore'

describe('Formula Store State & CRUD Operations', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should initialize with default pre-configured formulas', () => {
    const store = useFormulaStore()
    expect(store.formulas.length).toBeGreaterThan(0)
    expect(store.getFormulaById('POWER_EFFICIENCY_INDEX')).toBeDefined()
    expect(store.getFormulaById('TEMP_FAHRENHEIT')).toBeDefined()
  })

  it('should create and retrieve a new formula', () => {
    const store = useFormulaStore()
    const newFormula = store.addFormula({
      id: 'CUSTOM_TEST_METRIC',
      name: 'Custom Test Metric',
      category: 'KPI',
      expression: 'sensor_005 * 2.5',
      targetSensor: 'sensor_005',
      description: 'Test description',
    })

    expect(newFormula.id).toBe('CUSTOM_TEST_METRIC')
    const retrieved = store.getFormulaById('CUSTOM_TEST_METRIC')
    expect(retrieved).toBeDefined()
    expect(retrieved?.expression).toBe('sensor_005 * 2.5')
  })

  it('should prevent deleting a formula when another formula depends on it', () => {
    const store = useFormulaStore()
    // NORMALIZED_HEAT depends on TEMP_FAHRENHEIT
    expect(() => {
      store.deleteFormula('TEMP_FAHRENHEIT')
    }).toThrow(/Cannot delete/)
  })

  it('should successfully delete an unreferenced formula', () => {
    const store = useFormulaStore()
    store.addFormula({
      id: 'ORPHAN_FORMULA',
      name: 'Orphan Formula',
      category: 'Thermal',
      expression: '1 + 1',
    })
    expect(store.getFormulaById('ORPHAN_FORMULA')).toBeDefined()
    store.deleteFormula('ORPHAN_FORMULA')
    expect(store.getFormulaById('ORPHAN_FORMULA')).toBeUndefined()
  })

  it('should duplicate an existing formula with unique id', () => {
    const store = useFormulaStore()
    const dup = store.duplicateFormula('TEMP_FAHRENHEIT')
    expect(dup.id).toContain('TEMP_FAHRENHEIT_COPY')
    expect(store.getFormulaById(dup.id)).toBeDefined()
  })
})
