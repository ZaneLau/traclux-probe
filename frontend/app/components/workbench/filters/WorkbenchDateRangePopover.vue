<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getLocalTimeZone, parseDate, today, type CalendarDate, type DateValue } from '@internationalized/date'

type CalendarRange = {
  start: CalendarDate
  end: CalendarDate
} | null

type CalendarEventRange = {
  start?: DateValue | null
  end?: DateValue | null
} | null

type PresetOption = {
  key: string
  label: string
}

const props = withDefaults(defineProps<{
  dateFrom?: string
  dateTo?: string
  boundaryRef?: HTMLElement | null
  placeholder?: string
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl'
  buttonClass?: string
  panelClass?: string
  presetOptions?: PresetOption[]
  disabled?: boolean
  open?: boolean
  hideTrigger?: boolean
}>(), {
  dateFrom: '',
  dateTo: '',
  boundaryRef: null,
  placeholder: '选择日期范围',
  size: 'md',
  buttonClass: '',
  panelClass: '',
  presetOptions: () => [],
  disabled: false,
  open: false,
  hideTrigger: false
})

const emit = defineEmits<{
  'update:dateFrom': [value: string]
  'update:dateTo': [value: string]
  'update:open': [value: boolean]
  'select-preset': [value: string]
}>()

const localOpen = ref(false)
const containerRef = ref<HTMLElement | null>(null)

function normalizeDate(value?: string) {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value.trim()) ? value.trim() : ''
}

function toCalendarDate(value?: string) {
  const normalized = normalizeDate(value)
  return normalized ? parseDate(normalized) : null
}

function formatDateValue(value?: CalendarDate | null) {
  if (!value || typeof value.toDate !== 'function') {
    return ''
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  }).format(value.toDate(getLocalTimeZone()))
}

function formatDateString(value: CalendarDate) {
  const month = `${value.month}`.padStart(2, '0')
  const day = `${value.day}`.padStart(2, '0')
  return `${value.year}-${month}-${day}`
}

const internalValue = computed<CalendarRange>({
  get: () => {
    const start = toCalendarDate(props.dateFrom)
    const end = toCalendarDate(props.dateTo)
    return start && end ? { start, end } : null
  },
  set: (value) => {
    emit('update:dateFrom', value?.start ? formatDateString(value.start) : '')
    emit('update:dateTo', value?.end ? formatDateString(value.end) : '')
  }
})

watch(() => props.open, async (value) => {
  localOpen.value = value
}, { immediate: true })

watch(localOpen, (value) => {
  if (value !== props.open) {
    emit('update:open', value)
  }
})

function handleDocumentPointerDown(event: PointerEvent) {
  if (!localOpen.value) {
    return
  }

  const target = event.target
  if (!(target instanceof Node)) {
    return
  }

  if (containerRef.value?.contains(target)) {
    return
  }

  if (props.boundaryRef?.contains(target)) {
    return
  }

  localOpen.value = false
}

onMounted(() => {
  if (import.meta.client) {
    window.addEventListener('pointerdown', handleDocumentPointerDown)
  }
})

onBeforeUnmount(() => {
  if (import.meta.client) {
    window.removeEventListener('pointerdown', handleDocumentPointerDown)
  }
})

const rangeLabel = computed(() => {
  const start = toCalendarDate(props.dateFrom)
  const end = toCalendarDate(props.dateTo)
  if (!start || !end) {
    return props.placeholder
  }
  return `${formatDateValue(start)} - ${formatDateValue(end)}`
})

function applyPreset(days: number) {
  const end = today(getLocalTimeZone())
  const start = end.subtract({ days: Math.max(days - 1, 0) })
  emit('update:dateFrom', formatDateString(start))
  emit('update:dateTo', formatDateString(end))
  localOpen.value = false
}

function selectPreset(key: string) {
  if (key === 'today') {
    applyPreset(1)
    emit('select-preset', key)
    return
  }
  if (key === 'yesterday') {
    const end = today(getLocalTimeZone()).subtract({ days: 1 })
    emit('update:dateFrom', formatDateString(end))
    emit('update:dateTo', formatDateString(end))
    localOpen.value = false
    emit('select-preset', key)
    return
  }
  if (key === 'last_7_days') {
    applyPreset(7)
    emit('select-preset', key)
    return
  }
  if (key === 'last_14_days') {
    applyPreset(14)
    emit('select-preset', key)
    return
  }
  if (key === 'last_30_days') {
    applyPreset(30)
    emit('select-preset', key)
    return
  }

  emit('select-preset', key)
  localOpen.value = false
}

