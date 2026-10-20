<script setup lang="ts">
import { computed, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import EngineQueryFanoutsTab from '~/components/engine/EngineQueryFanoutsTab.vue'
import ProbeStatusTabs from '~/components/engine/ProbeStatusTabs.vue'
import TagFilterBar from '~/components/engine/TagFilterBar.vue'
import ProblemAnalysisPromptTableToolbar from '~/components/workbench/ProblemAnalysisPromptTableToolbar.vue'
import { getEngineDatasetShared } from '~/composables/engine/engineDatasetShared'
import { resolveProductModelLabel } from '~/utils/productModels'
import type {
  CompareWindow,
  CountryCode,
  Granularity,
  LanguageCode,
  OptionItem,
  ProbeSeed,
  ProbeStatus,
  ProbeTagSeed,
  PromptColumnKey,
  PromptColumnOption,
  PromptGroupMode,
  PromptMetricRow,
  PromptTopicGroup,
  TimeWindow
} from '~/types/engine'

type PromptVolumesView = 'volume' | 'intent' | 'demographic' | 'hierarchy' | 'fanout'
type IntentKey = 'discovery' | 'decision' | 'risk' | 'cost'
type KeywordListKey = 'all' | 'core' | 'scenario' | 'comparison'
type ProjectionWindow = '30d' | '90d'
type ProjectionScenario = 'baseline' | 'aggressive' | 'conservative'
type MatchingType = 'semantic' | 'exact' | 'phrase'

interface IntentStat {
  key: IntentKey
  label: string
  color: string
  count: number
  share: number
  examples: string[]
}

interface PromptVolumeRow {
  platform: string
  value: number
  share: number
  delta: number
}

interface HierarchyCluster {
  label: string
  count: number
  samples: string[]
}

interface HierarchyTopic {
  topic: string
  demandIndex: number
  clusters: HierarchyCluster[]
}

interface KeywordListOption {
  key: KeywordListKey
  label: string
  matcher: (topic: string, prompt: string) => boolean
}

interface KeywordResearchRow {
  keyword: string
  topic: string
  monthlyVolume: number
  projection: number
  competition: number
  relevance: number
  opportunity: number
  intentLabel: string
  trend: number
}

interface BulkKeywordAnalysisRow {
  keyword: string
  projection: number
  share: number
  intent: string
  action: string
}

interface IntentSubIntentRow {
  key: string
  parent: IntentKey
  parentLabel: string
  label: string
  count: number
  share: number
  change: number
}

interface BrandRelevantPromptRow {
  id: string
  topic: string
  prompt: string
  status: '已被引用' | '待强化' | '竞品被引（我方缺口）' | '潜力词'
  citedTarget: string
  opportunity: number
  action: string
  landingPage: string
}

const props = withDefaults(defineProps<{
  hasPromptCoverage: boolean
  selectedBrand: string
  promptTotalCount: number
  promptTopicGroups: PromptTopicGroup[]
  promptTableGridStyle: Record<string, string>
  promptVisibleColumns: PromptColumnOption[]
  togglePromptTopic: (topic: string) => void
  isPromptTopicExpanded: (topic: string) => boolean
  resolvePromptCellValue: (row: PromptMetricRow, key: PromptColumnKey) => string
  resolvePromptCellDelta: (row: PromptMetricRow, key: PromptColumnKey) => string | null
  resolvePromptDeltaClass: (row: PromptMetricRow, key: PromptColumnKey) => string
  probes?: ProbeSeed[]
  selectedPlatforms: string[]
  selectedPromptTopics: string[]
  selectedTimeWindow: TimeWindow
  selectedCompare: CompareWindow
  selectedGranularity: Granularity
  selectedCountryCode?: CountryCode
  selectedLanguageCode?: LanguageCode
  selectedMatchingType?: MatchingType
  probeStatus?: ProbeStatus | 'all'
  probeTagIds?: string[]
  probeTagFilterMode?: 'or' | 'and'
  probeTags?: ProbeTagSeed[]
  promptGroupMode: PromptGroupMode
  promptGroupModeOptions: OptionItem<PromptGroupMode>[]
  setPromptGroupMode: (value: PromptGroupMode) => void
  promptSearch: string
  setPromptSearch: (value: string) => void
  selectedPromptColumns: PromptColumnKey[]
  promptColumnOptions: PromptColumnOption[]
  togglePromptColumn: (key: PromptColumnKey) => void
  workbenchMode?: boolean
}>(), {
  selectedCountryCode: 'CN',
  selectedLanguageCode: 'zh-CN',
  selectedMatchingType: 'semantic',
  probeStatus: 'all',
  probeTagIds: () => [],
  probeTagFilterMode: 'or',
  probeTags: () => [],
  probes: () => [],
  workbenchMode: false
})

const emit = defineEmits<{
  (event: 'update:probeStatus', value: ProbeStatus | 'all'): void
  (event: 'update:probeTagIds', value: string[]): void
  (event: 'update:probeTagFilterMode', value: 'or' | 'and'): void
  (event: 'openProbeDetail', probeId: string): void
}>()

const viewTabs: Array<{ key: PromptVolumesView, label: string }> = [
  { key: 'volume', label: '量级' },
  { key: 'intent', label: '意图' },
  { key: 'demographic', label: '人群' },
  { key: 'hierarchy', label: '问题层级' },
  { key: 'fanout', label: '扩展' }
]

const activeView = ref<PromptVolumesView>('volume')
const activeKeywordList = ref<KeywordListKey>('all')
const expandedHierarchyTopics = ref<string[]>([])
const keywordResearchSearch = ref('')
const projectionWindow = ref<ProjectionWindow>('30d')
const projectionScenario = ref<ProjectionScenario>('baseline')
const selectedResearchKeywords = ref<string[]>([])
const brandGapOnly = ref(false)
const minOpportunity = ref(60)
const engineDataset = getEngineDatasetShared()
const promptTopicPromptBank = computed<Record<string, string[]>>(() => {
  return Object.fromEntries(
    props.promptTopicGroups
      .filter(group => Array.isArray(group.rows) && group.rows.length)
      .map(group => [group.displayTopic, group.rows.map(row => row.prompt)])
  )
})

const probeSeedsForBrand = computed(() => {
  if (props.probes?.length) {
    return props.probes
  }
  return engineDataset.probeSeedsByBrand[props.selectedBrand] || []
})
const probeSeedByPromptKey = computed(() => {
  const map = new Map<string, ProbeSeed>()
  probeSeedsForBrand.value.forEach((item) => {
    map.set(`${item.topic}::${item.prompt}`, item)
  })
  return map
})

const probeTagMap = computed(() => {
  return new Map((props.probeTags || []).map(tag => [tag.id, tag]))
})
const hasProbeTagFilters = computed(() => (props.probeTags || []).length > 0)

const probeStatusCounts = computed<Record<ProbeStatus | 'all', number>>(() => {
  const all = probeSeedsForBrand.value.length
  const active = probeSeedsForBrand.value.filter(item => item.status === 'active').length
  const suggested = probeSeedsForBrand.value.filter(item => item.status === 'suggested').length
  const inactive = probeSeedsForBrand.value.filter(item => item.status === 'inactive').length
  return { all, active, suggested, inactive }
})

const availablePlatforms = computed(() => {
  if (props.selectedPlatforms.length) {
    return props.selectedPlatforms.map(resolveProductModelLabel)
  }
  return []
})

function resolveProbeSeed(topic: string, prompt: string): ProbeSeed | null {
  return probeSeedByPromptKey.value.get(`${topic}::${prompt}`)
    || probeSeedsForBrand.value.find(item => item.prompt === prompt)
    || null
}

function matchProbeStatus(seed: ProbeSeed | null): boolean {
  if (props.probeStatus === 'all') {
    return true
  }
  if (!seed) {
    return false
  }
  return seed.status === props.probeStatus
}

function matchProbeTags(seed: ProbeSeed | null): boolean {
  const selectedTags = props.probeTagIds || []
  if (!selectedTags.length) {
    return true
  }
  if (!seed) {
    return false
  }
  if (props.probeTagFilterMode === 'and') {
    return selectedTags.every(tag => seed.tags.includes(tag))
  }
  return selectedTags.some(tag => seed.tags.includes(tag))
}

const contextMultipliers = computed(() => {
  const countryFactor = props.selectedCountryCode === 'US'
    ? 1.08
    : (props.selectedCountryCode === 'SG' ? 0.94 : 1)
  const matchingFactor = props.selectedMatchingType === 'exact'
    ? 0.86
    : (props.selectedMatchingType === 'phrase' ? 0.95 : 1)
  const projectionFactor = projectionScenario.value === 'aggressive'
    ? 1.18
    : (projectionScenario.value === 'conservative' ? 0.88 : 1)
  return {
    countryFactor,
    matchingFactor,
    projectionFactor
  }
})

const scopedPromptRows = computed(() =>
  props.promptTopicGroups.flatMap(group =>
    group.rows.map(row => ({
      ...row,
      topic: group.displayTopic
    })).filter((row) => {
      const seed = resolveProbeSeed(row.topic, row.prompt)
      return matchProbeStatus(seed) && matchProbeTags(seed)
    })
  )
)

function hashValue(input: string): number {
  let hash = 2166136261
  for (let index = 0; index < input.length; index += 1) {
    hash ^= input.charCodeAt(index)
    hash = Math.imul(hash, 16777619)
  }
  return Math.abs(hash >>> 0)
}

function round1(value: number) {
  return Math.round(value * 10) / 10
}

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value))
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function normalizeShare(values: number[]) {
  const total = values.reduce((sum, item) => sum + item, 0)
  if (total <= 0) {
    return values.map(() => 0)
  }
  const raw = values.map(item => (item / total) * 100)
  const rounded = raw.map(item => round1(item))
  const delta = round1(100 - rounded.reduce((sum, item) => sum + item, 0))
  if (rounded.length) {
    rounded[0] = round1((rounded[0] ?? 0) + delta)
  }
  return rounded
}

