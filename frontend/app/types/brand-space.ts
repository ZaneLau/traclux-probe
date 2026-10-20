export type BrandSpaceStatus = 'generating' | 'active' | 'failed' | (string & {})
export type BrandSpaceTrackingStatus = 'ACTIVE' | 'PAUSED' | 'NEEDS_ATTENTION' | (string & {})
export type BrandSpaceTrackingMode = 'ACTIVE' | 'PAUSED' | (string & {})
export type BrandSpaceHealthStatus = 'HEALTHY' | 'DEGRADED' | 'ERROR' | 'CONFIG_INCOMPLETE' | (string & {})
export type ServiceAccessStatus = 'active' | 'trial' | 'inactive' | 'expired' | (string & {})
export type RunStatus = 'created' | 'running' | 'completed' | 'failed' | 'cancelled' | 'partial' | (string & {})
export type InitialRunStatus = RunStatus | 'blocked' | (string & {})
export type RunEventType
  = | 'run_started'
    | 'step_changed'
    | 'probe_generated'
    | 'model_called'
    | 'citations_collected'
    | 'aggregation_completed'
    | 'run_completed'
    | 'run_failed'
    | (string & {})

export interface ServiceAccess {
  status: ServiceAccessStatus
  planName?: string | null
  expiresAt?: string | null
  reason?: string | null
  limits?: {
    brandSpaceLimit?: number | null
    monthlyRunLimit?: number | null
    dailyProbeQuota?: number | null
    promptLimitPerBrandSpace?: number | null
    enabledModelCount?: number | null
    creditsPerProbe?: number | null
  } | null
}

export interface BrandSpaceDraft {
  brandName: string
  category: string
  officialWebsite?: string
  competitors?: string[]
  supplementalBrief?: string
  regionCode?: string
  locale?: string
  language?: string
}

export interface BrandSpaceTopic {
  id?: string
  tempId?: string
  name: string
  description?: string | null
  enabled: boolean
  source?: string | null
}

export interface BrandSpacePrompt {
  id?: string
  tempId?: string
  topicId?: string
  topicTempId?: string
  text: string
  enabled: boolean
  source?: string | null
}

export interface EstimatedRun {
  targetProbeCount?: number | null
  estimatedDurationSeconds?: number | null
  realtimeSearchEnabled?: boolean | null
}

export interface BrandSpaceRecognition {
  suggestedCategory?: string | null
  officialWebsite?: string | null
  directCompetitors?: string[]
  indirectCompetitors?: string[]
  competitors?: string[]
  brandSummary?: string | null
  source?: string | null
  fallbackReason?: string | null
}

export interface BrandSpacePreviewRequest extends BrandSpaceDraft {
  idempotencyKey: string
  recognition?: BrandSpaceRecognition | null
  topics?: BrandSpaceTopic[] | null
}

export interface BrandSpacePreview {
  previewToken: string
  brandSpaceDraft: BrandSpaceDraft
  recognition?: BrandSpaceRecognition | null
  topics: BrandSpaceTopic[]
  prompts?: BrandSpacePrompt[] | null
  estimatedRun?: EstimatedRun | null
  serviceAccess?: ServiceAccess | null
}

export interface BrandSpaceCreateRequest {
  previewToken: string
  brandSpaceDraft: BrandSpaceDraft
  topics: BrandSpaceTopic[]
  targetProbeCount?: number | null
  targetModelProduct?: string | null
  executeModeCode?: 'RECURRING' | 'ONE_TIME' | string | null
  idempotencyKey: string
}

export interface BrandSpaceCreateResponse {
  brandSpaceId: string
  brandId: string
  brandScopeId: string
  initialRunId?: string | null
  initialRunStatus?: InitialRunStatus | null
  initialRunBlockCode?: string | null
  initialRunBlockMessage?: string | null
  status: BrandSpaceStatus
  nextView?: string | null
}

export interface BrandSpaceBasicProfileView {
  brandSpaceId: string
  brandId?: string | null
  brandName: string
  category: string
  officialWebsite?: string | null
  primaryDomain?: string | null
  competitors: string[]
  supplementalBrief?: string | null
}

