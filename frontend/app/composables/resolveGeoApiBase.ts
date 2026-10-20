export function resolveGeoApiBaseUrl(rawBase: string) {
  const trimmed = rawBase.trim() || '/api/v1'
  if (/^https?:\/\//i.test(trimmed)) {
    return trimmed
  }

  const normalizedPath = trimmed.startsWith('/') ? trimmed : `/${trimmed}`
  if (import.meta.server) {
    const requestUrl = useRequestURL()
    return `${requestUrl.origin}${normalizedPath}`
  }

  return normalizedPath
}
