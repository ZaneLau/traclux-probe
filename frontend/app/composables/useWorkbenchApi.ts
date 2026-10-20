import type {
  WorkbenchBrandPerformanceView,
  WorkbenchCitationSourcesView,
  WorkbenchDataProvenanceCardQuery,
  WorkbenchDataProvenanceCardView,
  WorkbenchOverviewView,
  WorkbenchProblemAnalysisView
} from '~/types/workbench'
import type { WorkbenchAnalysisRequest } from '~/composables/useWorkbenchAnalysisFilters'
import { mergeWorkbenchIdentity } from '~/composables/useWorkbenchIdentity'

interface WorkbenchProblemAnalysisScopeQuery {
  topicNames?: string[]
}

export function useWorkbenchApi() {
  const api = useGeoApi()

  function buildAnalysisQuery(context?: WorkbenchAnalysisRequest) {
    if (!context) {
      return {}
    }
    return {
      ...(context.timePreset ? { timePreset: context.timePreset } : {}),
      ...(context.dateFrom ? { dateFrom: context.dateFrom } : {}),
      ...(context.dateTo ? { dateTo: context.dateTo } : {}),
      ...(context.platforms?.length ? { platforms: context.platforms } : {}),
      ...(context.comparison ? { comparison: context.comparison } : {}),
      ...(context.frequency ? { frequency: context.frequency } : {})
    }
  }

  return {
    overview(brandSpaceId: string, context?: WorkbenchAnalysisRequest) {
      return api<WorkbenchOverviewView>('/workbench/overview', {
        method: 'GET',
        query: { brandSpaceId, ...buildAnalysisQuery(context) }
      }).then(mergeWorkbenchIdentity)
    },
    brandPerformance(brandSpaceId: string, context?: WorkbenchAnalysisRequest) {
      return api<WorkbenchBrandPerformanceView>('/workbench/brand-performance', {
        method: 'GET',
        query: { brandSpaceId, ...buildAnalysisQuery(context) }
      }).then(mergeWorkbenchIdentity)
    },
    citationSources(brandSpaceId: string, context?: WorkbenchAnalysisRequest) {
      return api<WorkbenchCitationSourcesView>('/workbench/citation-sources', {
        method: 'GET',
        query: { brandSpaceId, ...buildAnalysisQuery(context) }
      }).then(mergeWorkbenchIdentity)
    },
    problemAnalysis(brandSpaceId: string, scope?: WorkbenchProblemAnalysisScopeQuery, context?: WorkbenchAnalysisRequest) {
      return api<WorkbenchProblemAnalysisView>('/workbench/problem-analysis', {
        method: 'GET',
        query: {
          brandSpaceId,
          ...buildAnalysisQuery(context),
          ...(scope?.topicNames?.length ? { topicNames: scope.topicNames } : {})
        }
      }).then(mergeWorkbenchIdentity)
    },
    dataProvenanceCard(query: WorkbenchDataProvenanceCardQuery) {
      return api<WorkbenchDataProvenanceCardView>('/workbench/data-provenance-card', {
        method: 'GET',
        query
      })
    }
  }
}
