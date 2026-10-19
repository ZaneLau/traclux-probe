<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import WorkbenchAnalysisToolbar from '~/components/workbench/WorkbenchAnalysisToolbar.vue'
import WorkbenchDataProvenanceCard from '~/components/workbench/WorkbenchDataProvenanceCard.vue'
import {
  buildWorkbenchPlatformOptions,
  useWorkbenchAnalysisFilters,
  type WorkbenchPlatformOption
} from '~/composables/useWorkbenchAnalysisFilters'
import { resolveNavPageTitleByPath } from '~/composables/useDashboardNav'
import { useWorkbenchBrandSelection } from '~/composables/useWorkbenchBrandSelection'
import {
  buildDashboardOverviewContract,
  DASHBOARD_DETAIL_LOCATION_BY_CARD,
  DASHBOARD_DETAIL_ROUTE_PATH,
  type DashboardCardKey,
  type DashboardDetailRouteKey
} from '~/composables/dashboard/dashboardOverviewContract'
import type { WorkbenchOverviewView } from '~/types/workbench'

definePageMeta({
  layout: false,
  middleware: 'auth',
  requiresAuth: true
})

useSeoMeta({
  title: `${resolveNavPageTitleByPath('/dashboard', 'AI 可见度总览')} · Traclux 探镜`
})

type TrendDirection = 'up' | 'down' | 'flat' | 'none'

interface DashboardTrendPoint {
  anchorId: string
  runId?: string | null
  label: string
  mentionRate: number | null
  recommendRate: number | null
  top3Rate: number | null
  abvScore: number | null
}

interface DashboardSignalCard {
  key: string
  metricKey: 'recommendRate' | 'mentionRate' | 'top3Rate'
  title: string
  tooltip: string
  value: string
  deltaLabel: string
  deltaDirection: TrendDirection
}

interface DashboardCompetitorCardRow {
  key: string
  brandName: string
  value: string
  rate: number | null
  rankingMetric: string | null
  visibilityScore: number | null
  recommendRate: number | null
  mentionRate: number | null
  positiveRate: number | null
  rank: number | null
  isTarget: boolean
}

interface DashboardSourceCardRow {
  key: string
  label: string
  percentLabel: string
  percent: number
  citationCount: number | null
  totalCitationCount: number | null
  domain: string | null
}

type ModelPerformanceMetricKey = 'abvScore' | 'recommendRate' | 'mentionRate'

interface ModelPerformanceMetricOption {
  key: ModelPerformanceMetricKey
  label: string
}

interface DashboardModelPerformanceRow {
  key: string
  label: string
  value: string
  rate: number | null
  visibilityScore: number | null
  recommendRate: number | null
  mentionRate: number | null
  top3Rate: number | null
  sampleSize: number | null
  latestRunId: string | null
  deltaLabel: string
  deltaDirection: TrendDirection
}

interface DashboardNextStepCard {
  key: string
  priority: number
  title: string
  evidence: string
  nextStepLabel: string
  nextStepText: string
  path?: string | null
}

type DashboardCardShellStatus = 'ready' | 'loading' | 'empty' | 'error'

interface DashboardCardShellState {
  status: DashboardCardShellStatus
  message: string
}

const workbenchApi = useWorkbenchApi()
const brandSpaceDirectory = useWorkbenchBrandSpacesDirectory()
const route = useRoute()
const { load } = useAuthSnapshot()

const selectedModelPerformanceMetricKey = ref<ModelPerformanceMetricKey>('abvScore')
const workbenchOverview = ref<WorkbenchOverviewView | null>(null)
const workbenchOverviewLoading = ref(false)
const workbenchOverviewError = ref('')
const workbenchRef = ref<HTMLElement | null>(null)
const workbenchScrolled = ref(false)
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

const modelPerformanceMetricOptions: ModelPerformanceMetricOption[] = [
  { key: 'abvScore', label: 'AI 可见度' },
  { key: 'recommendRate', label: 'AI 推荐率' },
  { key: 'mentionRate', label: 'AI 提及率' }
]

const {
  brandSelectionOptions: dashboardBrandOptions,
  matchedBrandSpace: matchedWorkbenchBrandSpace,
  requestedBrandMissingOption,
  requestedBrandSpaceMissing,
  selectedBrandRouteOptionKey: selectedBrandKey
} = useWorkbenchBrandSelection(
  computed(() => typeof route.query.brandSpaceId === 'string' ? route.query.brandSpaceId : '')
)

const selectedBrandSpace = computed(() => matchedWorkbenchBrandSpace.value)
const hasSelectedBrandSpace = computed(() => Boolean(selectedBrandSpace.value?.brandSpaceId))
const showBrandSpaceEmptyState = computed(() => {
  return brandSpaceDirectory.loaded.value
    && !brandSpaceDirectory.loading.value
    && !brandSpaceDirectory.error.value
    && !dashboardBrandOptions.value.length
})
const showBrandSelectionPromptState = computed(() => {
  return brandSpaceDirectory.loaded.value
    && !brandSpaceDirectory.loading.value
    && !brandSpaceDirectory.error.value
    && dashboardBrandOptions.value.length > 0
    && !requestedBrandSpaceMissing.value
    && !hasSelectedBrandSpace.value
})
const pageError = computed(() => {
  if (requestedBrandSpaceMissing.value) {
    return '未找到对应品牌空间'
  }
  return brandSpaceDirectory.error.value || ''
})
const selectedBrandUpdatedAt = computed(() => {
  return workbenchOverview.value?.currentPerformance?.generatedAt
    || workbenchOverview.value?.lastGeneratedAt
    || ''
})
const workbenchCurrentPerformance = computed(() => workbenchOverview.value?.currentPerformance || null)
const workbenchTrend = computed(() => workbenchOverview.value?.trend || null)
const workbenchCards = computed(() => new Map((workbenchOverview.value?.cards || []).map(card => [card.cardKey, card] as const)))
const platformOptions = computed<WorkbenchPlatformOption[]>(() => {
  const analysisPlatforms = workbenchOverview.value?.analysisContext?.availablePlatforms || []
  return buildWorkbenchPlatformOptions(analysisPlatforms)
})
const analysisRequest = computed(() => buildRequest(platformOptions.value))
const analysisRequestKey = computed(() => serializeRequest(analysisRequest.value))
const dashboardBrandMetaText = computed(() => {
  if (selectedBrandUpdatedAt.value) {
    return `更新于 ${formatDateTime(selectedBrandUpdatedAt.value)}`
  }
  if (workbenchOverviewLoading.value) {
    return '正在加载真实结果'
  }
  return '等待首份结果'
})

function handleAnalysisTimePreset(value: string) {
  selectedTimePreset.value = value as typeof selectedTimePreset.value
}

const focusScoreValue = computed(() => formatPercent(normalizeNullableRate(workbenchCurrentPerformance.value?.visibilityScore), false))
const focusScoreNumber = computed(() => clampRate(normalizeNullableRate(workbenchCurrentPerformance.value?.visibilityScore) ?? 0))
const competitorCardRows = computed<DashboardCompetitorCardRow[]>(() => {
  const rows = workbenchOverview.value?.competitorRows || []
  return rows
    .map((item) => {
      const composite = normalizeNullableRate(
        item.displayScore
        ?? item.visibilityScore
        ?? item.recommendRate
        ?? item.mentionRate
        ?? item.positiveRate
        ?? null
      )

      return {
        key: item.id || item.brandName,
        brandName: item.brandName,
        value: formatPercent(composite),
        rate: composite,
        rankingMetric: item.rankingMetric || null,
        visibilityScore: normalizeNullableRate(item.visibilityScore),
        recommendRate: normalizeNullableRate(item.recommendRate),
        mentionRate: normalizeNullableRate(item.mentionRate),
        positiveRate: normalizeNullableRate(item.positiveRate),
        rank: item.rank ?? null,
        isTarget: Boolean(item.isTarget)
      }
    })
    .sort(sortCompetitorRows)
    .slice(0, 10)
})
const gaugeArcRadius = 84
const gaugeArcLength = Math.PI * gaugeArcRadius
const focusGaugeDashArray = computed(() => `${gaugeArcLength.toFixed(2)} ${gaugeArcLength.toFixed(2)}`)
const focusGaugeDashOffset = computed(() => {
  return (gaugeArcLength * (1 - focusScoreNumber.value / 100)).toFixed(2)
})
const focusGaugeNeedlePoint = computed(() => {
  const t = clampRate(focusScoreNumber.value) / 100
  const angle = Math.PI * (1 - t)
  const needleRadius = 68
  const x = 120 + needleRadius * Math.cos(angle)
  const y = 120 - needleRadius * Math.sin(angle)
  return { x: x.toFixed(2), y: y.toFixed(2) }
})

