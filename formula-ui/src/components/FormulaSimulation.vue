<template>
  <div class="formula-simulation-container">
    <!-- Configuration Control Panel -->
    <v-card class="mb-4 border elevation-0" rounded="lg">
      <v-card-text class="pa-4">
        <v-row align="center" dense>
          <!-- Formula Selector -->
          <v-col cols="12" md="3">
            <v-select
              v-model="selectedFormulaId"
              :items="formulaOptions"
              item-title="title"
              item-value="value"
              label="Select Formula to Simulate *"
              density="compact"
              variant="outlined"
              hide-details
            >
              <template #prepend-inner>
                <v-icon size="small" color="primary">mdi-function</v-icon>
              </template>
            </v-select>
          </v-col>

          <!-- Primary Sensor Selector -->
          <v-col cols="12" md="3">
            <v-autocomplete
              v-model="selectedSensorId"
              :items="sensorOptions"
              item-title="title"
              item-value="value"
              label="Target Sensor Stream (100 Sensors) *"
              density="compact"
              variant="outlined"
              hide-details
            >
              <template #prepend-inner>
                <v-icon size="small" color="secondary">mdi-access-point</v-icon>
              </template>
            </v-autocomplete>
          </v-col>

          <!-- Duration Selector -->
          <v-col cols="12" sm="6" md="2">
            <v-select
              v-model="selectedDuration"
              :items="durationOptions"
              label="Duration"
              density="compact"
              variant="outlined"
              hide-details
            ></v-select>
          </v-col>

          <!-- Resolution / Sample Step -->
          <v-col cols="12" sm="6" md="2">
            <v-select
              v-model="selectedSampleStep"
              :items="sampleStepOptions"
              label="Sampling Rate"
              density="compact"
              variant="outlined"
              hide-details
            ></v-select>
          </v-col>

          <!-- Run Button -->
          <v-col cols="12" md="2" class="d-flex justify-end">
            <v-btn
              color="primary"
              variant="flat"
              block
              size="large"
              prepend-icon="mdi-play"
              :loading="simulationStore.isRunning"
              @click="run"
            >
              Simulate
            </v-btn>
          </v-col>
        </v-row>

        <!-- Secondary options row -->
        <v-row align="center" dense class="mt-2 pt-2 border-t">
          <v-col cols="12" md="8" class="d-flex align-center flex-wrap gap-3">
            <div class="text-caption text-medium-emphasis d-flex align-center">
              <v-icon size="small" class="mr-1 text-primary">mdi-information-outline</v-icon>
              Formula Expression:
              <code class="ml-2 font-monospace text-primary bg-surface-variant px-2 py-1 rounded">
                {{ currentFormula?.expression || 'None' }}
              </code>
            </div>
          </v-col>

          <v-col cols="12" md="4" class="d-flex justify-end align-center">
            <v-switch
              v-model="useBackend"
              color="primary"
              density="compact"
              hide-details
              inset
              label="Connect to Spring Boot REST API (/api/v1/evaluate)"
              class="text-caption"
            ></v-switch>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <!-- Error Alert -->
    <v-alert
      v-if="simulationStore.errorMessage"
      type="error"
      variant="tonal"
      density="comfortable"
      class="mb-4"
      closable
      icon="mdi-alert-circle"
      @click:close="simulationStore.errorMessage = null"
    >
      {{ simulationStore.errorMessage }}
    </v-alert>

    <!-- Simulation Results Area -->
    <template v-if="simulationStore.currentResult">
      <!-- KPI Metrics Cards -->
      <v-row dense class="mb-4">
        <v-col cols="6" sm="4" md="2">
          <v-card class="pa-3 border text-center" rounded="lg" color="surface">
            <div class="text-caption text-medium-emphasis">Data Points</div>
            <div class="text-h6 font-weight-bold text-primary">
              {{ simulationStore.currentResult.metrics.totalPoints.toLocaleString() }}
            </div>
            <div class="text-caption text-disabled">1 pt / sec</div>
          </v-card>
        </v-col>

        <v-col cols="6" sm="4" md="2">
          <v-card class="pa-3 border text-center" rounded="lg" color="surface">
            <div class="text-caption text-medium-emphasis">Eval Latency</div>
            <div class="text-h6 font-weight-bold text-accent">
              {{ simulationStore.currentResult.metrics.executionTimeMs }} ms
            </div>
            <div class="text-caption text-disabled">{{ simulationStore.currentResult.dataSource }}</div>
          </v-card>
        </v-col>

        <v-col cols="6" sm="4" md="2">
          <v-card class="pa-3 border text-center" rounded="lg" color="surface">
            <div class="text-caption text-medium-emphasis">Mean (Avg)</div>
            <div class="text-h6 font-weight-bold">
              {{ simulationStore.currentResult.metrics.avg }}
            </div>
            <div class="text-caption text-disabled">Computed result</div>
          </v-card>
        </v-col>

        <v-col cols="6" sm="4" md="2">
          <v-card class="pa-3 border text-center" rounded="lg" color="surface">
            <div class="text-caption text-medium-emphasis">Min Value</div>
            <div class="text-h6 font-weight-bold text-info">
              {{ simulationStore.currentResult.metrics.min }}
            </div>
            <div class="text-caption text-disabled">Lowest point</div>
          </v-card>
        </v-col>

        <v-col cols="6" sm="4" md="2">
          <v-card class="pa-3 border text-center" rounded="lg" color="surface">
            <div class="text-caption text-medium-emphasis">Max Value</div>
            <div class="text-h6 font-weight-bold text-warning">
              {{ simulationStore.currentResult.metrics.max }}
            </div>
            <div class="text-caption text-disabled">Peak point</div>
          </v-card>
        </v-col>

        <v-col cols="6" sm="4" md="2">
          <v-card class="pa-3 border text-center" rounded="lg" color="surface">
            <div class="text-caption text-medium-emphasis">Std Deviation (σ)</div>
            <div class="text-h6 font-weight-bold text-secondary">
              {{ simulationStore.currentResult.metrics.stdDev }}
            </div>
            <div class="text-caption text-disabled">Normal spread</div>
          </v-card>
        </v-col>
      </v-row>

      <!-- DAG Resolution Hierarchy Banner -->
      <v-sheet rounded="lg" color="surface-variant" class="pa-3 mb-4 border d-flex align-center flex-wrap gap-2">
        <span class="text-caption font-weight-bold mr-2">
          <v-icon size="small" color="primary" class="mr-1">mdi-graph</v-icon>
          Topological Execution Chain:
        </span>
        <template v-for="(node, idx) in simulationStore.currentResult.resolvedDependencyOrder" :key="node">
          <v-chip size="small" :color="idx === simulationStore.currentResult.resolvedDependencyOrder.length - 1 ? 'primary' : 'secondary'" variant="flat">
            <v-icon start size="12">
              {{ idx === simulationStore.currentResult.resolvedDependencyOrder.length - 1 ? 'mdi-flag-checkered' : 'mdi-link-variant' }}
            </v-icon>
            {{ node }}
          </v-chip>
          <v-icon v-if="idx < simulationStore.currentResult.resolvedDependencyOrder.length - 1" size="small" color="disabled">
            mdi-arrow-right
          </v-icon>
        </template>
      </v-sheet>

      <!-- Interactive Chart View -->
      <v-card class="border mb-4 elevation-0" rounded="lg">
        <v-toolbar color="surface" density="comfortable" class="border-b px-4">
          <v-icon color="primary" class="mr-2">mdi-chart-timeline-variant-shimmer</v-icon>
          <v-toolbar-title class="text-subtitle-2 font-weight-bold">
            Interactive High-Resolution Telemetry Visualizer (1-Hour Timeseries, 1s Interval)
          </v-toolbar-title>
          <v-spacer></v-spacer>
          <v-btn
            size="small"
            variant="tonal"
            prepend-icon="mdi-download"
            @click="exportCsv"
          >
            Export CSV
          </v-btn>
        </v-toolbar>

        <v-card-text class="pa-4">
          <!-- ECharts container -->
          <div ref="chartContainerRef" class="chart-box"></div>
          <div class="text-caption text-center text-medium-emphasis mt-2">
            💡 Drag or brush the <strong>DataZoom slider</strong> at the bottom to zoom into any second in the 1-hour interval.
          </div>
        </v-card-text>
      </v-card>

      <!-- Paginated Raw Data Table -->
      <v-card class="border elevation-0" rounded="lg">
        <v-toolbar color="surface" density="compact" class="border-b px-4">
          <v-toolbar-title class="text-caption font-weight-bold text-uppercase">
            Simulation Sample Data Points Inspector (First 500 displayed)
          </v-toolbar-title>
        </v-toolbar>
        <v-table density="compact" hover>
          <thead>
            <tr class="bg-surface-variant text-caption">
              <th>Index</th>
              <th>Timestamp</th>
              <th>Primary Sensor ({{ selectedSensorId }})</th>
              <th v-for="subId in subFormulaIds" :key="subId">
                Sub-Formula ({{ subId }})
              </th>
              <th class="text-primary font-weight-bold">Computed Output</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in previewRows" :key="row.index">
              <td class="font-monospace text-caption">{{ row.index }}</td>
              <td class="font-monospace text-caption">{{ row.timestamp }}</td>
              <td class="font-monospace text-caption">{{ row.primarySensorValue }}</td>
              <td v-for="subId in subFormulaIds" :key="subId" class="font-monospace text-caption">
                {{ row.subFormulaValues[subId] ?? '-' }}
              </td>
              <td class="font-monospace text-caption font-weight-bold text-primary">
                {{ row.computedValue }}
              </td>
            </tr>
          </tbody>
        </v-table>
      </v-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import { useFormulaStore } from '../stores/formulaStore'
