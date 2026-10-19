export interface WorkbenchDataAvailability {
  status?: string | null
  message?: string | null
  reasonCode?: string | null
  hasCompletedReport?: boolean | null
  hasRunningRun?: boolean | null
  latestRunId?: string | null
  latestReportId?: string | null
  missingFields?: string[] | null
  missingFieldDetails?: Record<string, string[] | null> | null
}

export interface WorkbenchDataProvenanceCardQuery {
  brandSpaceId: string
  runId?: string | null
  topicId?: string | null
  topicName?: string | null
  probeId?: string | null
  anchorType?: string | null
  anchorKey?: string | null
  metricKey?: string | null
  metricLabel?: string | null
  metricValue?: string | null
  timePreset?: string | null
  dateFrom?: string | null
  dateTo?: string | null
  resultPointKey?: string | null
  windowKey?: string | null
  platform?: string | null
  domain?: string | null
  platforms?: string[] | null
  contextCitationCount?: number | null
  contextCitationDomainCount?: number | null
  contextTotalCitationCount?: number | null
  contextCitationShare?: number | null
  contextSampleSize?: number | null
  contextMentionCount?: number | null
  contextMentionRate?: number | null
  contextRecommendRate?: number | null
  contextTop3Rate?: number | null
  contextDemandVolume?: number | null
  contextDemandTotal?: number | null
  contextActiveTopicCount?: number | null
  contextActivePlatformCount?: number | null
  contextPromptCount?: number | null
  contextTotalProjection?: number | null
  contextHighValuePromptCount?: number | null
  contextPromptLoopTotalCount?: number | null
  contextPromptLoopCoveredCount?: number | null
  contextPromptLoopGapCount?: number | null
  contextPromptLoopHighOpportunityCount?: number | null
  samplePage?: number | null
  samplePageSize?: number | null
}

export interface WorkbenchDataProvenanceAnchor {
  anchorType?: string | null
  anchorKey?: string | null
  metricKey?: string | null
  metricLabel?: string | null
  metricValue?: string | null
  timeWindowLabel?: string | null
  topicId?: string | null
  topicName?: string | null
  probeId?: string | null
}

export interface WorkbenchDataProvenanceCompositionItem {
  label?: string | null
  value?: string | null
  description?: string | null
  formulaKey?: string | null
  rateValue?: number | null
  weightPercent?: number | null
  contributionValue?: number | null
  numerator?: string | null
  denominator?: string | null
  sourceMetricKey?: string | null
  source?: string | null
}

export interface WorkbenchDataProvenanceResultExplanation {
  status?: string | null
  statusLabel?: string | null
  statusNote?: string | null
  metricLabel?: string | null
  metricValue?: string | null
  timeWindowLabel?: string | null
  anchorLabel?: string | null
  meaning?: string | null
  calculation?: string | null
  limitationNote?: string | null
  composition?: WorkbenchDataProvenanceCompositionItem[] | null
}

export interface WorkbenchDataProvenanceRun {
  runId?: string | null
  status?: string | null
  generatedAt?: string | null
  model?: string | null
  engineVersion?: string | null
  targetProbeCount?: number | null
  completedProbeCount?: number | null
  failedProbeCount?: number | null
}

export interface WorkbenchDataProvenancePlanItem {
  probeId?: string | null
  topicId?: string | null
  topicName?: string | null
  seqNo?: number | null
  promptText?: string | null
  actionCode?: string | null
  executeModeCode?: string | null
  detailRouteKey?: string | null
  detailParams?: Record<string, string | null | undefined> | null
}

export interface WorkbenchDataProvenancePlan {
  planVersionId?: string | null
  planRecordId?: string | null
  planVersionNo?: number | null
  planRecordNo?: number | null
  changeTypeCode?: string | null
  executeModeCode?: string | null
  estimatedCredits?: number | null
  recurringEstimatedCreditsPerCycle?: number | null
  topicCount?: number | null
  probeCount?: number | null
  sampleProbes?: WorkbenchDataProvenancePlanItem[] | null
}