const previousTrendPoint = computed(() => {
  const points = workbenchTrend.value?.points || []
  if (points.length < 2) {
    return null
  }
  return points[points.length - 2] || null
})
const latestTrendPoint = computed(() => {
  const points = workbenchTrend.value?.points || []
  if (!points.length) {
    return null
  }
  return points[points.length - 1] || null
})
const focusScoreDeltaValue = computed(() => workbenchTrend.value?.deltaVisibilityScore ?? null)
const focusScoreDeltaDirection = computed(() => resolveDeltaDirection(focusScoreDeltaValue.value))
const focusScoreDeltaLabel = computed(() => formatDelta(focusScoreDeltaValue.value, false))
const recommendDeltaValue = computed(() => {
  const delta = normalizeNullableRate(workbenchTrend.value?.deltaRecommendRate)
  if (delta != null) {
    return delta
  }
  return computeDeltaValue(
    normalizeNullableRate(latestTrendPoint.value?.recommendRate),
    normalizeNullableRate(previousTrendPoint.value?.recommendRate)
  )
})
const mentionDeltaValue = computed(() => {
  const delta = normalizeNullableRate(workbenchTrend.value?.deltaMentionRate)
  if (delta != null) {
    return delta
  }
  return computeDeltaValue(
    normalizeNullableRate(latestTrendPoint.value?.mentionRate),
    normalizeNullableRate(previousTrendPoint.value?.mentionRate)
  )
})
const top3DeltaValue = computed(() => {
  return computeDeltaValue(
    normalizeNullableRate(latestTrendPoint.value?.top3Rate),
    normalizeNullableRate(previousTrendPoint.value?.top3Rate)
  )
})
const summarySignalCards = computed<DashboardSignalCard[]>(() => {
  const current = workbenchCurrentPerformance.value
  const recommend = normalizeNullableRate(current?.recommendRate)
  const mention = normalizeNullableRate(current?.mentionRate)
  const top3 = normalizeNullableRate(current?.top3Rate)

  return [
    {
      key: 'mention',
      metricKey: 'mentionRate',
      title: 'AI 提及率',
      tooltip: '在品牌相关问答中，模型提到该品牌的占比。',
      value: formatPercent(mention),
      deltaLabel: formatDelta(mentionDeltaValue.value),
      deltaDirection: resolveDeltaDirection(mentionDeltaValue.value)
    },
    {
      key: 'recommend',
      metricKey: 'recommendRate',
      title: 'AI 推荐率',
      tooltip: '在品牌相关问答中，模型明确推荐该品牌的占比。',
      value: formatPercent(recommend),
      deltaLabel: formatDelta(recommendDeltaValue.value),
      deltaDirection: resolveDeltaDirection(recommendDeltaValue.value)
    },
    {
      key: 'top3',
      metricKey: 'top3Rate',
      title: 'Top3 出现率',
      tooltip: '品牌进入模型回答前 3 位推荐/提及位置的占比。',
      value: formatPercent(top3),
      deltaLabel: formatDelta(top3DeltaValue.value),
      deltaDirection: resolveDeltaDirection(top3DeltaValue.value)
    }
  ]
})

function resolveDashboardSummaryMetricEntrypoint(key: DashboardSignalCard['key']) {
  if (key === 'recommend') {
    return 'dashboard.metric.recommend_rate'
  }
  if (key === 'mention') {
    return 'dashboard.metric.mention_rate'
  }
  return 'dashboard.metric.top3_rate'
}

const competitorHeadline = computed(() => {
  const rows = competitorCardRows.value
  const selfRow = rows.find(item => item.isTarget) || null
  const peerRow = rows.find(item => !item.isTarget) || null
  if (!selfRow) {
    return '等待竞品数据'
  }
  if (!peerRow || selfRow.rate == null || peerRow.rate == null) {
    return '当前只看到单品牌结果'
  }
  const gap = selfRow.rate - peerRow.rate
  return gap >= 0
    ? `领先 ${peerRow.brandName} ${Math.abs(gap).toFixed(1)} 点`
    : `落后 ${peerRow.brandName} ${Math.abs(gap).toFixed(1)} 点`
})
const competitorRankLabel = computed(() => {
  const rows = competitorCardRows.value
  const selfIndex = rows.findIndex(item => item.isTarget)
  if (selfIndex < 0) {
    return '# --'
  }
  return `#${rows[selfIndex]?.rank ?? selfIndex + 1}`
})

const sourceCardRows = computed<DashboardSourceCardRow[]>(() => {
  return (workbenchOverview.value?.sourceRows || [])
    .map(item => ({
      key: item.id,
      label: item.label,
      percentLabel: item.shareLabel || formatPercent(normalizeNullableRate(item.share)),
      percent: normalizeNullableRate(item.share) ?? 0,
      citationCount: typeof item.citationCount === 'number' ? item.citationCount : null,
      totalCitationCount: typeof item.totalCitationCount === 'number' ? item.totalCitationCount : null,
      domain: resolveDashboardSourceDomain(item.url, item.label)
    }))
    .sort((left, right) => right.percent - left.percent)
    .slice(0, 10)
})
const sourceCitationsMissing = computed(() => {
  return Boolean(
    workbenchOverview.value
    && !sourceCardRows.value.length
    && (workbenchOverview.value.dataAvailability?.missingFields || []).includes('sourceCitations')
  )
})
const sourceEmptyMessage = computed(() => {
  if (!sourceCitationsMissing.value) {
    return ''
  }

  const availabilityMessage = (workbenchOverview.value?.dataAvailability?.message || '').trim()
  if (availabilityMessage) {
    return availabilityMessage
  }

  return '本轮未产出可展示引用证据'
})
const sourceHeadline = computed(() => {
  const topSource = sourceCardRows.value[0] || null
  if (!topSource) {
    return sourceCitationsMissing.value ? sourceEmptyMessage.value : '等待来源数据'
  }
  return `${topSource.label} ${topSource.percentLabel}`
})

const latestInsight = computed(() => workbenchOverview.value?.latestInsight || null)
const latestInsightHeadline = computed(() => {
  return latestInsight.value?.headline || ''
})
const latestInsightItems = computed(() => latestInsight.value?.items || [])
const latestInsightGeneratedAt = computed(() => {
  return latestInsight.value?.generatedAt || null
})

function formatDashboardEvidenceCopy(rawValue?: string | null) {
  const text = String(rawValue || '').trim()
  if (!text) {
    return '暂无证据说明'
  }
  return text
}

function formatEngineVersionLabel(rawValue?: string | null) {
  const normalized = String(rawValue || '').trim().toLowerCase()
  if (!normalized) {
    return '--'
  }
  if (normalized === '4.3' || normalized === 'v4.3') {
    return '运行主链 5.0'
  }
  if (normalized === '4.2' || normalized === 'v4.2') {
    return '历史兼容链 4.2'
  }
  return String(rawValue || '').trim()
}
const latestInsightMetaItems = computed(() => {
  const items = [
    {
      key: 'generatedAt',
      label: '洞察时间',
      value: latestInsightGeneratedAt.value ? formatDateTime(latestInsightGeneratedAt.value) : '--'
    }
  ]

  const engineVersionLabel = formatEngineVersionLabel(latestInsight.value?.engineVersion)
  if (engineVersionLabel !== '--') {
    items.push({
      key: 'engineVersion',
      label: '当前引擎版本',
      value: engineVersionLabel
    })
  }

  return items
})

const dashboardNextSteps = computed<DashboardNextStepCard[]>(() => {
  const insightCards = latestInsightItems.value
    .slice(0, 3)
    .map((item, index) => {
      const routeKey = resolveDashboardDetailRouteKey(item.nextStepRouteKey)
      return {
        key: `insight-step-${index + 1}`,
        priority: index + 1,
        title: String(item.title || '').trim() || `洞察 ${index + 1}`,
        evidence: formatDashboardEvidenceCopy(item.evidenceSummary || item.evidence),
        nextStepLabel: String(item.nextStepLabel || '').trim(),
        nextStepText: String(item.nextStepText || '').trim(),
        path: routeKey ? DASHBOARD_DETAIL_ROUTE_PATH[routeKey] : null
      }
    })

  return insightCards
})

const trendDisplaySeries = computed<DashboardTrendPoint[]>(() => {
  return (workbenchTrend.value?.points || []).map(point => ({
    anchorId: point.runId || point.generatedAt || point.label,
    runId: point.runId || null,
    label: point.label,
    mentionRate: normalizeNullableRate(point.mentionRate),
    recommendRate: normalizeNullableRate(point.recommendRate),
    top3Rate: normalizeNullableRate(point.top3Rate),
    abvScore: normalizeNullableRate(point.visibilityScore)
  }))
})
const trendReady = computed(() => trendDisplaySeries.value.length >= 2)

const trendOption = computed<EChartsOption | null>(() => {
  if (!trendReady.value) {
    return null
  }

  return {
    backgroundColor: 'transparent',
    grid: {
      left: 28,
      right: 18,
      top: 34,
      bottom: 22,
      containLabel: true
    },
    legend: {
      top: 0,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: {
        color: '#6b7280',
        fontSize: 11
      }
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#111827',
      borderColor: '#1f2937',
      textStyle: {
        color: '#f9fafb'
      },
      formatter(params: unknown) {
        const rows = Array.isArray(params) ? params : []
        if (!rows.length) {
          return ''
        }

        const axisLabel = String((rows[0] as { axisValueLabel?: string }).axisValueLabel || '')
        const lines = rows.map((item) => {
          const row = item as { marker?: string, seriesName?: string, data?: number | string }
          const raw = typeof row.data === 'number' ? row.data : Number(row.data)
          const value = Number.isFinite(raw) ? raw.toFixed(2) : '--'
          return [
            `<span style="display:inline-flex;align-items:center;min-width:98px;">${row.marker || ''}<span style="margin-left:4px;">${row.seriesName || ''}</span></span>`,
            `<span style="display:inline-block;min-width:56px;text-align:right;">${value}</span>`
          ].join('')
        })

        return [axisLabel, ...lines].join('<br/>')
      }
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trendDisplaySeries.value.map(point => point.label),
      axisLabel: {
        color: '#6b7280',
        fontSize: 11
      },
      axisLine: {
        lineStyle: {
          color: '#e5e7eb'
        }
      }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: {
        color: '#6b7280',
        formatter: '{value}%',
        fontSize: 11
      },
      splitLine: {
        lineStyle: {
          color: '#f3f4f6'
        }
      }
    },
    series: [
      buildTrendSeries('AI 可见度', '#111827', trendDisplaySeries.value.map(point => point.abvScore)),
      buildTrendSeries('AI 推荐率', '#2563eb', trendDisplaySeries.value.map(point => point.recommendRate)),
      buildTrendSeries('AI 提及率', '#0f766e', trendDisplaySeries.value.map(point => point.mentionRate)),
      buildTrendSeries('Top3 出现率', '#f97316', trendDisplaySeries.value.map(point => point.top3Rate))
    ]
  }
})

