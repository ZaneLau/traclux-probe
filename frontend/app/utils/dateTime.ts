export function toTimestamp(value?: string | null) {
  if (!value) {
    return 0
  }

  const time = new Date(value).getTime()
  return Number.isNaN(time) ? 0 : time
}

export function occursWithinPastDays(value: string | null | undefined, days: number) {
  const timestamp = toTimestamp(value)
  if (!timestamp) {
    return false
  }

  const cutoff = Date.now() - days * 24 * 60 * 60 * 1000
  return timestamp >= cutoff
}

export function occursWithinNextDays(value: string | null | undefined, days: number) {
  const timestamp = toTimestamp(value)
  if (!timestamp) {
    return false
  }

  const now = Date.now()
  return timestamp >= now && timestamp <= now + days * 24 * 60 * 60 * 1000
}

export function formatDateTime(value?: string | null) {
  if (!value) {
    return '--'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(date)
}