export interface WorkbenchDataProvenanceProbe {
  probeId?: string | null
  probeRef?: string | null
  seqNo?: number | null
  promptText?: string | null
  status?: string | null
  detailRouteKey?: string | null
  detailParams?: Record<string, string | null | undefined> | null
}

export interface WorkbenchDataProvenanceCitation {
  evidenceId?: string | null
  probeId?: string | null
  title?: string | null
  domain?: string | null
  url?: string | null
  sourceType?: string | null
  capturedAt?: string | null
}

export interface WorkbenchDataProvenanceBrandMention {
  probeId?: string | null
  brandName?: string | null
  mentioned?: boolean | null
  recommended?: boolean | null
  positionIndex?: number | null
  sentimentCode?: number | null
}

export interface WorkbenchDataProvenanceEvidence {
  probeCount?: number | null
  selectedProbeCount?: number | null
  attemptCount?: number | null
  citationCount?: number | null
  brandMentionCount?: number | null
  probes?: WorkbenchDataProvenanceProbe[] | null
  citations?: WorkbenchDataProvenanceCitation[] | null
  brandMentions?: WorkbenchDataProvenanceBrandMention[] | null
}

export interface WorkbenchDataProvenanceAnswerSample {
  rowNo?: number | null
  runId?: string | null
  sourceTaskId?: string | null
  generatedAt?: string | null
  probeId?: string | null
  sourceProbeId?: string | null
  attemptId?: string | null
  sourceAttemptId?: string | null
  probeRef?: string | null
  seqNo?: number | null
  topicId?: string | null
  topicName?: string | null
  promptText?: string | null
  answerSnippet?: string | null
  provider?: string | null
  modelId?: string | null
  effectiveAnswer?: boolean | null
  excludedReason?: string | null
  mentioned?: boolean | null
  recommended?: boolean | null
  top3?: boolean | null
  recommendationRank?: number | null
  detailRouteKey?: string | null
  detailProbeId?: string | null
  detailAttemptId?: string | null
}

export interface WorkbenchDataProvenanceSamplePage {
  sourceStatus?: string | null
  sourceStatusLabel?: string | null
  sourceNote?: string | null
  page?: number | null
  pageSize?: number | null
  pageCount?: number | null
  totalCount?: number | null
  effectiveAnswerCount?: number | null
  excludedAnswerCount?: number | null
  mentionHitCount?: number | null
  recommendHitCount?: number | null
  top3HitCount?: number | null
  rows?: WorkbenchDataProvenanceAnswerSample[] | null
}

export interface WorkbenchDataProvenanceLedger {
  ledgerId?: string | null
  ledgerTypeCode?: string | null
  probeDelta?: number | null
  balanceAfter?: number | null
  sourceTypeCode?: string | null
  createdAt?: string | null
  memo?: string | null
}

export interface WorkbenchDataProvenanceBilling {
  ledgerCount?: number | null
  consumedCredits?: number | null
  latestBalanceAfter?: number | null
  ledgers?: WorkbenchDataProvenanceLedger[] | null
}

export interface WorkbenchDataProvenanceCardView {
  contractVersion?: string | null
  title?: string | null
  brandSpaceId: string
  anchor?: WorkbenchDataProvenanceAnchor | null
  resultExplanation?: WorkbenchDataProvenanceResultExplanation | null
  run?: WorkbenchDataProvenanceRun | null
  plan?: WorkbenchDataProvenancePlan | null
  evidence?: WorkbenchDataProvenanceEvidence | null
  samplePage?: WorkbenchDataProvenanceSamplePage | null
  billing?: WorkbenchDataProvenanceBilling | null
}

export interface WorkbenchIdentity {
  brandSpaceId: string
  brandId?: string | null
  brandName: string
  category?: string | null
  officialWebsite?: string | null
  primaryDomain?: string | null
}

