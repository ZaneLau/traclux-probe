<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import ChatEvidenceOverlay from '~/components/engine/ChatEvidenceOverlay.vue'
import ProbeDetailOverlay from '~/components/engine/ProbeDetailOverlay.vue'
import WorkbenchAnalysisToolbar from '~/components/workbench/WorkbenchAnalysisToolbar.vue'
import WorkbenchDataProvenanceCard from '~/components/workbench/WorkbenchDataProvenanceCard.vue'
import WorkbenchProblemAnalysisRichTabs from '~/components/workbench/WorkbenchProblemAnalysisRichTabs.vue'
import { resolveNavPageTitleByPath } from '~/composables/useDashboardNav'
import { useProblemAnalysisViewState } from '~/composables/useProblemAnalysisViewState'
import { syncWorkbenchIdentity } from '~/composables/useWorkbenchIdentity'
import type {
  BrandPromptTopicSeed,
  PromptColumnKey,
  ProbeStatus,
  PromptMetricRow,
  PromptTopicGroup
} from '~/types/engine'
import type {
  WorkbenchDataProvenanceCardQuery,
  WorkbenchProblemAnalysisView
} from '~/types/workbench'
import {
  useWorkbenchAnalysisFilters,
  type WorkbenchAnalysisRequest,
  type WorkbenchFilterOption,
  type WorkbenchPlatformOption
} from '~/composables/useWorkbenchAnalysisFilters'
import {
  buildProductModelDisplayMap,
  resolveProductModelDisplayLabel,
  resolveProductModelLabel
} from '~/utils/productModels'

definePageMeta({
  layout: false,
  middleware: ['auth'],
  requiresAuth: true
})

useSeoMeta({
  title: `${resolveNavPageTitleByPath('/probe-analysis', '问题分析')} · Traclux 探镜`
})

const route = useRoute()
const brandSpaceDirectory = useWorkbenchBrandSpacesDirectory()
const workbenchApi = useWorkbenchApi()
const workbenchRef = ref<HTMLElement | null>(null)
const workbenchScrolled = ref(false)

const {
  promptVisibleColumns,
  resolvePromptCellValue,
  selectedPromptTopics
} = useProblemAnalysisViewState()
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

const promptRuntime = reactive({
  loading: false,
  error: null as string | null,
  lastSync: 0
})

interface WorkbenchProblemAnalysisFilterCatalog {
  brandSpaceId: string
  topicRows: WorkbenchProblemAnalysisView['topicRows']
  promptRows: WorkbenchProblemAnalysisView['promptRows']
  catalog?: WorkbenchProblemAnalysisView['catalog']
}

const UNKNOWN_PROMPT_METRIC = Number.NaN
const problemAnalysisVolumeGranularity = 'day' as const
const activeProbeId = ref<string | null>(null)
const activeChatId = ref<string | null>(null)
const activeChatScope = ref<'record' | 'probe'>('record')
const activeChatProbeId = ref<string | null>(null)
const probeDeepLinkError = ref('')
const workbenchExpandedPromptTopics = ref<string[]>([])
const workbenchProblemAnalysisCatalog = ref<WorkbenchProblemAnalysisFilterCatalog | null>(null)
const workbenchProblemAnalysisCatalogRequestKey = ref('')
const workbenchProblemAnalysis = ref<WorkbenchProblemAnalysisView | null>(null)
const workbenchProblemAnalysisLoading = ref(false)
const workbenchProblemAnalysisError = ref('')
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
const requestedProbeDetailId = computed(() => typeof route.query.probeId === 'string' ? route.query.probeId : '')
const requestedAttemptDetailId = computed(() => typeof route.query.attemptId === 'string' ? route.query.attemptId : '')
const normalizedRequestedAttemptDetailId = computed(() => {
  const attemptId = requestedAttemptDetailId.value.trim()
  if (!attemptId) {
    return ''
  }
  return attemptId.startsWith('ka-attempt-') ? attemptId.slice('ka-attempt-'.length) : attemptId
})
const effectiveSelectedBrand = computed(() => {
  return workbenchProblemAnalysis.value?.brandName
    || selectedBrandLabel.value
    || matchedBrandSpace.value?.brandName
    || ''
})
const hasSelectedBrandSpace = computed(() => Boolean(workbenchBoundBrandSpaceId.value))
const isWorkbenchProblemAnalysisMode = computed(() => hasSelectedBrandSpace.value)
const richRoleView = computed(() => workbenchProblemAnalysis.value?.rich?.roles || workbenchProblemAnalysis.value?.rich?.audience || null)
const richOpportunityView = computed(() => workbenchProblemAnalysis.value?.rich?.opportunity || workbenchProblemAnalysis.value?.rich?.fanout || null)
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
const brandSpaceSelectionError = computed(() => {
  if (requestedBrandSpaceMissing.value) {
    return '未找到对应品牌空间'
  }
  return brandSpaceDirectory.error.value || ''
})
const problemAnalysisAvailability = computed(() => workbenchProblemAnalysis.value?.dataAvailability || null)
const problemAnalysisIsPartial = computed(() => problemAnalysisAvailability.value?.status === 'partial')
const problemAnalysisMissingFields = computed(() => problemAnalysisAvailability.value?.missingFields || [])
const PROBLEM_ANALYSIS_MISSING_FIELD_LABELS: Record<string, string> = {
  'promptRows': '问题',
  'topicRows': '主题',
  'probeRows': '探针',
  'attemptRows': '执行记录',
  'evidenceRows': '引用证据'
}
const problemAnalysisSummary = computed(() => workbenchProblemAnalysis.value?.summary || null)
const problemAnalysisSummaryStats = computed(() => {
  const summary = problemAnalysisSummary.value
  if (!summary) {
    return []
  }
  return [
    {
      key: 'prompt',
      label: '问题',
      total: summary.promptCount ?? summary.linkedPromptCount ?? 0,
      mapped: summary.linkedPromptCount ?? null
    },
    {
      key: 'probe',
      label: '探针',
      total: summary.probeCount ?? summary.linkedProbeCount ?? 0,
      mapped: summary.linkedProbeCount ?? null
    },
    {
      key: 'evidence',
      label: '证据',
      total: summary.evidenceCount ?? summary.linkedEvidenceCount ?? 0,
      mapped: summary.linkedEvidenceCount ?? null
    }
  ]
})

