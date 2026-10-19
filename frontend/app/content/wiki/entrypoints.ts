import type { WikiEntrypoint } from '~/types/wiki'

export const wikiEntrypoints: WikiEntrypoint[] = [
  {
    entrypointId: 'dashboard.page.title',
    route: '/dashboard',
    objectId: 'page.dashboard',
    triggerKind: 'inline-underline',
    pageContext: 'dashboard.page',
    contentType: 'wiki',
    enabled: true
  },
  {
    entrypointId: 'dashboard.metric.ai_visibility',
    route: '/dashboard',
    objectId: 'metric.ai_visibility',
    triggerKind: 'metric-label',
    pageContext: 'dashboard.summary',
    contentType: 'wiki',
    enabled: true
  },
  {
    entrypointId: 'dashboard.metric.recommend_rate',
    route: '/dashboard',
    objectId: 'metric.recommend_rate',
    triggerKind: 'metric-label',
    pageContext: 'dashboard.summary',
    contentType: 'wiki',
    enabled: true
  },
  {
    entrypointId: 'dashboard.metric.mention_rate',
    route: '/dashboard',
    objectId: 'metric.mention_rate',
    triggerKind: 'metric-label',
    pageContext: 'dashboard.summary',
    contentType: 'wiki',
    enabled: true
  },
  {
    entrypointId: 'dashboard.metric.top3_rate',
    route: '/dashboard',
    objectId: 'metric.top3_rate',
    triggerKind: 'metric-label',
    pageContext: 'dashboard.summary',
    contentType: 'wiki',
    enabled: true
  },
  {
    entrypointId: 'dashboard.module.trend',
    route: '/dashboard',
    objectId: 'module.dashboard.trend',
    triggerKind: 'inline-underline',
    pageContext: 'dashboard.trend',
    contentType: 'wiki',
    enabled: true
  },
  {
    entrypointId: 'dashboard.module.source_citations',
    route: '/dashboard',
    objectId: 'module.dashboard.source_citations',
    triggerKind: 'inline-underline',
    pageContext: 'dashboard.source-citations',
    contentType: 'wiki',
    enabled: true
  },
  {
    entrypointId: 'dashboard.module.model_performance',
    route: '/dashboard',
    objectId: 'module.dashboard.model_performance',
    triggerKind: 'inline-underline',
    pageContext: 'dashboard.model-performance',
    contentType: 'wiki',
    enabled: true
  },
  {
    entrypointId: 'engine.page.title',
    route: '/engine',
    objectId: 'page.engine',
    triggerKind: 'inline-underline',
    pageContext: 'engine.page',
    contentType: 'wiki',
    enabled: true
  },
  {
    entrypointId: 'engine.dimension.topic',
    route: '/engine',
    objectId: 'dimension.topic',
    triggerKind: 'inline-underline',
    pageContext: 'engine.topics',
    contentType: 'wiki',
    enabled: true
  },
  {
    entrypointId: 'engine.dimension.platform',
    route: '/engine',
    objectId: 'dimension.platform',
    triggerKind: 'inline-underline',
    pageContext: 'engine.platforms',
    contentType: 'wiki',
    enabled: true
  },
  {
    entrypointId: 'engine.dimension.sentiment',
    route: '/engine',
    objectId: 'dimension.sentiment',
    triggerKind: 'inline-underline',
    pageContext: 'engine.sentiment',
    contentType: 'wiki',
    enabled: true
  },
  {
    entrypointId: 'engine.metric.average_position',
    route: '/engine',
    objectId: 'metric.average_position',
    triggerKind: 'metric-label',
    pageContext: 'engine.topics',
    contentType: 'wiki',
    enabled: true
  },
  {
    entrypointId: 'sources.page.title',
    route: '/sources',
    objectId: 'page.sources',
    triggerKind: 'inline-underline',
    pageContext: 'sources.page',
    contentType: 'wiki',
    enabled: true
  },
  {
    entrypointId: 'sources.object.source_domain',
    route: '/sources',
    objectId: 'object.source_domain',
    triggerKind: 'inline-underline',
    pageContext: 'sources.list',
    contentType: 'wiki',
    enabled: true
  },
  {
    entrypointId: 'sources.metric.citation_share',
    route: '/sources',
    objectId: 'metric.citation_share',
    triggerKind: 'metric-label',
    pageContext: 'sources.list',
    contentType: 'wiki',
    enabled: true
  },
  {
    entrypointId: 'probe.page.title',
    route: '/probe-analysis',
    objectId: 'page.probe_analysis',
    triggerKind: 'inline-underline',
    pageContext: 'probe.page',
    contentType: 'wiki',
    enabled: true
  },
  {
    entrypointId: 'probe.object.prompt',
    route: '/probe-analysis',
    objectId: 'object.prompt',
    triggerKind: 'inline-underline',
    pageContext: 'probe.records',
    contentType: 'wiki',
    enabled: true
  },
  {
    entrypointId: 'probe.object.evidence',
    route: '/probe-analysis',
    objectId: 'object.evidence',
    triggerKind: 'inline-underline',
    pageContext: 'probe.records',
    contentType: 'wiki',
    enabled: true
  },
  {
    entrypointId: 'brands.object.brand_space',
    route: '/brands',
    objectId: 'object.brand_space',
    triggerKind: 'inline-underline',
    pageContext: 'brands.index',
    contentType: 'wiki',
    enabled: true
  }
]

const wikiEntrypointMap = new Map(wikiEntrypoints.map(item => [item.entrypointId, item]))

export function resolveWikiEntrypoint(entrypointId: string) {
  return wikiEntrypointMap.get(entrypointId) || null
}
