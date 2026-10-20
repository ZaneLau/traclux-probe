<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { GeoApiError } from '~/composables/useGeoApi'
import { buildPublicPreviewModelOptions, resolveProductModelLabel } from '~/utils/productModels'
import {
  resolveBrandSpaceTrackingState,
  resolveBrandSpaceUpdatedAt
} from '~/utils/brandSpaceState'
import type {
  BrandSpaceBasicProfileUpdateRequest,
  BrandSpaceBasicProfileView,
  BrandSpaceProbePlanExecuteOnceResponse,
  BrandSpaceProbePlanView,
  BrandSpaceConfigPrompt,
  BrandSpaceRunConfig,
  BrandSpaceConfigTopic,
  BrandSpaceConfigUpdateRequest,
  BrandSpaceConfigView,
  BrandSpaceListItem,
  ServiceAccess,
  BrandSpaceTrackingMode
} from '~/types/brand-space'

definePageMeta({
  layout: false,
  middleware: 'auth',
  requiresAuth: true
})

interface EditableTopicDraft {
  tempId: string
  topicId?: string
  name: string
  description: string
  enabled: boolean
  source?: string | null
}

interface EditablePromptDraft {
  tempId: string
  promptId?: string
  topicTempId?: string | null
  text: string
  enabled: boolean
  source?: string | null
}

interface EditableBasicProfileDraft {
  brandName: string
  category: string
  officialWebsite: string
  competitorsText: string
  supplementalBrief: string
}

interface EditableRunSettingsDraft {
  targetProbeCount: number
  targetModelProduct: string
  autoRunEnabled: boolean
}

const demoMode = useRuntimeConfig().public.demoMode === true
const route = useRoute()
const brandSpacesApi = useBrandSpacesApi()
const serviceAccessApi = useServiceAccessApi()
const creditsApi = useCreditsApi()
const brandSpaceDirectory = useWorkbenchBrandSpacesDirectory()
const { selectedBrand, selectedBrandSpaceId } = useWorkbenchSelectionState()
const {
  workbenchRef,
  workbenchScrolled,
  resetWorkbenchScrolled,
  syncWorkbenchScrolled
} = useWorkbenchShellState()

const configRuntime = ref<BrandSpaceConfigView | null>(null)
const basicProfileRuntime = ref<BrandSpaceBasicProfileView | null>(null)
const configLoading = ref(false)
const configError = ref('')
const basicProfileLoadError = ref('')
const configLastSync = ref(0)
const archiveConfirmOpen = ref(false)
const archiveConfirmText = ref('')
const archivePending = ref(false)
const archiveError = ref('')
const configEditMode = ref(false)
const configSavePending = ref(false)
const configSaveError = ref('')
const configSaveSuccess = ref('')
const trackingEditMode = ref(false)
const trackingModeDraft = ref<BrandSpaceTrackingMode>('ACTIVE')
const trackingPending = ref(false)
const trackingError = ref('')
const trackingSuccess = ref('')
const topicDrafts = ref<EditableTopicDraft[]>([])
const promptDrafts = ref<EditablePromptDraft[]>([])
const basicProfileDraft = ref<EditableBasicProfileDraft>({
  brandName: '',
  category: '',
  officialWebsite: '',
  competitorsText: '',
  supplementalBrief: ''
})
const serviceAccess = ref<ServiceAccess | null>(null)
const creditBalance = ref<number | null>(null)
const probePlanRuntime = ref<BrandSpaceProbePlanView | null>(null)
const probePlanLoading = ref(false)
const probePlanError = ref('')
const probePlanActionPending = ref(false)
const probePlanActionError = ref('')
const probePlanActionSuccess = ref('')
const lastExecuteOnceResponse = ref<BrandSpaceProbePlanExecuteOnceResponse | null>(null)
const runSettingsEditMode = ref(false)
const runSettingsSavePending = ref(false)
const runSettingsSaveError = ref('')
const runSettingsSaveSuccess = ref('')
const baselineRunSettingsPayload = ref('')
const runSettingsDraft = ref<EditableRunSettingsDraft>({
  targetProbeCount: 12,
  targetModelProduct: 'doubao',
  autoRunEnabled: true
})
const basicProfileEditMode = ref(false)
const basicProfileSavePending = ref(false)
const basicProfileSaveError = ref('')
const basicProfileSaveSuccess = ref('')
const baselineConfigPayload = ref('')
const draftSeed = ref(0)
const DEFAULT_TARGET_MODEL_PRODUCT = 'doubao'
const DEFAULT_TARGET_PROBE_COUNT = 12
const MAX_TARGET_PROBE_COUNT = 100
const DEFAULT_CREDITS_PER_PROBE = 1

const TRACKING_MODE_OPTIONS: Array<{ key: BrandSpaceTrackingMode, label: string, detail: string }> = [
  {
    key: 'ACTIVE',
    label: '跟踪中',
    detail: '继续保持长期跟踪，后续运行会使用当前配置。'
  },
  {
    key: 'PAUSED',
    label: '已暂停',
    detail: '保留当前配置，但暂停继续跟踪。'
  }
]

const brandSpaceId = computed(() => {
  const value = route.params.brandSpaceId
  return typeof value === 'string' ? value : ''
})

const currentBrandSpace = computed<BrandSpaceListItem | null>(() => {
  return brandSpaceDirectory.resolveByBrandSpaceId(brandSpaceId.value)
})

useSeoMeta({
  title: () => `${basicProfileRuntime.value?.brandName || currentBrandSpace.value?.brandName || configRuntime.value?.brandName || '品牌空间'} · 空间配置 · Traclux 探镜`
})

const configTopics = computed<BrandSpaceConfigTopic[]>(() => configRuntime.value?.topics || [])
const configPrompts = computed<BrandSpaceConfigPrompt[]>(() => configRuntime.value?.prompts || [])
const configSummary = computed(() => configRuntime.value?.summary || null)
const trackingState = computed(() => resolveBrandSpaceTrackingState(currentBrandSpace.value))
const trackingContractReady = computed(() => trackingState.value.contractReady)
const activeRunConfig = computed<BrandSpaceRunConfig | null>(() => configRuntime.value?.runConfig || null)
const effectiveServiceLimits = computed(() => serviceAccess.value?.limits || null)
const creditsPerProbe = computed(() => {
  const configured = effectiveServiceLimits.value?.creditsPerProbe
  return typeof configured === 'number' && configured > 0
    ? configured
    : DEFAULT_CREDITS_PER_PROBE
})
const enabledModelCount = computed(() => effectiveServiceLimits.value?.enabledModelCount ?? null)
const effectiveRunSettings = computed<EditableRunSettingsDraft>(() => {
  const runtime = activeRunConfig.value
  const fallbackProbeCount = configSummary.value?.enabledPromptCount ?? DEFAULT_TARGET_PROBE_COUNT
  return runSettingsEditMode.value
    ? runSettingsDraft.value
    : {
        targetProbeCount: normalizeTargetProbeCount(runtime?.targetProbeCount ?? fallbackProbeCount),
        targetModelProduct: normalizeTargetModelProduct(runtime?.targetModelProduct),
        autoRunEnabled: runtime?.autoRunEnabled !== false
      }
})
const monitoringModelOptions = computed(() =>
  buildPublicPreviewModelOptions(enabledModelCount.value, [effectiveRunSettings.value.targetModelProduct])
)
const currentModelLabel = computed(() =>
  monitoringModelOptions.value.find(option => option.selected)?.label
  || resolveProductModelLabel(effectiveRunSettings.value.targetModelProduct)
)
const currentRunModeLabel = computed(() =>
  effectiveRunSettings.value.autoRunEnabled ? '每日自动运行' : '仅手动运行'
)
const estimatedCreditsPerRun = computed(() =>
  effectiveRunSettings.value.targetProbeCount * creditsPerProbe.value
)
const balanceAfterNextRun = computed(() =>
  creditBalance.value == null ? null : Math.max(0, creditBalance.value - estimatedCreditsPerRun.value)
)
const creditBalanceLabel = computed(() =>
  creditBalance.value == null ? '待同步' : `${Math.max(0, creditBalance.value)} 积分`
)
const balanceAfterNextRunLabel = computed(() =>
  balanceAfterNextRun.value == null ? '待同步' : `${balanceAfterNextRun.value} 积分`
)
const runSettingsStatusLabel = computed(() => {
  if (!configRuntime.value) {
    return '未读取'
  }
  return runSettingsEditMode.value ? '编辑中' : '已保存'
})
const runSettingsValidationError = computed(() => {
  if (!effectiveRunSettings.value.targetModelProduct.trim()) {
    return '请选择一个运行模型。'
  }
  if (effectiveRunSettings.value.targetProbeCount < 1 || effectiveRunSettings.value.targetProbeCount > MAX_TARGET_PROBE_COUNT) {
    return `探针数必须在 1 到 ${MAX_TARGET_PROBE_COUNT} 之间。`
  }
  return ''
})
const currentProbePlan = computed(() => probePlanRuntime.value?.currentPlan || null)
const latestProbePlanRecord = computed(() => probePlanRuntime.value?.recentRecords?.[0] || null)
const probePlanSnapshotCount = computed(() => currentProbePlan.value?.items?.length ?? 0)
const probePlanStatusLabel = computed(() => {
  if (probePlanLoading.value && !probePlanRuntime.value) {
    return '同步中'
  }
  return currentProbePlan.value?.planVersionId ? '已确认' : '待确认'
})
const probePlanStatusDetail = computed(() => {
  if (currentProbePlan.value?.planVersionId) {
    return `已对齐当前配置，版本 ${currentProbePlan.value.versionNo || '未编号'}。`
  }
  return '当前配置还没有确认成可执行快照。'
})
const latestProbePlanRecordLabel = computed(() => {
  const code = latestProbePlanRecord.value?.changeTypeCode
  switch (code) {
    case 'INITIAL_CONFIRM':
      return '首次确认'
    case 'CHANGE_CONFIRM':
      return '配置确认'
    case 'MANUAL_RUN':
      return '手动运行'
    case 'SCHEDULED_RUN':
      return '自动运行'
    case 'CONTROLLED_RERUN':
      return '受控重跑'
    default:
      return code || '暂无记录'
  }
})
const latestExecuteResultLabel = computed(() => {
  const response = lastExecuteOnceResponse.value
  if (!response) {
    return '尚未执行'
  }
  return response.skipped ? '本次未创建运行' : '已创建运行'
})
const latestExecuteResultDetail = computed(() => {
  const response = lastExecuteOnceResponse.value
  if (!response) {
    return '还没有手动运行记录。'
  }
  if (response.skipped) {
    return formatExecuteOnceSkippedReason(response.skippedReason)
  }
  return `实扣 ${response.deductedCredits ?? 0} 积分，剩余 ${response.creditsRemainingAfterRun ?? '待同步'} 积分。`
})
const probePlanActionBlockedReason = computed(() => {
  if (configLoading.value || probePlanLoading.value) {
    return '正在同步当前配置，请稍后再试。'
  }
  if (configEditMode.value && hasConfigChanges.value) {
    return '请先保存 topic 与自定义 prompt。'
  }
  if (runSettingsEditMode.value && hasRunSettingsChanges.value) {
    return '请先保存运行设置。'
  }
  if (configEditMode.value && configValidationError.value) {
    return configValidationError.value
  }
  if (runSettingsEditMode.value && runSettingsValidationError.value) {
    return runSettingsValidationError.value
  }
  return ''
})
const executeOnceDisabledReason = computed(() => {
  if (probePlanActionBlockedReason.value) {
    return probePlanActionBlockedReason.value
  }
  if (!currentProbePlan.value?.planVersionId) {
    return '请先确认探针方案。'
  }
  if (creditBalance.value != null && creditBalance.value < estimatedCreditsPerRun.value) {
    return `当前可用积分不足，本次至少需要 ${estimatedCreditsPerRun.value} 积分。`
  }
  return ''
})
const canEditBasicProfile = computed(() => Boolean(basicProfileRuntime.value))
const basicBrandName = computed(() =>
  basicProfileRuntime.value?.brandName
  || currentBrandSpace.value?.brandName
  || configRuntime.value?.brandName
  || '未读取'
)
const basicCategory = computed(() =>
  basicProfileRuntime.value?.category
  || currentBrandSpace.value?.category
  || '未分类'
)
const officialWebsite = computed(() => {
  if (basicProfileRuntime.value) {
    return basicProfileRuntime.value.officialWebsite || '未填写'
  }
  return currentBrandSpace.value?.officialWebsite || '未填写'
})
const competitorNames = computed(() => basicProfileRuntime.value?.competitors || [])
const competitorDisplayText = computed(() => {
  if (!basicProfileRuntime.value && basicProfileLoadError.value) {
    return '未读取'
  }
  return competitorNames.value.length ? competitorNames.value.join('、') : '未配置'
})
const competitorHelperText = computed(() => {
  if (!basicProfileRuntime.value) {
    return ''
  }
  if (competitorNames.value.length) {
    return `已录入 ${competitorNames.value.length} 个竞品，用于后续对比。`
  }
  return '当前还没有录入竞品。'
})