const hasWorkbenchProblemAnalysis = computed(() => Boolean(workbenchProblemAnalysis.value))
const workbenchProblemRich = computed(() => workbenchProblemAnalysis.value?.rich || null)

const realBrandPromptTopicSeeds = computed<BrandPromptTopicSeed[]>(() => {
  const view = workbenchProblemAnalysisCatalog.value
  if (!view) {
    return []
  }

  const promptRowsByTopic = new Map<string, string[]>()
  for (const row of view.promptRows || []) {
    const topicId = row.topicId || 'uncategorized'
    const current = promptRowsByTopic.get(topicId) || []
    if (row.promptText) {
      current.push(row.promptText)
    }
    promptRowsByTopic.set(topicId, current)
  }

  const topicSeeds = (view.topicRows || [])
    .slice()
    .sort((left, right) => (left.seqNo ?? 9999) - (right.seqNo ?? 9999))
    .map((topic, index) => ({
      topicId: topic.topicId || `topic-${index + 1}`,
      topic: topic.topicName || `主题 ${index + 1}`,
      prompts: promptRowsByTopic.get(topic.topicId) || []
    }))
    .filter(topic => topic.prompts.length)

  return topicSeeds
})

function normalizeProbeStatus(value?: string | null): ProbeStatus {
  if (!value) {
    return 'suggested'
  }
  const status = value.toLowerCase()
  if (['inactive', 'disabled', 'paused'].includes(status)) {
    return 'inactive'
  }
  if (['completed', 'complete', 'captured', 'running', 'active'].includes(status)) {
    return 'active'
  }
  return 'suggested'
}

function resolveWorkbenchProbePrompt(row: WorkbenchProblemAnalysisView['probeRows'][number]) {
  return row.promptText?.trim() || '未绑定问题'
}

function normalizePromptMetricValue(value?: number | null) {
  return typeof value === 'number' && Number.isFinite(value)
    ? value
    : UNKNOWN_PROMPT_METRIC
}

function resolveWorkbenchPromptCellDeltaView(row: PromptMetricRow, key: PromptColumnKey) {
  void row
  void key
  return null
}

function resolveWorkbenchPromptDeltaClassView(row: PromptMetricRow, key: PromptColumnKey) {
  void row
  void key
  return ''
}

function buildWorkbenchPromptMetricRow(
  promptRow: WorkbenchProblemAnalysisView['promptRows'][number],
  topicName: string,
  fallbackId: string
) {
  const hasRawAnswer = promptRow.hasRawAnswer === true
  const hasPromptMetrics = hasRawAnswer
    || Number.isFinite(promptRow.visibilityRank)
    || Number.isFinite(promptRow.visibilityScore)
    || Number.isFinite(promptRow.averagePosition)
    || Number((promptRow.evidenceCount ?? 0)) > 0
  const hasAveragePosition = Number.isFinite(promptRow.averagePosition)
  const citationMetricsUnavailable = problemAnalysisMissingFields.value.includes('evidenceRows')

  return {
    id: promptRow.promptId || fallbackId,
    topic: topicName,
    prompt: promptRow.promptText,
    visibilityRank: hasPromptMetrics
      ? normalizePromptMetricValue(promptRow.visibilityRank)
      : UNKNOWN_PROMPT_METRIC,
    visibilityScore: hasRawAnswer
      ? normalizePromptMetricValue(promptRow.visibilityScore)
      : UNKNOWN_PROMPT_METRIC,
    visibilityDelta: hasPromptMetrics
      ? normalizePromptMetricValue(promptRow.visibilityDelta)
      : UNKNOWN_PROMPT_METRIC,
    averagePosition: hasAveragePosition
      ? normalizePromptMetricValue(promptRow.averagePosition)
      : UNKNOWN_PROMPT_METRIC,
    averageDelta: hasAveragePosition
      ? normalizePromptMetricValue(promptRow.averageDelta)
      : UNKNOWN_PROMPT_METRIC,
    citationShare: citationMetricsUnavailable
      ? UNKNOWN_PROMPT_METRIC
      : normalizePromptMetricValue(promptRow.citationShare),
    citationDelta: citationMetricsUnavailable
      ? UNKNOWN_PROMPT_METRIC
      : normalizePromptMetricValue(promptRow.citationDelta)
  }
}

const workbenchProbeOverlayRows = computed(() => {
  const view = workbenchProblemAnalysis.value
  if (!view) {
    return []
  }

  const rows = displayWorkbenchProbeRows.value
    .slice()
    .sort((left, right) => (left.seqNo ?? 9999) - (right.seqNo ?? 9999))
    .map((row, index) => {
      const prompt = resolveWorkbenchProbePrompt(row)
      const topic = row.topicName || '未分组问题'
      const evidenceCount = row.evidenceCount ?? 0

      return {
        id: row.probeId || `probe-${index + 1}`,
        brand: view.brandName,
        topicId: row.topicId || null,
        promptId: row.promptId || null,
        topic,
        prompt,
        status: normalizeProbeStatus(row.status),
        visibilityScore: row.visibilityScore ?? null,
        sentimentScore: row.sentimentScore ?? null,
        averagePosition: row.averagePosition ?? null,
        mentionCount: row.mentionCount ?? evidenceCount,
        evidenceCount,
        visibilityTrend: [],
        createdAt: view.lastGeneratedAt || null,
        refs: {
          taskId: view.latestRunId || undefined,
          probeId: row.probeId || undefined,
          topicId: row.topicId || undefined
        }
      }
    })

  return rows
})

const problemAnalysisPlatformDisplayMap = computed<Record<string, string>>(() => {
  const view = workbenchProblemAnalysis.value
  if (!view) {
    return {}
  }

  return buildProductModelDisplayMap([
    ...(view.attemptRows || []).map(item => item.model || item.provider),
    ...displayWorkbenchProbeRows.value.map(item => item.model),
    ...(view.rich?.volume?.platformRows || []).map(item => item.platform),
    ...(richRoleView.value?.platformRows || []).map(item => item.platform),
    ...((richOpportunityView.value?.platformRows || []).map(item => item.platform)),
    ...((richOpportunityView.value?.promptRows || []).flatMap(item => (item.platformRows || []).map(row => row.platform)))
  ])
})