const keywordListOptions = computed<KeywordListOption[]>(() => {
  const brandKeywords = (engineDataset.brandKeywordSeeds[props.selectedBrand] || [props.selectedBrand])
    .filter(Boolean)
    .map(item => item.trim())
    .filter(Boolean)
  const competitorKeywords = (engineDataset.competitorByBrand[props.selectedBrand] || [])
    .filter(Boolean)
    .map(item => item.trim())
    .filter(Boolean)

  const brandPattern = brandKeywords.length
    ? new RegExp(brandKeywords.map(escapeRegExp).join('|'), 'i')
    : null
  const competitorPattern = competitorKeywords.length
    ? new RegExp(competitorKeywords.map(escapeRegExp).join('|'), 'i')
    : null

  return [
    { key: 'all', label: '全量词表', matcher: () => true },
    {
      key: 'core',
      label: '核心品牌词',
      matcher: (topic, prompt) => {
        const text = `${topic} ${prompt}`
        return topic.includes('品牌')
          || /(认证|口碑|材质|做工|可靠|定位|心智)/.test(text)
          || (brandPattern ? brandPattern.test(text) : false)
      }
    },
    {
      key: 'scenario',
      label: '场景机会词',
      matcher: (topic, prompt) =>
        /(安装|出水|导热|体验|适配|收纳|售后|维护|清洗|场景|稳定|老房|改造|高频)/.test(topic + prompt)
    },
    {
      key: 'comparison',
      label: '竞品决策词',
      matcher: (topic, prompt) => {
        const text = `${topic} ${prompt}`
        return /(对比|谁更|差异|替代|预算|价格|值不值|推荐|成本|性价比)/.test(text)
          || (competitorPattern ? competitorPattern.test(text) : false)
      }
    }
  ]
})

const selectedKeywordListOption = computed(() => {
  return keywordListOptions.value.find(item => item.key === activeKeywordList.value) || keywordListOptions.value[0]!
})

const matchingTypeLabel = computed(() => {
  if (props.selectedMatchingType === 'exact') {
    return '精确'
  }
  if (props.selectedMatchingType === 'phrase') {
    return '短语'
  }
  return '语义'
})

function buildPromptTopicGroup(group: PromptTopicGroup, rows: PromptMetricRow[]) {
  if (!rows.length) {
    return null
  }

  const summary: PromptMetricRow = {
    id: `${group.topic}-summary-${props.workbenchMode ? 'workbench' : selectedKeywordListOption.value.key}`,
    topic: group.displayTopic,
    prompt: `${group.displayTopic} 摘要`,
    visibilityRank: Math.max(1, Math.round(rows.reduce((sum, row) => sum + row.visibilityRank, 0) / rows.length)),
    visibilityScore: round1(rows.reduce((sum, row) => sum + row.visibilityScore, 0) / rows.length),
    visibilityDelta: round1(rows.reduce((sum, row) => sum + row.visibilityDelta, 0) / rows.length),
    averagePosition: round1(rows.reduce((sum, row) => sum + row.averagePosition, 0) / rows.length),
    averageDelta: round1(rows.reduce((sum, row) => sum + row.averageDelta, 0) / rows.length),
    citationShare: round1(rows.reduce((sum, row) => sum + row.citationShare, 0) / rows.length),
    citationDelta: round1(rows.reduce((sum, row) => sum + row.citationDelta, 0) / rows.length)
  }

  return {
    ...group,
    rows,
    summary
  }
}

const filteredPromptTopicGroups = computed<PromptTopicGroup[]>(() => {
  const search = props.promptSearch.trim().toLowerCase()

  if (props.workbenchMode) {
    return props.promptTopicGroups
      .map((group) => {
        const rows = group.rows.filter((row) => {
          if (search && !`${group.displayTopic} ${row.prompt}`.toLowerCase().includes(search)) {
            return false
          }
          const seed = resolveProbeSeed(group.displayTopic, row.prompt)
          return matchProbeStatus(seed) && matchProbeTags(seed)
        })
        return buildPromptTopicGroup(group, rows)
      })
      .filter(Boolean) as PromptTopicGroup[]
  }

  const matcher = selectedKeywordListOption.value.matcher
  return props.promptTopicGroups
    .map((group) => {
      const rows = group.rows.filter((row) => {
        if (search && !`${group.displayTopic} ${row.prompt}`.toLowerCase().includes(search)) {
          return false
        }
        if (!matcher(group.displayTopic, row.prompt)) {
          return false
        }
        const seed = resolveProbeSeed(group.displayTopic, row.prompt)
        return matchProbeStatus(seed) && matchProbeTags(seed)
      })
      return buildPromptTopicGroup(group, rows)
    })
    .filter(Boolean) as PromptTopicGroup[]
})

const filteredPromptCount = computed(() =>
  filteredPromptTopicGroups.value.reduce((sum, group) => sum + group.rows.length, 0)
)

const keywordListStats = computed(() =>
  keywordListOptions.value.map(item => ({
    ...item,
    count: scopedPromptRows.value.filter(row => item.matcher(row.topic, row.prompt)).length
  }))
)

const trendPointCount = computed(() => {
  if (props.selectedTimeWindow === '7d') {
    return 7
  }
  if (props.selectedTimeWindow === '30d') {
    return 10
  }
  return 12
})

const promptVolumeTrend = computed(() => {
  const seed = hashValue([
    props.selectedBrand,
    activeKeywordList.value,
    props.selectedTimeWindow,
    props.selectedCompare,
    props.selectedGranularity,
    props.selectedCountryCode,
    props.selectedMatchingType,
    availablePlatforms.value.join('|'),
    props.selectedPromptTopics.join('|')
  ].join('::'))

  const labels = Array.from({ length: trendPointCount.value }, (_, index) => {
    if (props.selectedGranularity === 'month') {
      return `M${index + 1}`
    }
    if (props.selectedGranularity === 'week') {
      return `W${index + 1}`
    }
    return `D${index + 1}`
  })

  const base = Math.max(
    84,
    filteredPromptCount.value * 32 * contextMultipliers.value.countryFactor * contextMultipliers.value.matchingFactor + (seed % 110)
  )
  const current = labels.map((_, index) => {
    const wave = Math.sin(index * 0.72 + seed * 0.003) * (base * 0.08)
    const drift = index * (base * 0.012)
    return round1(clamp(base + wave + drift, 36, 1800))
  })
  const previous = current.map((value, index) => {
    const penalty = props.selectedCompare === 'yoy' ? 0.16 : 0.08
    const swing = Math.cos(index * 0.64 + seed * 0.0019) * (value * 0.06)
    return round1(clamp(value * (1 - penalty) + swing, 28, 1700))
  })

  return {
    labels,
    current,
    previous
  }
})

const promptVolumeKpi = computed(() => {
  const current = promptVolumeTrend.value.current.reduce((sum, value) => sum + value, 0)
  const previous = promptVolumeTrend.value.previous.reduce((sum, value) => sum + value, 0)
  const delta = previous > 0 ? round1(((current - previous) / previous) * 100) : 0
  return {
    total: Math.round(current),
    delta
  }
})

const promptVolumeTrendOption = computed<EChartsOption | null>(() => {
  const data = promptVolumeTrend.value
  if (!data.current.length) {
    return null
  }

  const showPrevious = props.selectedCompare !== 'none'

  return {
    grid: { top: 30, right: 16, left: 44, bottom: 38 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data.labels,
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
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#111827',
      borderColor: '#111827',
      textStyle: { color: '#f9fafb', fontSize: 11 }
    },
    series: [
      {
        name: '当前周期',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { color: '#111827', width: 2 },
        itemStyle: { color: '#111827' },
        areaStyle: { color: 'rgba(17, 24, 39, 0.04)' },
        data: data.current
      },
      {
        name: '对比周期',
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: {
          color: '#94a3b8',
          width: 1.5,
          type: 'dashed'
        },
        data: showPrevious ? data.previous : [],
        tooltip: { show: showPrevious }
      }
    ]
  }
})

