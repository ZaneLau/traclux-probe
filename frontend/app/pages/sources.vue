<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import SourceTypeDonut from '~/components/engine/SourceTypeDonut.vue'
import SourcesTable from '~/components/engine/SourcesTable.vue'
import WorkbenchAnalysisToolbar from '~/components/workbench/WorkbenchAnalysisToolbar.vue'
import WorkbenchDataProvenanceCard from '~/components/workbench/WorkbenchDataProvenanceCard.vue'
import {
  buildWorkbenchPlatformOptions,
  useWorkbenchAnalysisFilters,
  type WorkbenchPlatformOption
} from '~/composables/useWorkbenchAnalysisFilters'
import { resolveNavPageTitleByPath } from '~/composables/useDashboardNav'
import { syncWorkbenchIdentity } from '~/composables/useWorkbenchIdentity'
import type { SourceType } from '~/types/engine'
import type {
  WorkbenchDataProvenanceCardQuery,
  WorkbenchCitationDomainDetailRow,
  WorkbenchCitationSourcesView
} from '~/types/workbench'

definePageMeta({
  layout: false,
  middleware: ['auth'],
  requiresAuth: true
})

useSeoMeta({
  title: `${resolveNavPageTitleByPath('/sources', '引用来源')} · Traclux 探镜`
})

interface SourceTableRow {
  id: string
  label: string
  rawLabel: string
  hostKey: string
  type: SourceType | 'unknown'
  used: number
  avgCitations: number
  share: number
  delta: number | null
  url?: string
}

interface SourceDonutSlice {
  key: string
  label: string
  value: number
  color: string
}

interface SourceSummaryCard {
  id: string
  label: string
  value: string
  hint: string
  metricKey?: string | null
  provenanceEnabled?: boolean
  domain?: string | null
  citationContext?: {
    citationCount?: number | null
    totalCitationCount?: number | null
    citationDomainCount?: number | null
    citationShare?: number | null
  }
}

interface SourceFocusPageRow {
  id: string
  title: string
  url: string
  domain?: string | null
  used: number | null
  share: number | null
  delta: number | null
}

const readableSourceNameMap: Record<string, string> = {
  'post.m.smzdm.com': '什么值得买',
  'post.smzdm.com': '什么值得买',
  'smzdm.com': '什么值得买'
}

function resolveReadableSourceLabel(label?: string | null, url?: string | null) {
  const rawLabel = typeof label === 'string' ? label.trim() : ''
  if (rawLabel && !rawLabel.includes('.')) {
    return rawLabel
  }
  const normalized = rawLabel.toLowerCase()
  if (normalized && readableSourceNameMap[normalized]) {
    return readableSourceNameMap[normalized]
  }
  if (typeof url === 'string' && url.trim()) {
    try {
      const hostname = new URL(url).hostname.toLowerCase()
      return readableSourceNameMap[hostname] || rawLabel || hostname
    } catch {
      return rawLabel
    }
  }
  return rawLabel
}

function resolveSourceHostKey(label?: string | null, url?: string | null) {
  const rawLabel = typeof label === 'string' ? label.trim().toLowerCase() : ''
  if (rawLabel.includes('.')) {
    return rawLabel
  }
  if (typeof url === 'string' && url.trim()) {
    try {
      return new URL(url).hostname.toLowerCase()
    } catch {
      return rawLabel
    }
  }
  return rawLabel
}

function formatPercentOrPlaceholder(value?: number | null) {
  return typeof value === 'number' ? `${value.toFixed(1)}%` : '--'
}

function formatSourceDelta(value: number | null) {
  if (value == null) {
    return '--'
  }
  return `${value >= 0 ? '+' : ''}${value.toFixed(1)}%`
}

function hasCitationRows(view?: WorkbenchCitationSourcesView | null) {
  return Boolean(view && ((view.domainRows?.length || 0) > 0 || (view.urlRows?.length || 0) > 0))
}

const route = useRoute()
const workbenchApi = useWorkbenchApi()
const brandSpaceDirectory = useWorkbenchBrandSpacesDirectory()
const {
  workbenchRef,
  workbenchScrolled,
  resetWorkbenchScrolled,
  syncWorkbenchScrolled
} = useWorkbenchShellState()

