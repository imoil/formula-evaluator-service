<template>
  <v-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" max-width="850px" persistent>
    <v-card class="formula-editor-card">
      <v-toolbar color="surface" density="comfortable" class="border-b">
        <v-icon color="primary" class="ml-4">mdi-function-variant</v-icon>
        <v-toolbar-title class="text-subtitle-1 font-weight-bold">
          {{ isEditMode ? 'Edit Formula' : 'Create New Dynamic Formula' }}
        </v-toolbar-title>
        <v-spacer></v-spacer>
        <v-btn icon="mdi-close" variant="text" size="small" @click="close"></v-btn>
      </v-toolbar>

      <v-card-text class="pa-6">
        <v-form v-model="isFormValid" @submit.prevent="save">
          <v-row dense>
            <v-col cols="12" md="6">
              <v-text-field
                v-model="form.id"
                label="Formula Identifier (ID) *"
                placeholder="e.g. COMPOSITE_HEAT_INDEX"
                hint="Alphanumeric & underscore only. Used when other formulas call this formula."
                persistent-hint
                density="compact"
                variant="outlined"
                :disabled="isEditMode"
                :rules="[rules.required, rules.validId]"
              >
                <template #prepend-inner>
                  <v-icon size="small" color="secondary">mdi-identifier</v-icon>
                </template>
              </v-text-field>
            </v-col>

            <v-col cols="12" md="6">
              <v-text-field
                v-model="form.name"
                label="Display Name *"
                placeholder="e.g. Composite Heat Index"
                density="compact"
                variant="outlined"
                :rules="[rules.required]"
              ></v-text-field>
            </v-col>

            <v-col cols="12" md="6">
              <v-combobox
                v-model="form.category"
                :items="['Thermal', 'Pressure', 'Vibration', 'Electrical', 'Flow', 'Mechanical', 'Composite', 'KPI']"
                label="Category *"
                density="compact"
                variant="outlined"
                :rules="[rules.required]"
              ></v-combobox>
            </v-col>

            <v-col cols="12" md="6">
              <v-autocomplete
                v-model="form.targetSensor"
                :items="sensorOptions"
                item-title="title"
                item-value="value"
                label="Primary Target Sensor"
                hint="Default sensor mapped to 'value' in window functions"
                persistent-hint
                density="compact"
                variant="outlined"
                clearable
              >
                <template #prepend-inner>
                  <v-icon size="small" color="primary">mdi-access-point</v-icon>
                </template>
              </v-autocomplete>
            </v-col>

            <v-col cols="12">
              <v-textarea
                v-model="form.description"
                label="Description"
                placeholder="Briefly describe the physical meaning and formula intent..."
                rows="2"
                density="compact"
                variant="outlined"
              ></v-textarea>
            </v-col>

            <!-- Expression Builder -->
            <v-col cols="12">
              <div class="d-flex align-center justify-space-between mb-1">
                <span class="text-caption font-weight-bold text-medium-emphasis">
                  Formula Expression (Aviator / JS Syntax) *
                </span>
                <span class="text-caption text-secondary font-weight-medium">
                  Can reference other formulas by their ID!
                </span>
              </div>

              <!-- Quick Insert Toolbar -->
              <v-sheet rounded="lg" color="surface-variant" class="pa-2 mb-2 border">
                <div class="text-caption text-medium-emphasis mb-1 font-weight-bold">
                  Quick Insert Snippets:
                </div>
                <div class="d-flex flex-wrap gap-1 align-center">
                  <!-- Sub-Formula Chips -->
                  <v-menu v-if="otherFormulas.length > 0">
                    <template #activator="{ props }">
                      <v-btn size="x-small" color="primary" variant="tonal" prepend-icon="mdi-link-variant" v-bind="props" class="mr-1 mb-1">
                        Insert Sub-Formula
                      </v-btn>
                    </template>
                    <v-list density="compact" max-height="250">
                      <v-list-item
                        v-for="sub in otherFormulas"
                        :key="sub.id"
                        @click="insertToken(sub.id)"
                      >
                        <v-list-item-title class="font-weight-bold text-caption font-monospace">{{ sub.id }}</v-list-item-title>
                        <v-list-item-subtitle class="text-caption">{{ sub.name }}</v-list-item-subtitle>
                      </v-list-item>
                    </v-list>
                  </v-menu>

                  <!-- Sensors Menu -->
                  <v-menu>
                    <template #activator="{ props }">
                      <v-btn size="x-small" color="secondary" variant="tonal" prepend-icon="mdi-access-point" v-bind="props" class="mr-1 mb-1">
                        Insert Sensor
                      </v-btn>
                    </template>
                    <v-list density="compact" max-height="250">
                      <v-list-item
                        v-for="s in sampleSensorChoices"
                        :key="s.id"
                        @click="insertToken(s.id)"
                      >
                        <v-list-item-title class="font-weight-bold text-caption font-monospace">{{ s.id }}</v-list-item-title>
                        <v-list-item-subtitle class="text-caption">{{ s.name }} ({{ s.unit }})</v-list-item-subtitle>
                      </v-list-item>
                    </v-list>
                  </v-menu>

                  <!-- Window Functions -->
                  <v-btn size="x-small" variant="outlined" class="mr-1 mb-1" @click="insertToken('window_avg(10)')">
                    window_avg(10)
                  </v-btn>
                  <v-btn size="x-small" variant="outlined" class="mr-1 mb-1" @click="insertToken('window_max(10)')">
                    window_max(10)
                  </v-btn>
                  <v-btn size="x-small" variant="outlined" class="mr-1 mb-1" @click="insertToken('abs(')">
                    abs()
                  </v-btn>
                  <v-btn size="x-small" variant="outlined" class="mr-1 mb-1" @click="insertToken('sqrt(')">
                    sqrt()
                  </v-btn>
                  <v-btn size="x-small" variant="outlined" class="mr-1 mb-1" @click="insertToken(' ? 1.0 : 0.0')">
                    Ternary (? :)
                  </v-btn>
                </div>
              </v-sheet>

              <v-textarea
                v-model="form.expression"
                label="Expression *"
                placeholder="e.g. (SUB_FORMULA_A * 1.5) + window_avg(5) - sensor_002"
                rows="3"
                density="comfortable"
                variant="outlined"
                class="font-monospace text-body-2"
                :rules="[rules.required]"
                @update:model-value="validateDependencies"
              ></v-textarea>
            </v-col>

            <!-- Dependency & Validation Analysis Card -->
            <v-col cols="12">
              <v-alert
                v-if="validationError"
                type="error"
                variant="tonal"
                density="compact"
                class="mb-2"
                icon="mdi-alert-circle"
              >
                {{ validationError }}
              </v-alert>

              <v-sheet rounded="lg" color="surface-variant" class="pa-3 border">
                <div class="d-flex align-center justify-space-between mb-2">
                  <span class="text-caption font-weight-bold">
                    <v-icon size="small" color="primary" class="mr-1">mdi-graph-outline</v-icon>
                    Live Dependency Graph Analysis
                  </span>
                  <v-btn size="x-small" variant="tonal" color="info" prepend-icon="mdi-play" @click="testDryRun">
                    Dry Run Test
                  </v-btn>
                </div>

                <div class="d-flex flex-wrap align-center gap-2 mb-2">
                  <span class="text-caption text-medium-emphasis">Referenced Formulas:</span>
                  <template v-if="detectedFormulaRefs.length > 0">
                    <v-chip
                      v-for="fId in detectedFormulaRefs"
                      :key="fId"
                      size="x-small"
                      color="primary"
                      variant="flat"
                    >
                      <v-icon start size="12">mdi-link-variant</v-icon>
                      {{ fId }}
                    </v-chip>
                  </template>
                  <span v-else class="text-caption text-disabled">None (Self-contained)</span>
                </div>

                <div class="d-flex flex-wrap align-center gap-2 mb-2">
                  <span class="text-caption text-medium-emphasis">Referenced Sensors:</span>
                  <template v-if="detectedSensorRefs.length > 0">
                    <v-chip
                      v-for="sId in detectedSensorRefs"
                      :key="sId"
                      size="x-small"
                      color="secondary"
                      variant="flat"
                    >
                      <v-icon start size="12">mdi-access-point</v-icon>
                      {{ sId }}
                    </v-chip>
                  </template>
                  <span v-else class="text-caption text-disabled">None</span>
                </div>

                <div v-if="dryRunResult !== null" class="mt-2 text-caption font-monospace text-success font-weight-bold">
                  ✓ Dry Run Result on t=0: {{ dryRunResult }}
                </div>
              </v-sheet>
            </v-col>
          </v-row>
        </v-form>
      </v-card-text>

      <v-divider></v-divider>

      <v-card-actions class="pa-4 bg-surface">
        <v-btn variant="text" @click="close">Cancel</v-btn>
        <v-spacer></v-spacer>
        <v-btn
          color="primary"
          variant="flat"
          prepend-icon="mdi-content-save"
          :disabled="!isFormValid || !!validationError"
          @click="save"
        >
          {{ isEditMode ? 'Save Changes' : 'Create Formula' }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import type { FormulaDefinition } from '../types/formula'
import { useFormulaStore } from '../stores/formulaStore'
import { extractReferences } from '../services/formulaEngine'
import { getAllSensors } from '../services/sensorDataGenerator'

const props = defineProps<{
  modelValue: boolean
  formulaToEdit?: FormulaDefinition | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'saved', formula: FormulaDefinition): void
}>()

