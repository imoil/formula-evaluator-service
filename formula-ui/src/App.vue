<template>
  <v-app>
    <!-- Top Navigation App Bar -->
    <v-app-bar elevation="1" color="surface" class="border-b px-4">
      <div class="d-flex align-center">
        <v-avatar color="primary" variant="flat" size="36" class="mr-3">
          <v-icon color="white">mdi-chart-line-variant</v-icon>
        </v-avatar>
        <div>
          <div class="text-subtitle-1 font-weight-bold tracking-tight">
            Formula Evaluator Service
          </div>
          <div class="text-caption text-medium-emphasis">
            On-Demand Dynamic Formula Engine & IoT Telemetry Simulator
          </div>
        </div>
      </div>

      <v-spacer></v-spacer>

      <!-- System Quick Status Pills -->
      <div class="d-none d-md-flex align-center gap-2 mr-4">
        <v-chip
          size="small"
          :color="sensorStore.isBackendConnected ? 'success' : 'warning'"
          variant="tonal"
        >
          <v-icon start size="14">
            {{ sensorStore.isBackendConnected ? 'mdi-server-network' : 'mdi-cloud-off-outline' }}
          </v-icon>
          {{ sensorStore.isBackendConnected ? 'formula-api: Online' : 'formula-api: Offline' }}
        </v-chip>
        <v-chip size="small" color="primary" variant="tonal">
          <v-icon start size="14">mdi-access-point</v-icon>
          {{ sensorStore.sensors.length || 100 }} Sensors (1h / 1s interval)
        </v-chip>
        <v-chip size="small" color="secondary" variant="tonal">
          <v-icon start size="14">mdi-function-variant</v-icon>
          {{ formulaStore.formulas.length }} Formulas Defined
        </v-chip>
      </div>

      <!-- Action Buttons -->
      <v-btn
        color="primary"
        variant="flat"
        size="small"
        prepend-icon="mdi-plus"
        class="mr-2"
        @click="openCreateEditor"
      >
        New Formula
      </v-btn>

      <!-- Dark / Light theme toggle -->
      <v-btn
        icon
        variant="text"
        size="small"
        @click="toggleTheme"
      >
        <v-icon>{{ isDarkTheme ? 'mdi-weather-sunny' : 'mdi-weather-night' }}</v-icon>
      </v-btn>
    </v-app-bar>

    <!-- Main Content Container -->
    <v-main class="bg-background">
      <v-container fluid class="pa-4 pa-md-6" style="max-width: 1600px">
        <!-- Main Navigation Tabs -->
        <v-card class="mb-4 border elevation-0" rounded="lg">
          <v-tabs
            v-model="activeTab"
            color="primary"
            density="comfortable"
            grow
          >
            <v-tab value="formulas">
              <v-icon start>mdi-format-list-bulleted-square</v-icon>
              Formula Management ({{ formulaStore.formulas.length }})
            </v-tab>
            <v-tab value="simulation">
              <v-icon start>mdi-chart-bell-curve-cumulative</v-icon>
              Simulation Studio (1-Hour Timeseries)
            </v-tab>
            <v-tab value="sensors">
              <v-icon start>mdi-access-point-network</v-icon>
              100 Normal-Distribution Sensors (Source: formula-api)
            </v-tab>
          </v-tabs>
        </v-card>

        <!-- Tab 1: Formula List & Management -->
        <div v-show="activeTab === 'formulas'">
          <FormulaList
            @create="openCreateEditor"
            @edit="openEditEditor"
            @simulate="onSimulateFormula"
          />
        </div>

        <!-- Tab 2: Simulation Studio -->
        <div v-show="activeTab === 'simulation'">
          <FormulaSimulation
            :initial-formula-id="simulationTargetFormulaId"
            :initial-sensor-id="simulationTargetSensorId"
            :is-active="activeTab === 'simulation'"
          />
        </div>

        <!-- Tab 3: 100 Sample Sensors Explorer -->
        <div v-show="activeTab === 'sensors'">
          <SensorDataExplorer
            :is-active="activeTab === 'sensors'"
            @select-sensor-for-sim="onSelectSensorForSim"
          />
        </div>
      </v-container>
    </v-main>

    <!-- Formula Create / Edit Dialog -->
    <FormulaEditor
      v-model="editorDialog"
      :formula-to-edit="formulaToEdit"
      @saved="onFormulaSaved"
    />
  </v-app>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useTheme } from 'vuetify'
import type { FormulaDefinition } from './types/formula'
import { useFormulaStore } from './stores/formulaStore'
import { useSensorStore } from './stores/sensorStore'
import FormulaList from './components/FormulaList.vue'
import FormulaEditor from './components/FormulaEditor.vue'
import FormulaSimulation from './components/FormulaSimulation.vue'
import SensorDataExplorer from './components/SensorDataExplorer.vue'

const theme = useTheme()
const formulaStore = useFormulaStore()
const sensorStore = useSensorStore()

const activeTab = ref<'formulas' | 'simulation' | 'sensors'>('formulas')
const editorDialog = ref(false)
const formulaToEdit = ref<FormulaDefinition | null>(null)
const simulationTargetFormulaId = ref<string>('POWER_EFFICIENCY_INDEX')
const simulationTargetSensorId = ref<string>('sensor_000')

const isDarkTheme = computed(() => theme.global.current.value.dark)

function toggleTheme() {
  theme.global.name.value = isDarkTheme.value ? 'light' : 'dark'
}

function openCreateEditor() {
  formulaToEdit.value = null
  editorDialog.value = true
}

function openEditEditor(formula: FormulaDefinition) {
  formulaToEdit.value = formula
  editorDialog.value = true
}

function onFormulaSaved(saved: FormulaDefinition) {
  simulationTargetFormulaId.value = saved.id
}

function onSimulateFormula(formula: FormulaDefinition) {
  simulationTargetFormulaId.value = formula.id
  if (formula.targetSensor) {
    simulationTargetSensorId.value = formula.targetSensor
  }
  activeTab.value = 'simulation'
}

function onSelectSensorForSim(sensorId: string) {
  simulationTargetSensorId.value = sensorId
  activeTab.value = 'simulation'
}

// Synchronize responsive layout on tab change
watch(activeTab, async () => {
  await nextTick()
  window.dispatchEvent(new Event('resize'))
})

onMounted(() => {
  sensorStore.loadSensors()
})
</script>

<style>
/* Global styles */
html,
body {
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
}

.tracking-tight {
  letter-spacing: -0.02em;
}

.gap-2 {
  gap: 8px;
}

.gap-3 {
  gap: 12px;
}
</style>
