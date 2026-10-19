<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import {
  getProblemSpaceBucketLabel,
  resolveProblemSpaceBucket
} from '~/composables/problemSpaceBuckets'
import { buildPublicPreviewModelOptions, resolveProductModelLabel } from '~/utils/productModels'
import type {
  BrandSpaceCreateResponse,
  BrandSpaceDraft,
  BrandSpacePreview,
  BrandSpaceRecognition,
  BrandSpaceTopic,
  ServiceAccess
} from '~/types/brand-space'

definePageMeta({
  layout: false,
  middleware: 'auth',
  requiresAuth: true
})

useSeoMeta({
  title: '添加品牌 · Traclux 探镜'
})

type WizardStepId = 1 | 2 | 3 | 4

type RecognitionSyncState = 'clean' | 'details-dirty' | 'core-dirty'
type ExecuteModeCode = 'RECURRING' | 'ONE_TIME'

interface WizardStepMeta {
  id: WizardStepId
  label: string
  hint: string
}

interface PreviewRequestOptions {
  draft?: BrandSpaceDraft
  recognition?: BrandSpaceRecognition | null
  topics?: BrandSpaceTopic[] | null
  bypassCache?: boolean
}

interface DraftSnapshot {
  brandName: string
  category: string
  officialWebsite: string
  competitorsText: string
  supplementalBrief: string
}

interface CategoryTag {
  id: string
  label: string
  source: 'recommended' | 'manual'
}

interface ProbeCountOption {
  key: string
  label: string
  value: number
  note: string
}

type CompetitorGroup = 'direct' | 'indirect'

const MAX_TOPIC_SELECTION = 10
const MAX_TOTAL_COMPETITORS = 10
const MAX_DIRECT_COMPETITORS = 5
const MAX_INDIRECT_COMPETITORS = 5
const DEFAULT_TARGET_MODEL_PRODUCT = 'doubao'
const MAX_TARGET_PROBE_COUNT = 100
const DEFAULT_CREDITS_PER_PROBE = 1

const wizardSteps: WizardStepMeta[] = [
  { id: 1, label: '品牌信息', hint: '先确定品牌对象。' },
  { id: 2, label: '识别结果', hint: '把识别结果修正到可用为止。' },
  { id: 3, label: '关注主题', hint: '只保留你想长期跟踪的主题。' },
  { id: 4, label: '确认运行', hint: '只确认模型、探针数和运行方式。' }
]

const brandSpacesApi = useBrandSpacesApi()
const serviceAccessApi = useServiceAccessApi()
const creditsApi = useCreditsApi()
const route = useRoute()
const {
  workbenchRef,
  workbenchScrolled,
  resetWorkbenchScrolled,
  syncWorkbenchScrolled
} = useWorkbenchShellState()

const form = reactive({
  brandName: readPrefillQuery('brandName'),
  category: readPrefillQuery('category'),
  officialWebsite: normalizeWebsiteInput(readPrefillQuery('officialWebsite') || readPrefillQuery('website') || readPrefillQuery('suggestedWebsite')),
  competitorsText: '',
  supplementalBrief: ''
})

const currentStep = ref<WizardStepId>(1)
const preview = ref<BrandSpacePreview | null>(null)
const topics = ref<BrandSpaceTopic[]>([])
const previewPending = ref(false)
const createPending = ref(false)
const errorMessage = ref('')
const recognitionNotice = ref('')
const serviceAccess = ref<ServiceAccess | null>(null)
const serviceAccessError = ref('')
const creditBalance = ref<number | null>(null)
const creditBalanceError = ref('')
const customTopicName = ref('')
const customCategoryName = ref('')
const directCompetitorInput = ref('')
const indirectCompetitorInput = ref('')
const lastAutoFilledCompetitorsText = ref('')
const lastAutoFilledSupplementalBrief = ref('')
const lastPreviewSnapshot = ref<DraftSnapshot | null>(null)
const previewCache = new Map<string, BrandSpacePreview>()
const categoryTags = ref<CategoryTag[]>([])
const directCompetitorTags = ref<string[]>([])
const indirectCompetitorTags = ref<string[]>([])
const previewHydrating = ref(false)
const selectedTargetProbeCount = ref<number | null>(null)
const selectedTargetModelProduct = ref(DEFAULT_TARGET_MODEL_PRODUCT)
const selectedExecuteModeCode = ref<ExecuteModeCode>('RECURRING')

const currentStepMeta = computed(() => wizardSteps[currentStep.value - 1]!)

const isServiceBlocked = computed(() => {
  return serviceAccess.value?.status === 'inactive' || serviceAccess.value?.status === 'expired'
})

const previewSyncState = computed<RecognitionSyncState>(() => {
  const baseline = lastPreviewSnapshot.value
  if (!baseline) {
    return 'clean'
  }

  const current = buildDraftSnapshot(buildDraft())

  const coreChanged = current.brandName !== baseline.brandName
    || current.category !== baseline.category

  if (coreChanged) {
    return 'core-dirty'
  }

  const detailsChanged = current.officialWebsite !== baseline.officialWebsite
    || current.competitorsText !== baseline.competitorsText
    || current.supplementalBrief !== baseline.supplementalBrief

  return detailsChanged ? 'details-dirty' : 'clean'
})

const previewIsStale = computed(() => previewSyncState.value !== 'clean')

const recognitionPrimaryActionLabel = computed(() => {
  if (previewSyncState.value === 'core-dirty') {
    return '重新识别'
  }
  if (previewSyncState.value === 'details-dirty') {
    return '更新建议并下一步'
  }
  return '下一步'
})

const enabledTopics = computed(() =>
  topics.value.filter(item => item.enabled && item.name.trim())
)

const enabledTopicCount = computed(() => enabledTopics.value.length)
const effectiveServiceLimits = computed(() => preview.value?.serviceAccess?.limits || serviceAccess.value?.limits || null)
const enabledModelCount = computed(() =>
  effectiveServiceLimits.value?.enabledModelCount
  ?? null
)
const monitoringModelOptions = computed(() =>
  buildPublicPreviewModelOptions(enabledModelCount.value, [selectedTargetModelProduct.value])
)
const remainingTopicSlots = computed(() => Math.max(MAX_TOPIC_SELECTION - enabledTopicCount.value, 0))
const summaryTopicCountLabel = computed(() => previewIsStale.value ? '待更新' : String(enabledTopicCount.value))
const estimatedProbeCount = computed(() => {
  const targetProbeCount = preview.value?.estimatedRun?.targetProbeCount
  return typeof targetProbeCount === 'number' && targetProbeCount > 0
    ? targetProbeCount
    : Math.max(enabledTopicCount.value, 1)
})
const minSelectableProbeCount = computed(() => 1)
const maxSelectableProbeCount = computed(() => {
  return MAX_TARGET_PROBE_COUNT
})
const creditsPerProbe = computed(() => {
  const configured = effectiveServiceLimits.value?.creditsPerProbe
  return typeof configured === 'number' && configured > 0
    ? configured
    : DEFAULT_CREDITS_PER_PROBE
})
const estimatedCreditCost = computed(() => selectedProbeCount.value * creditsPerProbe.value)
const probeCountOptions = computed<ProbeCountOption[]>(() => {
  const minimum = minSelectableProbeCount.value
  const limit = maxSelectableProbeCount.value
  const recommended = Math.min(Math.max(estimatedProbeCount.value, minimum), limit)
  const rawOptions: ProbeCountOption[] = [
    {
      key: 'lite',
      label: '轻量',
      value: Math.min(Math.max(12, minimum), limit),
      note: '先跑一轮关键问题'
    },
    {
      key: 'standard',
      label: '标准',
      value: recommended,
      note: '建议默认'
    },
    {
      key: 'deep',
      label: '深度',
      value: limit,
      note: '覆盖更充分'
    }
  ]
  const seen = new Set<number>()
  return rawOptions.filter((option) => {
    if (seen.has(option.value)) {
      return false
    }
    seen.add(option.value)
    return true
  })
})
const selectedProbeCount = computed(() => {
  const selected = selectedTargetProbeCount.value
  if (typeof selected === 'number' && selected > 0) {
    return Math.min(Math.max(selected, minSelectableProbeCount.value), maxSelectableProbeCount.value)
  }
  const recommended = probeCountOptions.value.find(option => option.key === 'standard')
  return recommended?.value || estimatedProbeCount.value || 1
})
const selectedModelLabel = computed(() =>
  monitoringModelOptions.value.find(option => option.selected)?.label
  || resolveProductModelLabel(selectedTargetModelProduct.value)
)
const topicPreviewNodes = computed(() => coverageNodes.value.slice(0, 2))
const remainingPreviewTopicCount = computed(() => Math.max(coverageNodes.value.length - topicPreviewNodes.value.length, 0))
const runModeLabel = computed(() =>
  selectedExecuteModeCode.value === 'ONE_TIME' ? '只跑一次' : '每日跑一次'
)
const createActionLabel = computed(() =>
  selectedExecuteModeCode.value === 'ONE_TIME'
    ? `创建并首跑（扣 ${estimatedCreditCost.value} 积分）`
    : '创建并开启每日运行'
)
const previewPendingCopy = computed(() => {
  if (!previewPending.value) {
    return ''
  }
  if (currentStep.value === 1) {
    return '正在联网识别品牌，通常需要 20 - 60 秒。'
  }
  if (currentStep.value === 2) {
    return previewSyncState.value === 'core-dirty'
      ? '正在重新识别品牌，请先等待这一轮完成。'
      : '正在更新识别建议，请先等待这一轮完成。'
  }
  if (currentStep.value === 3) {
    return '正在生成本次执行方案，请先等待这一轮完成。'
  }
  return '正在处理中，请先等待这一轮完成。'
})
const previewPendingButtonLabel = computed(() => {
  if (!previewPending.value) {
    return '下一步，识别品牌'
  }
  return '正在联网识别...'
})
const creditsBalanceLabel = computed(() =>
  creditBalance.value == null ? '待同步' : `${Math.max(0, creditBalance.value)} 积分`
)
const balanceAfterRun = computed(() =>
  creditBalance.value == null ? null : Math.max(0, creditBalance.value - estimatedCreditCost.value)
)
const balanceAfterRunLabel = computed(() =>
  selectedExecuteModeCode.value === 'ONE_TIME' ? '执行后余额' : '首次执行后余额'
)
const hasEnoughCredits = computed(() =>
  creditBalance.value == null ? true : creditBalance.value >= estimatedCreditCost.value
)
const creditGuardCopy = computed(() => {
  if (creditBalance.value == null) {
    return '积分暂未同步，提交时会再次校验。'
  }
  if (!hasEnoughCredits.value) {
    return `当前余额不足，需要 ${estimatedCreditCost.value} 积分。`
  }
  if (selectedExecuteModeCode.value === 'ONE_TIME') {
    return `本次扣减 ${estimatedCreditCost.value} 积分，预计剩余 ${balanceAfterRun.value} 积分。`
  }
  return `每次自动运行约扣 ${estimatedCreditCost.value} 积分，首跑后预计剩余 ${balanceAfterRun.value} 积分。`
})
const receiptCreditHint = computed(() => {
  if (creditBalance.value == null) {
    return '提交创建时会再次校验积分。'
  }
  if (!hasEnoughCredits.value) {
    return `余额不足，至少需要 ${estimatedCreditCost.value} 积分。`
  }
  if (selectedExecuteModeCode.value === 'ONE_TIME') {
    return `本次消耗 ${estimatedCreditCost.value} 积分，执行后约剩 ${balanceAfterRun.value}。`
  }
  return `每次自动运行约消耗 ${estimatedCreditCost.value} 积分，首次后约剩 ${balanceAfterRun.value}。`
})
const pricingRuleLabel = computed(() => `${creditsPerProbe.value} 积分 = 1 条探针`)
const receiptCoverageLabel = computed(() => `${enabledTopicCount.value} 个主题`)
const receiptActionNote = computed(() => {
  if (selectedExecuteModeCode.value === 'ONE_TIME') {
    return '创建后先跑一轮，之后可随时手动再跑。'
  }
  return `首次扣 ${estimatedCreditCost.value} 积分，之后每次自动运行都按当前配置扣减。`
})