export interface WorkbenchAnalysisContext {
  timePreset?: string | null
  dateFrom?: string | null
  dateTo?: string | null
  windowKey?: string | null
  comparison?: string | null
  frequency?: string | null
  platforms?: string[] | null
  availablePlatforms?: string[] | null
  channelFilters?: Record<string, string[] | null> | null
  supportedDimensions?: string[] | null
  samplePolicy?: string | null
  truthSource?: string | null
  sourceWindowKey?: string | null
  notes?: string[] | null
}

export interface WorkbenchOverviewCard {
  cardKey: string
  title: string
  primary?: string | null
  secondary?: string | null
  detailRouteKey?: string | null
}

export interface WorkbenchCurrentPerformance {
  visibilityScore?: number | null
  competitorVisibilityAverage?: number | null
  recommendRate?: number | null
  mentionRate?: number | null
  top3Rate?: number | null
  abvGrade?: string | null
  headline?: string | null
  generatedAt?: string | null
}

export interface WorkbenchTrendPoint {
  reportId?: string | null
  runId?: string | null
  label: string
  generatedAt?: string | null
  visibilityScore?: number | null
  recommendRate?: number | null
  mentionRate?: number | null
  top3Rate?: number | null
}

export interface WorkbenchOverviewTrend {
  direction?: string | null
  deltaVisibilityScore?: number | null
  deltaRecommendRate?: number | null
  deltaMentionRate?: number | null
  points: WorkbenchTrendPoint[]
}

export interface WorkbenchInsightItem {
  title?: string | null
  evidence?: string | null
  evidenceSummary?: string | null
  recommendation?: string | null
  nextStepLabel?: string | null
  nextStepText?: string | null
  nextStepRouteKey?: string | null
}

export interface WorkbenchLatestInsight {
  generatedAt?: string | null
  engineVersion?: string | null
  runId?: string | null
  reportId?: string | null
  insightSource?: string | null
  sampleSize?: number | null
  headline?: string | null
  items?: WorkbenchInsightItem[] | null
}

export interface WorkbenchPriorityItem {
  id: string
  title: string
  summary?: string | null
  reason?: string | null
  detailRouteKey?: string | null
}

export interface WorkbenchCompetitorSummaryRow {
  id: string
  brandName: string
  rank?: number | null
  isTarget?: boolean | null
  visibilityScore?: number | null
  displayScore?: number | null
  rankingMetric?: string | null
  recommendRate?: number | null
  mentionRate?: number | null
  positiveRate?: number | null
}

export interface WorkbenchSourceSummaryRow {
  id: string
  label: string
  type?: string | null
  share?: number | null
  shareLabel?: string | null
  citationCount?: number | null
  totalCitationCount?: number | null
  url?: string | null
}

export interface WorkbenchModelSummaryRow {
  id: string
  label: string
  sampleSize?: number | null
  latestRunId?: string | null
  latestReportId?: string | null
  visibilityScore?: number | null
  recommendRate?: number | null
  mentionRate?: number | null
  top3Rate?: number | null
  deltaVisibilityScore?: number | null
  reportCount?: number | null
  primary?: boolean | null
}

export interface WorkbenchOverviewView {
  identity?: WorkbenchIdentity | null
  brandSpaceId: string
  brandId?: string | null
  brandName: string
  category?: string | null
  officialWebsite?: string | null
  primaryDomain?: string | null
  brandSpaceStatus?: string | null
  latestRunId?: string | null
  latestReportId?: string | null
  lastGeneratedAt?: string | null
  analysisContext?: WorkbenchAnalysisContext | null
  dataAvailability?: WorkbenchDataAvailability | null
  cards: WorkbenchOverviewCard[]
  currentPerformance?: WorkbenchCurrentPerformance | null
  latestInsight?: WorkbenchLatestInsight | null
  trend?: WorkbenchOverviewTrend | null
  priorities: WorkbenchPriorityItem[]
  competitorRows: WorkbenchCompetitorSummaryRow[]
  sourceRows: WorkbenchSourceSummaryRow[]
  modelPerformance: WorkbenchModelSummaryRow[]
  detailRoutes?: Record<string, string> | null
}