const isMissingBrand = computed(() => {
  return brandSpaceDirectory.loaded.value && !currentBrandSpace.value && !configLoading.value
})

const showSkeleton = computed(() => configLoading.value && configLastSync.value === 0)
const archiveBrandName = computed(() => currentBrandSpace.value?.brandName || configRuntime.value?.brandName || '')
const archiveCanConfirm = computed(() => {
  const expected = archiveBrandName.value.trim()
  return Boolean(expected) && archiveConfirmText.value.trim() === expected
})

const configWriteStatus = computed(() => {
  if (!configRuntime.value) {
    return '未读取'
  }
  return configEditMode.value ? '编辑中' : '已保存'
})

function normalizeTrackingMode(value?: string | null): BrandSpaceTrackingMode {
  return String(value || '').trim().toUpperCase() === 'PAUSED' ? 'PAUSED' : 'ACTIVE'
}

function normalizeTargetProbeCount(value?: number | null) {
  const raw = typeof value === 'number' && Number.isFinite(value)
    ? Math.trunc(value)
    : DEFAULT_TARGET_PROBE_COUNT
  return Math.min(Math.max(raw, 1), MAX_TARGET_PROBE_COUNT)
}

function normalizeTargetModelProduct(value?: string | null) {
  const normalized = String(value || '').trim().toLowerCase()
  return normalized || DEFAULT_TARGET_MODEL_PRODUCT
}

function isCustomSource(source?: string | null) {
  const normalized = String(source || '').trim().toLowerCase()
  return normalized === 'manual' || normalized === 'custom' || normalized === 'user_defined' || normalized === 'user_edited'
}

function promoteSource(source?: string | null) {
  return isCustomSource(source) ? source || 'manual' : 'manual'
}

const topicStatusSummary = computed(() => {
  const rows = configEditMode.value ? topicDrafts.value : configTopics.value
  return {
    total: rows.length,
    active: rows.filter(row => row.enabled !== false).length,
    inactive: rows.filter(row => row.enabled === false).length
  }
})

const customPromptDraftRows = computed(() =>
  promptDrafts.value
    .map((prompt, actualIndex) => ({
      prompt,
      actualIndex
    }))
    .filter(row => isCustomSource(row.prompt.source))
)

const customConfigPrompts = computed(() => configPrompts.value.filter(prompt => isCustomSource(prompt.source)))

const hasCustomPromptEntries = computed(() => {
  return configEditMode.value ? customPromptDraftRows.value.length > 0 : customConfigPrompts.value.length > 0
})

const customPromptStatusLabel = computed(() => hasCustomPromptEntries.value ? '已配置' : '未配置')
const autoPromptMessage = '除你手动添加的问题外，系统还会围绕已启用的 topic 自动补充更多问题，用于补齐覆盖面。这里仅展示你手动维护的自定义 prompt。'

const configSummaryTopicCount = computed(() =>
  configEditMode.value ? topicDrafts.value.length : (configSummary.value?.topicCount ?? configTopics.value.length)
)

const editableTopicOptions = computed(() => [
  {
    key: '',
    label: '未分组主题'
  },
  ...topicDrafts.value.map((topic, index) => ({
    key: topic.tempId,
    label: topic.name.trim() || `主题 ${index + 1}`
  }))
])

const basicProfileValidationError = computed(() => {
  if (!basicProfileDraft.value.brandName.trim()) {
    return '品牌名不能为空。'
  }
  if (!basicProfileDraft.value.category.trim()) {
    return '品类不能为空。'
  }
  if (resolveCompetitorListFromText(basicProfileDraft.value.competitorsText).length > 10) {
    return '竞品最多保留 10 个。'
  }
  if (basicProfileDraft.value.supplementalBrief.trim().length > 1000) {
    return '品牌介绍最多保留 1000 个字。'
  }
  return ''
})

const hasBasicProfileChanges = computed(() => {
  if (!basicProfileRuntime.value) {
    return false
  }
  return serializeBasicProfilePayload(buildBasicProfilePayload()) !== serializeBasicProfilePayload(
    buildBasicProfilePayload({
      brandName: basicProfileRuntime.value.brandName || '',
      category: basicProfileRuntime.value.category || '',
      officialWebsite: basicProfileRuntime.value.officialWebsite || '',
      competitorsText: formatCompetitorText(basicProfileRuntime.value.competitors || []),
      supplementalBrief: basicProfileRuntime.value.supplementalBrief || ''
    })
  )
})

function normalizeWebsiteInput(raw: string) {
  const trimmed = raw.trim()
  if (!trimmed) {
    return ''
  }
  if (/^https?:\/\//i.test(trimmed)) {
    return trimmed
  }
  return `http://${trimmed}`
}

function sanitizeWebsiteForSubmit(raw: string) {
  const normalized = normalizeWebsiteInput(raw)
  if (normalized === 'http://' || normalized === 'https://') {
    return null
  }
  return normalized || null
}

function resolveCompetitorListFromText(raw: string) {
  return Array.from(new Set(
    raw
      .split(/[\n，,]/)
      .map(item => item.trim())
      .filter(Boolean)
  ))
}

function formatCompetitorText(competitors: string[]) {
  return competitors.join(', ')
}

function buildBasicProfilePayload(
  draft: EditableBasicProfileDraft = basicProfileDraft.value
): BrandSpaceBasicProfileUpdateRequest {
  return {
    brandName: draft.brandName.trim(),
    category: draft.category.trim(),
    officialWebsite: sanitizeWebsiteForSubmit(draft.officialWebsite),
    competitors: resolveCompetitorListFromText(draft.competitorsText),
    supplementalBrief: draft.supplementalBrief.trim() || null
  }
}