const recognizedSummary = computed(() => preview.value?.recognition?.brandSummary?.trim() || '')
const totalCompetitorCount = computed(() => directCompetitorTags.value.length + indirectCompetitorTags.value.length)
const remainingCompetitorSlots = computed(() => Math.max(MAX_TOTAL_COMPETITORS - totalCompetitorCount.value, 0))
const coverageNodes = computed(() =>
  enabledTopics.value.map((topic) => {
    const bucketKey = resolveProblemSpaceBucket(
      topic.name,
      topic.description || topic.name
    )
    return {
      tempId: topic.tempId || '',
      name: topic.name.trim(),
      bucketKey,
      bucketLabel: getProblemSpaceBucketLabel(bucketKey),
      sourceLabel: topic.source === 'manual' ? '自定义' : '系统推荐'
    }
  })
)

const receiptRows = computed(() => [
  { label: '模型', value: selectedModelLabel.value },
  { label: '运行方式', value: selectedExecuteModeCode.value === 'ONE_TIME' ? '只跑一次' : '每日自动运行' },
  { label: '可用积分', value: creditsBalanceLabel.value },
  { label: balanceAfterRunLabel.value, value: balanceAfterRun.value == null ? '待同步' : `${balanceAfterRun.value} 积分` }
])

const canAdvanceFromTopics = computed(() => enabledTopicCount.value > 0)
const canCreate = computed(() =>
  !previewIsStale.value
  && Boolean(preview.value?.previewToken)
  && enabledTopicCount.value > 0
  && selectedProbeCount.value > 0
  && Boolean(selectedTargetModelProduct.value)
  && hasEnoughCredits.value
)

function newId(prefix: string) {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return `${prefix}_${crypto.randomUUID()}`
  }
  return `${prefix}_${Date.now()}_${Math.random().toString(16).slice(2)}`
}

function readPrefillQuery(key: string) {
  const raw = route.query[key]
  if (Array.isArray(raw)) {
    return raw[0] || ''
  }
  return typeof raw === 'string' ? raw : ''
}

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
    return undefined
  }
  return normalized || undefined
}

function normalizeTagText(raw: string) {
  return raw.replace(/\s+/g, ' ').trim()
}

function normalizeCompetitorsTextInput(raw: string) {
  return raw
    .split(/[，,]/)
    .map(item => item.trim())
    .filter(Boolean)
    .join(', ')
}

function resolveCompetitors() {
  const fromTags = [...directCompetitorTags.value, ...indirectCompetitorTags.value]
  if (fromTags.length) {
    return normalizeCompetitorsList(fromTags)
  }

  return normalizeCompetitorsTextInput(form.competitorsText)
    .split(',')
    .map(item => item.trim())
    .filter(Boolean)
}

function buildDraft(): BrandSpaceDraft {
  const competitors = resolveCompetitors()
  return {
    brandName: form.brandName.trim(),
    category: form.category.trim(),
    officialWebsite: sanitizeWebsiteForSubmit(form.officialWebsite),
    competitors: competitors.length ? competitors : undefined,
    supplementalBrief: form.supplementalBrief.trim() || undefined,
    regionCode: 'CN',
    locale: 'zh-CN',
    language: 'zh-CN'
  }
}

function buildCoreDraft(): BrandSpaceDraft {
  return {
    brandName: form.brandName.trim(),
    category: form.category.trim(),
    officialWebsite: sanitizeWebsiteForSubmit(form.officialWebsite),
    regionCode: 'CN',
    locale: 'zh-CN',
    language: 'zh-CN'
  }
}

function normalizeCompetitorsList(input?: string[] | null) {
  return (input || [])
    .map(item => normalizeTagText(item))
    .filter(Boolean)
}

function buildCategoryTags(manualLabels: string[], suggestedCategory?: string | null) {
  const tags: CategoryTag[] = []
  const seen = new Set<string>()
  const recommended = normalizeTagText(suggestedCategory || '')

  if (recommended) {
    seen.add(recommended)
    tags.push({
      id: `category_recommended_${recommended}`,
      label: recommended,
      source: 'recommended'
    })
  }

  for (const item of manualLabels) {
    const normalized = normalizeTagText(item)
    if (!normalized || seen.has(normalized)) {
      continue
    }
    seen.add(normalized)
    tags.push({
      id: `category_manual_${normalized}`,
      label: normalized,
      source: 'manual'
    })
  }

  return tags
}

function collectManualCategoryLabels() {
  return categoryTags.value
    .filter(tag => tag.source === 'manual')
    .map(tag => tag.label)
}

function setCategoryTags(nextTags: CategoryTag[], preferredLabel?: string | null) {
  categoryTags.value = nextTags
  const preferred = normalizeTagText(preferredLabel || '')
  const selected = nextTags.find(tag => tag.label === preferred) || nextTags[0]
  form.category = selected?.label || ''
}

function splitCompetitors(flatCompetitors: string[]) {
  const normalized = normalizeCompetitorsList(flatCompetitors).slice(0, MAX_TOTAL_COMPETITORS)
  return {
    direct: normalized.slice(0, MAX_DIRECT_COMPETITORS),
    indirect: normalized.slice(MAX_DIRECT_COMPETITORS, MAX_TOTAL_COMPETITORS)
  }
}

function syncCompetitorsTextFromTags() {
  form.competitorsText = normalizeCompetitorsTextInput(
    [...directCompetitorTags.value, ...indirectCompetitorTags.value].join(', ')
  )
}

function setCompetitorTagGroups(direct: string[], indirect: string[]) {
  const nextDirect = normalizeCompetitorsList(direct).slice(0, MAX_DIRECT_COMPETITORS)
  const occupied = new Set(nextDirect)
  const nextIndirect = normalizeCompetitorsList(indirect)
    .filter(item => !occupied.has(item))
    .slice(0, Math.min(MAX_INDIRECT_COMPETITORS, MAX_TOTAL_COMPETITORS - nextDirect.length))

  directCompetitorTags.value = nextDirect
  indirectCompetitorTags.value = nextIndirect
  syncCompetitorsTextFromTags()
}

function buildDraftSnapshot(input?: Partial<BrandSpaceDraft> | null): DraftSnapshot {
  return {
    brandName: input?.brandName?.trim() || '',
    category: input?.category?.trim() || '',
    officialWebsite: sanitizeWebsiteForSubmit(input?.officialWebsite || '') || '',
    competitorsText: normalizeCompetitorsList(input?.competitors).join(', '),
    supplementalBrief: input?.supplementalBrief?.trim() || ''
  }
}