const keyword = ref('')
const selectedType = ref<'all' | SourceType>('all')
const selectedSourceId = ref<string | null>(null)
const workbenchSources = ref<WorkbenchCitationSourcesView | null>(null)
const workbenchLoading = ref(false)
const workbenchError = ref('')
const {
  provenanceCard,
  provenanceError,
  provenanceLoading,
  provenanceOpen,
  openProvenanceCard,
  openProvenanceSamplePage,
  retryProvenanceCard
} = useWorkbenchDataProvenanceCard()
const {
  buildRequest,
  customDateFrom,
  customDateTo,
  selectedPlatforms,
  selectedTimePreset,
  serializeRequest,
  syncPlatformSelection,
  timePresetOptions
} = useWorkbenchAnalysisFilters()
const sourcesListCardRef = ref<HTMLElement | null>(null)
const sourcesListCardHeight = ref<number | null>(null)
const sourceTypeOptions: Array<{ key: 'all' | SourceType, label: string }> = [
  { key: 'all', label: '全部类型' },
  { key: 'editorial', label: '编辑媒体' },
  { key: 'corporate', label: '企业站点' },
  { key: 'ugc', label: '社区 UGC' },
  { key: 'reference', label: '参考资料' },
  { key: 'you', label: '自有资产' }
]

function normalizeSourceType(type?: string | null): SourceType | 'unknown' {
  if (type === 'editorial' || type === 'corporate' || type === 'ugc' || type === 'reference' || type === 'you') {
    return type
  }
  return 'unknown'
}

const {
  brandSelectionOptions,
  requestedBrandMissingOption,
  requestedBrandSpaceMissing,
  selectedBrandRouteOptionKey,
  workbenchBoundBrandSpaceId
} = useWorkbenchBrandSelection(
  computed(() => typeof route.query.brandSpaceId === 'string' ? route.query.brandSpaceId : '')
)
const hasSelectedBrandSpace = computed(() => Boolean(workbenchBoundBrandSpaceId.value))
const pageError = computed(() => {
  if (requestedBrandSpaceMissing.value) {
    return '未找到对应品牌空间，请先回品牌管理确认品牌空间状态。'
  }
  return brandSpaceDirectory.error.value || ''
})
const sourcesAvailability = computed(() => workbenchSources.value?.dataAvailability || null)
const sourcesIsPartial = computed(() => sourcesAvailability.value?.status === 'partial')
const sourcesMissingFields = computed(() => sourcesAvailability.value?.missingFields || [])
const showBrandSpaceEmptyState = computed(() => {
  return brandSpaceDirectory.loaded.value
    && !brandSpaceDirectory.loading.value
    && !brandSpaceDirectory.error.value
    && !brandSelectionOptions.value.length
})
const showBrandSelectionPromptState = computed(() => {
  return brandSpaceDirectory.loaded.value
    && !brandSpaceDirectory.loading.value
    && !brandSpaceDirectory.error.value
    && brandSelectionOptions.value.length > 0
    && !requestedBrandSpaceMissing.value
    && !hasSelectedBrandSpace.value
})
const brandSelectRenderKey = computed(() => {
  const sourceMode = brandSpaceDirectory.items.value.length ? 'real' : 'empty'
  const optionKeys = brandSelectionOptions.value.map(item => item.key).join('|')
  const missingKey = requestedBrandMissingOption.value?.key || 'ok'
  return `${sourceMode}:${missingKey}:${optionKeys}`
})
const sourcesContentRenderKey = computed(() => {
  const brandKey = workbenchBoundBrandSpaceId.value || 'empty'
  const modeKey = workbenchSources.value
    ? `workbench:${workbenchSources.value.dataAvailability?.status || 'ready'}:${workbenchSources.value.domainRows?.length || 0}:${workbenchSources.value.urlRows?.length || 0}`
    : (hasSelectedBrandSpace.value ? 'pending' : 'empty')
  return `${brandKey}:${modeKey}`
})
const platformOptions = computed<WorkbenchPlatformOption[]>(() => {
  const analysisPlatforms = workbenchSources.value?.analysisContext?.availablePlatforms || []
  return buildWorkbenchPlatformOptions(analysisPlatforms)
})
const analysisRequest = computed(() => buildRequest(platformOptions.value))
const analysisRequestKey = computed(() => serializeRequest(analysisRequest.value))

function handleAnalysisTimePreset(value: string) {
  selectedTimePreset.value = value as typeof selectedTimePreset.value
}

const domainRows = computed<SourceTableRow[]>(() => {
  if (!workbenchSources.value) {
    return []
  }
  const baseRows = (workbenchSources.value.domainRows || []).map(item => {
    const label = resolveReadableSourceLabel(item.domain, item.url)
    const hostKey = resolveSourceHostKey(item.domain, item.url)
    return {
      id: item.id,
      label,
      rawLabel: label,
      hostKey,
      type: normalizeSourceType(item.type),
      used: item.used || 0,
      avgCitations: item.avgCitations || 0,
      share: item.share || 0,
      delta: item.delta ?? null,
      url: item.url || undefined
    }
  })
  const duplicateCounts = new Map<string, number>()
  baseRows.forEach((row) => {
    duplicateCounts.set(row.rawLabel, (duplicateCounts.get(row.rawLabel) || 0) + 1)
  })
  return baseRows.map((row) => ({
    ...row,
    label: (duplicateCounts.get(row.rawLabel) || 0) > 1 && row.hostKey && row.hostKey !== row.rawLabel
      ? `${row.rawLabel}（${row.hostKey}）`
      : row.rawLabel
  }))
})

