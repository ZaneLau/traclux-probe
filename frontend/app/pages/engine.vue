<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { EChartsOption, SeriesOption } from 'echarts'
import type { AssetRow, BrandTrackingSeed } from '~/types/engine'
import EnginePlatformsTab from '~/components/engine/EnginePlatformsTab.vue'
import EngineSentimentTab from '~/components/engine/EngineSentimentTab.vue'
import EngineTopicsTab from '~/components/engine/EngineTopicsTab.vue'
import WorkbenchAnalysisToolbar from '~/components/workbench/WorkbenchAnalysisToolbar.vue'
import WorkbenchDataProvenanceCard from '~/components/workbench/WorkbenchDataProvenanceCard.vue'
import {
  buildWorkbenchPlatformOptions,
  useWorkbenchAnalysisFilters,
  type WorkbenchPlatformOption
} from '~/composables/useWorkbenchAnalysisFilters'
import { resolveNavPageTitleByPath } from '~/composables/useDashboardNav'
import { resolveWorkbenchIdentity, syncWorkbenchIdentity } from '~/composables/useWorkbenchIdentity'
import type {
  WorkbenchDataProvenanceCardQuery,
  WorkbenchBrandPerformanceTopicSummary,
  WorkbenchBrandPerformanceView,
  WorkbenchTrendPoint
} from '~/types/workbench'

definePageMeta({
  layout: false,
  middleware: ['auth'],
  requiresAuth: true
})

useSeoMeta({
  title: `${resolveNavPageTitleByPath('/engine', '品牌表现')} · Traclux 探镜`
})

const route = useRoute()
const workbenchApi = useWorkbenchApi()
const brandSpaceDirectory = useWorkbenchBrandSpacesDirectory()
const workbenchRef = ref<HTMLElement | null>(null)
const workbenchScrolled = ref(false)
const activeDimension = ref<'topics' | 'platforms' | 'sentiment'>('topics')
const engineDimensionTabs = [
  { key: 'topics', label: '主题' },
  { key: 'platforms', label: '平台' },
  { key: 'sentiment', label: '情绪' }
] as const
const activeDimensionLabel = computed(
  () => engineDimensionTabs.find(item => item.key === activeDimension.value)?.label || ''
)
const isPlatformsTab = computed(() => activeDimension.value === 'platforms')
const isSentimentTab = computed(() => activeDimension.value === 'sentiment')
const workbenchBrandPerformance = ref<WorkbenchBrandPerformanceView | null>(null)
const workbenchBrandPerformanceLoading = ref(false)
const workbenchBrandPerformanceError = ref('')
const {
  provenanceCard,
  provenanceError,
  provenanceLoading,
  provenanceOpen,
  openProvenanceCard,
  openProvenanceSamplePage,
  retryProvenanceCard
} = useWorkbenchDataProvenanceCard()
const showWorkbenchBrandPerformanceContent = computed(() =>
  Boolean(workbenchBrandPerformance.value) || !workbenchBrandPerformanceLoading.value
)
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

const debounceTimer: ReturnType<typeof setTimeout> | null = null

function resolveEngineDimension(value?: string | null) {
  if (value === 'topics' || value === 'platforms' || value === 'sentiment') {
    return value
  }
  return 'topics'
}

function normalizeNullableNumber(value?: number | null) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

const {
  brandSelectionOptions,
  matchedBrandSpace,
  requestedBrandMissingOption,
  requestedBrandSpaceMissing,
  selectedBrandLabel,
  selectedBrandRouteOptionKey,
  workbenchBoundBrandSpaceId
} = useWorkbenchBrandSelection(
  computed(() => typeof route.query.brandSpaceId === 'string' ? route.query.brandSpaceId : '')
)
const hasSelectedBrandSpace = computed(() => Boolean(workbenchBoundBrandSpaceId.value))
const useWorkbenchOnlySections = computed(() => hasSelectedBrandSpace.value)
const hasRealBrandSpaceOptions = computed(() => brandSelectionOptions.value.length > 0)
const showBrandSpaceEmptyState = computed(() => {
  return brandSpaceDirectory.loaded.value
    && !brandSpaceDirectory.loading.value
    && !brandSpaceDirectory.error.value
    && !hasRealBrandSpaceOptions.value
})
const showBrandSelectionPromptState = computed(() => {
  return brandSpaceDirectory.loaded.value
    && !brandSpaceDirectory.loading.value
    && !brandSpaceDirectory.error.value
    && hasRealBrandSpaceOptions.value
    && !requestedBrandSpaceMissing.value
    && !hasSelectedBrandSpace.value
})
const brandSpaceDirectoryError = computed(() => {
  if (requestedBrandSpaceMissing.value) {
    return '未找到对应品牌空间'
  }
  return brandSpaceDirectory.error.value || ''
})

