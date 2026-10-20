import type { EChartsOption } from 'echarts'

export type EngineDimension = 'visibility' | 'prompts' | 'platforms' | 'sentiment' | 'citations' | 'query-fanouts'
export type TimeWindow = '7d' | '30d' | '90d'
export type CompareWindow = 'none' | 'previous' | 'yoy'
export type Granularity = 'day' | 'week' | 'month'
export type ChartConfig = 'score' | 'rank' | 'dual'
export type CountryCode = 'CN' | 'US' | 'SG'
export type LanguageCode = 'zh-CN' | 'en-US'
export type PlatformWindow = 'today' | 'last7d'
export type PlatformCadence = 'daily' | 'weekly'
export type TopicInsightMetric = 'visibility' | 'avg-position' | 'citation-share'
export type TopicInsightTableMode = 'executions' | 'citations'
export type TopicInsightWindow = 'today' | '7d'
export type TopicInsightCadence = 'daily' | 'weekly'

export interface DimensionTab {
  key: EngineDimension
  label: string
}

export interface OptionItem<T extends string> {
  key: T
  label: string
}

export interface ScopeOption {
  key: string
  volume: number
}

export interface TrendPoint {
  label: string
  score: number
  peerScore: number
  rank: number
  peerRank: number
}

export interface AssetRow {
  rank: number
  asset: string
  score: number
  change: number
}

export interface SovRow {
  rank: number
  asset: string
  share: number
  previousShare: number
}

export interface AvgPositionRow {
  rank: number
  asset: string
  position: number
  previousPosition: number
}

export interface TopicVisibilityRankCell {
  asset: string
  shortLabel: string
  color: string
}

export interface TopicVisibilityRankRow {
  topic: string
  ranks: TopicVisibilityRankCell[]
}

export interface TopicDetailQuestionRow {
  id: string
  question: string
  ranks: TopicVisibilityRankCell[]
}

export interface TopicInsightOverlayPayload {
  topic: string
  question: string
  rank: number
  asset: string
  visibility: number
  mentionRate: number
  citationShare: number
}

export interface TopicInsightExecutionRow {
  id: string
  date: string
  mentioned: boolean
  position: number
  allMentions: string
  response: string
  platform: string
}

export type PromptGroupMode = 'topic' | 'flat'
export type PromptColumnKey = 'visibility-rank' | 'visibility-score' | 'average-position' | 'citation-share'

export interface PromptColumnOption {
  key: PromptColumnKey
  label: string
}

export interface PromptMetricRow {
  id: string
  topic: string
  prompt: string
  visibilityRank: number
  visibilityScore: number
  visibilityDelta: number
  averagePosition: number
  averageDelta: number
  citationShare: number
  citationDelta: number
}

export interface PromptTopicGroup {
  topic: string
  displayTopic: string
  rows: PromptMetricRow[]
  summary: PromptMetricRow
}

export interface BrandPromptTopicSeed {
  topicId: string
  topic: string
  prompts: string[]
}

export type CitationCategory = 'earned' | 'social' | 'owned' | 'competition'

export interface CitationDomainSeed {
  domain: string
  category: CitationCategory
  icon: string
  subdomains: number
}

export type ProbeStatus = 'active' | 'suggested' | 'inactive'
export type SourceType = 'editorial' | 'corporate' | 'ugc' | 'reference' | 'you'
export type MatchingType = 'semantic' | 'exact' | 'phrase'

export interface ProbeTagSeed {
  id: string
  label: string
  color: string
}

export interface ProbeSeed {
  id: string
  brand: string
  topicId: string
  topic: string
  prompt: string
  tags: string[]
  status: ProbeStatus
  location: CountryCode
  visibility: number
  sentiment: number
  position: number
  mentions: number
  volume: number
  visibilityTrend: number[]
  createdAt: string
  refs: EngineEntityRef
}

export interface SourceDomainSeed {
  id: string
  brand: string
  domain: string
  type: SourceType
  used: number
  avgCitations: number
  share: number
  delta: number
  trend: number[]
  refs: EngineEntityRef
}

export interface SourceUrlSeed {
  id: string
  brand: string
  domainId: string
  url: string
  type: SourceType
  used: number
  avgCitations: number
  share: number
  delta: number
  refs: EngineEntityRef
}