const modelPerformanceRows = computed<DashboardModelPerformanceRow[]>(() => {
  const metricKey = selectedModelPerformanceMetricKey.value
  const grouped = new Map<string, DashboardModelPerformanceRow>()

  for (const [index, item] of (workbenchOverview.value?.modelPerformance || []).entries()) {
    const currentValue = metricKey === 'abvScore'
      ? normalizeNullableRate(item.visibilityScore)
      : metricKey === 'recommendRate'
        ? normalizeNullableRate(item.recommendRate)
        : normalizeNullableRate(item.mentionRate)

    const deltaValue = metricKey === 'abvScore'
      ? normalizeNullableRate(item.deltaVisibilityScore)
      : null

      const candidate: DashboardModelPerformanceRow = {
        key: item.id || `model-${index}`,
        label: item.label,
        value: formatPercent(currentValue),
        rate: currentValue,
        visibilityScore: normalizeNullableRate(item.visibilityScore),
        recommendRate: normalizeNullableRate(item.recommendRate),
        mentionRate: normalizeNullableRate(item.mentionRate),
        top3Rate: normalizeNullableRate(item.top3Rate),
        sampleSize: item.sampleSize ?? null,
        latestRunId: item.latestRunId || null,
        deltaLabel: formatDelta(deltaValue),
        deltaDirection: resolveDeltaDirection(deltaValue)
      }

    const existing = grouped.get(item.label)
    if (!existing || (candidate.rate ?? -1) > (existing.rate ?? -1)) {
      grouped.set(item.label, candidate)
    }
  }

  return Array.from(grouped.values())
    .map((item) => {
      return item
    })
    .sort((left, right) => (right.rate ?? -1) - (left.rate ?? -1))
})
const hasModelPerformanceData = computed(() => modelPerformanceRows.value.some(row => row.rate != null))

const dashboardCardShellStates = computed<Record<DashboardCardKey, DashboardCardShellState>>(() => {
  const loading = workbenchOverviewLoading.value
  const error = Boolean(workbenchOverviewError.value)
  const hasCompetitorRows = competitorCardRows.value.length > 0
  const hasSourceRows = sourceCardRows.value.length > 0
  const hasCurrentPerformance = normalizeNullableRate(workbenchCurrentPerformance.value?.visibilityScore) != null
  const hasPriorities = Boolean(latestInsightHeadline.value)

  return {
    current_performance: loading
      ? { status: 'loading', message: '正在加载当前表现' }
      : error
        ? { status: 'error', message: '当前表现加载失败' }
        : hasCurrentPerformance
          ? { status: 'ready', message: '' }
          : { status: 'empty', message: '暂无当前表现' },
    trend: loading
      ? { status: 'loading', message: '正在加载变化趋势' }
      : error
        ? { status: 'error', message: '变化趋势加载失败' }
        : trendReady.value
          ? { status: 'ready', message: '' }
          : { status: 'empty', message: '暂无趋势样本' },
    priorities: loading
      ? { status: 'loading', message: '正在加载最新洞察结论' }
      : error
        ? { status: 'error', message: '最新洞察结论加载失败' }
        : hasPriorities
          ? { status: 'ready', message: '' }
          : { status: 'empty', message: '暂无最新洞察结论' },
    competitor_rank: loading
      ? { status: 'loading', message: '正在加载竞品排名' }
      : error
        ? { status: 'error', message: '竞品排名加载失败' }
        : hasCompetitorRows
          ? { status: 'ready', message: '' }
          : { status: 'empty', message: '暂无竞品对比数据' },
    source_citations: loading
      ? { status: 'loading', message: '正在加载引用来源' }
      : error
        ? { status: 'error', message: '引用来源加载失败' }
        : hasSourceRows
          ? { status: 'ready', message: '' }
          : { status: 'empty', message: sourceCitationsMissing.value ? sourceEmptyMessage.value : '暂无引用来源数据' },
    model_performance: loading
      ? { status: 'loading', message: '正在加载模型表现' }
      : error
        ? { status: 'error', message: '模型表现加载失败' }
        : hasModelPerformanceData.value
          ? { status: 'ready', message: '' }
          : { status: 'empty', message: '暂无模型表现数据' }
  }
})

function resolveDashboardCardShell(cardKey: DashboardCardKey): DashboardCardShellState {
  return dashboardCardShellStates.value[cardKey]
}

function isDashboardCardReady(cardKey: DashboardCardKey) {
  return resolveDashboardCardShell(cardKey).status === 'ready'
}

const dashboardOverview = computed(() => {
  const topSource = sourceCardRows.value[0] || null
  const topModel = modelPerformanceRows.value[0] || null
  const latestInsightTime = latestInsightGeneratedAt.value ? formatDateTime(latestInsightGeneratedAt.value) : '--'

  return buildDashboardOverviewContract({
    current_performance: {
      title: '当前表现',
      primary: workbenchCards.value.get('current_performance')?.primary || `AI 可见度 ${focusScoreValue.value}`,
      secondary: workbenchCards.value.get('current_performance')?.secondary || `较上期 ${focusScoreDeltaLabel.value}`,
      detailRouteKey: 'engine'
    },
    trend: {
      title: '变化趋势',
      primary: workbenchCards.value.get('trend')?.primary || (trendReady.value ? `近${trendDisplaySeries.value.length}期趋势` : '趋势样本不足'),
      secondary: workbenchCards.value.get('trend')?.secondary || 'AI 可见度 / AI 推荐率 / AI 提及率 / Top3 出现率',
      detailRouteKey: 'engine'
    },
    priorities: {
      title: '最新洞察结论',
      primary: latestInsightHeadline.value || '等待最新洞察结果',
      secondary: `洞察时间 ${latestInsightTime}`,
      detailRouteKey: 'probe_analysis'
    },
    competitor_rank: {
      title: '竞品排名',
      primary: workbenchCards.value.get('competitor_rank')?.primary || competitorRankLabel.value,
      secondary: workbenchCards.value.get('competitor_rank')?.secondary || competitorHeadline.value || '等待竞品对比结果',
      detailRouteKey: 'engine'
    },
    source_citations: {
      title: '引用来源',
      primary: sourceCitationsMissing.value
        ? sourceHeadline.value
        : (workbenchCards.value.get('source_citations')?.primary || sourceHeadline.value || '等待来源数据'),
      secondary: sourceCitationsMissing.value
        ? '去引用来源查看空态原因'
        : (workbenchCards.value.get('source_citations')?.secondary || (topSource
            ? `头部来源 ${topSource.label}`
            : '等待来源结构')),
      detailRouteKey: 'sources'
    },
    model_performance: {
      title: '模型表现',
      primary: hasModelPerformanceData.value
        ? (workbenchCards.value.get('model_performance')?.primary || (topModel ? `${topModel.label} ${topModel.value}` : '等待模型数据'))
        : '等待模型数据',
      secondary: hasModelPerformanceData.value
        ? (workbenchCards.value.get('model_performance')?.secondary || '按模型查看当前强弱')
        : '按模型查看当前强弱',
      detailRouteKey: 'engine'
    }
  })
})

const dashboardCardMap = computed(() => {
  return new Map(dashboardOverview.value.cards.map(card => [card.cardKey, card] as const))
})

watch(
  [() => matchedWorkbenchBrandSpace.value?.brandSpaceId || '', analysisRequestKey],
  () => {
    void loadWorkbenchOverview()
  },
  { immediate: true }
)

onMounted(() => {
  load()
  void loadBrandSpaceDirectory()
})

async function loadBrandSpaceDirectory() {
  try {
    await brandSpaceDirectory.ensureLoaded()
  } catch {
    // Controlled by the shared directory error state rendered above the dashboard.
  }
}

async function loadWorkbenchOverview() {
  workbenchOverviewError.value = ''

  const brandSpaceId = matchedWorkbenchBrandSpace.value?.brandSpaceId || ''
  const request = analysisRequest.value
  if (!brandSpaceId) {
    workbenchOverview.value = null
    return
  }
  if (!request) {
    return
  }

  workbenchOverviewLoading.value = true
  try {
    const overview = await workbenchApi.overview(brandSpaceId, request)
    if (matchedWorkbenchBrandSpace.value?.brandSpaceId !== brandSpaceId) {
      return
    }
    workbenchOverview.value = overview
    syncPlatformSelection(platformOptions.value)
  } catch (error) {
    workbenchOverviewError.value = resolveErrorMessage(error, '总览真实数据加载失败')
    workbenchOverview.value = null
  } finally {
    workbenchOverviewLoading.value = false
  }
}

function sortCompetitorRows(left: DashboardCompetitorCardRow, right: DashboardCompetitorCardRow) {
  if (left.rank != null && right.rank != null) {
    return left.rank - right.rank
  }
  if (left.rank != null) {
    return -1
  }
  if (right.rank != null) {
    return 1
  }
  return (right.rate ?? -1) - (left.rate ?? -1)
}