function resolveProblemAnalysisPlatformDisplay(value?: string | null) {
  return resolveProductModelDisplayLabel(value, problemAnalysisPlatformDisplayMap.value)
}

function normalizeProblemAnalysisPlatformKey(value?: string | null) {
  return typeof value === 'string' ? value.trim() : ''
}

const workbenchChatEvidenceRows = computed(() => {
  const view = workbenchProblemAnalysis.value
  if (!view) {
    return []
  }

  return displayWorkbenchAttemptRows.value
    .slice()
    .sort((left, right) => new Date(right.createdAt || 0).getTime() - new Date(left.createdAt || 0).getTime())
    .map((attempt, index) => {
      const probeId = attempt.probeId || `probe-${index + 1}`
      const rawPlatform = normalizeProblemAnalysisPlatformKey(attempt.model || attempt.provider || null)
      return {
        id: attempt.attemptId || `real-chat-${probeId}-${index + 1}`,
        brand: view.brandName,
        probeId,
        model: resolveProblemAnalysisPlatformDisplay(rawPlatform),
        rawPlatform,
        query: attempt.query || null,
        answer: attempt.rawAnswer || null,
        summary: attempt.summaryBullets || [],
        createdAt: attempt.createdAt || null,
        citations: (attempt.citations || []).map((citation, citationIndex) => ({
          id: citation.evidenceId || `${probeId}-evidence-${citationIndex + 1}`,
          title: citation.title || citation.domain || '引用证据',
          url: citation.url || '',
          domain: citation.domain || null
        })),
        brandHits: (attempt.brandHits || []).map(hit => ({
          brand: hit.brandName,
          score: hit.score ?? 0
        })),
        refs: {
          taskId: view.latestRunId || undefined,
          probeId,
          invocationId: attempt.attemptId || undefined,
          topicId: attempt.topicId || undefined,
          chatId: attempt.attemptId || undefined
        }
      }
    })
})

const effectiveProbeRows = computed(() => {
  return workbenchProbeOverlayRows.value
})

const effectiveChatEvidenceRows = computed(() => {
  return workbenchChatEvidenceRows.value
})

const effectiveBrandRanks = computed(() => [])

const realPromptTopicKeys = computed(() => realBrandPromptTopicSeeds.value.map(item => item.topic))

const effectiveResolvedPromptTopics = computed(() => {
  const selected = selectedPromptTopics.value.filter(topic => realPromptTopicKeys.value.includes(topic))
  return selected.length ? selected : realPromptTopicKeys.value
})
const workbenchProblemAnalysisCatalogPending = computed(() =>
  isWorkbenchProblemAnalysisMode.value
  && Boolean(workbenchBoundBrandSpaceId.value)
  && !workbenchProblemAnalysisCatalog.value
)
const workbenchPromptTopicContractMissing = computed(() =>
  isWorkbenchProblemAnalysisMode.value
  && Boolean(workbenchProblemAnalysisCatalog.value?.promptRows?.length)
  && realBrandPromptTopicSeeds.value.length === 0
)

const effectiveVisiblePromptTopics = computed(() => {
  return effectiveResolvedPromptTopics.value
})

const problemAnalysisBackendScopeQuery = computed(() => {
  const validTopics = selectedPromptTopics.value.filter(topic => realPromptTopicKeys.value.includes(topic))
  return {
    topicNames: validTopics.length > 0 && validTopics.length < realPromptTopicKeys.value.length
      ? validTopics
      : undefined
  }
})

function resolveProblemAnalysisTimestamp(value?: string | null) {
  if (!value) {
    return null
  }
  const timestamp = new Date(value).getTime()
  return Number.isFinite(timestamp) ? timestamp : null
}

const activeProblemAnalysisContext = computed(() =>
  workbenchProblemAnalysis.value?.analysisContext || null
)

const problemAnalysisWindowStartMs = computed(() => {
  const dateFrom = activeProblemAnalysisContext.value?.dateFrom
  if (!dateFrom) {
    return null
  }
  return resolveProblemAnalysisTimestamp(`${dateFrom}T00:00:00`)
})

const displayWorkbenchPromptRows = computed(() => workbenchProblemAnalysis.value?.promptRows || [])

const displayWorkbenchProbeRows = computed(() => workbenchProblemAnalysis.value?.probeRows || [])

const displayWorkbenchAttemptRows = computed(() => workbenchProblemAnalysis.value?.attemptRows || [])

const displayWorkbenchEvidenceRows = computed(() => workbenchProblemAnalysis.value?.evidenceRows || [])

const displayWorkbenchTopicRows = computed(() => workbenchProblemAnalysis.value?.topicRows || [])

const realPromptTopicGroups = computed<PromptTopicGroup[]>(() => {
  if (!workbenchProblemAnalysis.value) {
    return []
  }
  const rowsByTopicId = new Map<string, WorkbenchProblemAnalysisView['promptRows']>()
  for (const row of displayWorkbenchPromptRows.value) {
    const topicId = row.topicId || 'uncategorized'
    const current = rowsByTopicId.get(topicId) || []
    current.push(row)
    rowsByTopicId.set(topicId, current)
  }

  const groups = displayWorkbenchTopicRows.value
    .slice()
    .sort((left, right) => (left.seqNo ?? 9999) - (right.seqNo ?? 9999))
    .map((topic) => {
      const promptRows = (rowsByTopicId.get(topic.topicId) || [])
        .slice()
        .sort((left, right) => (left.seqNo ?? 9999) - (right.seqNo ?? 9999))
        .map((promptRow, promptIndex) =>
          buildWorkbenchPromptMetricRow(
            promptRow,
            topic.topicName || '未分组问题',
            `${topic.topicId}-${promptIndex + 1}`
          )
        )

      if (!promptRows.length) {
        return null
      }

      const summary = promptRows.length === 1
        ? (() => {
            const onlyRow = promptRows[0]!
            return {
              id: `${topic.topicId}-summary`,
              topic: topic.topicName || '未分组问题',
              prompt: `${topic.topicName || '未分组问题'} 摘要`,
              visibilityRank: onlyRow.visibilityRank,
              visibilityScore: onlyRow.visibilityScore,
              visibilityDelta: onlyRow.visibilityDelta,
              averagePosition: onlyRow.averagePosition,
              averageDelta: onlyRow.averageDelta,
              citationShare: onlyRow.citationShare,
              citationDelta: onlyRow.citationDelta
            }
          })()
        : {
            id: `${topic.topicId}-summary`,
            topic: topic.topicName || '未分组问题',
            prompt: `${topic.topicName || '未分组问题'} 摘要`,
            visibilityRank: UNKNOWN_PROMPT_METRIC,
            visibilityScore: UNKNOWN_PROMPT_METRIC,
            visibilityDelta: UNKNOWN_PROMPT_METRIC,
            averagePosition: UNKNOWN_PROMPT_METRIC,
            averageDelta: UNKNOWN_PROMPT_METRIC,
            citationShare: UNKNOWN_PROMPT_METRIC,
            citationDelta: UNKNOWN_PROMPT_METRIC
          }

      return {
        topic: topic.topicName || '未分组问题',
        displayTopic: topic.topicName || '未分组问题',
        rows: promptRows,
        summary
      }
    })
    .filter((group): group is PromptTopicGroup => Boolean(group))

  return groups
})

