import type { EngineDimension, Granularity, TimeWindow } from '~/types/engine'

export function resolvePointCount(window: TimeWindow, granularity: Granularity) {
  if (granularity === 'day') {
    return window === '7d' ? 7 : (window === '30d' ? 10 : 14)
  }
  if (granularity === 'week') {
    return window === '7d' ? 4 : (window === '30d' ? 8 : 12)
  }
  return window === '7d' ? 3 : (window === '30d' ? 6 : 9)
}

export function buildTimeLabels(count: number, granularity: Granularity) {
  if (granularity === 'day') {
    return Array.from({ length: count }, (_, index) => `04/${`${index + 1}`.padStart(2, '0')}`)
  }
  if (granularity === 'week') {
    return Array.from({ length: count }, (_, index) => `W${index + 1}`)
  }
  const months = ['01', '02', '03', '04', '05', '06', '07', '08', '09', '10', '11', '12']
  return Array.from({ length: count }, (_, index) => `${months[index % 12]}月`)
}

export function resolveDimensionBias(dimension: EngineDimension) {
  const map: Record<EngineDimension, number> = {
    'visibility': 3.2,
    'prompts': 1.8,
    'platforms': 0.6,
    'sentiment': -0.9,
    'citations': 2.4,
    'query-fanouts': 1.2
  }

  return map[dimension]
}

export function resolveBrandBias(brand: string) {
  return {
    摩恩: 3.8,
    卡贝: 2.1,
    正泰: 4.2,
    美厨物语: 1.4
  }[brand] || 0
}

export function buildSeed(inputs: string[]) {
  return inputs.join('|').split('').reduce((acc, char, index) => acc + char.charCodeAt(0) * (index + 1), 0)
}

export function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value))
}

export function clampRank(value: number) {
  return Math.max(1, Math.min(20, value))
}

export function round1(value: number) {
  return Number(value.toFixed(1))
}

export function formatSigned(value: number | null, suffix = '') {
  if (value == null || Number.isNaN(value)) {
    return '--'
  }
  const sign = value > 0 ? '+' : ''
  return `${sign}${value.toFixed(1)}${suffix}`
}

export function toggleValue(current: string[], value: string) {
  return current.includes(value)
    ? current.filter(item => item !== value)
    : [...current, value]
}

export function resolveScopeLabel(fallback: string, selected: string[], all: string[]) {
  if (!selected.length || selected.length === all.length) {
    return fallback
  }
  if (selected.length === 1) {
    return selected[0] || fallback
  }
  return `${selected[0]} +${selected.length - 1}`
}

export function resolveAssetShortLabel(asset: string) {
  const initials = asset.match(/[A-Z]/g)
  if (initials && initials.length >= 2) {
    return initials.slice(0, 2).join('')
  }
  if (initials && initials.length === 1) {
    return initials[0]
  }
  const compact = asset.replace(/\s+/g, '')
  return compact.slice(0, 2).toUpperCase()
}
