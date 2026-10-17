<template>
  <div class="sensor-management">
    <!-- Top KPI Cards -->
    <v-row class="mb-4">
      <v-col cols="12" sm="6" md="3">
        <v-card class="border elevation-0" rounded="lg">
          <v-card-text class="d-flex align-center">
            <v-avatar color="primary" variant="tonal" rounded="lg" size="48" class="mr-3">
              <v-icon color="primary">mdi-numeric</v-icon>
            </v-avatar>
            <div>
              <div class="text-caption text-medium-emphasis font-weight-medium">
                Global Serial Sensors
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
        <v-card class="border elevation-0" rounded="lg">
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
                Collision Disambiguated
              </div>
            </div>
          </v-card-text>
        </v-card>
      </v-col>

      <v-col cols="12" sm="6" md="3">
        <v-card class="border elevation-0" rounded="lg">
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
                Integrated Serial Numbered
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
          <v-col cols="12" md="4">
            <v-text-field
              v-model="store.searchQuery"
              prepend-inner-icon="mdi-magnify"
              label="Search by Serial (#), Code, Name, IoT Device..."
              variant="outlined"
              density="compact"
              hide-details
              clearable
            />
          </v-col>

          <!-- Type filter toggle -->
          <v-col cols="12" md="3">
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
          <v-col cols="12" md="2">
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
          <v-col cols="12" md="3" class="d-flex justify-end gap-2">
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
                  prepend-icon="mdi-memory"
                  title="Add IoT Physical Sensor"
                  @click="openCreateModal('PHYSICAL')"
                />
                <v-list-item
                  prepend-icon="mdi-function"
                  title="Add Formula Sensor"
                  @click="openCreateModal('FORMULA')"
                />
              </v-list>
            </v-menu>

            <v-btn
              color="indigo"
              variant="tonal"
              prepend-icon="mdi-sync"
              class="ml-2"
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
                  class="ml-2"
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
              class="ml-1"
              :loading="store.isLoading"
              @click="store.fetchSensors"
            />
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <!-- Sensor Registry Table -->
    <v-card class="border elevation-0" rounded="lg">
      <v-table hover density="comfortable">
        <thead>
          <tr class="bg-surface-variant text-caption font-weight-bold text-uppercase">
            <th style="width: 110px">Serial No</th>
            <th>Sensor Code & Name</th>
            <th style="width: 140px">Type</th>
            <th style="width: 180px">IoT Device / Index</th>
            <th>Formula / Expression</th>
            <th style="width: 140px">Category</th>
            <th style="width: 90px">Unit</th>
            <th style="width: 100px">Status</th>
            <th style="width: 150px" class="text-right">Actions</th>
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
            <td>
              <v-chip
                size="small"
                color="primary"
                variant="flat"
                class="font-weight-bold cursor-pointer"
                @click="copySerial(sensor.serialNumber)"
              >
                #{{ sensor.serialNumber }}
                <v-tooltip activator="parent" location="top">
                  Click to copy Serial Number #{{ sensor.serialNumber }}
                </v-tooltip>
              </v-chip>
            </td>

            <!-- Code & Name -->
            <td>
              <div class="font-weight-medium text-body-2">{{ sensor.sensorName }}</div>
              <div class="text-caption text-medium-emphasis font-family-monospace">
                {{ sensor.sensorCode }}
              </div>
            </td>

            <!-- Type -->
            <td>
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

            <!-- Device ID & Device Sensor Index -->
            <td>
              <div v-if="sensor.sensorType === 'PHYSICAL'" class="d-flex align-center">
                <v-icon size="16" color="indigo" class="mr-1">mdi-router-wireless</v-icon>
                <span class="font-weight-medium text-body-2 mr-1">{{ sensor.deviceId }}</span>
                <v-chip size="x-small" variant="outlined" color="secondary">
                  Index #{{ sensor.deviceSensorIndex }}
                </v-chip>
              </div>
              <div v-else class="text-caption text-medium-emphasis italic">
                Virtual (Formula)
              </div>
            </td>

            <!-- Formula Expression -->
            <td>
              <div v-if="sensor.sensorType === 'FORMULA'" class="formula-code-preview">
                <code>{{ sensor.formulaExpression }}</code>
              </div>
              <div v-else class="text-caption text-disabled">
                — (Raw Physical Signal)
              </div>
            </td>

            <!-- Category -->
            <td>
              <v-chip size="x-small" variant="text" class="text-capitalize">
                {{ sensor.category }}
              </v-chip>
            </td>

            <!-- Unit -->
            <td>
              <span class="text-caption font-weight-medium">{{ sensor.unit || '—' }}</span>
            </td>

            <!-- Status -->
            <td>
              <v-chip
                size="x-small"
                :color="getStatusColor(sensor.status)"
                variant="tonal"
              >
                {{ sensor.status }}
              </v-chip>
            </td>

            <!-- Actions (Simulate On-Demand, Edit, Delete) -->
            <td class="text-right">
              <v-tooltip text="Run On-Demand Telemetry Simulation" location="top">
                <template #activator="{ props }">
                  <v-btn
                    icon="mdi-play-circle-outline"
                    variant="text"
                    size="small"
                    color="success"
                    v-bind="props"
                    @click="simulateSensor(sensor)"
                  />
                </template>
              </v-tooltip>
              <v-btn
                icon="mdi-pencil-outline"
                variant="text"
                size="small"
                color="primary"
                @click="openEditModal(sensor)"
              />
              <v-btn
                icon="mdi-delete-outline"
                variant="text"
                size="small"
                color="error"
                @click="openDeleteModal(sensor)"
              />
            </td>
          </tr>
        </tbody>
      </v-table>
    </v-card>

    <!-- Create / Edit Sensor Dialog -->
    <v-dialog v-model="formDialog" max-width="650px" persistent>
      <v-card rounded="lg">
        <v-card-title class="px-6 pt-5 pb-2 font-weight-bold d-flex align-center">
          <v-icon
            :color="formType === 'PHYSICAL' ? 'indigo' : 'teal'"
            class="mr-2"
          >
            {{ isEditing ? 'mdi-pencil' : (formType === 'PHYSICAL' ? 'mdi-chip' : 'mdi-function-variant') }}
          </v-icon>
          {{ isEditing ? 'Edit Sensor' : (formType === 'PHYSICAL' ? 'Register IoT Physical Sensor' : 'Create Formula Sensor') }}
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
              <v-tab value="PHYSICAL">
                <v-icon start>mdi-chip</v-icon>
                IoT Physical Sensor
              </v-tab>
              <v-tab value="FORMULA">
                <v-icon start>mdi-function-variant</v-icon>
                Formula Sensor
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

              <!-- Formula Sensor specific fields -->
              <template v-if="formType === 'FORMULA'">
                <v-col cols="12">
                  <v-text-field
                    v-model="formFormulaExpression"
                    label="Formula Expression (Referencing Serial #) *"
                    placeholder="e.g. SN_10001 * 1.8 + 32, (SN_10002 - SN_10004) / 10"
                    variant="outlined"
                    density="comfortable"
                    :rules="[v => !!v || 'Formula expression is required']"
                  />
                </v-col>
                <v-col cols="12" class="mb-2">
                  <div class="text-caption text-medium-emphasis mb-1">
                    Quick Reference: Click to insert sensor serial number into formula:
                  </div>
                  <div class="d-flex flex-wrap gap-1">
                    <v-chip
                      v-for="s in store.sensors.slice(0, 8)"
                      :key="s.serialNumber"
                      size="x-small"
                      variant="outlined"
                      color="primary"
                      class="cursor-pointer"
                      @click="insertSerialToFormula(s.serialNumber)"
                    >
                      #{{ s.serialNumber }} ({{ s.sensorName.split(' ')[0] }})
                    </v-chip>
                  </div>
                </v-col>
              </template>

              <!-- Common fields -->
              <v-col cols="12">
                <v-text-field
                  v-model="formSensorName"
                  label="Sensor Name *"
                  placeholder="e.g. Main Bearing Temperature"
                  variant="outlined"
                  density="comfortable"
                  :rules="[v => !!v || 'Sensor name is required']"
                />
              </v-col>

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
                  placeholder="°C, bar, %, kW"
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
import { ref, onMounted } from 'vue';
import { useSensorRegistryStore } from '../stores/sensorRegistryStore';
import type {
  SensorRegistryItem,
  SensorType,
  SensorStatus,
} from '../types/sensorRegistry';