function buildRecognitionCachePayload(input?: BrandSpaceRecognition | null) {
  if (!input) {
    return null
  }

  return {
    suggestedCategory: input.suggestedCategory?.trim() || '',
    officialWebsite: sanitizeWebsiteForSubmit(input.officialWebsite || '') || '',
    directCompetitors: normalizeCompetitorsList(input.directCompetitors),
    indirectCompetitors: normalizeCompetitorsList(input.indirectCompetitors),
    competitors: normalizeCompetitorsList(input.competitors),
    brandSummary: input.brandSummary?.trim() || ''
  }
}

function buildTopicCachePayload(input?: BrandSpaceTopic[] | null) {
  return (input || []).map(item => ({
    name: item.name.trim(),
    description: item.description?.trim() || '',
    enabled: item.enabled !== false,
    source: item.source || ''
  }))
}

function buildPreviewCacheKey(draft: BrandSpaceDraft, options: PreviewRequestOptions = {}) {
  return JSON.stringify({
    draft: buildDraftSnapshot(draft),
    recognition: buildRecognitionCachePayload(options.recognition),
    topics: buildTopicCachePayload(options.topics)
  })
}

function isDeterministicRecognitionFallback(result: BrandSpacePreview) {
  return result.recognition?.source === 'deterministic_fallback'
}

function isInitialRunBlocked(result: BrandSpaceCreateResponse) {
  return result.initialRunStatus === 'blocked'
}

function resolveCreatedBrandNextView(result: BrandSpaceCreateResponse) {
  if (result.nextView?.startsWith('/brands?created=')) {
    return '/brands'
  }
  if (isInitialRunBlocked(result)) {
    return result.nextView || '/brands'
  }
  if (result.status === 'active' && result.brandSpaceId) {
    return `/dashboard?brandSpaceId=${encodeURIComponent(result.brandSpaceId)}`
  }
  return result.nextView || '/brands'
}

function normalizePreviewTopics(input: BrandSpaceTopic[]) {
  return input.map((item, index) => ({
    ...item,
    tempId: item.tempId || item.id || `topic_tmp_${index + 1}`,
    name: item.name || '',
    description: item.description || '',
    enabled: item.enabled !== false
  }))
}

function scrollWorkbenchToTop() {
  workbenchRef.value?.scrollTo({
    top: 0,
    behavior: 'smooth'
  })
}

async function applyPreviewResult(result: BrandSpacePreview, nextStep: WizardStepId) {
  previewHydrating.value = true
  try {
    preview.value = result
    topics.value = normalizePreviewTopics(result.topics || [])
    customTopicName.value = ''
    customCategoryName.value = ''
    directCompetitorInput.value = ''
    indirectCompetitorInput.value = ''

    const currentCategory = normalizeTagText(form.category)
    const manualCategoryLabels = [currentCategory, ...collectManualCategoryLabels()]
    const resolvedSuggestedCategory = normalizeTagText(result.recognition?.suggestedCategory || '')
    const nextCategoryTags = buildCategoryTags(
      manualCategoryLabels,
      resolvedSuggestedCategory
    )
    setCategoryTags(
      nextCategoryTags,
      resolvedSuggestedCategory || currentCategory || result.brandSpaceDraft?.category || ''
    )

    const directCompetitors = normalizeCompetitorsList(result.recognition?.directCompetitors)
    const indirectCompetitors = normalizeCompetitorsList(result.recognition?.indirectCompetitors)
    const fallbackCompetitors = normalizeCompetitorsList(
      result.recognition?.competitors?.length
        ? result.recognition.competitors
        : result.brandSpaceDraft?.competitors || []
    )
    const resolvedCompetitorGroups = directCompetitors.length || indirectCompetitors.length
      ? {
          direct: directCompetitors,
          indirect: indirectCompetitors
        }
      : splitCompetitors(fallbackCompetitors)

    const joinedCompetitors = normalizeCompetitorsTextInput([
      ...resolvedCompetitorGroups.direct,
      ...resolvedCompetitorGroups.indirect
    ].join(', '))

    if (joinedCompetitors && (!form.competitorsText.trim() || normalizeCompetitorsTextInput(form.competitorsText) === lastAutoFilledCompetitorsText.value)) {
      setCompetitorTagGroups(resolvedCompetitorGroups.direct, resolvedCompetitorGroups.indirect)
      lastAutoFilledCompetitorsText.value = joinedCompetitors
    }

    const resolvedSummary = result.recognition?.brandSummary?.trim() || ''
    if (resolvedSummary && (!form.supplementalBrief.trim() || form.supplementalBrief.trim() === lastAutoFilledSupplementalBrief.value)) {
      form.supplementalBrief = resolvedSummary
      lastAutoFilledSupplementalBrief.value = resolvedSummary
    }

    lastPreviewSnapshot.value = buildDraftSnapshot(buildDraft())
    currentStep.value = nextStep
    scrollWorkbenchToTop()
    await nextTick()
  } finally {
    previewHydrating.value = false
  }
}

async function requestPreview(nextStep: WizardStepId, options: PreviewRequestOptions = {}) {
  if (isServiceBlocked.value) {
    errorMessage.value = '当前账号未开通服务'
    return false
  }

  const draft = options.draft || buildDraft()
  if (!draft.brandName) {
    errorMessage.value = '请输入品牌名'
    return false
  }
  if (!draft.category) {
    errorMessage.value = '请输入品类'
    return false
  }

  errorMessage.value = ''
  recognitionNotice.value = ''
  previewPending.value = true

  try {
    const cacheKey = buildPreviewCacheKey(draft, options)
    const cachedResult = options.bypassCache ? null : previewCache.get(cacheKey)
    if (cachedResult) {
      await applyPreviewResult(cachedResult, nextStep)
      return true
    }

    const result = await brandSpacesApi.preview({
      ...draft,
      recognition: options.recognition,
      topics: options.topics,
      idempotencyKey: newId('brand_preview')
    })

    if (isDeterministicRecognitionFallback(result)) {
      recognitionNotice.value = '当前使用基础识别结果，可继续创建；真实 LLM 识别暂未接通。'
      await applyPreviewResult(result, nextStep)
      return true
    }

    previewCache.set(cacheKey, result)
    await applyPreviewResult(result, nextStep)
    return true
  } catch (error) {
    errorMessage.value = error instanceof Error && error.message ? error.message : '品牌识别失败'
    return false
  } finally {
    previewPending.value = false
  }
}

async function handleIdentifyBrandClick() {
  await requestPreview(2, {
    draft: buildCoreDraft(),
    bypassCache: true
  })
}

function handleWebsiteBlur() {
  form.officialWebsite = normalizeWebsiteInput(form.officialWebsite)
}

function selectCategoryTag(tagId: string) {
  const target = categoryTags.value.find(tag => tag.id === tagId)
  if (!target) {
    return
  }
  form.category = target.label
  errorMessage.value = ''
}

function addCustomCategoryTag() {
  const nextLabel = normalizeTagText(customCategoryName.value)
  if (!nextLabel) {
    errorMessage.value = '请输入品类'
    return
  }
  const nextTags = buildCategoryTags(
    [...collectManualCategoryLabels(), nextLabel],
    categoryTags.value.find(tag => tag.source === 'recommended')?.label || ''
  )
  setCategoryTags(nextTags, nextLabel)
  customCategoryName.value = ''
  errorMessage.value = ''
}

function removeCategoryTag(tagId: string) {
  const target = categoryTags.value.find(tag => tag.id === tagId)
  if (!target) {
    return
  }
  const nextTags = categoryTags.value.filter(tag => tag.id !== tagId)
  if (!nextTags.length) {
    errorMessage.value = '请至少保留一个品类'
    return
  }
  setCategoryTags(nextTags, form.category === target.label ? nextTags[0]?.label || '' : form.category)
  errorMessage.value = ''
}

function addCompetitorTag(group: CompetitorGroup) {
  const inputRef = group === 'direct' ? directCompetitorInput : indirectCompetitorInput
  const nextLabel = normalizeTagText(inputRef.value)
  if (!nextLabel) {
    errorMessage.value = '请输入竞品名'
    return
  }

  const nextDirect = [...directCompetitorTags.value]
  const nextIndirect = [...indirectCompetitorTags.value]
  const targetGroup = group === 'direct' ? nextDirect : nextIndirect
  const otherGroup = group === 'direct' ? nextIndirect : nextDirect
  const groupLimit = group === 'direct' ? MAX_DIRECT_COMPETITORS : MAX_INDIRECT_COMPETITORS

  const otherIndex = otherGroup.findIndex(item => item === nextLabel)
  if (otherIndex >= 0) {
    otherGroup.splice(otherIndex, 1)
  }
  if (targetGroup.includes(nextLabel)) {
    inputRef.value = ''
    errorMessage.value = ''
    return
  }
  if (targetGroup.length >= groupLimit) {
    errorMessage.value = group === 'direct' ? '直接竞品最多 5 个' : '间接竞品最多 5 个'
    return
  }
  if (nextDirect.length + nextIndirect.length >= MAX_TOTAL_COMPETITORS) {
    errorMessage.value = '参考竞品最多 10 个'
    return
  }

  targetGroup.push(nextLabel)
  setCompetitorTagGroups(nextDirect, nextIndirect)
  inputRef.value = ''
  errorMessage.value = ''
}

function removeCompetitorTag(group: CompetitorGroup, label: string) {
  if (group === 'direct') {
    setCompetitorTagGroups(
      directCompetitorTags.value.filter(item => item !== label),
      indirectCompetitorTags.value
    )
  } else {
    setCompetitorTagGroups(
      directCompetitorTags.value,
      indirectCompetitorTags.value.filter(item => item !== label)
    )
  }
  errorMessage.value = ''
}