function onWorkbenchScroll(event: Event) {
  const target = event.target as HTMLElement | null
  workbenchScrolled.value = (target?.scrollTop || 0) > 6
}

async function goNewBrandSpace() {
  await navigateTo('/brands/new')
}

async function goPage(path: string) {
  await navigateTo(path)
}

function currentDashboardBrandSpaceId() {
  return matchedWorkbenchBrandSpace.value?.brandSpaceId
    || workbenchOverview.value?.brandSpaceId
    || (typeof route.query.brandSpaceId === 'string' ? route.query.brandSpaceId : '')
    || ''
}

function latestDashboardRunId() {
  return latestTrendPoint.value?.runId
    || workbenchOverview.value?.latestRunId
    || latestInsight.value?.runId
    || null
}

function dashboardMetricLabel(metricKey?: string | null, fallback?: string | null) {
  if (fallback) {
    return fallback
  }
  const normalized = String(metricKey || '').trim()
  const map: Record<string, string> = {
    visibilityScore: 'AI 可见度',
    abvScore: 'AI 可见度',
    recommendRate: 'AI 推荐率',
    mentionRate: 'AI 提及率',
    top3Rate: 'Top3 出现率',
    citationShare: '引用占比'
  }
  return map[normalized] || normalized || '当前结果'
}

function resolveDashboardSourceDomain(url?: string | null, fallback?: string | null) {
  const source = String(url || '').trim()
  if (source) {
    try {
      return new URL(source).hostname || source
    } catch {
      return source
    }
  }
  return fallback || null
}

function openDashboardProvenance(
  anchorType: string,
  anchorKey: string,
  metricKey?: string | null,
  runId?: string | null,
  metricValue?: string | null,
  metricLabel?: string | null,
  contextOverrides?: Partial<{
    domain: string | null
    contextCitationCount: number | null
    contextCitationDomainCount: number | null
    contextTotalCitationCount: number | null
    contextCitationShare: number | null
    contextSampleSize: number | null
    contextMentionRate: number | null
    contextRecommendRate: number | null
    contextTop3Rate: number | null
    platform: string | null
  }>
) {
  const brandSpaceId = currentDashboardBrandSpaceId()
  if (!brandSpaceId) {
    return
  }
  const context = analysisRequest.value
  const metricContext = buildDashboardMetricProvenanceContext(metricKey)
  void openProvenanceCard({
    brandSpaceId,
    ...(context || { timePreset: selectedTimePreset.value }),
    runId: runId || null,
    anchorType,
    anchorKey,
    metricKey: metricKey || null,
    metricLabel: dashboardMetricLabel(metricKey, metricLabel),
    metricValue: metricValue || null,
    resultPointKey: `${anchorType}:${anchorKey}:${metricKey || 'metric'}`,
    ...metricContext,
    ...(contextOverrides || {})
  })
}

function buildDashboardMetricProvenanceContext(metricKey?: string | null) {
  if (!['visibilityScore', 'mentionRate', 'recommendRate', 'top3Rate'].includes(String(metricKey || ''))) {
    return {}
  }
  const current = workbenchOverview.value?.currentPerformance
  return {
    contextSampleSize: workbenchOverview.value?.latestInsight?.sampleSize ?? null,
    contextMentionRate: current?.mentionRate ?? null,
    contextRecommendRate: current?.recommendRate ?? null,
    contextTop3Rate: current?.top3Rate ?? null
  }
}

function dashboardCompetitorMetricKey(row: DashboardCompetitorCardRow) {
  const candidate = String(row.rankingMetric || '').trim()
  if (['visibilityScore', 'recommendRate', 'mentionRate', 'top3Rate', 'positiveRate'].includes(candidate)) {
    return candidate
  }
  if (row.visibilityScore != null && row.rate === row.visibilityScore) {
    return 'visibilityScore'
  }
  if (row.recommendRate != null && row.rate === row.recommendRate) {
    return 'recommendRate'
  }
  if (row.mentionRate != null && row.rate === row.mentionRate) {
    return 'mentionRate'
  }
  return 'recommendRate'
}

function openDashboardCompetitorProvenance(row: DashboardCompetitorCardRow) {
  const metricKey = dashboardCompetitorMetricKey(row)
  openDashboardProvenance(
    'dashboard_competitor_row',
    row.key,
    metricKey,
    null,
    row.value,
    `${row.brandName} ${dashboardMetricLabel(metricKey)}`,
    {
      contextSampleSize: workbenchOverview.value?.latestInsight?.sampleSize ?? null,
      contextMentionRate: row.mentionRate,
      contextRecommendRate: row.recommendRate,
      contextTop3Rate: metricKey === 'visibilityScore' ? null : null
    }
  )
}

function openDashboardModelPerformanceProvenance(row: DashboardModelPerformanceRow) {
  const metricKey = selectedModelPerformanceMetricKey.value === 'abvScore'
    ? 'visibilityScore'
    : selectedModelPerformanceMetricKey.value
  openDashboardProvenance(
    'dashboard_model_performance_row',
    row.key,
    metricKey,
    row.latestRunId,
    row.value,
    `${row.label} ${dashboardMetricLabel(metricKey)}`,
    {
      platform: row.label,
      contextSampleSize: row.sampleSize,
      contextMentionRate: row.mentionRate,
      contextRecommendRate: row.recommendRate,
      contextTop3Rate: row.top3Rate
    }
  )
}

function buildDashboardDetailLocation(cardKey: DashboardCardKey) {
  const brandSpaceId = currentDashboardBrandSpaceId()
  const location = DASHBOARD_DETAIL_LOCATION_BY_CARD[cardKey]
  const fallbackPath = DASHBOARD_DETAIL_ROUTE_PATH[resolveDashboardCard(cardKey)?.detailRouteKey || 'engine']
  const query = {
    ...(brandSpaceId ? { brandSpaceId } : {}),
    ...(location?.query || {})
  }

  return {
    path: location?.path || fallbackPath,
    query
  }
}

function resolveDashboardCard(cardKey: DashboardCardKey) {
  return dashboardCardMap.value.get(cardKey)
}

async function goDashboardCardDetail(cardKey: DashboardCardKey) {
  const card = resolveDashboardCard(cardKey)
  if (!card) {
    return
  }
  await navigateTo(buildDashboardDetailLocation(cardKey))
}

function resolveDashboardDetailRouteKey(value?: string | null): DashboardDetailRouteKey | null {
  if (!value) {
    return null
  }
  if (value === 'engine' || value === 'probe_analysis' || value === 'sources') {
    return value
  }
  return null
}

function trendIcon(direction: TrendDirection) {
  if (direction === 'up') {
    return 'i-lucide-trending-up'
  }
  if (direction === 'down') {
    return 'i-lucide-trending-down'
  }
  return 'i-lucide-move-right'
}

function buildTrendSeries(name: string, color: string, data: Array<number | null>) {
  return {
    name,
    type: 'line' as const,
    smooth: true,
    symbol: 'circle',
    symbolSize: 7,
    data,
    lineStyle: {
      width: 2,
      color
    },
    itemStyle: {
      color
    }
  }
}

function normalizeNullableRate(value?: number | null) {
  if (value == null || Number.isNaN(value)) {
    return null
  }
  return value <= 1 ? value * 100 : value
}

function computeDeltaValue(current: number | null, previous: number | null) {
  return current != null && previous != null ? current - previous : null
}

function resolveDeltaDirection(delta: number | null): TrendDirection {
  if (delta == null) {
    return 'none'
  }
  if (delta > 0.1) {
    return 'up'
  }
  if (delta < -0.1) {
    return 'down'
  }
  return 'flat'
}

function clampRate(value: number) {
  return Math.max(0, Math.min(100, value))
}

function formatPercent(value: number | null, withSuffix = true) {
  if (value == null || Number.isNaN(value)) {
    return '--'
  }
  const fixed = value.toFixed(1)
  return withSuffix ? `${fixed}%` : fixed
}

function formatDelta(value: number | null, withSuffix = true) {
  if (value == null) {
    return '无对比'
  }
  if (Math.abs(value) < 0.1) {
    return '持平'
  }
  const prefix = value > 0 ? '+' : ''
  const fixed = `${prefix}${value.toFixed(1)}`
  return withSuffix ? `${fixed}%` : fixed
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return '--'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(new Date(value))
}

function resolveErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}
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
            <div class="brand-zone__row">
              <div class="brand-zone__title">
                <h1>
                  <WikiGeoWikiTrigger entrypoint-id="dashboard.page.title">
                    {{ resolveNavPageTitleByPath('/dashboard', 'AI 可见度总览') }}
                  </WikiGeoWikiTrigger>
                </h1>
              </div>
              <label
                class="brand-select-wrap"
                for="dashboard-brand-select"
              >
                <select
                  id="dashboard-brand-select"
                  v-model="selectedBrandKey"
                  class="select-input"
                  :disabled="!dashboardBrandOptions.length"
                >
                  <option
                    value=""
                    disabled
                  >
                    {{ dashboardBrandOptions.length ? '选择品牌空间' : '暂无品牌空间' }}
                  </option>
                  <option
                    v-if="requestedBrandMissingOption"
                    :value="requestedBrandMissingOption.key"
                    disabled
                  >
                    {{ requestedBrandMissingOption.label }}
                  </option>
                  <option
                    v-for="brand in dashboardBrandOptions"
                    :key="`dashboard-brand-${brand.key}`"
                    :value="brand.key"
                  >
                    {{ brand.label }}
                  </option>
                </select>
              </label>
            </div>
            <span class="brand-zone__meta">
              {{ dashboardBrandMetaText }}
            </span>
          </div>
          <div class="head-actions">
            <button
              type="button"
              class="cta-primary"
              @click="goNewBrandSpace"
            >
              新增品牌
            </button>
          </div>
        </header>

        <WorkbenchAnalysisToolbar
          v-if="!pageError && !showBrandSelectionPromptState && !showBrandSpaceEmptyState"
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
        v-if="workbenchOverviewError"
        class="state-row state-row--muted"
      >
        {{ workbenchOverviewError }}
      </div>
      <div
        v-if="showBrandSelectionPromptState"
        class="state-row"
      >
        先选择品牌空间，再查看当前账号下的 AI 可见度总览。
      </div>
      <div
        v-else-if="showBrandSpaceEmptyState"
        class="state-row"
      >
        还没有品牌空间，先新增品牌，再进入总览。
      </div>

      <template v-if="!pageError && !showBrandSelectionPromptState && !showBrandSpaceEmptyState">
        <section class="surface section-surface summary-surface">
          <div class="summary-grid">
            <article class="focus-card">
              <div class="surface-head surface-head--top-anchor">
                <div class="surface-head__title">
                  <div class="title-with-help">
                    <strong>{{ resolveDashboardCard('current_performance')?.title || '当前表现' }}</strong>
                  </div>
                </div>
                <button
                  type="button"
                  class="card-detail-link"
                  @click="goDashboardCardDetail('current_performance')"
                >
                  查看详情
                </button>
              </div>
              <template v-if="isDashboardCardReady('current_performance')">
                <div class="focus-card__score-row">
                  <div class="focus-card__score-block">
                    <WikiGeoWikiTrigger
                      entrypoint-id="dashboard.metric.ai_visibility"
                      class="metric-label-btn focus-card__label metric-label metric-label--hint"
                    >
                      AI 可见度
                    </WikiGeoWikiTrigger>
	                    <button
	                      type="button"
	                      class="provenance-value-trigger focus-card__score"
	                      @click="openDashboardProvenance('dashboard_metric', 'current_performance', 'visibilityScore', null, focusScoreValue, 'AI 可见度')"
	                    >
                      {{ focusScoreValue }}
                    </button>
                  </div>
                  <div
                    class="focus-gauge"
                    aria-label="AI 可见度仪表盘"
                  >
                    <svg
                      viewBox="0 0 240 144"
                      class="focus-gauge__svg"
                      role="img"
                    >
                      <path
                        class="focus-gauge__track"
                        d="M36 120 A84 84 0 0 1 204 120"
                        pathLength="100"
                      />
                      <path
                        class="focus-gauge__progress"
                        d="M36 120 A84 84 0 0 1 204 120"
                        :stroke-dasharray="focusGaugeDashArray"
                        :stroke-dashoffset="focusGaugeDashOffset"
                      />
                      <line
                        class="focus-gauge__needle"
                        x1="120"
                        y1="120"
                        :x2="focusGaugeNeedlePoint.x"
                        :y2="focusGaugeNeedlePoint.y"
                      />
                      <circle
                        class="focus-gauge__needle-cap"
                        cx="120"
                        cy="120"
                        r="4.5"
                      />
                    </svg>
                    <div class="focus-gauge__center">
                      <span class="focus-gauge__value">
                        {{ focusScoreValue }}
                      </span>
                    </div>
                  </div>
                </div>

                <div class="focus-card__meta">
                  <span
                    class="focus-card__delta"
                    :class="{
                      'focus-card__delta--up': focusScoreDeltaDirection === 'up',
                      'focus-card__delta--down': focusScoreDeltaDirection === 'down'
                    }"
                  >
                    <UIcon
                      :name="trendIcon(focusScoreDeltaDirection)"
                      class="size-4"
                    />
                    <span>较上期 {{ focusScoreDeltaLabel }}</span>
                  </span>
                </div>

                <div class="focus-metric-row">
                  <article
                    v-for="card in summarySignalCards"
                    :key="card.key"
                    class="focus-metric-card"
                  >
                    <WikiGeoWikiTrigger
                      :entrypoint-id="resolveDashboardSummaryMetricEntrypoint(card.key)"
                      class="metric-label-btn focus-metric-card__title metric-label metric-label--hint"
                    >
                      {{ card.title }}
                    </WikiGeoWikiTrigger>
	                    <button
	                      type="button"
	                      class="provenance-value-trigger focus-metric-card__value"
	                      @click="openDashboardProvenance('dashboard_metric', card.key, card.metricKey, null, card.value, card.title)"
	                    >
                      {{ card.value }}
                    </button>
                    <span
                      class="focus-metric-card__delta"
                      :class="{
                        'focus-metric-card__delta--up': card.deltaDirection === 'up',
                        'focus-metric-card__delta--down': card.deltaDirection === 'down'
                      }"
                    >
                      <UIcon
                        :name="trendIcon(card.deltaDirection)"
                        class="size-3.5"
                      />
                      <span>{{ card.deltaLabel }}</span>
                    </span>
                  </article>
                </div>
              </template>
              <div
                v-else
                class="dashboard-card-state dashboard-card-state--focus"
              >
                {{ resolveDashboardCardShell('current_performance').message }}
              </div>
            </article>

            <div class="signal-grid">
              <article class="signal-card signal-card--trend-swap">
                <div class="surface-head surface-head--top-anchor">
                  <div class="surface-head__title">
                    <div class="title-with-help">
                      <strong>
                        <WikiGeoWikiTrigger entrypoint-id="dashboard.module.trend">
                          {{ resolveDashboardCard('trend')?.title || '变化趋势' }}
                        </WikiGeoWikiTrigger>
                      </strong>
                    </div>
                  </div>
                  <button
                    type="button"
                    class="card-detail-link"
                    @click="goDashboardCardDetail('trend')"
                  >
                    查看详情
                  </button>
                </div>

                <div
                  v-if="isDashboardCardReady('trend')"
                  class="chart-wrap chart-wrap--summary"
                >
	                  <GeoLazyEChart
	                    :option="trendOption"
	                    :height="360"
	                    empty-text="趋势样本不足"
	                  />
                </div>
                <div
                  v-else
                  class="chart-state chart-state--summary"
                >
                  {{ resolveDashboardCardShell('trend').message }}
                </div>
              </article>

              <article class="signal-card">
                <div class="signal-card__head">
                  <div class="signal-card__head-top">
                    <div class="signal-card__title-row">
                      <span class="signal-card__title">{{ resolveDashboardCard('priorities')?.title || '最新洞察结论' }}</span>
                      <span
                        class="signal-card__badge"
                        aria-label="magic"
                      >
                        <UIcon
                          name="i-lucide-sparkles"
                          class="signal-card__badge-icon"
                        />
                      </span>
                    </div>
                  </div>
                </div>
                <div
                  v-if="isDashboardCardReady('priorities')"
                  class="latest-insight"
                >
                  <p class="latest-insight__headline">
                    {{ latestInsightHeadline }}
                  </p>
                  <div class="latest-insight__meta-grid">
                    <article
                      v-for="item in latestInsightMetaItems"
                      :key="item.key"
                      class="latest-insight__meta-item"
                    >
                      <span class="latest-insight__meta-label">{{ item.label }}</span>
                      <span class="latest-insight__meta-value">{{ item.value }}</span>
                    </article>
                  </div>
                </div>
                <div
                  v-else
                  class="dashboard-card-state dashboard-card-state--signal"
                >
                  {{ resolveDashboardCardShell('priorities').message }}
                </div>
              </article>
            </div>
          </div>
        </section>

        <section
          id="data-overview"
          class="surface section-surface"
        >
          <div class="overview-panels">
            <article class="overview-panel overview-panel--plain">
              <div class="overview-signal-grid">
                <article class="overview-signal-card">
                  <div class="signal-card__head">
                    <div class="signal-card__head-top">
                      <span class="signal-card__title title-with-help title-with-help--inline">
                        <span>{{ resolveDashboardCard('competitor_rank')?.title || '竞品排名' }}</span>
                      </span>
                      <button
                        type="button"
                        class="card-detail-link"
                        @click="goDashboardCardDetail('competitor_rank')"
                      >
                        查看详情
                      </button>
                    </div>
                  </div>
                  <div
                    v-if="isDashboardCardReady('competitor_rank')"
                    class="rank-card rank-card--scrollable"
                  >
                    <div class="rank-card__summary">
                      <strong class="rank-card__rank">{{ competitorRankLabel }}</strong>
                    </div>
                    <div class="rank-card__list">
                      <article
                        v-for="row in competitorCardRows"
                        :key="`overview-${row.key}`"
                        class="rank-row"
                        :class="{ 'rank-row--target': row.isTarget }"
                      >
                        <span class="rank-row__index">#{{ row.rank ?? competitorCardRows.findIndex(item => item.key === row.key) + 1 }}</span>
                        <span class="rank-row__name">{{ row.brandName }}</span>
                        <span class="rank-row__value">
                          {{ row.value }}
                        </span>
                      </article>
                    </div>
                  </div>
                  <div
                    v-else
                    class="overview-card-state"
                  >
                    {{ resolveDashboardCardShell('competitor_rank').message }}
                  </div>
                </article>

                <article class="overview-signal-card">
                  <div class="signal-card__head">
                    <div class="signal-card__head-top">
                      <span class="signal-card__title title-with-help title-with-help--inline">
                        <WikiGeoWikiTrigger entrypoint-id="dashboard.module.source_citations">
                          {{ resolveDashboardCard('source_citations')?.title || '引用来源' }}
                        </WikiGeoWikiTrigger>
                      </span>
                      <button
                        type="button"
                        class="card-detail-link"
                        @click="goDashboardCardDetail('source_citations')"
                      >
                        查看详情
                      </button>
                    </div>
                    <span class="signal-card__summary">{{ resolveDashboardCard('source_citations')?.secondary || '等待来源结构' }}</span>
                  </div>
                  <div
                    v-if="isDashboardCardReady('source_citations')"
                    class="signal-card__rows signal-card__rows--source-offset"
                  >
                    <div
                      v-for="row in sourceCardRows"
                      :key="`overview-${row.key}`"
                      class="compact-bar-row"
                    >
                      <div class="compact-bar-row__head">
                        <span class="compact-bar-row__label">{{ row.label }}</span>
                        <span class="compact-bar-row__value">
                          {{ row.percentLabel }}
                        </span>
                      </div>
                      <div class="compact-bar-row__track">
                        <span
                          class="compact-bar-row__fill compact-bar-row__fill--source"
                          :style="{ width: `${row.percent}%` }"
                        />
                      </div>
                    </div>
                  </div>
                  <div
                    v-else
                    class="overview-card-state"
                  >
                    {{ resolveDashboardCardShell('source_citations').message }}
                  </div>
                </article>
              </div>
            </article>

            <article class="overview-panel">
              <div class="surface-head surface-head--top-anchor">
                <div class="surface-head__title">
                  <div class="title-with-help">
                    <strong>
                      <WikiGeoWikiTrigger entrypoint-id="dashboard.module.model_performance">
                        {{ resolveDashboardCard('model_performance')?.title || '模型表现' }}
                      </WikiGeoWikiTrigger>
                    </strong>
                  </div>
                  <span>{{ resolveDashboardCard('model_performance')?.secondary || '按模型查看当前强弱' }}</span>
                </div>
                <button
                  type="button"
                  class="card-detail-link"
                  @click="goDashboardCardDetail('model_performance')"
                >
                  查看详情
                </button>
              </div>

              <template v-if="isDashboardCardReady('model_performance')">
                <div
                  class="metric-tab-group"
                  role="tablist"
                  aria-label="模型表现指标"
                >
                  <button
                    v-for="metric in modelPerformanceMetricOptions"
                    :key="metric.key"
                    type="button"
                    class="metric-tab"
                    :class="{ 'metric-tab--active': selectedModelPerformanceMetricKey === metric.key }"
                    @click="selectedModelPerformanceMetricKey = metric.key"
                  >
                    {{ metric.label }}
                  </button>
                </div>

                <div class="model-performance-list">
                  <article
                    v-for="row in modelPerformanceRows"
                    :key="row.key"
                    class="model-performance-row"
                  >
                    <div class="model-performance-row__head">
                      <span class="model-performance-row__name">{{ row.label }}</span>
                      <span
                        class="model-performance-row__delta"
                        :class="{
                          'model-performance-row__delta--up': row.deltaDirection === 'up',
                          'model-performance-row__delta--down': row.deltaDirection === 'down'
                        }"
                      >
                        <UIcon
                          :name="trendIcon(row.deltaDirection)"
                          class="size-3.5"
                        />
                        <span>{{ row.deltaLabel }}</span>
                      </span>
                    </div>
                    <div class="model-performance-row__body">
                      <div class="model-performance-row__track">
                        <span
                          class="model-performance-row__fill"
                          :style="{ width: `${row.rate ?? 0}%` }"
                        />
                      </div>
                      <span class="model-performance-row__value">
                        {{ row.value }}
                      </span>
                    </div>
                  </article>
                </div>
              </template>
              <div
                v-else
                class="dashboard-card-state dashboard-card-state--model"
              >
                {{ resolveDashboardCardShell('model_performance').message }}
              </div>
            </article>
          </div>
        </section>

        <section class="surface section-surface">
          <div class="surface-head">
            <div class="surface-head__title">
              <div class="title-with-help">
                <strong>接下来做什么</strong>
              </div>
            </div>
          </div>

          <div
            v-if="dashboardNextSteps.length"
            class="next-step-grid"
          >
            <article
              v-for="card in dashboardNextSteps"
              :key="card.key"
              class="next-step-card"
            >
              <div class="next-step-card__head">
                <span class="next-step-card__priority">{{ card.priority }}</span>
                <span class="next-step-card__title">{{ card.title }}</span>
              </div>
              <span class="next-step-card__evidence-label">证据</span>
              <span class="next-step-card__evidence">{{ card.evidence }}</span>
              <div
                v-if="card.nextStepLabel || card.nextStepText"
                class="next-step-card__action"
              >
                <span class="next-step-card__evidence-label">下一步</span>
                <div class="next-step-card__action-row">
                  <button
                    v-if="card.path && card.nextStepLabel"
                    type="button"
                    class="next-step-card__action-link"
                    @click="goPage(card.path)"
                  >
                    {{ card.nextStepLabel }}
                  </button>
                  <span
                    v-else-if="card.nextStepLabel"
                    class="next-step-card__action-link next-step-card__action-link--static"
                  >
                    {{ card.nextStepLabel }}
                  </span>
                  <span
                    v-if="card.nextStepText"
                    class="next-step-card__action-text"
                  >
                    {{ card.nextStepText }}
                  </span>
                </div>
              </div>
            </article>
          </div>
          <div
            v-else
            class="dashboard-card-state dashboard-card-state--signal"
          >
            {{ workbenchOverviewLoading ? '正在加载洞察建议' : '本轮还没有可展示的三条洞察' }}
          </div>
        </section>
      </template>
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
.dashboard-shell {
  --dashboard-surface-canvas: var(--traclux-surface-canvas);
  --dashboard-surface-rail: var(--traclux-surface-sidebar);
  --dashboard-surface-card: var(--traclux-surface-card);
  --dashboard-surface-card-hover: var(--traclux-surface-card-hover);
  --dashboard-surface-sticky: var(--traclux-surface-sticky-overlay);
  --dashboard-surface-sticky-rail: var(--traclux-surface-sticky-rail-overlay);
  --dashboard-border-soft: var(--traclux-border-soft);
  --dashboard-border-strong: var(--traclux-border-strong);
  --dashboard-border-subtle: var(--traclux-border-subtle);
  --dashboard-text-primary: var(--traclux-text-primary);
  --dashboard-text-secondary: var(--traclux-text-secondary);
  --dashboard-text-tertiary: var(--traclux-text-tertiary);
  --dashboard-text-quaternary: var(--traclux-text-quaternary);
  --dashboard-font-ui: var(--traclux-font-ui);
  --dashboard-radius: var(--traclux-radius-hard);
  --dashboard-shadow-sticky: var(--traclux-shadow-sticky);
  --engine-shell-surface-canvas: var(--dashboard-surface-canvas);
  --engine-shell-surface-rail: var(--dashboard-surface-rail);
  --engine-shell-surface-card: var(--dashboard-surface-card);
  --engine-shell-surface-sticky: var(--dashboard-surface-sticky);
  --engine-shell-surface-sticky-rail: var(--dashboard-surface-sticky-rail);
  --engine-shell-border-soft: var(--dashboard-border-soft);
  --engine-shell-border-strong: var(--dashboard-border-strong);
  --engine-shell-text-primary: var(--dashboard-text-primary);
  --engine-shell-text-secondary: var(--dashboard-text-secondary);
  --engine-shell-text-tertiary: var(--dashboard-text-tertiary);
  --engine-shell-font-ui: var(--dashboard-font-ui);
  --engine-shell-radius: var(--dashboard-radius);
  --engine-shell-shadow-sticky: var(--dashboard-shadow-sticky);
  --engine-shell-brand-select-width: 180px;
  --engine-shell-sticky-padding: 20px 14px 10px;
}

