<template>
  <div class="sensor-explorer-container">
    <!-- Header Banner -->
    <v-card class="mb-4 border elevation-0" rounded="lg">
      <v-card-text class="pa-4">
        <v-row align="center" dense>
          <v-col cols="12" md="4">
            <v-text-field
              v-model="searchQuery"
              placeholder="Search 100 sample sensors by ID or name..."
              prepend-inner-icon="mdi-magnify"
              density="compact"
              variant="outlined"
              hide-details
              clearable
            ></v-text-field>
          </v-col>

          <v-col cols="12" md="5">
            <v-chip-group v-model="selectedCategory" selected-class="text-primary font-weight-bold" mandatory>
              <v-chip value="ALL" size="small" variant="tonal">All ({{ sensorStore.sensors.length }})</v-chip>
              <v-chip v-for="cat in sensorStore.categories" :key="cat" :value="cat" size="small" variant="tonal">
                {{ cat }}
              </v-chip>
            </v-chip-group>
          </v-col>

          <v-col cols="12" md="3" class="text-right d-flex align-center justify-end gap-2">
            <v-chip
              size="x-small"
              :color="sensorStore.isBackendConnected ? 'success' : 'warning'"
              variant="flat"
            >
              <v-icon start size="12">
                {{ sensorStore.isBackendConnected ? 'mdi-server-network' : 'mdi-cloud-off-outline' }}
              </v-icon>
              {{ sensorStore.isBackendConnected ? 'Backend formula-api' : 'Offline Fallback' }}
            </v-chip>
            <span class="text-caption text-medium-emphasis">
              Sampling: <strong>1 pt / sec</strong>
            </span>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <!-- Main View: Sensors List & Preview -->
    <v-row dense>
      <v-col cols="12" md="7">
        <v-card class="border elevation-0" rounded="lg">
          <v-toolbar color="surface" density="compact" class="border-b px-4">
            <v-toolbar-title class="text-caption font-weight-bold text-uppercase">
              IoT Sensor Catalog (Source: {{ sensorStore.isBackendConnected ? 'formula-api Mock DataSource' : 'Fallback Generator' }})
            </v-toolbar-title>
            <v-spacer></v-spacer>
            <v-btn
              size="x-small"
              variant="text"
              icon
              :loading="sensorStore.isLoading"
              @click="sensorStore.loadSensors(true)"
            >
              <v-icon size="small">mdi-refresh</v-icon>
            </v-btn>
          </v-toolbar>
          <v-table density="compact" hover>
            <thead>
              <tr class="bg-surface-variant text-caption">
                <th>Sensor ID</th>
                <th>Name & Unit</th>
                <th>Category</th>
                <th>Mean (μ)</th>
                <th>Std Dev (σ)</th>
                <th class="text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="s in filteredSensors"
                :key="s.id"
                :class="{ 'bg-surface-variant': previewSensor?.id === s.id }"
                style="cursor: pointer"
                @click="selectPreviewSensor(s)"
              >
                <td class="font-monospace text-caption font-weight-bold text-primary">
                  {{ s.id }}
                </td>
                <td>
                  <div class="text-body-2 font-weight-medium">{{ s.name }}</div>
                  <div class="text-caption text-medium-emphasis">Unit: {{ s.unit }}</div>
                </td>
                <td>
                  <v-chip size="x-small" :color="getCategoryColor(s.category)" variant="tonal">
                    {{ s.category }}
                  </v-chip>
                </td>
                <td class="font-monospace text-caption">{{ s.mean }}</td>
                <td class="font-monospace text-caption text-secondary">±{{ s.stdDev }}</td>
                <td class="text-right">
                  <v-btn
                    size="x-small"
                    variant="tonal"
                    color="primary"
                    prepend-icon="mdi-chart-bell-curve-cumulative"
                    @click.stop="$emit('select-sensor-for-sim', s.id)"
                  >
                    Simulate
                  </v-btn>
                </td>
              </tr>
            </tbody>
          </v-table>
        </v-card>
      </v-col>

      <!-- Right: Detailed Sensor Telemetry Preview -->
      <v-col cols="12" md="5">
        <v-card v-if="previewSensor" class="border elevation-0" rounded="lg">
          <v-toolbar color="surface" density="compact" class="border-b px-4">
            <v-icon color="secondary" size="small" class="mr-2">mdi-chart-line</v-icon>
            <v-toolbar-title class="text-subtitle-2 font-weight-bold">
              {{ previewSensor.name }} ({{ previewSensor.id }})
            </v-toolbar-title>
            <v-spacer></v-spacer>
            <v-chip size="x-small" color="primary" variant="flat">
              {{ previewSensor.unit }}
            </v-chip>
          </v-toolbar>

          <v-card-text class="pa-4">
            <div class="d-flex justify-space-between mb-3 text-caption">
              <div>
                <strong>Category:</strong> {{ previewSensor.category }}
              </div>
              <div>
                <strong>Distribution:</strong> N({{ previewSensor.mean }}, {{ previewSensor.stdDev }}²)
              </div>
            </div>

            <!-- ECharts preview container -->
            <div ref="previewChartRef" class="preview-chart-box"></div>

            <div class="text-caption text-medium-emphasis mt-3">
              {{ previewSensor.description }}
            </div>

            <v-btn
              block
              color="primary"
              variant="flat"
              class="mt-4"
              prepend-icon="mdi-play"
              @click="$emit('select-sensor-for-sim', previewSensor.id)"
            >
              Use in Formula Simulation
            </v-btn>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import type { SensorMetadata } from '../types/sensor'