const promptVolumeRows = computed<PromptVolumeRow[]>(() => {
  const seed = hashValue(
    `${props.selectedBrand}-${activeKeywordList.value}-${props.selectedTimeWindow}-${props.selectedCountryCode}-${props.selectedMatchingType}`
  )
  const base = Math.max(
    140,
    filteredPromptCount.value * 42 * contextMultipliers.value.countryFactor * contextMultipliers.value.matchingFactor
  )

  const raw = availablePlatforms.value.map((platform, index) => {
    const weight = 1 - index * 0.14
    const wave = Math.sin(seed * 0.0023 + index * 0.87) * 0.08
    const value = Math.max(24, Math.round(base * clamp(weight + wave, 0.22, 1.3)))
    const delta = round1(Math.cos(seed * 0.003 + index * 0.63) * 9.5)
    return {
      platform,
      value,
      delta
    }
  })
  const shares = normalizeShare(raw.map(item => item.value))
  return raw
    .map((row, index) => ({
      ...row,
      share: shares[index] || 0
    }))
    .sort((a, b) => b.value - a.value)
})

const projectionWindowOptions: Array<{ key: ProjectionWindow, label: string }> = [
  { key: '30d', label: '近 30 天估算' },
  { key: '90d', label: '近 90 天估算' }
]

const projectionScenarioOptions: Array<{ key: ProjectionScenario, label: string }> = [
  { key: 'baseline', label: '基线' },
  { key: 'aggressive', label: '激进' },
  { key: 'conservative', label: '保守' }
]

const keywordResearchCandidates = computed(() => {
  const brandKeywords = (engineDataset.brandKeywordSeeds[props.selectedBrand] || [props.selectedBrand]).slice(0, 4)
  const competitors = (engineDataset.competitorByBrand[props.selectedBrand] || []).slice(0, 3)
  const topicTokens = filteredPromptTopicGroups.value
    .map(group => group.displayTopic)
    .flatMap(topic => topic.split(/[、/与和]/g).map(item => item.trim()))
    .filter(Boolean)
  const scenarioKeywords = ['安装', '售后', '预算', '安全', '口碑', '稳定', '耐用', '推荐', '对比', '认证']
  return Array.from(new Set([
    ...brandKeywords,
    ...competitors,
    ...topicTokens,
    ...scenarioKeywords
  ]))
})

const keywordResearchRows = computed<KeywordResearchRow[]>(() => {
  const horizon = projectionWindow.value === '90d' ? 3 : 1
  return keywordResearchCandidates.value.map((keyword, index) => {
    const seed = hashValue([
      props.selectedBrand,
      keyword,
      props.selectedCountryCode,
      props.selectedMatchingType,
      projectionWindow.value,
      projectionScenario.value,
      activeKeywordList.value,
      String(index)
    ].join('::'))
    const topic = filteredPromptTopicGroups.value[index % Math.max(filteredPromptTopicGroups.value.length, 1)]?.displayTopic || '综合'
    const monthlyVolume = Math.round(clamp(140 + (seed % 820), 80, 1600) * contextMultipliers.value.countryFactor * contextMultipliers.value.matchingFactor)
    const projection = Math.round(monthlyVolume * horizon * contextMultipliers.value.projectionFactor)
    const competition = round1(clamp(28 + Math.sin(seed * 0.00031 + index * 0.31) * 32, 8, 96))
    const relevance = round1(clamp(52 + Math.cos(seed * 0.00027 + index * 0.47) * 35, 20, 98))
    const opportunity = round1(clamp(relevance * 0.62 + (100 - competition) * 0.38, 12, 98))
    const trend = round1(clamp(Math.sin(seed * 0.00045 + index * 0.33) * 18, -20, 22))
    const text = `${keyword} ${topic}`
    const intentLabel = /(价格|预算|成本|价)/.test(text)
      ? '成本'
      : (/(对比|推荐|替代|选)/.test(text) ? '决策' : (/(故障|风险|售后|稳定)/.test(text) ? '风险' : '探索'))
    return {
      keyword,
      topic,
      monthlyVolume,
      projection,
      competition,
      relevance,
      opportunity,
      intentLabel,
      trend
    }
  }).sort((a, b) => b.opportunity - a.opportunity)
})

const filteredKeywordResearchRows = computed(() => {
  const keyword = keywordResearchSearch.value.trim().toLowerCase()
  if (!keyword) {
    return keywordResearchRows.value
  }
  return keywordResearchRows.value.filter(row => `${row.keyword} ${row.topic}`.toLowerCase().includes(keyword))
})

const activeResearchKeywords = computed(() => {
  if (selectedResearchKeywords.value.length) {
    return selectedResearchKeywords.value
  }
  return filteredKeywordResearchRows.value.slice(0, 6).map(row => row.keyword)
})

const bulkKeywordAnalysisRows = computed<BulkKeywordAnalysisRow[]>(() => {
  const picked = new Set(activeResearchKeywords.value)
  const rows = keywordResearchRows.value.filter(row => picked.has(row.keyword))
  const totalProjection = rows.reduce((sum, row) => sum + row.projection, 0)
  return rows.map(row => ({
    keyword: row.keyword,
    projection: row.projection,
    share: totalProjection > 0 ? round1((row.projection / totalProjection) * 100) : 0,
    intent: row.intentLabel,
    action: row.opportunity >= 70 ? '优先扩展探针' : (row.opportunity >= 56 ? '保留观察' : '低优先级')
  }))
})

const keywordProjectionSummary = computed(() => {
  const rows = bulkKeywordAnalysisRows.value
  const total = rows.reduce((sum, row) => sum + row.projection, 0)
  const highValue = rows.filter(row => row.action === '优先扩展探针').length
  return {
    total,
    highValue,
    count: rows.length
  }
})

function toggleResearchKeyword(keyword: string) {
  if (selectedResearchKeywords.value.includes(keyword)) {
    selectedResearchKeywords.value = selectedResearchKeywords.value.filter(item => item !== keyword)
    return
  }
  selectedResearchKeywords.value = [...selectedResearchKeywords.value, keyword]
}

function resolveIntentKey(prompt: string): IntentKey {
  if (/(对比|替代|谁更|推荐|选哪个|差异|值得买吗)/i.test(prompt)) {
    return 'decision'
  }
  if (/(漏水|故障|风险|误跳|掉漆|问题|投诉|是否稳定|耐用)/i.test(prompt)) {
    return 'risk'
  }
  if (/(价格|预算|成本|性价比|贵|便宜|价差|回报)/i.test(prompt)) {
    return 'cost'
  }
  return 'discovery'
}

function resolveSubIntent(prompt: string, parent: IntentKey) {
  if (parent === 'discovery') {
    if (/(定位|口碑|心智|认知)/.test(prompt)) {
      return '品牌认知'
    }
    if (/(参数|材质|做工|认证)/.test(prompt)) {
      return '参数理解'
    }
    return '需求识别'
  }
  if (parent === 'decision') {
    if (/(对比|差异|谁更)/.test(prompt)) {
      return '同档对比'
    }
    if (/(推荐|选|型号|系列)/.test(prompt)) {
      return '型号选择'
    }
    return '购买路径'
  }
  if (parent === 'risk') {
    if (/(安装|改造|兼容)/.test(prompt)) {
      return '安装风险'
    }
    if (/(售后|补件|客服|报修)/.test(prompt)) {
      return '售后风险'
    }
    return '质量风险'
  }
  if (/(活动|折扣|促销)/.test(prompt)) {
    return '价格窗口'
  }
  if (/(预算|成本|回报)/.test(prompt)) {
    return '预算控制'
  }
  return '总持有成本'
}

const intentStats = computed<IntentStat[]>(() => {
  const rows = filteredPromptTopicGroups.value.flatMap(group => group.rows)
  const intents: Array<{ key: IntentKey, label: string, color: string }> = [
    { key: 'discovery', label: '信息探索', color: '#2563eb' },
    { key: 'decision', label: '购买决策', color: '#0f766e' },
    { key: 'risk', label: '风险验证', color: '#b91c1c' },
    { key: 'cost', label: '成本评估', color: '#7c3aed' }
  ]

  return intents.map((intent) => {
    const matched = rows.filter(row => resolveIntentKey(row.prompt) === intent.key)
    return {
      ...intent,
      count: matched.length,
      share: rows.length ? round1((matched.length / rows.length) * 100) : 0,
      examples: matched.slice(0, 3).map(row => row.prompt)
    }
  }).sort((a, b) => b.count - a.count)
})

const intentSubIntentRows = computed<IntentSubIntentRow[]>(() => {
  const rows = filteredPromptTopicGroups.value.flatMap(group => group.rows)
  if (!rows.length) {
    return []
  }
  const map = new Map<string, IntentSubIntentRow>()
  const parentLabelMap: Record<IntentKey, string> = {
    discovery: '信息探索',
    decision: '购买决策',
    risk: '风险验证',
    cost: '成本评估'
  }
  rows.forEach((row, index) => {
    const parent = resolveIntentKey(row.prompt)
    const label = resolveSubIntent(row.prompt, parent)
    const key = `${parent}-${label}`
    const change = round1(clamp(Math.sin(hashValue(`${key}-${row.prompt}-${index}`) * 0.00043) * 7.8, -9.5, 11.2))
    const current = map.get(key)
    if (!current) {
      map.set(key, {
        key,
        parent,
        parentLabel: parentLabelMap[parent],
        label,
        count: 1,
        share: 0,
        change
      })
      return
    }
    current.count += 1
  })
  const total = rows.length
  return Array.from(map.values())
    .map(item => ({ ...item, share: round1((item.count / total) * 100) }))
    .sort((a, b) => b.share - a.share)
})

