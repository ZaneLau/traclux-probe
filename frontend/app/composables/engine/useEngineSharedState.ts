import { ref } from 'vue'
import type { Ref } from 'vue'
import type {
  ChartConfig,
  CompareWindow,
  CountryCode,
  EngineDimension,
  Granularity,
  LanguageCode,
  PlatformCadence,
  PlatformWindow,
  PromptColumnKey,
  PromptGroupMode,
  TimeWindow,
  TopicInsightCadence,
  TopicInsightMetric,
  TopicInsightOverlayPayload,
  TopicInsightTableMode,
  TopicInsightWindow
} from '~/types/engine'

interface EngineSharedState {
  selectedBrand: Ref<string>
  selectedBrandSpaceId: Ref<string>
  activeDimension: Ref<EngineDimension>
  selectedTimeWindow: Ref<TimeWindow>
  selectedCompare: Ref<CompareWindow>
  selectedGranularity: Ref<Granularity>
  selectedCountryCode: Ref<CountryCode>
  selectedLanguageCode: Ref<LanguageCode>
  selectedPlatformWindow: Ref<PlatformWindow>
  selectedPlatformCadence: Ref<PlatformCadence>
  selectedChartConfig: Ref<ChartConfig>
  selectedTopics: Ref<string[]>
  selectedPlatforms: Ref<string[]>
  workbenchRef: Ref<HTMLElement | null>
  workbenchScrolled: Ref<boolean>
  expandedTopicKey: Ref<string | null>
  topicInsightOverlay: Ref<TopicInsightOverlayPayload | null>
  topicInsightMetric: Ref<TopicInsightMetric>
  topicInsightTableMode: Ref<TopicInsightTableMode>
  topicInsightWindow: Ref<TopicInsightWindow>
  topicInsightCadence: Ref<TopicInsightCadence>
  topicInsightPlatformFilter: Ref<string>
  promptGroupMode: Ref<PromptGroupMode>
  selectedPromptColumns: Ref<PromptColumnKey[]>
  promptSearch: Ref<string>
  expandedPromptTopics: Ref<string[]>
  selectedPromptTopics: Ref<string[]>
  selectedCitationCategories: Ref<string[]>
  selectedCitationPromptSignals: Ref<string[]>
}

export function useEngineSharedState(): EngineSharedState {
  const { selectedBrand, selectedBrandSpaceId } = useWorkbenchSelectionState()
  const activeDimension = useState<EngineDimension>('engine-active-dimension', () => 'visibility')
  const selectedTimeWindow = useState<TimeWindow>('engine-selected-time-window', () => '30d')
  const selectedCompare = useState<CompareWindow>('engine-selected-compare', () => 'previous')
  const selectedGranularity = useState<Granularity>('engine-selected-granularity', () => 'week')
  const selectedCountryCode = useState<CountryCode>('engine-selected-country-code', () => 'CN')
  const selectedLanguageCode = useState<LanguageCode>('engine-selected-language-code', () => 'zh-CN')
  const selectedPlatformWindow = useState<PlatformWindow>('engine-selected-platform-window', () => 'today')
  const selectedPlatformCadence = useState<PlatformCadence>('engine-selected-platform-cadence', () => 'daily')
  const selectedChartConfig = useState<ChartConfig>('engine-selected-chart-config', () => 'score')
  const selectedTopics = useState<string[]>('engine-selected-topics', () => [])
  const selectedPlatforms = useState<string[]>('engine-selected-platforms', () => [])
  const expandedTopicKey = useState<string | null>('engine-expanded-topic-key', () => null)
  const topicInsightOverlay = useState<TopicInsightOverlayPayload | null>('engine-topic-insight-overlay', () => null)
  const topicInsightMetric = useState<TopicInsightMetric>('engine-topic-insight-metric', () => 'visibility')
  const topicInsightTableMode = useState<TopicInsightTableMode>('engine-topic-insight-table-mode', () => 'executions')
  const topicInsightWindow = useState<TopicInsightWindow>('engine-topic-insight-window', () => 'today')
  const topicInsightCadence = useState<TopicInsightCadence>('engine-topic-insight-cadence', () => 'daily')
  const topicInsightPlatformFilter = useState<string>('engine-topic-insight-platform-filter', () => 'all')
  const promptGroupMode = useState<PromptGroupMode>('engine-prompt-group-mode', () => 'topic')
  const selectedPromptColumns = useState<PromptColumnKey[]>(
    'engine-selected-prompt-columns',
    () => ['visibility-rank', 'visibility-score', 'average-position', 'citation-share']
  )
  const promptSearch = useState<string>('engine-prompt-search', () => '')
  const expandedPromptTopics = useState<string[]>('engine-expanded-prompt-topics', () => [])
  const selectedPromptTopics = useState<string[]>('engine-selected-prompt-topics', () => [])
  const selectedCitationCategories = useState<string[]>(
    'engine-selected-citation-categories',
    () => ['earned', 'social', 'owned', 'competition']
  )
  const selectedCitationPromptSignals = useState<string[]>(
    'engine-selected-citation-prompt-signals',
    () => ['visibility']
  )
  const workbenchScrolled = useState<boolean>('engine-workbench-scrolled', () => false)

  return {
    selectedBrand,
    selectedBrandSpaceId,
    activeDimension,
    selectedTimeWindow,
    selectedCompare,
    selectedGranularity,
    selectedCountryCode,
    selectedLanguageCode,
    selectedPlatformWindow,
    selectedPlatformCadence,
    selectedChartConfig,
    selectedTopics,
    selectedPlatforms,
    workbenchRef: ref<HTMLElement | null>(null),
    workbenchScrolled,
    expandedTopicKey,
    topicInsightOverlay,
    topicInsightMetric,
    topicInsightTableMode,
    topicInsightWindow,
    topicInsightCadence,
    topicInsightPlatformFilter,
    promptGroupMode,
    selectedPromptColumns,
    promptSearch,
    expandedPromptTopics,
    selectedPromptTopics,
    selectedCitationCategories,
    selectedCitationPromptSignals
  }
}
