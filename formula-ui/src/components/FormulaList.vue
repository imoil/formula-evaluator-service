<template>
  <div class="formula-list-container">
    <!-- Action Bar & Statistics -->
    <v-card class="mb-4 border elevation-0" rounded="lg">
      <v-card-text class="pa-4">
        <v-row align="center" dense>
          <v-col cols="12" md="4">
            <v-text-field
              v-model="searchQuery"
              placeholder="Search by name, ID, or expression..."
              prepend-inner-icon="mdi-magnify"
              density="compact"
              variant="outlined"
              hide-details
              clearable
            ></v-text-field>
          </v-col>

          <v-col cols="12" md="4">
            <v-chip-group v-model="selectedCategory" selected-class="text-primary font-weight-bold" mandatory>
              <v-chip value="ALL" size="small" variant="tonal">All ({{ formulaStore.formulas.length }})</v-chip>
              <v-chip
                v-for="cat in formulaStore.formulaCategories"
                :key="cat"
                :value="cat"
                size="small"
                variant="tonal"
              >
                {{ cat }}
              </v-chip>
            </v-chip-group>
          </v-col>

          <v-col cols="12" md="4" class="d-flex justify-end gap-2">
            <v-btn
              color="primary"
              variant="flat"
              prepend-icon="mdi-plus"
              @click="$emit('create')"
            >
              New Formula
            </v-btn>

            <v-menu>
              <template #activator="{ props }">
                <v-btn icon="mdi-dots-vertical" variant="outlined" v-bind="props" density="comfortable"></v-btn>
              </template>
              <v-list density="compact">
                <v-list-item prepend-icon="mdi-download" title="Export Formulas (JSON)" @click="exportData"></v-list-item>
                <v-list-item prepend-icon="mdi-upload" title="Import Formulas (JSON)" @click="triggerFileInput"></v-list-item>
                <v-divider></v-divider>
                <v-list-item prepend-icon="mdi-refresh" title="Reset Default Presets" @click="confirmReset"></v-list-item>
              </v-list>
            </v-menu>
            <input ref="fileInputRef" type="file" accept=".json" style="display: none" @change="handleFileImport" />
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <!-- Formula Table -->
    <v-card class="border elevation-0" rounded="lg">
      <v-table hover density="comfortable">
        <thead>
          <tr class="bg-surface-variant text-caption text-uppercase font-weight-bold">
            <th style="min-width: 180px">Formula Identifier & Name</th>
            <th style="min-width: 110px">Category</th>
            <th style="min-width: 280px">Expression</th>
            <th style="min-width: 180px">Dependency Composition</th>
            <th style="min-width: 140px">Updated At</th>
            <th class="text-right" style="min-width: 160px">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in filteredFormulas" :key="item.id">
            <!-- Identifier & Name -->
            <td>
              <div class="d-flex align-center">
                <v-avatar color="primary" variant="tonal" size="32" class="mr-3 font-weight-bold text-caption">
                  {{ item.id.substring(0, 2) }}
                </v-avatar>
                <div>
                  <div class="font-weight-bold text-body-2 d-flex align-center">
                    {{ item.name }}
                  </div>
                  <div class="text-caption font-monospace text-medium-emphasis">
                    {{ item.id }}
                  </div>
                </div>
              </div>
            </td>

            <!-- Category -->
            <td>
              <v-chip
                size="x-small"
                :color="getCategoryColor(item.category)"
                variant="tonal"
                class="font-weight-medium"
              >
                {{ item.category }}
              </v-chip>
            </td>

            <!-- Expression -->
            <td>
              <div class="expression-badge d-flex align-center">
                <code class="font-monospace text-caption mr-2">{{ item.expression }}</code>
                <v-btn
                  icon="mdi-content-copy"
                  size="x-small"
                  variant="text"
                  @click="copyExpression(item.expression)"
                ></v-btn>
              </div>
              <div v-if="item.description" class="text-caption text-disabled mt-1 text-truncate" style="max-width: 320px">
                {{ item.description }}
              </div>
            </td>

            <!-- Dependency Composition -->
            <td>
              <div class="d-flex flex-column gap-1">
                <!-- Referenced Formulas -->
                <div v-if="getDeps(item.id).referencedFormulas.length > 0" class="d-flex align-center flex-wrap gap-1">
                  <span class="text-caption text-medium-emphasis" style="font-size: 10px !important">Calls:</span>
                  <v-chip
                    v-for="subId in getDeps(item.id).referencedFormulas"
                    :key="subId"
                    size="x-small"
                    color="primary"
                    variant="flat"
                  >
                    <v-icon start size="10">mdi-link-variant</v-icon>
                    {{ subId }}
                  </v-chip>
                </div>

                <!-- Referenced Sensors -->
                <div v-if="getDeps(item.id).referencedSensors.length > 0" class="d-flex align-center flex-wrap gap-1">
                  <span class="text-caption text-medium-emphasis" style="font-size: 10px !important">Sensors:</span>
                  <v-chip
                    v-for="sId in getDeps(item.id).referencedSensors"
                    :key="sId"
                    size="x-small"
                    color="secondary"
                    variant="flat"
                  >
                    {{ sId }}
                  </v-chip>
                </div>

                <!-- Used By indicator -->
                <div v-if="getDeps(item.id).dependentFormulas.length > 0" class="d-flex align-center flex-wrap gap-1">
                  <span class="text-caption text-accent" style="font-size: 10px !important">Used by:</span>
                  <v-chip
                    v-for="parent in getDeps(item.id).dependentFormulas"
                    :key="parent"
                    size="x-small"
                    color="accent"
                    variant="outlined"
                  >
                    {{ parent }}
                  </v-chip>
                </div>

                <span
                  v-if="
                    getDeps(item.id).referencedFormulas.length === 0 &&
                    getDeps(item.id).referencedSensors.length === 0
                  "
                  class="text-caption text-disabled"
                  style="font-size: 11px"
                >
                  Direct / Standalone
                </span>
              </div>
            </td>

            <!-- Updated At -->
            <td class="text-caption text-medium-emphasis">
              {{ item.updatedAt }}
            </td>

            <!-- Actions -->
            <td class="text-right">
              <v-btn
                color="primary"
                variant="tonal"
                size="small"
                prepend-icon="mdi-chart-bell-curve-cumulative"
                class="mr-2"
                @click="$emit('simulate', item)"
              >
                Simulate
              </v-btn>

              <v-btn icon="mdi-pencil" size="x-small" variant="text" class="mr-1" @click="$emit('edit', item)"></v-btn>
              <v-btn icon="mdi-content-copy" size="x-small" variant="text" class="mr-1" @click="duplicate(item.id)"></v-btn>
              <v-btn icon="mdi-delete" size="x-small" color="error" variant="text" @click="remove(item.id)"></v-btn>
            </td>
          </tr>

          <tr v-if="filteredFormulas.length === 0">
            <td colspan="6" class="text-center pa-8 text-medium-emphasis">
              <v-icon size="48" color="disabled" class="mb-2">mdi-function</v-icon>
              <div>No formulas found matching your filter criteria.</div>
              <v-btn color="primary" variant="tonal" size="small" class="mt-3" @click="$emit('create')">
                Create First Formula
              </v-btn>
            </td>
          </tr>
        </tbody>
      </v-table>
    </v-card>

    <!-- Snackbar for feedback -->
    <v-snackbar v-model="snackbar.show" :color="snackbar.color" timeout="3000">
      {{ snackbar.text }}
    </v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { FormulaDefinition } from '../types/formula'