export interface WorkbenchBrandPerformanceSummary {
  visibilityScore?: number | null
  recommendRate?: number | null
  mentionRate?: number | null
  top3Rate?: number | null
  abvGrade?: string | null
  sampleSize?: number | null
  generatedAt?: string | null
  visibilityRank?: number | null
  visibilityRankDelta?: number | null
  shareOfVoice?: number | null
  shareOfVoiceDelta?: number | null
  averagePosition?: number | null
  averagePositionDelta?: number | null
  headline?: string | null
  issueCount?: number | null
  evidenceCount?: number | null
  topTopicLabels?: string[] | null
}

export interface WorkbenchBrandPerformanceCompetitorRow {
  id: string
  brandName: string
  rank?: number | null
  isTarget?: boolean | null
  visibilityScore?: number | null
  recommendRate?: number | null
  mentionRate?: number | null
  positiveRate?: number | null
  top3Rate?: number | null
  rankDelta?: number | null
  shareOfVoice?: number | null
  shareOfVoiceDelta?: number | null
  averagePosition?: number | null
  averagePositionDelta?: number | null
  mentionCount?: number | null
}

export interface WorkbenchBrandPerformanceModelRow {
  id: string
  label: string
  primary?: boolean | null
  reportCount?: number | null
  sampleSize?: number | null
  latestRunId?: string | null
  latestReportId?: string | null
  visibilityScore?: number | null
  recommendRate?: number | null
  mentionRate?: number | null
  top3Rate?: number | null
  deltaVisibilityScore?: number | null
}

export interface WorkbenchBrandPerformanceTopicSummary {
  topicId: string
  topicName: string
  seqNo?: number | null
  enabled?: boolean | null
  sampleSize?: number | null
  visibilityScore?: number | null
  recommendRate?: number | null
  mentionRate?: number | null
  top3Rate?: number | null
  averagePosition?: number | null
  mentionCount?: number | null
  deltaVisibilityScore?: number | null
  deltaRecommendRate?: number | null
  deltaMentionRate?: number | null
  deltaTop3Rate?: number | null
  promptCount?: number | null
  enabledPromptCount?: number | null
  promptShare?: number | null
  signalCount?: number | null
  signalShare?: number | null
  sampleQuestions?: string[] | null
}

export interface WorkbenchBrandPerformanceView {
  identity?: WorkbenchIdentity | null
  brandSpaceId: string
  brandId?: string | null
  brandName: string
  category?: string | null
  officialWebsite?: string | null
  primaryDomain?: string | null
  brandSpaceStatus?: string | null
  latestRunId?: string | null
  latestReportId?: string | null
  lastGeneratedAt?: string | null
  analysisContext?: WorkbenchAnalysisContext | null
  dataAvailability?: WorkbenchDataAvailability | null
  summary?: WorkbenchBrandPerformanceSummary | null
  competitorRows: WorkbenchBrandPerformanceCompetitorRow[]
  modelPerformance: WorkbenchBrandPerformanceModelRow[]
  trendPoints: WorkbenchTrendPoint[]
  topicSummaries: WorkbenchBrandPerformanceTopicSummary[]
}

export interface WorkbenchCitationSummary {
  totalCitations?: number | null
  ownedShare?: number | null
  thirdPartyShare?: number | null
  competitorShare?: number | null
  dominantLabel?: string | null
  dominantShare?: number | null
}

export interface WorkbenchCitationCategorySlice {
  key: string
  label: string
  share?: number | null
  used?: number | null
}

export interface WorkbenchCitationDomainRow {
  id: string
  domain: string
  type?: string | null
  used?: number | null
  avgCitations?: number | null
  share?: number | null
  delta?: number | null
  url?: string | null
}

export interface WorkbenchCitationUrlRow {
  id: string
  title?: string | null
  url: string
  domain?: string | null
  type?: string | null
  used?: number | null
  avgCitations?: number | null
  share?: number | null
  delta?: number | null
}

