export type DashboardCardKey
  = | 'current_performance'
    | 'trend'
    | 'priorities'
    | 'competitor_rank'
    | 'source_citations'
    | 'model_performance'

export type DashboardDetailRouteKey = 'engine' | 'probe_analysis' | 'sources'

export interface DashboardDetailLocation {
  path: string
  query?: Record<string, string>
}

export interface DashboardOverviewCard {
  cardKey: DashboardCardKey
  title: string
  primary: string
  secondary: string
  detailRouteKey: DashboardDetailRouteKey
}

export interface DashboardOverviewContract {
  cards: DashboardOverviewCard[]
}

type DashboardOverviewCardInput = Omit<DashboardOverviewCard, 'cardKey'>

export const DASHBOARD_CARD_ORDER: DashboardCardKey[] = [
  'current_performance',
  'trend',
  'priorities',
  'competitor_rank',
  'source_citations',
  'model_performance'
]

export const DASHBOARD_DETAIL_ROUTE_PATH: Record<DashboardDetailRouteKey, string> = {
  engine: '/engine',
  probe_analysis: '/probe-analysis',
  sources: '/sources'
}

export const DASHBOARD_DETAIL_LOCATION_BY_CARD: Record<DashboardCardKey, DashboardDetailLocation> = {
  current_performance: {
    path: '/engine',
    query: { tab: 'topics' }
  },
  trend: {
    path: '/engine',
    query: { tab: 'topics' }
  },
  priorities: {
    path: '/probe-analysis'
  },
  competitor_rank: {
    path: '/engine',
    query: { tab: 'sentiment' }
  },
  source_citations: {
    path: '/sources'
  },
  model_performance: {
    path: '/engine',
    query: { tab: 'platforms' }
  }
}

const DEFAULT_CARD_INPUTS: Record<DashboardCardKey, DashboardOverviewCardInput> = {
  current_performance: {
    title: '当前表现',
    primary: '暂无数据',
    secondary: '等待更新',
    detailRouteKey: 'engine'
  },
  trend: {
    title: '变化趋势',
    primary: '暂无数据',
    secondary: '等待更新',
    detailRouteKey: 'engine'
  },
  priorities: {
    title: '优先关注',
    primary: '暂无数据',
    secondary: '等待更新',
    detailRouteKey: 'probe_analysis'
  },
  competitor_rank: {
    title: '竞品排名',
    primary: '暂无数据',
    secondary: '等待更新',
    detailRouteKey: 'engine'
  },
  source_citations: {
    title: '引用来源',
    primary: '暂无数据',
    secondary: '等待更新',
    detailRouteKey: 'sources'
  },
  model_performance: {
    title: '模型表现',
    primary: '暂无数据',
    secondary: '等待更新',
    detailRouteKey: 'engine'
  }
}

function resolveText(value: string | undefined, fallback: string) {
  const text = (value || '').trim()
  return text || fallback
}

export function buildDashboardOverviewContract(
  inputs: Partial<Record<DashboardCardKey, DashboardOverviewCardInput>>
): DashboardOverviewContract {
  const cards: DashboardOverviewCard[] = DASHBOARD_CARD_ORDER.map((cardKey) => {
    const base = DEFAULT_CARD_INPUTS[cardKey]
    const custom = inputs[cardKey]
    return {
      cardKey,
      title: resolveText(custom?.title, base.title),
      primary: resolveText(custom?.primary, base.primary),
      secondary: resolveText(custom?.secondary, base.secondary),
      detailRouteKey: custom?.detailRouteKey || base.detailRouteKey
    }
  })
  return { cards }
}