import { useFormulaStore } from '../stores/formulaStore'

const emit = defineEmits<{
  (e: 'create'): void
  (e: 'edit', formula: FormulaDefinition): void
  (e: 'simulate', formula: FormulaDefinition): void
}>()

const formulaStore = useFormulaStore()

const searchQuery = ref('')
const selectedCategory = ref('ALL')
const fileInputRef = ref<HTMLInputElement>()

const snackbar = ref({
  show: false,
  text: '',
  color: 'success',
})

function notify(text: string, color = 'success') {
  snackbar.value = { show: true, text, color }
}

const filteredFormulas = computed(() => {
  return formulaStore.formulas.filter((f) => {
    // Category filter
    if (selectedCategory.value !== 'ALL' && f.category !== selectedCategory.value) {
      return false
    }
    // Search query filter
    if (searchQuery.value) {
      const q = searchQuery.value.toLowerCase()
      const matchId = f.id.toLowerCase().includes(q)
      const matchName = f.name.toLowerCase().includes(q)
      const matchExpr = f.expression.toLowerCase().includes(q)
      return matchId || matchName || matchExpr
    }
    return true
  })
})

function getDeps(id: string) {
  return formulaStore.getDependencies(id)
}

function getCategoryColor(cat: string): string {
  switch (cat) {
    case 'Thermal':
      return 'warning'
    case 'Pressure':
      return 'info'
    case 'Vibration':
      return 'error'
    case 'Composite':
      return 'primary'
    case 'KPI':
      return 'success'
    default:
      return 'secondary'
  }
}

function copyExpression(expr: string) {
  navigator.clipboard.writeText(expr)
  notify('Expression copied to clipboard!')
}

function duplicate(id: string) {
  try {
    const dup = formulaStore.duplicateFormula(id)
    notify(`Created duplicate formula: ${dup.id}`)
  } catch (err: any) {
    notify(err.message, 'error')
  }
}

function remove(id: string) {
  if (confirm(`Are you sure you want to delete formula "${id}"?`)) {
    try {
      formulaStore.deleteFormula(id)
      notify(`Formula ${id} removed.`)
    } catch (err: any) {
      notify(err.message, 'error')
    }
  }
}

function exportData() {
  const json = formulaStore.exportJson()
  const blob = new Blob([json], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `formulas_backup_${new Date().toISOString().substring(0, 10)}.json`
  a.click()
  URL.revokeObjectURL(url)
  notify('Exported formulas as JSON!')
}

function triggerFileInput() {
  fileInputRef.value?.click()
}

function handleFileImport(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  const reader = new FileReader()
  reader.onload = (evt) => {
    try {
      const content = evt.target?.result as string
      const { importedCount } = formulaStore.importJson(content)
      notify(`Imported ${importedCount} formula(s) successfully!`)
    } catch (err: any) {
      notify(`Import failed: ${err.message}`, 'error')
    }
  }
  reader.readAsText(file)
  input.value = ''
}

function confirmReset() {
  if (confirm('Reset to default preset formulas? Any custom modifications will be replaced.')) {
    formulaStore.resetToDefaults()
    notify('Reset to default preset formulas.')
  }
}
</script>

<style scoped>
.font-monospace {
  font-family: 'JetBrains Mono', 'Fira Code', Menlo, Monaco, Consolas, monospace !important;
}

.expression-badge code {
  background-color: rgba(var(--v-theme-surface-variant), 0.7);
  padding: 4px 8px;
  border-radius: 4px;
  border: 1px solid rgba(var(--v-theme-border), 0.1);
  word-break: break-all;
}

.gap-1 {
  gap: 4px;
}

.gap-2 {
  gap: 8px;
}
</style>