const hasWorkbenchCitationRows = computed(() => hasCitationRows(workbenchSources.value))

const truthfullyEmptySources = computed(() =>
  Boolean(workbenchSources.value)
  && !hasWorkbenchCitationRows.value
)

const hasSpecificSourcesAvailabilityReason = computed(() => {
  const message = (sourcesAvailability.value?.message || '').trim()
  return Boolean(message)
})

const sourcesTruthEmptyReason = computed(() => {
  if (!truthfullyEmptySources.value) {
    return ''
  }
  if (hasSpecificSourcesAvailabilityReason.value) {
    return (sourcesAvailability.value?.message || '').trim()
  }
  return '最近一轮未产出可展示引用证据。'
})

const sourcesAvailabilityNotice = computed(() => {
  if (!sourcesIsPartial.value) {
    return ''
  }
  if (hasSpecificSourcesAvailabilityReason.value) {
    const message = (sourcesAvailability.value?.message || '').trim()
    if (message.includes('上一轮报告') || message.includes('新一轮生成中')) {
      return ''
    }
    return message
  }
  if (sourcesAvailability.value?.message) {
    return sourcesAvailability.value.message
  }
  if (truthfullyEmptySources.value) {
    return sourcesTruthEmptyReason.value
  }
  return '引用来源真实数据仍在补齐。'
})

const showSourcesMissingFields = computed(() =>
  sourcesMissingFields.value.length > 0 && !hasSpecificSourcesAvailabilityReason.value
)

const showSourcesPartialNotice = computed(() =>
  Boolean(sourcesAvailabilityNotice.value)
)

const urlRows = computed<SourceTableRow[]>(() => {
  if (!workbenchSources.value) {
    return []
  }
  return (workbenchSources.value.urlRows || []).map(item => ({
    id: item.id,
    label: item.title?.trim()
      || resolveReadableSourceLabel(item.domain, item.url)
      || item.url.replace(/^https?:\/\//, ''),
    rawLabel: item.title?.trim()
      || resolveReadableSourceLabel(item.domain, item.url)
      || item.url.replace(/^https?:\/\//, ''),
    hostKey: resolveSourceHostKey(item.domain, item.url),
    type: normalizeSourceType(item.type),
    used: item.used || 0,
    avgCitations: item.avgCitations || 0,
    share: item.share || 0,
    delta: item.delta ?? null,
    url: item.url
  }))
})

const tableRows = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase()

  return domainRows.value.filter((item) => {
    if (selectedType.value !== 'all' && item.type !== selectedType.value) {
      return false
    }
    if (!normalizedKeyword) {
      return true
    }
    return item.label.toLowerCase().includes(normalizedKeyword)
  })
})

const typeSlices = computed(() => {
  const realSlices = workbenchSources.value?.categorySlices || []
  if (!workbenchSources.value) {
    return []
  }
  if (realSlices.length) {
    return realSlices
      .map(item => ({
        key: item.key,
        label: item.label,
        value: item.share || 0
      }))
      .filter(item => item.value > 0)
      .sort((a, b) => b.value - a.value)
  }
  return []
})

const realTrendPoints = computed(() => workbenchSources.value?.trendPoints || [])
const realDomainDetails = computed(() => workbenchSources.value?.domainDetails || [])

const totalCitations = computed<number | null>(() => {
  if (!workbenchSources.value) {
    return null
  }
  if (typeof workbenchSources.value.summary?.totalCitations === 'number') {
    return workbenchSources.value.summary.totalCitations
  }
  return null
})

const donutSlices = computed<SourceDonutSlice[]>(() => {
  const colorMap: Record<string, string> = {
    editorial: '#3b82f6',
    corporate: '#f59e0b',
    ugc: '#14b8a6',
    reference: '#a855f7',
    you: '#22c55e'
  }

  return typeSlices.value.map(item => ({
    key: item.key,
    label: item.label,
    value: item.value,
    color: colorMap[item.key] || '#94a3b8'
  }))
})

const donutEmptyText = computed(() => {
  return truthfullyEmptySources.value
    ? sourcesTruthEmptyReason.value
    : (workbenchSources.value ? '来源结构摘要待后端补齐。' : '本轮还没有可展示的来源结构。')
})