.nav-account-card {
  margin-top: auto;
  border: 1px solid var(--dashboard-border-soft);
  border-radius: var(--dashboard-radius);
  background: var(--dashboard-surface-card);
  padding: 10px;
  display: grid;
  gap: 8px;
}

.nav-account-card h2 {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  color: var(--dashboard-text-primary);
}

.nav-account-card__meta {
  display: grid;
  gap: 2px;
  padding: 0 8px;
}

.nav-account-card__name {
  font-size: 12px;
  font-weight: 700;
  color: var(--dashboard-text-primary);
}

.nav-account-card__id {
  font-size: 11px;
  color: var(--dashboard-text-secondary);
}

.nav-account-card__actions {
  display: grid;
  gap: 4px;
}

.nav-account-card__logout {
  border-color: var(--dashboard-border-soft);
  color: var(--dashboard-text-primary);
  background: var(--dashboard-surface-card);
}

.nav-account-card__logout:hover {
  border-color: var(--dashboard-border-strong);
  background: var(--dashboard-surface-card-hover);
}

.brand-zone {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.brand-zone__row {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.brand-zone__title {
  display: grid;
  gap: 4px;
}

.brand-zone__meta {
  font-size: 12px;
  color: var(--dashboard-text-secondary);
}

.section-surface {
  display: grid;
  gap: 10px;
}

.surface-head {
  min-height: 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.surface-head--top-anchor {
  min-height: 0;
  align-items: flex-start;
}

.surface-head__title {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.surface-head__context {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.surface-head strong {
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.surface-head span {
  font-size: 12px;
  color: var(--dashboard-text-tertiary);
}

.surface-head__object {
  color: var(--dashboard-text-primary);
  white-space: nowrap;
}

.card-detail-link {
  border: none;
  background: transparent;
  padding: 0;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  font-weight: 500;
  color: var(--traclux-accent-link);
  line-height: 1.1;
  white-space: nowrap;
  cursor: pointer;
}

.card-detail-link::after {
  content: '→';
  font-size: 11px;
  line-height: 1;
}

.card-detail-link:hover {
  color: var(--traclux-accent-link-hover);
  text-decoration: underline;
}

.card-detail-link--disabled {
  color: var(--dashboard-text-tertiary);
  cursor: default;
  pointer-events: none;
}

.card-detail-link--disabled::after {
  content: none;
}

.summary-surface {
  padding: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: minmax(280px, 0.72fr) minmax(0, 1.28fr);
  gap: 10px;
}

.focus-card {
  display: grid;
  gap: 8px;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  padding: 10px;
}

.focus-card__score-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.focus-card__score-block {
  display: grid;
  gap: 6px;
}

.focus-card__label {
  font-size: 12px;
  font-weight: 700;
  color: #4b5563;
}

.metric-label {
  display: inline-flex;
  width: fit-content;
}

.metric-label-btn {
  border: none;
  background: transparent;
  padding: 0;
  margin: 0;
  text-align: left;
}

.title-with-help {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.title-with-help--inline {
  display: inline-flex;
}

.metric-label--hint {
  text-decoration-line: underline;
  text-decoration-style: dashed;
  text-decoration-color: #9ca3af;
  text-underline-offset: 4px;
  cursor: help;
}

.focus-card__score {
  font-family: 'Sora', 'Noto Sans SC', sans-serif;
  font-size: 44px;
  line-height: 0.9;
  letter-spacing: -0.04em;
}

.focus-gauge {
  width: 244px;
  min-height: 152px;
  border: none;
  border-radius: 0;
  background: transparent;
  padding: 0;
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 4px;
}

.focus-gauge__svg {
  width: 100%;
  max-width: 220px;
  height: 112px;
}

.focus-gauge__track {
  fill: none;
  stroke: #dbe4f1;
  stroke-width: 12;
  stroke-linecap: round;
}

.focus-gauge__progress {
  fill: none;
  stroke: #2563eb;
  stroke-width: 12;
  stroke-linecap: round;
  transition: stroke-dashoffset 240ms ease;
}

.focus-gauge__needle {
  stroke: #0f172a;
  stroke-width: 2;
  stroke-linecap: round;
}

.focus-gauge__needle-cap {
  fill: #0f172a;
}

.focus-gauge__avg-point {
  fill: #f59e0b;
  stroke: #ffffff;
  stroke-width: 1.5;
}

.focus-gauge__center {
  margin-top: -4px;
  display: inline-flex;
  align-items: baseline;
  gap: 3px;
  color: #0f172a;
}

.focus-gauge__center strong {
  font-family: 'Sora', 'Noto Sans SC', sans-serif;
  font-size: 20px;
  line-height: 1;
}

.focus-gauge__center span {
  font-size: 12px;
  color: #64748b;
}

.focus-gauge__legend {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: #475569;
}

.focus-gauge__legend-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #f59e0b;
}

.focus-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.focus-card__delta {
  min-height: 28px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  padding: 0 10px;
  font-size: 12px;
  color: #6b7280;
}

.focus-card__delta--up {
  color: #047857;
}

.focus-card__delta--down {
  color: #b91c1c;
}

.focus-metric-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.focus-metric-card {
  min-height: 88px;
  border: none;
  background: transparent;
  box-shadow: none;
  border-radius: 2px;
  padding: 9px;
  display: grid;
  gap: 6px;
  align-content: start;
}

.focus-metric-card__title {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: #111827;
}

.focus-metric-card__value {
  font-family: 'Sora', 'Noto Sans SC', sans-serif;
  font-size: 24px;
  line-height: 1;
  letter-spacing: -0.03em;
  color: #111827;
}

.focus-metric-card__delta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #6b7280;
}

.focus-metric-card__delta--up {
  color: #047857;
}

.focus-metric-card__delta--down {
  color: #b91c1c;
}

.signal-grid {
  --signal-card-height: 408px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  align-items: stretch;
}

.signal-card {
  min-height: var(--signal-card-height);
  border: 1px solid var(--dashboard-border-soft);
  border-radius: var(--dashboard-radius);
  background: var(--dashboard-surface-card);
  padding: 10px;
  display: grid;
  align-content: start;
  gap: 8px;
  align-self: stretch;
  height: var(--signal-card-height);
}

.signal-card--scrollable {
  min-height: 0;
  overflow: hidden;
  grid-template-rows: auto minmax(0, 1fr);
}

.signal-card--trend-swap {
  grid-column: span 2;
}

.signal-card__head {
  display: grid;
  gap: 10px;
}

.signal-card__head-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.signal-card__title-row {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.signal-card__title {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--dashboard-text-primary);
}

.signal-card__badge {
  width: 15px;
  height: 15px;
  display: inline-grid;
  place-items: center;
  line-height: 0;
  color: var(--traclux-accent-soul);
  background: transparent;
  flex: 0 0 auto;
}

.signal-card__badge-icon {
  width: 11px;
  height: 11px;
  display: block;
}

.signal-card__summary {
  font-size: 12px;
  color: var(--dashboard-text-secondary);
}

.signal-card__rows {
  display: grid;
  gap: 8px;
}

.signal-card--scrollable .signal-card__rows {
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
}

.signal-card__rows--source-offset {
  padding-top: 40px;
}

.rank-card {
  display: grid;
  gap: 10px;
}

.rank-card--scrollable {
  height: 100%;
  min-height: 0;
  grid-template-rows: auto minmax(0, 1fr);
}

.rank-card__summary {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.rank-card__rank {
  font-family: 'Sora', 'Noto Sans SC', sans-serif;
  font-size: 32px;
  line-height: 1;
  letter-spacing: -0.03em;
  color: #111827;
}

.rank-card__list {
  display: grid;
  gap: 6px;
}

.rank-card--scrollable .rank-card__list {
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
}

.rank-row {
  min-height: 30px;
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  border-top: 1px solid var(--dashboard-border-subtle);
  padding-top: 6px;
}

.rank-row:first-child {
  border-top: none;
  padding-top: 0;
}

.rank-row--target .rank-row__name,
.rank-row--target .rank-row__value {
  font-weight: 700;
  color: var(--dashboard-text-primary);
}

.rank-row__index,
.rank-row__name {
  font-size: 12px;
  color: var(--dashboard-text-quaternary);
}

.rank-row__value {
  font-family: var(--dashboard-font-ui);
  font-size: 14px;
  line-height: 1;
  letter-spacing: -0.02em;
  color: var(--dashboard-text-primary);
}

.compact-bar-row {
  display: grid;
  gap: 6px;
}

.compact-bar-row__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.compact-bar-row__label {
  font-size: 12px;
  color: var(--dashboard-text-quaternary);
}

.compact-bar-row__label--target {
  font-weight: 700;
  color: var(--dashboard-text-primary);
}

.compact-bar-row__value {
  font-family: var(--dashboard-font-ui);
  font-size: 14px;
  line-height: 1;
  letter-spacing: -0.02em;
  color: var(--dashboard-text-primary);
}

.compact-bar-row__track {
  position: relative;
  min-height: 10px;
  border-radius: 999px;
  overflow: hidden;
  background: var(--dashboard-border-subtle);
}

.compact-bar-row__fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--identity-ink) 0%, var(--traclux-accent-link) 100%);
}

.compact-bar-row__fill--source {
  background: linear-gradient(90deg, var(--traclux-accent-data-teal) 0%, var(--traclux-accent-data-blue) 100%);
}

.latest-insight {
  display: grid;
  gap: 12px;
  padding-top: 24px;
}

.latest-insight__headline {
  margin: 0;
  font-size: 20px;
  line-height: 1.45;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--dashboard-text-primary);
}

.latest-insight__meta-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 8px;
}

.latest-insight__meta-item {
  border: 1px solid var(--dashboard-border-subtle);
  border-radius: var(--dashboard-radius);
  padding: 8px;
  display: grid;
  gap: 6px;
}

.latest-insight__meta-label,
.latest-insight__meta-value {
  font-size: 12px;
}

.latest-insight__meta-label {
  font-weight: 700;
  color: var(--dashboard-text-primary);
}

.latest-insight__meta-value {
  line-height: 1.35;
  color: var(--dashboard-text-secondary);
  word-break: break-all;
}

.overview-panels {
  display: grid;
  grid-template-columns: minmax(0, 1.28fr) minmax(0, 0.72fr);
  gap: 10px;
}

.overview-panel {
  border: 1px solid var(--dashboard-border-soft);
  border-radius: var(--dashboard-radius);
  background: var(--dashboard-surface-card);
  padding: 10px;
  display: grid;
  align-content: start;
  gap: 10px;
}

.overview-panel--plain {
  border: none;
  background: transparent;
  padding: 0;
  gap: 8px;
}

.model-performance-list {
  display: grid;
  gap: 8px;
}

.model-performance-row {
  display: grid;
  gap: 8px;
}

.model-performance-row__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.model-performance-row__name {
  font-size: 13px;
  font-weight: 700;
  color: var(--dashboard-text-primary);
}

.model-performance-row__delta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--dashboard-text-secondary);
}