export interface ChatEvidenceCitationSeed {
  id: string
  sourceId: string
  title: string
  url: string
  refs: EngineEntityRef
}

export interface ChatEvidenceBrandHitSeed {
  brand: string
  score: number
}

export interface ChatEvidenceSeed {
  id: string
  brand: string
  probeId: string
  model: string
  query: string
  answer: string
  summary: string[]
  createdAt: string
  citations: ChatEvidenceCitationSeed[]
  brandHits: ChatEvidenceBrandHitSeed[]
  refs: EngineEntityRef
}

export type BrandTrackStatus = 'tracked' | 'suggested' | 'rejected'

export interface BrandTrackingSeed {
  id: string
  workspaceBrand: string
  brand: string
  domain: string
  mentions: number
  visibility: number
  sentiment: number
  position: number
  status: BrandTrackStatus
  refs: EngineEntityRef
}

export interface EngineDatasetMeta {
  dataVersion: string
  generatedAt: string
  seed: string
}

export interface EngineEntityRef {
  taskId?: string
  probeId?: string
  evidenceId?: string
  invocationId?: string
  topicId?: string
  tagIds?: string[]
  sourceDomainId?: string
  sourceUrlId?: string
  chatId?: string
  brandTrackingId?: string
  citationId?: string
}

export type TheaterStage
  = | 'task-decomposition'
    | 'state-assembly'
    | 'orchestration'
    | 'actor-generation'
    | 'probe-generation'
    | 'execution'
    | 'result-bridging'

export type TheaterBucketKey = 'coverage' | 'mainstream' | 'adversarial'
export type TheaterNodeType = 'task' | 'topic' | 'state' | 'bucket' | 'actor' | 'probe' | 'attempt' | 'evidence' | 'source' | 'mention' | 'result'
export type TheaterNodeStatus = 'idle' | 'active' | 'done' | 'warning' | 'error'
export type TheaterEdgeType = 'contains' | 'derives' | 'routes' | 'executes' | 'supports' | 'mentions' | 'converges'
export type TheaterProbeRunStatus = 'queued' | 'running' | 'complete' | 'fallback'
export type TheaterAttemptStatus = 'pending' | 'running' | 'succeeded' | 'failed'
export type TheaterEventType
  = | 'task_ingested'
    | 'state_assembled'
    | 'bucket_bound'
    | 'actor_generated'
    | 'probe_generated'
    | 'attempt_started'
    | 'evidence_attached'
    | 'brand_mentioned'
    | 'round_summary'
    | 'final_gate'
    | 'engine_log'
export type TheaterEventStatus = 'info' | 'running' | 'success' | 'warning' | 'error'
export type TheaterConsoleLevel = 'system' | 'route' | 'network' | 'gate'
export type TheaterDensityMode = 'overview' | 'detailed'
export type TheaterDetailTab = 'node' | 'probe' | 'evidence' | 'result'
export type TheaterInspectionMode = 'auto' | 'node' | 'event' | 'cluster'
export type TheaterGraphNodePhase = 'hidden' | 'entering' | 'idle' | 'active' | 'resolved' | 'dimmed'
export type TheaterGraphEdgePhase = 'hidden' | 'entering' | 'idle' | 'flowing' | 'resolved' | 'dimmed'
export type TheaterGraphClusterKind = 'topic' | 'state' | 'actor' | 'probe-batch'
export type TheaterGraphNodeEmphasis = 'normal' | 'focus' | 'warning' | 'success'

export interface TheaterStageStep {
  key: TheaterStage
  label: string
  description: string
  status: 'done' | 'active' | 'pending'
}

export interface TheaterTask {
  id: string
  brand: string
  category: string
  objective: string
  competitorCount: number
  modelScope: string[]
  networkEnabled: boolean
  probeTarget: number
  currentRound: number
  totalRounds: number
  progress: number
  currentStage: TheaterStage
  statusLabel: string
  taskLine: string
  refs: EngineEntityRef
}

export interface TheaterStateNode {
  id: string
  type: TheaterNodeType
  label: string
  subtitle: string
  detailLines: string[]
  stage: TheaterStage
  layer: number
  slot: number
  bucketKey?: TheaterBucketKey
  status: TheaterNodeStatus
  revealedAt: number
  emphasis?: 'core' | 'support'
  refs: EngineEntityRef
}