function buildWorkbenchTrendOption(points: WorkbenchTrendPoint[]): EChartsOption | null {
  if (points.length < 2) {
    return null
  }

  const labels = points.map(item => item.label)
  const buildSeries = (name: string, color: string, values: Array<number | null>): SeriesOption => ({
    name,
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 6,
    data: values,
    lineStyle: { width: 2, color },
    itemStyle: { color }
  })

  return {
    grid: { top: 28, right: 18, bottom: 24, left: 32, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#111827',
      borderColor: '#1f2937',
      textStyle: { color: '#f9fafb', fontSize: 11 },
      valueFormatter: value => (typeof value === 'number' ? `${value.toFixed(2)}%` : '--')
    },
    legend: {
      top: 0,
      left: 0,
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: '#6b7280', fontSize: 11 }
    },
    xAxis: {
      type: 'category',
      data: labels,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#6b7280', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#9ca3af', fontSize: 11, formatter: '{value}%' },
      splitLine: { lineStyle: { color: '#f3f4f6' } }
    },
    series: [
      buildSeries('AI 可见度', '#111827', points.map(item => normalizeNullableNumber(item.visibilityScore))),
      buildSeries('AI 推荐率', '#2563eb', points.map(item => normalizeNullableNumber(item.recommendRate))),
      buildSeries('AI 提及率', '#0f766e', points.map(item => normalizeNullableNumber(item.mentionRate))),
      buildSeries('Top3 出现率', '#f97316', points.map(item => normalizeNullableNumber(item.top3Rate)))
    ]
  }
}

function buildWorkbenchShareOfVoiceOption(
  rows: Array<{ brandName: string, shareOfVoice?: number | null }>
): EChartsOption | null {
  const visibleRows = rows
    .filter(item => typeof item.shareOfVoice === 'number' && Number.isFinite(item.shareOfVoice))
    .slice(0, 6)

  if (!visibleRows.length) {
    return null
  }

  return {
    backgroundColor: 'transparent',
    grid: {
      left: 34,
      right: 16,
      top: 18,
      bottom: 40,
      containLabel: true
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      },
      backgroundColor: '#111827',
      borderColor: '#1f2937',
      textStyle: {
        color: '#f9fafb'
      },
      formatter: '{b}<br/>声量占比：{c}%'
    },
    xAxis: {
      type: 'category',
      data: visibleRows.map(row => row.brandName),
      axisLabel: {
        color: '#6b7280',
        fontSize: 11,
        interval: 0
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
        fontSize: 11,
        formatter: '{value}%'
      },
      splitLine: {
        lineStyle: {
          color: '#f3f4f6'
        }
      }
    },
    series: [
      {
        name: '声量占比',
        type: 'bar',
        barWidth: 22,
        itemStyle: {
          color: '#111827',
          borderRadius: [2, 2, 0, 0]
        },
        data: visibleRows.map(row => row.shareOfVoice)
      }
    ]
  }
}

function buildWorkbenchAveragePositionOption(
  rows: Array<{ brandName: string, averagePosition?: number | null }>
): EChartsOption | null {
  const visibleRows = rows
    .filter(item => typeof item.averagePosition === 'number' && Number.isFinite(item.averagePosition))
    .slice(0, 6)

  if (!visibleRows.length) {
    return null
  }

  const maxValue = Math.max(...visibleRows.map(row => row.averagePosition || 0), 1.5)

  return {
    backgroundColor: 'transparent',
    grid: {
      left: 34,
      right: 16,
      top: 18,
      bottom: 40,
      containLabel: true
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      },
      backgroundColor: '#111827',
      borderColor: '#1f2937',
      textStyle: {
        color: '#f9fafb'
      },
      formatter: '{b}<br/>平均位次：{c}'
    },
    xAxis: {
      type: 'category',
      data: visibleRows.map(row => row.brandName),
      axisLabel: {
        color: '#6b7280',
        fontSize: 11,
        interval: 0
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
      max: Math.min(10, Math.max(3.5, Number((maxValue + 0.8).toFixed(1)))),
      splitNumber: 4,
      axisLabel: {
        color: '#6b7280',
        fontSize: 11
      },
      splitLine: {
        lineStyle: {
          color: '#f3f4f6'
        }
      }
    },
    series: [
      {
        name: '平均位次',
        type: 'bar',
        barWidth: 22,
        itemStyle: {
          color: '#111827',
          borderRadius: [2, 2, 0, 0]
        },
        data: visibleRows.map(row => row.averagePosition)
      }
    ]
  }
}

