<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import type {
  Granularity,
  PromptColumnKey,
  PromptColumnOption,
  PromptMetricRow,
  PromptTopicGroup
} from '~/types/engine'
import type {
  WorkbenchProblemAnalysisOpportunityPromptRow,
  WorkbenchProblemAnalysisPlatformDemandRow,
  WorkbenchProblemAnalysisRichView,
  WorkbenchProblemAnalysisVolumeSummary,
  WorkbenchProblemAnalysisVolumeTopicSliceRow,
  WorkbenchProblemAnalysisVolumeTrendPoint
} from '~/types/workbench'
import { resolveProductModelDisplayLabel } from '~/utils/productModels'

type RichViewTab = 'volume' | 'intent' | 'roles' | 'opportunity' | 'records'
type ChartSeriesClickPayload = { dataIndex?: number | null }

interface PromptProbeLookupItem {
  id: string
  promptId?: string | null
  topic: string
  prompt: string
}

interface ProblemAnalysisRecordRow {
  id: string
  createdAtLabel: string
  createdDateLabel: string
  createdTimeLabel: string
  platformLabel: string
  query: string
  citationCount: number
  probeId?: string | null
}

const props = defineProps<{
  selectedBrand: string
  hasPromptCoverage: boolean
  promptTotalCount: number
  promptTotalLabel?: string
  emptyCoverageMessage?: string
  promptTopicGroups: PromptTopicGroup[]
  promptTableGridStyle: Record<string, string>
  promptVisibleColumns: PromptColumnOption[]
  togglePromptTopic: (topic: string) => void
  isPromptTopicExpanded: (topic: string) => boolean
  resolvePromptCellValue: (row: PromptMetricRow, key: PromptColumnKey) => string
  resolvePromptCellDelta: (row: PromptMetricRow, key: PromptColumnKey) => string | null
  resolvePromptDeltaClass: (row: PromptMetricRow, key: PromptColumnKey) => string
  probes?: PromptProbeLookupItem[]
  selectedPlatforms?: string[]
  selectedTopics?: string[]
  platformDisplayMap?: Record<string, string>
  windowStartMs?: number | null
  granularity: Granularity
  rich?: WorkbenchProblemAnalysisRichView | null
  recordRows?: ProblemAnalysisRecordRow[]
  recordEmptyText?: string
  recordEvidencePending?: boolean
}>()

const emit = defineEmits<{
  (event: 'openProbeDetail', probeId: string): void
  (event: 'openChatEvidence', chatId: string): void
  (event: 'openProvenance', anchorKey: string, metricKey: string, probeId?: string | null, topicId?: string | null, metricValue?: string | null, metricLabel?: string | null, platform?: string | null, windowKey?: string | null, contextCitationCount?: number | null, contextDemandVolume?: number | null, contextDemandTotal?: number | null, contextActiveTopicCount?: number | null, contextActivePlatformCount?: number | null, contextPromptCount?: number | null, contextTotalProjection?: number | null, contextHighValuePromptCount?: number | null, contextPromptLoopTotalCount?: number | null, contextPromptLoopCoveredCount?: number | null, contextPromptLoopGapCount?: number | null, contextPromptLoopHighOpportunityCount?: number | null): void
}>()

const activeView = ref<RichViewTab>('volume')
const brandGapOnly = ref(false)
const minOpportunity = ref(60)
const promptOpportunitySearch = ref('')
const selectedResearchPrompts = ref<string[]>([])
const expandedHierarchyTopics = ref<string[]>([])
const fanoutSearch = ref('')
const expandedFanoutPromptIds = ref<string[]>([])
const focusedFanoutPromptId = ref<string | null>(null)

const tabs: Array<{ key: RichViewTab, label: string }> = [
  { key: 'volume', label: '量级' },
  { key: 'intent', label: '意图' },
  { key: 'roles', label: '角色' },
  { key: 'opportunity', label: '机会' },
  { key: 'records', label: '记录' }
]

const roleView = computed(() => props.rich?.roles || props.rich?.audience || null)
const demandMiningView = computed(() => props.rich?.demandMining || props.rich?.hierarchy || null)
const opportunityView = computed(() => props.rich?.opportunity || props.rich?.fanout || null)

const promptContextLabel = computed(() => {
  const brand = props.selectedBrand?.trim()
  const countLabel = `${props.promptTotalCount} ${props.promptTotalLabel || '条问题'}`.trim()
  return brand ? `${brand} · ${countLabel}` : countLabel
})

const promptSeedByKey = computed(() => {
  const map = new Map<string, PromptProbeLookupItem>()
  for (const seed of props.probes || []) {
    map.set(`${seed.topic}::${seed.prompt}`, seed)
  }
  return map
})

function resolveProbeSeed(row: PromptMetricRow) {
  if (row.id) {
    const matchedByPromptId = (props.probes || []).find(item => item.promptId && item.promptId === row.id)
    if (matchedByPromptId) {
      return matchedByPromptId
    }
  }

  return promptSeedByKey.value.get(`${row.topic}::${row.prompt}`)
    || (props.probes || []).find(item => item.prompt === row.prompt)
    || null
}

const selectedPlatformSet = computed(() => new Set((props.selectedPlatforms || []).filter(Boolean)))
const selectedTopicSet = computed(() => new Set((props.selectedTopics || []).filter(Boolean)))
const visiblePromptTopicSet = computed(() => new Set(props.promptTopicGroups.map(group => group.topic).filter(Boolean)))
const hasExplicitTopicFilter = computed(() =>
  selectedTopicSet.value.size > 0
  && visiblePromptTopicSet.value.size > 0
  && selectedTopicSet.value.size < visiblePromptTopicSet.value.size
)

function matchesSelectedPlatform(platform?: string | null) {
  if (!selectedPlatformSet.value.size) {
    return true
  }
  const label = resolvePlatformDisplay(platform)
  if (!label) {
    return false
  }
  return selectedPlatformSet.value.has(label)
}

function resolvePlatformDisplay(platform?: string | null) {
  return resolveProductModelDisplayLabel(platform, props.platformDisplayMap)
}

function matchesSelectedTopic(topic?: string | null) {
  if (!selectedTopicSet.value.size) {
    return true
  }
  if (!topic || topic === '未分组问题') {
    return !hasExplicitTopicFilter.value
  }
  return selectedTopicSet.value.has(topic)
}

const selectedVolumeSliceKey = ref<string | null>(null)

function resolveVolumePointTimestamp(value?: string | null) {
  if (!value) {
    return null
  }
  const timestamp = new Date(value).getTime()
  return Number.isFinite(timestamp) ? timestamp : null
}

function startOfWeek(date: Date) {
  const copy = new Date(date)
  copy.setHours(0, 0, 0, 0)
  const day = copy.getDay()
  const offset = day === 0 ? -6 : 1 - day
  copy.setDate(copy.getDate() + offset)
  return copy
}