const intentConversationOption = computed<EChartsOption | null>(() => {
  if (!intentStats.value.length) {
    return null
  }
  const parentData = intentStats.value.map(item => ({
    name: item.label,
    value: item.count,
    itemStyle: { color: item.color }
  }))
  const childData = intentSubIntentRows.value.map((item) => {
    const parent = intentStats.value.find(row => row.key === item.parent)
    return {
      name: `${item.parentLabel}·${item.label}`,
      value: item.count,
      itemStyle: { color: parent?.color || '#94a3b8' }
    }
  })
  return {
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        const datum = Array.isArray(params) ? params[0] : params
        const value = Number(datum?.value ?? 0)
        const total = Math.max(1, intentStats.value.reduce((sum, item) => sum + item.count, 0))
        return `${datum?.name || ''}<br/>${round1((value / total) * 100)}%`
      }
    },
    series: [
      {
        type: 'pie',
        radius: ['0%', '46%'],
        center: ['38%', '52%'],
        label: { show: false },
        itemStyle: { borderColor: '#fff', borderWidth: 1 },
        data: parentData
      },
      {
        type: 'pie',
        radius: ['58%', '82%'],
        center: ['38%', '52%'],
        label: { show: false },
        itemStyle: { borderColor: '#fff', borderWidth: 1, opacity: 0.76 },
        data: childData
      }
    ]
  }
})

const brandRelevantPromptRows = computed<BrandRelevantPromptRow[]>(() => {
  const competitors = engineDataset.competitorByBrand[props.selectedBrand] || []
  const mainCompetitor = competitors[0] || '竞品A'
  return filteredPromptTopicGroups.value.flatMap(group =>
    group.rows.map((row, index) => {
      const promptSeed = hashValue(`${row.id}-${group.topic}-${props.selectedBrand}-${index}`)
      const competitorHit = competitors.some(item => row.prompt.includes(item))
      let status: BrandRelevantPromptRow['status'] = '潜力词'
      let citedTarget = props.selectedBrand
      if (row.citationShare >= 8 && row.visibilityRank <= 3) {
        status = '已被引用'
      } else if (competitorHit || (row.citationShare < 4 && row.visibilityRank > 3)) {
        status = '竞品被引（我方缺口）'
        citedTarget = mainCompetitor
      } else if (row.citationShare >= 4) {
        status = '待强化'
      }
      const opportunity = Math.round(clamp(
        100 - row.citationShare * 2.2 - row.visibilityRank * 6 + Math.sin(promptSeed * 0.00038) * 8 + (status === '竞品被引（我方缺口）' ? 14 : 0),
        20,
        98
      ))
      const action = status === '竞品被引（我方缺口）'
        ? '补对比页面并新增证据链'
        : (status === '待强化' ? '补常见问题页 + 参数页摘要' : (status === '已被引用' ? '维持并拓展到更多平台' : '加入下一轮探针池'))
      const landingPage = `/assets/${encodeURIComponent(group.displayTopic)}/evidence`
      return {
        id: row.id,
        topic: group.displayTopic,
        prompt: row.prompt,
        status,
        citedTarget,
        opportunity,
        action,
        landingPage
      }
    })
  ).sort((a, b) => b.opportunity - a.opportunity)
})

const filteredBrandRelevantPromptRows = computed(() => {
  return brandRelevantPromptRows.value.filter((row) => {
    if (brandGapOnly.value && row.status !== '竞品被引（我方缺口）') {
      return false
    }
    return row.opportunity >= minOpportunity.value
  })
})

const brandRelevantPromptSummary = computed(() => {
  const rows = brandRelevantPromptRows.value
  const total = rows.length || 1
  const covered = rows.filter(row => row.status === '已被引用').length
  const gaps = rows.filter(row => row.status === '竞品被引（我方缺口）').length
  const highOpportunity = rows.filter(row => row.opportunity >= 75).length
  return {
    coverage: round1((covered / total) * 100),
    gaps,
    highOpportunity
  }
})

const demographicAgeRows = computed(() => {
  const seed = hashValue(`${props.selectedBrand}-${activeKeywordList.value}-age`)
  const labels = ['18-24', '25-34', '35-44', '45-54', '55+']
  const raw = labels.map((_, index) =>
    clamp(14 + Math.sin(seed * 0.0017 + index * 0.86) * 7 + (index === 1 ? 6 : 0), 6, 42)
  )
  const shares = normalizeShare(raw)
  return labels.map((label, index) => ({
    label,
    share: shares[index] || 0
  }))
})

const demographicIncomeRows = computed(() => {
  const seed = hashValue(`${props.selectedBrand}-${activeKeywordList.value}-income`)
  const labels = ['5k 以下', '5k-10k', '10k-20k', '20k+']
  const raw = labels.map((_, index) =>
    clamp(16 + Math.cos(seed * 0.0021 + index * 1.08) * 6 + (index === 2 ? 4 : 0), 8, 44)
  )
  const shares = normalizeShare(raw)
  return labels.map((label, index) => ({
    label,
    share: shares[index] || 0
  }))
})

const demographicPlatformRows = computed(() =>
  promptVolumeRows.value.map(item => ({
    platform: item.platform,
    score: round1(52 + item.share * 0.42),
    confidence: round1(clamp(48 + item.share * 0.46, 48, 92))
  }))
)

const hierarchyTopics = computed<HierarchyTopic[]>(() => {
  const seed = hashValue(`${props.selectedBrand}-${activeKeywordList.value}-hierarchy`)
  return filteredPromptTopicGroups.value.map((group, index) => {
    const coreSamples = group.rows.slice(0, 3).map(item => item.prompt)
    const decisionSamples = group.rows
      .filter(item => resolveIntentKey(item.prompt) === 'decision' || resolveIntentKey(item.prompt) === 'cost')
      .slice(0, 3)
      .map(item => item.prompt)
    const longTailSeeds = group.rows.slice(0, 2).map(item => `${item.prompt} 怎么选更稳`)
    return {
      topic: group.displayTopic,
      demandIndex: Math.round(clamp(group.rows.length * 18 + Math.sin(seed * 0.002 + index * 0.7) * 24 + 120, 80, 420)),
      clusters: [
        { label: '核心问法', count: coreSamples.length, samples: coreSamples },
        { label: '决策问法', count: decisionSamples.length, samples: decisionSamples.length ? decisionSamples : coreSamples.slice(0, 2) },
        { label: '长尾延展', count: longTailSeeds.length, samples: longTailSeeds }
      ]
    }
  })
})

const defaultHierarchyTopic = computed(() => hierarchyTopics.value[0]?.topic || '')

function isHierarchyTopicExpanded(topic: string) {
  if (!expandedHierarchyTopics.value.length) {
    return topic === defaultHierarchyTopic.value
  }
  return expandedHierarchyTopics.value.includes(topic)
}

function toggleHierarchyTopic(topic: string) {
  if (expandedHierarchyTopics.value.includes(topic)) {
    expandedHierarchyTopics.value = expandedHierarchyTopics.value.filter(item => item !== topic)
    return
  }
  expandedHierarchyTopics.value = [...expandedHierarchyTopics.value, topic]
}

function toPercent(value: number) {
  return `${value.toFixed(1)}%`
}

function updateProbeStatus(value: ProbeStatus | 'all') {
  emit('update:probeStatus', value)
}

function updateProbeTagIds(value: string[]) {
  emit('update:probeTagIds', value)
}

function updateProbeTagFilterMode(value: 'or' | 'and') {
  emit('update:probeTagFilterMode', value)
}

function resolveProbeStatusLabel(status: ProbeStatus | undefined) {
  if (status === 'active') {
    return '已启用'
  }
  if (status === 'suggested') {
    return '建议启用'
  }
  if (status === 'inactive') {
    return '已停用'
  }
  return '未标注'
}

function resolveProbeTags(seed: ProbeSeed | null) {
  if (!seed?.tags?.length) {
    return []
  }
  return seed.tags
    .map(id => probeTagMap.value.get(id))
    .filter(Boolean) as ProbeTagSeed[]
}

function openProbeDetail(topic: string, prompt: string) {
  const seed = resolveProbeSeed(topic, prompt)
  if (!seed) {
    return
  }
  emit('openProbeDetail', seed.id)
}
</script>