const formulaStore = useFormulaStore()
const allSensors = getAllSensors()

const isFormValid = ref(true)
const validationError = ref<string | null>(null)
const dryRunResult = ref<number | null>(null)

const isEditMode = computed(() => !!props.formulaToEdit)

const form = ref<Omit<FormulaDefinition, 'createdAt' | 'updatedAt'>>({
  id: '',
  name: '',
  description: '',
  expression: '',
  category: 'Composite',
  targetSensor: 'sensor_000',
  tags: [],
})

const sensorOptions = computed(() => {
  return allSensors.map((s) => ({
    title: `${s.id} - ${s.name} (${s.unit})`,
    value: s.id,
  }))
})

const sampleSensorChoices = computed(() => {
  return allSensors.slice(0, 15)
})

const otherFormulas = computed(() => {
  return formulaStore.formulas.filter((f) => f.id !== form.value.id)
})

const detectedFormulaRefs = computed(() => {
  return extractReferences(form.value.expression, formulaStore.formulas).formulaIds
})

const detectedSensorRefs = computed(() => {
  return extractReferences(form.value.expression, formulaStore.formulas).sensorIds
})

const rules = {
  required: (v: any) => !!v || 'This field is required.',
  validId: (v: string) => {
    if (!/^[A-Z0-9_]+$/.test(v)) {
      return 'Identifier must contain only uppercase letters, numbers, and underscores.'
    }
    if (!isEditMode.value && formulaStore.formulaMap.has(v)) {
      return 'Formula ID already exists. Choose a unique ID.'
    }
    return true
  },
}