function resolveVolumeBucketKey(date: Date, granularity: Granularity) {
  if (granularity === 'month') {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
  }
  if (granularity === 'week') {
    const weekStart = startOfWeek(date)
    return `week-${weekStart.getFullYear()}-${String(weekStart.getMonth() + 1).padStart(2, '0')}-${String(weekStart.getDate()).padStart(2, '0')}`
  }
  return `day-${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function formatVolumeBucketLabel(date: Date, granularity: Granularity) {
  if (granularity === 'month') {
    return `${date.getMonth() + 1} 月`
  }
  if (granularity === 'week') {
    const weekStart = startOfWeek(date)
    return `${weekStart.getMonth() + 1}/${weekStart.getDate()} 周`
  }
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function groupVolumeTrendPoints(points: WorkbenchProblemAnalysisVolumeTrendPoint[], granularity: Granularity) {
  if (granularity === 'day') {
    return points
  }

  const buckets = new Map<string, WorkbenchProblemAnalysisVolumeTrendPoint[]>()
  for (const point of points) {
    const timestamp = resolveVolumePointTimestamp(point.generatedAt)
    if (timestamp == null) {
      const fallbackKey = point.sliceKey || point.label
      const current = buckets.get(fallbackKey) || []
      current.push(point)
      buckets.set(fallbackKey, current)
      continue
    }

    const key = resolveVolumeBucketKey(new Date(timestamp), granularity)
    const current = buckets.get(key) || []
    current.push(point)
    buckets.set(key, current)
  }

  return Array.from(buckets.values())
    .map((group) => {
      const lastPoint = group[group.length - 1]!
      const timestamp = resolveVolumePointTimestamp(lastPoint.generatedAt)
      return {
        ...lastPoint,
        label: timestamp == null ? lastPoint.label : formatVolumeBucketLabel(new Date(timestamp), granularity)
      }
    })
}

const rawVolumeTrendPoints = computed<WorkbenchProblemAnalysisVolumeTrendPoint[]>(() => props.rich?.volume?.trendPoints || [])
const timeWindowedVolumeTrendPoints = computed<WorkbenchProblemAnalysisVolumeTrendPoint[]>(() => {
  const windowStart = props.windowStartMs
  if (windowStart == null) {
    return rawVolumeTrendPoints.value
  }
  const filtered = rawVolumeTrendPoints.value.filter((point) => {
    const timestamp = resolveVolumePointTimestamp(point.generatedAt)
    return timestamp == null || timestamp >= windowStart
  })

  return filtered.length ? filtered : rawVolumeTrendPoints.value
})

const volumeTrendPoints = computed<WorkbenchProblemAnalysisVolumeTrendPoint[]>(() =>
  groupVolumeTrendPoints(timeWindowedVolumeTrendPoints.value, props.granularity)
)
const showVolumeComparisons = computed(() => false)

const latestVolumeSlice = computed<WorkbenchProblemAnalysisVolumeTrendPoint | null>(() => {
  const points = volumeTrendPoints.value
  if (!points.length) {
    return null
  }
  return points.find(point => Boolean(point.isLatest) && point.sliceKey) || points[points.length - 1] || null
})

const activeVolumeSlice = computed<WorkbenchProblemAnalysisVolumeTrendPoint | null>(() => {
  if (selectedVolumeSliceKey.value) {
    const matched = volumeTrendPoints.value.find(point => point.sliceKey === selectedVolumeSliceKey.value)
    if (matched) {
      return matched
    }
  }
  return latestVolumeSlice.value
})

const activeVolumeSnapshot = computed(() => activeVolumeSlice.value?.snapshot || null)
const hasActiveVolumeSnapshot = computed(() => Boolean(activeVolumeSnapshot.value))
const hasVolumePlatformRows = computed(() => filteredVolumePlatformRows.value.length > 0)
const isHistoricalVolumeSliceActive = computed(() => Boolean(activeVolumeSlice.value) && !isLatestVolumeSliceActive.value)

const activeVolumeSummary = computed<WorkbenchProblemAnalysisVolumeSummary | null>(() =>
  activeVolumeSnapshot.value?.summary || null
)

const activeVolumeTopTopics = computed<WorkbenchProblemAnalysisVolumeTopicSliceRow[]>(() =>
  (activeVolumeSnapshot.value?.topTopics || []).filter(row => matchesSelectedTopic(row.topicName))
)

const filteredVolumePlatformRows = computed<WorkbenchProblemAnalysisPlatformDemandRow[]>(() =>
  (activeVolumeSnapshot.value?.platformRows || [])
    .filter(row => matchesSelectedPlatform(row.platform))
    .map(row => ({
      ...row,
      platform: resolvePlatformDisplay(row.platform)
    }))
)

const activeVolumePlatformCount = computed(() => {
  if (filteredVolumePlatformRows.value.length) {
    return filteredVolumePlatformRows.value.length
  }
  return activeVolumeSummary.value?.activePlatformCount
    ?? activeVolumeSnapshot.value?.platformRows?.length
    ?? 0
})

const isLatestVolumeSliceActive = computed(() => {
  const latestSliceKey = latestVolumeSlice.value?.sliceKey
  const activeSliceKey = activeVolumeSlice.value?.sliceKey
  if (!latestSliceKey || !activeSliceKey) {
    return !selectedVolumeSliceKey.value
  }
  return activeSliceKey === latestSliceKey
})

const activeVolumeSliceTag = computed(() => {
  if (!activeVolumeSlice.value) {
    return '最近时间点'
  }
  if (isLatestVolumeSliceActive.value) {
    return '最近时间点'
  }
  return activeVolumeSlice.value.generatedAt || activeVolumeSlice.value.label
})

function handleTrendChartClick(payload: unknown) {
  const dataIndex = typeof (payload as ChartSeriesClickPayload | null)?.dataIndex === 'number'
    ? Number((payload as ChartSeriesClickPayload).dataIndex)
    : -1
  if (dataIndex < 0) {
    return
  }

  const point = volumeTrendPoints.value[dataIndex]
  if (!point?.sliceKey) {
    return
  }
  if (point.sliceKey === latestVolumeSlice.value?.sliceKey) {
    selectedVolumeSliceKey.value = null
    return
  }
  selectedVolumeSliceKey.value = selectedVolumeSliceKey.value === point.sliceKey ? null : point.sliceKey
}

function resetVolumeSliceSelection() {
  selectedVolumeSliceKey.value = null
}

watch(activeView, (value) => {
  if (value !== 'volume' && selectedVolumeSliceKey.value) {
    resetVolumeSliceSelection()
  }
})

const trendChartOption = computed<EChartsOption | null>(() => {
  const points = volumeTrendPoints.value
  if (!points.length) {
    return null
  }
  const activeSliceKey = activeVolumeSlice.value?.sliceKey
  return {
    grid: { top: 24, right: 16, left: 44, bottom: 38 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#111827',
      borderColor: '#111827',
      textStyle: { color: '#f9fafb', fontSize: 11 }
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: points.map(point => point.label),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#6b7280', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#f1f5f9' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 }
    },
    series: [
      {
        name: '当前值',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { color: '#111827', width: 2 },
        areaStyle: { color: 'rgba(17, 24, 39, 0.04)' },
        data: points.map(point => ({
          value: point.currentValue ?? 0,
          symbolSize: point.sliceKey === activeSliceKey ? 9 : 5,
          itemStyle: {
            color: point.sliceKey === activeSliceKey ? '#2563eb' : '#111827',
            borderColor: '#ffffff',
            borderWidth: point.sliceKey === activeSliceKey ? 2 : 1
          }
        }))
      },
      ...(showVolumeComparisons.value
        ? [{
            name: '对比值',
            type: 'line' as const,
            smooth: true,
            symbol: 'none',
            lineStyle: {
              color: '#94a3b8',
              width: 1.5,
              type: 'dashed' as const
            },
            data: points.map(point => point.previousValue ?? null)
          }]
        : [])
    ]
  }
})

const intentColorMap: Record<string, string> = {
  discovery: '#2563eb',
  decision: '#0f766e',
  risk: '#b91c1c',
  cost: '#7c3aed'
}

const displayIntentRows = computed(() => props.rich?.intent?.intents || [])

const displayIntentSubRows = computed(() => props.rich?.intent?.subIntents || [])

const orderedIntentRows = computed(() =>
  displayIntentRows.value
    .filter(item => (item.share ?? 0) > 0)
    .slice()
    .sort((left, right) => (right.share ?? 0) - (left.share ?? 0))
)

const orderedIntentSubRows = computed(() => {
  const parentOrder = new Map(
    orderedIntentRows.value.map((item, index) => [item.key, index] as const)
  )

  return displayIntentSubRows.value
    .filter(item => (item.share ?? 0) > 0)
    .slice()
    .sort((left, right) => {
      const leftOrder = parentOrder.get(left.parent || '') ?? Number.MAX_SAFE_INTEGER
      const rightOrder = parentOrder.get(right.parent || '') ?? Number.MAX_SAFE_INTEGER
      if (leftOrder !== rightOrder) {
        return leftOrder - rightOrder
      }
      const shareGap = (right.share ?? 0) - (left.share ?? 0)
      if (shareGap !== 0) {
        return shareGap
      }
      return left.label.localeCompare(right.label)
    })
})
const hasIntentRows = computed(() => orderedIntentRows.value.length > 0)
const hasIntentSubRows = computed(() => orderedIntentSubRows.value.length > 0)

const nestedIntentChartData = computed(() => {
  const innerData: Array<Record<string, unknown>> = []
  const outerData: Array<Record<string, unknown>> = []

  for (const parent of orderedIntentRows.value) {
    const parentShare = Math.max(parent.share ?? 0, 0)
    const color = intentColorMap[parent.key] || '#64748b'
    const childRows = orderedIntentSubRows.value.filter(item => item.parent === parent.key)
    const childShareTotal = childRows.reduce((sum, item) => sum + Math.max(item.share ?? 0, 0), 0)
    const scale = childShareTotal > parentShare && parentShare > 0
      ? parentShare / childShareTotal
      : 1

    innerData.push({
      name: parent.label,
      value: parentShare,
      rawShare: parentShare,
      itemStyle: { color }
    })

    for (const child of childRows) {
      const rawShare = Math.max(child.share ?? 0, 0)
      outerData.push({
        name: `${child.parentLabel || parent.label}·${child.label}`,
        value: rawShare * scale,
        rawShare,
        itemStyle: { color, opacity: 0.82 }
      })
    }

    const remainingShare = Math.max(parentShare - childShareTotal * scale, 0)
    if (remainingShare > 0.2) {
      outerData.push({
        name: `${parent.label}·其他`,
        value: remainingShare,
        rawShare: remainingShare,
        itemStyle: { color, opacity: 0.22 }
      })
    }
  }

  return { innerData, outerData }
})

const intentChartOption = computed<EChartsOption | null>(() => {
  if (!orderedIntentRows.value.length) {
    return null
  }
  return {
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        const datum = Array.isArray(params) ? params[0] : params
        const rawShare = Number((datum as { data?: { rawShare?: number } })?.data?.rawShare ?? datum?.value ?? 0)
        return `${datum?.name || ''}<br/>${rawShare.toFixed(1)}%`
      }
    },
    series: [
      {
        type: 'pie',
        radius: ['0%', '46%'],
        center: ['38%', '52%'],
        label: { show: false },
        itemStyle: { borderColor: '#fff', borderWidth: 1 },
        data: nestedIntentChartData.value.innerData
      },
      {
        type: 'pie',
        radius: ['58%', '82%'],
        center: ['38%', '52%'],
        label: { show: false },
        itemStyle: { borderColor: '#fff', borderWidth: 1, opacity: 0.78 },
        data: nestedIntentChartData.value.outerData
      }
    ]
  }
})

const filteredPromptOpportunityRows = computed(() => {
  const rows = props.rich?.volume?.promptOpportunityRows || []
  const search = promptOpportunitySearch.value.trim().toLowerCase()
  return rows.filter((row) => {
    if (!matchesSelectedTopic(row.topic)) {
      return false
    }
    if (!search) {
      return true
    }
    return `${row.prompt} ${row.topic || ''}`.toLowerCase().includes(search)
  })
})

const promptProjectionByPrompt = computed(() =>
  new Map(
    (props.rich?.volume?.promptProjectionRows || []).map(row => [row.prompt, row] as const)
  )
)

const promptPriorityRows = computed(() => {
  return filteredPromptOpportunityRows.value
    .slice()
    .sort((left, right) => {
      const opportunityGap = (right.opportunityScore ?? 0) - (left.opportunityScore ?? 0)
      if (opportunityGap !== 0) {
        return opportunityGap
      }
      return (promptProjectionByPrompt.value.get(right.prompt)?.projection ?? right.projection ?? 0)
        - (promptProjectionByPrompt.value.get(left.prompt)?.projection ?? left.projection ?? 0)
    })
    .map(row => ({
      prompt: row.prompt,
      topic: row.topic || '综合',
      monthlyDemand: row.demandIndex ?? 0,
      projection: promptProjectionByPrompt.value.get(row.prompt)?.projection ?? row.projection ?? 0,
      opportunity: row.opportunityScore ?? 0,
      intentLabel: row.intentLabel || promptProjectionByPrompt.value.get(row.prompt)?.intentLabel || '综合',
      action: promptProjectionByPrompt.value.get(row.prompt)?.action || '保持观察'
    }))
})

const activeResearchPrompts = computed(() => {
  const visiblePrompts = new Set(promptPriorityRows.value.map(row => row.prompt))
  const preservedSelection = selectedResearchPrompts.value.filter(prompt => visiblePrompts.has(prompt))
  if (preservedSelection.length) {
    return preservedSelection
  }
  return promptPriorityRows.value.map(row => row.prompt)
})

const activeResearchPromptSet = computed(() => new Set(activeResearchPrompts.value))

const bulkPromptAnalysisRows = computed(() => {
  const picked = activeResearchPromptSet.value
  const rows = promptPriorityRows.value.filter(row => picked.has(row.prompt))
  const totalProjection = rows.reduce((sum, row) => sum + row.projection, 0)
  return rows.map(row => ({
    prompt: row.prompt,
    projection: row.projection,
    share: totalProjection > 0 ? (row.projection / totalProjection) * 100 : 0,
    intentLabel: row.intentLabel,
    action: row.action
  }))
})

const promptProjectionSummary = computed(() => {
  const rows = promptPriorityRows.value.filter(row => activeResearchPromptSet.value.has(row.prompt))
  return {
    promptCount: rows.length,
    totalProjection: rows.reduce((sum, row) => sum + row.projection, 0),
    highValuePromptCount: rows.filter(row => row.opportunity >= 70).length
  }
})
const hasPromptPriorityRows = computed(() => promptPriorityRows.value.length > 0)

function toggleResearchPrompt(prompt: string) {
  if (!selectedResearchPrompts.value.length) {
    selectedResearchPrompts.value = [prompt]
    return
  }
  if (selectedResearchPrompts.value.includes(prompt)) {
    selectedResearchPrompts.value = selectedResearchPrompts.value.filter(item => item !== prompt)
    return
  }
  selectedResearchPrompts.value = [...selectedResearchPrompts.value, prompt]
}

const scopedPromptLoopRows = computed(() =>
  (props.rich?.volume?.promptLoopRows || []).filter((row) => {
    if (!matchesSelectedTopic(row.topic)) {
      return false
    }
    return true
  })
)

const filteredPromptLoopRows = computed(() => {
  return scopedPromptLoopRows.value.filter((row) => {
    if (brandGapOnly.value && row.status !== '竞品被引（我方缺口）') {
      return false
    }
    return (row.opportunity ?? 0) >= minOpportunity.value
  })
})

const filteredPromptLoopSummary = computed(() => {
  const rows = scopedPromptLoopRows.value
  if (!rows.length) {
    return {
      coverage: 0,
      totalCount: 0,
      coveredCount: 0,
      gapCount: 0,
      highOpportunityCount: 0
    }
  }
  const covered = rows.filter(row => row.status === '已被引用').length
  return {
    coverage: (covered / rows.length) * 100,
    totalCount: rows.length,
    coveredCount: covered,
    gapCount: rows.filter(row => row.status === '竞品被引（我方缺口）').length,
    highOpportunityCount: rows.filter(row => (row.opportunity ?? 0) >= 75).length
  }
})

const promptLoopRows = computed(() => props.rich?.volume?.promptLoopRows || [])
const hasScopedPromptLoopRows = computed(() => scopedPromptLoopRows.value.length > 0)

const promptLoopEmptyText = computed(() => {
  if (!promptLoopRows.value.length) {
    return '当前范围内还没有返回可展示的问题覆盖建议。'
  }
  return '当前筛选条件下没有命中覆盖建议。'
})

const filteredAudiencePlatformRows = computed(() =>
  (roleView.value?.platformRows || [])
    .filter(row => matchesSelectedPlatform(row.platform))
    .map(row => ({
      ...row,
      platform: resolvePlatformDisplay(row.platform)
    }))
)
const hasAudienceRoleRows = computed(() => Boolean((roleView.value?.roleRows || []).length))
const hasAudienceBudgetRows = computed(() => Boolean((roleView.value?.budgetRows || []).length))
const audienceOverviewCardCount = computed(() =>
  Number(hasAudienceRoleRows.value) + Number(hasAudienceBudgetRows.value)
)
const hasAudiencePlatformRows = computed(() => filteredAudiencePlatformRows.value.length > 0)
const hasAudienceData = computed(() =>
  hasAudienceRoleRows.value || hasAudienceBudgetRows.value || hasAudiencePlatformRows.value
)

function openProbeDetail(row: PromptMetricRow) {
  const probe = resolveProbeSeed(row)
  if (!probe) {
    return
  }
  emit('openProbeDetail', probe.id)
}

function openProvenance(
  anchorKey: string,
  metricKey: string,
  probeId?: string | null,
  topicId?: string | null,
  metricValue?: string | null,
  metricLabel?: string | null,
  platform?: string | null,
  windowKey?: string | null,
  contextCitationCount?: number | null,
  contextDemandVolume?: number | null,
  contextDemandTotal?: number | null,
  contextActiveTopicCount?: number | null,
  contextActivePlatformCount?: number | null,
  contextPromptCount?: number | null,
  contextTotalProjection?: number | null,
  contextHighValuePromptCount?: number | null,
  contextPromptLoopTotalCount?: number | null,
  contextPromptLoopCoveredCount?: number | null,
  contextPromptLoopGapCount?: number | null,
  contextPromptLoopHighOpportunityCount?: number | null
) {
  emit('openProvenance', anchorKey, metricKey, probeId || null, topicId || null, metricValue || null, metricLabel || null, platform || null, windowKey || null, contextCitationCount ?? null, contextDemandVolume ?? null, contextDemandTotal ?? null, contextActiveTopicCount ?? null, contextActivePlatformCount ?? null, contextPromptCount ?? null, contextTotalProjection ?? null, contextHighValuePromptCount ?? null, contextPromptLoopTotalCount ?? null, contextPromptLoopCoveredCount ?? null, contextPromptLoopGapCount ?? null, contextPromptLoopHighOpportunityCount ?? null)
}

function openVolumeSliceProvenance(metricKey: string, metricValue?: string | null, metricLabel?: string | null) {
  const sliceKey = activeVolumeSlice.value?.sliceKey || 'latest_volume_slice'
  openProvenance(sliceKey, metricKey, null, null, metricValue || null, metricLabel || null, null, sliceKey, null, null, activeVolumeSummary.value?.demandTotal ?? null, activeVolumeSummary.value?.activeTopicCount ?? null, activeVolumePlatformCount.value)
}

function openPromptProjectionProvenance(metricKey: 'promptCount' | 'totalProjection', metricValue: string, metricLabel: string) {
  openProvenance(
    'prompt_projection_summary',
    metricKey,
    null,
    null,
    metricValue,
    metricLabel,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    promptProjectionSummary.value.promptCount,
    promptProjectionSummary.value.totalProjection,
    promptProjectionSummary.value.highValuePromptCount
  )
}

function openPromptLoopProvenance(metricKey: 'coverage' | 'gapCount' | 'highOpportunityCount', metricValue: string, metricLabel: string) {
  openProvenance(
    'prompt_loop_summary',
    metricKey,
    null,
    null,
    metricValue,
    metricLabel,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    filteredPromptLoopSummary.value.totalCount,
    filteredPromptLoopSummary.value.coveredCount,
    filteredPromptLoopSummary.value.gapCount,
    filteredPromptLoopSummary.value.highOpportunityCount
  )
}

function toggleHierarchyTopic(topic: string) {
  if (expandedHierarchyTopics.value.includes(topic)) {
    expandedHierarchyTopics.value = expandedHierarchyTopics.value.filter(item => item !== topic)
    return
  }
  expandedHierarchyTopics.value = [...expandedHierarchyTopics.value, topic]
}

function isHierarchyTopicExpanded(topic: string) {
  if (!expandedHierarchyTopics.value.length) {
    return filteredHierarchyTopics.value[0]?.topic === topic
  }
  return expandedHierarchyTopics.value.includes(topic)
}

const rawFanoutPromptRows = computed(() => opportunityView.value?.promptRows || [])
const scopedFanoutPromptRows = computed(() =>
  rawFanoutPromptRows.value.filter((row) => {
    if (!matchesSelectedTopic(row.topic)) {
      return false
    }
    if (selectedPlatformSet.value.size) {
      const platformRows = row.platformRows || []
      if (!platformRows.some(item => matchesSelectedPlatform(item.platform))) {
        return false
      }
    }
    return true
  })
)
const hasScopedFanoutPromptRows = computed(() => scopedFanoutPromptRows.value.length > 0)

const filteredFanoutPromptRows = computed(() => {
  const search = fanoutSearch.value.trim().toLowerCase()
  if (!search) {
    return scopedFanoutPromptRows.value
  }
  return scopedFanoutPromptRows.value.filter((row) => {
    const haystack = `${row.prompt} ${row.topic || ''} ${(row.variations || []).map(item => item.prompt).join(' ')}`.toLowerCase()
    return haystack.includes(search)
  })
})

function toggleFanoutPrompt(id: string) {
  if (expandedFanoutPromptIds.value.includes(id)) {
    expandedFanoutPromptIds.value = expandedFanoutPromptIds.value.filter(item => item !== id)
    if (focusedFanoutPromptId.value === id) {
      focusedFanoutPromptId.value = null
    }
    return
  }
  expandedFanoutPromptIds.value = [...expandedFanoutPromptIds.value, id]
  focusedFanoutPromptId.value = id
}

function isFanoutPromptExpanded(id: string) {
  return expandedFanoutPromptIds.value.includes(id)
}

const focusedFanoutPrompt = computed<WorkbenchProblemAnalysisOpportunityPromptRow | null>(() => {
  if (!focusedFanoutPromptId.value) {
    return null
  }
  return filteredFanoutPromptRows.value.find(row => row.id === focusedFanoutPromptId.value) || null
})

const filteredFanoutPlatformRows = computed(() => {
  const totals = new Map<string, { platform: string, queryTotal: number, promptCount: number }>()
  for (const row of filteredFanoutPromptRows.value) {
    for (const platformRow of row.platformRows || []) {
      if (!matchesSelectedPlatform(platformRow.platform)) {
        continue
      }
      const platformLabel = resolvePlatformDisplay(platformRow.platform)
      const current = totals.get(platformLabel) || {
        platform: platformLabel,
        queryTotal: 0,
        promptCount: 0
      }
      current.queryTotal += platformRow.queryCount ?? 0
      current.promptCount += 1
      totals.set(platformLabel, current)
    }
  }

  return Array.from(totals.values())
    .map(row => ({
      rank: 0,
      platform: row.platform,
      avgQueriesPerExecution: row.promptCount ? row.queryTotal / row.promptCount : 0
    }))
    .sort((left, right) => (right.avgQueriesPerExecution ?? 0) - (left.avgQueriesPerExecution ?? 0))
    .map((row, index) => ({ ...row, rank: index + 1 }))
})

const filteredHierarchyTopics = computed(() =>
  (demandMiningView.value?.topics || []).filter((topic) => {
    if (!matchesSelectedTopic(topic.topic)) {
      return false
    }
    if (visiblePromptTopicSet.value.size && !visiblePromptTopicSet.value.has(topic.topic)) {
      return false
    }
    return true
  })
)
const hasHierarchyTopics = computed(() => filteredHierarchyTopics.value.length > 0)
const hasOpportunityData = computed(() =>
  hasScopedPromptLoopRows.value
  || hasScopedFanoutPromptRows.value
  || filteredFanoutPlatformRows.value.length > 0
)

const fanoutPlatformChartOption = computed<EChartsOption | null>(() => {
  const rows = filteredFanoutPlatformRows.value
  if (!rows.length) {
    return null
  }
  return {
    backgroundColor: 'transparent',
    grid: { left: 34, right: 16, top: 22, bottom: 30, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: '#111827',
      borderColor: '#1f2937',
      textStyle: { color: '#f9fafb' },
      formatter: (params) => {
        const item = Array.isArray(params) ? params[0] : params
        const row = rows[item?.dataIndex || 0]
        return row ? `${row.platform}<br/>平均追问数：${(row.avgQueriesPerExecution ?? 0).toFixed(1)}` : ''
      }
    },
    xAxis: {
      type: 'category',
      data: rows.map(row => row.platform),
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#6b7280', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      min: 0,
      axisLine: { show: false },
      axisLabel: { color: '#6b7280', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f3f4f6' } }
    },
    series: [
      {
        type: 'bar',
        barWidth: 42,
        itemStyle: {
          color: '#111827',
          borderRadius: [2, 2, 0, 0]
        },
        data: rows.map(row => row.avgQueriesPerExecution ?? 0)
      }
    ]
  }
})

function toPercent(value?: number | null) {
  return `${Number(value || 0).toFixed(1)}%`
}

function formatScore(value?: number | null) {
  return Number(value || 0).toFixed(1)
}

function formatSigned(value?: number | null) {
  const safe = Number(value || 0)
  return `${safe >= 0 ? '+' : ''}${safe.toFixed(1)}`
}
</script>

<template>
  <section class="surface section-surface prompts-surface">
      <div class="surface-head">
        <div class="surface-head__title">
          <div class="title-with-help">
            <strong>问题分析</strong>
          </div>
          <div class="surface-head__context">
            <span class="surface-head__object">{{ promptContextLabel }}</span>
          </div>
        </div>
      </div>

    <div
      v-if="!props.hasPromptCoverage"
      class="state-block"
    >
      {{ props.emptyCoverageMessage || '当前品牌暂无可展示的真实问题清单' }}
    </div>

    <template v-else>
      <section class="surface prompts-tab-surface">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          class="prompts-tab-btn"
          :class="{ 'prompts-tab-btn--active': activeView === tab.key }"
          @click="activeView = tab.key"
        >
          {{ tab.label }}
        </button>
      </section>

      <template v-if="activeView === 'volume'">
        <section
          class="volume-overview-grid"
          :class="{ 'volume-overview-grid--single': !hasVolumePlatformRows }"
        >
          <article class="surface section-surface prompt-volume-chart">
            <div class="metric-card__head">
              <div class="surface-head__title">
                <strong>问题需求趋势</strong>
              </div>
              <div class="volume-slice-toolbar">
                <span class="volume-slice-chip">{{ activeVolumeSliceTag }}</span>
                <button
                  v-if="!isLatestVolumeSliceActive"
                  type="button"
                  class="inline-mini"
                  @click="resetVolumeSliceSelection"
                >
                  回到最近
                </button>
              </div>
            </div>

            <div class="prompt-volume-kpi-row">
              <div class="kpi-tile">
                <span>需求总量</span>
                <strong>
                  {{ activeVolumeSummary?.demandTotal ?? '—' }}
                </strong>
                <small
                  v-if="hasActiveVolumeSnapshot && showVolumeComparisons"
                  :class="(activeVolumeSummary?.demandDelta ?? 0) >= 0 ? 'delta-up' : 'delta-down'"
                >
                  {{ formatSigned(activeVolumeSummary?.demandDelta) }}%
                </small>
                <small v-else-if="hasActiveVolumeSnapshot">当前切片</small>
                <small v-else>所选时间点暂无快照</small>
              </div>
              <div class="kpi-tile">
                <span>活跃主题</span>
                <strong>
                  {{ activeVolumeSummary?.activeTopicCount ?? '—' }}
                </strong>
                <small v-if="hasActiveVolumeSnapshot">{{ activeVolumePlatformCount }} 平台</small>
                <small v-else>等待所选时间点结果</small>
              </div>
            </div>

            <GeoLazyEChart
              :option="trendChartOption"
              :height="300"
              :x-axis-band-click-series-index="0"
              empty-text="暂无趋势数据"
              @chart-click="handleTrendChartClick"
            />
          </article>

          <aside
            v-if="hasVolumePlatformRows"
            class="surface section-surface prompt-volume-rank"
          >
            <div class="surface-head surface-head--compact">
              <strong>平台需求贡献</strong>
            </div>

            <div class="volume-rank-table prompt-volume-rank-table">
              <div class="volume-rank-table__head">
                <span />
                <span>平台</span>
                <span>量级</span>
                <span>占比</span>
                <span v-if="showVolumeComparisons">变化</span>
              </div>
              <div
                v-for="(row, index) in filteredVolumePlatformRows"
                :key="`platform-volume-${row.platform}`"
                class="volume-rank-table__row"
              >
                <span>{{ index + 1 }}.</span>
                <span class="volume-rank-table__platform">{{ row.platform }}</span>
                <span class="table-static-value">
                  {{ row.value ?? 0 }}
                </span>
                <span class="table-static-value">
                  {{ toPercent(row.share) }}
                </span>
                <span
                  v-if="showVolumeComparisons"
                  :class="showVolumeComparisons ? ((row.delta ?? 0) >= 0 ? 'delta-up' : 'delta-down') : ''"
                >
                  {{ formatSigned(row.delta) }}
                </span>
              </div>
            </div>
          </aside>
        </section>

        <section class="surface section-surface keyword-research-surface">
          <div class="metric-card__head">
            <div class="surface-head__title">
              <strong>问题优先级研究</strong>
            </div>
          </div>

          <div
            v-if="hasPromptPriorityRows"
            class="prompt-volume-two-up keyword-depth-two-up keyword-depth-two-up--merged"
          >
            <article class="surface section-surface prompt-volume-chart">
              <div class="keyword-tools-row">
                <label class="prompt-search keyword-search">
                  <span class="prompt-search__icon">⌕</span>
                  <input
                    v-model="promptOpportunitySearch"
                    type="search"
                    placeholder="搜索问题"
                  >
                </label>
              </div>

              <div class="keyword-research-table">
                <div class="keyword-research-table__head">
                  <span>问题</span>
                  <span>需求指数</span>
                  <span>需求估算</span>
                  <span>机会分</span>
                </div>
                <button
                  v-for="row in promptPriorityRows"
                  :key="`prompt-opportunity-${row.prompt}`"
                  class="keyword-research-table__row"
                  :class="{ 'keyword-research-table__row--active': activeResearchPrompts.includes(row.prompt) }"
                  type="button"
                  @click="toggleResearchPrompt(row.prompt)"
                >
                  <span>{{ row.prompt }}</span>
                  <span>{{ row.monthlyDemand }}</span>
                  <span>{{ row.projection }}</span>
                  <span :class="row.opportunity >= 70 ? 'delta-up' : ''">{{ row.opportunity.toFixed(1) }}</span>
                </button>
              </div>
            </article>

            <aside class="surface section-surface prompt-volume-rank keyword-projection-panel">
              <div class="prompt-volume-kpi-row prompt-volume-kpi-row--compact">
                <div class="kpi-tile">
                  <span>问题数</span>
                  <strong>
                    {{ promptProjectionSummary.promptCount }}
                  </strong>
                  <small>当前筛选范围</small>
                </div>
                <div class="kpi-tile">
                  <span>总需求量</span>
                  <strong>
                    {{ promptProjectionSummary.totalProjection.toLocaleString() }}
                  </strong>
                  <small>高价值 {{ promptProjectionSummary.highValuePromptCount }} 题</small>
                </div>
              </div>

              <div class="keyword-projection-table-shell">
                <div class="keyword-projection-table-head">
                  <strong>建议优先级</strong>
                </div>

                <div class="volume-rank-table keyword-projection-table">
                  <div class="volume-rank-table__head">
                    <span />
                    <span>问题</span>
                    <span>占比</span>
                    <span>意图</span>
                    <span>建议</span>
                  </div>
                  <div
                    v-for="(row, index) in bulkPromptAnalysisRows"
                    :key="`projection-${row.prompt}`"
                    class="volume-rank-table__row"
                  >
                    <span>{{ index + 1 }}.</span>
                    <span class="volume-rank-table__platform">{{ row.prompt }}</span>
                    <span>{{ toPercent(row.share) }}</span>
                    <span>{{ row.intentLabel || '综合' }}</span>
                    <span>
                      <span class="keyword-projection-action">{{ row.action || '观察' }}</span>
                    </span>
                  </div>
                </div>
              </div>
            </aside>
          </div>
          <div
            v-else
            class="state-block"
          >
            当前范围内暂无可展示的问题优先级结果
          </div>
        </section>

      </template>

      <template v-else-if="activeView === 'intent'">
        <div
          v-if="hasIntentRows || hasHierarchyTopics"
          class="intent-tab-stack"
        >
          <section
            v-if="hasIntentRows"
            class="prompt-volume-two-up"
            :class="{ 'prompt-volume-two-up--single': !hasIntentSubRows }"
          >
            <article class="surface section-surface intent-surface">
            <div class="surface-head surface-head--compact">
              <strong>意图占比</strong>
            </div>
            <GeoLazyEChart
              :option="intentChartOption"
              :height="308"
              empty-text="暂无意图数据"
            />
            </article>

            <aside
              v-if="hasIntentSubRows"
              class="surface section-surface intent-surface"
            >
            <div class="surface-head surface-head--compact">
              <strong>子意图细分</strong>
            </div>
            <div
              v-if="orderedIntentSubRows.length"
              class="intent-sub-table"
            >
              <div class="intent-sub-table__head">
                <span>主意图</span>
                <span>子意图</span>
                <span>份额</span>
                <span>变化</span>
              </div>
              <div
                v-for="item in orderedIntentSubRows"
                :key="`intent-sub-${item.key}`"
                class="intent-sub-table__row"
              >
                <span>{{ item.parentLabel || '综合' }}</span>
                <span>{{ item.label }}</span>
                <span>{{ toPercent(item.share) }}</span>
                <span :class="(item.change ?? 0) >= 0 ? 'delta-up' : 'delta-down'">
                  {{ formatSigned(item.change) }}%
                </span>
              </div>
            </div>
            </aside>
          </section>

          <section
            v-if="hasIntentRows"
            class="surface section-surface"
          >
            <div class="surface-head surface-head--compact">
              <strong>意图样本</strong>
            </div>
            <div class="intent-card-grid">
              <article
                v-for="item in orderedIntentRows"
                :key="`intent-card-${item.key}`"
                class="intent-card"
              >
                <div class="intent-card__head">
                  <span>{{ item.label }}</span>
                  <span>{{ toPercent(item.share) }}</span>
                </div>
                <ul class="intent-card__samples">
                  <li
                    v-for="sample in item.examples || []"
                    :key="`intent-sample-${item.key}-${sample}`"
                  >
                    {{ sample }}
                  </li>
                </ul>
              </article>
            </div>
          </section>

          <section
            v-if="hasHierarchyTopics"
            class="surface section-surface hierarchy-surface"
          >
            <div class="surface-head">
              <div class="surface-head__title">
                <strong>需求挖掘</strong>
              </div>
            </div>

            <div class="hierarchy-table">
              <div class="hierarchy-table__head">
                <span>主题</span>
                <span>热度分</span>
              </div>
              <template
                v-for="topic in filteredHierarchyTopics"
                :key="`hierarchy-topic-${topic.topic}`"
              >
                <button
                  type="button"
                  class="hierarchy-topic-row"
                  @click="toggleHierarchyTopic(topic.topic)"
                >
                  <span class="hierarchy-topic-row__main">
                    <span
                      class="prompts-table__chevron"
                      :class="{ 'prompts-table__chevron--open': isHierarchyTopicExpanded(topic.topic) }"
                    >›</span>
                    <span>{{ topic.topic }}</span>
                  </span>
                  <span>{{ topic.demandIndex ?? 0 }}</span>
                </button>
                <div
                  v-if="isHierarchyTopicExpanded(topic.topic)"
                  class="hierarchy-topic-body"
                >
                  <article
                    v-for="cluster in topic.clusters || []"
                    :key="`hierarchy-cluster-${topic.topic}-${cluster.label}`"
                    class="hierarchy-cluster"
                  >
                    <div class="hierarchy-cluster__head">
                      <strong>{{ cluster.label }}</strong>
                      <span>{{ cluster.count ?? 0 }} 条</span>
                    </div>
                    <ul>
                      <li
                        v-for="sample in cluster.samples || []"
                        :key="`hierarchy-sample-${topic.topic}-${cluster.label}-${sample}`"
                      >
                        {{ sample }}
                      </li>
                    </ul>
                  </article>
                </div>
              </template>
            </div>
          </section>
        </div>
        <div
          v-else
          class="state-block"
        >
          当前范围内暂无可展示的意图与需求结构
        </div>
      </template>

      <template v-else-if="activeView === 'roles'">
        <div
          v-if="hasAudienceData"
          class="audience-tab-stack"
        >
          <section
            v-if="audienceOverviewCardCount"
            class="prompt-volume-two-up"
            :class="{ 'prompt-volume-two-up--single': audienceOverviewCardCount === 1 }"
          >
            <article
              v-if="hasAudienceRoleRows"
              class="surface section-surface demographic-surface"
            >
              <div class="surface-head surface-head--compact">
                <strong>决策角色推断</strong>
              </div>
              <div
                v-for="row in roleView?.roleRows || []"
                :key="`audience-role-${row.label}`"
                class="demographic-row"
              >
                <span class="demographic-row__label">{{ row.label }}</span>
                <div class="demographic-row__track">
                  <div
                    class="demographic-row__value demographic-row__value--age"
                    :style="{ width: `${Math.max(row.share || 0, 3)}%` }"
                  />
                </div>
                <span class="demographic-row__share">{{ toPercent(row.share) }}</span>
              </div>
            </article>

            <aside
              v-if="hasAudienceBudgetRows"
              class="surface section-surface demographic-surface"
            >
              <div class="surface-head surface-head--compact">
                <strong>预算取向推断</strong>
              </div>
              <div
                v-for="row in roleView?.budgetRows || []"
                :key="`audience-budget-${row.label}`"
                class="demographic-row"
              >
                <span class="demographic-row__label">{{ row.label }}</span>
                <div class="demographic-row__track">
                  <div
                    class="demographic-row__value demographic-row__value--income"
                    :style="{ width: `${Math.max(row.share || 0, 3)}%` }"
                  />
                </div>
                <span class="demographic-row__share">{{ toPercent(row.share) }}</span>
              </div>
            </aside>
          </section>

          <section
            v-if="hasAudiencePlatformRows"
            class="surface section-surface demographic-platform-surface"
          >
            <div class="surface-head surface-head--compact">
              <strong>平台问题信号</strong>
            </div>
            <div class="demographic-platform-table">
              <div class="demographic-platform-table__head">
                <span>平台</span>
                <span>信号分</span>
                <span>样本充分度</span>
              </div>
              <div
                v-for="row in filteredAudiencePlatformRows"
                :key="`audience-platform-${row.platform}`"
                class="demographic-platform-table__row"
              >
                <span>{{ row.platform }}</span>
                <span>{{ formatScore(row.score) }}</span>
                <span>{{ toPercent(row.confidence) }}</span>
              </div>
            </div>
          </section>
        </div>
        <div
          v-else
          class="state-block"
        >
          当前范围内暂无可展示的角色与预算信号
        </div>
      </template>

      <template v-else-if="activeView === 'opportunity'">
        <div
          v-if="hasOpportunityData"
          class="opportunity-tab-stack"
        >
          <section
            v-if="hasScopedPromptLoopRows"
            class="surface section-surface brand-loop-surface"
          >
            <div class="surface-head">
              <div class="surface-head__title">
                <strong>问题覆盖建议</strong>
              </div>
              <div class="brand-loop-actions">
                <label class="brand-loop-check">
                  <input
                    v-model="brandGapOnly"
                    type="checkbox"
                  >
                  <span>仅看缺口</span>
                </label>
                <label class="brand-loop-threshold">
                  <span>机会分 ≥</span>
                  <input
                    v-model.number="minOpportunity"
                    type="number"
                    min="0"
                    max="100"
                  >
                </label>
              </div>
            </div>

            <div class="prompt-volume-kpi-row prompt-volume-kpi-row--compact">
              <div class="kpi-tile">
                <span>被引覆盖率</span>
                <strong>
                  {{ toPercent(filteredPromptLoopSummary.coverage) }}
                </strong>
                <small>已被引用问题占比</small>
              </div>
              <div class="kpi-tile">
                <span>缺口词</span>
                <strong>
                  {{ filteredPromptLoopSummary.gapCount }}
                </strong>
                <small>竞品被引而我方缺失</small>
              </div>
              <div class="kpi-tile">
                <span>高价值机会</span>
                <strong>
                  {{ filteredPromptLoopSummary.highOpportunityCount }}
                </strong>
                <small>机会分 ≥ 75</small>
              </div>
            </div>

            <div class="brand-loop-table">
              <div
                v-if="filteredPromptLoopRows.length"
                class="brand-loop-table__head"
              >
                <span>主题</span>
                <span>问题</span>
                <span>状态</span>
                <span>当前被引对象</span>
                <span>机会分</span>
                <span>建议动作</span>
              </div>
              <div
                v-for="row in filteredPromptLoopRows.slice(0, 12)"
                :key="`brand-loop-${row.id}`"
                class="brand-loop-table__row"
              >
                <span>{{ row.topic || '未分组问题' }}</span>
                <span class="brand-loop-table__prompt">{{ row.prompt }}</span>
                <span
                  class="brand-loop-status"
                  :class="{
                    'brand-loop-status--ok': row.status === '已被引用',
                    'brand-loop-status--warn': row.status === '待强化',
                    'brand-loop-status--gap': row.status === '竞品被引（我方缺口）'
                  }"
                >
                  {{ row.status || '潜力词' }}
                </span>
                <span>{{ row.citedTarget || '待抢占' }}</span>
                <span :class="(row.opportunity ?? 0) >= 75 ? 'delta-up' : ''">{{ row.opportunity ?? 0 }}</span>
                <span class="brand-loop-table__action">{{ row.action || '加入下一轮探针池' }}</span>
              </div>
              <div
                v-if="!filteredPromptLoopRows.length"
                class="state-block state-block--table"
              >
                {{ promptLoopEmptyText }}
              </div>
            </div>
          </section>

          <section
            v-if="hasScopedFanoutPromptRows"
            class="surface section-surface fanouts-module"
          >
            <div class="metric-card__head">
              <div class="surface-head__title">
                <strong>追问机会</strong>
              </div>
              <label class="prompt-search fanouts-search">
                <span class="prompt-search__icon">⌕</span>
                <input
                  v-model="fanoutSearch"
                  type="search"
                  placeholder="搜索探针"
                >
              </label>
            </div>

            <div
              v-if="!filteredFanoutPromptRows.length"
              class="state-block"
            >
              当前品牌暂无可展示的相关问法
            </div>

            <div
              v-else
              class="fanouts-table"
            >
              <div class="fanouts-table__head fanouts-table__row">
                <span>探针</span>
                <span>追问次数</span>
                <span>单次执行平均追问</span>
              </div>

              <template
                v-for="row in filteredFanoutPromptRows"
                :key="`fanout-row-${row.id}`"
              >
                <div
                  class="fanouts-table__row fanouts-table__row--parent"
                  :class="{ 'fanouts-table__row--expanded': isFanoutPromptExpanded(row.id) }"
                  @click="toggleFanoutPrompt(row.id)"
                >
                  <div class="fanout-prompt-cell">
                    <button
                      type="button"
                      class="fanout-prompt-toggle"
                    >
                      <span
                        class="fanout-prompt-toggle__chevron"
                        :class="{ 'fanout-prompt-toggle__chevron--open': isFanoutPromptExpanded(row.id) }"
                      >›</span>
                      <span class="fanout-prompt-toggle__text">{{ row.prompt }}</span>
                      <span class="fanout-prompt-toggle__meta">{{ (row.variations || []).length }} 条相关追问</span>
                    </button>
                  </div>

                  <span>{{ row.queryCount ?? 0 }}</span>
                  <span>{{ (row.avgQueriesPerExecution ?? 0).toFixed(1) }}</span>
                </div>

                <div
                  v-if="isFanoutPromptExpanded(row.id)"
                  class="fanouts-table__group"
                >
                  <div
                    v-for="variation in row.variations || []"
                    :key="`fanout-variation-${variation.id}`"
                    class="fanouts-table__row fanouts-table__row--child"
                  >
                    <span class="fanout-variation-text">{{ variation.prompt }}</span>
                    <span>
                      {{ variation.queryCount ?? 0 }}
                      <em :class="(variation.delta ?? 0) >= 0 ? 'delta-up' : 'delta-down'">{{ formatSigned(variation.delta) }}</em>
                    </span>
                    <span class="fanout-cell-muted">-</span>
                  </div>
                </div>
              </template>
            </div>

            <section
              v-if="focusedFanoutPrompt"
              class="fanout-insight"
            >
              <header class="fanout-insight__head">
                <div class="surface-head__title">
                  <strong>机会洞察</strong>
                  <span class="fanout-insight__prompt">{{ focusedFanoutPrompt.prompt }}</span>
                </div>
                <button
                  type="button"
                  class="inline-mini"
                  @click="focusedFanoutPromptId = null"
                >
                  收起
                </button>
              </header>

              <div class="fanout-insight__grid">
                <article class="fanout-insight__card">
                  <div class="fanout-insight__title">
                    追问明细
                  </div>
                  <div class="fanout-insight-list">
                    <div
                      v-for="variation in focusedFanoutPrompt.variations || []"
                      :key="`focused-variation-${variation.id}`"
                      class="fanout-insight-list__row"
                    >
                      <span class="fanout-insight-list__text">{{ variation.prompt }}</span>
                      <span class="fanout-insight-list__meta">
                        {{ variation.queryCount ?? 0 }}
                        <em :class="(variation.delta ?? 0) >= 0 ? 'delta-up' : 'delta-down'">{{ formatSigned(variation.delta) }}</em>
                      </span>
                    </div>
                  </div>
                </article>

                <aside class="fanout-insight__card">
                  <div class="fanout-insight__title">
                    平台机会贡献
                  </div>
                  <div class="fanout-platform-list">
                    <div
                      v-for="platformRow in (focusedFanoutPrompt.platformRows || []).filter(item => matchesSelectedPlatform(item.platform))"
                      :key="`focused-platform-${platformRow.platform}`"
                      class="fanout-platform-list__row"
                    >
                      <div class="fanout-platform-list__meta">
                        <span>{{ resolvePlatformDisplay(platformRow.platform) }}</span>
                        <span>{{ platformRow.queryCount ?? 0 }} 次</span>
                      </div>
                      <div class="fanout-platform-list__track">
                        <div
                          class="fanout-platform-list__value"
                          :style="{ width: `${Math.max(platformRow.share || 0, 4)}%` }"
                        />
                      </div>
                      <span class="fanout-platform-list__share">{{ toPercent(platformRow.share) }}</span>
                    </div>
                  </div>
                </aside>
              </div>
            </section>
          </section>

          <section
            v-if="filteredFanoutPlatformRows.length"
            class="surface section-surface fanouts-module"
          >
            <div class="metric-card__head">
              <div class="surface-head__title">
                <strong>平台机会强度</strong>
              </div>
            </div>

            <div class="hero-grid hero-grid--single fanouts-hero-grid">
              <article class="surface chart-surface fanouts-chart-surface">
                <div class="fanouts-kpi-head">
                  <div>
                    <span class="fanouts-kpi-title">单次执行平均追问</span>
                    <strong>{{ (filteredFanoutPlatformRows[0]?.avgQueriesPerExecution ?? 0).toFixed(1) }}</strong>
                  </div>
                </div>
                <GeoLazyEChart
                  :option="fanoutPlatformChartOption"
                  :height="312"
                  empty-text="暂无平台机会数据"
                />
              </article>

              <aside class="surface side-surface fanouts-rank-surface">
                <div class="surface-head surface-head--compact">
                  <div class="surface-head__title">
                    <span class="fanouts-rank-title">平台排名</span>
                  </div>
                </div>

                <div class="fanouts-rank-table">
                <div class="fanouts-rank-table__head">
                  <span />
                  <span>平台</span>
                  <span>单次执行平均追问</span>
                </div>
                <div
                  v-for="row in filteredFanoutPlatformRows"
                  :key="`fanout-platform-${row.platform}`"
                  class="fanouts-rank-table__row"
                >
                  <span>{{ row.rank ?? 0 }}.</span>
                  <span>{{ row.platform }}</span>
                  <span>{{ (row.avgQueriesPerExecution ?? 0).toFixed(1) }}</span>
                </div>
              </div>
              </aside>
            </div>
          </section>
        </div>
        <div
          v-else
          class="state-block"
        >
          当前范围内暂无可展示的覆盖建议与追问机会
        </div>
      </template>

      <template v-else>
        <section class="surface section-surface probe-chat-surface">
          <div class="surface-head">
            <div class="surface-head__title">
              <strong>最近真实问答记录</strong>
            </div>
            <span class="record-table-count">{{ props.recordRows?.length || 0 }} 条</span>
          </div>

          <div
            v-if="props.recordEvidencePending"
            class="sync-notice sync-notice--partial sync-notice--embedded"
          >
            当前已记录真实问题与回答，但这轮没有产出可挂载证据，因此引用列会保持为 0。
          </div>

          <div
            v-if="!props.recordRows?.length"
            class="state-block"
          >
            {{ props.recordEmptyText || '当前暂无可展示的真实问答记录' }}
          </div>

          <div
            v-else
            class="record-table-scroll"
          >
            <div class="record-table-shell">
              <div class="record-table__head">
                <span>时间</span>
                <span>平台</span>
                <span>
                  <WikiGeoWikiTrigger entrypoint-id="probe.object.prompt">
                    真实问题
                  </WikiGeoWikiTrigger>
                </span>
                <span>
                  <WikiGeoWikiTrigger entrypoint-id="probe.object.evidence">
                    引用
                  </WikiGeoWikiTrigger>
                </span>
                <span>动作</span>
              </div>
              <div
                v-for="row in props.recordRows"
                :key="`problem-analysis-record-${row.id}`"
                class="record-table__row"
              >
                <span class="record-table__time">
                  <strong>{{ row.createdDateLabel }}</strong>
                  <small>{{ row.createdTimeLabel }}</small>
                </span>
                <span class="record-table__platform">
                  <i class="record-platform-pill">{{ row.platformLabel }}</i>
                </span>
                <span class="record-table__query">
                  <strong>{{ row.query }}</strong>
                  <small>ID · {{ row.id.slice(0, 8) }}</small>
                </span>
                <span class="record-table__count">
                  <i
                    class="record-count-pill"
                    :class="{ 'record-count-pill--empty': row.citationCount === 0 }"
                  >
                    {{ row.citationCount }} 条
                  </i>
                </span>
                <span class="record-table__actions">
                  <button
                    type="button"
                    class="inline-mini"
                    @click="emit('openChatEvidence', row.id)"
                  >
                    查看证据
                  </button>
                  <button
                    v-if="row.probeId"
                    type="button"
                    class="inline-mini"
                    @click="emit('openProbeDetail', row.probeId)"
                  >
                    查看探针
                  </button>
                  <span
                    v-else
                    class="record-table__placeholder"
                  >
                    —
                  </span>
                </span>
              </div>
            </div>
          </div>
        </section>
      </template>
    </template>
  </section>
</template>

<style scoped>
.surface {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #ffffff;
  padding: 10px;
}

.surface-head {
  min-height: 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.title-with-help {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.surface-head--compact {
  min-height: 24px;
}

.prompts-surface,
.fanouts-module {
  border: none;
  gap: 8px;
}

.state-block {
  min-height: 120px;
  border: 1px dashed #9ca3af;
  border-radius: 2px;
  display: grid;
  place-items: center;
  color: #4b5563;
  font-size: 12px;
}

.state-block--table {
  min-height: 96px;
  border-style: solid;
  border-color: #e5e7eb;
  background: #fcfcfd;
  margin-top: 0;
}

.prompts-tab-surface {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  padding: 6px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  width: max-content;
}

.prompts-tab-btn {
  min-height: 30px;
  border: 1px solid transparent;
  border-radius: 2px;
  background: transparent;
  color: #4b5563;
  font-size: 12px;
  font-weight: 600;
  padding: 0 12px;
  cursor: pointer;
}

.prompts-tab-btn:hover {
  border-color: #e5e7eb;
  background: #f3f4f6;
  color: #111827;
}

.prompts-tab-btn--active {
  border-color: #d1d5db;
  background: #ffffff;
  color: #111827;
}

.probe-filter-surface {
  border: none;
  padding: 0;
}

.volume-overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(0, 0.95fr);
  gap: 10px;
  align-items: start;
}

.volume-overview-grid--single {
  grid-template-columns: minmax(0, 1fr);
}

.prompt-volume-two-up,
.keyword-depth-two-up,
.fanout-insight__grid,
.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  gap: 10px;
}

.prompt-volume-two-up--single,
.hero-grid--single {
  grid-template-columns: minmax(0, 1fr);
}

.intent-tab-stack,
.audience-tab-stack,
.opportunity-tab-stack {
  display: grid;
  gap: 10px;
}

.keyword-depth-two-up--merged {
  grid-template-columns: minmax(0, 1fr) 500px;
}

.prompt-volume-chart,
.prompt-volume-rank,
.keyword-research-surface,
.prompts-table-surface,
.brand-loop-surface,
.intent-surface,
.demographic-surface,
.demographic-platform-surface,
.hierarchy-surface,
.fanout-insight__card,
.fanouts-chart-surface,
.fanouts-rank-surface {
  gap: 8px;
}

.metric-card__head,
.fanout-insight__head {
  min-height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.volume-slice-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.volume-slice-chip {
  min-height: 24px;
  display: inline-flex;
  align-items: center;
  padding: 0 8px;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 600;
}

.prompt-volume-kpi-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.prompt-volume-kpi-row--compact {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.kpi-tile {
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  padding: 10px;
  display: grid;
  gap: 4px;
}

.kpi-tile span,
.kpi-tile small {
  font-size: 12px;
  color: #6b7280;
}

.kpi-tile strong,
.provenance-value-trigger {
  font-size: 20px;
  color: #111827;
}

.provenance-value-trigger {
  appearance: none;
  width: fit-content;
  border: 0;
  border-bottom: 1px dotted currentColor;
  background: linear-gradient(180deg, rgba(20, 184, 166, 0.08), rgba(20, 184, 166, 0.15));
  cursor: zoom-in;
  font: inherit;
  font-weight: 700;
  line-height: 1.05;
  padding: 0 2px;
  text-align: left;
}

.provenance-value-trigger:hover {
  background: rgba(20, 184, 166, 0.2);
}

.provenance-value-trigger--table {
  font-size: 12px;
}

.provenance-value-trigger--record {
  font-size: 11px;
}

.prompt-volume-hotspots {
  gap: 8px;
}

.topic-signal-card {
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  background: #ffffff;
  padding: 10px;
  display: grid;
  gap: 6px;
}

.topic-signal-card--lead {
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.topic-signal-card__eyebrow {
  font-size: 11px;
  line-height: 1.4;
  color: #6b7280;
}

.topic-signal-card__lead {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.topic-signal-card__lead strong {
  min-width: 0;
  font-size: 13px;
  line-height: 1.35;
  color: #111827;
}

.topic-signal-card__lead span {
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 600;
  color: #111827;
}

.topic-signal-card__prompt {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: #4b5563;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.topic-signal-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  font-size: 11px;
  color: #4b5563;
}

.topic-signal-stack {
  display: grid;
  gap: 8px;
}

.record-table-count {
  min-height: 24px;
  padding: 0 10px;
  border: 1px solid #dbe3ee;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  color: #475569;
}

.record-table-scroll {
  overflow-x: auto;
}

.record-table-shell {
  min-width: 860px;
  border: 1px solid #dbe3ee;
  border-radius: 2px;
  overflow: hidden;
  background: linear-gradient(180deg, #ffffff 0%, #fcfdff 100%);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.record-table__head,
.record-table__row {
  display: grid;
  grid-template-columns: 104px 164px minmax(340px, 1.85fr) 84px 156px;
  gap: 12px;
  padding: 0 14px;
  font-size: 12px;
}

.record-table__head {
  min-height: 44px;
  align-items: center;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border-bottom: 1px solid #dbe3ee;
  color: #64748b;
  font-weight: 600;
}

.record-table__row {
  min-height: 86px;
  align-items: start;
  padding-top: 14px;
  padding-bottom: 14px;
  border-top: 1px solid #e8edf4;
  color: #111827;
}

.record-table__head + .record-table__row {
  border-top: none;
}

.record-table__row:hover {
  background: linear-gradient(180deg, #fbfdff 0%, #f8fbff 100%);
}

.record-table__time,
.record-table__query {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.record-table__time strong,
.record-table__time small {
  font-variant-numeric: tabular-nums;
}

.record-table__time strong {
  font-size: 12px;
  color: #111827;
}

.record-table__time small,
.record-table__query small {
  font-size: 11px;
  color: #94a3b8;
}

.record-table__platform,
.record-table__count,
.record-table__actions {
  display: flex;
  align-items: center;
  padding-top: 4px;
}

.record-platform-pill,
.record-count-pill {
  min-height: 24px;
  padding: 0 10px;
  border: 1px solid #dbe3ee;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
  color: #334155;
  font-style: normal;
  font-size: 11px;
  line-height: 1.2;
}

.record-platform-pill {
  max-width: 100%;
  justify-content: flex-start;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.record-count-pill {
  min-width: 54px;
  font-weight: 600;
  background: #eff6ff;
  border-color: #bfdbfe;
  color: #1d4ed8;
}

.record-count-pill--empty {
  background: #f8fafc;
  border-color: #d1d5db;
  color: #64748b;
}

.record-table__query strong {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  color: #111827;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.record-table__actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-start;
}

.fanout-insight__prompt {
  font-size: 12px;
  line-height: 1.5;
  color: #475569;
}

.record-table__placeholder {
  color: #9ca3af;
}

.volume-rank-table,
.keyword-research-table,
.brand-loop-table,
.intent-sub-table,
.demographic-platform-table,
.hierarchy-table,
.fanouts-table,
.fanouts-rank-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
  background: #ffffff;
}

.volume-rank-table__head,
.volume-rank-table__row,
.keyword-research-table__head,
.keyword-research-table__row,
.brand-loop-table__head,
.brand-loop-table__row,
.intent-sub-table__head,
.intent-sub-table__row,
.demographic-platform-table__head,
.demographic-platform-table__row,
.hierarchy-table__head,
.fanouts-table__row,
.fanouts-rank-table__head,
.fanouts-rank-table__row {
  display: grid;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 0 10px;
  font-size: 12px;
}

.volume-rank-table__head,
.keyword-research-table__head,
.brand-loop-table__head,
.intent-sub-table__head,
.demographic-platform-table__head,
.hierarchy-table__head,
.fanouts-table__head,
.fanouts-rank-table__head {
  background: #f8fafc;
  color: #6b7280;
  border-bottom: 1px solid #d1d5db;
}

.prompt-volume-rank-table .volume-rank-table__head,
.prompt-volume-rank-table .volume-rank-table__row {
  grid-template-columns: 32px minmax(0, 1fr) 88px 88px;
}

.keyword-depth-two-up--merged .volume-rank-table__head,
.keyword-depth-two-up--merged .volume-rank-table__row {
  grid-template-columns: 32px minmax(0, 1fr) 88px 88px 120px;
}

.keyword-research-table__head,
.keyword-research-table__row {
  grid-template-columns: minmax(0, 1fr) 90px 90px 90px;
}

.brand-loop-table__head,
.brand-loop-table__row {
  grid-template-columns: 140px minmax(0, 1.6fr) 160px 120px 90px 180px;
}

.intent-sub-table__head,
.intent-sub-table__row {
  grid-template-columns: 110px minmax(0, 1fr) 90px 90px;
}

.demographic-platform-table__head,
.demographic-platform-table__row {
  grid-template-columns: minmax(0, 1fr) 120px 120px;
}

.fanouts-table__row {
  grid-template-columns: minmax(0, 1fr) 160px 180px;
}

.fanouts-rank-table__head,
.fanouts-rank-table__row {
  grid-template-columns: 32px minmax(0, 1fr) 160px;
}

.volume-rank-table__row,
.keyword-research-table__row,
.brand-loop-table__row,
.intent-sub-table__row,
.demographic-platform-table__row,
.fanouts-rank-table__row {
  border-top: 1px solid #f3f4f6;
}

.keyword-research-table__row {
  width: 100%;
  border: none;
  border-bottom: 1px solid #e5e7eb;
  background: #ffffff;
  text-align: left;
  color: #111827;
  cursor: pointer;
}

.keyword-research-table__row:last-child {
  border-bottom: none;
}

.keyword-research-table__row:hover {
  background: #f9fafb;
}

.keyword-research-table__row--active {
  background: #f3f4f6;
}

.keyword-projection-panel {
  gap: 12px;
}

.keyword-projection-panel .prompt-volume-kpi-row--compact {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.keyword-projection-panel .kpi-tile {
  min-height: 96px;
  padding: 14px 16px;
  gap: 6px;
  border-color: #e2e8f0;
  border-radius: 12px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.keyword-projection-panel .kpi-tile span {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: #64748b;
}

.keyword-projection-panel .kpi-tile strong,
.keyword-projection-panel .provenance-value-trigger {
  font-size: 28px;
  line-height: 1;
}

.keyword-projection-panel .kpi-tile small {
  font-size: 12px;
  line-height: 1.45;
  color: #475569;
}

.keyword-projection-table-shell {
  display: grid;
  gap: 8px;
}

.keyword-projection-table-head {
  display: grid;
  gap: 2px;
}

.keyword-projection-table-head strong {
  font-size: 14px;
  line-height: 1.35;
  color: #111827;
}

.keyword-projection-table-head span {
  font-size: 12px;
  line-height: 1.45;
  color: #6b7280;
}

.keyword-projection-table {
  border-radius: 12px;
  border-color: #e2e8f0;
}

.keyword-projection-panel .volume-rank-table__head,
.keyword-projection-panel .volume-rank-table__row {
  min-height: 46px;
  padding: 0 12px;
  grid-template-columns: 28px minmax(0, 1.3fr) 76px 72px 114px;
}

.keyword-projection-panel .volume-rank-table__head {
  background: #f8fafc;
  color: #64748b;
}

.keyword-projection-panel .volume-rank-table__row {
  border-top-color: #eef2f7;
}

.keyword-projection-panel .volume-rank-table__platform {
  white-space: normal;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.keyword-projection-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #334155;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

.prompts-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
}

.prompts-table__head,
.prompts-table__row {
  display: grid;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 10px;
}

.prompts-table__head {
  background: #f8fafc;
  color: #6b7280;
  border-bottom: 1px solid #d1d5db;
  font-size: 12px;
}

.prompts-table__row {
  font-size: 12px;
  border-top: 1px solid #f3f4f6;
}

.prompts-table__row--topic {
  cursor: pointer;
  background: #ffffff;
  transition: background 140ms ease, border-color 140ms ease;
}

.prompts-table__row--topic:hover {
  background: #f8fafc;
}

.prompts-table__row--topic-open {
  background: #f8fafc;
  border-top-color: #dbe4ee;
}

.prompts-table__row--prompt {
  min-height: 54px;
  padding: 0 12px 0 24px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #ffffff;
  cursor: pointer;
  transition: transform 140ms ease, border-color 140ms ease, box-shadow 140ms ease, background 140ms ease;
}

.prompts-table__row--prompt:hover {
  border-color: #cbd5e1;
  background: #fcfcfd;
  box-shadow: 0 10px 20px -18px rgba(15, 23, 42, 0.55);
  transform: translateY(-1px);
}

.prompts-table__topic-trigger,
.fanout-prompt-toggle {
  min-width: 0;
  border: none;
  background: transparent;
  padding: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #111827;
  font-size: 13px;
  cursor: pointer;
}

.prompts-table__topic-copy {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.prompts-table__topic {
  color: #6b7280;
}

.prompts-table__topic-text,
.prompts-table__prompt-text,
.volume-rank-table__platform,
.brand-loop-table__prompt,
.fanout-prompt-toggle__text,
.fanout-variation-text,
.fanout-insight-list__text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prompts-table__topic-meta,
.fanout-prompt-toggle__meta {
  color: #64748b;
  font-size: 11px;
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
  width: fit-content;
  min-height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  background: #eef2f7;
}

.prompts-table__chevron,
.fanout-prompt-toggle__chevron {
  color: #6b7280;
  font-size: 14px;
  transition: transform 140ms ease;
}

.prompts-table__chevron--open,
.fanout-prompt-toggle__chevron--open {
  transform: rotate(90deg);
}

.prompts-cell {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
}

.prompts-table__group-body {
  padding: 10px 12px 12px 18px;
  background: linear-gradient(180deg, #f8fafc 0%, #ffffff 100%);
}

.prompts-table__group-shell {
  display: grid;
  gap: 8px;
}

.prompts-table__prompt-stack {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.prompts-cell__value {
  color: #111827;
}

.prompts-table__prompt-meta {
  color: #64748b;
  font-size: 11px;
  line-height: 1.45;
}

.prompts-cell__delta--na,
.fanout-cell-muted {
  color: #9ca3af;
}

.keyword-tools-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 128px 120px;
  gap: 8px;
}

.keyword-search {
  min-width: 0;
}

.select-inline {
  min-height: 32px;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #ffffff;
  color: #374151;
  font-size: 12px;
  padding: 0 8px;
}

.select-inline:focus {
  outline: none;
  border-color: #9ca3af;
}

.prompt-search {
  min-width: 210px;
  min-height: 32px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #ffffff;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
}

.prompt-search__icon {
  color: #9ca3af;
  font-size: 12px;
}

.prompt-search input {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  color: #111827;
  font-size: 12px;
}

.brand-loop-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.brand-loop-check,
.brand-loop-threshold {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #4b5563;
}

.brand-loop-threshold input {
  width: 56px;
  min-height: 28px;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  padding: 0 8px;
}

.brand-loop-status {
  color: #6b7280;
}

.brand-loop-status--ok {
  color: #059669;
}

.brand-loop-status--warn {
  color: #d97706;
}

.brand-loop-status--gap {
  color: #dc2626;
}

.intent-card-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.intent-card {
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  padding: 10px;
  display: grid;
  gap: 8px;
}

.intent-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: #111827;
}

.intent-card__samples {
  margin: 0;
  padding-left: 16px;
  display: grid;
  gap: 4px;
  font-size: 12px;
  color: #4b5563;
}

.demographic-row {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr) 80px;
  align-items: center;
  gap: 8px;
  min-height: 34px;
}

.demographic-row__label,
.demographic-row__share {
  font-size: 12px;
  color: #4b5563;
}

.demographic-row__track,
.fanout-platform-list__track {
  min-height: 8px;
  border-radius: 999px;
  background: #f3f4f6;
  overflow: hidden;
}

.demographic-row__value,
.fanout-platform-list__value {
  min-height: 8px;
  border-radius: 999px;
  background: #111827;
}

.demographic-row__value--income {
  background: #7c3aed;
}

.hierarchy-topic-row {
  width: 100%;
  border: none;
  background: #ffffff;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 120px;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 10px;
  font-size: 12px;
  border-top: 1px solid #f3f4f6;
}

.hierarchy-topic-row__main {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.hierarchy-topic-body {
  padding: 0 10px 10px 28px;
  display: grid;
  gap: 8px;
  border-top: 1px solid #f3f4f6;
}

.hierarchy-cluster {
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  padding: 10px;
  display: grid;
  gap: 8px;
}

.hierarchy-cluster__head,
.fanout-platform-list__meta,
.fanout-insight-list__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.hierarchy-cluster ul {
  margin: 0;
  padding-left: 16px;
  display: grid;
  gap: 4px;
  font-size: 12px;
  color: #4b5563;
}

.fanout-insight {
  border-top: 1px solid #e5e7eb;
  padding-top: 10px;
}

.fanout-insight__card {
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  padding: 10px;
}

.fanout-insight__title,
.fanouts-kpi-title,
.fanouts-rank-title {
  font-size: 12px;
  color: #6b7280;
}

.fanout-insight-list,
.fanout-platform-list {
  display: grid;
  gap: 8px;
}

.fanout-insight-list__meta,
.fanout-platform-list__share {
  font-size: 12px;
  color: #4b5563;
}

.fanouts-kpi-head {
  min-height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.fanouts-kpi-head strong {
  display: block;
  margin-top: 2px;
  font-size: 24px;
  color: #111827;
}

.fanouts-hero-grid {
  grid-template-columns: minmax(0, 1fr) 560px;
  gap: 0;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
}

.chart-surface,
.side-surface {
  border: none;
}

.delta-up {
  color: #059669;
}

.delta-down {
  color: #dc2626;
}

.inline-mini {
  min-height: 24px;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #ffffff;
  color: #374151;
  font-size: 11px;
  padding: 0 8px;
}

@media (max-width: 1200px) {
  .volume-overview-grid,
  .prompt-volume-two-up,
  .keyword-depth-two-up,
  .hero-grid,
  .fanout-insight__grid,
  .fanouts-hero-grid {
    grid-template-columns: 1fr;
  }

  .intent-card-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .keyword-tools-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .intent-card-grid {
    grid-template-columns: 1fr;
  }

  .brand-loop-table__head,
  .brand-loop-table__row,
  .keyword-research-table__head,
  .keyword-research-table__row,
  .volume-rank-table__head,
  .volume-rank-table__row,
  .intent-sub-table__head,
  .intent-sub-table__row,
  .fanouts-rank-table__head,
  .fanouts-rank-table__row {
    grid-template-columns: 1fr;
    padding: 8px 10px;
  }

  .demographic-row {
    grid-template-columns: 1fr;
  }
}
</style>
