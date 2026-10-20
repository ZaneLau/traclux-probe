<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import WorkbenchDateRangePopover from '~/components/workbench/filters/WorkbenchDateRangePopover.vue'
import WorkbenchFilterMultiSelect from '~/components/workbench/filters/WorkbenchFilterMultiSelect.vue'
import {
  workbenchCompactButtonClass,
  workbenchFieldShellClass,
  workbenchPanelClass,
  workbenchSelectUi
} from '~/components/workbench/filters/workbenchFilterUi'
import type { WorkbenchFilterOption } from '~/composables/useWorkbenchAnalysisFilters'

const props = withDefaults(defineProps<{
  timePreset?: string
  dateFrom?: string
  dateTo?: string
  timeOptions?: WorkbenchFilterOption[]
  platformOptions?: WorkbenchFilterOption[]
  selectedPlatforms?: string[]
  compareValue?: string
  compareOptions?: WorkbenchFilterOption[]
  frequencyValue?: string
  frequencyOptions?: WorkbenchFilterOption[]
  problemSpaceOptions?: WorkbenchFilterOption[]
  selectedProblemSpaces?: string[]
  problemSpaceDisabled?: boolean
  problemSpaceDisabledLabel?: string
  topicOptions?: WorkbenchFilterOption[]
  selectedTopics?: string[]
  topicDisabled?: boolean
  topicDisabledLabel?: string
  note?: string
  showDateRange?: boolean
  combineScopeRow?: boolean
}>(), {
  timePreset: '',
  dateFrom: '',
  dateTo: '',
  timeOptions: () => [],
  platformOptions: () => [],
  selectedPlatforms: () => [],
  compareValue: '',
  compareOptions: () => [],
  frequencyValue: '',
  frequencyOptions: () => [],
  problemSpaceOptions: () => [],
  selectedProblemSpaces: () => [],
  problemSpaceDisabled: false,
  problemSpaceDisabledLabel: '',
  topicOptions: () => [],
  selectedTopics: () => [],
  topicDisabled: false,
  topicDisabledLabel: '',
  note: '',
  showDateRange: true,
  combineScopeRow: false
})

const emit = defineEmits<{
  'update:timePreset': [value: string]
  'update:dateFrom': [value: string]
  'update:dateTo': [value: string]
  'update:selectedPlatforms': [value: string[]]
  'update:compareValue': [value: string]
  'update:frequencyValue': [value: string]
  'update:selectedProblemSpaces': [value: string[]]
  'update:selectedTopics': [value: string[]]
}>()

const timePresetModel = computed({
  get: () => props.timePreset,
  set: (value: string) => emit('update:timePreset', value)
})

const compareValueModel = computed({
  get: () => props.compareValue,
  set: (value: string) => emit('update:compareValue', value)
})

const frequencyValueModel = computed({
  get: () => props.frequencyValue,
  set: (value: string) => emit('update:frequencyValue', value)
})

const customDatePopoverOpen = ref(false)
const customTimeFieldRef = ref<HTMLElement | null>(null)
const isCustomTimePreset = computed(() => props.timePreset === 'custom')

function formatCompactDate(value?: string) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value.trim())) {
    return ''
  }
  return value.trim().slice(5).replace('-', '/')
}

const timeOptionsView = computed<WorkbenchFilterOption[]>(() => {
  return props.timeOptions.map((option) => {
    if (option.key !== 'custom') {
      return option
    }

    if (props.timePreset !== 'custom') {
      return option
    }

    const start = formatCompactDate(props.dateFrom)
    const end = formatCompactDate(props.dateTo)
    if (!start || !end) {
      return option
    }

    return {
      ...option,
      label: `${start}~${end}`
    }
  })
})

const customTimePresetSwitchOptions = computed<WorkbenchFilterOption[]>(() =>
  props.timeOptions.filter(option => option.key !== 'custom')
)

const customRangeTriggerLabel = computed(() => {
  const start = formatCompactDate(props.dateFrom)
  const end = formatCompactDate(props.dateTo)
  if (!start || !end) {
    return '自定义'
  }
  return `${start}~${end}`
})

