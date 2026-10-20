import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import { getEngineDatasetShared } from './engineDatasetShared'
import { useEngineSharedState } from './useEngineSharedState'
import { buildLineSeries, buildPlatformMetricOption } from './workbench/charts'
import {
  buildSeed,
  buildTimeLabels,
  clamp,
  clampRank,
  formatSigned,
  resolveAssetShortLabel,
  resolveBrandBias,
  resolveDimensionBias,
  resolvePointCount,
  resolveScopeLabel,
  round1,
  toggleValue
} from './workbench/math'
import type {
  AssetRow,
  AvgPositionRow,
  BrandPromptTopicSeed,
  ChartConfig,
  CompareWindow,
  CountryCode,
  DimensionTab,
  Granularity,
  LanguageCode,
  OptionItem,
  PlatformCadence,
  PlatformMatrixRow,
  PlatformMetricKey,
  PlatformMetricPoint,
  PlatformMetricSection,
  PlatformWindow,
  PromptColumnKey,
  PromptColumnOption,
  PromptGroupMode,
  PromptMetricRow,
  PromptTopicGroup,
  ScopeOption,
  SovRow,
  TimeWindow,
  TopicDetailQuestionRow,
  TopicInsightExecutionRow,
  TopicVisibilityRankCell,
  TopicVisibilityRankRow,
  TrendPoint
} from '~/types/engine'