function handleToggle() {
  if (props.disabled) {
    return
  }
  localOpen.value = !localOpen.value
}

function isCalendarDate(value?: DateValue | null): value is CalendarDate {
  return Boolean(
    value
    && typeof value === 'object'
    && 'calendar' in value
    && 'era' in value
    && 'year' in value
    && 'month' in value
    && 'day' in value
  )
}

function handleCalendarValue(value: CalendarEventRange) {
  const normalized = value && isCalendarDate(value.start) && isCalendarDate(value.end)
    ? { start: value.start, end: value.end }
    : null

  internalValue.value = normalized

  if (normalized?.start && normalized?.end) {
    localOpen.value = false
  }
}
</script>

<template>
  <div
    ref="containerRef"
    :class="['date-range-popover', { 'date-range-popover--panel-only': hideTrigger }]"
  >
    <UButton
      v-if="!hideTrigger"
      color="neutral"
      variant="ghost"
      :size="size"
      icon="i-lucide-calendar-range"
      trailing-icon="i-lucide-chevron-down"
      :label="rangeLabel"
      :disabled="disabled"
      :class="`!rounded-none ${buttonClass}`"
      @click="handleToggle"
    />

    <div
      v-if="localOpen"
      :class="['date-range-popover__panel', panelClass]"
    >
      <div class="space-y-4">
        <div class="flex items-center justify-between gap-4">
          <div>
            <p class="text-xs font-semibold tracking-[0.16em] text-slate-400 uppercase">
              自定义时间
            </p>
            <p class="mt-1 text-sm text-slate-600">
              直接在日历里选开始和结束日期。
            </p>
          </div>
        </div>

        <div
          v-if="presetOptions.length"
          class="flex flex-wrap gap-2"
        >
          <UButton
            v-for="option in presetOptions"
            :key="option.key"
            color="neutral"
            variant="ghost"
            size="xs"
            :label="option.label"
            class="!rounded-none !text-slate-600 hover:!bg-slate-100 hover:!text-slate-900"
            @click="selectPreset(option.key)"
          />
        </div>

        <UCalendar
          v-model="internalValue"
          range
          color="neutral"
          variant="solid"
          size="sm"
          :number-of-months="2"
          paged-navigation
          :month-controls="true"
          :year-controls="false"
          :prev-month="{ class: '!rounded-none !text-slate-700 hover:!bg-slate-100 hover:!text-slate-950' }"
          :next-month="{ class: '!rounded-none !text-slate-700 hover:!bg-slate-100 hover:!text-slate-950' }"
          class="rounded-none border border-slate-200 bg-slate-50/80 p-3"
          :ui="{
            header: 'mb-1 flex items-center justify-between',
            heading: 'text-sm font-medium text-slate-800',
            headCell: 'text-[12px] font-medium text-slate-500',
            cellTrigger: 'm-0.5 flex size-8 items-center justify-center rounded-none text-slate-800 transition data-[selected]:bg-slate-700 data-[selected]:text-white data-[highlighted]:bg-slate-200 hover:not-data-[selected]:bg-slate-100 focus-visible:ring-2 focus-visible:ring-slate-300',
            grid: 'w-full border-collapse select-none space-y-1 focus:outline-none',
            gridRow: 'grid grid-cols-7 place-items-center',
            body: 'flex flex-col gap-4 pt-3 sm:flex-row sm:gap-4'
          }"
          @update:model-value="handleCalendarValue"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.date-range-popover {
  position: relative;
}

.date-range-popover__panel {
  position: absolute;
  top: calc(100% + 10px);
  left: 0;
  z-index: 30;
  width: max-content;
  max-width: min(100vw - 32px, 720px);
  border: 1px solid #e2e8f0;
  background: #ffffff;
  padding: 16px;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.12);
}

.date-range-popover--panel-only .date-range-popover__panel {
  top: 10px;
}

.date-range-popover--panel-only {
  position: absolute;
  top: 100%;
  left: 0;
  z-index: 40;
}
</style>