export interface WorkbenchCitationTrendDomainSlice {
  domain: string
  used?: number | null
  share?: number | null
  type?: string | null
  ownershipKey?: string | null
  ownershipLabel?: string | null
}

export interface WorkbenchCitationTrendPoint {
  sliceKey: string
  label: string
  generatedAt?: string | null
  totalCitations?: number | null
  ownedShare?: number | null
  thirdPartyShare?: number | null
  competitorShare?: number | null
  isLatest?: boolean | null
  topDomains?: WorkbenchCitationTrendDomainSlice[] | null
}

export interface WorkbenchCitationOwnershipDomainRow {
  domain: string
  used?: number | null
  share?: number | null
  type?: string | null
}

export interface WorkbenchCitationOwnershipSlice {
  key: string
  label: string
  share?: number | null
  used?: number | null
  domainCount?: number | null
  urlCount?: number | null
  topDomains?: WorkbenchCitationOwnershipDomainRow[] | null
}

export interface WorkbenchCitationOwnershipSummary {
  ownedShare?: number | null
  thirdPartyShare?: number | null
  competitorShare?: number | null
  otherShare?: number | null
}

export interface WorkbenchCitationOwnershipView {
  summary?: WorkbenchCitationOwnershipSummary | null
  slices: WorkbenchCitationOwnershipSlice[]
}

export interface WorkbenchCitationUrlDetailRow {
  urlId: string
  url: string
  title?: string | null
  used?: number | null
  share?: number | null
  delta?: number | null
}

export interface WorkbenchCitationDomainDetailRow {
  domainId: string
  domain: string
  type?: string | null
  ownershipKey?: string | null
  ownershipLabel?: string | null
  used?: number | null
  avgCitations?: number | null
  share?: number | null
  delta?: number | null
  urlCount?: number | null
  urls?: WorkbenchCitationUrlDetailRow[] | null
}

export interface WorkbenchCitationSourcesView {
  identity?: WorkbenchIdentity | null
  brandSpaceId: string
  brandId?: string | null
  brandName: string
  category?: string | null
  officialWebsite?: string | null
  primaryDomain?: string | null
  brandSpaceStatus?: string | null
  latestRunId?: string | null
  latestReportId?: string | null
  lastGeneratedAt?: string | null
  analysisContext?: WorkbenchAnalysisContext | null
  dataAvailability?: WorkbenchDataAvailability | null
  summary?: WorkbenchCitationSummary | null
  categorySlices: WorkbenchCitationCategorySlice[]
  domainRows: WorkbenchCitationDomainRow[]
  urlRows: WorkbenchCitationUrlRow[]
  trendPoints?: WorkbenchCitationTrendPoint[] | null
  ownership?: WorkbenchCitationOwnershipView | null
  domainDetails?: WorkbenchCitationDomainDetailRow[] | null
}

export interface WorkbenchProblemAnalysisSummary {
  topicCount?: number | null
  promptCount?: number | null
  enabledPromptCount?: number | null
  problemClusterCount?: number | null
  probeCount?: number | null
  attemptCount?: number | null
  evidenceCount?: number | null
  rawAnswerCount?: number | null
  linkedPromptCount?: number | null
  linkedProbeCount?: number | null
  linkedEvidenceCount?: number | null
}

export interface WorkbenchProblemAnalysisTopicRow {
  topicId: string
  topicName: string
  problemSpaceBucket?: string | null
  seqNo?: number | null
  enabled?: boolean | null
  promptCount?: number | null
  enabledPromptCount?: number | null
  signalCount?: number | null
  sampleQuestions?: string[] | null
}

export interface WorkbenchProblemAnalysisPromptRow {
  promptId: string
  topicId?: string | null
  topicName?: string | null
  problemSpaceBucket?: string | null
  seqNo?: number | null
  promptText: string
  enabled?: boolean | null
  probeCount?: number | null
  evidenceCount?: number | null
  hasRawAnswer?: boolean | null
  latestProbeStatus?: string | null
  visibilityRank?: number | null
  visibilityScore?: number | null
  visibilityDelta?: number | null
  averagePosition?: number | null
  averageDelta?: number | null
  citationShare?: number | null
  citationDelta?: number | null
}