export function useEngineWorkbenchModel() {
  const dimensionTabs: DimensionTab[] = [
    { key: 'visibility', label: '可见度' },
    { key: 'prompts', label: '问题分析' },
    { key: 'platforms', label: '平台' },
    { key: 'sentiment', label: '情感' },
    { key: 'citations', label: '引用' },
    { key: 'query-fanouts', label: '问题扩展' }
  ]

  const engineDataset = getEngineDatasetShared()
  const brandOptions: OptionItem<string>[] = engineDataset.brandOptions

  const timeWindowOptions: OptionItem<TimeWindow>[] = [
    { key: '7d', label: '近 7 天' },
    { key: '30d', label: '近 30 天' },
    { key: '90d', label: '近 90 天' }
  ]

  const platformWindowOptions: OptionItem<PlatformWindow>[] = [
    { key: 'today', label: '今日' },
    { key: 'last7d', label: '近 7 天' }
  ]

  const platformCadenceOptions: OptionItem<PlatformCadence>[] = [
    { key: 'daily', label: '日' },
    { key: 'weekly', label: '周' }
  ]

  const compareOptions: OptionItem<CompareWindow>[] = [
    { key: 'none', label: '不对比' },
    { key: 'previous', label: '对比上期' },
    { key: 'yoy', label: '同比去年' }
  ]

  const granularityOptions: OptionItem<Granularity>[] = [
    { key: 'day', label: '日' },
    { key: 'week', label: '周' },
    { key: 'month', label: '月' }
  ]

  const chartConfigOptions: OptionItem<ChartConfig>[] = [
    { key: 'score', label: '得分' },
    { key: 'rank', label: '排名' },
    { key: 'dual', label: '双轴' }
  ]

  const countryOptions: OptionItem<CountryCode>[] = [
    { key: 'CN', label: '中国大陆' },
    { key: 'US', label: '美国' },
    { key: 'SG', label: '新加坡' }
  ]

  const languageOptions: OptionItem<LanguageCode>[] = [
    { key: 'zh-CN', label: '中文' },
    { key: 'en-US', label: 'English' }
  ]

  // 1.0 国内版：保留结构，不在界面显示
  const showCountryFilter = false
  const showLanguageFilter = false

  const platformScopeOptions: ScopeOption[] = engineDataset.platformScopeOptions

  const promptGroupModeOptions: OptionItem<PromptGroupMode>[] = [
    { key: 'topic', label: '按主题分组' },
    { key: 'flat', label: '不分组' }
  ]

  const promptColumnOptions: PromptColumnOption[] = [
    { key: 'visibility-rank', label: '可见度排名' },
    { key: 'visibility-score', label: '可见度得分' },
    { key: 'average-position', label: '平均位次' },
    { key: 'citation-share', label: '引用占比' }
  ]

  const brandKeywordSeeds: Record<string, string[]> = engineDataset.brandKeywordSeeds

  const platformOptions = platformScopeOptions.map(item => item.key)
  const rankingColorPalette = ['#1f2937', '#2563eb', '#0f766e', '#7c3aed', '#b45309', '#be185d', '#334155', '#0369a1']

  const competitorByBrand: Record<string, string[]> = engineDataset.competitorByBrand
  const brandPromptTopicSeeds: Record<string, BrandPromptTopicSeed[]> = engineDataset.brandPromptTopicSeeds

  const {
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
    workbenchRef,
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
  } = useEngineSharedState()

  const citationCategoryOptions = [
    { key: 'earned', label: '自然引用', icon: '▣' },
    { key: 'social', label: '社媒引用', icon: '◉' },
    { key: 'owned', label: '自有资产', icon: '▤' },
    { key: 'competition', label: '竞对引用', icon: '◌' }
  ] as const

  const citationPromptSignalOptions = [
    { key: 'visibility', label: '可见度信号' },
    { key: 'sentiment', label: '情感信号' }
  ] as const

  const topicScopeOptions = computed<ScopeOption[]>(() => {
    const seeds = brandPromptTopicSeeds[selectedBrand.value] || []
    return seeds.map((item, index) => ({
      key: item.topic,
      volume: item.prompts.length * 10 + index * 7 + 90
    }))
  })

  const topicOptions = computed(() => topicScopeOptions.value.map(item => item.key))
  const resolvedSelectedTopics = computed(() => {
    const available = new Set(topicOptions.value)
    const valid = selectedTopics.value.filter(topic => available.has(topic))
    if (valid.length) {
      return valid
    }
    return topicOptions.value.slice(0, 2)
  })

  const promptTopicPromptBank = computed<Record<string, string[]>>(() => {
    const brand = selectedBrand.value
    const topicSeeds = brandPromptTopicSeeds[brand]
    if (topicSeeds?.length) {
      return Object.fromEntries(topicSeeds.map(item => [item.topic, item.prompts]))
    }

    const keywords = brandKeywordSeeds[brand] || [brand]
    const primary = keywords[0] || brand
    const alias = keywords[1] || primary
    const competitors = competitorByBrand[brand] || []
    const mainCompetitor = competitors[0] || '竞品 A'
    const secondCompetitor = competitors[1] || '竞品 B'
    const thirdCompetitor = competitors[2] || '竞品 C'

    const topicBrandNarrative = '品牌认知与定位'
    const topicExperience = '使用体验与适配'
    const topicCost = '预算与投入回报'
    const topicCompetition = '竞品对比与替代风险'

    return {
      [topicBrandNarrative]: [
        `${primary} 的核心定位是什么`,
        `${primary} 在用户心智中的关键词有哪些`,
        `${alias} 的可信度通常如何被描述`,
        `${primary} 和 ${mainCompetitor} 在口碑叙事上有什么差异`,
        `${primary} 在模型问答里常被放在什么层级`,
        `${primary} 的优势说法哪些最常出现`,
        `${primary} 的弱点说法集中在哪些点`,
        `${primary} 的品牌背书是否充分`,
        `${primary} 在同类品牌里辨识度高吗`,
        `${primary} 在权威来源中被提及频率如何`
      ],
      [topicExperience]: [
        `${primary} 在高频使用场景下稳定吗`,
        `${primary} 的上手门槛高不高`,
        `${primary} 在复杂场景中的适配表现如何`,
        `${primary} 常见负面反馈多出现在什么环节`,
        `${primary} 哪些体验点最容易被正向提及`,
        `${primary} 与 ${secondCompetitor} 的体验差距主要在哪`,
        `${primary} 在新手和专业用户中的评价差异`,
        `${primary} 常见故障或阻塞点是什么`,
        `${primary} 的服务支持是否能覆盖关键问题`,
        `${primary} 在长期使用中的稳定性趋势如何`
      ],
      [topicCost]: [
        `${primary} 的预算投入主要花在哪`,
        `${primary} 与 ${mainCompetitor} 的综合成本谁更低`,
        `${primary} 在中长期投入回报是否合理`,
        `${primary} 的维护成本是否容易被低估`,
        `${primary} 在促销期与常规期价差有多大`,
        `${primary} 在不同购买路径中的成本差异`,
        `${primary} 成本结构对中小团队是否友好`,
        `${primary} 的投入回收周期大概多久`,
        `${primary} 在多品牌场景下的预算压力如何`,
        `${primary} 的高价部分是否对应真实增益`
      ],
      [topicCompetition]: [
        `${primary} 和 ${mainCompetitor} 的核心差距是什么`,
        `${primary} 替代 ${mainCompetitor} 的风险点有哪些`,
        `${primary} 与 ${secondCompetitor} 在用户评价上谁更稳`,
        `${primary} 与 ${thirdCompetitor} 的优势边界在哪里`,
        `${primary} 在竞品对比中最常输在哪些维度`,
        `${primary} 在竞品对比中最常赢在哪些维度`,
        `${primary} 在不同平台的竞品位次变化如何`,
        `${primary} 被替代的高风险场景有哪些`,
        `${primary} 的防御策略应优先补哪些点`,
        `${primary} 与竞品在引用来源质量上的差异`
      ]
    }
  })

  const hasCoverage = computed(() => resolvedSelectedTopics.value.length > 0 && selectedPlatforms.value.length > 0)
  const isPromptsTab = computed(() => activeDimension.value === 'prompts')
  const isPlatformsTab = computed(() => activeDimension.value === 'platforms')
  const isSentimentTab = computed(() => activeDimension.value === 'sentiment')
  const isCitationsTab = computed(() => activeDimension.value === 'citations')
  const isQueryFanoutsTab = computed(() => activeDimension.value === 'query-fanouts')
  const useCompactTimeFilter = computed(
    () => isPlatformsTab.value || isSentimentTab.value || isCitationsTab.value || isQueryFanoutsTab.value
  )
  const citationCategoryKeys = computed(() => citationCategoryOptions.map(item => item.key))
  const resolvedCitationCategories = computed(() => {
    const valid = selectedCitationCategories.value.filter(
      (item): item is (typeof citationCategoryOptions)[number]['key'] => citationCategoryKeys.value.includes(item as (typeof citationCategoryOptions)[number]['key'])
    )
    if (valid.length) {
      return valid
    }
    return ['earned']
  })
  const citationPromptSignalKeys = computed(() => citationPromptSignalOptions.map(item => item.key))
  const resolvedCitationPromptSignals = computed(() => {
    const valid = selectedCitationPromptSignals.value.filter(
      (item): item is (typeof citationPromptSignalOptions)[number]['key'] => citationPromptSignalKeys.value.includes(item as (typeof citationPromptSignalOptions)[number]['key'])
    )
    if (valid.length) {
      return valid
    }
    return ['visibility']
  })
  const promptTopicScopeOptions = computed<ScopeOption[]>(() => {
    const bank = promptTopicPromptBank.value
    const keys = Object.keys(bank)
    return keys.map((key, index) => ({
      key,
      volume: (bank[key]?.length || 0) * 10 + index * 7 + 90
    }))
  })
  const promptTopicKeys = computed(() => promptTopicScopeOptions.value.map(item => item.key))
  const resolvedSelectedPromptTopics = computed(() => {
    const available = new Set(promptTopicKeys.value)
    const valid = selectedPromptTopics.value.filter(topic => available.has(topic))
    if (valid.length) {
      return valid
    }
    return promptTopicKeys.value.slice(0, 2)
  })
  const hasPromptCoverage = computed(() => resolvedSelectedPromptTopics.value.length > 0 && selectedPlatforms.value.length > 0)
  const promptTopicScopeLabel = computed(() => resolveScopeLabel('主题', resolvedSelectedPromptTopics.value, promptTopicKeys.value))
  const topicScopeLabel = computed(() => resolveScopeLabel('主题', resolvedSelectedTopics.value, topicOptions.value))
  const platformScopeLabel = computed(() => resolveScopeLabel('平台', selectedPlatforms.value, platformOptions))

  const trendPoints = computed<TrendPoint[]>(() => {
    if (!hasCoverage.value) {
      return []
    }

    const pointCount = resolvePointCount(selectedTimeWindow.value, selectedGranularity.value)
    const labels = buildTimeLabels(pointCount, selectedGranularity.value)
    const seedBase = buildSeed([
      selectedBrand.value,
      activeDimension.value,
      selectedTimeWindow.value,
      selectedGranularity.value,
      resolvedSelectedTopics.value.join('|'),
      selectedPlatforms.value.join('|')
    ])

    const topicWeight = resolvedSelectedTopics.value.length * 1.8
    const platformWeight = selectedPlatforms.value.length * 1.4
    const dimensionBias = resolveDimensionBias(activeDimension.value)
    const brandBias = resolveBrandBias(selectedBrand.value)
    const windowBias = selectedTimeWindow.value === '7d' ? -1.6 : (selectedTimeWindow.value === '30d' ? 0.8 : 2.4)
    const slope = selectedGranularity.value === 'month' ? 1.2 : (selectedGranularity.value === 'week' ? 0.9 : 0.6)

    return labels.map((label, index) => {
      const wave = Math.sin((index + 1) * 0.9 + seedBase * 0.04) * 3.4
      const drift = index * slope
      const score = clamp(54 + brandBias + dimensionBias + topicWeight + platformWeight + windowBias + drift + wave, 18, 96)
      const peerScore = clamp(score - 4.2 + Math.cos((index + 1) * 0.7 + seedBase * 0.03) * 2.1, 12, 90)
      const rank = clampRank(Math.round(18 - score / 6 + Math.sin(index * 0.45 + seedBase * 0.02) * 1.2))
      const peerRank = clampRank(Math.round(rank + 1.5 + Math.cos(index * 0.35 + seedBase * 0.02) * 1.1))

      return {
        label,
        score: round1(score),
        peerScore: round1(peerScore),
        rank,
        peerRank
      }
    })
  })

  const compareTrendPoints = computed<TrendPoint[]>(() => {
    if (!hasCoverage.value || selectedCompare.value === 'none') {
      return []
    }

    const comparePenalty = selectedCompare.value === 'previous' ? 3.6 : 5.4

    return trendPoints.value.map((point, index) => ({
      label: point.label,
      score: round1(clamp(point.score - comparePenalty + Math.sin(index * 0.7) * 1.3, 8, 94)),
      peerScore: round1(clamp(point.peerScore - comparePenalty + Math.cos(index * 0.68) * 1.1, 8, 92)),
      rank: clampRank(Math.round(point.rank + comparePenalty / 2 - Math.cos(index * 0.5) * 0.9)),
      peerRank: clampRank(Math.round(point.peerRank + comparePenalty / 2 - Math.sin(index * 0.45) * 0.9))
    }))
  })

  const visibilityScore = computed(() => {
    const last = trendPoints.value.at(-1)
    return last ? round1(last.score) : null
  })

  const visibilityDelta = computed(() => {
    if (trendPoints.value.length < 2) {
      return null
    }
    return round1((trendPoints.value.at(-1)?.score || 0) - (trendPoints.value[0]?.score || 0))
  })

  const visibilityRank = computed(() => {
    const last = trendPoints.value.at(-1)
    return last ? last.rank : null
  })

  const rankDelta = computed(() => {
    if (trendPoints.value.length < 2) {
      return null
    }
    return (trendPoints.value[0]?.rank || 0) - (trendPoints.value.at(-1)?.rank || 0)
  })

  const assetRows = computed<AssetRow[]>(() => {
    if (!hasCoverage.value) {
      return []
    }

    const brand = selectedBrand.value
    const peers = competitorByBrand[brand] || []
    const assets = [brand, ...peers]

    const seedBase = buildSeed([
      brand,
      activeDimension.value,
      selectedTimeWindow.value,
      selectedCompare.value,
      selectedGranularity.value,
      resolvedSelectedTopics.value.join('|'),
      selectedPlatforms.value.join('|')
    ])

    const rows = assets.map((asset, index) => {
      const scoreCore = 52 + resolveDimensionBias(activeDimension.value) - index * 3.4
      const wave = Math.sin(seedBase * 0.03 + index * 1.12) * 4.6
      const topicLift = resolvedSelectedTopics.value.length * 1.6
      const platformLift = selectedPlatforms.value.length * 1.2
      const timeLift = selectedTimeWindow.value === '90d' ? 3 : (selectedTimeWindow.value === '30d' ? 1.8 : 0)
      const score = clamp(scoreCore + wave + topicLift + platformLift + timeLift, 12, 96)
      const change = round1(Math.cos(seedBase * 0.04 + index * 0.93) * 6.2)

      return {
        rank: 0,
        asset,
        score: round1(score),
        change
      }
    })

    return rows
      .sort((a, b) => b.score - a.score)
      .map((row, index) => ({
        ...row,
        rank: index + 1
      }))
  })

  const sovRows = computed<SovRow[]>(() => {
    const topRows = assetRows.value.slice(0, 5)
    const total = topRows.reduce((sum, row) => sum + row.score, 0)

    if (!topRows.length || total <= 0) {
      return []
    }

    return topRows.map((row, index) => {
      const share = round1((row.score / total) * 100)
      const compareShift = selectedCompare.value === 'none' ? 0 : (selectedCompare.value === 'previous' ? 2.4 : 3.8)
      const previousShare = round1(clamp(share - compareShift + Math.sin(index * 0.74) * 1.4, 2, 86))

      return {
        rank: index + 1,
        asset: row.asset,
        share,
        previousShare
      }
    })
  })

  const selectedSovRow = computed(() => sovRows.value.find(row => row.asset === selectedBrand.value) || null)
  const selectedSovShare = computed(() => selectedSovRow.value?.share ?? null)
  const rankingColumns = Array.from({ length: 10 }, (_, index) => index + 1)

  const averagePositionRows = computed<AvgPositionRow[]>(() => {
    if (!assetRows.value.length) {
      return []
    }

    const seedBase = buildSeed([
      selectedBrand.value,
      selectedTimeWindow.value,
      selectedCompare.value,
      selectedGranularity.value
    ])

    return assetRows.value
      .slice(0, 5)
      .map((row, index) => {
        const basePosition = 1.2 + index * 0.46 + Math.sin(seedBase * 0.0007 + index * 0.72) * 0.08
        const position = round1(clamp(basePosition, 1.1, 3.5))
        const previousPosition = round1(
          clamp(position + Math.cos(seedBase * 0.0009 + index * 0.68) * 0.18, 1.0, 3.5)
        )
        return {
          rank: 0,
          asset: row.asset,
          position,
          previousPosition
        }
      })
      .sort((a, b) => a.position - b.position)
      .map((row, index) => ({
        ...row,
        rank: index + 1
      }))
  })

  const selectedAveragePositionRow = computed(
    () => averagePositionRows.value.find(row => row.asset === selectedBrand.value) || null
  )
  const selectedAveragePosition = computed(() => selectedAveragePositionRow.value?.position ?? null)

  const promptVisibleColumns = computed<PromptColumnOption[]>(() => {
    const active = promptColumnOptions.filter(option => selectedPromptColumns.value.includes(option.key))
    return active.length ? active : [promptColumnOptions[0]].filter(Boolean) as PromptColumnOption[]
  })

  const promptTableGridStyle = computed(() => ({
    gridTemplateColumns: `minmax(420px, 2.8fr) repeat(${promptVisibleColumns.value.length}, minmax(124px, 1fr))`
  }))

  const promptTopicGroups = computed<PromptTopicGroup[]>(() => {
    if (!hasPromptCoverage.value) {
      return []
    }

    const search = promptSearch.value.trim().toLowerCase()
    const seedBase = buildSeed([
      selectedBrand.value,
      selectedTimeWindow.value,
      selectedCompare.value,
      selectedGranularity.value,
      selectedPlatforms.value.join('|')
    ])

    const grouped = resolvedSelectedPromptTopics.value.map((topic, topicIndex) => {
      const topicLabel = topic
      const promptTemplates = promptTopicPromptBank.value[topic] || []
      const rows = promptTemplates
        .map((prompt, promptIndex) => {
          const rowSeed = buildSeed([
            String(seedBase),
            topic,
            prompt,
            String(promptIndex),
            selectedBrand.value
          ])
          const visibilityRank = clampRank(1 + ((rowSeed + topicIndex * 11) % 8))
          const visibilityScore = round1(
            clamp(
              98 - visibilityRank * 5.4 + Math.sin(rowSeed * 0.0006 + promptIndex * 0.44) * 7.2
              + selectedPlatforms.value.length * 1.3 + resolveBrandBias(selectedBrand.value) * 0.18,
              28,
              100
            )
          )
          const visibilityDelta = round1(
            clamp(
              Math.cos(rowSeed * 0.0008 + promptIndex * 0.41)
              * (selectedCompare.value === 'none' ? 0 : (selectedCompare.value === 'previous' ? 8.6 : 12.4)),
              -19.8,
              19.8
            )
          )
          const averagePosition = round1(
            clamp(
              1 + visibilityRank * 0.18 + Math.sin(rowSeed * 0.0007 + promptIndex * 0.52) * 0.36,
              1.0,
              5.0
            )
          )
          const averageDelta = round1(
            clamp(
              Math.sin(rowSeed * 0.0009 + promptIndex * 0.39)
              * (selectedCompare.value === 'none' ? 0 : (selectedCompare.value === 'previous' ? 0.26 : 0.42)),
              -0.9,
              0.9
            )
          )
          const citationShare = round1(
            clamp(
              visibilityScore * 0.085 + Math.cos(rowSeed * 0.0005 + promptIndex * 0.34) * 2.2,
              0,
              26
            )
          )
          const citationDelta = round1(
            clamp(
              Math.cos(rowSeed * 0.00075 + promptIndex * 0.58)
              * (selectedCompare.value === 'none' ? 0 : (selectedCompare.value === 'previous' ? 2.8 : 4.6)),
              -8,
              8
            )
          )

          return {
            id: `${topic}-${promptIndex}`,
            topic: topicLabel,
            prompt,
            visibilityRank,
            visibilityScore,
            visibilityDelta,
            averagePosition,
            averageDelta,
            citationShare,
            citationDelta
          }
        })
        .filter((row) => {
          if (!search) {
            return true
          }
          return row.prompt.toLowerCase().includes(search) || topicLabel.toLowerCase().includes(search)
        })

      if (!rows.length) {
        return null
      }

      const summary: PromptMetricRow = {
        id: `${topic}-summary`,
        topic: topicLabel,
        prompt: `${topicLabel} 摘要`,
        visibilityRank: Math.max(1, Math.round(rows.reduce((sum, row) => sum + row.visibilityRank, 0) / rows.length)),
        visibilityScore: round1(rows.reduce((sum, row) => sum + row.visibilityScore, 0) / rows.length),
        visibilityDelta: round1(rows.reduce((sum, row) => sum + row.visibilityDelta, 0) / rows.length),
        averagePosition: round1(rows.reduce((sum, row) => sum + row.averagePosition, 0) / rows.length),
        averageDelta: round1(rows.reduce((sum, row) => sum + row.averageDelta, 0) / rows.length),
        citationShare: round1(rows.reduce((sum, row) => sum + row.citationShare, 0) / rows.length),
        citationDelta: round1(rows.reduce((sum, row) => sum + row.citationDelta, 0) / rows.length)
      }

      return {
        topic,
        displayTopic: topicLabel,
        rows,
        summary
      }
    }).filter(Boolean) as PromptTopicGroup[]

    if (promptGroupMode.value === 'flat') {
      const flattenedRows = grouped.flatMap(group =>
        group.rows.map(row => ({
          ...row,
          prompt: `[${group.displayTopic}] ${row.prompt}`
        }))
      )
      if (!flattenedRows.length) {
        return []
      }
      return [{
        topic: '全部探针',
        displayTopic: '全部探针',
        rows: flattenedRows,
        summary: {
          id: 'all-summary',
          topic: '全部探针',
          prompt: '全部探针摘要',
          visibilityRank: Math.max(1, Math.round(flattenedRows.reduce((sum, row) => sum + row.visibilityRank, 0) / flattenedRows.length)),
          visibilityScore: round1(flattenedRows.reduce((sum, row) => sum + row.visibilityScore, 0) / flattenedRows.length),
          visibilityDelta: round1(flattenedRows.reduce((sum, row) => sum + row.visibilityDelta, 0) / flattenedRows.length),
          averagePosition: round1(flattenedRows.reduce((sum, row) => sum + row.averagePosition, 0) / flattenedRows.length),
          averageDelta: round1(flattenedRows.reduce((sum, row) => sum + row.averageDelta, 0) / flattenedRows.length),
          citationShare: round1(flattenedRows.reduce((sum, row) => sum + row.citationShare, 0) / flattenedRows.length),
          citationDelta: round1(flattenedRows.reduce((sum, row) => sum + row.citationDelta, 0) / flattenedRows.length)
        }
      }]
    }

    return grouped
  })

  const promptTotalCount = computed(() =>
    promptTopicGroups.value.reduce((sum, group) => sum + group.rows.length, 0)
  )

  const activePromptGroupModeLabel = computed(
    () => promptGroupModeOptions.find(option => option.key === promptGroupMode.value)?.label || '按主题分组'
  )

  const resolvedExpandedPromptTopics = computed(() => {
    if (!promptTopicGroups.value.length) {
      return []
    }
    const validTopics = new Set(promptTopicGroups.value.map(group => group.topic))
    return expandedPromptTopics.value.filter(topic => validTopics.has(topic))
  })

  const topicAssetPool = computed(() => {
    const pool = [selectedBrand.value, ...(competitorByBrand[selectedBrand.value] || [])]
    return Array.from(new Set(pool)).slice(0, 6)
  })

  const topicVisibilityRows = computed<TopicVisibilityRankRow[]>(() => {
    const seedBase = buildSeed([
      selectedBrand.value,
      selectedTimeWindow.value,
      selectedCompare.value,
      selectedGranularity.value,
      resolvedSelectedTopics.value.join('|'),
      selectedPlatforms.value.join('|')
    ])

    const pool = topicAssetPool.value

    return topicOptions.value.map((topic, topicIndex) => {
      const rankedAssets = pool
        .map((asset, assetIndex) => ({
          asset,
          score: Math.sin(seedBase * 0.00037 + (topicIndex + 1) * 0.86 + (assetIndex + 1) * 0.71)
        }))
        .sort((a, b) => b.score - a.score)
        .map(item => item.asset)

      const effectiveDepth = Math.max(3, Math.min(rankedAssets.length, 4 + ((seedBase + topicIndex * 17) % 3)))
      const visibleRankedAssets = rankedAssets.slice(0, effectiveDepth)

      const ranks = rankingColumns.map((_, rankIndex) => {
        const asset = visibleRankedAssets[rankIndex]
        if (!asset) {
          return {
            asset: '--',
            shortLabel: '--',
            color: '#94a3b8'
          }
        }
        return {
          asset,
          shortLabel: resolveAssetShortLabel(asset),
          color: resolveAssetColor(asset)
        }
      })

      return {
        topic,
        ranks
      }
    })
  })

  const topicQuestionBank = computed<Record<string, string[]>>(() => {
    return Object.fromEntries(
      topicOptions.value.map(topic => [
        topic,
        [
          `${topic} 相关提问里，${selectedBrand.value} 是否稳定进入前二？`,
          `${topic} 场景下，用户更常被推荐哪个品牌？`,
          `${topic} 类问题中，${selectedBrand.value} 与竞品谁更常被首位提及？`,
          `${topic} 问法变化后，哪些品牌的可见位次波动最大？`,
          `${topic} 在不同模型平台下的回答排序是否一致？`
        ]
      ])
    )
  })

  const topicDetailRowsByTopic = computed<Record<string, TopicDetailQuestionRow[]>>(() => {
    const fallbackQuestions = topicQuestionBank.value[topicOptions.value[0] || ''] || []
    return Object.fromEntries(
      topicVisibilityRows.value.map((row, topicIndex) => {
        const baseAssets = row.ranks.filter(cell => cell.asset !== '--').map(cell => cell.asset)
        const questions = topicQuestionBank.value[row.topic] || fallbackQuestions

        const details = questions.map((question, questionIndex) => {
          const mutationSeed = buildSeed([row.topic, question, String(topicIndex), String(questionIndex), selectedBrand.value])
          const rotateOffset = baseAssets.length ? mutationSeed % baseAssets.length : 0
          const rotatedAssets = [...baseAssets.slice(rotateOffset), ...baseAssets.slice(0, rotateOffset)]
          const depthPenalty = mutationSeed % 3 === 0 ? 1 : 0
          const detailDepth = Math.max(3, Math.min(rotatedAssets.length, rotatedAssets.length - depthPenalty))

          return {
            id: `${row.topic}-${questionIndex}`,
            question,
            ranks: rankingColumns.map((_, rankIndex) => {
              const asset = rankIndex < detailDepth ? rotatedAssets[rankIndex] : '--'
              if (!asset || asset === '--') {
                return {
                  asset: '--',
                  shortLabel: '--',
                  color: '#94a3b8'
                }
              }
              return {
                asset,
                shortLabel: resolveAssetShortLabel(asset),
                color: resolveAssetColor(asset)
              }
            })
          }
        })
        return [row.topic, details] as const
      })
    )
  })

  const topicInsightMetricLabel = computed(() => {
    if (topicInsightMetric.value === 'visibility') {
      return '可见度得分'
    }
    if (topicInsightMetric.value === 'avg-position') {
      return '平均位次'
    }
    return '引用占比'
  })

  const topicInsightMetricValue = computed(() => {
    if (!topicInsightOverlay.value) {
      return '--'
    }
    if (topicInsightMetric.value === 'visibility') {
      return `${topicInsightOverlay.value.visibility.toFixed(1)}%`
    }
    if (topicInsightMetric.value === 'avg-position') {
      return `${round1(clamp(1 + topicInsightOverlay.value.rank * 0.34, 1.0, 5.0)).toFixed(1)}`
    }
    return `${topicInsightOverlay.value.citationShare.toFixed(1)}%`
  })

  const topicInsightExecutionCount = computed(() => {
    if (!topicInsightOverlay.value) {
      return 0
    }
    const seed = buildSeed([topicInsightOverlay.value.asset, topicInsightOverlay.value.question, topicInsightWindow.value])
    return 1 + (seed % 6)
  })

  const topicInsightTrendSeries = computed(() => {
    if (!topicInsightOverlay.value) {
      return { labels: [] as string[], values: [] as number[] }
    }
    const overlay = topicInsightOverlay.value

    const count = topicInsightWindow.value === 'today' ? 7 : 12
    const labels
      = topicInsightCadence.value === 'daily'
        ? Array.from({ length: count }, (_, index) => `03/${`${index + 1}`.padStart(2, '0')}`)
        : Array.from({ length: count }, (_, index) => `W${index + 1}`)

    const seed = buildSeed([
      overlay.asset,
      overlay.question,
      topicInsightMetric.value,
      topicInsightWindow.value,
      topicInsightCadence.value
    ])

    const values = labels.map((_, index) => {
      if (topicInsightMetric.value === 'avg-position') {
        const base = 1 + overlay.rank * 0.3 + index * 0.05
        return round1(clamp(base + Math.sin(seed * 0.0006 + index * 0.42) * 0.24, 1.0, 5.0))
      }
      const base
        = topicInsightMetric.value === 'visibility'
          ? overlay.visibility
          : overlay.citationShare * 2.3 + 32
      return round1(clamp(base + Math.sin(seed * 0.0008 + index * 0.58) * 4.8, 6, 100))
    })

    return { labels, values }
  })

  const topicInsightTrendOption = computed<EChartsOption | null>(() => {
    if (!topicInsightOverlay.value || !topicInsightTrendSeries.value.labels.length) {
      return null
    }

    const isPositionMetric = topicInsightMetric.value === 'avg-position'

    return {
      backgroundColor: 'transparent',
      grid: {
        left: 36,
        right: 20,
        top: 24,
        bottom: 28,
        containLabel: true
      },
      tooltip: {
        trigger: 'axis',
        backgroundColor: '#111827',
        borderColor: '#1f2937',
        textStyle: {
          color: '#f9fafb'
        }
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: topicInsightTrendSeries.value.labels,
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
        min: isPositionMetric ? 0 : 0,
        max: isPositionMetric ? 5 : 100,
        axisLabel: {
          color: '#6b7280',
          fontSize: 11,
          formatter: isPositionMetric ? '{value}' : '{value}%'
        },
        splitLine: {
          lineStyle: {
            color: '#f3f4f6'
          }
        }
      },
      series: [
        {
          name: topicInsightMetricLabel.value,
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 5,
          lineStyle: {
            width: 2,
            color: '#111827'
          },
          itemStyle: {
            color: '#111827'
          },
          areaStyle: {
            color: 'rgba(17, 24, 39, 0.05)'
          },
          data: topicInsightTrendSeries.value.values
        }
      ]
    }
  })

  const topicInsightPlatformBars = computed(() => {
    if (!topicInsightOverlay.value) {
      return []
    }
    const overlay = topicInsightOverlay.value

    const availablePlatforms = platformOptions
    const platforms = topicInsightPlatformFilter.value === 'all'
      ? availablePlatforms
      : availablePlatforms.filter(platform => platform === topicInsightPlatformFilter.value)

    const seed = buildSeed([
      overlay.asset,
      overlay.topic,
      topicInsightMetric.value,
      platforms.join('|')
    ])

    return platforms.map((platform, index) => {
      if (topicInsightMetric.value === 'avg-position') {
        const value = round1(clamp(1.1 + overlay.rank * 0.24 + index * 0.26 + Math.sin(seed * 0.0007 + index) * 0.17, 1.0, 5.0))
        return { platform, value }
      }
      const base = topicInsightMetric.value === 'visibility' ? overlay.visibility : overlay.citationShare * 2.1 + 34
      const value = round1(clamp(base - index * 2.4 + Math.cos(seed * 0.0008 + index) * 4.2, 8, 100))
      return { platform, value }
    })
  })

  const topicInsightPlatformOption = computed<EChartsOption | null>(() => {
    if (!topicInsightOverlay.value || !topicInsightPlatformBars.value.length) {
      return null
    }

    const isPositionMetric = topicInsightMetric.value === 'avg-position'

    return {
      backgroundColor: 'transparent',
      grid: {
        left: 34,
        right: 16,
        top: 28,
        bottom: 28,
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
        }
      },
      xAxis: {
        type: 'category',
        data: topicInsightPlatformBars.value.map(item => item.platform),
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
        max: isPositionMetric ? 5 : 110,
        axisLabel: {
          color: '#6b7280',
          fontSize: 11,
          formatter: isPositionMetric ? '{value}' : '{value}%'
        },
        splitLine: {
          lineStyle: {
            color: '#f3f4f6'
          }
        }
      },
      series: [
        {
          name: topicInsightMetricLabel.value,
          type: 'bar',
          barWidth: 32,
          itemStyle: {
            color: '#111827',
            borderRadius: [2, 2, 0, 0]
          },
          data: topicInsightPlatformBars.value.map(item => item.value)
        }
      ]
    }
  })

  const topicInsightTableRows = computed<TopicInsightExecutionRow[]>(() => {
    if (!topicInsightOverlay.value) {
      return []
    }
    const overlay = topicInsightOverlay.value

    const rowCount = topicInsightTableMode.value === 'executions' ? 8 : 6
    const allPlatforms = platformOptions
    const seed = buildSeed([
      overlay.asset,
      overlay.question,
      topicInsightTableMode.value
    ])

    return Array.from({ length: rowCount }, (_, index) => {
      const position = Math.max(1, Math.min(10, overlay.rank + Math.round(Math.sin(seed * 0.0006 + index * 0.44) * 2)))
      return {
        id: `${overlay.topic}-${topicInsightTableMode.value}-${index}`,
        date: `2026/04/${`${index + 1}`.padStart(2, '0')}`,
        mentioned: topicInsightTableMode.value === 'executions' ? index % 3 !== 0 : index % 2 === 0,
        position,
        allMentions: allPlatforms.slice(0, 2 + (index % Math.min(2, allPlatforms.length))).join(' / '),
        response:
          topicInsightTableMode.value === 'executions'
            ? `针对「${overlay.topic}」问题，优先提及 ${overlay.asset} 并给出简短理由。`
            : `引用来源覆盖 ${overlay.asset} 的产品页、评测页与第三方站点。`,
        platform: allPlatforms[index % allPlatforms.length] || 'ChatGPT'
      }
    })
  })

  const trendOption = computed<EChartsOption | null>(() => {
    if (!trendPoints.value.length) {
      return null
    }

    const labels = trendPoints.value.map(point => point.label)
    const hasCompare = compareTrendPoints.value.length > 0

    const base: EChartsOption = {
      backgroundColor: 'transparent',
      grid: {
        left: 34,
        right: 20,
        top: 36,
        bottom: 24,
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
        }
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: labels,
        axisLabel: {
          color: '#6b7280',
          fontSize: 11
        },
        axisLine: {
          lineStyle: {
            color: '#e5e7eb'
          }
        }
      }
    }

    if (selectedChartConfig.value === 'score') {
      return {
        ...base,
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
          buildLineSeries('本品牌得分', '#111827', trendPoints.value.map(point => point.score)),
          buildLineSeries('竞品均值', '#2563eb', trendPoints.value.map(point => point.peerScore)),
          ...(hasCompare
            ? [
                buildLineSeries('本品牌得分（对比）', '#9ca3af', compareTrendPoints.value.map(point => point.score), 'dashed')
              ]
            : [])
        ]
      }
    }

    if (selectedChartConfig.value === 'rank') {
      return {
        ...base,
        yAxis: {
          type: 'value',
          min: 1,
          max: 20,
          inverse: true,
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
          buildLineSeries('本品牌排名', '#111827', trendPoints.value.map(point => point.rank)),
          buildLineSeries('竞品中位排名', '#2563eb', trendPoints.value.map(point => point.peerRank)),
          ...(hasCompare
            ? [
                buildLineSeries('本品牌排名（对比）', '#9ca3af', compareTrendPoints.value.map(point => point.rank), 'dashed')
              ]
            : [])
        ]
      }
    }

    return {
      ...base,
      yAxis: [
        {
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
        {
          type: 'value',
          min: 1,
          max: 20,
          inverse: true,
          axisLabel: {
            color: '#6b7280',
            fontSize: 11
          },
          splitLine: {
            show: false
          }
        }
      ],
      series: [
        buildLineSeries('得分', '#111827', trendPoints.value.map(point => point.score), 'solid', 0),
        buildLineSeries('排名', '#f97316', trendPoints.value.map(point => point.rank), 'solid', 1),
        ...(hasCompare
          ? [
              buildLineSeries('得分（对比）', '#9ca3af', compareTrendPoints.value.map(point => point.score), 'dashed', 0)
            ]
          : [])
      ]
    }
  })

  const sovOption = computed<EChartsOption | null>(() => {
    if (!sovRows.value.length) {
      return null
    }
    const palette = ['#22c55e', '#06b6d4', '#8b5cf6', '#ef4444', '#ec4899', '#9ca3af']

    return {
      backgroundColor: 'transparent',
      color: palette,
      legend: {
        bottom: 0,
        left: 'center',
        itemWidth: 10,
        itemHeight: 10,
        icon: 'circle',
        textStyle: {
          color: '#6b7280',
          fontSize: 11
        }
      },
      tooltip: {
        trigger: 'item',
        backgroundColor: '#111827',
        borderColor: '#1f2937',
        textStyle: {
          color: '#f9fafb'
        },
        formatter: '{b}<br/>{c}% · {d}%'
      },
      series: [
        {
          name: '声量占比',
          type: 'pie',
          radius: ['56%', '74%'],
          center: ['50%', '46%'],
          startAngle: 90,
          avoidLabelOverlap: true,
          label: {
            show: false
          },
          labelLine: {
            show: false
          },
          itemStyle: {
            borderColor: '#ffffff',
            borderWidth: 6,
            borderRadius: 5
          },
          data: sovRows.value.map(row => ({
            name: row.asset,
            value: row.share
          }))
        }
      ]
    }
  })

  const averagePositionOption = computed<EChartsOption | null>(() => {
    if (!averagePositionRows.value.length) {
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
        formatter: '{b}<br/>平均位次：{c}'
      },
      xAxis: {
        type: 'category',
        data: averagePositionRows.value.map(row => row.asset),
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
        max: 3.5,
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
          data: averagePositionRows.value.map(row => row.position)
        }
      ]
    }
  })

  const activePlatformKeys = computed(() =>
    selectedPlatforms.value.length ? selectedPlatforms.value : platformOptions.slice(0, 1)
  )

  const platformMatrixGridStyle = computed(() => ({
    gridTemplateColumns: `minmax(260px, 1.8fr) repeat(${activePlatformKeys.value.length}, minmax(0, 1fr))`
  }))

  const platformMatrixRows = computed<PlatformMatrixRow[]>(() => {
    if (!hasCoverage.value) {
      return []
    }

    const assets = Array.from(new Set([
      ...(competitorByBrand[selectedBrand.value] || []),
      selectedBrand.value
    ]))

    const seedBase = buildSeed([
      selectedBrand.value,
      selectedPlatformWindow.value,
      selectedPlatformCadence.value,
      resolvedSelectedTopics.value.join('|'),
      activePlatformKeys.value.join('|')
    ])

    const rows = assets.map((asset, assetIndex) => {
      const values = Object.fromEntries(
        activePlatformKeys.value.map((platform, platformIndex) => {
          const base = 74 - assetIndex * 8.2 - platformIndex * 3.6 + Math.sin(seedBase * 0.0008 + assetIndex * 0.62 + platformIndex * 0.37) * 6.2
          const selectedPenalty = asset === selectedBrand.value ? 9.4 : 0
          const value = round1(clamp(base - selectedPenalty, 4, 88))
          return [platform, value]
        })
      )

      return {
        asset,
        isSelected: asset === selectedBrand.value,
        values
      }
    })

    const orderPlatform = activePlatformKeys.value[0] || platformOptions[0] || 'ChatGPT'
    return rows.sort((a, b) => (b.values[orderPlatform] || 0) - (a.values[orderPlatform] || 0))
  })

  const platformMetricRows = computed<PlatformMetricPoint[]>(() => {
    if (!hasCoverage.value) {
      return []
    }

    const seedBase = buildSeed([
      selectedBrand.value,
      selectedPlatformWindow.value,
      selectedPlatformCadence.value,
      selectedCompare.value,
      resolvedSelectedTopics.value.join('|'),
      activePlatformKeys.value.join('|')
    ])

    const showDelta = selectedPlatformWindow.value !== 'today'

    return activePlatformKeys.value.map((platform, index) => {
      const platformIndex = Math.max(0, platformOptions.indexOf(platform))
      const base = 57 + resolveBrandBias(selectedBrand.value) * 0.95 + resolvedSelectedTopics.value.length * 1.4
      const visibility = round1(clamp(base - platformIndex * 2.8 + Math.sin(seedBase * 0.0007 + index * 0.53) * 5.4, 18, 84))
      const shareOfVoice = round1(clamp(visibility * 0.31 + Math.cos(seedBase * 0.0008 + index * 0.62) * 2.3, 3, 34))
      const averagePosition = round1(clamp(3.28 - visibility / 38 + Math.sin(seedBase * 0.0005 + index * 0.44) * 0.17, 1.0, 4.2))
      const citationShare = round1(clamp(visibility * 0.14 + Math.cos(seedBase * 0.0006 + index * 0.51) * 1.2, 1.2, 19))
      const sentiment = round1(clamp(36 + visibility * 0.39 + Math.sin(seedBase * 0.0009 + index * 0.71) * 4.3, 18, 90))

      const deltaScale = selectedCompare.value === 'yoy' ? 1.5 : 1

      return {
        platform,
        visibility,
        visibilityDelta: showDelta ? round1(clamp(Math.cos(seedBase * 0.001 + index * 0.73) * 5.4 * deltaScale, -14, 14)) : null,
        shareOfVoice,
        shareOfVoiceDelta: showDelta ? round1(clamp(Math.sin(seedBase * 0.00095 + index * 0.64) * 3.1 * deltaScale, -9, 9)) : null,
        averagePosition,
        averagePositionDelta: showDelta ? round1(clamp(Math.cos(seedBase * 0.0011 + index * 0.57) * 0.28 * deltaScale, -0.9, 0.9)) : null,
        citationShare,
        citationShareDelta: showDelta ? round1(clamp(Math.sin(seedBase * 0.00086 + index * 0.48) * 2.4 * deltaScale, -8, 8)) : null,
        sentiment,
        sentimentDelta: showDelta ? round1(clamp(Math.cos(seedBase * 0.00107 + index * 0.59) * 4.9 * deltaScale, -13, 13)) : null
      }
    })
  })

  const platformMetricSections = computed<PlatformMetricSection[]>(() => {
    const specs = [
      {
        key: 'visibility',
        title: '平台可见度得分',
        subtitle: `${selectedBrand.value} 在各模型平台回答中的出现频率`,
        chartLabel: '可见度得分',
        rankTitle: '平台可见度排名',
        rankColumnLabel: '可见度得分',
        isPercent: true,
        lowerIsBetter: false,
        axisMax: 66
      },
      {
        key: 'share-of-voice',
        title: '平台声量占比',
        subtitle: `${selectedBrand.value} 在各模型平台相对竞品的提及占比`,
        chartLabel: '声量占比',
        rankTitle: '平台声量排名',
        rankColumnLabel: '声量占比',
        isPercent: true,
        lowerIsBetter: false,
        axisMax: 22
      },
      {
        key: 'average-position',
        title: '平台平均位次',
        subtitle: `${selectedBrand.value} 在各模型平台回答中的平均出现位置`,
        chartLabel: '平均位次',
        rankTitle: '平台位次排名',
        rankColumnLabel: '平均位次',
        isPercent: false,
        lowerIsBetter: true,
        axisMax: 1.7
      },
      {
        key: 'citation-share',
        title: '平台引用占比',
        subtitle: `${selectedBrand.value} 在各模型平台回答中的引用来源占比`,
        chartLabel: '引用占比',
        rankTitle: '平台引用排名',
        rankColumnLabel: '引用占比',
        isPercent: true,
        lowerIsBetter: false,
        axisMax: 9.9
      },
      {
        key: 'sentiment',
        title: '平台情感倾向',
        subtitle: `各模型平台回答中对 ${selectedBrand.value} 的正向程度`,
        chartLabel: '情感倾向',
        rankTitle: '平台情感排名',
        rankColumnLabel: '情感倾向',
        isPercent: true,
        lowerIsBetter: false,
        axisMax: 64.9
      }
    ] as const

    return specs.map((spec) => {
      const rows = platformMetricRows.value.map((row) => {
        const value = resolvePlatformMetricValue(row, spec.key)
        const delta = resolvePlatformMetricDelta(row, spec.key)
        return {
          rank: 0,
          platform: row.platform,
          value,
          delta
        }
      })

      const sorted = rows
        .slice()
        .sort((a, b) => (spec.lowerIsBetter ? a.value - b.value : b.value - a.value))
        .map((row, index) => ({
          ...row,
          rank: index + 1
        }))

      return {
        ...spec,
        rows: sorted,
        chartOption: buildPlatformMetricOption(spec, sorted)
      }
    })
  })

  function isTabActive(tab: DimensionTab) {
    return activeDimension.value === tab.key
  }

  function toggleTopic(topic: string) {
    const base = selectedTopics.value.length
      ? selectedTopics.value
      : resolvedSelectedTopics.value
    selectedTopics.value = toggleValue(base, topic)
  }

  function togglePromptTopicScope(topic: string) {
    const base = selectedPromptTopics.value.length
      ? selectedPromptTopics.value
      : resolvedSelectedPromptTopics.value
    selectedPromptTopics.value = toggleValue(base, topic)
  }

  function togglePlatform(platform: string) {
    selectedPlatforms.value = toggleValue(selectedPlatforms.value, platform)
  }

  function togglePromptColumn(column: PromptColumnKey) {
    if (selectedPromptColumns.value.includes(column) && selectedPromptColumns.value.length === 1) {
      return
    }
    selectedPromptColumns.value = toggleValue(selectedPromptColumns.value, column) as PromptColumnKey[]
  }

  function toggleCitationCategory(key: string) {
    if (resolvedCitationCategories.value.includes(key) && resolvedCitationCategories.value.length === 1) {
      return
    }
    selectedCitationCategories.value = toggleValue(resolvedCitationCategories.value, key)
  }

  function toggleCitationPromptSignal(key: string) {
    if (resolvedCitationPromptSignals.value.includes(key) && resolvedCitationPromptSignals.value.length === 1) {
      return
    }
    selectedCitationPromptSignals.value = toggleValue(resolvedCitationPromptSignals.value, key)
  }

  function resetCitationPromptSignals() {
    selectedCitationPromptSignals.value = ['visibility']
  }

  function isPromptTopicExpanded(topic: string) {
    return resolvedExpandedPromptTopics.value.includes(topic)
  }

  function togglePromptTopic(topic: string) {
    expandedPromptTopics.value = toggleValue(resolvedExpandedPromptTopics.value, topic)
  }

  function resolvePromptCellValue(row: PromptMetricRow, key: PromptColumnKey) {
    if (key === 'visibility-rank') {
      if (!Number.isFinite(row.visibilityRank)) {
        return '—'
      }
      return `#${row.visibilityRank}`
    }
    if (key === 'visibility-score') {
      if (!Number.isFinite(row.visibilityScore)) {
        return '—'
      }
      return `${row.visibilityScore.toFixed(1)}%`
    }
    if (key === 'average-position') {
      if (!Number.isFinite(row.averagePosition)) {
        return '—'
      }
      return row.averagePosition.toFixed(1)
    }
    if (!Number.isFinite(row.citationShare)) {
      return '—'
    }
    return `${row.citationShare.toFixed(1)}%`
  }

  function resolvePromptCellDelta(row: PromptMetricRow, key: PromptColumnKey) {
    if (key === 'visibility-rank') {
      return null
    }
    if (key === 'visibility-score') {
      if (!Number.isFinite(row.visibilityScore) || !Number.isFinite(row.visibilityDelta)) {
        return null
      }
      if (Math.abs(row.visibilityDelta) < 0.05) {
        return null
      }
      return formatSigned(row.visibilityDelta, '%')
    }
    if (key === 'average-position') {
      if (!Number.isFinite(row.averagePosition) || !Number.isFinite(row.averageDelta)) {
        return null
      }
      if (Math.abs(row.averageDelta) < 0.05) {
        return null
      }
      return formatSigned(row.averageDelta)
    }
    if (!Number.isFinite(row.citationShare) || !Number.isFinite(row.citationDelta)) {
      return null
    }
    if (Math.abs(row.citationDelta) < 0.05) {
      return null
    }
    return formatSigned(row.citationDelta, '%')
  }

  function resolvePromptDeltaClass(row: PromptMetricRow, key: PromptColumnKey) {
    if (key === 'visibility-rank') {
      return ''
    }
    const delta = key === 'visibility-score'
      ? row.visibilityDelta
      : (key === 'average-position' ? row.averageDelta : row.citationDelta)
    if (!Number.isFinite(delta)) {
      return ''
    }
    if (Math.abs(delta) < 0.05) {
      return ''
    }
    return delta >= 0 ? 'delta-up' : 'delta-down'
  }

  function resolvePlatformMetricValue(row: PlatformMetricPoint, key: PlatformMetricKey) {
    if (key === 'visibility') {
      return row.visibility
    }
    if (key === 'share-of-voice') {
      return row.shareOfVoice
    }
    if (key === 'average-position') {
      return row.averagePosition
    }
    if (key === 'citation-share') {
      return row.citationShare
    }
    return row.sentiment
  }

  function resolvePlatformMetricDelta(row: PlatformMetricPoint, key: PlatformMetricKey) {
    if (key === 'visibility') {
      return row.visibilityDelta
    }
    if (key === 'share-of-voice') {
      return row.shareOfVoiceDelta
    }
    if (key === 'average-position') {
      return row.averagePositionDelta
    }
    if (key === 'citation-share') {
      return row.citationShareDelta
    }
    return row.sentimentDelta
  }

  function formatMetricValue(value: number, isPercent: boolean) {
    if (isPercent) {
      const rounded = Math.abs(value - Math.round(value)) < 0.05 ? Math.round(value).toString() : value.toFixed(1)
      return `${rounded}%`
    }
    return value.toFixed(1)
  }

  function formatMetricDelta(value: number | null, isPercent: boolean) {
    if (value == null) {
      return '-'
    }
    if (isPercent) {
      const rounded = Math.abs(value - Math.round(value)) < 0.05 ? Math.round(value).toString() : value.toFixed(1)
      return `${value > 0 ? '+' : ''}${rounded}%`
    }
    return `${value > 0 ? '+' : ''}${value.toFixed(1)}`
  }

  function resolvePlatformHeatCellStyle(value: number) {
    const ratio = clamp(value / 100, 0, 1)
    const opacity = 0.12 + ratio * 0.72
    return {
      backgroundColor: `rgba(27, 94, 168, ${opacity.toFixed(3)})`,
      color: ratio > 0.48 ? '#f8fafc' : '#1f2937'
    }
  }

  function resolveAssetColor(asset: string) {
    const seed = buildSeed([asset])
    return rankingColorPalette[seed % rankingColorPalette.length] || '#1f2937'
  }

  function isTopicExpanded(topic: string) {
    return expandedTopicKey.value === topic
  }

  function toggleTopicDetails(topic: string) {
    expandedTopicKey.value = expandedTopicKey.value === topic ? null : topic
  }

  function topicDetailRows(topic: string) {
    return topicDetailRowsByTopic.value[topic] || []
  }

  function openTopicInsight(topic: string, question: string, rank: number, cell: TopicVisibilityRankCell) {
    if (cell.asset === '--') {
      return
    }

    const seed = buildSeed([topic, question, cell.asset, String(rank), selectedBrand.value])
    const visibility = round1(clamp(74 - rank * 2.8 + Math.sin(seed * 0.0007) * 6.2, 31, 96))
    const mentionRate = round1(clamp(58 - rank * 2.3 + Math.cos(seed * 0.0009) * 7.1, 22, 91))
    const citationShare = round1(clamp(18 - rank * 0.9 + Math.sin(seed * 0.0004) * 2.6, 2.8, 32))

    topicInsightOverlay.value = {
      topic,
      question,
      rank,
      asset: cell.asset,
      visibility,
      mentionRate,
      citationShare
    }
    topicInsightMetric.value = 'visibility'
    topicInsightTableMode.value = 'executions'
    topicInsightWindow.value = 'today'
    topicInsightCadence.value = 'daily'
    topicInsightPlatformFilter.value = 'all'
  }

  function closeTopicInsight() {
    topicInsightOverlay.value = null
  }

  return {
    activeDimension,
    activePlatformKeys,
    activePromptGroupModeLabel,
    assetRows,
    averagePositionOption,
    averagePositionRows,
    brandKeywordSeeds,
    brandOptions,
    brandPromptTopicSeeds,
    buildLineSeries,
    buildPlatformMetricOption,
    buildSeed,
    buildTimeLabels,
    chartConfigOptions,
    clamp,
    clampRank,
    closeTopicInsight,
    compareOptions,
    countryOptions,
    compareTrendPoints,
    citationCategoryKeys,
    citationCategoryOptions,
    citationPromptSignalKeys,
    citationPromptSignalOptions,
    competitorByBrand,
    dimensionTabs,
    expandedPromptTopics,
    expandedTopicKey,
    formatMetricDelta,
    formatMetricValue,
    formatSigned,
    granularityOptions,
    hasCoverage,
    hasPromptCoverage,
    isPlatformsTab,
    isPromptTopicExpanded,
    isPromptsTab,
    isSentimentTab,
    isCitationsTab,
    isQueryFanoutsTab,
    isTabActive,
    isTopicExpanded,
    languageOptions,
    openTopicInsight,
    platformCadenceOptions,
    platformMatrixGridStyle,
    platformMatrixRows,
    platformMetricRows,
    platformMetricSections,
    platformOptions,
    platformScopeLabel,
    platformScopeOptions,
    platformWindowOptions,
    promptColumnOptions,
    promptGroupMode,
    promptGroupModeOptions,
    promptSearch,
    promptTableGridStyle,
    promptTopicGroups,
    promptTopicKeys,
    promptTopicPromptBank,
    promptTopicScopeLabel,
    promptTopicScopeOptions,
    promptTotalCount,
    promptVisibleColumns,
    rankDelta,
    rankingColorPalette,
    rankingColumns,
    resolveAssetColor,
    resolveAssetShortLabel,
    resolveBrandBias,
    resolveDimensionBias,
    resolvePlatformHeatCellStyle,
    resolvePlatformMetricDelta,
    resolvePlatformMetricValue,
    resolvePointCount,
    resolvePromptCellDelta,
    resolvePromptCellValue,
    resolvePromptDeltaClass,
    resolveScopeLabel,
    resolvedExpandedPromptTopics,
    resolvedCitationCategories,
    resolvedCitationPromptSignals,
    resolvedSelectedPromptTopics,
    resolvedSelectedTopics,
    round1,
    showCountryFilter,
    showLanguageFilter,
    selectedAveragePosition,
    selectedAveragePositionRow,
    selectedBrand,
    selectedBrandSpaceId,
    selectedChartConfig,
    selectedCompare,
    selectedCountryCode,
    selectedGranularity,
    selectedLanguageCode,
    selectedPlatformCadence,
    selectedPlatformWindow,
    selectedPlatforms,
    selectedPromptColumns,
    selectedPromptTopics,
    selectedCitationCategories,
    selectedCitationPromptSignals,
    selectedSovRow,
    selectedSovShare,
    selectedTimeWindow,
    selectedTopics,
    sovOption,
    sovRows,
    timeWindowOptions,
    togglePlatform,
    togglePromptColumn,
    toggleCitationCategory,
    toggleCitationPromptSignal,
    togglePromptTopic,
    togglePromptTopicScope,
    toggleTopic,
    toggleTopicDetails,
    toggleValue,
    resetCitationPromptSignals,
    topicAssetPool,
    topicDetailRows,
    topicDetailRowsByTopic,
    topicInsightCadence,
    topicInsightExecutionCount,
    topicInsightMetric,
    topicInsightMetricLabel,
    topicInsightMetricValue,
    topicInsightOverlay,
    topicInsightPlatformBars,
    topicInsightPlatformFilter,
    topicInsightPlatformOption,
    topicInsightTableMode,
    topicInsightTableRows,
    topicInsightTrendOption,
    topicInsightTrendSeries,
    topicInsightWindow,
    topicOptions,
    topicQuestionBank,
    topicScopeLabel,
    topicScopeOptions,
    topicVisibilityRows,
    trendOption,
    trendPoints,
    useCompactTimeFilter,
    visibilityDelta,
    visibilityRank,
    visibilityScore,
    workbenchRef,
    workbenchScrolled
  }
}