export interface BrandSpaceBasicProfileUpdateRequest {
  brandName: string
  category: string
  officialWebsite?: string | null
  competitors: string[]
  supplementalBrief?: string | null
}

export interface BrandSpaceConfigTopic {
  topicId: string
  name: string
  description?: string | null
  seqNo?: number | null
  enabled?: boolean | null
  source?: string | null
  promptCount?: number | null
  enabledPromptCount?: number | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface BrandSpaceConfigPrompt {
  promptId: string
  topicId?: string | null
  topicName?: string | null
  seqNo?: number | null
  text: string
  enabled?: boolean | null
  source?: string | null
  regionCode?: string | null
  locale?: string | null
  language?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface BrandSpaceConfigSummary {
  topicCount?: number | null
  enabledTopicCount?: number | null
  promptCount?: number | null
  enabledPromptCount?: number | null
}

export interface BrandSpaceConfigCapabilities {
  canRead?: boolean | null
  canCreateTopic?: boolean | null
  canUpdateTopic?: boolean | null
  canReorderTopic?: boolean | null
  canCreatePrompt?: boolean | null
  canUpdatePrompt?: boolean | null
  canReorderPrompt?: boolean | null
  tagTruthObjectSupported?: boolean | null
}

export interface BrandSpaceRunConfig {
  targetProbeCount?: number | null
  targetModelProduct?: string | null
  autoRunEnabled?: boolean | null
}

export interface BrandSpaceConfigTopicUpdate {
  tempId?: string
  name: string
  description?: string | null
  enabled?: boolean | null
  source?: string | null
}

export interface BrandSpaceConfigPromptUpdate {
  tempId?: string
  topicTempId?: string | null
  text: string
  enabled?: boolean | null
  source?: string | null
}

export interface BrandSpaceConfigUpdateRequest {
  topics: BrandSpaceConfigTopicUpdate[]
  prompts: BrandSpaceConfigPromptUpdate[]
  targetProbeCount?: number | null
  targetModelProduct?: string | null
  autoRunEnabled?: boolean | null
  regionCode?: string
  locale?: string
  language?: string
}

export interface BrandSpaceTrackingModeUpdateRequest {
  trackingMode: BrandSpaceTrackingMode
}

export interface BrandSpaceProbePlanConfirmRequest {
  plannedProbeCount?: number | null
  targetModelProduct?: string | null
  executeModeCode?: 'RECURRING' | 'ONE_TIME' | string | null
  reason?: string | null
}

export interface BrandSpaceProbePlanItem {
  probeId?: string | null
  topicId?: string | null
  seqNo?: number | null
  promptText?: string | null
  actionCode?: string | null
  executeModeCode?: string | null
}

export interface BrandSpaceProbePlanCurrentPlan {
  planVersionId?: string | null
  versionNo?: number | null
  activeRecurringProbeCount?: number | null
  estimatedCreditsPerCycle?: number | null
  configGeneration?: number | null
  snapshot?: Record<string, unknown> | null
  items?: BrandSpaceProbePlanItem[] | null
}

export interface BrandSpaceProbePlanRecord {
  planRecordId?: string | null
  planVersionId?: string | null
  recordNo?: number | null
  changeTypeCode?: string | null
  executeModeCode?: string | null
  immediateEstimatedCredits?: number | null
  recurringEstimatedCreditsPerCycle?: number | null
  triggeredRunId?: string | null
  summary?: Record<string, unknown> | null
  createdAt?: string | null
}

export interface BrandSpaceProbePlanView {
  contractVersion?: string | null
  brandSpaceId: string
  currentRunConfig?: BrandSpaceRunConfig | null
  currentPlan?: BrandSpaceProbePlanCurrentPlan | null
  recentRecords?: BrandSpaceProbePlanRecord[] | null
}

export interface BrandSpaceProbePlanExecuteOnceResponse {
  brandSpaceId: string
  runId?: string | null
  requestId?: string | null
  targetProbeCount?: number | null
  estimatedCredits?: number | null
  deductedCredits?: number | null
  creditsRemainingAfterRun?: number | null
  skipped?: boolean | null
  skippedReason?: string | null
  probePlan?: BrandSpaceProbePlanView | null
}

export interface BrandSpaceConfigView {
  brandSpaceId: string
  brandId?: string | null
  brandName: string
  configGeneration?: number | null
  topics: BrandSpaceConfigTopic[]
  prompts: BrandSpaceConfigPrompt[]
  runConfig?: BrandSpaceRunConfig | null
  summary?: BrandSpaceConfigSummary | null
  capabilities?: BrandSpaceConfigCapabilities | null
}

export interface BrandSpaceScheduleView {
  triggerMode?: string | null
  targetProbeCount?: number | null
  coverageStrategy?: string | null
  schedulingReason?: string | null
  selectedTopicCount?: number | null
  selectedTopicNames?: string[] | null
  deferredTopicCount?: number | null
  deferredTopicNames?: string[] | null
  coldStart?: boolean | null
  justEdited?: boolean | null
  quotaLimited?: boolean | null
  accountBrandCount?: number | null
  brandCoreProbeCount?: number | null
  brandExpansionProbeCount?: number | null
  weeklyTargetTopicCount?: number | null
  weeklyCoveredTopicCount?: number | null
  weeklyGapTopicCount?: number | null
  weeklyCoverageRatio?: number | null
}

export interface BrandSpaceListItem {
  brandSpaceId: string
  brandId?: string | null
  brandScopeId?: string | null
  brandName: string
  category?: string | null
  officialWebsite?: string | null
  primaryDomain?: string | null
  status: BrandSpaceStatus
  latestRunId?: string | null
  latestReportId?: string | null
  lastGeneratedAt?: string | null
  visibilityScore?: number | null
  recommendRate?: number | null
  mentionRate?: number | null
  top3Rate?: number | null
  serviceAccessStatus?: ServiceAccessStatus | null
  trackingStatus?: BrandSpaceTrackingStatus | null
  healthStatus?: BrandSpaceHealthStatus | null
  configReady?: boolean | null
  activePromptCount?: number | null
  probePlanStatus?: string | null
  probePlanVersionNo?: number | null
  activeRecurringProbeCount?: number | null
  estimatedCreditsPerCycle?: number | null
  lastRunStatus?: RunStatus | null
  lastRunAt?: string | null
  latestSchedule?: BrandSpaceScheduleView | null
}

export interface BrandSpaceListResponse {
  items: BrandSpaceListItem[]
  total: number
}

export interface BrandSpaceArchiveItem {
  brandSpaceId: string
  brandName: string
  lifecycleStatus?: string | null
  archivedAt?: string | null
  retentionUntil?: string | null
  internalRestoreUntil?: string | null
  purgedAt?: string | null
  canUserRestore?: boolean | null
  canInternalRestore?: boolean | null
}

export interface BrandSpaceArchiveListResponse {
  items: BrandSpaceArchiveItem[]
  total: number
}

export interface RunSummary {
  runId: string
  brandSpaceId?: string | null
  brandName?: string | null
  status: RunStatus
  progressPercent?: number | null
  currentStep?: string | null
  currentStepLabel?: string | null
  startedAt?: string | null
  completedAt?: string | null
  latestEventSeq?: number | null
}

export interface RunEvent {
  id?: string
  seq?: number
  type?: RunEventType
  eventType?: RunEventType
  provisional?: boolean | null
  title?: string | null
  message?: string | null
  step?: string | null
  createdAt?: string | null
  payload?: Record<string, unknown> | null
}

export interface RunEventsResponse {
  events?: RunEvent[]
  items?: RunEvent[]
  nextSeq?: number | null
  latestEventSeq?: number | null
}

export interface BrandSpaceRunHistoryItem {
  runId: string
  runDate?: string | null
  startedAt?: string | null
  completedAt?: string | null
  updatedAt?: string | null
  triggerMode?: string | null
  triggerLabel?: string | null
  status?: RunStatus | null
  statusLabel?: string | null
  plannedProbeCount?: number | null
  completedProbeCount?: number | null
  failedProbeCount?: number | null
  coverageTopicCount?: number | null
  evidenceCount?: number | null
  schedulingReason?: string | null
}

export interface BrandSpaceRunHistoryView {
  brandSpaceId: string
  brandName?: string | null
  dateFrom?: string | null
  dateTo?: string | null
  totalRuns?: number | null
  items: BrandSpaceRunHistoryItem[]
}

export interface BrandSpaceRunTopicDecision {
  topicId?: string | null
  topicName?: string | null
  plannedProbeCount?: number | null
  priorityScore?: number | null
  reasonTags?: string[] | null
}

export interface BrandSpaceRunOverview {
  runDate?: string | null
  startedAt?: string | null
  completedAt?: string | null
  updatedAt?: string | null
  durationSeconds?: number | null
  triggerMode?: string | null
  triggerLabel?: string | null
  status?: RunStatus | null
  statusLabel?: string | null
  plannedProbeCount?: number | null
  completedProbeCount?: number | null
  failedProbeCount?: number | null
  evidenceCount?: number | null
  coverageTopicCount?: number | null
  reportGeneratedAt?: string | null
  failureMessage?: string | null
}

export interface BrandSpaceRunScheduleDecision {
  snapshotAvailable?: boolean | null
  coverageStrategy?: string | null
  schedulingReason?: string | null
  coldStart?: boolean | null
  justEdited?: boolean | null
  quotaLimited?: boolean | null
  accountBrandCount?: number | null
  brandCoreProbeCount?: number | null
  brandExpansionProbeCount?: number | null
  weeklyTargetTopicCount?: number | null
  weeklyCoveredTopicCount?: number | null
  weeklyGapTopicCount?: number | null
  weeklyCoverageRatio?: number | null
  selectedTopics?: BrandSpaceRunTopicDecision[] | null
  deferredTopics?: BrandSpaceRunTopicDecision[] | null
}

export interface BrandSpaceRunTopicExecution {
  topicId?: string | null
  topicName?: string | null
  plannedProbeCount?: number | null
  promptCount?: number | null
  promptSamples?: string[] | null
  priorityScore?: number | null
  reasonTags?: string[] | null
}

export interface BrandSpaceRunProbeExecution {
  probeId?: string | null
  probeRef?: string | null
  seqNo?: number | null
  topicId?: string | null
  topicName?: string | null
  promptId?: string | null
  renderedPrompt?: string | null
  modelId?: string | null
  status?: string | null
  statusLabel?: string | null
  evidenceCount?: number | null
  latencyMs?: number | null
  createdAt?: string | null
  provisional?: boolean | null
}

export interface BrandSpaceRunMilestone {
  type?: string | null
  label?: string | null
  createdAt?: string | null
  summary?: string | null
}

export interface BrandSpaceRunPlanTrace {
  planVersionId?: string | null
  planVersionNo?: number | null
  planRecordId?: string | null
  planRecordNo?: number | null
  executeModeCode?: string | null
  estimatedCredits?: number | null
  chargedCredits?: number | null
  creditDelta?: number | null
  creditReconciliationStatus?: string | null
  estimatedCreditsSourceCode?: string | null
  chargedCreditsSourceCode?: string | null
  coveredTopicCount?: number | null
  plannedProbeCount?: number | null
}

export interface BrandSpaceRunExecutionRecord {
  selectedTopicCount?: number | null
  deferredTopicCount?: number | null
  completedProbeCount?: number | null
  failedProbeCount?: number | null
  topicExecutions?: BrandSpaceRunTopicExecution[] | null
  probeExecutions?: BrandSpaceRunProbeExecution[] | null
  milestones?: BrandSpaceRunMilestone[] | null
}

export interface BrandSpaceRunRawRecord {
  taskOrder?: Record<string, unknown> | null
  scheduleDecision?: Record<string, unknown> | null
  reportSummary?: Record<string, unknown> | null
  events?: RunEvent[] | null
  failureMessage?: string | null
}

export interface BrandSpaceRunDetailView {
  brandSpaceId: string
  brandName?: string | null
  runId: string
  overview?: BrandSpaceRunOverview | null
  planTrace?: BrandSpaceRunPlanTrace | null
  scheduleDecision?: BrandSpaceRunScheduleDecision | null
  executionRecord?: BrandSpaceRunExecutionRecord | null
  rawRecord?: BrandSpaceRunRawRecord | null
}