import { useSensorStore } from '../stores/sensorStore'

const props = withDefaults(
  defineProps<{
    isActive?: boolean
  }>(),
  {
    isActive: true,
  }
)

defineEmits<{
  (e: 'select-sensor-for-sim', sensorId: string): void
}>()

const sensorStore = useSensorStore()
const searchQuery = ref('')
const selectedCategory = ref('ALL')
const previewSensor = ref<SensorMetadata | null>(null)
const previewChartRef = ref<HTMLDivElement | null>(null)
let previewChartInstance: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const filteredSensors = computed(() => {
  return sensorStore.sensors.filter((s) => {
    if (selectedCategory.value !== 'ALL' && s.category !== selectedCategory.value) {
      return false
    }
    if (searchQuery.value) {
      const q = searchQuery.value.toLowerCase()
      return s.id.toLowerCase().includes(q) || s.name.toLowerCase().includes(q)
    }
    return true
  })
})

function getCategoryColor(cat: string): string {
  switch (cat) {
    case 'Temperature':
      return 'warning'
    case 'Pressure':
      return 'info'
    case 'Vibration':
      return 'error'
    case 'Electrical':
      return 'primary'
    case 'Flow':
      return 'accent'
    default:
      return 'secondary'
  }
}

// Watch isActive prop
watch(
  () => props.isActive,
  async (active) => {
    if (active) {
      await nextTick()
      handleResize()
    }
  }
)

async function selectPreviewSensor(s: SensorMetadata) {
  previewSensor.value = s
  await nextTick()
  renderPreviewChart()
}

async function renderPreviewChart() {
  if (!previewChartRef.value || !previewSensor.value) return

  if (previewChartRef.value.clientWidth === 0) {
    return
  }

  if (!previewChartInstance) {
    previewChartInstance = echarts.init(previewChartRef.value, 'dark')
  }

  // Load 1-hour time series from backend sensorStore (downsampled to 360 points for quick preview)
  const fullPoints = await sensorStore.fetchTimeSeries(previewSensor.value.id, 3600)
  const sampleStep = 10
  const sampled = fullPoints.filter((_, idx) => idx % sampleStep === 0)

  const timestamps = sampled.map((p) => p.timestamp)
  const values = sampled.map((p) => p.value)

  const option: echarts.EChartsOption = {
    backgroundColor: 'transparent',
    grid: {
      left: '5%',
      right: '4%',
      top: '10%',
      bottom: '15%',
      containLabel: true,
    },
    tooltip: {
      trigger: 'axis',
    },
    xAxis: {
      type: 'category',
      data: timestamps,
      axisLabel: { color: '#94a3b8', fontSize: 10 },
      axisLine: { lineStyle: { color: '#475569' } },
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLabel: { color: '#94a3b8', fontSize: 10 },
      splitLine: { lineStyle: { color: '#1e293b' } },
    },
    series: [
      {
        name: previewSensor.value.id,
        type: 'line',
        data: values,
        showSymbol: false,
        smooth: true,
        lineStyle: { color: '#38bdf8', width: 1.8 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(56, 189, 248, 0.3)' },
            { offset: 1, color: 'rgba(56, 189, 248, 0.0)' },
          ]),
        },
      },
    ],
  }

  previewChartInstance.setOption(option, true)
  previewChartInstance.resize()
}

function handleResize() {
  if (!previewChartRef.value) return

  if (previewChartRef.value.clientWidth > 0) {
    if (!previewChartInstance && previewSensor.value) {
      renderPreviewChart()
    } else if (previewChartInstance) {
      previewChartInstance.resize()
    }
  }
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)

  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        if (entry.contentRect.width > 0) {
          handleResize()
        }
      }
    })
    if (previewChartRef.value) {
      resizeObserver.observe(previewChartRef.value)
    }
  }

  await sensorStore.loadSensors()
  if (sensorStore.sensors.length > 0) {
    previewSensor.value = sensorStore.sensors[0]
    await nextTick()
    renderPreviewChart()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  previewChartInstance?.dispose()
})
</script>

<style scoped>
.font-monospace {
  font-family: 'JetBrains Mono', 'Fira Code', Menlo, Monaco, Consolas, monospace !important;
}

.preview-chart-box {
  width: 100%;
  height: 280px;
}
</style>