const effectivePromptTopicGroups = computed(() => {
  return realPromptTopicGroups.value
})

const workbenchPromptTableGridStyle = computed(() => ({
  gridTemplateColumns: `minmax(420px, 2.8fr) repeat(${promptVisibleColumns.value.length}, minmax(124px, 1fr))`
}))

const effectivePromptTotalCount = computed(() => {
  return effectivePromptTopicGroups.value.reduce((sum, group) => sum + group.rows.length, 0)
})

const effectiveHasPromptCoverage = computed(() => {
  return Boolean(effectivePromptTopicGroups.value.length)
})

const effectiveExpandedPromptTopics = computed(() => {
  if (!effectivePromptTopicGroups.value.length) {
    return []
  }
  const validTopics = new Set(effectivePromptTopicGroups.value.map(group => group.topic))
  return workbenchExpandedPromptTopics.value.filter(topic => validTopics.has(topic))
})

const effectivePromptTopicScopeOptions = computed(() => {
  return realBrandPromptTopicSeeds.value.map(item => ({
    key: item.topic,
    label: item.topic,
    volume: item.prompts.length
  }))
})

const effectivePromptTopicScopeLabel = computed(() => {
  if (workbenchProblemAnalysisCatalogPending.value) {
    return '主题目录同步中'
  }
  if (workbenchPromptTopicContractMissing.value) {
    return '主题待原生目录'
  }
  if (!realPromptTopicKeys.value.length) {
    return '暂无主题'
  }
  return effectiveResolvedPromptTopics.value.length === realPromptTopicKeys.value.length
    ? '全部主题'
    : `已选 ${effectiveResolvedPromptTopics.value.length} 个主题`
})

const realPlatformScopeOptions = computed(() => {
  const view = workbenchProblemAnalysis.value
  if (!view) {
    return []
  }

  const counts = new Map<string, { label: string, volume: number }>()
  for (const option of buildWorkbenchPlatformOptions(view.analysisContext?.availablePlatforms || [])) {
    counts.set(option.key, {
      label: option.label,
      volume: 0
    })
  }
  const pushCount = (platform?: string | null, weight = 1) => {
    const key = normalizeProblemAnalysisPlatformKey(platform)
    if (!key) {
      return
    }
    const label = resolveProblemAnalysisPlatformDisplay(platform)
    const current = counts.get(key)
    counts.set(key, {
      label: current?.label || label || key,
      volume: (current?.volume || 0) + Math.max(weight, 1)
    })
  }

  for (const attempt of view.attemptRows || []) {
    pushCount(attempt.model || attempt.provider, 1)
  }
  for (const probe of view.probeRows || []) {
    pushCount(probe.model, 1)
  }
  if (!counts.size) {
    for (const row of view.rich?.volume?.platformRows || []) {
      pushCount(row.platform, row.value ?? 1)
    }
  }
  for (const row of richOpportunityView.value?.platformRows || []) {
    const platformKey = normalizeProblemAnalysisPlatformKey(row.platform)
    if (!counts.has(platformKey)) {
      pushCount(row.platform, 1)
    }
  }
  for (const row of richRoleView.value?.platformRows || []) {
    const platformKey = normalizeProblemAnalysisPlatformKey(row.platform)
    if (!counts.has(platformKey)) {
      pushCount(row.platform, 1)
    }
  }

  return Array.from(counts.entries())
    .map(([key, entry]) => ({ key, label: entry.label, volume: entry.volume }))
    .sort((left, right) => right.volume - left.volume || left.key.localeCompare(right.key))
})

const effectivePlatformScopeOptions = computed(() => {
  return realPlatformScopeOptions.value
})

const problemAnalysisPlatformOptionsForRequest = computed<WorkbenchPlatformOption[]>(() =>
  effectivePlatformScopeOptions.value.map(option => ({
    key: option.key,
    label: option.label
  }))
)

const problemAnalysisAnalysisRequest = computed<WorkbenchAnalysisRequest | null>(() => {
  return buildRequest(problemAnalysisPlatformOptionsForRequest.value)
})

const problemAnalysisAnalysisRequestKey = computed(() => {
  const request = problemAnalysisAnalysisRequest.value
  if (!request) {
    return 'pending'
  }
  return serializeRequest(request)
})

const problemAnalysisToolbarTimeOptions = computed<WorkbenchFilterOption[]>(() => timePresetOptions)

const problemAnalysisToolbarPlatformOptions = computed<WorkbenchFilterOption[]>(() =>
  effectivePlatformScopeOptions.value.map(option => ({
    key: option.key,
    label: option.label,
    count: option.volume
  }))
)

const problemAnalysisToolbarTopicOptions = computed<WorkbenchFilterOption[]>(() =>
  effectivePromptTopicScopeOptions.value.map(option => ({
    key: option.key,
    label: option.label,
    count: option.volume
  }))
)

const effectivePlatformKeys = computed(() => effectivePlatformScopeOptions.value.map(item => item.key))

const effectiveSelectedPlatforms = computed(() => {
  if (!effectivePlatformKeys.value.length) {
    return []
  }
  const valid = selectedPlatforms.value.filter(platform => effectivePlatformKeys.value.includes(platform))
  return valid.length ? valid : effectivePlatformKeys.value
})