export interface WorkbenchProblemAnalysisProblemCluster {
  id: string
  title: string
  summary?: string | null
  severity?: string | null
  problemSpaceBucket?: string | null
  signalCount?: number | null
  representativeQuestions?: string[] | null
}

export interface WorkbenchProblemAnalysisProbeRow {
  probeId: string
  probeRef?: string | null
  seqNo?: number | null
  topicId?: string | null
  topicName?: string | null
  problemSpaceBucket?: string | null
  promptId?: string | null
  promptText?: string | null
  status?: string | null
  evidenceCount?: number | null
  hasRawAnswer?: boolean | null
  latestAttemptId?: string | null
  model?: string | null
  answerPreview?: string | null
  rawAnswer?: string | null
  visibilityScore?: number | null
  averagePosition?: number | null
  sentimentScore?: number | null
  mentionCount?: number | null
}

export interface WorkbenchProblemAnalysisEvidenceRow {
  evidenceId: string
  attemptId?: string | null
  probeId?: string | null
  topicId?: string | null
  topicName?: string | null
  problemSpaceBucket?: string | null
  promptId?: string | null
  promptText?: string | null
  sourceType?: string | null
  domain?: string | null
  url?: string | null
  title?: string | null
  snippet?: string | null
  capturedAt?: string | null
}

export interface WorkbenchProblemAnalysisAttemptBrandHit {
  brandName: string
  score?: number | null
}

export interface WorkbenchProblemAnalysisAttemptCitation {
  evidenceId?: string | null
  title?: string | null
  url?: string | null
  domain?: string | null
}

export interface WorkbenchProblemAnalysisAttemptRow {
  attemptId: string
  probeId?: string | null
  probeRef?: string | null
  topicId?: string | null
  topicName?: string | null
  problemSpaceBucket?: string | null
  promptId?: string | null
  promptText?: string | null
  provider?: string | null
  model?: string | null
  status?: string | null
  createdAt?: string | null
  query?: string | null
  answerPreview?: string | null
  rawAnswer?: string | null
  summaryBullets?: string[] | null
  brandHits?: WorkbenchProblemAnalysisAttemptBrandHit[] | null
  citations?: WorkbenchProblemAnalysisAttemptCitation[] | null
}

export interface WorkbenchProblemAnalysisVolumeTrendPoint {
  sliceKey?: string | null
  label: string
  generatedAt?: string | null
  currentValue?: number | null
  previousValue?: number | null
  isLatest?: boolean | null
  snapshot?: WorkbenchProblemAnalysisVolumeTimeSliceSnapshot | null
}

export interface WorkbenchProblemAnalysisVolumeSummary {
  demandTotal?: number | null
  demandDelta?: number | null
  activeTopicCount?: number | null
  activePlatformCount?: number | null
}

export interface WorkbenchProblemAnalysisPlatformDemandRow {
  platform: string
  value?: number | null
  share?: number | null
  delta?: number | null
}

export interface WorkbenchProblemAnalysisVolumeTopicSliceRow {
  topicId?: string | null
  topicName: string
  problemSpaceBucket?: string | null
  demandCount?: number | null
  share?: number | null
  leadPromptText?: string | null
}

export interface WorkbenchProblemAnalysisVolumeTimeSliceSnapshot {
  summary?: WorkbenchProblemAnalysisVolumeSummary | null
  topTopics?: WorkbenchProblemAnalysisVolumeTopicSliceRow[] | null
  platformRows?: WorkbenchProblemAnalysisPlatformDemandRow[] | null
}

export interface WorkbenchProblemAnalysisPromptOpportunityRow {
  prompt: string
  topic?: string | null
  problemSpaceBucket?: string | null
  demandIndex?: number | null
  projection?: number | null
  competitionScore?: number | null
  relevanceScore?: number | null
  opportunityScore?: number | null
  intentLabel?: string | null
  trendDelta?: number | null
}