watch(isCustomTimePreset, async (isCustom, wasCustom) => {
  if (isCustom && !wasCustom) {
    customDatePopoverOpen.value = false
    await nextTick()
    customDatePopoverOpen.value = true
    return
  }
  if (!isCustom) {
    customDatePopoverOpen.value = false
  }
}, { flush: 'post' })

const showScopeRow = computed(() => {
  return props.problemSpaceOptions.length > 0
    || props.topicOptions.length > 0
    || props.problemSpaceDisabled
    || props.topicDisabled
})

const showCombinedScopeRow = computed(() => props.combineScopeRow && showScopeRow.value)

const combinedScopeColumnCount = computed(() => {
  if (!showCombinedScopeRow.value) {
    return 0
  }

  let count = 0

  if (props.timeOptions.length) count += 1
  if (props.platformOptions.length || props.selectedPlatforms.length) count += 1
  if (props.compareOptions.length) count += 1
  if (props.frequencyOptions.length) count += 1
  if (props.problemSpaceOptions.length || props.problemSpaceDisabled) count += 1
  if (props.topicOptions.length || props.topicDisabled) count += 1

  return Math.max(count, 1)
})

function handleDateFrom(value: string) {
  emit('update:dateFrom', value)
  emit('update:timePreset', 'custom')
}

function handleDateTo(value: string) {
  emit('update:dateTo', value)
  emit('update:timePreset', 'custom')
}

function handleTimePresetShortcut(value: string) {
  emit('update:timePreset', value)
}

function openCustomDatePopover() {
  if (!isCustomTimePreset.value) {
    return
  }
  customDatePopoverOpen.value = true
}
</script>