const showSyncSkeleton = computed(() => workbenchProblemAnalysisLoading.value && promptRuntime.lastSync === 0)
const recentChatEvidenceRows = computed(() => {
  return effectiveChatEvidenceRows.value
    .slice()
    .sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime())
    .slice(0, 8)
})

const probeScopedChatEvidenceRows = computed(() => {
  if (!activeChatProbeId.value) {
    return []
  }
  return effectiveChatEvidenceRows.value.filter(chat => (chat.refs?.probeId || chat.probeId) === activeChatProbeId.value)
})

const activeChatEvidenceRows = computed(() => {
  if (!activeChatId.value) {
    return []
  }

  if (activeChatScope.value === 'probe') {
    const scopedRows = probeScopedChatEvidenceRows.value
    if (scopedRows.some(chat => chat.id === activeChatId.value)) {
      return scopedRows
    }
  }

  const recentRows = recentChatEvidenceRows.value
  if (recentRows.some(chat => chat.id === activeChatId.value)) {
    return recentRows
  }

  return effectiveChatEvidenceRows.value.filter(chat => chat.id === activeChatId.value)
})

const problemAnalysisRecordRows = computed(() =>
  recentChatEvidenceRows.value.map(chat => ({
    id: chat.id,
    createdAtLabel: formatChatTime(chat.createdAt),
    createdDateLabel: formatChatTime(chat.createdAt).split(' ')[0] || '未记录日期',
    createdTimeLabel: formatChatTime(chat.createdAt).split(' ')[1] || '未记录时间',
    platformLabel: resolveRecentChatPlatformLabel(chat.model),
    query: chat.query || '未记录查询',
    citationCount: chat.citations.length,
    probeId: chat.probeId
  }))
)

const effectivePromptHeaderCount = computed(() => {
  if (effectivePromptTotalCount.value > 0) {
    return effectivePromptTotalCount.value
  }
  const rawAnswerCount = Number(problemAnalysisSummary.value?.rawAnswerCount || 0)
  if (rawAnswerCount > 0) {
    return rawAnswerCount
  }
  return recentChatEvidenceRows.value.length
})

const effectivePromptHeaderLabel = computed(() => {
  if (effectivePromptTotalCount.value > 0) {
    return '条问题'
  }
  return effectivePromptHeaderCount.value > 0 ? '条真实问答' : '条问题'
})

const effectivePromptCoverageEmptyMessage = computed(() => {
  if (effectivePromptTotalCount.value > 0) {
    return '当前品牌暂无可展示的真实问题清单'
  }
  if (effectivePromptHeaderCount.value > 0) {
    return '真实问答已接通，但问题归类仍在补齐。先从记录栏进入查看。'
  }
  return '当前品牌暂无可展示的真实问题清单'
})

function resolveRecentChatPlatformLabel(value?: string | null) {
  return typeof value === 'string' && value.trim() ? value.trim() : '未记录平台'
}

const problemAnalysisEvidencePending = computed(() =>
  isWorkbenchProblemAnalysisMode.value
  && problemAnalysisMissingFields.value.includes('evidenceRows')
)

const hasSpecificProblemAnalysisAvailabilityReason = computed(() => {
  const message = (problemAnalysisAvailability.value?.message || '').trim()
  return Boolean(message)
})

const problemAnalysisMissingFieldLabels = computed(() => {
  return Array.from(
    new Set(
      problemAnalysisMissingFields.value
        .filter(field => field !== 'problemSpaceBuckets' && field !== 'catalog.problemSpaceBuckets')
        .map(field => PROBLEM_ANALYSIS_MISSING_FIELD_LABELS[field] || field)
    )
  )
})

const showProblemAnalysisMissingFields = computed(() =>
  problemAnalysisMissingFieldLabels.value.length > 0
  && !hasSpecificProblemAnalysisAvailabilityReason.value
  && !problemAnalysisEvidencePending.value
)

const problemAnalysisAvailabilityNotice = computed(() => {
  if (!problemAnalysisIsPartial.value) {
    return ''
  }

  if (hasSpecificProblemAnalysisAvailabilityReason.value) {
    const message = (problemAnalysisAvailability.value?.message || '').trim()
    if (message.includes('上一轮报告') || message.includes('新一轮生成中')) {
      return ''
    }
    return message
  }

  if (problemAnalysisEvidencePending.value) {
    return '这轮已产出真实问题和回答，但还没有可展示的引用证据。'
  }

  return '真实问题分析已接通，部分数据仍在补齐。'
})

const showProblemAnalysisPartialNotice = computed(() =>
  Boolean(problemAnalysisAvailabilityNotice.value) && !hasWorkbenchProblemAnalysis.value
)

const recentChatEmptyText = computed(() => {
  if (problemAnalysisEvidencePending.value) {
    return '这轮已记录真实问题与回答，但还没有产出可挂载的引用证据。'
  }
  return '当前暂无可展示的真实问答记录'
})

let debounceTimer: ReturnType<typeof setTimeout> | null = null
let syncNonce = 0

function pickProblemAnalysisFilterCatalog(view: WorkbenchProblemAnalysisView): WorkbenchProblemAnalysisFilterCatalog {
  return {
    brandSpaceId: view.brandSpaceId,
    topicRows: view.topicRows || [],
    promptRows: view.promptRows || [],
    catalog: view.catalog || null
  }
}