async function loadWorkbenchBrandPerformance() {
  const brandSpaceId = matchedBrandSpace.value?.brandSpaceId || ''
  const previousView = workbenchBrandPerformance.value
  const loadedBrandSpaceId = previousView?.brandSpaceId || ''
  const switchingBrandSpace = Boolean(brandSpaceId && loadedBrandSpaceId && loadedBrandSpaceId !== brandSpaceId)
  const request = analysisRequest.value
  if (!brandSpaceId) {
    workbenchBrandPerformance.value = null
    workbenchBrandPerformanceError.value = ''
    return
  }
  if (!request) {
    workbenchBrandPerformanceError.value = ''
    return
  }

  workbenchBrandPerformanceLoading.value = true
  workbenchBrandPerformanceError.value = ''
  try {
    if (switchingBrandSpace) {
      workbenchBrandPerformance.value = null
    }
    const view = await workbenchApi.brandPerformance(brandSpaceId, request)
    workbenchBrandPerformance.value = view
    syncWorkbenchIdentity(view)
    syncPlatformSelection(platformOptions.value)
  } catch (error) {
    workbenchBrandPerformanceError.value = error instanceof Error && error.message ? error.message : '品牌表现真实数据加载失败'
    workbenchBrandPerformance.value = switchingBrandSpace ? null : previousView
  } finally {
    workbenchBrandPerformanceLoading.value = false
  }
}

async function loadBrandSpaceDirectory() {
  try {
    await brandSpaceDirectory.ensureLoaded()
  } catch {
    // Directory errors are surfaced through the shared error state above the page body.
  }
}

const workbenchSummary = computed(() => workbenchBrandPerformance.value?.summary || null)
const workbenchCompetitorRows = computed(() => workbenchBrandPerformance.value?.competitorRows || [])
const workbenchTrendPoints = computed(() => workbenchBrandPerformance.value?.trendPoints || [])
const workbenchTopicSummaries = computed<WorkbenchBrandPerformanceTopicSummary[]>(() => {
  return workbenchBrandPerformance.value?.topicSummaries || []
})
const workbenchModelPerformanceRows = computed(() => workbenchBrandPerformance.value?.modelPerformance || [])
const workbenchIdentity = computed(() => resolveWorkbenchIdentity(workbenchBrandPerformance.value))
const platformOptions = computed<WorkbenchPlatformOption[]>(() => {
  const analysisPlatforms = workbenchBrandPerformance.value?.analysisContext?.availablePlatforms || []
  return buildWorkbenchPlatformOptions(analysisPlatforms)
})
const analysisRequest = computed(() => buildRequest(platformOptions.value))
const analysisRequestKey = computed(() => serializeRequest(analysisRequest.value))

function handleAnalysisTimePreset(value: string) {
  selectedTimePreset.value = value as typeof selectedTimePreset.value
}
const workbenchDataAvailability = computed(() => workbenchBrandPerformance.value?.dataAvailability || null)
const workbenchMissingFields = computed(() => workbenchDataAvailability.value?.missingFields || [])
const workbenchBlockingMissingFieldSet = new Set([
  'summary',
  'competitorRows',
  'trendPoints'
])
const workbenchBlockingMissingFields = computed(() => {
  return workbenchMissingFields.value.filter(field => workbenchBlockingMissingFieldSet.has(field))
})
const workbenchHasBlockingPartialState = computed(() => {
  return workbenchDataAvailability.value?.status === 'partial' && workbenchBlockingMissingFields.value.length > 0
})

const visibilityScoreView = computed(() => {
  return workbenchSummary.value?.visibilityScore ?? null
})