function goToPreviousStep() {
  if (previewPending.value || createPending.value) {
    return
  }
  currentStep.value = Math.max(1, currentStep.value - 1) as WizardStepId
  scrollWorkbenchToTop()
}

function selectModel(modelKey: string, selectable: boolean) {
  if (!selectable) {
    return
  }
  selectedTargetModelProduct.value = modelKey
  errorMessage.value = ''
}

function selectProbeCount(value: number) {
  selectedTargetProbeCount.value = Math.min(Math.max(value, minSelectableProbeCount.value), maxSelectableProbeCount.value)
  errorMessage.value = ''
}

function selectExecuteMode(mode: ExecuteModeCode) {
  selectedExecuteModeCode.value = mode
  errorMessage.value = ''
}

function goToCompletedStep(stepId: WizardStepId) {
  if (stepId >= currentStep.value || previewPending.value || createPending.value) {
    return
  }
  currentStep.value = stepId
  scrollWorkbenchToTop()
}

async function handleAdvanceFromRecognition() {
  if (previewSyncState.value === 'core-dirty') {
    await requestPreview(2, {
      draft: buildCoreDraft(),
      bypassCache: true
    })
    return
  }

  if (previewSyncState.value === 'details-dirty') {
    await requestPreview(3, {
      recognition: preview.value?.recognition || null
    })
    return
  }

  currentStep.value = 3
  scrollWorkbenchToTop()
}

async function handleAdvanceFromTopics() {
  if (!canAdvanceFromTopics.value) {
    errorMessage.value = '请至少保留一个主题'
    return
  }

  await requestPreview(4, {
    recognition: preview.value?.recognition || null,
    topics: topics.value
  })
}

function handleTopicToggle(topic: BrandSpaceTopic) {
  if (!topic.enabled && enabledTopicCount.value >= MAX_TOPIC_SELECTION) {
    errorMessage.value = `最多保留 ${MAX_TOPIC_SELECTION} 个主题`
    return
  }
  topic.enabled = !topic.enabled
  errorMessage.value = ''
}

function addCustomTopic() {
  const name = customTopicName.value.trim()
  if (!name) {
    errorMessage.value = '请输入自定义主题'
    return
  }
  if (enabledTopicCount.value >= MAX_TOPIC_SELECTION) {
    errorMessage.value = `最多保留 ${MAX_TOPIC_SELECTION} 个主题`
    return
  }
  if (topics.value.some(item => item.name.trim() === name)) {
    errorMessage.value = '这个主题已经存在'
    return
  }

  const topicTempId = newId('topic_tmp')
  const topic: BrandSpaceTopic = {
    tempId: topicTempId,
    name,
    description: '',
    enabled: true,
    source: 'manual'
  }

  topics.value.push(topic)
  customTopicName.value = ''
  errorMessage.value = ''
}

function removeCustomTopic(topicTempId?: string) {
  if (!topicTempId) {
    return
  }

  topics.value = topics.value.filter(item => item.tempId !== topicTempId)
}

async function createBrandSpace() {
  if (isServiceBlocked.value) {
    errorMessage.value = '当前账号未开通服务'
    return
  }
  if (!hasEnoughCredits.value) {
    errorMessage.value = `当前可用积分不足，本次至少需要 ${estimatedCreditCost.value} 积分`
    return
  }
  if (!preview.value?.previewToken) {
    errorMessage.value = '请先完成品牌识别'
    return
  }

  if (!canCreate.value) {
    errorMessage.value = '请至少保留一个可运行的主题'
    return
  }

  errorMessage.value = ''
  createPending.value = true

  try {
    const draft = buildDraft()
    const result = await brandSpacesApi.create({
      previewToken: preview.value.previewToken,
      brandSpaceDraft: draft,
      topics: topics.value,
      targetProbeCount: selectedProbeCount.value,
      targetModelProduct: selectedTargetModelProduct.value,
      executeModeCode: selectedExecuteModeCode.value,
      idempotencyKey: newId('brand_create')
    })
    await navigateTo(resolveCreatedBrandNextView(result))
  } catch (error) {
    errorMessage.value = error instanceof Error && error.message ? error.message : '品牌创建失败'
  } finally {
    createPending.value = false
  }
}

async function backToBrands() {
  await navigateTo('/brands')
}

async function loadServiceAccess() {
  serviceAccessError.value = ''
  try {
    serviceAccess.value = await serviceAccessApi.get()
  } catch (error) {
    serviceAccessError.value = error instanceof Error && error.message ? error.message : '服务状态暂时不可用'
  }
}

async function loadCreditBalance() {
  creditBalanceError.value = ''
  try {
    const response = await creditsApi.getBalance()
    creditBalance.value = typeof response?.creditsRemaining === 'number'
      ? Math.max(0, response.creditsRemaining)
      : 0
  } catch (error) {
    creditBalance.value = null
    creditBalanceError.value = error instanceof Error && error.message ? error.message : '积分余额暂时不可用'
  }
}

function onWorkbenchScroll(event: Event) {
  const target = event.target as HTMLElement | null
  syncWorkbenchScrolled(target)
}

watch(
  () => [form.brandName, form.category],
  () => {
    errorMessage.value = ''
    if (previewHydrating.value) {
      return
    }
    if (!preview.value) {
      return
    }

    if (normalizeCompetitorsTextInput(form.competitorsText) === lastAutoFilledCompetitorsText.value) {
      setCompetitorTagGroups([], [])
    }

    if (form.supplementalBrief.trim() === lastAutoFilledSupplementalBrief.value) {
      form.supplementalBrief = ''
    }

    lastAutoFilledCompetitorsText.value = ''
    lastAutoFilledSupplementalBrief.value = ''
  }
)

watch(
  () => form.competitorsText,
  (value) => {
    const normalized = normalizeCompetitorsTextInput(value)
    if (normalized && normalized !== lastAutoFilledCompetitorsText.value) {
      lastAutoFilledCompetitorsText.value = ''
    }
  }
)

watch(
  () => form.supplementalBrief,
  (value) => {
    const normalized = value.trim()
    if (normalized && normalized !== lastAutoFilledSupplementalBrief.value) {
      lastAutoFilledSupplementalBrief.value = ''
    }
  }
)