const citationSummaryCards = computed<SourceSummaryCard[]>(() => {
  const summary = workbenchSources.value?.summary
  const total = totalCitations.value
  const rows = domainRows.value
  const ownedCount = sumSourceRows(rows.filter(row => row.type === 'you'))
  const ownedCountFallback = countFromShare(total, summary?.ownedShare)
  const resolvedOwnedCount = ownedCount > 0 ? ownedCount : ownedCountFallback
  const resolvedThirdPartyCount = total == null ? null : Math.max(total - (resolvedOwnedCount || 0), 0)
  const dominantRow = rows[0] || null
  const dominantLabel = summary?.dominantLabel?.trim() || '--'
  const dominantShare = typeof summary?.dominantShare === 'number'
    ? `${summary.dominantShare.toFixed(1)}%`
    : '--'
  const summaryGapHint = workbenchSources.value
    ? (truthfullyEmptySources.value
        ? sourcesTruthEmptyReason.value
        : '摘要指标待后端补齐，请以下方来源列表为准。')
    : (hasSelectedBrandSpace.value ? '暂无引用数据' : '')

  return [
    {
      id: 'total',
      label: '引用总量',
      value: total == null ? '--' : total.toLocaleString(),
      hint: summary?.dominantLabel ? `当前主导 ${summary.dominantLabel}` : summaryGapHint,
      metricKey: 'citationCount',
      provenanceEnabled: total != null,
      citationContext: {
        citationCount: total,
        totalCitationCount: total,
        citationDomainCount: rows.length,
        citationShare: total == null ? null : 100
      }
    },
    {
      id: 'owned',
      label: '自有引用',
      value: typeof summary?.ownedShare === 'number' ? formatPercentOrPlaceholder(summary.ownedShare) : '--',
      hint: typeof summary?.ownedShare === 'number' ? '品牌自有资产占比' : summaryGapHint,
      metricKey: 'ownedShare',
      provenanceEnabled: typeof summary?.ownedShare === 'number',
      citationContext: {
        citationCount: resolvedOwnedCount,
        totalCitationCount: total,
        citationDomainCount: rows.filter(row => row.type === 'you').length,
        citationShare: summary?.ownedShare ?? null
      }
    },
    {
      id: 'third-party',
      label: '第三方引用',
      value: typeof summary?.thirdPartyShare === 'number' ? formatPercentOrPlaceholder(summary.thirdPartyShare) : '--',
      hint: typeof summary?.thirdPartyShare === 'number' ? '媒体 / 社区 / 参考站点' : summaryGapHint,
      metricKey: 'thirdPartyShare',
      provenanceEnabled: typeof summary?.thirdPartyShare === 'number',
      citationContext: {
        citationCount: resolvedThirdPartyCount,
        totalCitationCount: total,
        citationDomainCount: rows.filter(row => row.type !== 'you').length,
        citationShare: summary?.thirdPartyShare ?? null
      }
    },
    {
      id: 'dominant',
      label: '主导结构',
      value: typeof summary?.dominantShare === 'number' && dominantLabel !== '--'
        ? `${dominantLabel} ${dominantShare}`
        : dominantLabel,
      hint: typeof summary?.dominantShare === 'number' ? '当前窗口引用占比最高的来源' : summaryGapHint,
      metricKey: 'citationShare',
      provenanceEnabled: typeof summary?.dominantShare === 'number',
      domain: dominantRow?.hostKey || dominantRow?.id || null,
      citationContext: {
        citationCount: dominantRow?.used ?? countFromShare(total, summary?.dominantShare),
        totalCitationCount: total,
        citationDomainCount: dominantRow ? 1 : null,
        citationShare: summary?.dominantShare ?? null
      }
    }
  ]
})

function sumSourceRows(rows: SourceTableRow[]) {
  return rows.reduce((sum, row) => sum + (row.used || 0), 0)
}

function countFromShare(total?: number | null, share?: number | null) {
  if (typeof total !== 'number' || typeof share !== 'number') {
    return null
  }
  return Math.round(total * share / 100)
}

const trendOption = computed<EChartsOption | null>(() => {
  if (!realTrendPoints.value.length) {
    return null
  }

  return {
    grid: { top: 24, right: 16, bottom: 28, left: 40, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#111827',
      borderColor: '#111827',
      textStyle: { color: '#f9fafb', fontSize: 11 },
      formatter: (rawParams: unknown) => {
        const params = Array.isArray(rawParams) ? rawParams as Array<{ dataIndex?: number, axisValueLabel?: string, value?: number }> : []
        const point = realTrendPoints.value[(params[0]?.dataIndex || 0)]
        const domains = (point?.topDomains || [])
          .map(item => `${item.domain} ${typeof item.share === 'number' ? `${item.share.toFixed(1)}%` : '--'}`)
          .join('<br/>')
        return [
          params[0]?.axisValueLabel || point?.label || '',
          `引用总量：${params[0]?.value ?? point?.totalCitations ?? 0}`,
          `自有引用：${formatPercentOrPlaceholder(point?.ownedShare)}`,
          `第三方引用：${formatPercentOrPlaceholder(point?.thirdPartyShare)}`,
          domains ? `主导来源：<br/>${domains}` : '主导来源：暂无'
        ].join('<br/>')
      }
    },
    xAxis: {
      type: 'category',
      data: realTrendPoints.value.map(item => item.label),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#6b7280', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#9ca3af', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f3f4f6' } }
    },
    series: [
      {
        name: '引用总量',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2, color: '#2563eb' },
        itemStyle: { color: '#2563eb' },
        areaStyle: { color: 'rgba(37, 99, 235, 0.08)' },
        data: realTrendPoints.value.map(item => item.totalCitations || 0)
      }
    ]
  }
})