const visibilityDeltaView = computed(() => {
  const points = workbenchTrendPoints.value
  if (points.length >= 2) {
    const latest = normalizeNullableNumber(points[points.length - 1]?.visibilityScore)
    const previous = normalizeNullableNumber(points[points.length - 2]?.visibilityScore)
    if (latest != null && previous != null) {
      return latest - previous
    }
  }
  return null
})

const visibilityRankView = computed(() => {
  return workbenchSummary.value?.visibilityRank
    ?? workbenchCompetitorRows.value.find(item => item.isTarget)?.rank
    ?? null
})

const rankDeltaView = computed(() => {
  return workbenchSummary.value?.visibilityRankDelta
    ?? workbenchCompetitorRows.value.find(item => item.isTarget)?.rankDelta
    ?? null
})

const visibilityTrendOptionView = computed(() => {
  return buildWorkbenchTrendOption(workbenchTrendPoints.value)
})

const assetRowsView = computed<AssetRow[]>(() => {
  if (workbenchCompetitorRows.value.length) {
    return workbenchCompetitorRows.value.map((item, index) => ({
      rank: item.rank || index + 1,
      asset: item.brandName,
      score: item.visibilityScore ?? item.shareOfVoice ?? item.recommendRate ?? item.mentionRate ?? 0,
      change: item.shareOfVoiceDelta ?? 0
    }))
  }
  return []
})

const selectedBrandDisplay = computed(() => {
  return workbenchIdentity.value?.brandName || workbenchBrandPerformance.value?.brandName || ''
})

const visibilityHasCoverage = computed(() => {
  return Boolean(workbenchBrandPerformance.value)
})

const selectedSovShareView = computed(() => {
  return workbenchSummary.value?.shareOfVoice ?? null
})

const sovOptionView = computed(() => {
  return buildWorkbenchShareOfVoiceOption(workbenchCompetitorRows.value)
})

const selectedAveragePositionView = computed(() => {
  return workbenchSummary.value?.averagePosition ?? null
})

const averagePositionUnavailableTextView = computed(() => {
  const details = workbenchDataAvailability.value?.missingFieldDetails
  return details?.averagePosition?.[0]
    || details?.competitorAveragePosition?.[0]
    || null
})

const averagePositionOptionView = computed(() => {
  return buildWorkbenchAveragePositionOption(workbenchCompetitorRows.value)
})

const topicVisibilityRowsView = computed(() => {
  return []
})

const workbenchHeadlineView = computed(() => workbenchSummary.value?.headline || '')
const workbenchIssueCountView = computed(() => workbenchSummary.value?.issueCount ?? null)
const workbenchEvidenceCountView = computed(() => workbenchSummary.value?.evidenceCount ?? null)
const workbenchTopTopicLabelsView = computed(() => workbenchSummary.value?.topTopicLabels || [])

const chartConfigOptions: Array<{ key: string, label: string }> = []
const selectedChartConfig = ref('score')
const rankingColumns = Array.from({ length: 10 }, (_, index) => index + 1)
const emptyPeecBrandRows: BrandTrackingSeed[] = []

function setChartConfig(value: string) {
  selectedChartConfig.value = value
}

function formatSigned(value: number | null, suffix = '') {
  if (value == null || !Number.isFinite(value)) {
    return '--'
  }
  const normalized = Number(value.toFixed(1))
  return `${normalized > 0 ? '+' : ''}${normalized.toFixed(1)}${suffix}`
}

function isTabActive(tab: { key: typeof activeDimension.value }) {
  return activeDimension.value === tab.key
}

function isTopicExpanded() {
  return false
}

function toggleTopicDetails() {
  // /engine visible path now shows workbench topic summaries only.
}

function topicDetailRows() {
  return []
}

function openTopicInsight() {
  // Topic insight remains on theater/demo paths, not the live /engine route.
}

async function goNewBrand() {
  await navigateTo('/brands/new')
}

async function setActiveDimension(next: 'topics' | 'platforms' | 'sentiment') {
  activeDimension.value = next
  if (route.query.tab === next) {
    return
  }
  await navigateTo(
    {
      path: route.path,
      query: {
        ...route.query,
        tab: next
      }
    },
    { replace: true }
  )
}

function onWorkbenchScroll(event: Event) {
  const target = event.target as HTMLElement | null
  workbenchScrolled.value = (target?.scrollTop || 0) > 6
}