.model-performance-row__delta--up {
  color: var(--traclux-status-positive);
}

.model-performance-row__delta--down {
  color: var(--traclux-status-negative);
}

.model-performance-row__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
}

.model-performance-row__track {
  position: relative;
  min-height: 12px;
  border-radius: 999px;
  background: var(--dashboard-border-subtle);
  overflow: hidden;
}

.model-performance-row__fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--identity-ink) 0%, var(--traclux-accent-link) 100%);
}

.model-performance-row__value {
  font-family: var(--dashboard-font-ui);
  font-size: 22px;
  line-height: 1;
  letter-spacing: -0.03em;
  color: var(--dashboard-text-primary);
}

.metric-tab-group {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.metric-tab {
  min-height: 28px;
  border: 1px solid var(--dashboard-border-soft);
  border-radius: var(--dashboard-radius);
  background: var(--dashboard-surface-card);
  color: var(--dashboard-text-tertiary);
  font-size: 12px;
  padding: 0 10px;
}

.metric-tab--active {
  border-color: var(--dashboard-text-primary);
  color: var(--dashboard-text-primary);
}

.next-step-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.next-step-card {
  min-height: 172px;
  border: 1px solid var(--dashboard-border-soft);
  border-radius: var(--dashboard-radius);
  background: var(--dashboard-surface-card);
  padding: 12px;
  display: grid;
  align-content: start;
  gap: 8px;
  text-align: left;
}

.next-step-card__head {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.next-step-card__priority {
  width: 20px;
  height: 20px;
  border-radius: 999px;
  background: var(--identity-ink);
  color: var(--identity-ink-inverse);
  font-size: 12px;
  font-weight: 700;
  display: inline-grid;
  place-items: center;
  line-height: 1;
}

.next-step-card__title {
  font-size: 13px;
  font-weight: 700;
  color: var(--dashboard-text-primary);
}

.next-step-card__evidence-label {
  font-size: 12px;
  color: var(--dashboard-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.next-step-card__evidence {
  font-size: 13px;
  line-height: 1.5;
  color: var(--dashboard-text-quaternary);
}

.next-step-card__action {
  display: grid;
  gap: 6px;
}

.next-step-card__action-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.next-step-card__action-link {
  border: none;
  background: transparent;
  padding: 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--dashboard-accent-strong);
  cursor: pointer;
}

.next-step-card__action-link--static {
  cursor: default;
}

.next-step-card__action-text {
  font-size: 13px;
  line-height: 1.5;
  color: var(--dashboard-text-secondary);
}

.selected-row {
  min-height: 42px;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  padding: 0 10px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 94px 160px 90px;
  align-items: center;
  gap: 8px;
}

.selected-row__brand,
.selected-row__time {
  font-size: 12px;
}

.selected-row__abv,
.selected-row__trend {
  justify-self: start;
}

.selected-row--empty {
  display: flex;
  align-items: center;
  color: #6b7280;
}

.trend-meta-row {
  min-height: 30px;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  padding: 0 10px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.trend-meta-row__time {
  font-size: 12px;
  color: #6b7280;
}

.trend-meta-row--empty {
  justify-content: flex-start;
  color: #6b7280;
}

.chart-wrap {
  margin-top: 2px;
}

.chart-wrap--summary {
  margin-top: 0;
}

.chart-state {
  min-height: 360px;
  border: 1px dashed #9ca3af;
  border-radius: 2px;
  display: grid;
  place-items: center;
  font-size: 13px;
  color: #4b5563;
}

.chart-state--compact {
  min-height: 260px;
}

.chart-state--summary {
  min-height: 240px;
}

.dashboard-card-state,
.overview-card-state {
  border: 1px dashed #9ca3af;
  border-radius: 2px;
  display: grid;
  place-items: center;
  text-align: center;
  font-size: 13px;
  color: #4b5563;
  padding: 0 10px;
}

.dashboard-card-state--focus {
  min-height: 324px;
}

.dashboard-card-state--signal {
  min-height: 322px;
}

.dashboard-card-state--model {
  min-height: 220px;
}

.overview-card-state {
  min-height: 0;
  height: 100%;
}

.overview-signal-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.overview-signal-card {
  min-height: 320px;
  height: 320px;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #ffffff;
  padding: 10px;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 8px;
}

.overview-signal-card .rank-card--scrollable {
  min-height: 0;
}

.overview-signal-card .signal-card__rows {
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
}

.brand-side-surface {
  display: grid;
  align-content: start;
  gap: 6px;
}

.side-brand-list {
  display: grid;
  gap: 6px;
  max-height: 382px;
  overflow-y: auto;
  padding-right: 2px;
}

.side-brand-row {
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  min-height: 54px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 54px;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
}

.side-brand-row--active {
  border-color: #9ca3af;
  background: #f9fafb;
}

.side-brand-row__main {
  border: none;
  background: transparent;
  text-align: left;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-rows: auto auto;
  align-items: center;
  gap: 6px 8px;
  padding: 0;
  cursor: pointer;
}

.side-brand-row__name {
  grid-column: 1;
  grid-row: 1;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-brand-row__main .abv-chip {
  grid-column: 2;
  grid-row: 1 / span 2;
  align-self: center;
}

.side-brand-row__main .trend {
  grid-column: 1 / span 2;
  grid-row: 2;
  font-size: 11px;
}

.side-brand-row__action {
  min-height: 28px;
  align-self: center;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.abv-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 24px;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 650;
  color: #4b5563;
}

.abv-chip--a {
  border-color: #059669;
  color: #065f46;
  background: #ecfdf5;
}

.abv-chip--b {
  border-color: #b45309;
  color: #92400e;
  background: #fffbeb;
}

.abv-chip--c {
  border-color: #c2410c;
  color: #9a3412;
  background: #fff7ed;
}

.abv-chip--d {
  border-color: #b91c1c;
  color: #991b1b;
  background: #fef2f2;
}

.trend {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #6b7280;
  font-size: 12px;
  white-space: nowrap;
}

.trend--up {
  color: #047857;
}

.trend--down {
  color: #b91c1c;
}

.task-table {
  border: 1px solid var(--dashboard-border-soft);
  border-radius: var(--dashboard-radius);
  overflow: hidden;
}

.task-table__head,
.task-row {
  display: grid;
  grid-template-columns: minmax(200px, 1fr) 96px 96px 160px;
  align-items: center;
  min-height: 42px;
  gap: 8px;
  padding: 0 10px;
}

.task-table__head {
  background: var(--dashboard-surface-canvas);
  border-bottom: 1px solid var(--dashboard-border-soft);
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--dashboard-text-secondary);
}

.task-row {
  border-bottom: 1px solid var(--dashboard-border-subtle);
  font-size: 12px;
}

.task-row:last-child {
  border-bottom: none;
}

.cta-primary {
  min-height: 38px;
  border: 1px solid var(--identity-ink);
  background: var(--identity-ink);
  color: var(--identity-ink-inverse);
  border-radius: var(--dashboard-radius);
  font-size: 13px;
  font-weight: 650;
  padding: 0 14px;
}

.cta-primary:hover {
  background: #1f2937;
}

.btn-inline,
.btn-plain {
  min-height: 28px;
  border: 1px solid var(--dashboard-border-soft);
  border-radius: var(--dashboard-radius);
  background: var(--dashboard-surface-card);
  color: var(--dashboard-text-quaternary);
  font-size: 12px;
  padding: 0 10px;
}

.btn-inline {
  white-space: nowrap;
}

.btn-inline:hover,
.btn-plain:hover {
  border-color: var(--dashboard-text-primary);
  color: var(--dashboard-text-primary);
}

.btn-inline:disabled,
.btn-plain:disabled,
.cta-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.state-row {
  min-height: 36px;
  border: 1px solid var(--dashboard-border-soft);
  border-radius: var(--dashboard-radius);
  display: flex;
  align-items: center;
  padding: 0 10px;
  font-size: 13px;
}

.state-row--error {
  border-color: #fca5a5;
  background: #fef2f2;
  color: var(--traclux-status-negative);
}

.state-block {
  min-height: 120px;
  border: 1px dashed var(--dashboard-border-strong);
  border-radius: var(--dashboard-radius);
  display: grid;
  place-items: center;
  color: var(--dashboard-text-tertiary);
  font-size: 13px;
}

.provenance-value-trigger {
  appearance: none;
  border: 0;
  border-bottom: 1px dotted currentColor;
  background: linear-gradient(180deg, rgba(20, 184, 166, 0.08), rgba(20, 184, 166, 0.15));
  color: inherit;
  cursor: zoom-in;
  font: inherit;
  text-align: inherit;
}

.provenance-value-trigger:hover {
  background: rgba(20, 184, 166, 0.2);
}

.provenance-value-trigger--gauge {
  color: inherit;
  font-weight: inherit;
}

@media (max-width: 1280px) {
  .dashboard-shell {
    grid-template-columns: 214px minmax(0, 1fr);
  }
}

@media (max-width: 1080px) {
  .focus-metric-row,
  .summary-grid,
  .overview-panels,
  .overview-signal-grid,
  .signal-grid,
  .next-step-grid,
  .task-table__head,
  .task-row {
    grid-template-columns: 1fr;
  }

  .signal-card--trend-swap {
    grid-column: auto;
  }

  .focus-gauge {
    width: 100%;
    max-width: 280px;
    justify-self: start;
  }
}
</style>