export interface WorkbenchProblemAnalysisPromptProjectionSummary {
  promptCount?: number | null
  totalProjection?: number | null
  highValuePromptCount?: number | null
}

export interface WorkbenchProblemAnalysisPromptProjectionRow {
  prompt: string
  projection?: number | null
  share?: number | null
  intentLabel?: string | null
  action?: string | null
}

export interface WorkbenchProblemAnalysisPromptLoopSummary {
  coverage?: number | null
  gapCount?: number | null
  highOpportunityCount?: number | null
}

export interface WorkbenchProblemAnalysisPromptLoopRow {
  id: string
  topic?: string | null
  problemSpaceBucket?: string | null
  prompt: string
  status?: string | null
  citedTarget?: string | null
  opportunity?: number | null
  action?: string | null
  targetBrandCited?: boolean | null
  citationCoverage?: number | null
  competitorLeadBrand?: string | null
  citedBrandNames?: string[] | null
  gapType?: string | null
}

export interface WorkbenchProblemAnalysisVolumeView {
  trendPoints?: WorkbenchProblemAnalysisVolumeTrendPoint[] | null
  summary?: WorkbenchProblemAnalysisVolumeSummary | null
  platformRows?: WorkbenchProblemAnalysisPlatformDemandRow[] | null
  promptOpportunityRows?: WorkbenchProblemAnalysisPromptOpportunityRow[] | null
  promptProjectionSummary?: WorkbenchProblemAnalysisPromptProjectionSummary | null
  promptProjectionRows?: WorkbenchProblemAnalysisPromptProjectionRow[] | null
  promptLoopSummary?: WorkbenchProblemAnalysisPromptLoopSummary | null
  promptLoopRows?: WorkbenchProblemAnalysisPromptLoopRow[] | null
}

export interface WorkbenchProblemAnalysisIntentStat {
  key: string
  label: string
  count?: number | null
  share?: number | null
  examples?: string[] | null
}

export interface WorkbenchProblemAnalysisIntentSubIntent {
  key: string
  parent?: string | null
  parentLabel?: string | null
  label: string
  count?: number | null
  share?: number | null
  change?: number | null
}

export interface WorkbenchProblemAnalysisIntentView {
  intents?: WorkbenchProblemAnalysisIntentStat[] | null
  subIntents?: WorkbenchProblemAnalysisIntentSubIntent[] | null
}

export interface WorkbenchProblemAnalysisAudienceDistributionRow {
  label: string
  share?: number | null
}

export interface WorkbenchProblemAnalysisAudiencePlatformRow {
  platform: string
  score?: number | null
  confidence?: number | null
}

export interface WorkbenchProblemAnalysisRoleView {
  roleRows?: WorkbenchProblemAnalysisAudienceDistributionRow[] | null
  budgetRows?: WorkbenchProblemAnalysisAudienceDistributionRow[] | null
  platformRows?: WorkbenchProblemAnalysisAudiencePlatformRow[] | null
}

export type WorkbenchProblemAnalysisAudienceView = WorkbenchProblemAnalysisRoleView

export interface WorkbenchProblemAnalysisDemandMiningCluster {
  label: string
  count?: number | null
  samples?: string[] | null
}

export interface WorkbenchProblemAnalysisDemandMiningTopic {
  topic: string
  problemSpaceBucket?: string | null
  demandIndex?: number | null
  clusters?: WorkbenchProblemAnalysisDemandMiningCluster[] | null
}

export interface WorkbenchProblemAnalysisDemandMiningView {
  topics?: WorkbenchProblemAnalysisDemandMiningTopic[] | null
}

export type WorkbenchProblemAnalysisHierarchyCluster = WorkbenchProblemAnalysisDemandMiningCluster
export type WorkbenchProblemAnalysisHierarchyTopic = WorkbenchProblemAnalysisDemandMiningTopic
export type WorkbenchProblemAnalysisHierarchyView = WorkbenchProblemAnalysisDemandMiningView

