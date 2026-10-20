<script setup lang="ts">
import { computed } from 'vue'
import {
  workbenchDropdownUi,
  workbenchMenuFieldShellClass,
  workbenchMenuTriggerClass,
  workbenchPanelClass
} from '~/components/workbench/filters/workbenchFilterUi'
import type { WorkbenchFilterOption } from '~/composables/useWorkbenchAnalysisFilters'

const props = withDefaults(defineProps<{
  label: string
  allLabel: string
  emptyLabel?: string
  disabledLabel?: string
  options: WorkbenchFilterOption[]
  selected: string[]
  disabled?: boolean
  collapseAllSelection?: boolean
}>(), {
  emptyLabel: '暂无可选项',
  disabledLabel: '',
  disabled: false,
  collapseAllSelection: true
})

const emit = defineEmits<{
  'update:selected': [value: string[]]
}>()

const availableKeys = computed(() => props.options.map(option => option.key))

const effectiveSelected = computed(() => {
  if (!availableKeys.value.length) {
    return []
  }
  const valid = props.selected.filter(value => availableKeys.value.includes(value))
  return valid.length ? valid : availableKeys.value
})

const summaryLabel = computed(() => {
  if (props.disabled) {
    return props.disabledLabel || props.emptyLabel
  }
  if (!props.options.length) {
    return props.emptyLabel
  }
  if (effectiveSelected.value.length === props.options.length) {
    return props.allLabel
  }
  if (effectiveSelected.value.length === 1) {
    return props.options.find(option => option.key === effectiveSelected.value[0])?.label || props.allLabel
  }
  return `${props.allLabel}（${effectiveSelected.value.length}/${props.options.length}）`
})

function commitSelection(next: string[]) {
  const normalized = props.collapseAllSelection && next.length === props.options.length ? [] : next
  emit('update:selected', normalized)
}

function toggleOption(key: string) {
  const current = new Set(effectiveSelected.value)
  if (current.has(key)) {
    if (current.size === 1) {
      return
    }
    current.delete(key)
  } else {
    current.add(key)
  }
  const next = availableKeys.value.filter(item => current.has(item))
  commitSelection(next)
}

const items = computed(() => props.options.map(option => ({
  label: option.count != null ? `${option.label} · ${option.count}` : option.label,
  type: 'checkbox' as const,
  checked: effectiveSelected.value.includes(option.key),
  onSelect: (event: Event) => {
    event.preventDefault()
    toggleOption(option.key)
  }
})))
</script>

<template>
  <div :class="workbenchPanelClass">
    <p class="workbench-filter-label">{{ label }}</p>

    <div
      v-if="disabled || !options.length"
      :class="workbenchMenuFieldShellClass"
    >
      <div class="workbench-filter-disabled">
        {{ summaryLabel }}
      </div>
    </div>

    <UDropdownMenu
      v-else
      :items="items"
      :content="{ align: 'start', side: 'bottom', sideOffset: 10 }"
      :ui="workbenchDropdownUi"
    >
      <div :class="workbenchMenuFieldShellClass">
        <UButton
          color="neutral"
          variant="ghost"
          :class="workbenchMenuTriggerClass"
          trailing-icon="i-lucide-chevron-down"
        >
          <span>{{ summaryLabel }}</span>
        </UButton>
      </div>
    </UDropdownMenu>
  </div>
</template>

<style scoped>
.workbench-filter-label {
  font-size: 11px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #475569;
}

.workbench-filter-disabled {
  display: flex;
  min-height: 36px;
  align-items: center;
  padding: 0 12px;
  color: #64748b;
  font-size: 14px;
}
</style>
