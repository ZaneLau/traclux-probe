import type { RunEventsResponse, RunSummary } from '~/types/brand-space'

export function useRunsApi() {
  const api = useGeoApi()

  return {
    get(runId: string) {
      return api<RunSummary>(`/runs/${runId}`, {
        method: 'GET'
      })
    },
    events(runId: string) {
      return api<RunEventsResponse>(`/runs/${runId}/events`, {
        method: 'GET'
      })
    }
  }
}