function insertToken(token: string) {
  form.value.expression = (form.value.expression || '') + token
  validateDependencies()
}

function validateDependencies() {
  dryRunResult.value = null
  validationError.value = null

  if (!form.value.expression) return

  // Check self-reference
  const refs = extractReferences(form.value.expression, formulaStore.formulas)
  if (refs.formulaIds.includes(form.value.id)) {
    validationError.value = 'Self-reference detected! A formula cannot reference itself.'
    return
  }

  // Check syntax
  try {
    let dummy = form.value.expression
      .replace(/\bwindow_avg\s*\(\s*\d+\s*\)/g, '1')
      .replace(/\bwindow_max\s*\(\s*\d+\s*\)/g, '1')
      .replace(/\bwindow_min\s*\(\s*\d+\s*\)/g, '1')
    refs.formulaIds.forEach((f) => (dummy = dummy.replaceAll(f, '1')))
    refs.sensorIds.forEach((s) => (dummy = dummy.replaceAll(s, '1')))
    dummy = dummy.replaceAll('value', '1')

    // eslint-disable-next-line no-new-func
    new Function(`return (${dummy})`)
  } catch (err: any) {
    validationError.value = `Syntax error: ${err.message}`
  }
}

function testDryRun() {
  validateDependencies()
  if (validationError.value) return

  try {
    const ctx: Record<string, number> = { value: 50 }
    detectedSensorRefs.value.forEach((s) => (ctx[s] = 45.5))
    detectedFormulaRefs.value.forEach((f) => (ctx[f] = 72.0))

    let testExpr = form.value.expression
      .replace(/\bwindow_avg\s*\(\s*\d+\s*\)/g, 'ctx.value')
      .replace(/\bwindow_max\s*\(\s*\d+\s*\)/g, 'ctx.value')
      .replace(/\bwindow_min\s*\(\s*\d+\s*\)/g, 'ctx.value')

    // eslint-disable-next-line no-new-func
    const fn = new Function('ctx', `with(ctx) { return (${testExpr}); }`)
    const res = fn(ctx)
    dryRunResult.value = Math.round(Number(res) * 1000) / 1000
  } catch (e: any) {
    validationError.value = `Evaluation dry run failed: ${e.message}`
  }
}

function close() {
  emit('update:modelValue', false)
}

function save() {
  validateDependencies()
  if (validationError.value) return

  try {
    let saved: FormulaDefinition
    if (isEditMode.value) {
      formulaStore.updateFormula(form.value.id, {
        name: form.value.name,
        category: form.value.category,
        targetSensor: form.value.targetSensor,
        description: form.value.description,
        expression: form.value.expression,
      })
      saved = formulaStore.getFormulaById(form.value.id)!
    } else {
      saved = formulaStore.addFormula({
        id: form.value.id,
        name: form.value.name,
        category: form.value.category,
        targetSensor: form.value.targetSensor,
        description: form.value.description,
        expression: form.value.expression,
      })
    }
    emit('saved', saved)
    close()
  } catch (e: any) {
    validationError.value = e.message
  }
}

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      if (props.formulaToEdit) {
        form.value = {
          id: props.formulaToEdit.id,
          name: props.formulaToEdit.name,
          category: props.formulaToEdit.category,
          targetSensor: props.formulaToEdit.targetSensor || 'sensor_000',
          description: props.formulaToEdit.description || '',
          expression: props.formulaToEdit.expression,
          tags: props.formulaToEdit.tags || [],
        }
      } else {
        form.value = {
          id: 'NEW_FORMULA_' + Math.floor(Math.random() * 1000),
          name: 'New Custom Metric',
          category: 'Composite',
          targetSensor: 'sensor_000',
          description: '',
          expression: 'sensor_000 * 1.5',
          tags: [],
        }
      }
      validationError.value = null
      dryRunResult.value = null
      nextTick(() => validateDependencies())
    }
  }
)
</script>

<style scoped>
.font-monospace {
  font-family: 'JetBrains Mono', 'Fira Code', Menlo, Monaco, Consolas, monospace !important;
}
.gap-1 {
  gap: 6px;
}
.gap-2 {
  gap: 8px;
}
</style>
