import { computed } from 'vue'

export type WorkbenchTimePreset =
  | 'today'
  | 'yesterday'
  | 'last_7_days'
  | 'last_14_days'
  | 'last_30_days'
  | 'custom'

export interface WorkbenchFilterOption {
  key: string
  label: string
  count?: number
}

export interface WorkbenchPlatformOption {
  key: string
  label: string
  count?: number
}

export interface WorkbenchAnalysisRequest {
  timePreset?: string
  dateFrom?: string
  dateTo?: string
  platforms?: string[]
  comparison?: string
  frequency?: string
}

const timePresetOptions: WorkbenchFilterOption[] = [
  { key: 'today', label: '今天' },
  { key: 'yesterday', label: '昨天' },
  { key: 'last_7_days', label: '近 7 天' },
  { key: 'last_14_days', label: '近 14 天' },
  { key: 'last_30_days', label: '近 30 天' },
  { key: 'custom', label: '自定义' }
]

function normalizeDateValue(value?: string | null) {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value.trim()) ? value.trim() : ''
}

function uniqueValues(values: string[]) {
  return Array.from(new Set(values.filter(Boolean)))
}

function normalizePlatformValue(value?: string | null) {
  return typeof value === 'string' ? value.trim() : ''
}

function formatDateValue(date: Date) {
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
}

function buildRelativeRange(days: number, endOffsetDays = 0) {
  const end = new Date()
  end.setHours(0, 0, 0, 0)
  end.setDate(end.getDate() - endOffsetDays)
  const start = new Date(end)
  start.setDate(start.getDate() - Math.max(days - 1, 0))
  return {
    dateFrom: formatDateValue(start),
    dateTo: formatDateValue(end)
  }
}

function resolvePresetRange(preset: WorkbenchTimePreset) {
  if (preset === 'today') {
    return buildRelativeRange(1)
  }
  if (preset === 'yesterday') {
    return buildRelativeRange(1, 1)
  }
  if (preset === 'last_7_days') {
    return buildRelativeRange(7)
  }
  if (preset === 'last_14_days') {
    return buildRelativeRange(14)
  }
  if (preset === 'last_30_days') {
    return buildRelativeRange(30)
  }
  return null
}

export function buildWorkbenchPlatformOptions(values: Array<string | null | undefined>) {
  const normalizedValues = uniqueValues(values.map(normalizePlatformValue))
  return normalizedValues.map((key) => ({
    key,
    label: key
  }))
}

export function useWorkbenchAnalysisFilters() {
  const demoMode = useRuntimeConfig().public.demoMode === true
  const defaultPreset: WorkbenchTimePreset = demoMode ? 'custom' : 'last_14_days'
  const selectedTimePreset = useState<WorkbenchTimePreset>('workbench-analysis-time-preset', () => defaultPreset)
  const customDateFrom = useState<string>('workbench-analysis-date-from', () => demoMode ? '2026-10-07' : '')
  const customDateTo = useState<string>('workbench-analysis-date-to', () => demoMode ? '2026-10-20' : '')
  const selectedPlatforms = useState<string[]>('workbench-analysis-platforms', () => [])

  const hasCustomRange = computed(() => selectedTimePreset.value === 'custom')
  const customRangeReady = computed(() => {
    if (!hasCustomRange.value) {
      return true
    }
    return Boolean(normalizeDateValue(customDateFrom.value) && normalizeDateValue(customDateTo.value))
  })

  function normalizePlatformSelection(platformOptions: WorkbenchPlatformOption[]) {
    const availableKeys = platformOptions.map(option => option.key)
    const normalized = uniqueValues(selectedPlatforms.value.filter(item => availableKeys.includes(item)))
    return normalized.length === availableKeys.length ? [] : normalized
  }

  function syncPlatformSelection(platformOptions: WorkbenchPlatformOption[]) {
    const normalized = normalizePlatformSelection(platformOptions)
    if (normalized.join('|') !== selectedPlatforms.value.join('|')) {
      selectedPlatforms.value = normalized
    }
  }

  function buildRequest(platformOptions: WorkbenchPlatformOption[]): WorkbenchAnalysisRequest | null {
    if (!customRangeReady.value) {
      return null
    }
    const request: WorkbenchAnalysisRequest = {
      timePreset: selectedTimePreset.value
    }
    if (selectedTimePreset.value === 'custom') {
      request.dateFrom = normalizeDateValue(customDateFrom.value)
      request.dateTo = normalizeDateValue(customDateTo.value)
    }
    const normalizedPlatforms = normalizePlatformSelection(platformOptions)
    if (normalizedPlatforms.length) {
      request.platforms = normalizedPlatforms
    }
    return request
  }

  function serializeRequest(request: WorkbenchAnalysisRequest | null) {
    if (!request) {
      return 'pending'
    }
    return JSON.stringify({
      timePreset: request.timePreset || '',
      dateFrom: request.dateFrom || '',
      dateTo: request.dateTo || '',
      platforms: request.platforms || []
    })
  }

  return {
    customDateFrom,
    customDateTo,
    customRangeReady,
    hasCustomRange,
    selectedPlatforms,
    selectedTimePreset,
    serializeRequest,
    syncPlatformSelection,
    timePresetOptions,
    buildRequest
  }
}
