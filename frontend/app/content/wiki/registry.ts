import type {
  WikiContextBlock,
  WikiObjectAuthoringDefinition,
  WikiResolvedEntrypoint,
  WikiRuntimeObject
} from '~/types/wiki'
import { brandsWikiObjects } from '~/content/wiki/objects/brands'
import { dashboardWikiObjects } from '~/content/wiki/objects/dashboard'
import { engineWikiObjects } from '~/content/wiki/objects/engine'
import { probeAnalysisWikiObjects } from '~/content/wiki/objects/probe-analysis'
import { sourcesWikiObjects } from '~/content/wiki/objects/sources'

export const wikiAuthoringObjects: WikiObjectAuthoringDefinition[] = [
  ...dashboardWikiObjects,
  ...engineWikiObjects,
  ...sourcesWikiObjects,
  ...probeAnalysisWikiObjects,
  ...brandsWikiObjects
]

function buildSourceLabel(definition: WikiObjectAuthoringDefinition) {
  if (definition.userSourceLabel) {
    return definition.userSourceLabel
  }

  const primary = definition.sourceOfTruth[0]
  if (!primary) {
    return undefined
  }

  const labelByKind: Record<WikiObjectAuthoringDefinition['sourceOfTruth'][number]['kind'], string> = {
    prd: '产品定义',
    'backend-contract': '后端接口口径',
    'frontend-contract': '前端展示口径',
    field: '工作台字段口径',
    route: '页面定义',
    manual: '产品定义'
  }

  return labelByKind[primary.kind] || primary.note || primary.ref
}

function toRuntimeObject(definition: WikiObjectAuthoringDefinition): WikiRuntimeObject {
  return {
    objectId: definition.objectId,
    displayName: definition.displayName,
    type: definition.type,
    status: definition.status,
    conceptState: definition.conceptState,
    updatedAt: definition.updatedAt,
    scope: definition.scope,
    summary: definition.summary,
    sourceLabel: buildSourceLabel(definition),
    contexts: definition.contexts,
    nextSteps: definition.nextSteps
  }
}

export const wikiRuntimeObjects: WikiRuntimeObject[] = wikiAuthoringObjects.map(toRuntimeObject)

const wikiRuntimeMap = new Map(wikiRuntimeObjects.map(item => [item.objectId, item]))
const wikiAuthoringMap = new Map(wikiAuthoringObjects.map(item => [item.objectId, item]))

export function resolveWikiRuntimeObject(objectId: string) {
  return wikiRuntimeMap.get(objectId) || null
}

export function resolveWikiAuthoringObject(objectId: string) {
  return wikiAuthoringMap.get(objectId) || null
}

export function canPublishWikiObject(object: WikiRuntimeObject | null) {
  return Boolean(
    object
    && object.status === 'active'
    && (object.conceptState === 'stable' || object.conceptState === 'mapped')
  )
}

export function resolveWikiContextBlock(object: WikiRuntimeObject | null, pageContext: string | null) {
  if (!object) {
    return null
  }
  if (pageContext) {
    const exactMatch = object.contexts.find(context => context.pageContext === pageContext)
    if (exactMatch) {
      return exactMatch
    }
  }
  return object.contexts[0] || null
}

export function hasMeaningfulWikiNextSteps(object: WikiRuntimeObject | null) {
  return Boolean(object?.nextSteps?.length)
}

export function formatWikiScope(scope: string[]) {
  return scope.filter(Boolean).join(' / ')
}

export function formatWikiUpdatedAt(updatedAt: string) {
  return updatedAt
}

export function formatWikiContextTitle(pageContext: string) {
  const labels: Record<string, string> = {
    'dashboard.page': '总览页',
    'dashboard.summary': '总览指标区',
    'dashboard.trend': '趋势区',
    'dashboard.source-citations': '来源摘要区',
    'dashboard.model-performance': '模型表现区',
    'engine.page': '品牌表现页',
    'engine.topics': '主题维度',
    'engine.platforms': '平台维度',
    'engine.sentiment': '情绪维度',
    'sources.page': '引用来源页',
    'sources.list': '来源域名区',
    'probe.page': '问题分析页',
    'probe.records': '真实记录区',
    'brands.index': '品牌空间列表'
  }

  return labels[pageContext] || pageContext
}

export function resolveWikiBlockPayload(object: WikiRuntimeObject | null, pageContext: string | null) {
  if (!object) {
    return null
  }

  const context = resolveWikiContextBlock(object, pageContext)

  return {
    object,
    sourceLabel: object.sourceLabel,
    context,
    scopeText: formatWikiScope(object.scope),
    updatedAtText: formatWikiUpdatedAt(object.updatedAt),
    contextTitle: context ? formatWikiContextTitle(context.pageContext) : null
  }
}
