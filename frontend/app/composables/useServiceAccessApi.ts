import type { ServiceAccess } from '~/types/brand-space'

export function useServiceAccessApi() {
  const api = useGeoApi()
  const demoMode = useRuntimeConfig().public.demoMode === true

  return {
    get() {
      if (demoMode) return Promise.resolve<ServiceAccess>({ status: 'active', planName: '只读演示' })
      return api<ServiceAccess>('/service-access', {
        method: 'GET'
      })
    }
  }
}
