export type WikiObjectType =
  | 'page'
  | 'module'
  | 'metric'
  | 'dimension'
  | 'object'
  | 'term'
  | 'state'
  | 'action'

export type WikiObjectStatus = 'draft' | 'review' | 'active' | 'deprecated'
export type WikiConceptState = 'stable' | 'mapped' | 'contested' | 'retired'

export interface WikiSourceRef {
  kind: 'prd' | 'backend-contract' | 'frontend-contract' | 'field' | 'route' | 'manual'
  ref: string
  note?: string
}

export interface WikiNextStep {
  label: string
  route?: string
  query?: Record<string, string>
  note?: string
}

export interface WikiContextBlock {
  pageContext: string
  definition: string
  importance: string
  readingGuide: string
  formula?: string
  interpretation?: WikiReadingPoint[]
  rules?: string[]
  boundaries?: string[]
  commonMisreads?: string[]
}

export interface WikiReadingPoint {
  label: string
  text: string
}

export interface WikiObjectAuthoringDefinition {
  objectId: string
  displayName: string
  type: WikiObjectType
  status: WikiObjectStatus
  conceptState: WikiConceptState
  updatedAt: string
  owner: string
  scope: string[]
  sourceOfTruth: WikiSourceRef[]
  userSourceLabel?: string
  aliases?: string[]
  summary: string
  contexts: WikiContextBlock[]
  nextSteps: WikiNextStep[]
}

export interface WikiRuntimeObject {
  objectId: string
  displayName: string
  type: WikiObjectType
  status: WikiObjectStatus
  conceptState: WikiConceptState
  updatedAt: string
  scope: string[]
  summary: string
  sourceLabel?: string
  contexts: WikiContextBlock[]
  nextSteps: WikiNextStep[]
}

export type WikiTriggerKind =
  | 'inline-underline'
  | 'title-help'
  | 'metric-label'
  | 'empty-state-link'
  | 'action-explainer'

export interface WikiEntrypoint {
  entrypointId: string
  route: string
  objectId: string
  triggerKind: WikiTriggerKind
  pageContext: string
  contentType: 'wiki'
  enabled: boolean
}

export interface WikiResolvedEntrypoint {
  entrypoint: WikiEntrypoint
  object: WikiRuntimeObject
}
