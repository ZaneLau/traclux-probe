export type BrandSpaceRunDatePreset = 'today' | 'yesterday' | 'last_7_days' | 'last_14_days' | 'custom'

function cloneDate(value: Date) {
  return new Date(value.getTime())
}

function startOfDay(value: Date) {
  const date = cloneDate(value)
  date.setHours(0, 0, 0, 0)
  return date
}

function shiftDays(value: Date, delta: number) {
  const date = cloneDate(value)
  date.setDate(date.getDate() + delta)
  return date
}

export function formatDateQuery(value: Date) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function resolveBrandSpaceRunDateRange(
  preset: Exclude<BrandSpaceRunDatePreset, 'custom'>,
  now = new Date()
) {
  const today = startOfDay(now)

  switch (preset) {
    case 'today':
      return {
        dateFrom: formatDateQuery(today),
        dateTo: formatDateQuery(today)
      }
    case 'yesterday': {
      const yesterday = shiftDays(today, -1)
      return {
        dateFrom: formatDateQuery(yesterday),
        dateTo: formatDateQuery(yesterday)
      }
    }
    case 'last_7_days':
      return {
        dateFrom: formatDateQuery(shiftDays(today, -6)),
        dateTo: formatDateQuery(today)
      }
    case 'last_14_days':
    default:
      return {
        dateFrom: formatDateQuery(shiftDays(today, -13)),
        dateTo: formatDateQuery(today)
      }
  }
}

export function resolveBrandSpaceRunPreset(
  dateFrom?: string | null,
  dateTo?: string | null,
  now = new Date()
): BrandSpaceRunDatePreset {
  if (!dateFrom || !dateTo) {
    return 'last_14_days'
  }

  const presets: Array<Exclude<BrandSpaceRunDatePreset, 'custom'>> = [
    'today',
    'yesterday',
    'last_7_days',
    'last_14_days'
  ]

  for (const preset of presets) {
    const range = resolveBrandSpaceRunDateRange(preset, now)
    if (range.dateFrom === dateFrom && range.dateTo === dateTo) {
      return preset
    }
  }

  return 'custom'
}