const selectedSource = computed(() => {
  if (!selectedSourceId.value) {
    return null
  }
  return tableRows.value.find(item => item.id === selectedSourceId.value) || null
})

const selectedDomainDetail = computed<WorkbenchCitationDomainDetailRow | null>(() => {
  if (!selectedSourceId.value) {
    return null
  }
  return realDomainDetails.value.find(row => row.domainId === selectedSourceId.value) || null
})

const selectedSourceFocusRows = computed<SourceFocusPageRow[]>(() => {
  if (!selectedSource.value) {
    return []
  }

  if (selectedDomainDetail.value?.urls?.length) {
    return selectedDomainDetail.value.urls.map(item => ({
      id: item.urlId,
      title: item.title?.trim() || `${selectedSource.value?.label || '当前来源'}代表页面`,
      url: item.url,
      domain: resolveReadableSourceLabel(selectedDomainDetail.value?.domain, item.url),
      used: item.used ?? null,
      share: item.share ?? null,
      delta: item.delta ?? null
    }))
  }
  return []
})

const selectedSourceFocusTitle = computed(() => '代表页面')

const selectedSourceFocusEmptyText = computed(() => {
  if (truthfullyEmptySources.value) {
    return sourcesTruthEmptyReason.value
  }
  return '当前来源还没有可展开的代表页面。'
})

const sourceTableEmptyText = computed(() => {
  if (truthfullyEmptySources.value) {
    return sourcesTruthEmptyReason.value
  }
  return '暂无来源域名数据'
})

const showSourceFocusCard = computed(() => Boolean(selectedSource.value) || !tableRows.value.length)

const sourceFocusPlaceholderText = computed(() => {
  if (truthfullyEmptySources.value) {
    return sourcesTruthEmptyReason.value
  }
  return '先从左侧来源域名列表选择一个来源查看展开页。'
})

const trendPlaceholderCopy = computed(() => {
  return truthfullyEmptySources.value
    ? sourcesTruthEmptyReason.value
    : '本轮还没有可展示的引用趋势。'
})

const sourceListCaption = computed(() => {
  return '按域名排序'
})

const sourceFocusCardStyle = computed(() => {
  if (!sourcesListCardHeight.value) {
    return {}
  }
  return {
    height: `${sourcesListCardHeight.value}px`
  }
})

function syncSourcesListCardHeight() {
  sourcesListCardHeight.value = sourcesListCardRef.value
    ? Math.round(sourcesListCardRef.value.getBoundingClientRect().height)
    : null
}

let sourcesListCardObserver: ResizeObserver | null = null

watch(sourcesListCardRef, (element, previousElement) => {
  if (previousElement && sourcesListCardObserver) {
    sourcesListCardObserver.unobserve(previousElement)
  }
  if (element && sourcesListCardObserver) {
    sourcesListCardObserver.observe(element)
  }
  syncSourcesListCardHeight()
})

watch(tableRows, () => {
  if (!selectedSourceId.value) {
    selectedSourceId.value = tableRows.value[0]?.id || null
    return
  }
  if (selectedSourceId.value && !tableRows.value.some(item => item.id === selectedSourceId.value)) {
    selectedSourceId.value = tableRows.value[0]?.id || null
  }
}, { immediate: true })

onMounted(() => {
  syncSourcesListCardHeight()
  if (typeof ResizeObserver === 'undefined') {
    return
  }
  sourcesListCardObserver = new ResizeObserver(() => {
    syncSourcesListCardHeight()
  })
  if (sourcesListCardRef.value) {
    sourcesListCardObserver.observe(sourcesListCardRef.value)
  }
})

onBeforeUnmount(() => {
  sourcesListCardObserver?.disconnect()
  sourcesListCardObserver = null
})

async function loadWorkbenchSources() {
  workbenchError.value = ''

  const brandSpaceId = workbenchBoundBrandSpaceId.value
  const request = analysisRequest.value
  const previousView = workbenchSources.value
  const previousBrandSpaceId = previousView?.brandSpaceId || ''
  const switchingBrandSpace = Boolean(brandSpaceId && previousBrandSpaceId && previousBrandSpaceId !== brandSpaceId)
  if (!brandSpaceId) {
    workbenchSources.value = null
    return
  }
  if (!request) {
    return
  }

  workbenchLoading.value = true
  try {
    if (switchingBrandSpace) {
      workbenchSources.value = null
      selectedSourceId.value = null
    }
    const view = await workbenchApi.citationSources(brandSpaceId, request)
    workbenchSources.value = view
    syncWorkbenchIdentity(view)
    syncPlatformSelection(platformOptions.value)
  } catch (error) {
    workbenchError.value = error instanceof Error && error.message ? error.message : '引用来源真实数据加载失败'
    workbenchSources.value = switchingBrandSpace ? null : previousView
  } finally {
    workbenchLoading.value = false
  }
}