import { useSimulationStore } from '../stores/simulationStore'
import { getAllSensors } from '../services/sensorDataGenerator'

const props = withDefaults(
  defineProps<{
    initialFormulaId?: string
    initialSensorId?: string
    isActive?: boolean
  }>(),
  {
    isActive: true,
  }
)

const formulaStore = useFormulaStore()
const simulationStore = useSimulationStore()
const allSensors = getAllSensors()

const selectedFormulaId = ref<string>(props.initialFormulaId || 'POWER_EFFICIENCY_INDEX')
const selectedSensorId = ref<string>(props.initialSensorId || 'sensor_000')
const selectedDuration = ref<number>(3600) // 1 hour (3600s)
const selectedSampleStep = ref<number>(1) // 1 second
const useBackend = ref<boolean>(false)

const chartContainerRef = ref<HTMLDivElement | null>(null)
let chartInstance: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const durationOptions = [
  { title: '1 Hour (3,600s)', value: 3600 },
  { title: '30 Minutes (1,800s)', value: 1800 },
  { title: '15 Minutes (900s)', value: 900 },
  { title: '5 Minutes (300s)', value: 300 },
]

const sampleStepOptions = [
  { title: '1s (Full 3,600 pts/hr)', value: 1 },
  { title: '5s (720 pts/hr)', value: 5 },
  { title: '10s (360 pts/hr)', value: 10 },
  { title: '30s (120 pts/hr)', value: 30 },
  { title: '60s (60 pts/hr)', value: 60 },
]