interface EngineMetricProvenanceContext {
  sampleSize?: number | null
  mentionCount?: number | null
  mentionRate?: number | null
  recommendRate?: number | null
  top3Rate?: number | null
}

function engineMetricLabel(metricKey?: string | null, fallback?: string | null) {
  if (fallback) {
    return fallback
  }
  const map: Record<string, string> = {
    visibilityScore: 'AI 可见度',
    recommendRate: '推荐率',
    mentionRate: '提及率',
    top3Rate: 'Top3 出现率',
    positiveRate: '正向率'
  }
  return map[String(metricKey || '').trim()] || '当前结果'
}

function openEngineProvenance(
  anchorKey: string,
  metricKey?: string | null,
  metricValue?: string | null,
  metricLabel?: string | null,
  platform?: string | null,
  metricContext?: EngineMetricProvenanceContext
) {
  const brandSpaceId = workbenchBoundBrandSpaceId.value
  if (!brandSpaceId) {
    return
  }
  const context = analysisRequest.value
  const query: WorkbenchDataProvenanceCardQuery = {
    brandSpaceId,
    ...(context || { timePreset: selectedTimePreset.value }),
    runId: null,
    anchorType: 'brand_performance_point',
    anchorKey,
    metricKey: metricKey || null,
    metricLabel: engineMetricLabel(metricKey, metricLabel),
    metricValue: metricValue || null,
    platform: platform || null,
    resultPointKey: `brand_performance_point:${anchorKey}:${metricKey || 'metric'}`
  }
  if (metricContext) {
    query.contextSampleSize = metricContext.sampleSize ?? null
    query.contextMentionCount = metricContext.mentionCount ?? null
    query.contextMentionRate = metricContext.mentionRate ?? null
    query.contextRecommendRate = metricContext.recommendRate ?? null
    query.contextTop3Rate = metricContext.top3Rate ?? null
  }
  void openProvenanceCard(query)
}

function openEngineTopicProvenance(
  anchorKey: string,
  metricKey: string,
  topicId?: string | null,
  metricValue?: string | null,
  metricLabel?: string | null,
  topicContext?: EngineMetricProvenanceContext
) {
  const brandSpaceId = workbenchBoundBrandSpaceId.value
  if (!brandSpaceId) {
    return
  }
  const context = analysisRequest.value
  const query: WorkbenchDataProvenanceCardQuery = {
    brandSpaceId,
    ...(context || { timePreset: selectedTimePreset.value }),
    runId: null,
    topicId: topicId || anchorKey,
    topicName: anchorKey,
    anchorType: 'brand_performance_topic_metric',
    anchorKey,
    metricKey,
    metricLabel: engineMetricLabel(metricKey, metricLabel),
    metricValue: metricValue || null,
    resultPointKey: `brand_performance_topic_metric:${anchorKey}:${metricKey}`
  }
  if (topicContext) {
    query.contextSampleSize = topicContext.sampleSize ?? null
    query.contextMentionCount = topicContext.mentionCount ?? null
    query.contextMentionRate = topicContext.mentionRate ?? null
    query.contextRecommendRate = topicContext.recommendRate ?? null
    query.contextTop3Rate = topicContext.top3Rate ?? null
  }
  void openProvenanceCard(query)
}

watch([workbenchBoundBrandSpaceId, analysisRequestKey], () => {
  void loadWorkbenchBrandPerformance()
})

watch(
  () => typeof route.query.tab === 'string' ? route.query.tab : '',
  (tab) => {
    activeDimension.value = resolveEngineDimension(tab)
  },
  { immediate: true }
)

onMounted(async () => {
  await loadBrandSpaceDirectory()
  if (showBrandSpaceEmptyState.value || showBrandSelectionPromptState.value || brandSpaceDirectoryError.value) {
    return
  }
  void loadWorkbenchBrandPerformance()
})