onMounted(() => {
  resetWorkbenchScrolled()
  void loadServiceAccess()
  void loadCreditBalance()
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
            <h1>添加品牌</h1>
          </div>
          <div class="head-actions">
            <button
              type="button"
              class="btn-plain"
              @click="backToBrands"
            >
              返回品牌管理
            </button>
          </div>
        </header>
      </div>

      <section
        v-if="errorMessage"
        class="state-row state-row--error"
      >
        {{ errorMessage }}
      </section>
      <section
        v-else-if="recognitionNotice"
        class="state-row state-row--muted"
      >
        {{ recognitionNotice }}
      </section>
      <section
        v-else-if="previewPendingCopy"
        class="state-row state-row--pending"
        aria-live="polite"
      >
        <span class="state-row__dot" />
        {{ previewPendingCopy }}
      </section>
      <section
        v-else-if="isServiceBlocked"
        class="state-row state-row--error"
      >
        当前账号未开通服务
      </section>
      <section
        v-else-if="serviceAccessError"
        class="state-row state-row--muted"
      >
        {{ serviceAccessError }}
      </section>
      <section
        v-else-if="creditBalanceError"
        class="state-row state-row--muted"
      >
        {{ creditBalanceError }}
      </section>

      <div class="wizard-grid">
        <section class="surface section-surface builder-surface wizard-main">
          <div class="surface-head wizard-head">
            <div class="surface-head__title">
              <strong>{{ currentStepMeta.label }}</strong>
              <span>{{ currentStepMeta.hint }}</span>
            </div>
            <span class="wizard-step-badge">第 {{ currentStep }} / {{ wizardSteps.length }} 步</span>
          </div>

          <ol
            class="wizard-stepper"
            aria-label="添加品牌步骤"
          >
            <li
              v-for="step in wizardSteps"
              :key="step.id"
              class="wizard-stepper__item"
              :class="{
                'is-current': step.id === currentStep,
                'is-done': step.id < currentStep
              }"
            >
              <button
                type="button"
                class="wizard-stepper__button"
                @click="goToCompletedStep(step.id)"
              >
                <span class="wizard-stepper__index">{{ step.id }}</span>
                <span class="wizard-stepper__copy">{{ step.label }}</span>
              </button>
            </li>
          </ol>

          <div
            v-if="currentStep === 1"
            class="step-panel"
          >
            <div class="form-grid form-grid--compact">
              <label class="field">
                <span>品牌名</span>
                <input
                  v-model="form.brandName"
                  type="text"
                  :disabled="previewPending || createPending"
                  maxlength="80"
                  placeholder="比如：卫龙"
                >
              </label>

              <label class="field">
                <span>品类</span>
                <input
                  v-model="form.category"
                  type="text"
                  :disabled="previewPending || createPending"
                  maxlength="80"
                  placeholder="比如：辣条"
                >
              </label>
            </div>

            <section
              v-if="previewPending"
              class="pending-panel"
              aria-live="polite"
            >
              <div class="pending-panel__head">
                <span class="pending-panel__eyebrow">当前动作</span>
                <strong>正在识别品牌</strong>
              </div>
              <p>系统会联网确认品牌、品类和竞品，请不要重复点击或离开当前页面。</p>
            </section>

            <div class="actions actions--end">
              <button
                type="button"
                class="cta-primary"
                :disabled="previewPending || createPending || isServiceBlocked"
                @click="handleIdentifyBrandClick"
              >
                {{ previewPendingButtonLabel }}
              </button>
            </div>
          </div>

          <div
            v-else-if="currentStep === 2"
            class="step-panel"
          >
            <div class="form-grid form-grid--recognition">
              <label class="field">
                <span>品牌</span>
                <input
                  v-model="form.brandName"
                  type="text"
                  maxlength="80"
                  placeholder="比如：卫龙"
                >
              </label>

              <label class="field">
                <div class="field-head">
                  <span>品类</span>
                </div>
                <div class="tag-editor">
                  <div class="tag-editor__frame">
                    <div class="tag-cloud tag-cloud--compact">
                      <article
                        v-for="tag in categoryTags"
                        :key="tag.id"
                        class="selection-tag"
                        :class="{
                          'selection-tag--active': tag.label === form.category,
                          'selection-tag--recommended': tag.source === 'recommended',
                          'selection-tag--manual': tag.source === 'manual'
                        }"
                      >
                        <button
                          type="button"
                          class="selection-tag__main"
                          @click="selectCategoryTag(tag.id)"
                        >
                          <span
                            v-if="tag.source === 'recommended'"
                            class="selection-tag__badge"
                          >推荐</span>
                          <span>{{ tag.label }}</span>
                        </button>
                        <button
                          type="button"
                          class="selection-tag__remove"
                          :aria-label="`删除品类 ${tag.label}`"
                          @click="removeCategoryTag(tag.id)"
                        >
                          ×
                        </button>
                      </article>
                    </div>
                    <div class="field-inline__control tag-editor__row">
                      <input
                        v-model="customCategoryName"
                        type="text"
                        maxlength="80"
                        placeholder="补充品类"
                      >
                      <button
                        type="button"
                        class="btn-plain btn-plain--compact"
                        @click="addCustomCategoryTag"
                      >添加</button>
                    </div>
                  </div>
                </div>
              </label>

              <label class="field">
                <span>官网</span>
                <input
                  v-model="form.officialWebsite"
                  type="url"
                  maxlength="200"
                  placeholder="填写官网地址"
                  @blur="handleWebsiteBlur"
                >
              </label>

              <label class="field">
                <span>参考竞品</span>
                <div class="competitor-editor">
                  <div class="competitor-editor__head">
                    <span>已识别 {{ totalCompetitorCount }}/{{ MAX_TOTAL_COMPETITORS }}</span>
                    <span>还能补充 {{ remainingCompetitorSlots }}</span>
                  </div>
                  <div class="competitor-editor__grid">
                    <section class="competitor-group">
                      <div class="competitor-group__head">
                        <strong>直接竞品</strong>
                        <span>{{ directCompetitorTags.length }}/{{ MAX_DIRECT_COMPETITORS }}</span>
                      </div>
                      <div class="competitor-group__panel">
                        <div class="tag-cloud tag-cloud--compact">
                          <article
                            v-for="item in directCompetitorTags"
                            :key="`direct_${item}`"
                            class="selection-tag selection-tag--plain"
                          >
                            <span class="selection-tag__label">{{ item }}</span>
                            <button
                              type="button"
                              class="selection-tag__remove"
                              :aria-label="`删除直接竞品 ${item}`"
                              @click="removeCompetitorTag('direct', item)"
                            >
                              ×
                            </button>
                          </article>
                        </div>
                        <div class="field-inline__control tag-editor__row">
                          <input
                            v-model="directCompetitorInput"
                            type="text"
                            maxlength="80"
                            placeholder="添加直接竞品"
                          >
                          <button
                            type="button"
                            class="btn-plain btn-plain--compact"
                            @click="addCompetitorTag('direct')"
                          >添加</button>
                        </div>
                      </div>
                    </section>

                    <section class="competitor-group">
                      <div class="competitor-group__head">
                        <strong>间接竞品</strong>
                        <span>{{ indirectCompetitorTags.length }}/{{ MAX_INDIRECT_COMPETITORS }}</span>
                      </div>
                      <div class="competitor-group__panel">
                        <div class="tag-cloud tag-cloud--compact">
                          <article
                            v-for="item in indirectCompetitorTags"
                            :key="`indirect_${item}`"
                            class="selection-tag selection-tag--plain"
                          >
                            <span class="selection-tag__label">{{ item }}</span>
                            <button
                              type="button"
                              class="selection-tag__remove"
                              :aria-label="`删除间接竞品 ${item}`"
                              @click="removeCompetitorTag('indirect', item)"
                            >
                              ×
                            </button>
                          </article>
                        </div>
                        <div class="field-inline__control tag-editor__row">
                          <input
                            v-model="indirectCompetitorInput"
                            type="text"
                            maxlength="80"
                            placeholder="添加间接竞品"
                          >
                          <button
                            type="button"
                            class="btn-plain btn-plain--compact"
                            @click="addCompetitorTag('indirect')"
                          >添加</button>
                        </div>
                      </div>
                    </section>
                  </div>
                </div>
              </label>
            </div>

            <section class="result-block">
              <div class="result-block__head">
                <strong>品牌概况</strong>
                <span>最多 500 字</span>
              </div>
              <label class="field field--full">
                <textarea
                  v-model="form.supplementalBrief"
                  rows="6"
                  maxlength="500"
                  :placeholder="recognizedSummary || '补充品牌定位、主打产品或重点关注点'"
                />
              </label>
            </section>

            <div class="actions actions--split">
              <button
                type="button"
                class="btn-plain"
                :disabled="previewPending || createPending"
                @click="goToPreviousStep"
              >
                上一步
              </button>
              <button
                type="button"
                class="cta-primary"
                :disabled="previewPending || createPending"
                @click="handleAdvanceFromRecognition"
              >
                {{ previewPending ? (previewSyncState === 'core-dirty' ? '重新识别中...' : '更新中...') : recognitionPrimaryActionLabel }}
              </button>
            </div>
          </div>

          <div
            v-else-if="currentStep === 3"
            class="step-panel"
          >
            <section class="topic-panel">
              <div class="topic-panel__head">
                <strong>推荐主题</strong>
                <span>保留你准备长期跟踪的重点主题</span>
              </div>

              <div class="topic-grid">
                <article
                  v-for="topic in topics"
                  :key="topic.tempId"
                  class="topic-card"
                  :class="{ 'topic-card--active': topic.enabled }"
                >
                  <button
                    type="button"
                    class="topic-card__toggle"
                    @click="handleTopicToggle(topic)"
                  >
                    <span class="topic-card__check">{{ topic.enabled ? '已选' : '未选' }}</span>
                    <strong>{{ topic.name }}</strong>
                  </button>
                  <p
                    v-if="topic.description"
                    class="topic-card__description"
                  >
                    {{ topic.description }}
                  </p>
                  <button
                    v-if="topic.source === 'manual'"
                    type="button"
                    class="inline-mini"
                    @click="removeCustomTopic(topic.tempId)"
                  >
                    删除自定义
                  </button>
                </article>
              </div>
            </section>

            <section class="custom-topic-row">
              <label class="field field--inline">
                <span>添加自定义主题</span>
                <div class="field-inline__control">
                  <input
                    v-model="customTopicName"
                    type="text"
                    maxlength="80"
                    placeholder="比如：渠道评价"
                  >
                  <button
                    type="button"
                    class="btn-plain"
                    @click="addCustomTopic"
                  >添加</button>
                </div>
              </label>
              <p class="inline-note">
                最多保留 {{ MAX_TOPIC_SELECTION }} 个主题，当前还能添加 {{ remainingTopicSlots }} 个。
              </p>
            </section>

            <div class="actions actions--split">
              <button
                type="button"
                class="btn-plain"
                :disabled="previewPending || createPending"
                @click="goToPreviousStep"
              >
                上一步
              </button>
              <button
                type="button"
                class="cta-primary"
                :disabled="previewPending || createPending"
                @click="handleAdvanceFromTopics"
              >
                继续，确认首跑
              </button>
            </div>
          </div>

          <div
            v-else-if="currentStep === 4"
            class="step-panel step-panel--checkout"
          >
            <div class="run-setup-grid run-setup-grid--checkout">
              <section class="run-choice-section">
                <div class="run-choice-section__head">
                  <strong>模型</strong>
                  <span>当前开放 3 个入口，仅豆包可选</span>
                </div>

                <div class="model-choice-list">
                  <button
                    v-for="model in monitoringModelOptions"
                    :key="model.key"
                    type="button"
                    class="model-choice-card"
                    :class="{
                      'is-selected': model.selected,
                      'is-disabled': !model.selectable
                    }"
                    :disabled="!model.selectable"
                    @click="selectModel(model.key, model.selectable)"
                  >
                    <div>
                      <strong>{{ model.label }}</strong>
                      <span>{{ model.description }}</span>
                    </div>
                    <b>{{ model.statusLabel }}</b>
                  </button>
                </div>
              </section>

              <section class="run-choice-section">
                <div class="run-choice-section__head">
                  <strong>探针数</strong>
                  <span>支持 1 到 {{ maxSelectableProbeCount }} 条</span>
                </div>

                <div class="probe-count-grid">
                  <button
                    v-for="option in probeCountOptions"
                    :key="option.key"
                    type="button"
                    class="probe-count-card"
                    :class="{ 'is-selected': option.value === selectedProbeCount }"
                    @click="selectProbeCount(option.value)"
                  >
                    <span>{{ option.label }}</span>
                    <strong>{{ option.value }}</strong>
                    <em>{{ option.note }}</em>
                  </button>
                </div>

                <div class="probe-range-control">
                  <input
                    :value="selectedProbeCount"
                    type="range"
                    :min="minSelectableProbeCount"
                    :max="maxSelectableProbeCount"
                    step="1"
                    @input="selectProbeCount(Number(($event.target as HTMLInputElement).value))"
                  >
                  <label>
                    <span>精确探针数</span>
                    <input
                      :value="selectedProbeCount"
                      type="number"
                      inputmode="numeric"
                      :min="minSelectableProbeCount"
                      :max="maxSelectableProbeCount"
                      @input="selectProbeCount(Number(($event.target as HTMLInputElement).value))"
                    >
                  </label>
                </div>

                <div class="probe-cost-line">
                  <span>{{ selectedProbeCount }} 条探针</span>
                  <strong>{{ estimatedCreditCost }} 积分</strong>
                </div>
                <p class="probe-cost-meta">
                  {{ creditGuardCopy }}
                </p>
              </section>

              <section class="run-choice-section run-choice-section--mode">
                <div class="run-choice-section__head">
                  <strong>运行方式</strong>
                  <span>{{ selectedExecuteModeCode === 'ONE_TIME' ? '创建后先跑一轮' : '创建后保持每日运行' }}</span>
                </div>

                <div class="run-mode-grid">
                  <button
                    type="button"
                    class="run-mode-card"
                    :class="{ 'is-selected': selectedExecuteModeCode === 'ONE_TIME' }"
                    @click="selectExecuteMode('ONE_TIME')"
                  >
                    <strong>单次</strong>
                    <span>只跑这一次，后续手动启动。</span>
                  </button>
                  <button
                    type="button"
                    class="run-mode-card"
                    :class="{ 'is-selected': selectedExecuteModeCode === 'RECURRING' }"
                    @click="selectExecuteMode('RECURRING')"
                  >
                    <strong>每日</strong>
                    <span>创建后首跑，并继续按当前配置自动运行。</span>
                  </button>
                </div>
              </section>
            </div>
          </div>
        </section>

        <aside class="wizard-side">
          <section
            v-if="currentStep >= 4"
            class="surface section-surface receipt-surface"
          >
            <div class="receipt-brand">
              <span>品牌空间</span>
              <strong>{{ form.brandName || '未填写品牌' }}</strong>
              <em>{{ form.category || '品类待确认' }}</em>
            </div>

            <div class="receipt-coverage">
              <span>本次覆盖</span>
              <strong>{{ receiptCoverageLabel }}</strong>
              <div class="receipt-topic-chips">
                <span
                  v-for="node in topicPreviewNodes"
                  :key="node.tempId"
                >
                  {{ node.name }}
                </span>
                <span v-if="remainingPreviewTopicCount > 0">
                  +{{ remainingPreviewTopicCount }}
                </span>
              </div>
            </div>

            <div class="receipt-total">
              <span>{{ selectedExecuteModeCode === 'ONE_TIME' ? '本次结算' : '首次结算' }}</span>
              <div class="receipt-total__value">
                <strong>{{ estimatedCreditCost }}</strong>
                <small>积分</small>
              </div>
              <div class="receipt-credit-line">
                {{ selectedProbeCount }} 条探针 · {{ runModeLabel }}
              </div>
              <div class="receipt-credit-hint">
                {{ receiptCreditHint }}
              </div>
            </div>

            <div class="receipt-pricing">
              <span>计费口径</span>
              <strong>{{ pricingRuleLabel }}</strong>
            </div>

            <div class="receipt-list">
              <div
                v-for="row in receiptRows"
                :key="row.label"
              >
                <span>{{ row.label }}</span>
                <strong>{{ row.value }}</strong>
              </div>
            </div>

            <div class="receipt-actions">
              <p class="receipt-action-note">
                {{ receiptActionNote }}
              </p>
              <button
                type="button"
                class="cta-primary receipt-primary"
                :disabled="createPending || !canCreate || isServiceBlocked"
                @click="createBrandSpace"
              >
                {{ createPending ? '创建中...' : createActionLabel }}
              </button>
              <button
                type="button"
                class="btn-plain receipt-secondary"
                :disabled="previewPending || createPending"
                @click="goToPreviousStep"
              >
                返回调整主题
              </button>
            </div>
          </section>

          <section
            v-else
            class="surface section-surface side-surface"
          >
            <div class="surface-head surface-head--compact">
              <strong>创建进度</strong>
            </div>

            <div class="side-stack">
              <article class="side-stat">
                <span>当前步骤</span>
                <strong>{{ currentStepMeta.label }}</strong>
              </article>
              <article class="side-stat">
                <span>已选主题</span>
                <strong>{{ summaryTopicCountLabel }}</strong>
              </article>
              <article class="side-stat">
                <span>预计探针</span>
                <strong>{{ estimatedProbeCount }}</strong>
              </article>
            </div>
          </section>
        </aside>
      </div>
    </section>
  </main>