async function syncPrompts() {
  const nonce = ++syncNonce
  const brandSpaceId = workbenchBoundBrandSpaceId.value
  const analysisRequest = problemAnalysisAnalysisRequest.value
  if (!analysisRequest) {
    return
  }
  const previousView = workbenchProblemAnalysis.value
  const previousCatalog = workbenchProblemAnalysisCatalog.value
  const previousCatalogRequestKey = workbenchProblemAnalysisCatalogRequestKey.value
  const loadedBrandSpaceId = previousView?.brandSpaceId || previousCatalog?.brandSpaceId || ''
  const switchingBrandSpace = Boolean(brandSpaceId && loadedBrandSpaceId && loadedBrandSpaceId !== brandSpaceId)
  const catalogRequestKey = brandSpaceId
    ? `${brandSpaceId}:${problemAnalysisAnalysisRequestKey.value}`
    : ''
  const requiresCatalogRefresh = Boolean(
    brandSpaceId
    && (
      !previousCatalog
      || previousCatalog.brandSpaceId !== brandSpaceId
      || workbenchProblemAnalysisCatalogRequestKey.value !== catalogRequestKey
    )
  )
  const scopeQuery = problemAnalysisBackendScopeQuery.value
  promptRuntime.loading = true
  promptRuntime.error = null

  try {
    if (!brandSpaceId) {
      workbenchProblemAnalysisCatalog.value = null
      workbenchProblemAnalysisCatalogRequestKey.value = ''
      workbenchProblemAnalysis.value = null
      promptRuntime.lastSync = Date.now()
      return
    }
    workbenchProblemAnalysisLoading.value = true
    workbenchProblemAnalysisError.value = ''
    if (switchingBrandSpace) {
      workbenchProblemAnalysisCatalog.value = null
      workbenchProblemAnalysis.value = null
    }
    if (requiresCatalogRefresh) {
      try {
        const catalogView = await workbenchApi.problemAnalysis(brandSpaceId, undefined, analysisRequest)
        if (nonce !== syncNonce) {
          return
        }
        workbenchProblemAnalysisCatalog.value = pickProblemAnalysisFilterCatalog(catalogView)
        workbenchProblemAnalysisCatalogRequestKey.value = catalogRequestKey
      } catch {
        if (nonce !== syncNonce) {
          return
        }
        workbenchProblemAnalysisCatalog.value = null
        workbenchProblemAnalysisCatalogRequestKey.value = ''
      }
    }
    const view = await workbenchApi.problemAnalysis(brandSpaceId, scopeQuery, analysisRequest)
    if (nonce !== syncNonce) {
      return
    }
    if (!view) {
      throw new Error('问题分析真实数据同步失败')
    }
    workbenchProblemAnalysis.value = view
    syncWorkbenchIdentity(view)
    promptRuntime.lastSync = Date.now()
  } catch (error) {
    if (nonce !== syncNonce) {
      return
    }
    const message = (error as Error)?.message || '问题分析真实数据同步失败'
    promptRuntime.error = message
    workbenchProblemAnalysisError.value = message
    workbenchProblemAnalysisCatalog.value = switchingBrandSpace ? null : previousCatalog
    workbenchProblemAnalysisCatalogRequestKey.value = switchingBrandSpace ? '' : previousCatalogRequestKey
    workbenchProblemAnalysis.value = switchingBrandSpace ? null : previousView
  } finally {
    if (nonce === syncNonce) {
      promptRuntime.loading = false
      workbenchProblemAnalysisLoading.value = false
    }
  }
}

function schedulePromptsSync() {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
  debounceTimer = setTimeout(() => {
    void syncPrompts()
  }, 120)
}

function handleProblemAnalysisTimePreset(value: string) {
  selectedTimePreset.value = value as typeof selectedTimePreset.value
}

function isEffectivePromptTopicExpanded(topic: string) {
  return effectiveExpandedPromptTopics.value.includes(topic)
}

function toggleEffectivePromptTopic(topic: string) {
  workbenchExpandedPromptTopics.value = effectiveExpandedPromptTopics.value.includes(topic)
    ? effectiveExpandedPromptTopics.value.filter(item => item !== topic)
    : [...effectiveExpandedPromptTopics.value, topic]
}

async function goNewBrand() {
  await navigateTo('/brands/new')
}

function onWorkbenchScroll(event: Event) {
  const target = event.target as HTMLElement | null
  workbenchScrolled.value = (target?.scrollTop || 0) > 6
}

function openProbeDetail(probeId: string) {
  activeChatId.value = null
  activeChatProbeId.value = null
  activeProbeId.value = probeId
}

function syncRequestedProbeDetail() {
  const requestedAttemptId = normalizedRequestedAttemptDetailId.value
  if (requestedAttemptId) {
    const matchedChat = effectiveChatEvidenceRows.value.find(chat => chat.id === requestedAttemptId)
    if (matchedChat) {
      return
    }
  }

  const probeId = requestedProbeDetailId.value
  if (!probeId) {
    probeDeepLinkError.value = ''
    return
  }
  if (workbenchProblemAnalysisLoading.value || promptRuntime.loading) {
    return
  }

  const matchedProbe = effectiveProbeRows.value.find(row => row.id === probeId || row.refs?.probeId === probeId)
  if (matchedProbe) {
    probeDeepLinkError.value = ''
    openProbeDetail(matchedProbe.id)
    return
  }

  if (hasWorkbenchProblemAnalysis.value || promptRuntime.lastSync > 0) {
    activeProbeId.value = null
    probeDeepLinkError.value = '未找到这条探针，可能已被当前筛选窗口、品牌空间或运行记录排除。'
  }
}

function syncRequestedAttemptDetail() {
  const attemptId = normalizedRequestedAttemptDetailId.value
  if (!attemptId) {
    return
  }
  if (workbenchProblemAnalysisLoading.value || promptRuntime.loading) {
    return
  }

  const matchedChat = effectiveChatEvidenceRows.value.find(chat => chat.id === attemptId)
  if (matchedChat) {
    probeDeepLinkError.value = ''
    openRecordChatEvidence(matchedChat.id)
  }
}

function problemAnalysisMetricLabel(metricKey?: string | null, fallback?: string | null) {
  if (fallback) {
    return fallback
  }
  const map: Record<string, string> = {
    demandTotal: '需求总量',
    activeTopicCount: '活跃主题',
    demandVolume: '需求量级',
    demandShare: '需求占比',
    promptCount: '问题数',
    totalProjection: '总需求量',
    coverage: '被引覆盖率',
    gapCount: '缺口词',
    highOpportunityCount: '高价值机会',
    citationCount: '引用证据数'
  }
  return map[String(metricKey || '').trim()] || '当前结果'
}

