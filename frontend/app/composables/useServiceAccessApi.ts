import type { ServiceAccess } from '~/types/brand-space'

export function useServiceAccessApi() {
  const api = useGeoApi()

  return {
    get() {
      return api<ServiceAccess>('/service-access', {
        method: 'GET'
      })
    }
  }
}