export interface WorkbenchProblemAnalysisOpportunityVariationRow {
  id: string
  prompt: string
  queryCount?: number | null
  delta?: number | null
}

export interface WorkbenchProblemAnalysisOpportunityPlatformInsightRow {
  platform: string
  queryCount?: number | null
  share?: number | null
}

export interface WorkbenchProblemAnalysisOpportunityPromptRow {
  id: string
  topic?: string | null
  problemSpaceBucket?: string | null
  prompt: string
  queryCount?: number | null
  avgQueriesPerExecution?: number | null
  variations?: WorkbenchProblemAnalysisOpportunityVariationRow[] | null
  platformRows?: WorkbenchProblemAnalysisOpportunityPlatformInsightRow[] | null
}

export interface WorkbenchProblemAnalysisOpportunityPlatformRow {
  rank?: number | null
  platform: string
  avgQueriesPerExecution?: number | null
  delta?: number | null
}

export interface WorkbenchProblemAnalysisOpportunityView {
  promptRows?: WorkbenchProblemAnalysisOpportunityPromptRow[] | null
  platformRows?: WorkbenchProblemAnalysisOpportunityPlatformRow[] | null
}

export type WorkbenchProblemAnalysisFanoutVariationRow = WorkbenchProblemAnalysisOpportunityVariationRow
export type WorkbenchProblemAnalysisFanoutPlatformInsightRow = WorkbenchProblemAnalysisOpportunityPlatformInsightRow
export type WorkbenchProblemAnalysisFanoutPromptRow = WorkbenchProblemAnalysisOpportunityPromptRow
export type WorkbenchProblemAnalysisFanoutPlatformRow = WorkbenchProblemAnalysisOpportunityPlatformRow
export type WorkbenchProblemAnalysisFanoutView = WorkbenchProblemAnalysisOpportunityView

export interface WorkbenchProblemAnalysisCatalogBucket {
  key: string
  label?: string | null
  description?: string | null
  topicCount?: number | null
  promptCount?: number | null
  volume?: number | null
}

export interface WorkbenchProblemAnalysisCatalog {
  problemSpaceBuckets?: WorkbenchProblemAnalysisCatalogBucket[] | null
}

export interface WorkbenchProblemAnalysisRichView {
  volume?: WorkbenchProblemAnalysisVolumeView | null
  intent?: WorkbenchProblemAnalysisIntentView | null
  roles?: WorkbenchProblemAnalysisRoleView | null
  demandMining?: WorkbenchProblemAnalysisDemandMiningView | null
  opportunity?: WorkbenchProblemAnalysisOpportunityView | null
  audience?: WorkbenchProblemAnalysisRoleView | null
  hierarchy?: WorkbenchProblemAnalysisDemandMiningView | null
  fanout?: WorkbenchProblemAnalysisOpportunityView | null
}

export interface WorkbenchProblemAnalysisView {
  identity?: WorkbenchIdentity | null
  brandSpaceId: string
  brandId?: string | null
  brandName: string
  category?: string | null
  officialWebsite?: string | null
  primaryDomain?: string | null
  brandSpaceStatus?: string | null
  latestRunId?: string | null
  latestReportId?: string | null
  lastGeneratedAt?: string | null
  analysisContext?: WorkbenchAnalysisContext | null
  dataAvailability?: WorkbenchDataAvailability | null
  summary?: WorkbenchProblemAnalysisSummary | null
  topicRows: WorkbenchProblemAnalysisTopicRow[]
  promptRows: WorkbenchProblemAnalysisPromptRow[]
  problemClusters: WorkbenchProblemAnalysisProblemCluster[]
  probeRows: WorkbenchProblemAnalysisProbeRow[]
  evidenceRows: WorkbenchProblemAnalysisEvidenceRow[]
  attemptRows?: WorkbenchProblemAnalysisAttemptRow[] | null
  catalog?: WorkbenchProblemAnalysisCatalog | null
  rich?: WorkbenchProblemAnalysisRichView | null
}