<template>
  <section class="surface section-surface prompts-surface">
    <div class="surface-head">
      <div class="surface-head__title">
        <strong>问题分析</strong>
        <div class="surface-head__context">
          <span class="surface-head__object">{{ props.selectedBrand }}</span>
          <span class="surface-head__meta">{{ filteredPromptCount }} / {{ props.promptTotalCount }} 条问题</span>
          <span
            v-if="!props.workbenchMode"
            class="surface-head__meta"
          >匹配 {{ matchingTypeLabel }}</span>
        </div>
      </div>

      <div
        v-if="!props.workbenchMode"
        class="prompt-head-side"
      >
        <div class="prompt-list-chips">
          <button
            v-for="list in keywordListStats"
            :key="`keyword-list-${list.key}`"
            type="button"
            class="prompt-list-chip"
            :class="{ 'prompt-list-chip--active': activeKeywordList === list.key }"
            @click="activeKeywordList = list.key"
          >
            <span>{{ list.label }}</span>
            <span class="prompt-list-chip__count">{{ list.count }}</span>
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="!props.hasPromptCoverage"
      class="state-block"
    >
      {{ props.workbenchMode ? '当前品牌暂无结构化问题清单，可先查看最近聊天证据链。' : '至少选择 1 个主题和 1 个平台' }}
    </div>

    <template v-else>
      <section
        v-if="!props.workbenchMode"
        class="surface prompts-tab-surface"
      >
        <button
          v-for="tab in viewTabs"
          :key="`prompts-view-${tab.key}`"
          type="button"
          class="prompts-tab-btn"
          :class="{ 'prompts-tab-btn--active': activeView === tab.key }"
          @click="activeView = tab.key"
        >
          {{ tab.label }}
        </button>
      </section>

      <section class="surface probe-filter-surface">
        <ProbeStatusTabs
          :model-value="props.probeStatus"
          :counts="probeStatusCounts"
          @update:model-value="updateProbeStatus"
        />
        <TagFilterBar
          v-if="!props.workbenchMode && hasProbeTagFilters"
          :tags="props.probeTags"
          :model-value="props.probeTagIds"
          :mode="props.probeTagFilterMode"
          @update:model-value="updateProbeTagIds"
          @update:mode="updateProbeTagFilterMode"
        />
      </section>

      <div
        v-if="!filteredPromptTopicGroups.length"
        class="state-block"
      >
        {{ props.workbenchMode ? '当前品牌暂无结构化问题清单，可先查看最近聊天证据链。' : '当前词表未命中可显示的探针' }}
      </div>

      <template v-else-if="props.workbenchMode">
        <section class="surface section-surface prompts-table-surface">
          <div class="surface-head surface-head--compact">
            <strong>主题与探针清单</strong>
          </div>

          <ProblemAnalysisPromptTableToolbar
            :prompt-search="props.promptSearch"
            :set-prompt-search="props.setPromptSearch"
            :prompt-group-mode="props.promptGroupMode"
            :prompt-group-mode-options="props.promptGroupModeOptions"
            :set-prompt-group-mode="props.setPromptGroupMode"
            :selected-prompt-columns="props.selectedPromptColumns"
            :prompt-column-options="props.promptColumnOptions"
            :toggle-prompt-column="props.togglePromptColumn"
          />

          <div class="prompts-table">
            <div
              class="prompts-table__head"
              :style="props.promptTableGridStyle"
            >
              <span class="prompts-table__topic">主题</span>
              <span
                v-for="column in props.promptVisibleColumns"
                :key="`prompt-head-workbench-${column.key}`"
              >{{ column.label }}</span>
            </div>

            <template
              v-for="group in filteredPromptTopicGroups"
              :key="`prompt-topic-group-workbench-${group.topic}`"
            >
              <div
                class="prompts-table__row prompts-table__row--topic"
                :style="props.promptTableGridStyle"
                @click="props.togglePromptTopic(group.topic)"
              >
                <button
                  type="button"
                  class="prompts-table__topic-trigger"
                >
                  <span
                    class="prompts-table__chevron"
                    :class="{ 'prompts-table__chevron--open': props.isPromptTopicExpanded(group.topic) }"
                  >›</span>
                  <span class="prompts-table__topic-text">{{ group.displayTopic }}</span>
                  <span class="prompts-table__topic-meta">{{ group.rows.length }} 条探针</span>
                </button>

                <div
                  v-for="column in props.promptVisibleColumns"
                  :key="`prompt-topic-summary-workbench-${group.topic}-${column.key}`"
                  class="prompts-cell"
                >
                  <span class="prompts-cell__value">{{ props.resolvePromptCellValue(group.summary, column.key) }}</span>
                  <span
                    v-if="props.resolvePromptCellDelta(group.summary, column.key)"
                    class="prompts-cell__delta"
                    :class="props.resolvePromptDeltaClass(group.summary, column.key)"
                  >
                    {{ props.resolvePromptCellDelta(group.summary, column.key) }}
                  </span>
                  <span
                    v-else
                    class="prompts-cell__delta prompts-cell__delta--na"
                  >-</span>
                </div>
              </div>

              <div
                v-if="props.isPromptTopicExpanded(group.topic)"
                class="prompts-table__group-body"
              >
                <div
                  v-for="row in group.rows"
                  :key="`prompt-topic-row-workbench-${row.id}`"
                  class="prompts-table__row prompts-table__row--prompt"
                  :style="props.promptTableGridStyle"
                  @click="openProbeDetail(group.displayTopic, row.prompt)"
                >
                  <div class="prompts-table__prompt-text">
                    {{ row.prompt }}
                    <small class="prompts-table__prompt-meta">
                      <span class="prompt-meta-pill">
                        {{ resolveProbeStatusLabel(resolveProbeSeed(group.displayTopic, row.prompt)?.status) }}
                      </span>
                    </small>
                  </div>

                  <div
                    v-for="column in props.promptVisibleColumns"
                    :key="`prompt-row-cell-workbench-${row.id}-${column.key}`"
                    class="prompts-cell"
                  >
                    <span class="prompts-cell__value">{{ props.resolvePromptCellValue(row, column.key) }}</span>
                    <span
                      v-if="props.resolvePromptCellDelta(row, column.key)"
                      class="prompts-cell__delta"
                      :class="props.resolvePromptDeltaClass(row, column.key)"
                    >
                      {{ props.resolvePromptCellDelta(row, column.key) }}
                    </span>
                    <span
                      v-else
                      class="prompts-cell__delta prompts-cell__delta--na"
                    >-</span>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </section>
      </template>

      <template v-else-if="activeView === 'volume'">
        <section class="prompt-volume-two-up">
          <article class="surface section-surface prompt-volume-chart">
            <div class="metric-card__head">
              <div class="surface-head__title">
                <strong>搜索需求量趋势</strong>
                <p class="module-subcopy">
                  观察各模型平台中与 {{ selectedKeywordListOption.label }} 相关的提问热度变化
                </p>
              </div>
            </div>

            <div class="prompt-volume-kpi-row">
              <div class="kpi-tile">
                <span>需求量</span>
                <strong>{{ promptVolumeKpi.total.toLocaleString() }}</strong>
                <small :class="promptVolumeKpi.delta >= 0 ? 'delta-up' : 'delta-down'">
                  {{ promptVolumeKpi.delta >= 0 ? '+' : '' }}{{ promptVolumeKpi.delta.toFixed(1) }}%
                </small>
              </div>
              <div class="kpi-tile">
                <span>活跃主题</span>
                <strong>{{ filteredPromptTopicGroups.length }}</strong>
                <small>{{ availablePlatforms.length }} 平台</small>
              </div>
            </div>

            <GeoLazyEChart
              :option="promptVolumeTrendOption"
              :height="300"
              empty-text="暂无趋势数据"
            />
          </article>

          <aside class="surface section-surface prompt-volume-rank">
            <div class="surface-head surface-head--compact">
              <strong>平台需求贡献</strong>
            </div>

            <div class="volume-rank-table">
              <div class="volume-rank-table__head">
                <span />
                <span>平台</span>
                <span>量级</span>
                <span>占比</span>
                <span>变化</span>
              </div>
              <div
                v-for="(row, index) in promptVolumeRows"
                :key="`platform-volume-${row.platform}`"
                class="volume-rank-table__row"
              >
                <span>{{ index + 1 }}.</span>
                <span class="volume-rank-table__platform">{{ row.platform }}</span>
                <span>{{ row.value }}</span>
                <span>{{ toPercent(row.share) }}</span>
                <span :class="row.delta >= 0 ? 'delta-up' : 'delta-down'">
                  {{ row.delta >= 0 ? '+' : '' }}{{ row.delta.toFixed(1) }}%
                </span>
              </div>
            </div>
          </aside>
        </section>

        <section class="surface section-surface keyword-research-surface">
          <div class="metric-card__head">
            <div class="surface-head__title">
              <strong>关键词研究</strong>
              <p class="module-subcopy">
                合并关键词筛选、需求估算与批量分析，统一判断该补哪些高价值问法。
              </p>
            </div>
          </div>

          <div class="prompt-volume-two-up keyword-depth-two-up keyword-depth-two-up--merged">
            <article class="surface section-surface prompt-volume-chart">
              <div class="keyword-tools-row">
                <label class="prompt-search keyword-search">
                  <span class="prompt-search__icon">⌕</span>
                  <input
                    v-model="keywordResearchSearch"
                    type="search"
                    placeholder="搜索关键词"
                  >
                </label>
                <select
                  v-model="projectionWindow"
                  class="select-inline"
                >
                  <option
                    v-for="option in projectionWindowOptions"
                    :key="`projection-window-${option.key}`"
                    :value="option.key"
                  >
                    {{ option.label }}
                  </option>
                </select>
                <select
                  v-model="projectionScenario"
                  class="select-inline"
                >
                  <option
                    v-for="option in projectionScenarioOptions"
                    :key="`projection-scenario-${option.key}`"
                    :value="option.key"
                  >
                    {{ option.label }}
                  </option>
                </select>
              </div>

              <div class="keyword-research-table">
                <div class="keyword-research-table__head">
                  <span>关键词</span>
                  <span>月需求</span>
                  <span>需求估算</span>
                  <span>机会分</span>
                </div>
                <button
                  v-for="row in filteredKeywordResearchRows.slice(0, 8)"
                  :key="`keyword-research-${row.keyword}`"
                  type="button"
                  class="keyword-research-table__row"
                  :class="{ 'keyword-research-table__row--active': activeResearchKeywords.includes(row.keyword) }"
                  @click="toggleResearchKeyword(row.keyword)"
                >
                  <span>{{ row.keyword }}</span>
                  <span>{{ row.monthlyVolume }}</span>
                  <span>{{ row.projection }}</span>
                  <span :class="row.opportunity >= 70 ? 'delta-up' : ''">{{ row.opportunity.toFixed(1) }}</span>
                </button>
              </div>
            </article>

            <aside class="surface section-surface prompt-volume-rank">
              <div class="prompt-volume-kpi-row prompt-volume-kpi-row--compact">
                <div class="kpi-tile">
                  <span>关键词数</span>
                  <strong>{{ keywordProjectionSummary.count }}</strong>
                  <small>{{ projectionWindow === '90d' ? '近90天估算' : '近30天估算' }}</small>
                </div>
                <div class="kpi-tile">
                  <span>总需求量</span>
                  <strong>{{ keywordProjectionSummary.total.toLocaleString() }}</strong>
                  <small>高价值 {{ keywordProjectionSummary.highValue }} 词</small>
                </div>
              </div>

              <div class="volume-rank-table">
                <div class="volume-rank-table__head">
                  <span />
                  <span>关键词</span>
                  <span>占比</span>
                  <span>意图</span>
                  <span>动作</span>
                </div>
                <div
                  v-for="(row, index) in bulkKeywordAnalysisRows.slice(0, 8)"
                  :key="`bulk-keyword-${row.keyword}`"
                  class="volume-rank-table__row"
                >
                  <span>{{ index + 1 }}.</span>
                  <span class="volume-rank-table__platform">{{ row.keyword }}</span>
                  <span>{{ row.share.toFixed(1) }}%</span>
                  <span>{{ row.intent }}</span>
                  <span>{{ row.action }}</span>
                </div>
              </div>
            </aside>
          </div>
        </section>

        <section class="surface section-surface prompts-table-surface">
          <div class="surface-head surface-head--compact">
            <strong>主题与探针清单</strong>
          </div>

          <ProblemAnalysisPromptTableToolbar
            :prompt-search="props.promptSearch"
            :set-prompt-search="props.setPromptSearch"
            :prompt-group-mode="props.promptGroupMode"
            :prompt-group-mode-options="props.promptGroupModeOptions"
            :set-prompt-group-mode="props.setPromptGroupMode"
            :selected-prompt-columns="props.selectedPromptColumns"
            :prompt-column-options="props.promptColumnOptions"
            :toggle-prompt-column="props.togglePromptColumn"
          />

          <div class="prompts-table">
            <div
              class="prompts-table__head"
              :style="props.promptTableGridStyle"
            >
              <span class="prompts-table__topic">主题</span>
              <span
                v-for="column in props.promptVisibleColumns"
                :key="`prompt-head-${column.key}`"
              >{{ column.label }}</span>
            </div>

            <template
              v-for="group in filteredPromptTopicGroups"
              :key="`prompt-topic-group-${group.topic}`"
            >
              <div
                class="prompts-table__row prompts-table__row--topic"
                :style="props.promptTableGridStyle"
                @click="props.togglePromptTopic(group.topic)"
              >
                <button
                  type="button"
                  class="prompts-table__topic-trigger"
                >
                  <span
                    class="prompts-table__chevron"
                    :class="{ 'prompts-table__chevron--open': props.isPromptTopicExpanded(group.topic) }"
                  >›</span>
                  <span class="prompts-table__topic-text">{{ group.displayTopic }}</span>
                  <span class="prompts-table__topic-meta">{{ group.rows.length }} 条探针</span>
                </button>

                <div
                  v-for="column in props.promptVisibleColumns"
                  :key="`prompt-topic-summary-${group.topic}-${column.key}`"
                  class="prompts-cell"
                >
                  <span class="prompts-cell__value">{{ props.resolvePromptCellValue(group.summary, column.key) }}</span>
                  <span
                    v-if="props.resolvePromptCellDelta(group.summary, column.key)"
                    class="prompts-cell__delta"
                    :class="props.resolvePromptDeltaClass(group.summary, column.key)"
                  >
                    {{ props.resolvePromptCellDelta(group.summary, column.key) }}
                  </span>
                  <span
                    v-else
                    class="prompts-cell__delta prompts-cell__delta--na"
                  >-</span>
                </div>
              </div>

              <div
                v-if="props.isPromptTopicExpanded(group.topic)"
                class="prompts-table__group-body"
              >
                <div
                  v-for="row in group.rows"
                  :key="`prompt-topic-row-${row.id}`"
                  class="prompts-table__row prompts-table__row--prompt"
                  :style="props.promptTableGridStyle"
                  @click="openProbeDetail(group.displayTopic, row.prompt)"
                >
                  <div class="prompts-table__prompt-text">
                    {{ row.prompt }}
                    <small class="prompts-table__prompt-meta">
                      <span class="prompt-meta-pill">
                        {{ resolveProbeStatusLabel(resolveProbeSeed(group.displayTopic, row.prompt)?.status) }}
                      </span>
                      <span
                        v-for="tag in resolveProbeTags(resolveProbeSeed(group.displayTopic, row.prompt))"
                        :key="`prompt-tag-${row.id}-${tag.id}`"
                        class="prompt-meta-tag"
                      >
                        {{ tag.label }}
                      </span>
                    </small>
                  </div>

                  <div
                    v-for="column in props.promptVisibleColumns"
                    :key="`prompt-row-cell-${row.id}-${column.key}`"
                    class="prompts-cell"
                  >
                    <span class="prompts-cell__value">{{ props.resolvePromptCellValue(row, column.key) }}</span>
                    <span
                      v-if="props.resolvePromptCellDelta(row, column.key)"
                      class="prompts-cell__delta"
                      :class="props.resolvePromptDeltaClass(row, column.key)"
                    >
                      {{ props.resolvePromptCellDelta(row, column.key) }}
                    </span>
                    <span
                      v-else
                      class="prompts-cell__delta prompts-cell__delta--na"
                    >-</span>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </section>

        <section class="surface section-surface brand-loop-surface">
          <div class="surface-head">
            <div class="surface-head__title">
              <strong>品牌相关提示词闭环</strong>
              <p class="module-subcopy">
                识别“竞品已被引用而我方缺失”的高信号探针，并给出下一步动作。
              </p>
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
              <strong>{{ brandRelevantPromptSummary.coverage.toFixed(1) }}%</strong>
              <small>已被引用探针占比</small>
            </div>
            <div class="kpi-tile">
              <span>缺口词</span>
              <strong>{{ brandRelevantPromptSummary.gaps }}</strong>
              <small>竞品被引而我方缺失</small>
            </div>
            <div class="kpi-tile">
              <span>高价值机会</span>
              <strong>{{ brandRelevantPromptSummary.highOpportunity }}</strong>
              <small>机会分 ≥ 75</small>
            </div>
          </div>

          <div class="brand-loop-table">
            <div class="brand-loop-table__head">
              <span>主题</span>
              <span>探针</span>
              <span>状态</span>
              <span>当前被引对象</span>
              <span>机会分</span>
              <span>建议动作</span>
            </div>
            <div
              v-for="row in filteredBrandRelevantPromptRows.slice(0, 12)"
              :key="`brand-loop-${row.id}`"
              class="brand-loop-table__row"
            >
              <span>{{ row.topic }}</span>
              <span class="brand-loop-table__prompt">{{ row.prompt }}</span>
              <span
                class="brand-loop-status"
                :class="{
                  'brand-loop-status--ok': row.status === '已被引用',
                  'brand-loop-status--warn': row.status === '待强化',
                  'brand-loop-status--gap': row.status === '竞品被引（我方缺口）'
                }"
              >
                {{ row.status }}
              </span>
              <span>{{ row.citedTarget }}</span>
              <span :class="row.opportunity >= 75 ? 'delta-up' : ''">{{ row.opportunity }}</span>
              <span class="brand-loop-table__action">{{ row.action }}</span>
            </div>
          </div>
        </section>
      </template>

      <template v-else-if="activeView === 'intent'">
        <section class="prompt-volume-two-up">
          <article class="surface section-surface intent-surface">
            <div class="surface-head surface-head--compact">
              <strong>意图占比</strong>
            </div>
            <GeoLazyEChart
              :option="intentConversationOption"
              :height="308"
              empty-text="暂无对话份额数据"
            />
          </article>

          <aside class="surface section-surface intent-surface">
            <div class="surface-head surface-head--compact">
              <strong>子意图细分</strong>
            </div>
            <div class="intent-sub-table">
              <div class="intent-sub-table__head">
                <span>主意图</span>
                <span>子意图</span>
                <span>份额</span>
                <span>变化</span>
              </div>
              <div
                v-for="item in intentSubIntentRows"
                :key="`intent-sub-${item.key}`"
                class="intent-sub-table__row"
              >
                <span>{{ item.parentLabel }}</span>
                <span>{{ item.label }}</span>
                <span>{{ item.share.toFixed(1) }}%</span>
                <span :class="item.change >= 0 ? 'delta-up' : 'delta-down'">
                  {{ item.change >= 0 ? '+' : '' }}{{ item.change.toFixed(1) }}%
                </span>
              </div>
            </div>
          </aside>
        </section>

        <section class="surface section-surface">
          <div class="surface-head surface-head--compact">
            <strong>意图样本</strong>
          </div>
          <div class="intent-card-grid">
            <article
              v-for="item in intentStats"
              :key="`intent-card-${item.key}`"
              class="intent-card"
            >
              <div class="intent-card__head">
                <span>{{ item.label }}</span>
                <span>{{ toPercent(item.share) }}</span>
              </div>
              <ul class="intent-card__samples">
                <li
                  v-for="sample in item.examples"
                  :key="`intent-sample-${item.key}-${sample}`"
                >
                  {{ sample }}
                </li>
              </ul>
            </article>
          </div>
        </section>
      </template>

      <template v-else-if="activeView === 'demographic'">
        <section class="demographic-coming-soon">
          <div class="demographic-coming-soon__body">
            <section class="prompt-volume-two-up">
              <article class="surface section-surface demographic-surface">
                <div class="surface-head surface-head--compact">
                  <strong>年龄结构</strong>
                </div>
                <div
                  v-for="row in demographicAgeRows"
                  :key="`age-row-${row.label}`"
                  class="demographic-row"
                >
                  <span class="demographic-row__label">{{ row.label }}</span>
                  <div class="demographic-row__track">
                    <div
                      class="demographic-row__value demographic-row__value--age"
                      :style="{ width: `${Math.max(row.share, 3)}%` }"
                    />
                  </div>
                  <span class="demographic-row__share">{{ toPercent(row.share) }}</span>
                </div>
              </article>

              <aside class="surface section-surface demographic-surface">
                <div class="surface-head surface-head--compact">
                  <strong>消费层级</strong>
                </div>
                <div
                  v-for="row in demographicIncomeRows"
                  :key="`income-row-${row.label}`"
                  class="demographic-row"
                >
                  <span class="demographic-row__label">{{ row.label }}</span>
                  <div class="demographic-row__track">
                    <div
                      class="demographic-row__value demographic-row__value--income"
                      :style="{ width: `${Math.max(row.share, 3)}%` }"
                    />
                  </div>
                  <span class="demographic-row__share">{{ toPercent(row.share) }}</span>
                </div>
              </aside>
            </section>

            <section class="surface section-surface demographic-platform-surface">
              <div class="surface-head surface-head--compact">
                <strong>平台人群信号</strong>
              </div>
              <div class="demographic-platform-table">
                <div class="demographic-platform-table__head">
                  <span>平台</span>
                  <span>兴趣匹配</span>
                  <span>信号置信度</span>
                </div>
                <div
                  v-for="row in demographicPlatformRows"
                  :key="`demographic-platform-${row.platform}`"
                  class="demographic-platform-table__row"
                >
                  <span>{{ row.platform }}</span>
                  <span>{{ toPercent(row.score) }}</span>
                  <span>{{ toPercent(row.confidence) }}</span>
                </div>
              </div>
            </section>
          </div>

          <div class="demographic-coming-soon__mask">
            <span>敬请期待</span>
          </div>
        </section>
      </template>

      <template v-else-if="activeView === 'hierarchy'">
        <section class="surface section-surface hierarchy-surface">
          <div class="surface-head">
            <div class="surface-head__title">
              <strong>问题层级</strong>
              <p class="module-subcopy">
                按主题组织核心问法、决策问法与长尾扩展，定位最有价值的探针簇。
              </p>
            </div>
          </div>

          <div class="hierarchy-table">
            <div class="hierarchy-table__head">
              <span>主题</span>
              <span>需求指数</span>
            </div>
            <template
              v-for="topic in hierarchyTopics"
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
                <span>{{ topic.demandIndex }}</span>
              </button>
              <div
                v-if="isHierarchyTopicExpanded(topic.topic)"
                class="hierarchy-topic-body"
              >
                <article
                  v-for="cluster in topic.clusters"
                  :key="`hierarchy-cluster-${topic.topic}-${cluster.label}`"
                  class="hierarchy-cluster"
                >
                  <div class="hierarchy-cluster__head">
                    <strong>{{ cluster.label }}</strong>
                    <span>{{ cluster.count }} 条</span>
                  </div>
                  <ul>
                    <li
                      v-for="sample in cluster.samples"
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
      </template>

      <template v-else>
        <EngineQueryFanoutsTab
          :has-coverage="props.hasPromptCoverage"
          :selected-brand="props.selectedBrand"
          :selected-topics="props.selectedPromptTopics"
          :selected-platforms="props.selectedPlatforms"
          :prompt-topic-prompt-bank="promptTopicPromptBank"
        />
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