const formulaOptions = computed(() => {
  return formulaStore.formulas.map((f) => ({
    title: `${f.name} [${f.id}]`,
    value: f.id,
  }))
})

const sensorOptions = computed(() => {
  return allSensors.map((s) => ({
    title: `${s.id} - ${s.name} (${s.unit}) ~ N(${s.mean}, ${s.stdDev}²)`,
    value: s.id,
  }))
})

const currentFormula = computed(() => {
  return formulaStore.getFormulaById(selectedFormulaId.value)
})

const subFormulaIds = computed(() => {
  if (!simulationStore.currentResult || simulationStore.currentResult.points.length === 0) return []
  const first = simulationStore.currentResult.points[0]
  return Object.keys(first.subFormulaValues).filter((k) => k !== simulationStore.currentResult?.formulaId)
})

const previewRows = computed(() => {
  if (!simulationStore.currentResult) return []
  return simulationStore.currentResult.points.slice(0, 100)
})

watch(
  () => props.initialFormulaId,
  (newId) => {
    if (newId) {
      selectedFormulaId.value = newId
      run()
    }
  }
)

watch(
  () => props.initialSensorId,
  (newSensor) => {
    if (newSensor) {
      selectedSensorId.value = newSensor
      run()
    }
  }
)

watch(
  selectedFormulaId,
  (newId) => {
    const f = formulaStore.getFormulaById(newId)
    if (f && f.targetSensor) {
      selectedSensorId.value = f.targetSensor
    }
  },
  { immediate: true }
)

// Watch isActive prop from parent tab switcher: resize chart when becoming visible
watch(
  () => props.isActive,
  async (active) => {
    if (active) {
      await nextTick()
      handleResize()
    }
  }
)

async function run() {
  await simulationStore.runSimulation({
    formulaId: selectedFormulaId.value,
    targetSensorId: selectedSensorId.value,
    durationSeconds: selectedDuration.value,
    sampleStep: selectedSampleStep.value,
    useBackend: useBackend.value,
  })

  await nextTick()
  renderChart()
}

