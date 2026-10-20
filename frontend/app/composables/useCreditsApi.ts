import type { CreditBalanceResponse } from '~/types/credits'

export function useCreditsApi() {
  const api = useGeoApi()

  return {
    getBalance() {
      return api<CreditBalanceResponse>('/credits/balance', {
        method: 'GET'
      })
    }
  }
}