.surface-head--compact {
  min-height: 24px;
}

.module-subcopy {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: #6b7280;
}

.prompts-surface {
  border: none;
  gap: 8px;
}

.prompt-list-chips {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.prompt-head-side {
  min-width: 0;
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}

.inline-mini {
  min-height: 26px;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #ffffff;
  color: #4b5563;
  font-size: 11px;
  padding: 0 9px;
}

.inline-mini:hover {
  border-color: #111827;
  color: #111827;
}

.probe-filter-surface {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #ffffff;
  padding: 8px;
  display: grid;
  gap: 8px;
}

.prompt-list-chip {
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
}

.prompt-list-chip:hover {
  border-color: #9ca3af;
  color: #111827;
}

.prompt-list-chip--active {
  border-color: #111827;
  color: #111827;
}

.prompt-list-chip__count {
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

.prompt-volume-two-up {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  gap: 0;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #ffffff;
  overflow: hidden;
}

.prompt-volume-chart,
.prompt-volume-rank,
.prompts-table-surface,
.intent-surface,
.demographic-surface,
.demographic-platform-surface,
.hierarchy-surface {
  border: none;
}

.metric-card__head {
  min-height: 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.prompt-volume-kpi-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 2px;
}

.prompt-volume-kpi-row--compact {
  margin-bottom: 0;
}

.kpi-tile {
  min-height: 64px;
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  background: #f8fafc;
  padding: 8px 10px;
  display: grid;
  align-content: center;
  gap: 2px;
}

.kpi-tile span {
  font-size: 12px;
  color: #6b7280;
}

.kpi-tile strong {
  font-size: 28px;
  line-height: 1;
  color: #111827;
  font-weight: 700;
}

.kpi-tile small {
  font-size: 12px;
  color: #6b7280;
}

.volume-rank-table {
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  overflow: hidden;
}

.volume-rank-table__head,
.volume-rank-table__row {
  min-height: 40px;
  display: grid;
  align-items: center;
  grid-template-columns: 40px minmax(0, 1fr) 88px 74px 74px;
  gap: 6px;
  padding: 0 10px;
}

.volume-rank-table__head {
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
  color: #6b7280;
  font-size: 12px;
  font-weight: 600;
}

.volume-rank-table__row {
  border-bottom: 1px solid #e5e7eb;
  font-size: 12px;
  color: #111827;
}

.volume-rank-table__row:last-child {
  border-bottom: none;
}

.volume-rank-table__platform {
  font-weight: 600;
}

.prompt-volume-chart {
  padding: 10px;
  border-right: 1px solid #d1d5db;
}

.prompt-volume-rank {
  padding: 10px 10px 10px 12px;
  grid-template-rows: auto auto;
  align-content: start;
  align-items: start;
}

.prompt-volume-rank .surface-head {
  min-height: 28px;
  align-items: flex-start;
}

.prompt-volume-rank .surface-head strong {
  line-height: 1.3;
}

.prompt-volume-rank .volume-rank-table {
  margin-top: 0;
  align-self: start;
}

.keyword-depth-two-up {
  margin-top: 0;
}

.keyword-depth-two-up--merged {
  border: 1px solid #d1d5db;
}

.keyword-research-surface {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #ffffff;
  padding: 10px;
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

.keyword-research-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
}

.keyword-research-table__head,
.keyword-research-table__row {
  min-height: 38px;
  display: grid;
  align-items: center;
  grid-template-columns: minmax(0, 1fr) 100px 100px 88px;
  gap: 8px;
  padding: 0 10px;
  font-size: 12px;
}

.keyword-research-table__head {
  background: #f8fafc;
  border-bottom: 1px solid #d1d5db;
  color: #6b7280;
  font-weight: 600;
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

.prompts-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
  background: #ffffff;
}

.prompts-table__head,
.prompts-table__row {
  display: grid;
  align-items: center;
  min-height: 42px;
  column-gap: 0;
  padding: 0 10px;
}

.prompts-table__head {
  border-bottom: 1px solid #d1d5db;
  background: #f8fafc;
}

.prompts-table__head > span {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
}

.prompts-table__topic {
  padding-left: 2px;
}

.prompts-table__row {
  border-bottom: 1px solid #e5e7eb;
}

.prompts-table__row--topic {
  background: #f9fafb;
  cursor: pointer;
}

.prompts-table__row--topic:hover {
  background: #f3f4f6;
}

.prompts-table__row--prompt {
  min-height: 40px;
  background: #ffffff;
  cursor: pointer;
}

.prompts-table__row--prompt:hover {
  background: #fafafa;
}

.prompts-table__group-body .prompts-table__row:last-child {
  border-bottom: 1px solid #e5e7eb;
}

.prompts-table > .prompts-table__row:last-child {
  border-bottom: none;
}

.prompts-table__topic-trigger {
  border: none;
  background: transparent;
  padding: 0;
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #111827;
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
}

.prompts-table__chevron {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  color: #6b7280;
  transform: rotate(0deg);
  transition: transform 120ms ease;
}

.prompts-table__chevron--open {
  transform: rotate(90deg);
}

.prompts-table__topic-meta {
  color: #9ca3af;
  font-size: 11px;
  font-weight: 500;
}

.prompts-table__prompt-text {
  font-size: 12px;
  line-height: 1.5;
  color: #1f2937;
  padding-left: 22px;
  display: grid;
  gap: 4px;
}

.prompts-table__prompt-meta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.prompt-meta-pill {
  min-height: 18px;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  background: #f9fafb;
  color: #6b7280;
  font-size: 10px;
  padding: 0 6px;
  display: inline-flex;
  align-items: center;
}

.prompt-meta-tag {
  min-height: 18px;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  background: #ffffff;
  color: #4b5563;
  font-size: 10px;
  padding: 0 6px;
  display: inline-flex;
  align-items: center;
}

.prompts-cell {
  min-height: 40px;
  display: inline-flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-start;
  gap: 1px;
  font-size: 12px;
  color: #111827;
}

.prompts-cell__value {
  font-weight: 600;
}

.prompts-cell__delta {
  font-size: 11px;
}

.prompts-cell__delta--na {
  color: #9ca3af;
}

.intent-row {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr) 72px;
  align-items: center;
  gap: 10px;
}

.intent-row__meta {
  display: grid;
  gap: 2px;
  font-size: 12px;
  color: #6b7280;
}

.intent-row__label {
  font-weight: 600;
  color: #111827;
}

.intent-row__bar-track {
  min-height: 10px;
  border-radius: 999px;
  background: #f3f4f6;
  overflow: hidden;
}

.intent-row__bar-value {
  height: 10px;
  border-radius: 999px;
}

.intent-row__share {
  text-align: right;
  font-size: 12px;
  color: #374151;
}

.intent-card {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  padding: 8px 10px;
  display: grid;
  gap: 6px;
}

.intent-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.intent-sub-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
}