function onWorkbenchScroll(event: Event) {
  const target = event.target as HTMLElement | null
  syncWorkbenchScrolled(target)
}

function sourcesMetricLabel(metricKey?: string | null, fallback?: string | null) {
  if (fallback) {
    return fallback
  }
  const map: Record<string, string> = {
    ownedShare: '自有引用占比',
    thirdPartyShare: '第三方引用占比',
    citationShare: '引用占比',
    citationCount: '引用总量'
  }
  return map[String(metricKey || '').trim()] || '当前结果'
}

function openSourcesProvenance(
  anchorKey: string,
  metricKey?: string | null,
  metricValue?: string | null,
  metricLabel?: string | null,
  domain?: string | null,
  citationContext?: {
    citationCount?: number | null
    totalCitationCount?: number | null
    citationDomainCount?: number | null
    citationShare?: number | null
  }
) {
  const brandSpaceId = workbenchBoundBrandSpaceId.value
  if (!brandSpaceId) {
    return
  }
  const context = analysisRequest.value
  const citationCount = citationContext?.citationCount ?? totalCitations.value
  const citationDomainCount = citationContext?.citationDomainCount ?? workbenchSources.value?.domainRows?.length ?? null
  const query: WorkbenchDataProvenanceCardQuery = {
    brandSpaceId,
    ...(context || { timePreset: selectedTimePreset.value }),
    runId: null,
    anchorType: 'citation_source_point',
    anchorKey,
    metricKey: metricKey || null,
    metricLabel: sourcesMetricLabel(metricKey, metricLabel),
    metricValue: metricValue || null,
    domain: domain || null,
    resultPointKey: `citation_source_point:${anchorKey}:${metricKey || 'metric'}`,
    contextCitationCount: citationCount,
    contextCitationDomainCount: citationDomainCount,
    contextTotalCitationCount: citationContext?.totalCitationCount ?? null,
    contextCitationShare: citationContext?.citationShare ?? null
  }
  void openProvenanceCard(query)
}

function openSourceRowProvenance(row: { id: string, label: string, share: number, used: number, hostKey?: string }) {
  openSourcesProvenance(
    row.hostKey || row.id,
    'citationShare',
    `${row.share.toFixed(1)}%`,
    `${row.label} 引用占比`,
    row.hostKey || row.id,
    {
      citationCount: row.used,
      totalCitationCount: totalCitations.value,
      citationDomainCount: 1,
      citationShare: row.share
    }
  )
}

onMounted(() => {
  resetWorkbenchScrolled()
  void brandSpaceDirectory.ensureLoaded().catch(() => {
    // Error state is exposed through the directory composable.
  })
  if (showBrandSpaceEmptyState.value || showBrandSelectionPromptState.value) {
    return
  }
  void loadWorkbenchSources()
})

watch([workbenchBoundBrandSpaceId, analysisRequestKey], () => {
  void loadWorkbenchSources()
})
</script>

