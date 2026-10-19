<script setup lang="ts">
import type { ProbeStatus } from '~/types/engine'

type ProbeStatusValue = ProbeStatus | 'all'

const props = defineProps<{
  modelValue: ProbeStatusValue
  counts: Record<ProbeStatusValue, number>
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: ProbeStatusValue): void
}>()

const options: Array<{ key: ProbeStatusValue, label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'active', label: '已启用' },
  { key: 'suggested', label: '建议启用' },
  { key: 'inactive', label: '已停用' }
]

function setStatus(value: ProbeStatusValue) {
  if (value === props.modelValue) {
    return
  }
  emit('update:modelValue', value)
}
</script>

<template>
  <section class="probe-status-tabs">
    <button
      v-for="option in options"
      :key="`probe-status-${option.key}`"
      type="button"
      class="probe-status-tabs__item"
      :class="{ 'probe-status-tabs__item--active': modelValue === option.key }"
      @click="setStatus(option.key)"
    >
      <span>{{ option.label }}</span>
      <span class="probe-status-tabs__count">{{ counts[option.key] ?? 0 }}</span>
    </button>
  </section>
</template>

<style scoped>
.probe-status-tabs {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.probe-status-tabs__item {
  min-height: 28px;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  background: #ffffff;
  color: #4b5563;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  cursor: pointer;
}

.probe-status-tabs__item:hover {
  border-color: #9ca3af;
  color: #111827;
}

.probe-status-tabs__item--active {
  border-color: #111827;
  color: #111827;
  background: #f9fafb;
}

.probe-status-tabs__count {
  min-width: 18px;
  min-height: 18px;
  border-radius: 999px;
  background: #f3f4f6;
  color: #4b5563;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
}

.probe-status-tabs__item--active .probe-status-tabs__count {
  background: #111827;
  color: #f9fafb;
}
</style>
