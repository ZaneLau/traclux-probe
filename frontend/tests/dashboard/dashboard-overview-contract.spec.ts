import { describe, expect, it } from 'vitest'
import {
  buildDashboardOverviewContract,
  DASHBOARD_CARD_ORDER,
  DASHBOARD_DETAIL_LOCATION_BY_CARD,
  DASHBOARD_DETAIL_ROUTE_PATH
} from '../../app/composables/dashboard/dashboardOverviewContract'

describe('dashboardOverviewContract', () => {
  it('locks six cards count/order and unique keys', () => {
    const overview = buildDashboardOverviewContract({})
    const keys = overview.cards.map(card => card.cardKey)

    expect(overview.cards).toHaveLength(6)
    expect(keys).toEqual(DASHBOARD_CARD_ORDER)
    expect(new Set(keys).size).toBe(6)
  })

  it('ensures each card has non-empty title/primary/secondary/detailRouteKey', () => {
    const overview = buildDashboardOverviewContract({})

    overview.cards.forEach((card) => {
      expect(card.title.trim().length).toBeGreaterThan(0)
      expect(card.primary.trim().length).toBeGreaterThan(0)
      expect(card.secondary.trim().length).toBeGreaterThan(0)
      expect(Object.keys(DASHBOARD_DETAIL_ROUTE_PATH)).toContain(card.detailRouteKey)
    })
  })

  it('locks detail route mapping by card key', () => {
    const overview = buildDashboardOverviewContract({})
    const routes = Object.fromEntries(overview.cards.map(card => [card.cardKey, card.detailRouteKey]))

    expect(routes.current_performance).toBe('engine')
    expect(routes.trend).toBe('engine')
    expect(routes.priorities).toBe('probe_analysis')
    expect(routes.competitor_rank).toBe('engine')
    expect(routes.source_citations).toBe('sources')
    expect(routes.model_performance).toBe('engine')
  })

  it('locks dashboard detail location overrides by card key', () => {
    expect(DASHBOARD_DETAIL_LOCATION_BY_CARD.current_performance).toEqual({
      path: '/engine',
      query: { tab: 'topics' }
    })
    expect(DASHBOARD_DETAIL_LOCATION_BY_CARD.trend).toEqual({
      path: '/engine',
      query: { tab: 'topics' }
    })
    expect(DASHBOARD_DETAIL_LOCATION_BY_CARD.priorities).toEqual({
      path: '/probe-analysis'
    })
    expect(DASHBOARD_DETAIL_LOCATION_BY_CARD.competitor_rank).toEqual({
      path: '/engine',
      query: { tab: 'sentiment' }
    })
    expect(DASHBOARD_DETAIL_LOCATION_BY_CARD.source_citations).toEqual({
      path: '/sources'
    })
    expect(DASHBOARD_DETAIL_LOCATION_BY_CARD.model_performance).toEqual({
      path: '/engine',
      query: { tab: 'platforms' }
    })
  })
})
