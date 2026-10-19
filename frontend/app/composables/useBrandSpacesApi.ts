import type {
  BrandSpaceArchiveItem,
  BrandSpaceArchiveListResponse,
  BrandSpaceBasicProfileUpdateRequest,
  BrandSpaceRunDetailView,
  BrandSpaceRunHistoryView,
  BrandSpaceBasicProfileView,
  BrandSpaceConfigUpdateRequest,
  BrandSpaceCreateRequest,
  BrandSpaceCreateResponse,
  BrandSpaceConfigView,
  BrandSpaceProbePlanConfirmRequest,
  BrandSpaceProbePlanExecuteOnceResponse,
  BrandSpaceProbePlanView,
  BrandSpaceListItem,
  BrandSpaceListResponse,
  BrandSpacePreview,
  BrandSpacePreviewRequest,
  BrandSpaceTrackingModeUpdateRequest
} from '~/types/brand-space'

export function useBrandSpacesApi() {
  const api = useGeoApi()

  return {
    preview(payload: BrandSpacePreviewRequest) {
      return api<BrandSpacePreview>('/brand-spaces/preview', {
        method: 'POST',
        body: payload
      })
    },
    create(payload: BrandSpaceCreateRequest) {
      return api<BrandSpaceCreateResponse>('/brand-spaces', {
        method: 'POST',
        body: payload
      })
    },
    list() {
      return api<BrandSpaceListResponse>('/brand-spaces', {
        method: 'GET'
      })
    },
    listArchived() {
      return api<BrandSpaceArchiveListResponse>('/brand-spaces/archived', {
        method: 'GET'
      })
    },
    basicProfile(brandSpaceId: string) {
      return api<BrandSpaceBasicProfileView>(`/brand-spaces/${brandSpaceId}/basic-profile`, {
        method: 'GET'
      })
    },
    updateBasicProfile(brandSpaceId: string, payload: BrandSpaceBasicProfileUpdateRequest) {
      return api<BrandSpaceBasicProfileView>(`/brand-spaces/${brandSpaceId}/basic-profile`, {
        method: 'PUT',
        body: payload
      })
    },
    config(brandSpaceId: string) {
      return api<BrandSpaceConfigView>(`/brand-spaces/${brandSpaceId}/config`, {
        method: 'GET'
      })
    },
    getProbePlan(brandSpaceId: string) {
      return api<BrandSpaceProbePlanView>(`/brand-spaces/${brandSpaceId}/probe-plan`, {
        method: 'GET'
      })
    },
    confirmProbePlan(brandSpaceId: string, payload: BrandSpaceProbePlanConfirmRequest) {
      return api<BrandSpaceProbePlanView>(`/brand-spaces/${brandSpaceId}/probe-plan/confirm`, {
        method: 'POST',
        body: payload
      })
    },
    executeProbePlanOnce(brandSpaceId: string) {
      return api<BrandSpaceProbePlanExecuteOnceResponse>(`/brand-spaces/${brandSpaceId}/probe-plan/execute-once`, {
        method: 'POST'
      })
    },
    runs(brandSpaceId: string, query?: { dateFrom?: string, dateTo?: string }) {
      return api<BrandSpaceRunHistoryView>(`/brand-spaces/${brandSpaceId}/runs`, {
        method: 'GET',
        query
      })
    },
    runDetail(brandSpaceId: string, runId: string) {
      return api<BrandSpaceRunDetailView>(`/brand-spaces/${brandSpaceId}/runs/${runId}`, {
        method: 'GET'
      })
    },
    updateConfig(brandSpaceId: string, payload: BrandSpaceConfigUpdateRequest) {
      return api<BrandSpaceConfigView>(`/brand-spaces/${brandSpaceId}/config`, {
        method: 'PUT',
        body: payload
      })
    },
    updateTrackingMode(brandSpaceId: string, payload: BrandSpaceTrackingModeUpdateRequest) {
      return api<BrandSpaceListItem>(`/brand-spaces/${brandSpaceId}/tracking-mode`, {
        method: 'PATCH',
        body: payload
      })
    },
    archive(brandSpaceId: string) {
      return api<BrandSpaceArchiveItem>(`/brand-spaces/${brandSpaceId}/archive`, {
        method: 'PATCH'
      })
    },
    restore(brandSpaceId: string) {
      return api<BrandSpaceListItem>(`/brand-spaces/${brandSpaceId}/restore`, {
        method: 'PATCH'
      })
    },
    deleteArchived(brandSpaceId: string) {
      return api<BrandSpaceArchiveItem>(`/brand-spaces/${brandSpaceId}/archived`, {
        method: 'DELETE'
      })
    }
  }
}