const emit = defineEmits<{
  (e: 'simulate-sensor', sensor: SensorRegistryItem): void;
}>();

const store = useSensorRegistryStore();

// Options
const typeFilterOptions = [
  { title: 'All Sensors (Total)', value: 'ALL' },
  { title: 'IoT Physical Sensors', value: 'PHYSICAL' },
  { title: 'Formula Sensors', value: 'FORMULA' },
];

const defaultDeviceOptions = ['DEVICE-001', 'DEVICE-002', 'DEVICE-003', 'TURBINE-01', 'PUMP-A'];
const categoryOptions = ['Temperature', 'Pressure', 'Flow', 'Vibration', 'Electrical', 'Efficiency', 'Mechanical', 'Environmental'];
const unitOptions = ['°C', 'bar', 'mm/s', 'm³/h', 'kW', 'RPM', '%', 'ratio'];

// Form State
const formDialog = ref(false);
const isEditing = ref(false);
const isSubmitting = ref(false);
const editingItem = ref<SensorRegistryItem | null>(null);

const formType = ref<SensorType>('PHYSICAL');
const formSensorName = ref('');
const formDevice = ref('DEVICE-001');
const formDeviceIndex = ref<number>(0);
const formFormulaExpression = ref('');
const formCategory = ref('Temperature');
const formUnit = ref('°C');
const formDescription = ref('');
const formStatus = ref<SensorStatus>('ACTIVE');

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

