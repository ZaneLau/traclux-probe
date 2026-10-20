function normalizeFeatureFlag(value: unknown, fallback = false) {
  if (typeof value === 'boolean') {
    return value
  }

  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase()
    if (['1', 'true', 'yes', 'on'].includes(normalized)) {
      return true
    }
    if (['0', 'false', 'no', 'off'].includes(normalized)) {
      return false
    }
  }

  return fallback
}

export function useFeatureAvailability() {
  const runtimeConfig = useRuntimeConfig()

  const isEngineTheaterEnabled = computed(() => {
    return normalizeFeatureFlag(runtimeConfig.public.engineTheaterEnabled, false)
  })

  return {
    isEngineTheaterEnabled
  }
}