function openProblemAnalysisProvenance(
  anchorKey: string,
  metricKey?: string | null,
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
  const brandSpaceId = workbenchBoundBrandSpaceId.value
  if (!brandSpaceId) {
    return
  }
  const context = problemAnalysisAnalysisRequest.value
  const query: WorkbenchDataProvenanceCardQuery = {
    brandSpaceId,
    ...(context || { timePreset: selectedTimePreset.value }),
    runId: probeId ? workbenchProblemAnalysis.value?.latestRunId || null : null,
    topicId: topicId || null,
    topicName: topicId ? anchorKey : null,
    probeId: probeId || null,
    anchorType: 'problem_analysis_point',
    anchorKey,
    metricKey: metricKey || null,
    metricLabel: problemAnalysisMetricLabel(metricKey, metricLabel),
    metricValue: metricValue || null,
    platform: platform || null,
    windowKey: windowKey || null,
    contextCitationCount: contextCitationCount ?? null,
    contextDemandVolume: contextDemandVolume ?? null,
    contextDemandTotal: contextDemandTotal ?? null,
    contextActiveTopicCount: contextActiveTopicCount ?? null,
    contextActivePlatformCount: contextActivePlatformCount ?? null,
    contextPromptCount: contextPromptCount ?? null,
    contextTotalProjection: contextTotalProjection ?? null,
    contextHighValuePromptCount: contextHighValuePromptCount ?? null,
    contextPromptLoopTotalCount: contextPromptLoopTotalCount ?? null,
    contextPromptLoopCoveredCount: contextPromptLoopCoveredCount ?? null,
    contextPromptLoopGapCount: contextPromptLoopGapCount ?? null,
    contextPromptLoopHighOpportunityCount: contextPromptLoopHighOpportunityCount ?? null,
    resultPointKey: `problem_analysis_point:${anchorKey}:${metricKey || 'metric'}`
  }
  void openProvenanceCard(query)
}

function openRecordChatEvidence(chatId: string) {
  activeChatScope.value = 'record'
  activeChatProbeId.value = null
  activeProbeId.value = null
  activeChatId.value = chatId
}

function openProbeChatEvidence(chatId: string) {
  activeChatScope.value = 'probe'
  activeChatProbeId.value = activeProbeId.value
  activeProbeId.value = null
  activeChatId.value = chatId
}

function reopenProbeFromChat(probeId: string) {
  activeChatId.value = null
  activeChatProbeId.value = probeId
  activeProbeId.value = probeId
}