function renderChart() {
  if (!chartContainerRef.value || !simulationStore.currentResult) return

  // If container has 0 width (e.g. hidden tab), don't initialize with broken geometry
  if (chartContainerRef.value.clientWidth === 0) {
    return
  }

  if (!chartInstance) {
    chartInstance = echarts.init(chartContainerRef.value, 'dark')
  }

  const result = simulationStore.currentResult
  const timestamps = result.points.map((p) => p.timestamp)
  const computedValues = result.points.map((p) => p.computedValue)
  const primarySensorValues = result.points.map((p) => p.primarySensorValue)

  const series: echarts.SeriesOption[] = [
    {
      name: `Formula: ${result.formulaId}`,
      type: 'line',
      data: computedValues,
      showSymbol: false,
      smooth: true,
      lineStyle: {
        width: 2.5,
        color: '#06b6d4',
      },
      itemStyle: {
        color: '#06b6d4',
      },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(6, 182, 212, 0.25)' },
          { offset: 1, color: 'rgba(6, 182, 212, 0.00)' },
        ]),
      },
      markLine: {
        silent: true,
        data: [
          { type: 'average', name: 'Avg', lineStyle: { color: '#10b981', type: 'dashed' } },
        ],
      },
    },
    {
      name: `Raw Sensor (${selectedSensorId.value})`,
      type: 'line',
      data: primarySensorValues,
      showSymbol: false,
      smooth: false,
      lineStyle: {
        width: 1.2,
        color: '#94a3b8',
        type: 'dotted',
      },
      itemStyle: {
        color: '#94a3b8',
      },
    },
  ]

  // Add sub-formula series if available
  const subIds = subFormulaIds.value
  const colors = ['#f59e0b', '#ec4899', '#8b5cf6', '#10b981']
  subIds.forEach((subId, idx) => {
    series.push({
      name: `Sub-Formula: ${subId}`,
      type: 'line',
      data: result.points.map((p) => p.subFormulaValues[subId] ?? null),
      showSymbol: false,
      smooth: true,
      lineStyle: {
        width: 1.5,
        color: colors[idx % colors.length],
      },
    })
  })

  const option: echarts.EChartsOption = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross',
        label: {
          backgroundColor: '#334155',
        },
      },
    },
    legend: {
      top: 0,
      textStyle: {
        color: '#cbd5e1',
      },
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '15%',
      top: '12%',
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: timestamps,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#475569' } },
      axisLabel: { color: '#94a3b8' },
    },
    yAxis: {
      type: 'value',
      scale: true,
      splitLine: { lineStyle: { color: '#1e293b' } },
      axisLabel: { color: '#94a3b8' },
    },
    dataZoom: [
      {
        type: 'slider',
        show: true,
        start: 0,
        end: 100,
        bottom: 5,
        height: 24,
        borderColor: '#334155',
        fillerColor: 'rgba(6, 182, 212, 0.2)',
        handleStyle: { color: '#06b6d4' },
        textStyle: { color: '#94a3b8' },
      },
      {
        type: 'inside',
        start: 0,
        end: 100,
      },
    ],
    series,
  }

  chartInstance.setOption(option, true)
  // Ensure precise initial resize fit
  chartInstance.resize()
}

function handleResize() {
  if (!chartContainerRef.value) return

  if (chartContainerRef.value.clientWidth > 0) {
    if (!chartInstance && simulationStore.currentResult) {
      renderChart()
    } else if (chartInstance) {
      chartInstance.resize()
    }
  }
}

function exportCsv() {
  if (!simulationStore.currentResult) return

  const result = simulationStore.currentResult
  const subIds = subFormulaIds.value

  const headers = ['index', 'timestamp', `sensor_${selectedSensorId.value}`, ...subIds, 'computed_result']
  const rows = result.points.map((p) => {
    return [
      p.index,
      p.timestamp,
      p.primarySensorValue,
      ...subIds.map((id) => p.subFormulaValues[id] ?? ''),
      p.computedValue,
    ].join(',')
  })

  const csvContent = 'data:text/csv;charset=utf-8,' + [headers.join(','), ...rows].join('\n')
  const encodedUri = encodeURI(csvContent)
  const link = document.createElement('a')
  link.setAttribute('href', encodedUri)
  link.setAttribute('download', `simulation_${result.formulaId}_${result.metrics.totalPoints}pts.csv`)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

onMounted(() => {
  window.addEventListener('resize', handleResize)

  // Use ResizeObserver to detect when tab becomes visible or container resizes
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        if (entry.contentRect.width > 0) {
          handleResize()
        }
      }
    })
    if (chartContainerRef.value) {
      resizeObserver.observe(chartContainerRef.value)
    }
  }

  run()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  chartInstance?.dispose()
})
</script>

<style scoped>
.font-monospace {
  font-family: 'JetBrains Mono', 'Fira Code', Menlo, Monaco, Consolas, monospace !important;
}

.chart-box {
  width: 100%;
  height: 440px;
}

.gap-2 {
  gap: 8px;
}

.gap-3 {
  gap: 12px;
}
</style>