.intent-sub-table__head,
.intent-sub-table__row {
  min-height: 38px;
  display: grid;
  align-items: center;
  grid-template-columns: 120px minmax(0, 1fr) 88px 84px;
  gap: 8px;
  padding: 0 10px;
  font-size: 12px;
}

.intent-sub-table__head {
  background: #f8fafc;
  border-bottom: 1px solid #d1d5db;
  color: #6b7280;
  font-weight: 600;
}

.intent-sub-table__row {
  border-bottom: 1px solid #e5e7eb;
  color: #111827;
}

.intent-sub-table__row:last-child {
  border-bottom: none;
}

.intent-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  color: #111827;
}

.intent-card__samples {
  margin: 0;
  padding-left: 16px;
  display: grid;
  gap: 4px;
  font-size: 12px;
  line-height: 1.45;
  color: #4b5563;
}

.demographic-row {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr) 64px;
  align-items: center;
  gap: 10px;
}

.demographic-row__label {
  font-size: 12px;
  color: #374151;
}

.demographic-row__track {
  min-height: 10px;
  border-radius: 999px;
  background: #f3f4f6;
  overflow: hidden;
}

.demographic-row__value {
  height: 10px;
  border-radius: 999px;
}

.demographic-row__value--age {
  background: #2563eb;
}