<template>
  <section class="surface workbench-toolbar-surface">
    <div class="workbench-toolbar-card">
      <div
        class="workbench-toolbar-grid"
        :class="{ 'workbench-toolbar-grid--single-row': showCombinedScopeRow }"
        :style="showCombinedScopeRow ? { '--workbench-toolbar-columns': String(combinedScopeColumnCount) } : undefined"
      >
        <div
          v-if="timeOptions.length"
          :class="`${workbenchPanelClass} workbench-toolbar-time-panel`"
        >
          <p class="workbench-filter-label">时间选择</p>
          <div
            ref="customTimeFieldRef"
            :class="[workbenchFieldShellClass, { 'workbench-toolbar-time-shell': showDateRange && isCustomTimePreset }]"
            @click="openCustomDatePopover"
          >
            <button
              v-if="showDateRange && isCustomTimePreset"
              type="button"
              :class="`workbench-toolbar-time-trigger ${workbenchCompactButtonClass}`"
              @click.stop="openCustomDatePopover"
            >
              <span>{{ customRangeTriggerLabel }}</span>
              <UIcon
                name="i-lucide-calendar-range"
                class="size-4 text-slate-500"
              />
            </button>

            <USelect
              v-else
              v-model="timePresetModel"
              :items="timeOptionsView"
              value-key="key"
              label-key="label"
              color="neutral"
              variant="ghost"
              :class="workbenchCompactButtonClass"
              :ui="workbenchSelectUi"
            />

            <WorkbenchDateRangePopover
              v-if="showDateRange && isCustomTimePreset"
              v-model:open="customDatePopoverOpen"
              :date-from="dateFrom"
              :date-to="dateTo"
              :boundary-ref="customTimeFieldRef"
              hide-trigger
              panel-class="workbench-toolbar-time-panel__date-panel"
              :preset-options="customTimePresetSwitchOptions"
              @update:date-from="handleDateFrom"
              @update:date-to="handleDateTo"
              @select-preset="handleTimePresetShortcut"
            />
          </div>
        </div>

        <WorkbenchFilterMultiSelect
          v-if="platformOptions.length || selectedPlatforms.length"
          label="平台"
          all-label="全部平台"
          empty-label="暂无平台"
          :options="platformOptions"
          :selected="selectedPlatforms"
          @update:selected="emit('update:selectedPlatforms', $event)"
        />

        <div
          v-if="compareOptions.length"
          :class="workbenchPanelClass"
        >
          <p class="workbench-filter-label">对比</p>
          <div :class="workbenchFieldShellClass">
            <USelect
              v-model="compareValueModel"
              :items="compareOptions"
              value-key="key"
              label-key="label"
              color="neutral"
              variant="ghost"
              :class="workbenchCompactButtonClass"
              :ui="workbenchSelectUi"
            />
          </div>
        </div>

        <div
          v-if="frequencyOptions.length"
          :class="workbenchPanelClass"
        >
          <p class="workbench-filter-label">频率</p>
          <div :class="workbenchFieldShellClass">
            <USelect
              v-model="frequencyValueModel"
              :items="frequencyOptions"
              value-key="key"
              label-key="label"
              color="neutral"
              variant="ghost"
              :class="workbenchCompactButtonClass"
              :ui="workbenchSelectUi"
            />
          </div>
        </div>

        <WorkbenchFilterMultiSelect
          v-if="showCombinedScopeRow && (problemSpaceOptions.length || problemSpaceDisabled)"
          label="问题空间"
          all-label="全部问题空间"
          empty-label="暂无问题空间"
          :disabled="problemSpaceDisabled"
          :disabled-label="problemSpaceDisabledLabel"
          :options="problemSpaceOptions"
          :selected="selectedProblemSpaces"
          @update:selected="emit('update:selectedProblemSpaces', $event)"
        />

        <WorkbenchFilterMultiSelect
          v-if="showCombinedScopeRow && (topicOptions.length || topicDisabled)"
          label="主题"
          all-label="全部主题"
          empty-label="暂无主题"
          :disabled="topicDisabled"
          :disabled-label="topicDisabledLabel"
          :options="topicOptions"
          :selected="selectedTopics"
          @update:selected="emit('update:selectedTopics', $event)"
        />
      </div>

      <div
        v-if="showScopeRow && !showCombinedScopeRow"
        class="workbench-toolbar-grid workbench-toolbar-grid--scope"
      >
        <WorkbenchFilterMultiSelect
          v-if="problemSpaceOptions.length || problemSpaceDisabled"
          label="问题空间"
          all-label="全部问题空间"
          empty-label="暂无问题空间"
          :disabled="problemSpaceDisabled"
          :disabled-label="problemSpaceDisabledLabel"
          :options="problemSpaceOptions"
          :selected="selectedProblemSpaces"
          @update:selected="emit('update:selectedProblemSpaces', $event)"
        />

        <WorkbenchFilterMultiSelect
          v-if="topicOptions.length || topicDisabled"
          label="主题"
          all-label="全部主题"
          empty-label="暂无主题"
          :disabled="topicDisabled"
          :disabled-label="topicDisabledLabel"
          :options="topicOptions"
          :selected="selectedTopics"
          @update:selected="emit('update:selectedTopics', $event)"
        />
      </div>

      <p
        v-if="note"
        class="workbench-toolbar-note"
      >
        {{ note }}
      </p>
    </div>
  </section>
</template>

<style scoped>
.workbench-toolbar-surface {
  padding: 12px;
}

.workbench-toolbar-card {
  display: grid;
  gap: 12px;
}

.workbench-toolbar-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(176px, 208px));
  align-items: stretch;
}

.workbench-toolbar-grid--scope {
  grid-template-columns: repeat(auto-fit, minmax(260px, 320px));
}

.workbench-toolbar-grid--single-row {
  grid-template-columns: repeat(var(--workbench-toolbar-columns, 6), minmax(0, 1fr));
}

.workbench-toolbar-time-panel {
  min-width: 0;
}

.workbench-toolbar-time-trigger {
  display: inline-flex;
  align-items: center;
  width: 100%;
  border: 0;
  cursor: pointer;
}

.workbench-toolbar-time-shell {
  position: relative;
  cursor: pointer;
}

:deep(.workbench-toolbar-time-panel__date-panel) {
  min-width: min(720px, calc(100vw - 48px));
}

.workbench-filter-label {
  font-size: 11px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #475569;
}

.workbench-toolbar-note {
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

@media (max-width: 900px) {
  .workbench-toolbar-grid,
  .workbench-toolbar-grid--scope {
    grid-template-columns: 1fr;
  }

  .workbench-toolbar-time-panel {
    min-width: 0;
  }
}
</style>