function serializeBasicProfilePayload(payload: BrandSpaceBasicProfileUpdateRequest) {
  return JSON.stringify({
    brandName: payload.brandName,
    category: payload.category,
    officialWebsite: payload.officialWebsite || null,
    competitors: payload.competitors || [],
    supplementalBrief: payload.supplementalBrief || null
  })
}

function hydrateBasicProfileDraft(view: BrandSpaceBasicProfileView | null) {
  basicProfileDraft.value = {
    brandName: view?.brandName || '',
    category: view?.category || '',
    officialWebsite: view?.officialWebsite || '',
    competitorsText: formatCompetitorText(view?.competitors || []),
    supplementalBrief: view?.supplementalBrief || ''
  }
  basicProfileEditMode.value = false
  basicProfileSaveError.value = ''
  basicProfileSaveSuccess.value = ''
}

function formatDate(value?: string | null, emptyLabel = '未生成') {
  if (!value) {
    return emptyLabel
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

function formatExecuteOnceSkippedReason(reason?: string | null) {
  switch (String(reason || '').trim()) {
    case 'credits_insufficient':
      return '积分不足，当前没有创建新的运行。'
    case 'reward_quota_snapshot_unavailable':
      return '供应商快照暂不可用，当前没有创建新的运行。'
    case 'execution_plan_missing':
      return '当前还没有可执行的探针方案。'
    default:
      return reason || '当前没有创建新的运行。'
  }
}

function dashboardPath() {
  return `/dashboard?brandSpaceId=${encodeURIComponent(brandSpaceId.value)}`
}

function updatedAt() {
  return resolveBrandSpaceUpdatedAt(currentBrandSpace.value)
}

function nextDraftId(prefix: 'topic' | 'prompt') {
  draftSeed.value += 1
  return `${prefix}-draft-${draftSeed.value}`
}

function buildConfigPayload(
  topics = topicDrafts.value,
  prompts = promptDrafts.value,
  runSettings?: EditableRunSettingsDraft | null
): BrandSpaceConfigUpdateRequest {
  const validTopicTempIds = new Set(topics.map(topic => topic.tempId))
  return {
    topics: topics.map(topic => ({
      tempId: topic.tempId,
      name: topic.name.trim(),
      description: topic.description.trim() || null,
      enabled: topic.enabled,
      source: topic.source || 'manual'
    })),
    prompts: prompts.map(prompt => ({
      tempId: prompt.tempId,
      topicTempId: prompt.topicTempId && validTopicTempIds.has(prompt.topicTempId) ? prompt.topicTempId : null,
      text: prompt.text.trim(),
      enabled: prompt.enabled,
      source: prompt.source || 'manual'
    })),
    ...(runSettings ? buildRunSettingsPayload(runSettings) : {})
  }
}

function serializeConfigPayload(payload: BrandSpaceConfigUpdateRequest) {
  return JSON.stringify(payload)
}

function syncTrackingDraft() {
  trackingModeDraft.value = normalizeTrackingMode(currentBrandSpace.value?.trackingStatus)
}

function buildRunSettingsPayload(draft: EditableRunSettingsDraft = runSettingsDraft.value) {
  return {
    targetProbeCount: normalizeTargetProbeCount(draft.targetProbeCount),
    targetModelProduct: normalizeTargetModelProduct(draft.targetModelProduct),
    autoRunEnabled: Boolean(draft.autoRunEnabled)
  }
}

function serializeRunSettingsPayload(payload = buildRunSettingsPayload()) {
  return JSON.stringify(payload)
}

const hasRunSettingsChanges = computed(() => {
  if (!runSettingsEditMode.value || !baselineRunSettingsPayload.value) {
    return false
  }
  return serializeRunSettingsPayload() !== baselineRunSettingsPayload.value
})

function hydrateRunSettingsDraft(view: BrandSpaceConfigView | null) {
  const runtime = view?.runConfig
  const fallbackProbeCount = view?.summary?.enabledPromptCount ?? DEFAULT_TARGET_PROBE_COUNT
  runSettingsDraft.value = {
    targetProbeCount: normalizeTargetProbeCount(runtime?.targetProbeCount ?? fallbackProbeCount),
    targetModelProduct: normalizeTargetModelProduct(runtime?.targetModelProduct),
    autoRunEnabled: runtime?.autoRunEnabled !== false
  }
  baselineRunSettingsPayload.value = serializeRunSettingsPayload(buildRunSettingsPayload(runSettingsDraft.value))
  runSettingsEditMode.value = false
  runSettingsSaveError.value = ''
}

function hydrateDrafts(view: BrandSpaceConfigView | null) {
  if (!view) {
    topicDrafts.value = []
    promptDrafts.value = []
    baselineConfigPayload.value = ''
    configEditMode.value = false
    trackingEditMode.value = false
    hydrateRunSettingsDraft(null)
    syncTrackingDraft()
    return
  }

  const topics = view.topics.map((topic, index) => ({
    tempId: topic.topicId || nextDraftId('topic'),
    topicId: topic.topicId || undefined,
    name: topic.name || `主题 ${index + 1}`,
    description: topic.description || '',
    enabled: topic.enabled !== false,
    source: topic.source || 'system_generated'
  }))
  const topicTempIdByTopicId = new Map(
    topics
      .filter(topic => Boolean(topic.topicId))
      .map(topic => [topic.topicId as string, topic.tempId] as const)
  )
  const prompts = view.prompts.map((prompt) => ({
    tempId: prompt.promptId || nextDraftId('prompt'),
    promptId: prompt.promptId || undefined,
    topicTempId: prompt.topicId ? (topicTempIdByTopicId.get(prompt.topicId) || null) : null,
    text: prompt.text || '',
    enabled: prompt.enabled !== false,
    source: prompt.source || 'system_generated'
  }))

  topicDrafts.value = topics
  promptDrafts.value = prompts
  baselineConfigPayload.value = serializeConfigPayload(buildConfigPayload(topics, prompts))
  configEditMode.value = false
  trackingEditMode.value = false
  hydrateRunSettingsDraft(view)
  configSaveError.value = ''
  trackingError.value = ''
  syncTrackingDraft()
}

function startBasicProfileEdit() {
  if (!basicProfileRuntime.value) {
    return
  }
  hydrateBasicProfileDraft(basicProfileRuntime.value)
  basicProfileSaveSuccess.value = ''
  basicProfileEditMode.value = true
}

function cancelBasicProfileEdit() {
  hydrateBasicProfileDraft(basicProfileRuntime.value)
  basicProfileSaveSuccess.value = ''
}

async function saveBasicProfileEdits() {
  if (!brandSpaceId.value || !basicProfileRuntime.value || basicProfileValidationError.value) {
    return
  }

  basicProfileSavePending.value = true
  basicProfileSaveError.value = ''
  basicProfileSaveSuccess.value = ''

  try {
    const saved = await brandSpacesApi.updateBasicProfile(brandSpaceId.value, buildBasicProfilePayload())
    basicProfileRuntime.value = saved
    if (configRuntime.value) {
      configRuntime.value = {
        ...configRuntime.value,
        brandName: saved.brandName
      }
    }
    hydrateBasicProfileDraft(saved)
    try {
      await brandSpaceDirectory.ensureLoaded(true)
      const matched = brandSpaceDirectory.resolveByBrandSpaceId(brandSpaceId.value)
      if (matched) {
        selectedBrandSpaceId.value = matched.brandSpaceId
        selectedBrand.value = matched.brandName
      } else {
        selectedBrand.value = saved.brandName
      }
    } catch {
      selectedBrand.value = saved.brandName
    }
    configLastSync.value = Date.now()
    basicProfileSaveSuccess.value = '基础信息已保存，后续运行会使用最新主档。'
  } catch (error) {
    basicProfileSaveError.value = error instanceof GeoApiError
      ? error.message
      : (error as Error)?.message || '基础信息保存失败'
  } finally {
    basicProfileSavePending.value = false
  }
}

const configValidationError = computed(() => {
  if (!topicDrafts.value.length) {
    return '至少保留 1 个主题后才能保存。'
  }

  const emptyTopic = topicDrafts.value.findIndex(topic => !topic.name.trim())
  if (emptyTopic >= 0) {
    return `主题 ${emptyTopic + 1} 还没有填写名称。`
  }

  const emptyPrompt = customPromptDraftRows.value.findIndex(({ prompt }) => !prompt.text.trim())
  if (emptyPrompt >= 0) {
    return `自定义 prompt ${emptyPrompt + 1} 还没有填写内容。`
  }

  return ''
})

const hasConfigChanges = computed(() => {
  if (!configEditMode.value || !baselineConfigPayload.value) {
    return false
  }
  return serializeConfigPayload(buildConfigPayload()) !== baselineConfigPayload.value
})

const trackingModeChanged = computed(() => trackingModeDraft.value !== normalizeTrackingMode(currentBrandSpace.value?.trackingStatus))

function clearConfigFeedback() {
  configSaveError.value = ''
  configSaveSuccess.value = ''
}

function clearTrackingFeedback() {
  trackingError.value = ''
  trackingSuccess.value = ''
}

function clearProbePlanFeedback() {
  probePlanActionError.value = ''
  probePlanActionSuccess.value = ''
}

function startRunSettingsEdit() {
  if (!configRuntime.value) {
    return
  }
  hydrateRunSettingsDraft(configRuntime.value)
  runSettingsEditMode.value = true
  runSettingsSaveError.value = ''
  runSettingsSaveSuccess.value = ''
}

function cancelRunSettingsEdit() {
  hydrateRunSettingsDraft(configRuntime.value)
  runSettingsSaveSuccess.value = ''
}

function selectRunSettingsModel(modelKey: string, selectable: boolean) {
  if (!runSettingsEditMode.value || !selectable) {
    return
  }
  runSettingsDraft.value.targetModelProduct = modelKey
}

function updateRunSettingsProbeCount(value: number) {
  if (!runSettingsEditMode.value) {
    return
  }
  runSettingsDraft.value.targetProbeCount = normalizeTargetProbeCount(value)
}

function selectRunMode(autoRunEnabled: boolean) {
  if (!runSettingsEditMode.value) {
    return
  }
  runSettingsDraft.value.autoRunEnabled = autoRunEnabled
}

function startConfigEdit() {
  if (!configRuntime.value || runSettingsEditMode.value) {
    return
  }
  hydrateDrafts(configRuntime.value)
  configEditMode.value = true
  clearConfigFeedback()
}

function cancelConfigEdit() {
  hydrateDrafts(configRuntime.value)
  clearConfigFeedback()
}

function openTrackingEdit() {
  trackingEditMode.value = true
  syncTrackingDraft()
  clearTrackingFeedback()
}

function cancelTrackingEdit() {
  trackingEditMode.value = false
  syncTrackingDraft()
  clearTrackingFeedback()
}

function touchTopic(topic: EditableTopicDraft) {
  topic.source = promoteSource(topic.source)
}

function touchPrompt(prompt: EditablePromptDraft) {
  prompt.source = promoteSource(prompt.source)
}

function moveTopic(index: number, direction: -1 | 1) {
  const target = index + direction
  if (target < 0 || target >= topicDrafts.value.length) {
    return
  }
  const next = topicDrafts.value.slice()
  const [item] = next.splice(index, 1)
  if (!item) {
    return
  }
  item.source = promoteSource(item.source)
  next.splice(target, 0, item)
  topicDrafts.value = next
}

function movePrompt(visibleIndex: number, direction: -1 | 1) {
  const targetVisibleIndex = visibleIndex + direction
  if (targetVisibleIndex < 0 || targetVisibleIndex >= customPromptDraftRows.value.length) {
    return
  }

  const sourceActualIndex = customPromptDraftRows.value[visibleIndex]?.actualIndex
  const targetActualIndex = customPromptDraftRows.value[targetVisibleIndex]?.actualIndex
  if (sourceActualIndex == null || targetActualIndex == null) {
    return
  }

  const next = promptDrafts.value.slice()
  const [item] = next.splice(sourceActualIndex, 1)
  if (!item) {
    return
  }
  item.source = promoteSource(item.source)
  next.splice(targetActualIndex, 0, item)
  promptDrafts.value = next
}

function addTopic() {
  topicDrafts.value = [
    ...topicDrafts.value,
    {
      tempId: nextDraftId('topic'),
      name: '',
      description: '',
      enabled: true,
      source: 'manual'
    }
  ]
}

function addPrompt() {
  promptDrafts.value = [
    ...promptDrafts.value,
    {
      tempId: nextDraftId('prompt'),
      topicTempId: null,
      text: '',
      enabled: true,
      source: 'manual'
    }
  ]
}

async function saveConfigEdits() {
  if (!brandSpaceId.value || !configEditMode.value || configSavePending.value) {
    return
  }

  clearConfigFeedback()

  if (configValidationError.value) {
    configSaveError.value = configValidationError.value
    return
  }

  configSavePending.value = true
  try {
    const view = await brandSpacesApi.updateConfig(brandSpaceId.value, buildConfigPayload())
    configRuntime.value = view
    configLastSync.value = Date.now()
    hydrateDrafts(view)
    await Promise.allSettled([
      brandSpaceDirectory.ensureLoaded(true),
      loadProbePlan(syncNonce),
      loadCreditBalance()
    ])
    configSaveSuccess.value = 'topic 与自定义 prompt 配置已保存，并已回显最新结果。'
  } catch (error) {
    configSaveError.value = error instanceof GeoApiError
      ? error.message
      : (error as Error)?.message || '品牌空间配置保存失败'
  } finally {
    configSavePending.value = false
  }
}

async function saveRunSettings() {
  if (!brandSpaceId.value || !runSettingsEditMode.value || runSettingsSavePending.value) {
    return
  }
  runSettingsSaveError.value = ''
  runSettingsSaveSuccess.value = ''
  if (runSettingsValidationError.value) {
    runSettingsSaveError.value = runSettingsValidationError.value
    return
  }

  runSettingsSavePending.value = true
  try {
    const view = await brandSpacesApi.updateConfig(brandSpaceId.value, {
      ...buildConfigPayload(topicDrafts.value, promptDrafts.value, runSettingsDraft.value)
    })
    configRuntime.value = view
    configLastSync.value = Date.now()
    hydrateDrafts(view)
    await Promise.allSettled([
      brandSpaceDirectory.ensureLoaded(true),
      loadProbePlan(syncNonce),
      loadCreditBalance()
    ])
    runSettingsSaveSuccess.value = '运行设置已保存，下一次运行会按最新配置执行。'
  } catch (error) {
    runSettingsSaveError.value = error instanceof GeoApiError
      ? error.message
      : (error as Error)?.message || '运行设置保存失败'
  } finally {
    runSettingsSavePending.value = false
  }
}

async function saveTrackingMode() {
  if (!brandSpaceId.value || trackingPending.value || !trackingModeChanged.value) {
    trackingEditMode.value = false
    return
  }

  trackingPending.value = true
  clearTrackingFeedback()

  try {
    await brandSpacesApi.updateTrackingMode(brandSpaceId.value, {
      trackingMode: trackingModeDraft.value
    })
    await brandSpaceDirectory.ensureLoaded(true)
    trackingSuccess.value = trackingModeDraft.value === 'PAUSED'
      ? '品牌空间已切换为暂停跟踪。'
      : '品牌空间已恢复跟踪。'
    trackingEditMode.value = false
  } catch (error) {
    trackingError.value = error instanceof GeoApiError
      ? error.message
      : (error as Error)?.message || '跟踪状态更新失败'
  } finally {
    trackingPending.value = false
  }
}

function openArchiveConfirm() {
  archiveConfirmOpen.value = true
  archiveConfirmText.value = ''
  archiveError.value = ''
}

function cancelArchiveConfirm() {
  archiveConfirmOpen.value = false
  archiveConfirmText.value = ''
  archiveError.value = ''
}

function onWorkbenchScroll(event: Event) {
  const target = event.target as HTMLElement | null
  syncWorkbenchScrolled(target)
}

async function loadServiceAccess() {
  try {
    serviceAccess.value = await serviceAccessApi.get()
  } catch {
    serviceAccess.value = null
  }
}

async function loadCreditBalance() {
  if (demoMode) return
  try {
    const response = await creditsApi.getBalance()
    creditBalance.value = typeof response?.creditsRemaining === 'number'
      ? Math.max(0, response.creditsRemaining)
      : 0
  } catch {
    creditBalance.value = null
  }
}

async function loadProbePlan(expectedNonce = syncNonce) {
  if (!brandSpaceId.value) {
    if (expectedNonce === syncNonce) {
      probePlanRuntime.value = null
      probePlanError.value = ''
      probePlanLoading.value = false
    }
    return
  }

  if (expectedNonce === syncNonce) {
    probePlanLoading.value = true
    probePlanError.value = ''
  }

  try {
    const view = await brandSpacesApi.getProbePlan(brandSpaceId.value)
    if (expectedNonce !== syncNonce) {
      return
    }
    probePlanRuntime.value = view
  } catch (error) {
    if (expectedNonce !== syncNonce) {
      return
    }
    probePlanRuntime.value = null
    probePlanError.value = error instanceof GeoApiError
      ? error.message
      : (error as Error)?.message || '探针方案暂时不可读'
  } finally {
    if (expectedNonce === syncNonce) {
      probePlanLoading.value = false
    }
  }
}

async function confirmCurrentProbePlan() {
  if (!brandSpaceId.value || probePlanActionPending.value) {
    return
  }
  clearProbePlanFeedback()
  const blockedReason = probePlanActionBlockedReason.value
  if (blockedReason) {
    probePlanActionError.value = blockedReason
    return
  }

  probePlanActionPending.value = true
  try {
    const view = await brandSpacesApi.confirmProbePlan(brandSpaceId.value, {
      plannedProbeCount: effectiveRunSettings.value.targetProbeCount,
      targetModelProduct: normalizeTargetModelProduct(effectiveRunSettings.value.targetModelProduct),
      executeModeCode: effectiveRunSettings.value.autoRunEnabled ? 'RECURRING' : 'ONE_TIME'
    })
    probePlanRuntime.value = view
    lastExecuteOnceResponse.value = null
    probePlanActionSuccess.value = effectiveRunSettings.value.autoRunEnabled
      ? '当前探针方案已确认，后续自动运行会按最新配置执行。'
      : '当前探针方案已确认，品牌空间保持仅手动运行。'
  } catch (error) {
    probePlanActionError.value = error instanceof GeoApiError
      ? error.message
      : (error as Error)?.message || '探针方案确认失败'
  } finally {
    probePlanActionPending.value = false
  }
}

async function executeCurrentProbePlanOnce() {
  if (!brandSpaceId.value || probePlanActionPending.value) {
    return
  }
  clearProbePlanFeedback()
  const disabledReason = executeOnceDisabledReason.value
  if (disabledReason) {
    probePlanActionError.value = disabledReason
    return
  }

  probePlanActionPending.value = true
  try {
    const response = await brandSpacesApi.executeProbePlanOnce(brandSpaceId.value)
    lastExecuteOnceResponse.value = response
    probePlanRuntime.value = response.probePlan || probePlanRuntime.value
    await Promise.allSettled([
      loadCreditBalance(),
      brandSpaceDirectory.ensureLoaded(true)
    ])
    if (response.skipped) {
      probePlanActionError.value = formatExecuteOnceSkippedReason(response.skippedReason)
    } else {
      probePlanActionSuccess.value = `已创建一次手动运行，实扣 ${response.deductedCredits ?? 0} 积分，剩余 ${response.creditsRemainingAfterRun ?? '待同步'} 积分。`
    }
  } catch (error) {
    probePlanActionError.value = error instanceof GeoApiError
      ? error.message
      : (error as Error)?.message || '立即运行失败'
  } finally {
    probePlanActionPending.value = false
  }
}

async function archiveBrandSpace() {
  if (!brandSpaceId.value || archivePending.value || !archiveCanConfirm.value) {
    return
  }

  archivePending.value = true
  archiveError.value = ''

  try {
    await brandSpacesApi.archive(brandSpaceId.value)
    await brandSpaceDirectory.ensureLoaded(true)
    if (selectedBrandSpaceId.value === brandSpaceId.value) {
      selectedBrandSpaceId.value = ''
    }
    if (selectedBrand.value === archiveBrandName.value) {
      selectedBrand.value = ''
    }
    await navigateTo('/brands')
  } catch (error) {
    archiveError.value = error instanceof GeoApiError
      ? error.message
      : (error as Error)?.message || '品牌空间移出失败'
  } finally {
    archivePending.value = false
  }
}

let syncNonce = 0

async function loadPage() {
  const nonce = ++syncNonce
  configLoading.value = true
  configError.value = ''
  basicProfileLoadError.value = ''

  try {
    await brandSpaceDirectory.ensureLoaded()
    await Promise.allSettled([loadServiceAccess(), loadCreditBalance()])
    const matched = brandSpaceDirectory.resolveByBrandSpaceId(brandSpaceId.value)

    if (matched) {
      selectedBrandSpaceId.value = matched.brandSpaceId
      selectedBrand.value = matched.brandName
    }

    if (!brandSpaceId.value) {
      configRuntime.value = null
      basicProfileRuntime.value = null
      probePlanRuntime.value = null
      probePlanError.value = ''
      clearProbePlanFeedback()
      lastExecuteOnceResponse.value = null
      configLastSync.value = Date.now()
      hydrateDrafts(null)
      hydrateBasicProfileDraft(null)
      return
    }

    const view = await brandSpacesApi.config(brandSpaceId.value)
    if (nonce !== syncNonce) {
      return
    }

    configRuntime.value = view
    configLastSync.value = Date.now()
    hydrateDrafts(view)
    clearProbePlanFeedback()
    lastExecuteOnceResponse.value = null
    await loadProbePlan(nonce)

    try {
      const basicProfile = await brandSpacesApi.basicProfile(brandSpaceId.value)
      if (nonce !== syncNonce) {
        return
      }
      basicProfileRuntime.value = basicProfile
      hydrateBasicProfileDraft(basicProfile)
    } catch (error) {
      if (nonce !== syncNonce) {
        return
      }
      basicProfileRuntime.value = null
      hydrateBasicProfileDraft(null)
      basicProfileLoadError.value = error instanceof GeoApiError
        ? error.message
        : (error as Error)?.message || '基础信息暂不可编辑'
    }
  } catch (error) {
    if (nonce !== syncNonce) {
      return
    }
    configError.value = error instanceof GeoApiError
      ? error.message
      : (error as Error)?.message || '品牌空间配置加载失败'
    configRuntime.value = null
    basicProfileRuntime.value = null
    probePlanRuntime.value = null
    probePlanError.value = ''
    clearProbePlanFeedback()
    lastExecuteOnceResponse.value = null
    hydrateDrafts(null)
    hydrateBasicProfileDraft(null)
  } finally {
    if (nonce === syncNonce) {
      configLoading.value = false
    }
  }
}

onMounted(() => {
  resetWorkbenchScrolled()
  void loadPage()
})

watch(
  () => brandSpaceId.value,
  () => {
    void loadPage()
  }
)

watch(currentBrandSpace, () => {
  if (!trackingEditMode.value) {
    syncTrackingDraft()
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
          <div class="brand-zone brand-zone--stacked">
            <div class="brand-zone__copy">
              <h1>{{ basicProfileRuntime?.brandName || currentBrandSpace?.brandName || configRuntime?.brandName || '品牌空间配置' }}</h1>
              <span>空间配置</span>
            </div>
          </div>

          <div class="head-actions">
            <NuxtLink
              class="btn-plain"
              to="/brands"
            >
              返回品牌管理
            </NuxtLink>
            <NuxtLink
              v-if="brandSpaceId"
              class="btn-plain"
              :to="dashboardPath()"
            >
              查看总览
            </NuxtLink>
          </div>
        </header>
      </div>

      <section
        v-if="showSkeleton"
        class="surface section-surface"
      >
        <div class="empty-state">
          <strong>正在读取品牌空间配置</strong>
          <span>正在同步基础信息，并读取当前生效的 topic 与自定义 prompt 配置。</span>
        </div>
      </section>

      <section
        v-else-if="configError"
        class="surface notice-surface notice-surface--error"
      >
        <div class="notice-copy">
          <strong>品牌空间配置加载失败</strong>
          <span>{{ configError }}</span>
        </div>
      </section>

      <section
        v-else-if="isMissingBrand"
        class="surface notice-surface notice-surface--warn"
      >
        <div class="notice-copy">
          <strong>品牌空间不存在或尚未同步</strong>
          <span>当前品牌空间没有在品牌目录中找到，请先回品牌管理确认状态。</span>
        </div>
      </section>

      <section
        v-else
        class="page-body"
      >
        <section class="surface notice-surface notice-surface--muted">
          <div class="notice-copy">
            <strong>{{ demoMode ? '本案例的品牌与探针配置' : '这里管理的是品牌空间的长期配置' }}</strong>
            <span v-if="!demoMode">你在这里调整的 topic 和自定义 prompt，会用于后续分析。</span>
          </div>
        </section>

        <section
          v-if="!trackingContractReady"
          class="surface notice-surface notice-surface--muted"
        >
          <div class="notice-copy">
            <strong>跟踪状态仍在同步</strong>
            <span>当前品牌空间的长期跟踪状态还没完全返回，列表里会先明确显示为“状态同步中”。</span>
          </div>
        </section>

        <section
          v-if="configSaveError"
          class="surface notice-surface notice-surface--error"
        >
          <div class="notice-copy">
            <strong>配置保存失败</strong>
            <span>{{ configSaveError }}</span>
          </div>
        </section>

        <section
          v-if="configSaveSuccess"
          class="surface notice-surface notice-surface--success"
        >
          <div class="notice-copy">
            <strong>配置已保存</strong>
            <span>{{ configSaveSuccess }}</span>
          </div>
        </section>

        <section
          v-if="basicProfileSaveError"
          class="surface notice-surface notice-surface--error"
        >
          <div class="notice-copy">
            <strong>基础信息保存失败</strong>
            <span>{{ basicProfileSaveError }}</span>
          </div>
        </section>

        <section
          v-if="basicProfileSaveSuccess"
          class="surface notice-surface notice-surface--success"
        >
          <div class="notice-copy">
            <strong>基础信息已保存</strong>
            <span>{{ basicProfileSaveSuccess }}</span>
          </div>
        </section>

        <section
          v-if="trackingError"
          class="surface notice-surface notice-surface--error"
        >
          <div class="notice-copy">
            <strong>跟踪状态更新失败</strong>
            <span>{{ trackingError }}</span>
          </div>
        </section>

        <section
          v-if="trackingSuccess"
          class="surface notice-surface notice-surface--success"
        >
          <div class="notice-copy">
            <strong>跟踪状态已更新</strong>
            <span>{{ trackingSuccess }}</span>
          </div>
        </section>

        <section
          v-if="runSettingsSaveError"
          class="surface notice-surface notice-surface--error"
        >
          <div class="notice-copy">
            <strong>运行设置保存失败</strong>
            <span>{{ runSettingsSaveError }}</span>
          </div>
        </section>

        <section
          v-if="runSettingsSaveSuccess"
          class="surface notice-surface notice-surface--success"
        >
          <div class="notice-copy">
            <strong>运行设置已保存</strong>
            <span>{{ runSettingsSaveSuccess }}</span>
          </div>
        </section>

        <section class="surface section-surface">
          <div class="surface-head surface-head--compact">
            <div class="surface-head__title">
              <strong>基础信息</strong>
              <span>最近更新 {{ formatDate(updatedAt(), '未同步') }}</span>
            </div>
            <GeoInfoPopover text="这里维护品牌名、官网、竞品和品牌介绍，后续分析会参考这些信息。" />
            <div class="surface-inline-actions">
              <button
                v-if="!basicProfileEditMode && canEditBasicProfile"
                type="button"
                class="btn-plain"
                @click="startBasicProfileEdit"
              >
                编辑基础信息
              </button>
              <template v-else>
                <button
                  type="button"
                  class="btn-plain"
                  :disabled="basicProfileSavePending"
                  @click="cancelBasicProfileEdit"
                >
                  取消
                </button>
                <button
                  type="button"
                  class="btn-primary"
                  :disabled="basicProfileSavePending || !hasBasicProfileChanges || Boolean(basicProfileValidationError)"
                  @click="saveBasicProfileEdits"
                >
                  {{ basicProfileSavePending ? '保存中...' : '保存基础信息' }}
                </button>
              </template>
            </div>
          </div>

          <div class="detail-grid">
            <article class="detail-card">
              <span>品牌名</span>
              <template v-if="basicProfileEditMode">
                <label class="editor-field">
                  <input
                    v-model="basicProfileDraft.brandName"
                    type="text"
                    class="editor-input"
                    maxlength="120"
                    placeholder="填写品牌名"
                  >
                </label>
              </template>
              <strong v-else>{{ basicBrandName }}</strong>
            </article>
            <article class="detail-card">
              <span>品类</span>
              <template v-if="basicProfileEditMode">
                <label class="editor-field">
                  <input
                    v-model="basicProfileDraft.category"
                    type="text"
                    class="editor-input"
                    maxlength="120"
                    placeholder="填写品类"
                  >
                </label>
              </template>
              <strong v-else>{{ basicCategory }}</strong>
            </article>
            <article class="detail-card">
              <span>官网</span>
              <template v-if="basicProfileEditMode">
                <label class="editor-field">
                  <input
                    v-model="basicProfileDraft.officialWebsite"
                    type="text"
                    class="editor-input"
                    maxlength="500"
                    placeholder="例如：https://www.example.com"
                  >
                </label>
              </template>
              <strong v-else>{{ officialWebsite }}</strong>
            </article>
            <article class="detail-card detail-card--wide">
              <span>竞品</span>
              <template v-if="basicProfileEditMode">
                <label class="editor-field">
                  <textarea
                    v-model="basicProfileDraft.competitorsText"
                    class="editor-textarea"
                    rows="4"
                    maxlength="500"
                    placeholder="多个竞品用逗号或换行分隔"
                  />
                </label>
              </template>
              <template v-else>
                <strong>{{ competitorDisplayText }}</strong>
                <small v-if="competitorHelperText">{{ competitorHelperText }}</small>
              </template>
            </article>
            <article class="detail-card detail-card--wide">
              <span>品牌介绍</span>
              <template v-if="basicProfileEditMode">
                <label class="editor-field">
                  <textarea
                    v-model="basicProfileDraft.supplementalBrief"
                    class="editor-textarea"
                    rows="5"
                    maxlength="1000"
                    placeholder="补充品牌定位、主打产品、目标客群或你特别关注的方向"
                  />
                </label>
              </template>
              <template v-else>
                <p class="detail-card__body-text">{{ basicProfileRuntime?.supplementalBrief || '未填写' }}</p>
              </template>
            </article>
          </div>

          <div
            v-if="(!canEditBasicProfile && basicProfileLoadError) || (basicProfileEditMode && basicProfileValidationError)"
            class="editor-status-row"
          >
            <span v-if="!canEditBasicProfile && basicProfileLoadError">{{ basicProfileLoadError }}</span>
            <span v-else>{{ basicProfileValidationError }}</span>
          </div>
        </section>

        <section class="surface section-surface">
          <div class="surface-head surface-head--compact">
            <div class="surface-head__title">
              <strong>运行设置</strong>
              <span>{{ demoMode ? '本案例使用的模型、探针数与运行方式' : '模型、探针数、运行方式与积分预估' }}</span>
            </div>
            <GeoInfoPopover v-if="!demoMode" text="运行设置会同时影响手动首跑和后续每日自动运行，所有消耗都按统一积分结算。" />

            <div class="surface-inline-actions">
              <button
                v-if="!runSettingsEditMode"
                type="button"
                class="btn-plain"
                :disabled="configEditMode"
                @click="startRunSettingsEdit"
              >
                编辑运行设置
              </button>
              <template v-else>
                <button
                  type="button"
                  class="btn-plain"
                  :disabled="runSettingsSavePending"
                  @click="cancelRunSettingsEdit"
                >
                  取消
                </button>
                <button
                  type="button"
                  class="btn-primary"
                  :disabled="runSettingsSavePending || !hasRunSettingsChanges || Boolean(runSettingsValidationError)"
                  @click="saveRunSettings"
                >
                  {{ runSettingsSavePending ? '保存中...' : '保存运行设置' }}
                </button>
              </template>
            </div>
          </div>

          <div class="editor-status-row">
            <span>保存状态：{{ runSettingsStatusLabel }}</span>
            <span v-if="runSettingsEditMode && runSettingsValidationError">{{ runSettingsValidationError }}</span>
            <span v-else-if="runSettingsEditMode && !hasRunSettingsChanges">当前草稿与已保存配置一致</span>
            <span v-else>当前模式：{{ currentRunModeLabel }}</span>
          </div>

          <div class="run-settings-grid">
            <section class="run-settings-block">
              <div class="surface-head surface-head--compact">
                <strong>{{ demoMode ? '案例模型' : '选择模型' }}</strong>
                <span v-if="!demoMode">当前开放 3 个入口，仅豆包可用</span>
              </div>
              <div v-if="demoMode" class="detail-card"><strong>豆包（Seed 2.0 Lite）</strong><span>本次运行未开启联网搜索</span></div>
              <div v-else class="model-selection-grid">
                <button
                  v-for="option in monitoringModelOptions"
                  :key="option.key"
                  type="button"
                  class="model-option-card"
                  :class="{
                    'model-option-card--selected': option.selected,
                    'model-option-card--disabled': !option.selectable || !runSettingsEditMode
                  }"
                  :disabled="!runSettingsEditMode || !option.selectable"
                  @click="selectRunSettingsModel(option.key, option.selectable)"
                >
                  <div class="model-option-card__head">
                    <div class="model-option-card__title">
                      <strong>{{ option.label }}</strong>
                      <span class="model-option-card__capability">{{ option.capabilityLabel }}</span>
                    </div>
                    <span>{{ option.statusLabel }}</span>
                  </div>
                  <p>{{ option.description }}</p>
                  <p class="model-option-card__capability-copy">{{ option.capabilityDescription }}</p>
                </button>
              </div>
            </section>

            <section class="run-settings-block">
              <div class="surface-head surface-head--compact">
                <strong>探针数</strong>
                <span>允许 1 到 100 条</span>
              </div>
              <div class="probe-range-control">
                <input
                  :value="effectiveRunSettings.targetProbeCount"
                  type="range"
                  min="1"
                  :max="MAX_TARGET_PROBE_COUNT"
                  step="1"
                  :disabled="!runSettingsEditMode"
                  @input="updateRunSettingsProbeCount(Number(($event.target as HTMLInputElement).value))"
                >
                <label>
                  <span>精确探针数</span>
                  <input
                    :value="effectiveRunSettings.targetProbeCount"
                    type="number"
                    inputmode="numeric"
                    min="1"
                    :max="MAX_TARGET_PROBE_COUNT"
                    :disabled="!runSettingsEditMode"
                    @input="updateRunSettingsProbeCount(Number(($event.target as HTMLInputElement).value))"
                  >
                </label>
              </div>
              <div v-if="!demoMode" class="probe-cost-line">
                <span>{{ creditsPerProbe }} 积分 / 条探针</span>
                <strong>{{ estimatedCreditsPerRun }} 积分</strong>
              </div>
            </section>

            <section class="run-settings-block">
              <div class="surface-head surface-head--compact">
                <strong>运行方式</strong>
                <span>{{ currentRunModeLabel }}</span>
              </div>
              <div class="run-mode-grid">
                <button
                  type="button"
                  class="run-mode-card"
                  :class="{ 'is-selected': effectiveRunSettings.autoRunEnabled }"
                  :disabled="!runSettingsEditMode"
                  @click="selectRunMode(true)"
                >
                  <strong>每日自动运行</strong>
                  <span>保留当前配置，每天自动执行一次。</span>
                </button>
                <button
                  type="button"
                  class="run-mode-card"
                  :class="{ 'is-selected': !effectiveRunSettings.autoRunEnabled }"
                  :disabled="!runSettingsEditMode"
                  @click="selectRunMode(false)"
                >
                  <strong>仅手动运行</strong>
                  <span>保留配置，但自动运行关闭。</span>
                </button>
              </div>
            </section>

            <section v-if="!demoMode" class="run-settings-block">
              <div class="surface-head surface-head--compact">
                <strong>结算预估</strong>
                <span>提交前后端都会再次校验积分</span>
              </div>
              <div class="detail-grid detail-grid--compact">
                <article class="detail-card">
                  <span>扣费口径</span>
                  <strong>{{ creditsPerProbe }} 积分 / 条</strong>
                </article>
                <article class="detail-card">
                  <span>可用积分</span>
                  <strong>{{ creditBalanceLabel }}</strong>
                </article>
                <article class="detail-card">
                  <span>本次预计消耗</span>
                  <strong>{{ estimatedCreditsPerRun }} 积分</strong>
                </article>
                <article class="detail-card">
                  <span>下次运行后余额</span>
                  <strong>{{ balanceAfterNextRunLabel }}</strong>
                </article>
              </div>
            </section>

            <section class="run-settings-block">
              <div class="surface-head surface-head--compact">
                <strong>探针方案与立即运行</strong>
                <span>{{ probePlanStatusLabel }}</span>
              </div>

              <div class="detail-grid detail-grid--compact">
                <article class="detail-card">
                  <span>方案状态</span>
                  <strong>{{ probePlanStatusLabel }}</strong>
                  <small>{{ probePlanStatusDetail }}</small>
                </article>
                <article class="detail-card">
                  <span>当前内容快照</span>
                  <strong>{{ probePlanSnapshotCount }} 条</strong>
                  <small v-if="currentProbePlan?.versionNo">版本 {{ currentProbePlan.versionNo }}</small>
                  <small v-else>还没有确认版本</small>
                </article>
                <article class="detail-card">
                  <span>最近动作</span>
                  <strong>{{ latestProbePlanRecordLabel }}</strong>
                  <small>{{ formatDate(latestProbePlanRecord?.createdAt, '暂无记录') }}</small>
                </article>
                <article class="detail-card">
                  <span>最近一次手动运行</span>
                  <strong>{{ latestExecuteResultLabel }}</strong>
                  <small>{{ latestExecuteResultDetail }}</small>
                </article>
              </div>

              <div class="editor-status-row">
                <span v-if="probePlanError">{{ probePlanError }}</span>
                <span v-else-if="probePlanActionError">{{ probePlanActionError }}</span>
                <span v-else-if="probePlanActionSuccess">{{ probePlanActionSuccess }}</span>
                <span v-else-if="executeOnceDisabledReason">{{ executeOnceDisabledReason }}</span>
                <span v-else>确认一次方案后，才可立即运行。</span>
              </div>

              <div class="surface-inline-actions">
                <button
                  type="button"
                  class="btn-plain"
                  :disabled="probePlanActionPending || Boolean(probePlanActionBlockedReason)"
                  @click="confirmCurrentProbePlan"
                >
                  {{ probePlanActionPending ? '处理中...' : '确认当前探针方案' }}
                </button>
                <button
                  type="button"
                  class="btn-primary"
                  :disabled="probePlanActionPending || Boolean(executeOnceDisabledReason)"
                  @click="executeCurrentProbePlanOnce"
                >
                  {{ probePlanActionPending ? '处理中...' : '立即运行一次' }}
                </button>
              </div>
            </section>
          </div>
        </section>

        <section class="surface section-surface">
          <div class="surface-head surface-head--compact">
            <div class="surface-head__title">
              <strong>配置编辑</strong>
              <span>topic 与自定义 prompt</span>
            </div>
            <GeoInfoPopover text="这里维护长期关注的 topic 和你手动补充的自定义 prompt。" />

            <div class="surface-inline-actions">
              <button
                v-if="!configEditMode"
                type="button"
                class="btn-plain"
                :disabled="runSettingsEditMode"
                @click="startConfigEdit"
              >
                编辑 topic 与自定义 prompt
              </button>
              <template v-else>
                <button
                  type="button"
                  class="btn-plain"
                  :disabled="configSavePending"
                  @click="cancelConfigEdit"
                >
                  取消
                </button>
                <button
                  type="button"
                  class="btn-primary"
                  :disabled="configSavePending || !hasConfigChanges || Boolean(configValidationError)"
                  @click="saveConfigEdits"
                >
                  {{ configSavePending ? '保存中...' : '保存配置' }}
                </button>
              </template>
            </div>
          </div>

          <div class="editor-status-row">
            <span>保存状态：{{ configWriteStatus }}</span>
            <span v-if="configEditMode && configValidationError">{{ configValidationError }}</span>
            <span v-else-if="configEditMode && !hasConfigChanges">当前草稿与已保存配置一致</span>
            <span v-else-if="configEditMode">当前有未保存改动</span>
          </div>
        </section>

        <section class="config-columns">
          <section class="surface section-surface">
            <div class="surface-head surface-head--compact">
              <strong>topic 配置</strong>
              <span>{{ configSummaryTopicCount }} 个</span>
            </div>

            <div class="source-summary-grid">
              <article class="summary-card">
                <span>全部 topic</span>
                <strong>{{ topicStatusSummary.total }}</strong>
              </article>
              <article class="summary-card">
                <span>启用中</span>
                <strong>{{ topicStatusSummary.active }}</strong>
              </article>
              <article class="summary-card">
                <span>已停用</span>
                <strong>{{ topicStatusSummary.inactive }}</strong>
              </article>
            </div>

            <div
              v-if="configEditMode"
              class="editor-list"
            >
              <article
                v-for="(topic, index) in topicDrafts"
                :key="topic.tempId"
                class="editor-card"
              >
                <div class="editor-card__head">
                  <div class="editor-card__meta">
                    <strong>主题 {{ index + 1 }}</strong>
                  </div>

                  <div class="editor-card__actions">
                    <label class="toggle-chip">
                      <input
                        v-model="topic.enabled"
                        type="checkbox"
                        @change="touchTopic(topic)"
                      >
                      <span>{{ topic.enabled ? '启用中' : '已停用' }}</span>
                    </label>
                    <button
                      type="button"
                      class="btn-plain btn-plain--small"
                      :disabled="index === 0"
                      @click="moveTopic(index, -1)"
                    >
                      上移
                    </button>
                    <button
                      type="button"
                      class="btn-plain btn-plain--small"
                      :disabled="index === topicDrafts.length - 1"
                      @click="moveTopic(index, 1)"
                    >
                      下移
                    </button>
                  </div>
                </div>

                <label class="editor-field">
                  <span>主题名称</span>
                  <input
                    v-model="topic.name"
                    type="text"
                    class="editor-input"
                    maxlength="120"
                    placeholder="例如：产品力、价格带、售后体验"
                    @input="touchTopic(topic)"
                  >
                </label>

                <label class="editor-field">
                  <span>主题说明</span>
                  <textarea
                    v-model="topic.description"
                    class="editor-textarea"
                    rows="3"
                    maxlength="500"
                    placeholder="补充说明这个主题关注什么问题"
                    @input="touchTopic(topic)"
                  />
                </label>
              </article>

              <button
                type="button"
                class="btn-plain btn-plain--add"
                @click="addTopic"
              >
                新增主题
              </button>
            </div>

            <div
              v-else-if="configTopics.length"
              class="entity-list entity-list--scrollable"
            >
              <article
                v-for="topic in configTopics"
                :key="topic.topicId"
                class="entity-list__item"
              >
                <strong>{{ topic.name }}</strong>
                <small>{{ topic.enabled === false ? '已停用' : '启用中' }}</small>
                <small v-if="topic.description">{{ topic.description }}</small>
              </article>
            </div>
            <div
              v-else
              class="entity-empty"
            >
              当前品牌空间还没有主题配置。
            </div>
          </section>

          <section class="surface section-surface">
            <div class="surface-head surface-head--compact">
              <strong>自定义 prompt</strong>
              <span>可选补充</span>
            </div>

            <div class="prompt-guidance">
              <strong>系统会自动补充更多问题</strong>
              <span>{{ autoPromptMessage }}</span>
            </div>

            <div
              v-if="configEditMode"
              class="editor-list editor-list--prompt"
            >
              <div
                v-if="!customPromptDraftRows.length"
                class="entity-empty"
              >
                当前还没有自定义 prompt，可按需补充更细的追问。
              </div>
              <article
                v-for="(row, index) in customPromptDraftRows"
                :key="row.prompt.tempId"
                class="editor-card"
              >
                <div class="editor-card__head">
                  <div class="editor-card__meta">
                    <strong>自定义 prompt {{ index + 1 }}</strong>
                  </div>

                  <div class="editor-card__actions">
                    <label class="toggle-chip">
                      <input
                        v-model="row.prompt.enabled"
                        type="checkbox"
                        @change="touchPrompt(row.prompt)"
                      >
                      <span>{{ row.prompt.enabled ? '启用中' : '已停用' }}</span>
                    </label>
                    <button
                      type="button"
                      class="btn-plain btn-plain--small"
                      :disabled="index === 0"
                      @click="movePrompt(index, -1)"
                    >
                      上移
                    </button>
                    <button
                      type="button"
                      class="btn-plain btn-plain--small"
                      :disabled="index === customPromptDraftRows.length - 1"
                      @click="movePrompt(index, 1)"
                    >
                      下移
                    </button>
                  </div>
                </div>

                <label class="editor-field">
                  <span>prompt 内容</span>
                  <textarea
                    v-model="row.prompt.text"
                    class="editor-textarea"
                    rows="3"
                    maxlength="1000"
                    placeholder="填写你希望额外追问的具体问题"
                    @input="touchPrompt(row.prompt)"
                  />
                </label>

                <label class="editor-field">
                  <span>关联 topic</span>
                  <select
                    v-model="row.prompt.topicTempId"
                    class="editor-input"
                    @change="touchPrompt(row.prompt)"
                  >
                    <option
                      v-for="option in editableTopicOptions"
                      :key="`prompt-topic-${option.key || 'ungrouped'}`"
                      :value="option.key || null"
                    >
                      {{ option.label }}
                    </option>
                  </select>
                </label>
              </article>

              <button
                type="button"
                class="btn-plain btn-plain--add"
                @click="addPrompt"
              >
                新增自定义 prompt
              </button>
            </div>

            <div
              v-else-if="customConfigPrompts.length"
              class="entity-list entity-list--scrollable"
            >
              <article
                v-for="prompt in customConfigPrompts"
                :key="prompt.promptId"
                class="entity-list__item"
              >
                <strong>{{ prompt.text }}</strong>
                <small>{{ prompt.topicName || '未关联 topic' }} · {{ prompt.enabled === false ? '已停用' : '启用中' }}</small>
              </article>
            </div>
            <div
              v-else
              class="entity-empty"
            >
              当前还没有自定义 prompt。系统会继续围绕已启用的 topic 自动补齐监测问题。
            </div>
          </section>
        </section>

        <section v-if="!demoMode" class="surface section-surface danger-surface">
          <div class="surface-head surface-head--compact">
            <strong>移出当前账号</strong>
            <span>危险操作</span>
          </div>

          <div class="danger-copy">
            <strong>把这个品牌空间从当前账号移出，并停止继续跟踪</strong>
            <span>执行后，它会从品牌管理、分析页和全站品牌选择器中消失；30 天内仍可恢复，之后退出自助恢复区，不做立即物理删除。</span>
          </div>

          <div
            v-if="!archiveConfirmOpen"
            class="danger-actions"
          >
            <button
              type="button"
              class="btn-danger"
              @click="openArchiveConfirm"
            >
              移出该品牌
            </button>
          </div>

          <div
            v-else
            class="danger-confirm"
          >
            <label class="danger-confirm__field">
              <span>请输入品牌名 <strong>{{ archiveBrandName || '当前品牌' }}</strong> 确认移出</span>
              <input
                v-model="archiveConfirmText"
                type="text"
                class="danger-confirm__input"
                :placeholder="archiveBrandName || '输入品牌名确认'"
                :disabled="archivePending"
              >
            </label>

            <small class="danger-confirm__hint">
              这会立刻停用该品牌空间，并把它切换到可恢复的已移出状态。
            </small>

            <div class="danger-actions">
              <button
                type="button"
                class="btn-plain"
                :disabled="archivePending"
                @click="cancelArchiveConfirm"
              >
                取消
              </button>
              <button
                type="button"
                class="btn-danger"
                :disabled="archivePending || !archiveCanConfirm"
                @click="archiveBrandSpace"
              >
                {{ archivePending ? '移出中...' : '确认移出当前账号' }}
              </button>
            </div>

            <small
              v-if="archiveError"
              class="danger-error"
            >{{ archiveError }}</small>
          </div>
        </section>
      </section>
    </section>
  </main>
</template>

<style scoped src="~/assets/css/engine-shell.css"></style>

<style scoped>
.page-body {
  display: grid;
  gap: 10px;
}

.brand-zone--stacked {
  display: grid;
  gap: 6px;
}

.brand-zone__copy {
  display: grid;
  gap: 2px;
}

.brand-zone__copy span {
  font-size: 12px;
  color: #6b7280;
}

.overview-grid,
.detail-grid,
.source-summary-grid {
  display: grid;
  gap: 10px;
}

.overview-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.detail-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.source-summary-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.detail-grid--compact {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.detail-card--wide {
  grid-column: span 2;
}

.surface-head__status {
  max-width: 420px;
  font-size: 12px;
  line-height: 1.4;
  color: #6b7280;
  text-align: right;
}

.detail-card small {
  font-size: 12px;
  line-height: 1.5;
  color: #6b7280;
}

.detail-card__body-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  font-weight: 400;
  color: #111827;
}

.model-selection-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.model-option-card {
  display: grid;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid #d1d5db;
  background: #ffffff;
  text-align: left;
}

.model-option-card--selected {
  border-color: #111827;
  background: #f9fafb;
}

.model-option-card--disabled {
  background: #f9fafb;
  color: #6b7280;
  cursor: not-allowed;
}

.model-option-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.model-option-card__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.model-option-card__head strong {
  font-size: 14px;
  color: #111827;
}

.model-option-card__capability {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 0 6px;
  border: 1px solid #d1d5db;
  background: #ffffff;
  color: #4b5563;
  font-size: 10px;
  line-height: 1;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.model-option-card__head span {
  font-size: 11px;
  color: #6b7280;
}

.model-option-card p,
.model-selection-note {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: #4b5563;
}

.model-option-card__capability-copy {
  color: #6b7280;
}

.run-settings-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.run-settings-block {
  display: grid;
  gap: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  background: #ffffff;
  padding: 12px;
}

.probe-range-control {
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(0, 1fr) 136px;
  align-items: center;
}

.probe-range-control > input[type="range"] {
  width: 100%;
  accent-color: #111827;
}

.probe-range-control label {
  display: grid;
  gap: 6px;
}

.probe-range-control label span {
  font-size: 12px;
  color: #4b5563;
}

.probe-range-control label input {
  width: 100%;
  min-height: 34px;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #ffffff;
  padding: 8px 10px;
  font-size: 12px;
  color: #111827;
}

.probe-cost-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 2px;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.08), rgba(255, 255, 255, 0.92));
  padding: 10px 12px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.probe-cost-line strong {
  color: #111827;
  font-size: 15px;
}

.run-mode-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.run-mode-card {
  display: grid;
  gap: 6px;
  text-align: left;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #ffffff;
  padding: 12px;
  color: #111827;
}

.run-mode-card.is-selected {
  border-color: #111827;
  background: #f9fafb;
}

.run-mode-card:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.run-mode-card strong {
  font-size: 13px;
}

.run-mode-card span {
  font-size: 12px;
  line-height: 1.6;
  color: #6b7280;
}

.config-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.surface-inline-actions,
.editor-status-row,
.editor-card__actions,
.tracking-panel__actions,
.danger-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.editor-status-row {
  font-size: 12px;
  color: #6b7280;
}

.entity-list,
.editor-list {
  display: grid;
  gap: 8px;
}

.prompt-guidance {
  display: grid;
  gap: 6px;
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  background: #f8fafc;
}

.prompt-guidance strong {
  font-size: 13px;
  color: #111827;
}

.prompt-guidance span {
  font-size: 12px;
  color: #4b5563;
}

.entity-list--scrollable {
  max-height: 480px;
  overflow: auto;
  padding-right: 4px;
}

.entity-list__item,
.editor-card {
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  background: #ffffff;
  padding: 10px;
  display: grid;
  gap: 8px;
}

.entity-list__item strong,
.editor-card__meta strong {
  font-size: 13px;
  color: #111827;
}

.entity-list__item small,
.entity-empty,
.editor-card__meta small {
  font-size: 12px;
  color: #6b7280;
}

.editor-card__head,
.editor-card__meta,
.tracking-panel,
.tracking-panel__summary,
.tracking-editor,
.tracking-option,
.tracking-option__copy {
  display: grid;
  gap: 6px;
}

.editor-card__head {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
}

.editor-field {
  display: grid;
  gap: 6px;
}

.editor-field span {
  font-size: 12px;
  color: #374151;
}

.editor-input,
.editor-textarea,
.danger-confirm__input {
  width: 100%;
  min-height: 34px;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #ffffff;
  padding: 8px 10px;
  font-size: 12px;
  color: #111827;
}

.editor-textarea {
  resize: vertical;
}

.editor-input:focus,
.editor-textarea:focus,
.danger-confirm__input:focus {
  outline: none;
  border-color: #111827;
}

.toggle-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
  padding: 0 8px;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  background: #f8fafc;
  font-size: 12px;
  color: #374151;
}

.toggle-chip input {
  margin: 0;
}

.btn-primary,
.btn-danger,
.btn-plain--add {
  min-height: 34px;
  border-radius: 2px;
  padding: 0 12px;
  font-size: 12px;
  cursor: pointer;
}

.btn-primary {
  border: 1px solid #111827;
  background: #111827;
  color: #ffffff;
}

.btn-primary:disabled,
.btn-danger:disabled,
.btn-plain--add:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.btn-plain--small {
  min-height: 28px;
  padding: 0 10px;
  font-size: 12px;
}

.btn-plain--add {
  justify-self: flex-start;
  border: 1px dashed #9ca3af;
  background: #ffffff;
  color: #111827;
}

.tracking-panel {
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  background: #ffffff;
  padding: 10px;
}

.tracking-panel__summary span {
  font-size: 13px;
  font-weight: 700;
  color: #111827;
}

.tracking-panel__summary small,
.tracking-option__copy small {
  font-size: 12px;
  color: #6b7280;
}

.tracking-option {
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  background: #ffffff;
  padding: 10px;
}

.tracking-option input {
  margin-top: 2px;
}

.tracking-option__copy strong {
  font-size: 13px;
  color: #111827;
}

.danger-surface {
  border-color: #fca5a5;
  background: #fff7f7;
}

:deep(.notice-surface--success) {
  border-color: #86efac;
  background: #f0fdf4;
}

.danger-copy {
  display: grid;
  gap: 4px;
}

.danger-copy strong {
  font-size: 13px;
  color: #7f1d1d;
}

.danger-copy span,
.danger-confirm__field span,
.danger-confirm__hint {
  font-size: 12px;
  color: #991b1b;
}

.btn-danger {
  border: 1px solid #dc2626;
  background: #dc2626;
  color: #ffffff;
}

.danger-confirm {
  display: grid;
  gap: 10px;
}

.danger-confirm__field {
  display: grid;
  gap: 6px;
}

.danger-error {
  font-size: 12px;
  color: #b91c1c;
}

@media (max-width: 1280px) {
  .overview-grid,
  .detail-grid,
  .source-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .surface-head__status {
    max-width: none;
    text-align: left;
  }
}

@media (max-width: 1024px) {
  .run-settings-grid,
  .config-columns {
    grid-template-columns: 1fr;
  }

  .editor-card__head {
    grid-template-columns: 1fr;
  }

  .probe-range-control,
  .run-mode-grid,
  .model-selection-grid {
    grid-template-columns: 1fr;
  }
}
</style>