function copySerial(serial: number) {
  navigator.clipboard.writeText(serial.toString());
  notify(`Serial #${serial} copied to clipboard!`, 'info');
}

function insertSerialToFormula(serial: number) {
  if (formFormulaExpression.value) {
    formFormulaExpression.value += ` SN_${serial}`;
  } else {
    formFormulaExpression.value = `SN_${serial}`;
  }
}

function simulateSensor(sensor: SensorRegistryItem) {
  emit('simulate-sensor', sensor);
  notify(`Switched to Simulation Studio with Sensor #${sensor.serialNumber}`, 'info');
}

function openCreateModal(type: SensorType) {
  isEditing.value = false;
  editingItem.value = null;
  formType.value = type;
  formSensorName.value = '';
  formDevice.value = 'DEVICE-001';
  formDeviceIndex.value = 0;
  formFormulaExpression.value = '';
  formCategory.value = type === 'PHYSICAL' ? 'Temperature' : 'Efficiency';
  formUnit.value = type === 'PHYSICAL' ? '°C' : '%';
  formDescription.value = '';
  formStatus.value = 'ACTIVE';
  formDialog.value = true;
}

function openEditModal(sensor: SensorRegistryItem) {
  isEditing.value = true;
  editingItem.value = sensor;
  formType.value = sensor.sensorType;
  formSensorName.value = sensor.sensorName;
  formDevice.value = sensor.deviceId || '';
  formDeviceIndex.value = sensor.deviceSensorIndex ?? 0;
  formFormulaExpression.value = sensor.formulaExpression || '';
  formCategory.value = sensor.category || '';
  formUnit.value = sensor.unit || '';
  formDescription.value = sensor.description || '';
  formStatus.value = sensor.status;
  formDialog.value = true;
}

async function submitForm() {
  if (!formSensorName.value) {
    notify('Please fill in the sensor name.', 'error');
    return;
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
</style>