</template>

<style scoped src="~/assets/css/engine-shell.css"></style>

<style scoped>
.wizard-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 400px);
}

.wizard-main,
.wizard-side {
  align-content: start;
}

.wizard-head {
  align-items: flex-start;
}

.wizard-step-badge {
  border: 1px solid #d1d5db;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  color: #4b5563;
}

.surface-head {
  min-height: 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.surface-head--compact {
  min-height: 24px;
}

.surface-head__title {
  display: grid;
  gap: 3px;
}

.surface-head strong {
  font-size: 16px;
  line-height: 1.3;
}

.surface-head__title span {
  font-size: 12px;
  color: #6b7280;
}

.builder-surface,
.side-surface {
  display: grid;
  gap: 14px;
  align-content: start;
}

.wizard-stepper {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.wizard-stepper__item {
  list-style: none;
}

.wizard-stepper__button {
  width: 100%;
  min-height: 52px;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  background: #fff;
  display: grid;
  gap: 4px;
  justify-items: start;
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
}

.wizard-stepper__item.is-current .wizard-stepper__button {
  border-color: #111827;
  background: #111827;
  color: #fff;
}

.wizard-stepper__item.is-done .wizard-stepper__button {
  border-color: #cbd5e1;
  background: #f8fafc;
}

.wizard-stepper__index {
  font-size: 11px;
  color: #6b7280;
}

.wizard-stepper__item.is-current .wizard-stepper__index {
  color: rgba(255, 255, 255, 0.7);
}

.wizard-stepper__copy {
  font-size: 13px;
  font-weight: 600;
}

.step-panel,
.result-block,
.topic-panel,
.coverage-panel,
.side-stack {
  display: grid;
  gap: 12px;
}

.form-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.form-grid--compact {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.form-grid--recognition {
  align-items: start;
  grid-template-columns: minmax(0, 1fr);
}

.field {
  display: grid;
  gap: 6px;
}

.state-row--pending {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid rgba(37, 99, 235, 0.16);
  background: linear-gradient(90deg, rgba(239, 246, 255, 0.96) 0%, rgba(248, 250, 252, 0.96) 100%);
  color: #1d4ed8;
}

.state-row__dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: currentColor;
  box-shadow: 0 0 0 0 rgba(37, 99, 235, 0.2);
  animation: pendingPulse 1.6s ease-out infinite;
}

.pending-panel {
  display: grid;
  gap: 8px;
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(239, 246, 255, 0.92) 0%, rgba(255, 255, 255, 0.98) 100%);
  padding: 14px 16px;
}

.pending-panel__head {
  display: grid;
  gap: 4px;
}

.pending-panel__eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #1d4ed8;
}

.pending-panel__head strong {
  font-size: 15px;
  line-height: 1.35;
  color: #0f172a;
}

.pending-panel p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #475569;
}

.field--full {
  grid-column: 1 / -1;
}

.field--inline {
  gap: 10px;
}

.field span {
  font-size: 12px;
  font-weight: 600;
  color: #4b5563;
}

.field-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.field-chip {
  min-height: 20px;
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  background: #fef3c7;
  color: #92400e;
  font-size: 11px;
  font-weight: 700;
  padding: 0 8px;
}

.field input,
.field textarea {
  min-height: 40px;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  background: #fff;
  padding: 0 12px;
  font-size: 13px;
  color: #111827;
}

.field textarea {
  min-height: 120px;
  padding: 10px 12px;
  resize: vertical;
}

.field input:focus,
.field textarea:focus {
  outline: none;
  border-color: #111827;
}