onBeforeUnmount(() => {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
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
              <WikiGeoWikiTrigger entrypoint-id="engine.page.title">
                {{ resolveNavPageTitleByPath('/engine', '品牌表现') }}
              </WikiGeoWikiTrigger>
            </h1>
            <label
              class="brand-select-wrap"
              for="engine-brand-select"
            >
              <select
                id="engine-brand-select"
                v-model="selectedBrandRouteOptionKey"
                class="select-input"
                :disabled="!brandSelectionOptions.length"
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
                  :key="brand.key"
                  :value="brand.key"
                >{{ brand.label }}</option>
              </select>
            </label>
          </div>
        </header>

        <WorkbenchAnalysisToolbar
          v-if="!showBrandSpaceEmptyState && !showBrandSelectionPromptState && !brandSpaceDirectoryError"
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

        <section
          v-if="!showBrandSpaceEmptyState && !showBrandSelectionPromptState && !brandSpaceDirectoryError"
          class="surface tabs-surface"
        >
          <button
            v-for="tab in engineDimensionTabs"
            :key="tab.key"
            type="button"
            class="tab-btn"
            :class="isTabActive(tab) ? 'tab-btn--active' : ''"
            @click="setActiveDimension(tab.key)"
          >
            {{ tab.label }}
          </button>
        </section>
      </div>

      <div
        v-if="!showBrandSpaceEmptyState && !showBrandSelectionPromptState && !brandSpaceDirectoryError && workbenchBrandPerformanceLoading"
        class="sync-notice"
      >
        正在加载品牌表现真实数据。
      </div>
      <div
        v-else-if="!showBrandSpaceEmptyState && !showBrandSelectionPromptState && !brandSpaceDirectoryError && workbenchBrandPerformanceError"
        class="sync-notice"
      >
        {{ workbenchBrandPerformanceError }}<template v-if="workbenchBrandPerformance">
          ，已保留当前页面缓存数据。
        </template>
      </div>
      <div
        v-else-if="!showBrandSpaceEmptyState && !showBrandSelectionPromptState && brandSpaceDirectoryError"
        class="sync-notice"
      >
        {{ brandSpaceDirectoryError }}，请先检查品牌空间目录与登录态。
      </div>
      <div
        v-if="!showBrandSpaceEmptyState && !showBrandSelectionPromptState && !brandSpaceDirectoryError && workbenchHasBlockingPartialState"
        class="sync-notice sync-notice--warn"
      >
        品牌表现真实数据已接通，但当前仍缺少 {{ workbenchBlockingMissingFields.length ? workbenchBlockingMissingFields.join('、') : '部分结构化字段' }}。
        {{ workbenchDataAvailability?.message || '这意味着页面里的部分结构仍然不能按真实品牌完整展开。' }}
      </div>
      <div
        v-if="showBrandSelectionPromptState"
        class="empty-state"
      >
        <strong>先选择品牌空间</strong>
        <span>选择一个品牌空间后，这里才会展示对应品牌的真实品牌表现结果。</span>
      </div>

      <div
        v-if="showBrandSpaceEmptyState"
        class="empty-state"
      >
        <strong>还没有品牌空间</strong>
        <span>先新增品牌，再进入品牌表现查看真实主题、平台和情绪结果。</span>
        <button
          type="button"
          class="cta-primary"
          @click="goNewBrand"
        >
          新增品牌
        </button>
      </div>
      <template
        v-if="!showBrandSpaceEmptyState
          && !showBrandSelectionPromptState
          && !brandSpaceDirectoryError
          && showWorkbenchBrandPerformanceContent
          && isPlatformsTab"
      >
        <EnginePlatformsTab
          :selected-brand="selectedBrandDisplay"
          :workbench-model-rows="workbenchModelPerformanceRows"
          @open-provenance="openEngineProvenance"
        />
      </template>

      <template
        v-else-if="!showBrandSpaceEmptyState
          && !showBrandSelectionPromptState
          && !brandSpaceDirectoryError
          && showWorkbenchBrandPerformanceContent
          && isSentimentTab"
      >
        <EngineSentimentTab
          :selected-brand="selectedBrandDisplay"
          :workbench-competitor-rows="workbenchCompetitorRows"
          @open-provenance="openEngineProvenance"
        />
      </template>

      <template
        v-else-if="!showBrandSpaceEmptyState
          && !showBrandSelectionPromptState
          && !brandSpaceDirectoryError
          && showWorkbenchBrandPerformanceContent"
      >
        <EngineTopicsTab
          :has-coverage="visibilityHasCoverage"
          :selected-brand="selectedBrandDisplay"
          :last-generated-at="workbenchBrandPerformance?.lastGeneratedAt || null"
          :topic-summary-rows="workbenchTopicSummaries"
          @open-provenance="openEngineTopicProvenance"
        />
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