function formatChatTime(value?: string | null) {
  if (!value) {
    return '未记录时间'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return `${String(date.getMonth() + 1).padStart(2, '0')}/${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

watch(
  workbenchBoundBrandSpaceId,
  () => {
    schedulePromptsSync()
  },
  { immediate: true }
)

watch(
  problemAnalysisAnalysisRequestKey,
  () => {
    if (!workbenchBoundBrandSpaceId.value) {
      return
    }
    schedulePromptsSync()
  }
)

watch(
  selectedPromptTopics,
  () => {
    if (!workbenchBoundBrandSpaceId.value) {
      return
    }
    schedulePromptsSync()
  },
  { deep: true }
)

watch(
  realPromptTopicKeys,
  (topics) => {
    if (!isWorkbenchProblemAnalysisMode.value) {
      return
    }
    if (!topics.length) {
      if (selectedPromptTopics.value.length) {
        selectedPromptTopics.value = []
      }
      return
    }
    const validTopics = new Set(topics)
    const next = selectedPromptTopics.value.filter(topic => validTopics.has(topic))
    if (next.length !== selectedPromptTopics.value.length) {
      selectedPromptTopics.value = next
    }
  },
  { immediate: true }
)

watch(
  problemAnalysisPlatformOptionsForRequest,
  (options) => {
    syncPlatformSelection(options)
  },
  { deep: true, immediate: true }
)

watch(
  [requestedProbeDetailId, effectiveProbeRows, workbenchProblemAnalysisLoading],
  () => {
    syncRequestedProbeDetail()
  },
  { immediate: true }
)

watch(
  [requestedAttemptDetailId, effectiveChatEvidenceRows, workbenchProblemAnalysisLoading],
  () => {
    syncRequestedAttemptDetail()
  },
  { immediate: true }
)

watch(
  effectivePromptTopicGroups,
  (groups) => {
    if (!isWorkbenchProblemAnalysisMode.value) {
      return
    }
    if (!groups.length) {
      workbenchExpandedPromptTopics.value = []
      return
    }
    const validTopics = new Set(groups.map(group => group.topic))
    workbenchExpandedPromptTopics.value = workbenchExpandedPromptTopics.value.filter(topic => validTopics.has(topic))
  },
  { deep: true, immediate: true }
)

watch(workbenchBoundBrandSpaceId, () => {
  activeProbeId.value = null
  activeChatId.value = null
})

onMounted(async () => {
  workbenchScrolled.value = false
  await brandSpaceDirectory.ensureLoaded().catch(() => undefined)
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
              <WikiGeoWikiTrigger entrypoint-id="probe.page.title">
                {{ resolveNavPageTitleByPath('/probe-analysis', '问题分析') }}
              </WikiGeoWikiTrigger>
            </h1>
            <label
              class="brand-select-wrap"
              for="probe-analysis-brand-select"
            >
              <select
                id="probe-analysis-brand-select"
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

        <section
          v-if="!showBrandSpaceEmptyState && !showBrandSelectionPromptState && !brandSpaceSelectionError"
          class="surface filter-surface filter-surface--prompts"
        >
          <WorkbenchAnalysisToolbar
            :time-preset="selectedTimePreset"
            :date-from="customDateFrom"
            :date-to="customDateTo"
            :time-options="problemAnalysisToolbarTimeOptions"
            :platform-options="problemAnalysisToolbarPlatformOptions"
            :selected-platforms="selectedPlatforms"
            :topic-options="problemAnalysisToolbarTopicOptions"
            :selected-topics="selectedPromptTopics"
            :topic-disabled="workbenchProblemAnalysisCatalogPending || workbenchPromptTopicContractMissing"
            :topic-disabled-label="effectivePromptTopicScopeLabel"
            :combine-scope-row="true"
            @update:time-preset="handleProblemAnalysisTimePreset"
            @update:date-from="customDateFrom = $event"
            @update:date-to="customDateTo = $event"
            @update:selected-platforms="selectedPlatforms = $event"
            @update:selected-topics="selectedPromptTopics = $event"
          />
        </section>
      </div>

      <div
        v-if="!showBrandSpaceEmptyState && !showBrandSelectionPromptState && !brandSpaceSelectionError && showSyncSkeleton"
        class="sync-placeholder"
        aria-hidden="true"
      />
      <div
        v-if="!showBrandSpaceEmptyState && !showBrandSelectionPromptState && brandSpaceSelectionError"
        class="sync-notice"
      >
        {{ brandSpaceSelectionError }}，请先回品牌管理确认品牌空间状态。
      </div>
      <div
        v-else-if="!showBrandSpaceEmptyState && !showBrandSelectionPromptState && !brandSpaceSelectionError && promptRuntime.error"
        class="sync-notice sync-notice--warn"
      >
        {{ promptRuntime.error }}<template v-if="hasWorkbenchProblemAnalysis">
          ，已保留当前页面缓存数据。
        </template>
      </div>
      <div
        v-else-if="!showBrandSpaceEmptyState && !showBrandSelectionPromptState && !brandSpaceSelectionError && showProblemAnalysisPartialNotice"
        class="sync-notice sync-notice--partial"
      >
        <div>
          {{ problemAnalysisAvailabilityNotice }}
          <span
            v-if="showProblemAnalysisMissingFields"
            class="sync-notice__muted"
          >
            缺口：{{ problemAnalysisMissingFieldLabels.join('、') }}
          </span>
          <span
            v-if="workbenchPromptTopicContractMissing"
            class="sync-notice__muted"
          >
            问题已返回，但主题归类仍在补齐
          </span>
        </div>
        <div
          v-if="problemAnalysisSummaryStats.length"
          class="sync-notice__stats"
        >
          <span
            v-for="item in problemAnalysisSummaryStats"
            :key="`problem-analysis-linked-${item.key}`"
          >
            {{ item.label }} {{ item.total }}<template v-if="item.mapped !== null && item.mapped !== item.total"> / 已归类 {{ item.mapped }}</template>
          </span>
        </div>
      </div>
      <div
        v-else-if="showBrandSelectionPromptState"
        class="empty-state"
      >
        <strong>先选择品牌空间</strong>
        <span>选择一个品牌空间后，这里才会展示对应品牌的真实问题分析结果。</span>
      </div>
      <div
        v-if="showBrandSpaceEmptyState"
        class="empty-state"
      >
        <strong>还没有品牌空间</strong>
        <span>先新增品牌，再进入问题分析查看主题、机会和真实记录。</span>
        <button
          type="button"
          class="cta-primary"
          @click="goNewBrand"
        >
          新增品牌
        </button>
      </div>

      <WorkbenchProblemAnalysisRichTabs
        v-if="!showBrandSpaceEmptyState && !showBrandSelectionPromptState && !brandSpaceSelectionError && isWorkbenchProblemAnalysisMode && hasWorkbenchProblemAnalysis"
        :has-prompt-coverage="effectiveHasPromptCoverage"
        :selected-brand="effectiveSelectedBrand"
        :prompt-total-count="effectivePromptHeaderCount"
        :prompt-total-label="effectivePromptHeaderLabel"
        :empty-coverage-message="effectivePromptCoverageEmptyMessage"
        :prompt-topic-groups="effectivePromptTopicGroups"
        :prompt-table-grid-style="workbenchPromptTableGridStyle"
        :prompt-visible-columns="promptVisibleColumns"
        :toggle-prompt-topic="toggleEffectivePromptTopic"
        :is-prompt-topic-expanded="isEffectivePromptTopicExpanded"
        :resolve-prompt-cell-value="resolvePromptCellValue"
        :resolve-prompt-cell-delta="resolveWorkbenchPromptCellDeltaView"
        :resolve-prompt-delta-class="resolveWorkbenchPromptDeltaClassView"
        :probes="effectiveProbeRows"
        :selected-platforms="effectiveSelectedPlatforms"
        :selected-topics="effectiveVisiblePromptTopics"
        :platform-display-map="problemAnalysisPlatformDisplayMap"
        :window-start-ms="problemAnalysisWindowStartMs"
        :granularity="problemAnalysisVolumeGranularity"
        :rich="workbenchProblemRich"
        :record-rows="problemAnalysisRecordRows"
        :record-empty-text="recentChatEmptyText"
        :record-evidence-pending="problemAnalysisEvidencePending"
        @open-probe-detail="openProbeDetail"
        @open-chat-evidence="openRecordChatEvidence"
        @open-provenance="openProblemAnalysisProvenance"
      />
      <div
        v-if="probeDeepLinkError"
        class="state-block state-block--compact"
      >
        {{ probeDeepLinkError }}
      </div>

      <div
        v-else-if="!showBrandSpaceEmptyState && !showBrandSelectionPromptState && !brandSpaceSelectionError && isWorkbenchProblemAnalysisMode && !workbenchProblemAnalysisLoading"
        class="state-block"
      >
        {{ promptRuntime.error || '当前品牌空间暂无可展示的问题分析结果。' }}
      </div>

    </section>

    <ProbeDetailOverlay
      v-model="activeProbeId"
      :probes="effectiveProbeRows"
      :chats="effectiveChatEvidenceRows"
      :brand-ranks="effectiveBrandRanks"
      @open-chat="openProbeChatEvidence"
    />
    <ChatEvidenceOverlay
      v-model="activeChatId"
      :chats="activeChatEvidenceRows"
      @open-probe="reopenProbeFromChat"
    />
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
.filter-surface--prompts {
  padding: 12px;
}

.prompt-toolbar--analysis {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
}

.analysis-toolbar-group {
  min-width: 0;
  display: grid;
  gap: 10px;
}

.analysis-toolbar-group__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.analysis-toolbar-group__head strong {
  font-size: 12px;
  line-height: 1;
  color: #111827;
}

.analysis-toolbar-group__head span {
  font-size: 11px;
  line-height: 1.4;
  color: #6b7280;
}

.probe-chat-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
}

.probe-chat-table__head,
.probe-chat-table__row {
  min-height: 42px;
  display: grid;
  align-items: center;
  grid-template-columns: 112px 104px minmax(0, 1.8fr) 72px 132px;
  gap: 8px;
  padding: 0 10px;
}

.probe-chat-table__head {
  background: #f8fafc;
  border-bottom: 1px solid #d1d5db;
  color: #6b7280;
  font-size: 12px;
  font-weight: 600;
}

.probe-chat-table__row {
  border-bottom: 1px solid #e5e7eb;
  font-size: 12px;
  color: #111827;
}

.probe-chat-table__row:last-child {
  border-bottom: none;
}

.probe-chat-table__query {
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.probe-chat-table__actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

@media (max-width: 1220px) {
  .probe-chat-table__head,
  .probe-chat-table__row {
    grid-template-columns: 96px 92px minmax(0, 1fr) 64px 118px;
  }
}
</style>