.demographic-row__value--income {
  background: #0f766e;
}

.demographic-row__share {
  text-align: right;
  font-size: 12px;
  color: #374151;
}

.demographic-platform-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
}

.demographic-platform-table__head,
.demographic-platform-table__row {
  min-height: 40px;
  display: grid;
  align-items: center;
  grid-template-columns: minmax(0, 1fr) 130px 130px;
  padding: 0 10px;
  font-size: 12px;
}

.demographic-platform-table__head {
  background: #f8fafc;
  border-bottom: 1px solid #d1d5db;
  color: #6b7280;
  font-weight: 600;
}

.demographic-platform-table__row {
  border-bottom: 1px solid #e5e7eb;
  color: #111827;
}

.demographic-coming-soon {
  position: relative;
  display: grid;
  gap: 10px;
}

.demographic-coming-soon__body {
  display: grid;
  gap: 10px;
}

.demographic-coming-soon__mask {
  position: absolute;
  inset: 0;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.demographic-coming-soon__mask span {
  min-width: 124px;
  min-height: 44px;
  border: 1px solid rgba(17, 24, 39, 0.12);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  letter-spacing: 0.04em;
}

.demographic-platform-table__row:last-child {
  border-bottom: none;
}

.hierarchy-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
}

.hierarchy-table__head {
  min-height: 40px;
  display: grid;
  align-items: center;
  grid-template-columns: minmax(0, 1fr) 120px;
  padding: 0 10px;
  background: #f8fafc;
  border-bottom: 1px solid #d1d5db;
  font-size: 12px;
  color: #6b7280;
  font-weight: 600;
}

.hierarchy-topic-row {
  width: 100%;
  min-height: 40px;
  border: none;
  border-bottom: 1px solid #e5e7eb;
  background: #ffffff;
  color: #111827;
  padding: 0 10px;
  display: grid;
  align-items: center;
  grid-template-columns: minmax(0, 1fr) 120px;
  font-size: 12px;
  text-align: left;
}

.hierarchy-topic-row:hover {
  background: #f9fafb;
}

.hierarchy-topic-row__main {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.hierarchy-topic-body {
  border-bottom: 1px solid #e5e7eb;
  padding: 10px;
  background: #fcfcfd;
  display: grid;
  gap: 8px;
}

.hierarchy-cluster {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  padding: 8px 10px;
  display: grid;
  gap: 6px;
}

.hierarchy-cluster__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hierarchy-cluster__head strong {
  font-size: 12px;
  color: #111827;
}

.hierarchy-cluster__head span {
  font-size: 11px;
  color: #6b7280;
}

.hierarchy-cluster ul {
  margin: 0;
  padding-left: 16px;
  display: grid;
  gap: 4px;
  font-size: 12px;
  line-height: 1.45;
  color: #4b5563;
}

.brand-loop-surface {
  border: 1px solid #d1d5db;
}

.brand-loop-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
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
  width: 62px;
  min-height: 28px;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  padding: 0 6px;
  font-size: 12px;
  color: #111827;
}

.brand-loop-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
}

.brand-loop-table__head,
.brand-loop-table__row {
  min-height: 40px;
  display: grid;
  align-items: center;
  grid-template-columns: 126px minmax(0, 1.5fr) 152px 130px 84px minmax(0, 1fr);
  gap: 8px;
  padding: 0 10px;
  font-size: 12px;
}

.brand-loop-table__head {
  background: #f8fafc;
  border-bottom: 1px solid #d1d5db;
  color: #6b7280;
  font-weight: 600;
}

.brand-loop-table__row {
  border-bottom: 1px solid #e5e7eb;
  color: #111827;
}

.brand-loop-table__row:last-child {
  border-bottom: none;
}

.brand-loop-table__prompt,
.brand-loop-table__action {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.brand-loop-status {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  border: 1px solid #d1d5db;
  color: #4b5563;
  width: fit-content;
}

.brand-loop-status--ok {
  color: #047857;
  border-color: #04785733;
  background: #ecfdf5;
}

.brand-loop-status--warn {
  color: #b45309;
  border-color: #b4530933;
  background: #fffbeb;
}

.brand-loop-status--gap {
  color: #b91c1c;
  border-color: #b91c1c33;
  background: #fef2f2;
}

.delta-up {
  color: #047857;
}

.delta-down {
  color: #b91c1c;
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

@media (max-width: 1460px) {
  .prompt-volume-two-up {
    grid-template-columns: 1fr;
  }

  .prompt-volume-chart {
    border-right: none;
    border-bottom: 1px solid #d1d5db;
  }

  .prompts-tab-surface {
    width: 100%;
    overflow-x: auto;
  }

  .intent-card-grid {
    grid-template-columns: 1fr;
  }

  .keyword-tools-row {
    grid-template-columns: 1fr;
  }

  .brand-loop-table__head,
  .brand-loop-table__row {
    grid-template-columns: 116px minmax(0, 1.4fr) 144px 120px 74px minmax(0, 1fr);
  }
}
</style>
