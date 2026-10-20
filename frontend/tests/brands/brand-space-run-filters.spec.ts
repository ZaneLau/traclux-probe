import { describe, expect, it } from 'vitest'
import {
  formatDateQuery,
  resolveBrandSpaceRunDateRange,
  resolveBrandSpaceRunPreset
} from '../../app/utils/brandSpaceRunFilters'

describe('brandSpaceRunFilters', () => {
  const baseDate = new Date('2026-05-09T10:30:00+08:00')

  it('builds today and rolling window ranges in query format', () => {
    expect(formatDateQuery(baseDate)).toBe('2026-05-09')
    expect(resolveBrandSpaceRunDateRange('today', baseDate)).toEqual({
      dateFrom: '2026-05-09',
      dateTo: '2026-05-09'
    })
    expect(resolveBrandSpaceRunDateRange('last_7_days', baseDate)).toEqual({
      dateFrom: '2026-05-03',
      dateTo: '2026-05-09'
    })
    expect(resolveBrandSpaceRunDateRange('last_14_days', baseDate)).toEqual({
      dateFrom: '2026-04-26',
      dateTo: '2026-05-09'
    })
  })

  it('recognizes preset windows and falls back to custom when needed', () => {
    expect(resolveBrandSpaceRunPreset('2026-05-09', '2026-05-09', baseDate)).toBe('today')
    expect(resolveBrandSpaceRunPreset('2026-05-08', '2026-05-08', baseDate)).toBe('yesterday')
    expect(resolveBrandSpaceRunPreset('2026-05-03', '2026-05-09', baseDate)).toBe('last_7_days')
    expect(resolveBrandSpaceRunPreset('2026-05-01', '2026-05-09', baseDate)).toBe('custom')
  })
})
