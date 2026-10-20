import type { AuthSession } from '~/types/auth'
import type { BrandSpaceListItem } from '~/types/brand-space'

interface ApiResult<T> {
  code: number
  msg: string
  data: T
}

function resetAuthScopedFrontendState() {
  useState<string>('workbench-selected-brand', () => '').value = ''
  useState<string>('workbench-selected-brand-space-id', () => '').value = ''
  useState<string>('engine-selected-brand', () => '').value = ''
  useState<string>('engine-selected-brand-space-id', () => '').value = ''
  useState<BrandSpaceListItem[]>('workbench-brand-spaces-directory-items', () => []).value = []
  useState<boolean>('workbench-brand-spaces-directory-loading', () => false).value = false
  useState<boolean>('workbench-brand-spaces-directory-loaded', () => false).value = false
  useState<string | null>('workbench-brand-spaces-directory-error', () => null).value = null
  useState<string>('workbench-brand-spaces-directory-user-id', () => '').value = ''
}

function normalizeSession(payload: Partial<AuthSession> | null | undefined): AuthSession | null {
  if (!payload?.userId || !payload.username) {
    return null
  }
  return {
    userId: payload.userId,
    username: payload.username,
    role: payload.role ?? null,
    token: payload.token ?? null
  }
}

function resolveResponseStatus(error: unknown) {
  if (typeof error !== 'object' || error === null) {
    return 0
  }

  const candidate = error as {
    status?: number
    statusCode?: number
    response?: { status?: number }
  }

  return candidate.response?.status || candidate.statusCode || candidate.status || 0
}

interface UseAuthSnapshotOptions {
  autoLoad?: boolean
}

export function useAuthSnapshot(options: UseAuthSnapshotOptions = {}) {
  const { autoLoad = true } = options
  const runtimeConfig = useRuntimeConfig()
  const auth = useState<AuthSession | null>('geo-auth-snapshot', () => null)
  const hydrated = useState<boolean>('geo-auth-hydrated', () => false)
  const pending = useState<Promise<AuthSession | null> | null>('geo-auth-load-promise', () => null)
  const apiBaseUrl = resolveGeoApiBaseUrl(String(runtimeConfig.public.apiBase || '/api/v1'))

  async function requestSession() {
    try {
      const payload = await $fetch<ApiResult<AuthSession>>('/auth/session', {
        baseURL: apiBaseUrl,
        credentials: 'include'
      })
      if (payload?.code !== 0) {
        return null
      }
      return normalizeSession(payload.data)
    } catch (error) {
      const status = resolveResponseStatus(error)
      if (status !== 401 && status !== 403) {
        console.warn('Failed to restore auth session', error)
      }
      return null
    }
  }

  async function load(force = false) {
    const previousUserId = auth.value?.userId || ''

    if (import.meta.server) {
      hydrated.value = true
      return auth.value
    }

    if (!force) {
      if (auth.value) {
        hydrated.value = true
        return auth.value
      }
      if (pending.value) {
        return pending.value
      }
      if (hydrated.value) {
        return auth.value
      }
    }

    const task = (async () => {
      const snapshot = await requestSession()
      if (previousUserId && previousUserId !== (snapshot?.userId || '')) {
        resetAuthScopedFrontendState()
      }
      auth.value = snapshot
      hydrated.value = true
      pending.value = null
      return auth.value
    })()

    pending.value = task
    return task
  }

  function write(snapshot: AuthSession) {
    if (auth.value?.userId && auth.value.userId !== snapshot.userId) {
      resetAuthScopedFrontendState()
    }
    auth.value = snapshot
    hydrated.value = true
    pending.value = null
  }

  function clear() {
    resetAuthScopedFrontendState()
    auth.value = null
    hydrated.value = true
    pending.value = null
  }

  const isAuthenticated = computed(() => Boolean(auth.value?.userId))

  if (autoLoad && import.meta.client && !hydrated.value && !pending.value) {
    void load()
  }

  return {
    auth,
    hydrated,
    isAuthenticated,
    load,
    write,
    clear
  }
}
