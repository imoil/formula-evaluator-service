<template>
  <div class="sensor-management">
    <!-- Top KPI Cards (Click to filter by sensor type) -->
    <v-row class="mb-4">
      <v-col cols="12" sm="6" md="3">
        <v-card
          class="border elevation-0 cursor-pointer"
          :class="{ 'border-primary': store.typeFilter === 'ALL' }"
          rounded="lg"
          @click="store.typeFilter = 'ALL'"
        >
          <v-card-text class="d-flex align-center">
            <v-avatar color="primary" variant="tonal" rounded="lg" size="48" class="mr-3">
              <v-icon color="primary">mdi-numeric</v-icon>
            </v-avatar>
            <div>
              <div class="text-caption text-medium-emphasis font-weight-medium">
                Global Serial Sensors (All)
              </div>
              <div class="text-h5 font-weight-bold text-primary">
                {{ store.totalCount }}
              </div>
              <div class="text-caption text-medium-emphasis">
                Next Seq: #{{ store.nextSerialPreview }}
              </div>
            </div>
          </v-card-text>
        </v-card>
      </v-col>

      <v-col cols="12" sm="6" md="3">
        <v-card
          class="border elevation-0 cursor-pointer"
          :class="{ 'border-primary': store.typeFilter === 'PHYSICAL' }"
          rounded="lg"
          @click="store.typeFilter = 'PHYSICAL'"
        >
          <v-card-text class="d-flex align-center">
            <v-avatar color="indigo" variant="tonal" rounded="lg" size="48" class="mr-3">
              <v-icon color="indigo">mdi-devices</v-icon>
            </v-avatar>
            <div>
              <div class="text-caption text-medium-emphasis font-weight-medium">
                IoT Physical Sensors
              </div>
              <div class="text-h5 font-weight-bold text-indigo">
                {{ store.physicalCount }}
              </div>
              <div class="text-caption text-medium-emphasis">
                Click to filter physical sensors
              </div>
            </div>
          </v-card-text>
        </v-card>
      </v-col>

      <v-col cols="12" sm="6" md="3">
        <v-card
          class="border elevation-0 cursor-pointer"
          :class="{ 'border-primary': store.typeFilter === 'FORMULA' }"
          rounded="lg"
          @click="store.typeFilter = 'FORMULA'"
        >
          <v-card-text class="d-flex align-center">
            <v-avatar color="teal" variant="tonal" rounded="lg" size="48" class="mr-3">
              <v-icon color="teal">mdi-function-variant</v-icon>
            </v-avatar>
            <div>
              <div class="text-caption text-medium-emphasis font-weight-medium">
                Calculated Formula Sensors
              </div>
              <div class="text-h5 font-weight-bold text-teal">
                {{ store.formulaCount }}
              </div>
              <div class="text-caption text-medium-emphasis">
                Click to filter formula sensors
              </div>
            </div>
          </v-card-text>
        </v-card>
      </v-col>

      <v-col cols="12" sm="6" md="3">
        <v-card class="border elevation-0" rounded="lg">
          <v-card-text class="d-flex align-center">
            <v-avatar color="amber-darken-3" variant="tonal" rounded="lg" size="48" class="mr-3">
              <v-icon color="amber-darken-3">mdi-server-network</v-icon>
            </v-avatar>
            <div>
              <div class="text-caption text-medium-emphasis font-weight-medium">
                Oracle 19c Storage & Registry
              </div>
              <div class="text-subtitle-2 font-weight-bold text-amber-darken-4">
                {{ store.isBackendConnected ? 'Connected (Oracle 19c)' : 'Local Standalone Mode' }}
              </div>
              <div class="text-caption text-medium-emphasis">
                {{ store.availableDevices.length }} IoT Devices Registered
              </div>
            </div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <!-- Main Filter & Action Card -->
    <v-card class="mb-4 border elevation-0" rounded="lg">
      <v-card-text class="pa-4">
        <v-row align="center">
          <!-- Search input -->
          <v-col cols="12" sm="6" md="3">
            <v-text-field
              v-model="store.searchQuery"
              prepend-inner-icon="mdi-magnify"
              label="Search by Serial (#), Code, Name, Expression, Device..."
              variant="outlined"
              density="compact"
              hide-details
              clearable
            />
          </v-col>

          <!-- Type filter toggle -->
          <v-col cols="12" sm="3" md="2">
            <v-select
              v-model="store.typeFilter"
              :items="typeFilterOptions"
              label="Sensor Type Filter"
              variant="outlined"
              density="compact"
              hide-details
            />
          </v-col>

          <!-- Device filter -->
          <v-col cols="12" sm="3" md="2">
            <v-select
              v-model="store.deviceFilter"
              :items="['ALL', ...store.availableDevices]"
              label="IoT Device Filter"
              variant="outlined"
              density="compact"
              hide-details
            />
          </v-col>

          <!-- Action Buttons -->
          <v-col cols="12" md="5" class="d-flex flex-wrap align-center justify-start justify-md-end gap-2 action-buttons-container">
            <v-menu>
              <template #activator="{ props }">
                <v-btn
                  color="primary"
                  variant="flat"
                  prepend-icon="mdi-plus"
                  v-bind="props"
                >
                  Add Sensor
                </v-btn>
              </template>
              <v-list density="compact">
                <v-list-item
                  prepend-icon="mdi-function-variant"
                  title="Add Formula Sensor"
                  @click="openCreateModal('FORMULA')"
                />
                <v-list-item
                  prepend-icon="mdi-memory"
                  title="Add IoT Physical Sensor"
                  @click="openCreateModal('PHYSICAL')"
                />
              </v-list>
            </v-menu>

            <v-btn
              color="indigo"
              variant="tonal"
              prepend-icon="mdi-sync"
              @click="openSyncModal"
            >
              Sync Device
            </v-btn>

            <v-tooltip location="top">
              <template #activator="{ props }">
                <v-btn
                  color="teal"
                  variant="flat"
                  prepend-icon="mdi-sync"
                  :loading="store.syncingRules"
                  v-bind="props"
                  @click="handleSyncRules"
                >
                  Sync Rules to Flink
                </v-btn>
              </template>
              <span>Synchronize active formula rules to Flink in-memory BroadcastState</span>
            </v-tooltip>

            <v-btn
              icon="mdi-refresh"
              variant="text"
              size="small"
              :loading="store.isLoading"
              @click="store.fetchSensors"
            />
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <!-- Sensor Registry Table -->
    <v-card class="border elevation-0 sensor-table-card" rounded="lg">
      <v-table hover density="comfortable" class="sensor-registry-table">
        <thead>
          <tr class="bg-surface-variant text-caption font-weight-bold text-uppercase">
            <th class="col-serial">Serial No</th>
            <th class="col-name">Sensor Code & Name</th>
            <th class="col-type">Type</th>
            <th class="col-target">IoT Device / Target</th>
            <th class="col-formula">Formula Expression & Dependencies</th>
            <th class="col-category">Category</th>
            <th class="col-unit">Unit</th>
            <th class="col-status">Status</th>
            <th class="col-actions text-right">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="store.filteredSensors.length === 0">
            <td colspan="9" class="text-center py-8 text-medium-emphasis">
              <v-icon size="40" class="mb-2">mdi-database-search-outline</v-icon>
              <div>No registered sensors match the current filter criteria.</div>
            </td>
          </tr>
          <tr v-for="sensor in store.filteredSensors" :key="sensor.serialNumber">
            <!-- Serial Number -->
            <td class="col-serial">
              <v-chip
                size="small"
                color="primary"
                variant="flat"
                class="font-weight-bold cursor-pointer"
                @click="copySerial(sensor.serialNumber)"
              >
                #{{ sensor.serialNumber }}
                <v-tooltip activator="parent" location="top">
                  Click to copy Serial Number #{{ sensor.serialNumber }} (P_{{ sensor.serialNumber }})
                </v-tooltip>
              </v-chip>
            </td>

            <!-- Code & Name -->
            <td class="col-name">
              <div class="font-weight-medium text-body-2 text-truncate" :title="sensor.sensorName">
                {{ sensor.sensorName }}
              </div>
              <div class="text-caption text-medium-emphasis font-family-monospace">
                {{ sensor.sensorCode }}
              </div>
              <div
                v-if="sensor.description"
                class="text-caption text-disabled text-truncate"
                :title="sensor.description"
                style="max-width: 240px"
              >
                {{ sensor.description }}
              </div>
            </td>

            <!-- Type -->
            <td class="col-type">
              <v-chip
                size="x-small"
                :color="sensor.sensorType === 'PHYSICAL' ? 'indigo' : 'teal'"
                variant="tonal"
                class="font-weight-medium"
              >
                <v-icon start size="12">
                  {{ sensor.sensorType === 'PHYSICAL' ? 'mdi-chip' : 'mdi-function-variant' }}
                </v-icon>
                {{ sensor.sensorType === 'PHYSICAL' ? 'Physical IoT' : 'Formula Sensor' }}
              </v-chip>
            </td>

            <!-- Device ID & Device Sensor Index / Formula Target -->
            <td class="col-target">
              <div v-if="sensor.sensorType === 'PHYSICAL'" class="d-flex align-center flex-wrap gap-1">
                <v-icon size="16" color="indigo" class="mr-1">mdi-router-wireless</v-icon>
                <span class="font-weight-medium text-body-2 mr-1">{{ sensor.deviceId }}</span>
                <v-chip size="x-small" variant="outlined" color="secondary">
                  #{{ sensor.deviceSensorIndex }}
                </v-chip>
              </div>
              <div v-else class="d-flex flex-column gap-1">
                <span class="text-caption text-medium-emphasis">
                  Virtual (#{{ sensor.serialNumber }})
                </span>
                <v-chip
                  size="x-small"
                  variant="outlined"
                  color="teal"
                  style="width: fit-content"
                >
                  Target: #{{ getPrimaryTargetSerial(sensor.formulaExpression) }}
                </v-chip>
              </div>
            </td>

            <!-- Formula Expression & Dependency Composition -->
            <td class="col-formula">
              <div v-if="sensor.sensorType === 'FORMULA'" class="py-1">
                <div class="formula-code-preview d-flex align-center">
                  <code>{{ sensor.formulaExpression }}</code>
                  <v-btn
                    icon="mdi-content-copy"
                    size="x-small"
                    variant="text"
                    class="ml-1 flex-shrink-0"
                    @click="copyExpression(sensor.formulaExpression || '')"
                  />
                </div>
                <div class="d-flex flex-wrap gap-1 mt-1">
                  <template v-if="getSensorDeps(sensor).referencedFormulas.length > 0">
                    <v-chip
                      v-for="subId in getSensorDeps(sensor).referencedFormulas"
                      :key="subId"
                      size="x-small"
                      color="primary"
                      variant="flat"
                    >
                      <v-icon start size="10">mdi-link-variant</v-icon>
                      Calls: {{ subId }}
                    </v-chip>
                  </template>
                  <template v-if="getSensorDeps(sensor).referencedSensors.length > 0">
                    <v-chip
                      v-for="sId in getSensorDeps(sensor).referencedSensors"
                      :key="sId"
                      size="x-small"
                      color="secondary"
                      variant="tonal"
                    >
                      <v-icon start size="10">mdi-access-point</v-icon>
                      #{{ sId }}
                    </v-chip>
                  </template>
                  <template v-if="getSensorDeps(sensor).dependentFormulas.length > 0">
                    <v-chip
                      v-for="parent in getSensorDeps(sensor).dependentFormulas"
                      :key="parent"
                      size="x-small"
                      color="teal"
                      variant="outlined"
                    >
                      Used by: {{ parent }}
                    </v-chip>
                  </template>
                </div>
              </div>
              <div v-else class="text-caption text-disabled">
                — (Raw Physical Signal)
              </div>
            </td>

            <!-- Category -->
            <td class="col-category">
              <v-chip size="x-small" variant="text" class="text-capitalize px-1">
                {{ sensor.category }}
              </v-chip>
            </td>

            <!-- Unit -->
            <td class="col-unit">
              <span class="text-caption font-weight-medium">{{ sensor.unit || '—' }}</span>
            </td>

            <!-- Status -->
            <td class="col-status">
              <v-chip
                size="x-small"
                :color="getStatusColor(sensor.status)"
                variant="tonal"
              >
                {{ sensor.status }}
              </v-chip>
            </td>

            <!-- Actions (Simulate On-Demand, Duplicate, Edit, Delete) -->
            <td class="col-actions text-right">
              <div class="d-inline-flex align-center justify-end action-btn-group">
                <v-tooltip text="Run On-Demand Telemetry Simulation" location="top">
                  <template #activator="{ props }">
                    <v-btn
                      icon="mdi-play-circle-outline"
                      variant="text"
                      size="small"
                      density="compact"
                      color="success"
                      v-bind="props"
                      @click="simulateSensor(sensor)"
                    />
                  </template>
                </v-tooltip>
                <v-tooltip v-if="sensor.sensorType === 'FORMULA'" text="Duplicate Formula Sensor" location="top">
                  <template #activator="{ props }">
                    <v-btn
                      icon="mdi-content-copy"
                      variant="text"
                      size="small"
                      density="compact"
                      color="secondary"
                      v-bind="props"
                      @click="duplicateFormula(sensor)"
                    />
                  </template>
                </v-tooltip>
                <v-tooltip text="Edit Sensor" location="top">
                  <template #activator="{ props }">
                    <v-btn
                      icon="mdi-pencil-outline"
                      variant="text"
                      size="small"
                      density="compact"
                      color="primary"
                      v-bind="props"
                      @click="openEditModal(sensor)"
                    />
                  </template>
                </v-tooltip>
                <v-tooltip text="Delete Sensor" location="top">
                  <template #activator="{ props }">
                    <v-btn
                      icon="mdi-delete-outline"
                      variant="text"
                      size="small"
                      density="compact"
                      color="error"
                      v-bind="props"
                      @click="openDeleteModal(sensor)"
                    />
                  </template>
                </v-tooltip>
              </div>
            </td>
          </tr>
        </tbody>
      </v-table>
    </v-card>

    <!-- Create / Edit Sensor Dialog -->
    <v-dialog v-model="formDialog" max-width="760px" persistent>
      <v-card rounded="lg">
        <v-card-title class="px-6 pt-5 pb-2 font-weight-bold d-flex align-center">
          <v-icon
            :color="formType === 'PHYSICAL' ? 'indigo' : 'teal'"
            class="mr-2"
          >
            {{ isEditing ? 'mdi-pencil' : (formType === 'PHYSICAL' ? 'mdi-chip' : 'mdi-function-variant') }}
          </v-icon>
          {{ isEditing ? (formType === 'FORMULA' ? 'Edit Formula Sensor' : 'Edit Physical Sensor') : (formType === 'PHYSICAL' ? 'Register IoT Physical Sensor' : 'Create Formula Sensor') }}
          <v-spacer />
          <v-chip size="small" color="primary" variant="tonal" v-if="!isEditing">
            Next Serial: #{{ store.nextSerialPreview }}
          </v-chip>
          <v-chip size="small" color="primary" variant="flat" v-else>
            Serial: #{{ editingItem?.serialNumber }}
          </v-chip>
        </v-card-title>

        <v-card-text class="px-6 py-3">
          <!-- Type switch when creating -->
          <div v-if="!isEditing" class="mb-4">
            <v-tabs v-model="formType" color="primary" density="compact" grow>
              <v-tab value="FORMULA">
                <v-icon start>mdi-function-variant</v-icon>
                Formula Sensor
              </v-tab>
              <v-tab value="PHYSICAL">
                <v-icon start>mdi-chip</v-icon>
                IoT Physical Sensor
              </v-tab>
            </v-tabs>
          </div>

          <v-form ref="formRef" @submit.prevent="submitForm">
            <v-row dense>
              <!-- Physical IoT specific fields -->
              <template v-if="formType === 'PHYSICAL'">
                <v-col cols="12" sm="7">
                  <v-combobox
                    v-model="formDevice"
                    :items="defaultDeviceOptions"
                    label="IoT Device ID *"
                    placeholder="e.g. DEVICE-001, TURBINE-01"
                    variant="outlined"
                    density="comfortable"
                    :disabled="isEditing"
                    :rules="[v => !!v || 'IoT Device ID is required']"
                  />
                </v-col>
                <v-col cols="12" sm="5">
                  <v-text-field
                    v-model.number="formDeviceIndex"
                    type="number"
                    min="0"
                    label="Device Sensor Index *"
                    placeholder="0, 1, 2..."
                    variant="outlined"
                    density="comfortable"
                    :disabled="isEditing"
                    :rules="[v => v !== null && v !== undefined && v >= 0 || 'Valid sensor index required']"
                  />
                </v-col>
              </template>

              <!-- Common Name & Code fields -->
              <v-col cols="12" :sm="formType === 'FORMULA' ? 7 : 12">
                <v-text-field
                  v-model="formSensorName"
                  label="Sensor Name *"
                  :placeholder="formType === 'FORMULA' ? 'e.g. Exhaust Temp (°F)' : 'e.g. Main Bearing Temperature'"
                  variant="outlined"
                  density="comfortable"
                  :rules="[v => !!v || 'Sensor name is required']"
                />
              </v-col>

              <v-col v-if="formType === 'FORMULA'" cols="12" sm="5">
                <v-text-field
                  v-model="formSensorCode"
                  label="Formula Code (Optional)"
                  placeholder="e.g. TEMP_FAHRENHEIT"
                  variant="outlined"
                  density="comfortable"
                  :disabled="isEditing"
                  hint="Used for sub-formula calls (auto-generated if blank)"
                  persistent-hint
                />
              </v-col>

              <!-- Formula Sensor specific fields -->
              <template v-if="formType === 'FORMULA'">
                <v-col cols="12" class="mt-2">
                  <!-- Quick Insert Snippets Toolbar -->
                  <v-sheet rounded="lg" color="surface-variant" class="pa-2 mb-2 border">
                    <div class="text-caption text-medium-emphasis mb-1 font-weight-bold">
                      Quick Insert Snippets (Sensors, Sub-Formulas & Window Functions):
                    </div>
                    <div class="d-flex flex-wrap gap-1 align-center mb-1">
                      <v-chip
                        v-for="s in store.sensors.slice(0, 8)"
                        :key="s.serialNumber"
                        size="x-small"
                        variant="outlined"
                        :color="s.sensorType === 'PHYSICAL' ? 'indigo' : 'teal'"
                        class="cursor-pointer"
                        @click="insertTokenToFormula(s.sensorType === 'FORMULA' ? s.sensorCode : `P_${s.serialNumber}`)"
                      >
                        #{{ s.serialNumber }} ({{ s.sensorType === 'FORMULA' ? s.sensorCode : s.sensorName.split(' ').slice(-2).join(' ') }})
                      </v-chip>
                    </div>
                    <div class="d-flex flex-wrap gap-1 align-center">
                      <v-btn size="x-small" variant="outlined" @click="insertTokenToFormula('window_avg(10)')">
                        window_avg(10)
                      </v-btn>
                      <v-btn size="x-small" variant="outlined" @click="insertTokenToFormula('window_max(10)')">
                        window_max(10)
                      </v-btn>
                      <v-btn size="x-small" variant="outlined" @click="insertTokenToFormula('window_min(10)')">
                        window_min(10)
                      </v-btn>
                      <v-btn size="x-small" variant="outlined" @click="insertTokenToFormula('abs(')">
                        abs()
                      </v-btn>
                      <v-btn size="x-small" variant="outlined" @click="insertTokenToFormula('sqrt(')">
                        sqrt()
                      </v-btn>
                      <v-btn size="x-small" variant="outlined" @click="insertTokenToFormula(' ? 1.0 : 0.0')">
                        Ternary (? :)
                      </v-btn>
                    </div>
                  </v-sheet>

                  <v-textarea
                    v-model="formFormulaExpression"
                    label="Formula Expression (Referencing Serial P_# or Formula Code) *"
                    placeholder="e.g. P_10001 * 1.8 + 32.0, window_avg(10), (P_10002 - P_10004) / 10"
                    variant="outlined"
                    density="comfortable"
                    rows="2"
                    class="font-family-monospace"
                    :rules="[v => !!v || 'Formula expression is required']"
                    @update:model-value="validateFormulaInput"
                  />
                </v-col>

                <!-- Live Dependency & Dry-Run Card -->
                <v-col cols="12" class="mb-2">
                  <v-alert
                    v-if="formulaValidationError"
                    type="error"
                    variant="tonal"
                    density="compact"
                    class="mb-2"
                    icon="mdi-alert-circle"
                  >
                    {{ formulaValidationError }}
                  </v-alert>

                  <v-sheet rounded="lg" color="surface-variant" class="pa-3 border">
                    <div class="d-flex align-center justify-space-between mb-1">
                      <span class="text-caption font-weight-bold">
                        <v-icon size="small" color="teal" class="mr-1">mdi-graph-outline</v-icon>
                        Live Dependency Graph Analysis
                      </span>
                      <v-btn size="x-small" variant="tonal" color="teal" prepend-icon="mdi-play" @click="runDryTest">
                        Dry Run Test
                      </v-btn>
                    </div>
                    <div class="d-flex flex-wrap align-center gap-2 text-caption">
                      <span class="text-medium-emphasis">Referenced Sensors:</span>
                      <template v-if="detectedSensorRefs.length > 0">
                        <v-chip
                          v-for="sId in detectedSensorRefs"
                          :key="sId"
                          size="x-small"
                          color="secondary"
                          variant="flat"
                        >
                          #{{ sId }}
                        </v-chip>
                      </template>
                      <span v-else class="text-disabled">Primary Target (value)</span>

                      <span class="text-medium-emphasis ml-2">Sub-Formulas:</span>
                      <template v-if="detectedFormulaRefs.length > 0">
                        <v-chip
                          v-for="fId in detectedFormulaRefs"
                          :key="fId"
                          size="x-small"
                          color="primary"
                          variant="flat"
                        >
                          {{ fId }}
                        </v-chip>
                      </template>
                      <span v-else class="text-disabled">None</span>
                    </div>
                    <div v-if="dryRunResult !== null" class="mt-2 text-caption font-family-monospace text-success font-weight-bold">
                      ✓ Dry Run Sample Output: {{ dryRunResult }}
                    </div>
                  </v-sheet>
                </v-col>
              </template>

              <v-col cols="12" sm="6">
                <v-combobox
                  v-model="formCategory"
                  :items="categoryOptions"
                  label="Category"
                  variant="outlined"
                  density="comfortable"
                />
              </v-col>

              <v-col cols="12" sm="6">
                <v-combobox
                  v-model="formUnit"
                  :items="unitOptions"
                  label="Unit"
                  placeholder="°C, °F, bar, %, kW"
                  variant="outlined"
                  density="comfortable"
                />
              </v-col>

              <v-col cols="12" sm="6" v-if="isEditing">
                <v-select
                  v-model="formStatus"
                  :items="['ACTIVE', 'MAINTENANCE', 'INACTIVE']"
                  label="Status"
                  variant="outlined"
                  density="comfortable"
                />
              </v-col>

              <v-col cols="12">
                <v-textarea
                  v-model="formDescription"
                  label="Description / Note"
                  placeholder="Additional context about this sensor"
                  variant="outlined"
                  density="comfortable"
                  rows="2"
                  hide-details
                />
              </v-col>
            </v-row>
          </v-form>
        </v-card-text>

        <v-card-actions class="px-6 pb-5 pt-2">
          <v-spacer />
          <v-btn variant="text" @click="formDialog = false">Cancel</v-btn>
          <v-btn
            color="primary"
            variant="flat"
            :loading="isSubmitting"
            :disabled="formType === 'FORMULA' && !!formulaValidationError"
            @click="submitForm"
          >
            {{ isEditing ? 'Save Changes' : 'Register Sensor' }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- IoT Device Sync Dialog -->
    <v-dialog v-model="syncDialog" max-width="500px" persistent>
      <v-card rounded="lg">
        <v-card-title class="px-6 pt-5 pb-2 font-weight-bold d-flex align-center">
          <v-icon color="indigo" class="mr-2">mdi-sync</v-icon>
          Sync IoT Emulator Device Sensors
        </v-card-title>
        <v-card-text class="px-6 py-3">
          <p class="text-body-2 text-medium-emphasis mb-4">
            Batch-registers emulated IoT device sensors from <code>formula-emulator</code> and automatically assigns sequential, collision-free global serial numbers to Oracle 19c.
          </p>
          <v-combobox
            v-model="syncDeviceId"
            :items="defaultDeviceOptions"
            label="IoT Device Identifier *"
            placeholder="e.g. DEVICE-001, DEVICE-002"
            variant="outlined"
            density="comfortable"
            class="mb-3"
          />
          <v-select
            v-model.number="syncCount"
            :items="[10, 20, 50, 100]"
            label="Number of Sensors to Discover / Sync"
            variant="outlined"
            density="comfortable"
            class="mb-3"
          />
          <v-checkbox
            v-model="syncSkipExisting"
            label="Skip sensors already assigned a serial number"
            density="compact"
            hide-details
          />
        </v-card-text>
        <v-card-actions class="px-6 pb-5 pt-2">
          <v-spacer />
          <v-btn variant="text" @click="syncDialog = false">Cancel</v-btn>
          <v-btn
            color="indigo"
            variant="flat"
            :loading="isSyncing"
            @click="executeDeviceSync"
          >
            Start Device Sync
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Delete Confirmation Dialog -->
    <v-dialog v-model="deleteDialog" max-width="450px">
      <v-card rounded="lg">
        <v-card-title class="px-6 pt-5 pb-2 text-error font-weight-bold d-flex align-center">
          <v-icon color="error" class="mr-2">mdi-alert-circle-outline</v-icon>
          Delete Sensor #{{ deletingItem?.serialNumber }}?
        </v-card-title>
        <v-card-text class="px-6 py-2 text-body-2">
          Are you sure you want to permanently delete sensor
          <strong>{{ deletingItem?.sensorName }}</strong>
          (Serial #{{ deletingItem?.serialNumber }}, Code: <code>{{ deletingItem?.sensorCode }}</code>)?
          This action cannot be undone.
        </v-card-text>
        <v-card-actions class="px-6 pb-4">
          <v-spacer />
          <v-btn variant="text" @click="deleteDialog = false">Cancel</v-btn>
          <v-btn
            color="error"
            variant="flat"
            :loading="isDeleting"
            @click="executeDelete"
          >
            Delete
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Snackbar Notification -->
    <v-snackbar v-model="snackbar" :color="snackbarColor" timeout="3000" location="bottom right">
      {{ snackbarText }}
      <template #actions>
        <v-btn variant="text" @click="snackbar = false">Close</v-btn>
      </template>
    </v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useSensorRegistryStore } from '../stores/sensorRegistryStore';
import { useFormulaStore } from '../stores/formulaStore';
import { extractReferences, extractPrimarySensorId } from '../services/formulaEngine';
import type {
  SensorRegistryItem,
  SensorType,
  SensorStatus,
} from '../types/sensorRegistry';

const emit = defineEmits<{
  (e: 'simulate-sensor', sensor: SensorRegistryItem): void;
}>();

const store = useSensorRegistryStore();
const formulaStore = useFormulaStore();

// Options
const typeFilterOptions = [
  { title: 'All Sensors (Physical & Formula)', value: 'ALL' },
  { title: 'IoT Physical Sensors Only', value: 'PHYSICAL' },
  { title: 'Formula Sensors Only', value: 'FORMULA' },
];

const defaultDeviceOptions = ['DEVICE-001', 'DEVICE-002', 'DEVICE-003', 'TURBINE-01', 'PUMP-A'];
const categoryOptions = ['Temperature', 'Pressure', 'Flow', 'Vibration', 'Electrical', 'Efficiency', 'Mechanical', 'Environmental'];
const unitOptions = ['°C', '°F', 'bar', 'mm/s', 'm³/h', 'kW', 'RPM', '%', 'ratio'];

// Form State
const formDialog = ref(false);
const isEditing = ref(false);
const isSubmitting = ref(false);
const editingItem = ref<SensorRegistryItem | null>(null);

const formType = ref<SensorType>('FORMULA');
const formSensorName = ref('');
const formSensorCode = ref('');
const formDevice = ref('DEVICE-001');
const formDeviceIndex = ref<number>(0);
const formFormulaExpression = ref('');
const formCategory = ref('Temperature');
const formUnit = ref('°C');
const formDescription = ref('');
const formStatus = ref<SensorStatus>('ACTIVE');

const formulaValidationError = ref<string | null>(null);
const dryRunResult = ref<number | null>(null);

// Sync Dialog State
const syncDialog = ref(false);
const isSyncing = ref(false);
const syncDeviceId = ref('DEVICE-001');
const syncCount = ref(10);
const syncSkipExisting = ref(true);

// Delete Dialog State
const deleteDialog = ref(false);
const isDeleting = ref(false);
const deletingItem = ref<SensorRegistryItem | null>(null);

// Snackbar State
const snackbar = ref(false);
const snackbarText = ref('');
const snackbarColor = ref('success');

const detectedFormulaRefs = computed(() => {
  if (!formFormulaExpression.value) return [];
  return extractReferences(formFormulaExpression.value, formulaStore.formulas).formulaIds;
});

const detectedSensorRefs = computed(() => {
  if (!formFormulaExpression.value) return [];
  return extractReferences(formFormulaExpression.value, formulaStore.formulas).sensorIds;
});

function notify(text: string, color: 'success' | 'error' | 'info' = 'success') {
  snackbarText.value = text;
  snackbarColor.value = color;
  snackbar.value = true;
}

function getStatusColor(status: SensorStatus): string {
  switch (status) {
    case 'ACTIVE': return 'success';
    case 'MAINTENANCE': return 'warning';
    case 'INACTIVE': return 'grey';
    default: return 'primary';
  }
}

function getPrimaryTargetSerial(expression?: string | null): string {
  return extractPrimarySensorId(expression, '10001');
}

function getSensorDeps(sensor: SensorRegistryItem) {
  return formulaStore.getDependencies(sensor.sensorCode);
}

function copySerial(serial: number) {
  navigator.clipboard.writeText(serial.toString());
  notify(`Serial #${serial} copied to clipboard!`, 'info');
}

function copyExpression(expr: string) {
  navigator.clipboard.writeText(expr);
  notify('Formula expression copied to clipboard!', 'info');
}

function insertTokenToFormula(token: string) {
  if (formFormulaExpression.value) {
    formFormulaExpression.value += ` ${token}`;
  } else {
    formFormulaExpression.value = token;
  }
  validateFormulaInput();
}

function validateFormulaInput() {
  dryRunResult.value = null;
  formulaValidationError.value = null;
  if (!formFormulaExpression.value) return;

  const refs = extractReferences(formFormulaExpression.value, formulaStore.formulas);
  const selfCode = (formSensorCode.value || editingItem.value?.sensorCode || '').trim().toUpperCase().replace(/[^A-Z0-9_]/g, '_');
  if (selfCode && refs.formulaIds.includes(selfCode)) {
    formulaValidationError.value = 'Self-reference detected: a formula sensor cannot reference itself.';
    return;
  }

  try {
    let dummy = formFormulaExpression.value
      .replace(/\bwindow_avg\s*\(\s*\d+\s*\)/g, '1')
      .replace(/\bwindow_max\s*\(\s*\d+\s*\)/g, '1')
      .replace(/\bwindow_min\s*\(\s*\d+\s*\)/g, '1')
      .replace(/\b(?:abs|sqrt|round|floor|ceil|min|max|pow|log|sin|cos)\s*\(/g, 'Math.abs(');
    refs.formulaIds.forEach((f) => (dummy = dummy.replaceAll(f, '1')));
    refs.sensorIds.forEach((s) => {
      dummy = dummy.replaceAll(`P_${s}`, '1').replaceAll(`SN_${s}`, '1').replaceAll(s, '1');
    });
    dummy = dummy.replace(/\b(?:P_|SN_)\d+\b/gi, '1').replaceAll('value', '1');

    // eslint-disable-next-line no-new-func
    new Function(`return (${dummy})`);
  } catch (err: unknown) {
    formulaValidationError.value = `Syntax error: ${err instanceof Error ? err.message : String(err)}`;
  }
}

function runDryTest() {
  validateFormulaInput();
  if (formulaValidationError.value || !formFormulaExpression.value) return;

  try {
    const ctx: Record<string, number> = { value: 50.0 };
    detectedSensorRefs.value.forEach((s) => {
      ctx[s] = 45.5;
      ctx[`P_${s}`] = 45.5;
      ctx[`SN_${s}`] = 45.5;
    });
    detectedFormulaRefs.value.forEach((f) => {
      ctx[f] = 72.0;
    });

    const testExpr = formFormulaExpression.value
      .replace(/\bwindow_avg\s*\(\s*\d+\s*\)/g, 'ctx.value')
      .replace(/\bwindow_max\s*\(\s*\d+\s*\)/g, 'ctx.value')
      .replace(/\bwindow_min\s*\(\s*\d+\s*\)/g, 'ctx.value')
      .replace(/\b(abs|sqrt|round|floor|ceil|min|max|pow|log|sin|cos)\s*\(/g, 'Math.$1(');

    // eslint-disable-next-line no-new-func
    const fn = new Function('ctx', `with(ctx) { return (${testExpr}); }`);
    const res = fn(ctx);
    dryRunResult.value = Math.round(Number(res) * 1000) / 1000;
  } catch (err: unknown) {
    formulaValidationError.value = `Dry run evaluation failed: ${err instanceof Error ? err.message : String(err)}`;
  }
}

function simulateSensor(sensor: SensorRegistryItem) {
  emit('simulate-sensor', sensor);
  notify(`Switched to Simulation Studio with Sensor #${sensor.serialNumber}`, 'info');
}

async function duplicateFormula(sensor: SensorRegistryItem) {
  try {
    const dup = await store.duplicateFormulaSensor(sensor);
    notify(`Duplicated Formula Sensor as #${dup.serialNumber} (${dup.sensorCode})!`);
  } catch (err: unknown) {
    notify(err instanceof Error ? err.message : 'Failed to duplicate formula sensor', 'error');
  }
}

function openCreateModal(type: SensorType = 'FORMULA') {
  isEditing.value = false;
  editingItem.value = null;
  formType.value = type;
  formSensorName.value = '';
  formSensorCode.value = '';
  formDevice.value = 'DEVICE-001';
  formDeviceIndex.value = 0;
  formFormulaExpression.value = type === 'FORMULA' ? 'P_10001 * 1.5' : '';
  formCategory.value = type === 'PHYSICAL' ? 'Temperature' : 'Efficiency';
  formUnit.value = type === 'PHYSICAL' ? '°C' : '%';
  formDescription.value = '';
  formStatus.value = 'ACTIVE';
  formulaValidationError.value = null;
  dryRunResult.value = null;
  formDialog.value = true;
}

function openEditModal(sensor: SensorRegistryItem) {
  isEditing.value = true;
  editingItem.value = sensor;
  formType.value = sensor.sensorType;
  formSensorName.value = sensor.sensorName;
  formSensorCode.value = sensor.sensorCode;
  formDevice.value = sensor.deviceId || '';
  formDeviceIndex.value = sensor.deviceSensorIndex ?? 0;
  formFormulaExpression.value = sensor.formulaExpression || '';
  formCategory.value = sensor.category || '';
  formUnit.value = sensor.unit || '';
  formDescription.value = sensor.description || '';
  formStatus.value = sensor.status;
  formulaValidationError.value = null;
  dryRunResult.value = null;
  formDialog.value = true;
}

async function submitForm() {
  if (!formSensorName.value) {
    notify('Please fill in the sensor name.', 'error');
    return;
  }
  if (formType.value === 'FORMULA') {
    validateFormulaInput();
    if (formulaValidationError.value) return;
  }

  isSubmitting.value = true;
  try {
    if (isEditing.value && editingItem.value) {
      await store.updateSensor(editingItem.value.serialNumber, {
        sensorName: formSensorName.value,
        category: formCategory.value,
        unit: formUnit.value,
        formulaExpression: formType.value === 'FORMULA' ? formFormulaExpression.value : undefined,
        description: formDescription.value,
        status: formStatus.value,
      });
      notify(`Sensor #${editingItem.value.serialNumber} updated successfully!`);
    } else {
      const created = await store.createSensor({
        sensorCode: formType.value === 'FORMULA' && formSensorCode.value.trim() ? formSensorCode.value.trim() : undefined,
        sensorName: formSensorName.value,
        sensorType: formType.value,
        category: formCategory.value,
        unit: formUnit.value,
        deviceId: formType.value === 'PHYSICAL' ? formDevice.value : undefined,
        deviceSensorIndex: formType.value === 'PHYSICAL' ? formDeviceIndex.value : undefined,
        formulaExpression: formType.value === 'FORMULA' ? formFormulaExpression.value : undefined,
        description: formDescription.value,
        status: formStatus.value,
      });
      notify(`Sensor registered with Serial #${created.serialNumber}!`);
    }
    formDialog.value = false;
  } catch (err: unknown) {
    notify(err instanceof Error ? err.message : 'Operation failed', 'error');
  } finally {
    isSubmitting.value = false;
  }
}

async function handleSyncRules() {
  try {
    const res = await store.syncRulesToFlink();
    notify(res.message || `성공적으로 ${res.syncedCount}개의 룰이 Flink로 동기화되었습니다`, 'success');
  } catch (err: unknown) {
    notify(err instanceof Error ? err.message : 'Flink 룰 동기화 실패', 'error');
  }
}

function openSyncModal() {
  syncDialog.value = true;
}

async function executeDeviceSync() {
  if (!syncDeviceId.value) {
    notify('Please provide an IoT device identifier', 'error');
    return;
  }
  isSyncing.value = true;
  try {
    const synced = await store.syncDeviceSensors({
      deviceId: syncDeviceId.value,
      sensorCount: syncCount.value,
      skipExisting: syncSkipExisting.value,
    });
    notify(`Successfully synced and assigned serial numbers to ${synced.length} sensors from ${syncDeviceId.value}!`);
    syncDialog.value = false;
  } catch (err: unknown) {
    notify(err instanceof Error ? err.message : 'Sync failed', 'error');
  } finally {
    isSyncing.value = false;
  }
}

function openDeleteModal(sensor: SensorRegistryItem) {
  deletingItem.value = sensor;
  deleteDialog.value = true;
}

async function executeDelete() {
  if (!deletingItem.value) return;
  isDeleting.value = true;
  try {
    await store.deleteSensor(deletingItem.value.serialNumber);
    notify(`Sensor #${deletingItem.value.serialNumber} deleted.`);
    deleteDialog.value = false;
  } catch (err: unknown) {
    notify(err instanceof Error ? err.message : 'Delete failed', 'error');
  } finally {
    isDeleting.value = false;
  }
}

defineExpose({
  openCreateModal,
});

onMounted(() => {
  store.fetchSensors();
});
</script>

<style scoped>
.font-family-monospace {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}

.formula-code-preview code {
  background-color: rgba(var(--v-theme-surface-variant), 0.5);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.82rem;
  font-family: 'Consolas', 'Monaco', monospace;
  color: rgb(var(--v-theme-primary));
  word-break: break-all;
  white-space: normal;
}

.cursor-pointer {
  cursor: pointer;
}

.gap-1 {
  gap: 4px;
}

.gap-2 {
  gap: 8px;
}

.action-buttons-container {
  min-width: 0;
}

/* Responsive Table Layout */
.sensor-table-card {
  width: 100%;
}

.sensor-registry-table {
  width: 100%;
}

.sensor-registry-table :deep(.v-table__wrapper) {
  overflow-x: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(var(--v-theme-primary), 0.35) transparent;
}

.sensor-registry-table :deep(.v-table__wrapper)::-webkit-scrollbar {
  height: 6px;
}

.sensor-registry-table :deep(.v-table__wrapper)::-webkit-scrollbar-track {
  background: rgba(var(--v-theme-surface-variant), 0.3);
  border-radius: 3px;
}

.sensor-registry-table :deep(.v-table__wrapper)::-webkit-scrollbar-thumb {
  background: rgba(var(--v-theme-primary), 0.35);
  border-radius: 3px;
}

.sensor-registry-table :deep(.v-table__wrapper)::-webkit-scrollbar-thumb:hover {
  background: rgba(var(--v-theme-primary), 0.6);
}

.sensor-registry-table :deep(table) {
  width: 100%;
  min-width: 1020px;
}

.sensor-registry-table :deep(th),
.sensor-registry-table :deep(td) {
  padding: 0 10px !important;
}

/* Specific column styles */
.col-serial {
  width: 85px;
  min-width: 80px;
  white-space: nowrap;
}

.col-name {
  min-width: 160px;
  max-width: 250px;
}

.col-type {
  width: 120px;
  min-width: 110px;
  white-space: nowrap;
}

.col-target {
  width: 140px;
  min-width: 130px;
}

.col-formula {
  min-width: 190px;
}

.col-category {
  width: 95px;
  min-width: 85px;
  white-space: nowrap;
}

.col-unit {
  width: 65px;
  min-width: 55px;
  white-space: nowrap;
}

.col-status {
  width: 85px;
  min-width: 80px;
  white-space: nowrap;
}

.col-actions {
  width: 150px;
  min-width: 145px;
  white-space: nowrap;
}

/* Sticky Actions Column - stays visible even if window shrinks */
.sensor-registry-table :deep(th.col-actions),
.sensor-registry-table :deep(td.col-actions) {
  position: sticky;
  right: 0;
  z-index: 2;
  background-color: rgb(var(--v-theme-surface));
  border-left: 1px solid rgba(var(--v-border-color), 0.12);
  box-shadow: -4px 0 8px -2px rgba(0, 0, 0, 0.18);
  padding: 0 12px !important;
}

.sensor-registry-table :deep(thead th.col-actions) {
  z-index: 3;
  background-color: rgb(var(--v-theme-surface-variant));
  border-left: 1px solid rgba(var(--v-border-color), 0.2);
}

.action-btn-group {
  gap: 2px;
  white-space: nowrap;
}
</style>