<template>
  <main class="dashboard-shell">
    <GeoWorkbenchSidebar />

    <section
      ref="workbenchRef"
      class="workbench"
      @scroll.passive="onWorkbenchScroll"
    >
      <div
        class="workbench-sticky-layer"
        :class="{ 'workbench-sticky-layer--scrolled': workbenchScrolled }"
      >
        <header class="overview-head">
          <div class="brand-zone">
            <h1>
              <WikiGeoWikiTrigger entrypoint-id="sources.page.title">
                {{ resolveNavPageTitleByPath('/sources', '引用来源') }}
              </WikiGeoWikiTrigger>
            </h1>
            <label
              class="brand-select-wrap"
              for="sources-brand-select"
            >
              <select
                id="sources-brand-select"
                :key="brandSelectRenderKey"
                v-model="selectedBrandRouteOptionKey"
                class="select-input"
              >
                <option
                  value=""
                  disabled
                >
                  {{ brandSelectionOptions.length ? '选择品牌空间' : '暂无品牌空间' }}
                </option>
                <option
                  v-if="requestedBrandMissingOption"
                  :value="requestedBrandMissingOption.key"
                  disabled
                >
                  {{ requestedBrandMissingOption.label }}
                </option>
                <option
                  v-for="brand in brandSelectionOptions"
                  :key="`source-brand-${brand.key}`"
                  :value="brand.key"
                >
                  {{ brand.label }}
                </option>
              </select>
            </label>
          </div>
        </header>

        <WorkbenchAnalysisToolbar
          v-if="!pageError && !showBrandSpaceEmptyState && !showBrandSelectionPromptState"
          :time-preset="selectedTimePreset"
          :date-from="customDateFrom"
          :date-to="customDateTo"
          :time-options="timePresetOptions"
          :platform-options="platformOptions"
          :selected-platforms="selectedPlatforms"
          @update:time-preset="handleAnalysisTimePreset"
          @update:date-from="customDateFrom = $event"
          @update:date-to="customDateTo = $event"
          @update:selected-platforms="selectedPlatforms = $event"
        />
      </div>

      <div
        v-if="pageError"
        class="state-row state-row--error"
      >
        {{ pageError }}
      </div>
      <div
        v-else-if="showBrandSelectionPromptState"
        class="state-row"
      >
        先选择品牌空间，再查看这个品牌本轮真实产出的引用来源。
      </div>
      <div
        v-else-if="showBrandSpaceEmptyState"
        class="empty-state"
      >
        <strong>还没有品牌空间</strong>
        <span>先新增品牌，再进入引用来源查看真实来源结果。</span>
      </div>

      <div
        v-if="!pageError && !showBrandSpaceEmptyState && !showBrandSelectionPromptState"
        :key="sourcesContentRenderKey"
        class="sources-content"
      >
        <div
          v-if="workbenchLoading"
          class="state-row state-row--muted"
        >
          正在加载引用来源真实数据...
        </div>
        <div
          v-else-if="workbenchError"
          class="state-row state-row--muted"
        >
          {{ workbenchError }}
        </div>
        <div
          v-else-if="showSourcesPartialNotice"
          class="state-row state-row--muted"
        >
          {{ sourcesAvailabilityNotice }}<template v-if="showSourcesMissingFields">
            缺口：{{ sourcesMissingFields.join('、') }}
          </template>
        </div>

        <section class="sources-summary-grid">
          <article
            v-for="card in citationSummaryCards"
            :key="`citation-summary-${card.id}`"
            class="surface section-surface summary-metric-card"
          >
            <span>{{ card.label }}</span>
            <strong>{{ card.value }}</strong>
            <small>{{ card.hint }}</small>
          </article>
        </section>

        <section class="sources-top-grid">
          <article class="surface section-surface sources-trend-card">
            <div class="surface-head surface-head--compact">
              <div class="title-with-help">
                <strong>引用来源趋势</strong>
              </div>
            </div>
            <div
              v-if="!trendOption"
              class="state-block"
            >
              {{ trendPlaceholderCopy }}
            </div>
            <GeoLazyEChart
              v-else
              :option="trendOption"
              :height="260"
              empty-text="暂无趋势数据"
            />
          </article>

          <article class="surface section-surface sources-donut-card">
            <div class="surface-head">
              <div class="surface-head__title">
                <div class="title-with-help">
                  <strong>域名类型分布</strong>
                </div>
              </div>
            </div>
            <div
              v-if="!donutSlices.length || totalCitations == null"
              class="state-block"
            >
              {{ donutEmptyText }}
            </div>
            <SourceTypeDonut
              v-else
              :total="totalCitations"
              :slices="donutSlices"
              total-label="引用总量"
            />
          </article>
        </section>

        <section class="sources-main-grid">
          <div
            ref="sourcesListCardRef"
            class="surface section-surface sources-list-card"
          >
            <div class="surface-head surface-head--compact">
              <div class="surface-head__title">
                <div class="title-with-help">
                  <strong>
                    <WikiGeoWikiTrigger entrypoint-id="sources.object.source_domain">
                      来源域名列表
                    </WikiGeoWikiTrigger>
                  </strong>
                </div>
                <span>{{ sourceListCaption }}</span>
              </div>
              <div class="sources-list-card__head-actions">
                <label class="prompt-search">
                  <span class="prompt-search__icon">⌕</span>
                  <input
                    v-model="keyword"
                    type="search"
                    placeholder="搜索域名"
                  >
                </label>
                <select
                  v-model="selectedType"
                  class="select-input filter-select filter-select--narrow"
                >
                  <option
                    v-for="type in sourceTypeOptions"
                    :key="`source-type-${type.key}`"
                    :value="type.key"
                  >
                    {{ type.label }}
                  </option>
                </select>
                <span>{{ tableRows.length }}</span>
              </div>
            </div>
            <SourcesTable
              :rows="tableRows"
              :visible-rows="10"
              :empty-text="sourceTableEmptyText"
              share-entrypoint-id="sources.metric.citation_share"
              @select="selectedSourceId = $event"
              @open-provenance="openSourceRowProvenance"
            />
          </div>

          <section
            v-if="showSourceFocusCard"
            class="surface section-surface source-focus-card"
            :style="sourceFocusCardStyle"
          >
            <div class="surface-head surface-head--compact">
              <div class="surface-head__title">
                <div class="title-with-help">
                  <strong>来源展开</strong>
                </div>
                <span>{{ selectedSource?.label || '暂无可展开来源' }}</span>
              </div>
            </div>
            <div class="source-focus-section">
              <div
                v-if="selectedSource"
                class="surface-head surface-head--compact"
              >
                <strong>{{ selectedSourceFocusTitle }}</strong>
                <span>{{ selectedSourceFocusRows.length }}</span>
              </div>
              <div
                v-if="!selectedSource"
                class="state-block state-block--compact"
              >
                {{ sourceFocusPlaceholderText }}
              </div>
              <div
                v-else-if="!selectedSourceFocusRows.length"
                class="state-block state-block--compact"
              >
                {{ selectedSourceFocusEmptyText }}
              </div>
              <div
                v-else
                class="source-focus-link-list"
              >
                <a
                  v-for="row in selectedSourceFocusRows"
                  :key="`selected-source-focus-${row.id}`"
                  class="source-focus-link-card"
                  :href="row.url"
                  target="_blank"
                  rel="noreferrer"
                >
                  <div class="source-focus-link-card__meta">
                    <span v-if="row.domain">{{ row.domain }}</span>
                    <span v-if="row.used != null">{{ row.used }} 次</span>
                    <span v-if="row.share != null">{{ row.share.toFixed(1) }}%</span>
                    <span
                      v-if="row.delta != null"
                      :class="row.delta >= 0 ? 'delta-up' : 'delta-down'"
                    >
                      {{ formatSourceDelta(row.delta) }}
                    </span>
                  </div>
                  <strong>{{ row.title }}</strong>
                  <span class="source-focus-link-card__url">{{ row.url.replace(/^https?:\/\//, '') }}</span>
                </a>
              </div>
            </div>
          </section>
        </section>
      </div>
    </section>
    <WorkbenchDataProvenanceCard
      v-model="provenanceOpen"
      :loading="provenanceLoading"
      :error="provenanceError"
      :card="provenanceCard"
      @retry="retryProvenanceCard"
      @sample-page-change="openProvenanceSamplePage"
    />
  </main>
</template>

<style scoped src="~/assets/css/engine-shell.css"></style>

<style scoped>
.sources-content {
  display: grid;
}

.sources-top-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
}

.sources-summary-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 10px;
}

.summary-metric-card span {
  font-size: 12px;
  color: #6b7280;
}

.summary-metric-card strong {
  font-size: 22px;
  line-height: 1.05;
  color: #111827;
}

.provenance-value-trigger {
  appearance: none;
  width: fit-content;
  border: 0;
  border-bottom: 1px dotted currentColor;
  background: linear-gradient(180deg, rgba(20, 184, 166, 0.08), rgba(20, 184, 166, 0.15));
  color: #111827;
  cursor: zoom-in;
  font: inherit;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.05;
  padding: 0 2px;
  text-align: left;
}

.provenance-value-trigger:hover {
  background: rgba(20, 184, 166, 0.2);
}

.summary-metric-card small {
  font-size: 12px;
  color: #6b7280;
}

.title-with-help {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.surface-head a {
  color: #2563eb;
  text-decoration: none;
}

.surface-head a:hover {
  text-decoration: underline;
}

.sources-main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(320px, 0.9fr);
  gap: 10px;
  margin-top: 10px;
  align-items: start;
}

.sources-list-card {
  min-width: 0;
}

.sources-list-card__head-actions {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.sources-list-card__head-actions > span {
  font-size: 12px;
  color: #6b7280;
}

.source-focus-card {
  gap: 10px;
  grid-template-rows: auto minmax(0, 1fr);
  min-height: 0;
  overflow: hidden;
}

.source-focus-section {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 8px;
  min-height: 0;
}

.source-focus-link-list {
  display: grid;
  gap: 8px;
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
  scrollbar-gutter: stable;
}

.source-focus-link-card {
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  background: #ffffff;
  padding: 10px;
  display: grid;
  gap: 6px;
  color: #111827;
  text-decoration: none;
}

.source-focus-link-card:hover {
  border-color: #bfdbfe;
  background: #f8fbff;
}

.source-focus-link-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #6b7280;
}

.source-focus-link-card strong {
  font-size: 14px;
  line-height: 1.5;
}

.source-focus-link-card__url {
  font-size: 12px;
  color: #6b7280;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.state-block--compact {
  min-height: 48px;
}

.delta-up {
  color: #047857;
}

.delta-down {
  color: #b91c1c;
}

.delta-muted {
  color: #9ca3af;
}

@media (max-width: 1460px) {
  .sources-summary-grid,
  .sources-top-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1180px) {
  .sources-main-grid {
    grid-template-columns: 1fr;
  }

}
</style>
