import type {
  CompareWindow,
  CountryCode,
  EngineDataset,
  EngineModulePayload,
  Granularity,
  LanguageCode,
  PlatformCadence,
  PlatformWindow,
  TimeWindow
} from '~/types/engine'
import { cloneEngineDataset } from '~~/shared/engine/engineDatasetBase'
import {
  hydrateEngineDatasetShared,
  useEngineDatasetRuntimeState,
  useEngineDatasetSharedState
} from './engineDatasetShared'

export interface EngineFilterContext {
  brand: string
  topics: string[]
  platforms: string[]
  timeWindow: TimeWindow
  compare: CompareWindow
  granularity: Granularity
  countryCode: CountryCode
  languageCode: LanguageCode
  platformWindow: PlatformWindow
  platformCadence: PlatformCadence
  citationCategories: string[]
  citationPromptSignals: string[]
}

export interface EngineDataProvider {
  getDataset: () => Promise<EngineDataset>
  getOverview: (context: EngineFilterContext) => Promise<EngineModulePayload<'overview'>>
  getPrompts: (context: EngineFilterContext) => Promise<EngineModulePayload<'prompts'>>
  getPlatforms: (context: EngineFilterContext) => Promise<EngineModulePayload<'platforms'>>
  getSentiment: (context: EngineFilterContext) => Promise<EngineModulePayload<'sentiment'>>
  getCitations: (context: EngineFilterContext) => Promise<EngineModulePayload<'citations'>>
  getQueryFanouts: (context: EngineFilterContext) => Promise<EngineModulePayload<'queryFanouts'>>
  getTopicInsight: (context: EngineFilterContext) => Promise<EngineModulePayload<'topicInsight'>>
}

function buildPayload<K extends EngineModulePayload['module']>(
  module: K,
  context: EngineFilterContext
): EngineModulePayload<K> {
  return {
    module,
    filters: {
      brand: context.brand,
      topics: context.topics,
      platforms: context.platforms,
      timeWindow: context.timeWindow,
      compare: context.compare,
      granularity: context.granularity,
      countryCode: context.countryCode,
      languageCode: context.languageCode,
      platformWindow: context.platformWindow,
      platformCadence: context.platformCadence,
      citationCategories: context.citationCategories,
      citationPromptSignals: context.citationPromptSignals
    },
    dataset: hydrateEngineDatasetShared(cloneEngineDataset())
  }
}

const localEngineDataProvider: EngineDataProvider = {
  async getDataset() {
    return hydrateEngineDatasetShared(cloneEngineDataset())
  },
  async getOverview(context) {
    return buildPayload('overview', context)
  },
  async getPrompts(context) {
    return buildPayload('prompts', context)
  },
  async getPlatforms(context) {
    return buildPayload('platforms', context)
  },
  async getSentiment(context) {
    return buildPayload('sentiment', context)
  },
  async getCitations(context) {
    return buildPayload('citations', context)
  },
  async getQueryFanouts(context) {
    return buildPayload('queryFanouts', context)
  },
  async getTopicInsight(context) {
    return buildPayload('topicInsight', context)
  }
}

export function useEngineDataProvider() {
  const runtime = useEngineDatasetRuntimeState()
  useEngineDatasetSharedState()
  runtime.value.mode = 'local'
  runtime.value.error = null

  return {
    mode: 'local' as const,
    provider: localEngineDataProvider,
    error: null as string | null
  }
}