.field-inline__control {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.field-inline__control input {
  flex: 1;
  width: 100%;
  min-width: 0;
}

.tag-editor,
.competitor-editor,
.competitor-group {
  display: grid;
  gap: 10px;
}

.tag-editor__frame,
.competitor-group__panel {
  display: grid;
  gap: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
  padding: 12px;
}

.tag-editor__row {
  align-items: center;
}

.tag-editor__row input,
.competitor-editor input {
  min-height: 38px;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  background: #fff;
  padding: 0 12px;
  font-size: 13px;
  color: #111827;
}

.tag-editor__row input:focus,
.competitor-editor input:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-cloud--compact {
  gap: 10px 8px;
}

.selection-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  border: 1px solid #d9e2f2;
  border-radius: 999px;
  background: #fff;
  padding: 4px 6px 4px 10px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.selection-tag--active {
  border-color: #2563eb;
  background: #eef4ff;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.08);
}

.selection-tag--recommended {
  border-color: #bfdbfe;
  background: #f5f9ff;
}

.selection-tag--manual {
  border-color: #e5e7eb;
  background: #f8fafc;
}

.selection-tag--plain {
  border-color: #e2e8f0;
  background: #f8fafc;
}

.selection-tag__main,
.selection-tag__remove {
  border: 0;
  background: transparent;
  padding: 0;
  cursor: pointer;
}

.selection-tag__main {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.selection-tag--active .selection-tag__main,
.selection-tag--active .selection-tag__remove {
  color: #1d4ed8;
}

.selection-tag__label {
  font-size: 13px;
  color: #0f172a;
}

.selection-tag--manual .selection-tag__main,
.selection-tag--manual .selection-tag__label,
.selection-tag--plain .selection-tag__label {
  color: #475569;
}

.selection-tag__badge {
  min-height: 18px;
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 700;
  padding: 0 7px;
}

.selection-tag__remove {
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: #f8fafc;
  font-size: 14px;
  line-height: 1;
  color: #64748b;
  padding: 0;
}

.selection-tag--active .selection-tag__remove {
  background: #dbeafe;
}

@keyframes pendingPulse {
  0% {
    box-shadow: 0 0 0 0 rgba(37, 99, 235, 0.22);
  }
  70% {
    box-shadow: 0 0 0 10px rgba(37, 99, 235, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(37, 99, 235, 0);
  }
}

.competitor-editor__head,
.competitor-group__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.competitor-editor__head span,
.competitor-group__head strong,
.competitor-group__head span {
  font-size: 12px;
  color: #4b5563;
}

.competitor-group__head strong {
  color: #111827;
}

.competitor-editor__grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
}

.competitor-group {
  min-width: 0;
}

.result-block {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: linear-gradient(180deg, #fcfdff 0%, #ffffff 100%);
  padding: 12px;
}

.btn-plain--compact {
  min-width: 66px;
  justify-content: center;
}

.result-block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.result-block__head strong {
  font-size: 14px;
}

.result-block__head span {
  font-size: 12px;
  color: #6b7280;
}

.result-block__body,
.empty-note {
  font-size: 13px;
  line-height: 1.7;
  color: #4b5563;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chip {
  min-height: 30px;
  display: inline-flex;
  align-items: center;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  background: #fff;
  color: #111827;
  font-size: 12px;
  padding: 0 12px;
}

.chip--muted {
  background: #f8fafc;
  color: #374151;
}

.topic-panel__head,
.coverage-panel__head {
  display: grid;
  gap: 4px;
}

.topic-panel__head strong,
.coverage-panel__head strong {
  font-size: 14px;
}

.topic-panel__head span,
.coverage-panel__head span {
  font-size: 12px;
  color: #6b7280;
}

.topic-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.topic-card {
  position: relative;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
  padding: 12px;
  display: grid;
  gap: 10px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
  transition: border-color 0.16s ease, background 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.topic-card--active {
  border-color: #2563eb;
  background: linear-gradient(135deg, #eff6ff 0%, #ffffff 58%);
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.12), 0 10px 26px rgba(37, 99, 235, 0.08);
}

.topic-card--active::before {
  content: "";
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: linear-gradient(180deg, #1d4ed8, #60a5fa);
}

.topic-card__toggle {
  display: grid;
  gap: 6px;
  justify-items: start;
  text-align: left;
  background: transparent;
  border: 0;
  padding: 0;
  cursor: pointer;
}

.topic-card__check {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  border-radius: 999px;
  background: #f1f5f9;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
}

.topic-card--active .topic-card__check {
  background: #2563eb;
  color: #fff;
}

.topic-card__toggle strong {
  font-size: 14px;
  color: #111827;
}

.topic-card--active .topic-card__toggle strong {
  color: #0f3f96;
}

.topic-card__description {
  font-size: 12px;
  line-height: 1.6;
  color: #4b5563;
}

.inline-mini {
  border: 0;
  background: transparent;
  color: #4b5563;
  font-size: 12px;
  padding: 0;
  cursor: pointer;
}

.inline-mini:hover {
  color: #111827;
}

.custom-topic-row {
  border-top: 1px solid #e5e7eb;
  padding-top: 12px;
}

.checkout-panel {
  display: grid;
  height: 100%;
  gap: 12px;
  grid-template-rows: auto auto minmax(0, 1fr);
  min-height: 0;
}

.step-panel--checkout {
  height: clamp(360px, calc(100vh - 332px), 550px);
  min-height: 0;
  overflow: hidden;
}

.run-setup-panel {
  display: grid;
  height: 100%;
  min-height: 0;
  gap: 12px;
  grid-template-rows: auto minmax(0, 1fr);
  overflow: auto;
  padding-right: 2px;
}

.run-setup-head,
.run-choice-section__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.run-setup-head strong,
.run-choice-section__head strong {
  color: #111827;
  font-size: 16px;
  line-height: 1.35;
}

.run-setup-head span,
.run-choice-section__head span {
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
}

.execution-facts {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.execution-fact,
.run-choice-section {
  position: relative;
  overflow: hidden;
  border: 1px solid #dbe3ee;
  border-radius: 14px;
  background:
    linear-gradient(135deg, rgba(15, 23, 42, 0.04), transparent 38%),
    #ffffff;
}

.execution-fact {
  min-height: 82px;
  display: grid;
  align-content: end;
  gap: 6px;
  padding: 12px;
}

.execution-fact::before,
.run-choice-section::before {
  content: "";
  position: absolute;
  inset: 0 0 auto;
  height: 3px;
  background: linear-gradient(90deg, #0f172a 0%, #2563eb 44%, rgba(37, 99, 235, 0) 100%);
}

.execution-fact span,
.execution-fact em {
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
}

.execution-fact strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 24px;
  line-height: 1;
  letter-spacing: -0.07em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.execution-fact em {
  color: #2563eb;
}

.run-setup-grid {
  display: grid;
  min-height: 0;
  gap: 10px;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.9fr);
  grid-template-rows: auto auto auto;
}

.run-setup-grid--checkout {
  grid-template-columns: minmax(0, 1.14fr) minmax(0, 1fr);
  grid-template-rows: auto auto;
}

.run-setup-grid--checkout .run-choice-section--mode {
  grid-column: 1 / -1;
}

.run-choice-section {
  min-height: 0;
  display: grid;
  gap: 6px;
  grid-template-rows: auto minmax(0, 1fr);
  padding: 9px;
}

.run-choice-section--mode {
  grid-column: 1;
}

.run-choice-section--preview {
  grid-column: 2;
  grid-row: 1 / span 3;
}

.model-choice-list,
.probe-count-grid,
.run-mode-grid,
.run-topic-preview {
  min-height: 0;
}

.model-choice-list {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: start;
}

.model-choice-card,
.probe-count-card,
.run-mode-card {
  border: 1px solid #dbe3ee;
  border-radius: 12px;
  background: #ffffff;
  color: #0f172a;
  text-align: left;
  transition: border-color 160ms ease, background 160ms ease, transform 160ms ease;
}

.model-choice-card:hover,
.probe-count-card:hover,
.run-mode-card:hover {
  border-color: #94a3b8;
  background: #f8fafc;
}

.model-choice-card.is-selected,
.probe-count-card.is-selected,
.run-mode-card.is-selected {
  border-color: #0f172a;
  background:
    linear-gradient(135deg, rgba(37, 99, 235, 0.08), transparent 50%),
    #ffffff;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.08);
}

.model-choice-card.is-disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.model-choice-card.is-disabled b {
  color: #94a3b8;
}

.model-choice-card {
  display: grid;
  align-content: space-between;
  gap: 10px;
  grid-template-columns: minmax(0, 1fr);
  min-height: 104px;
  padding: 10px;
}

.model-choice-card div {
  display: grid;
  gap: 3px;
}

.model-choice-card strong,
.run-topic-row strong,
.run-mode-card strong {
  color: #0f172a;
  font-size: 13px;
  line-height: 1.4;
}

.model-choice-card span,
.run-topic-row span,
.run-mode-card span {
  color: #64748b;
  font-size: 11px;
  line-height: 1.45;
}

.model-choice-card b,
.run-topic-row b {
  color: #2563eb;
  font-size: 12px;
  white-space: nowrap;
}

.probe-count-grid,
.run-mode-grid {
  display: grid;
  gap: 6px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: start;
}

.probe-count-grid {
  position: relative;
  z-index: 1;
}

.run-mode-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.probe-count-card {
  display: grid;
  gap: 5px;
  padding: 9px 10px;
}

.probe-count-card span,
.probe-count-card em {
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
}

.probe-count-card strong {
  color: #0f172a;
  font-size: 22px;
  line-height: 0.95;
  letter-spacing: -0.05em;
}

.probe-count-card em {
  color: #2563eb;
}

.probe-range-control {
  display: grid;
  align-items: center;
  gap: 6px;
  grid-template-columns: minmax(0, 1fr) 96px;
  margin-top: 0;
  position: relative;
  z-index: 0;
}

.probe-range-control > input[type="range"] {
  margin: 0;
  width: 100%;
  accent-color: #0f172a;
}

.probe-range-control label {
  display: grid;
  gap: 5px;
}

.probe-range-control label span {
  color: #64748b;
  font-size: 11px;
  font-weight: 800;
}

.probe-range-control label input {
  width: 100%;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  background: #ffffff;
  color: #0f172a;
  font-size: 13px;
  font-weight: 800;
  padding: 6px 8px;
}

.probe-cost-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid rgba(37, 99, 235, 0.14);
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.08), rgba(255, 255, 255, 0.72));
  color: #475569;
  font-size: 12px;
  font-weight: 800;
  padding: 7px 9px;
}

.probe-cost-line strong {
  color: #0f172a;
  font-size: 16px;
  letter-spacing: -0.04em;
}

.probe-cost-meta {
  color: #64748b;
  font-size: 11px;
  line-height: 1.45;
}

.run-mode-card {
  display: grid;
  gap: 4px;
  min-height: 72px;
  padding: 9px 10px;
}

.run-preview-summary {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.run-preview-stat {
  display: grid;
  gap: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.92);
  padding: 9px 10px;
}

.run-preview-stat span,
.run-preview-more {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.run-preview-stat strong {
  color: #0f172a;
  font-size: 22px;
  line-height: 1;
  letter-spacing: -0.06em;
}

.run-topic-preview {
  display: grid;
  gap: 1px;
  overflow: hidden auto;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #e2e8f0;
}

.run-topic-row {
  display: grid;
  align-items: center;
  gap: 12px;
  grid-template-columns: minmax(0, 1fr) auto;
  background: #ffffff;
  padding: 8px 10px;
}

.run-topic-row div {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.checkout-detail-grid {
  min-height: 0;
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 0.76fr);
}

.checkout-panel__head,
.checkout-section__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.checkout-panel__head strong,
.checkout-section__head strong {
  color: #111827;
  font-size: 16px;
  line-height: 1.35;
}

.checkout-panel__head span,
.checkout-section__head span {
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}

.confirmation-facts {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.confirmation-fact,
.checkout-section,
.receipt-surface {
  position: relative;
  overflow: hidden;
  border: 1px solid #dbe3ee;
  border-radius: 14px;
  background:
    linear-gradient(135deg, rgba(15, 23, 42, 0.04), transparent 38%),
    #ffffff;
}

.confirmation-fact {
  min-height: 88px;
  display: grid;
  align-content: end;
  gap: 6px;
  padding: 12px;
}

.confirmation-fact::before,
.receipt-surface::before {
  content: "";
  position: absolute;
  inset: 0 0 auto;
  height: 3px;
  background: linear-gradient(90deg, #0f172a 0%, #2563eb 44%, rgba(37, 99, 235, 0) 100%);
}

.confirmation-fact span,
.receipt-brand span,
.receipt-total span,
.receipt-list span {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.confirmation-fact strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 24px;
  line-height: 1;
  letter-spacing: -0.07em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.confirmation-fact em,
.receipt-brand em {
  color: #2563eb;
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
}

.checkout-section {
  min-height: 0;
  display: grid;
  gap: 10px;
  grid-template-rows: auto minmax(0, 1fr);
  padding: 12px;
}

.checkout-topic-table,
.prompt-preview-list {
  display: grid;
  gap: 1px;
  overflow: hidden auto;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #e2e8f0;
}

.internal-scroll {
  height: 100%;
  min-height: 0;
  max-height: none;
  overscroll-behavior: contain;
  scrollbar-width: thin;
}

.checkout-topic-row,
.prompt-preview-row,
.prompt-preview-more {
  background: #fff;
  padding: 9px 11px;
}

.checkout-topic-row {
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
}

.checkout-topic-row div,
.prompt-preview-row {
  display: grid;
  gap: 4px;
}

.checkout-topic-row strong,
.prompt-preview-row strong,
.receipt-list strong {
  color: #0f172a;
  font-size: 13px;
  line-height: 1.45;
}

.checkout-topic-row span,
.prompt-preview-row span {
  color: #64748b;
  font-size: 12px;
}

.checkout-topic-row b {
  color: #2563eb;
  font-size: 12px;
  white-space: nowrap;
}

.prompt-preview-more {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.model-selection-grid--compact {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.receipt-surface {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: clamp(360px, calc(100vh - 214px), 550px);
  overflow: auto;
  padding: 12px;
  position: sticky;
  top: 86px;
}

.receipt-brand {
  display: grid;
  gap: 6px;
  padding-top: 4px;
}

.receipt-brand strong {
  color: #0f172a;
  font-size: 18px;
  line-height: 1.1;
  letter-spacing: -0.055em;
}

.receipt-total {
  display: grid;
  gap: 5px;
  border-top: 1px solid #e2e8f0;
  padding-top: 8px;
}

.receipt-coverage {
  display: grid;
  gap: 8px;
}

.receipt-coverage strong,
.receipt-pricing strong {
  color: #0f172a;
  font-size: 14px;
  line-height: 1.45;
}

.receipt-topic-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.receipt-topic-chips span {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border: 1px solid #dbe3ee;
  border-radius: 999px;
  background: #f8fafc;
  color: #475569;
  font-size: 11px;
  font-weight: 700;
}

.receipt-total__value {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.receipt-total__value strong {
  color: #0f172a;
  font-size: 36px;
  line-height: 0.9;
  letter-spacing: -0.09em;
}

.receipt-total__value small {
  color: #2563eb;
  font-size: 13px;
  font-weight: 800;
}

.receipt-credit-line {
  display: inline-flex;
  align-items: baseline;
  gap: 5px;
  color: #475569;
  font-size: 12px;
  font-weight: 800;
}

.receipt-credit-line strong {
  color: #2563eb;
  font-size: 14px;
  letter-spacing: -0.04em;
}

.receipt-credit-hint {
  color: #64748b;
  font-size: 11px;
  line-height: 1.4;
}

.receipt-pricing {
  display: grid;
  gap: 4px;
  padding: 8px 10px;
  border: 1px dashed rgba(37, 99, 235, 0.24);
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.06), rgba(255, 255, 255, 0.84));
}

.receipt-list {
  display: grid;
  gap: 1px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #e2e8f0;
}

.receipt-list div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  background: #fff;
  padding: 8px 10px;
}

.receipt-actions {
  display: grid;
  gap: 8px;
  margin-top: auto;
  position: sticky;
  bottom: -12px;
  padding: 10px 0 0;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0) 0%, rgba(255, 255, 255, 0.92) 18%, #ffffff 48%);
}

.receipt-action-note {
  margin: 0;
  color: #64748b;
  font-size: 11px;
  line-height: 1.45;
}

.receipt-primary,
.receipt-secondary {
  width: 100%;
  justify-content: center;
}

.coverage-network {
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1.1fr) minmax(240px, 0.85fr);
}

.coverage-network__column {
  display: grid;
  gap: 10px;
  align-content: start;
}

.coverage-network__column--center,
.coverage-network__column--summary {
  padding-left: 12px;
  border-left: 1px solid #e5e7eb;
}

.coverage-network__eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #6b7280;
}

.coverage-topic-list,
.coverage-bucket-list,
.coverage-metric-list {
  display: grid;
  gap: 10px;
}

.coverage-topic-node,
.coverage-bucket-card,
.coverage-metric-card {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
  padding: 12px;
  display: grid;
  gap: 8px;
}

.coverage-topic-node strong,
.coverage-bucket-card__meta strong,
.coverage-metric-card strong {
  font-size: 13px;
  color: #111827;
}

.coverage-topic-node span,
.coverage-bucket-card__meta span,
.coverage-bucket-card__stats em,
.coverage-metric-card span,
.coverage-metric-card em {
  font-size: 12px;
  line-height: 1.6;
  color: #6b7280;
}

.coverage-metric-card__body {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: #4b5563;
}

.model-selection-section {
  display: grid;
  gap: 12px;
  margin-top: 4px;
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

.coverage-topic-node em {
  font-size: 12px;
  color: #374151;
}

.coverage-bucket-card {
  background: #fcfcfd;
}

.coverage-bucket-card__stats {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.coverage-bucket-card__stats b {
  font-size: 12px;
  color: #111827;
}

.coverage-metric-list {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.coverage-metric-card--wide {
  grid-column: 1 / -1;
}

.side-stat {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fcfcfd;
  padding: 12px;
  display: grid;
  gap: 6px;
}

.side-stat span {
  font-size: 12px;
  color: #6b7280;
}

.side-stat strong {
  font-size: 14px;
  line-height: 1.5;
  color: #111827;
}

.side-stat__body {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: #4b5563;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.actions--end {
  justify-content: flex-end;
}

.actions--split {
  justify-content: space-between;
}

.cta-primary,
.btn-plain {
  min-height: 38px;
  padding: 0 14px;
}

@media (max-width: 1200px) {
  .wizard-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .wizard-stepper {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .coverage-network {
    grid-template-columns: minmax(0, 1fr);
  }

  .confirmation-facts,
  .execution-facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .checkout-detail-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .run-setup-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .run-choice-section--mode,
  .run-choice-section--preview {
    grid-column: auto;
    grid-row: auto;
  }

  .coverage-network__column--center,
  .coverage-network__column--summary {
    padding-left: 0;
    border-left: 0;
    padding-top: 12px;
    border-top: 1px solid #e5e7eb;
  }

  .model-selection-grid {
    grid-template-columns: 1fr;
  }

  .model-choice-list,
  .probe-count-grid,
  .run-mode-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .internal-scroll {
    max-height: 280px;
  }

  .receipt-surface {
    position: static;
  }
}

@media (max-width: 900px) {
  .form-grid,
  .form-grid--recognition,
  .topic-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .competitor-editor__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .field-inline__control {
    flex-direction: column;
    align-items: stretch;
  }

  .coverage-metric-list {
    grid-template-columns: minmax(0, 1fr);
  }

  .confirmation-facts,
  .checkout-topic-row {
    grid-template-columns: minmax(0, 1fr);
  }

  .coverage-bucket-card__stats {
    display: grid;
    gap: 4px;
    justify-content: start;
  }
}
</style>