export interface TheaterStateEdge {
  id: string
  sourceId: string
  targetId: string
  type: TheaterEdgeType
  stage: TheaterStage
  bucketKey?: TheaterBucketKey
  revealedAt: number
  refs: EngineEntityRef
}

export interface TheaterBucket {
  key: TheaterBucketKey
  label: string
  objective: string
  tone: string
  targetProbeCount: number
  generatedProbeCount: number
  executedProbeCount: number
  topicCoverage: number
  actorCoverage: number
  fallbackCount: number
  refs: EngineEntityRef
}

export interface TheaterActor {
  id: string
  name: string
  role: string
  stance: string
  posture: string
  sourceStateIds: string[]
  bucketKey: TheaterBucketKey
  summary: string
  revealedAt: number
  refs: EngineEntityRef
}

export interface TheaterProbeRun {
  id: string
  probeId: string
  label: string
  topic: string
  bucketKey: TheaterBucketKey
  actorId: string
  stateIds: string[]
  status: TheaterProbeRunStatus
  summary: string
  revealedAt: number
  refs: EngineEntityRef
}

export interface TheaterAttempt {
  id: string
  probeId: string
  round: number
  model: string
  latencyMs: number
  confidence: number
  status: TheaterAttemptStatus
  query: string
  renderedPrompt: string
  networkQueries: string[]
  revealedAt: number
  refs: EngineEntityRef
}

export interface TheaterEvidence {
  id: string
  probeId: string
  attemptId: string
  sourceDomainId: string
  sourceUrlId: string
  title: string
  snippet: string
  confidence: number
  revealedAt: number
  refs: EngineEntityRef
}

export interface TheaterMention {
  id: string
  probeId: string
  attemptId: string
  brand: string
  sentiment: number
  position: number
  confidence: number
  excerpt: string
  revealedAt: number
  refs: EngineEntityRef
}

export interface TheaterEvent {
  id: string
  sequence: number
  stage: TheaterStage
  type: TheaterEventType
  title: string
  summary: string
  status: TheaterEventStatus
  timeLabel: string
  nodeId?: string
  probeId?: string
  bucketKey?: TheaterBucketKey
  detailLines: string[]
  refs: EngineEntityRef
}

export interface TheaterConsoleLine {
  id: string
  sequence: number
  stage: TheaterStage
  level: TheaterConsoleLevel
  message: string
  timeLabel: string
  eventId?: string
  refs: EngineEntityRef
}

export interface TheaterResultBridge {
  id: string
  abvIndex: number
  firstChoiceRate: number
  confirmationRate: number
  defenseIntegrity: number
  summary: string[]
  routes: Array<{
    label: string
    path: string
    description: string
  }>
  refs: EngineEntityRef
}

export interface TheaterGraphCluster {
  id: string
  kind: TheaterGraphClusterKind
  label: string
  subtitle: string
  layer: number
  stage: TheaterStage
  memberNodeIds: string[]
  bucketKey?: TheaterBucketKey
  revealedAt: number
  refs: EngineEntityRef
}

export interface TheaterGraphSceneNode {
  id: string
  sourceNodeId: string
  type: TheaterNodeType
  label: string
  subtitle: string
  detailLines: string[]
  stage: TheaterStage
  layer: number
  slot: number
  clusterId?: string
  parentId?: string
  bucketKey?: TheaterBucketKey
  status: TheaterNodeStatus
  phase: TheaterGraphNodePhase
  emphasis: TheaterGraphNodeEmphasis
  anchorX: number
  anchorY?: number
  weight: number
  revealedAt: number
  refs: EngineEntityRef
}

export interface TheaterGraphSceneEdge {
  id: string
  sourceId: string
  targetId: string
  type: TheaterEdgeType
  stage: TheaterStage
  bucketKey?: TheaterBucketKey
  phase: TheaterGraphEdgePhase
  pulseLevel: 0 | 1 | 2 | 3
  revealedAt: number
  refs: EngineEntityRef
}

export interface TheaterGraphScene {
  nodes: TheaterGraphSceneNode[]
  edges: TheaterGraphSceneEdge[]
  clusters: TheaterGraphCluster[]
}

export interface TheaterGraphPanelNode {
  uuid: string
  name: string
  labels: string[]
  summary: string
  attributes: Record<string, string | number | boolean | null>
  createdAt: string | null
}

export interface TheaterGraphPanelEdge {
  uuid: string
  name: string
  fact: string
  factType: string
  sourceNodeUuid: string
  targetNodeUuid: string
  sourceNodeName: string
  targetNodeName: string
  attributes: Record<string, string | number | boolean | null>
  episodes: string[]
  createdAt: string | null
  validAt: string | null
}

export interface TheaterSceneState {
  playhead: number
  autoplay: boolean
  density: TheaterDensityMode
  activeBucket: TheaterBucketKey | 'all'
  stageFilter: TheaterStage | 'all'
  inspectionMode: TheaterInspectionMode
  selectedNodeId: string | null
  selectedEventId: string | null
  selectedClusterId: string | null
  currentNodeId: string | null
  currentEventId: string | null
}

export interface TheaterBrandDataset {
  task: TheaterTask
  stageSteps: TheaterStageStep[]
  nodes: TheaterStateNode[]
  edges: TheaterStateEdge[]
  buckets: TheaterBucket[]
  actors: TheaterActor[]
  probeRuns: TheaterProbeRun[]
  attempts: TheaterAttempt[]
  evidences: TheaterEvidence[]
  mentions: TheaterMention[]
  events: TheaterEvent[]
  consoleLines: TheaterConsoleLine[]
  resultBridge: TheaterResultBridge
}

export type PlatformMetricKey = 'visibility' | 'share-of-voice' | 'average-position' | 'citation-share' | 'sentiment'

export interface PlatformMetricPoint {
  platform: string
  visibility: number
  visibilityDelta: number | null
  shareOfVoice: number
  shareOfVoiceDelta: number | null
  averagePosition: number
  averagePositionDelta: number | null
  citationShare: number
  citationShareDelta: number | null
  sentiment: number
  sentimentDelta: number | null
}

export interface PlatformMatrixRow {
  asset: string
  isSelected: boolean
  values: Record<string, number>
}

export interface PlatformMetricRankRow {
  rank: number
  platform: string
  value: number
  delta: number | null
}

export interface PlatformMetricSection {
  key: PlatformMetricKey
  title: string
  subtitle: string
  chartLabel: string
  rankTitle: string
  rankColumnLabel: string
  isPercent: boolean
  lowerIsBetter: boolean
  axisMax: number
  rows: PlatformMetricRankRow[]
  chartOption: EChartsOption | null
}

export interface EngineDataset {
  meta: EngineDatasetMeta
  brandOptions: OptionItem<string>[]
  brandKeywordSeeds: Record<string, string[]>
  competitorByBrand: Record<string, string[]>
  brandPromptTopicSeeds: Record<string, BrandPromptTopicSeed[]>
  platformScopeOptions: ScopeOption[]
  citationDomainSeedsByBrand: Record<string, CitationDomainSeed[]>
  probeTags: ProbeTagSeed[]
  probeSeedsByBrand: Record<string, ProbeSeed[]>
  sourceDomainsByBrand: Record<string, SourceDomainSeed[]>
  sourceUrlsByBrand: Record<string, SourceUrlSeed[]>
  chatEvidenceByBrand: Record<string, ChatEvidenceSeed[]>
  brandTrackingByWorkspaceBrand: Record<string, BrandTrackingSeed[]>
  theaterByBrand: Record<string, TheaterBrandDataset>
}

export interface EngineApiError {
  code: string
  message: string
}

export interface EngineApiSuccess<T> {
  ok: true
  traceId: string
  data: T
}

export interface EngineApiFailure {
  ok: false
  traceId: string
  error: EngineApiError
}

export type EngineApiEnvelope<T> = EngineApiSuccess<T> | EngineApiFailure

export type EngineModuleKey = 'overview' | 'prompts' | 'platforms' | 'sentiment' | 'citations' | 'queryFanouts' | 'topicInsight'

export interface EngineModulePayload<K extends EngineModuleKey = EngineModuleKey> {
  module: K
  filters: Record<string, unknown>
  dataset: EngineDataset
}
