interface CacheEntry<T> {
  value: T
  freshUntil: number
  staleUntil: number
}

interface RequestConfig<T> {
  key: string
  request: (signal: AbortSignal) => Promise<T>
  cancelGroup?: string
  ttlMs?: number
  staleMs?: number
  timeoutMs?: number
  retries?: number
  retryDelayMs?: number
}

const inflight = new Map<string, Promise<unknown>>()
const cache = new Map<string, CacheEntry<unknown>>()
const controllersByGroup = new Map<string, AbortController>()

function wait(ms: number) {
  return new Promise<void>(resolve => setTimeout(resolve, ms))
}

function buildAbortError() {
  const err = new Error('Aborted')
  ;(err as Error & { name: string }).name = 'AbortError'
  return err
}

function withTimeout(signal: AbortSignal, timeoutMs: number) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(buildAbortError()), timeoutMs)

  const abortListener = () => controller.abort(buildAbortError())
  signal.addEventListener('abort', abortListener)

  return {
    signal: controller.signal,
    cleanup: () => {
      clearTimeout(timer)
      signal.removeEventListener('abort', abortListener)
    }
  }
}

async function runWithRetry<T>(
  fn: (signal: AbortSignal) => Promise<T>,
  signal: AbortSignal,
  retries: number,
  retryDelayMs: number
): Promise<T> {
  let attempt = 0
  // retries=0 means one attempt only
  while (true) {
    try {
      return await fn(signal)
    } catch (error) {
      if (signal.aborted) {
        throw buildAbortError()
      }

      const name = (error as Error | undefined)?.name || ''
      if (name === 'AbortError' || attempt >= retries) {
        throw error
      }

      attempt += 1
      await wait(retryDelayMs * Math.max(1, attempt))
    }
  }
}

function setCache<T>(key: string, value: T, ttlMs: number, staleMs: number) {
  const now = Date.now()
  cache.set(key, {
    value,
    freshUntil: now + Math.max(0, ttlMs),
    staleUntil: now + Math.max(0, ttlMs + staleMs)
  })
}

function getCache<T>(key: string) {
  const entry = cache.get(key) as CacheEntry<T> | undefined
  if (!entry) {
    return null
  }

  const now = Date.now()
  return {
    entry,
    isFresh: now <= entry.freshUntil,
    isStaleAllowed: now <= entry.staleUntil
  }
}

function startRequest<T>(config: RequestConfig<T>): Promise<T> {
  const {
    key,
    request,
    cancelGroup,
    ttlMs = 10_000,
    staleMs = 20_000,
    timeoutMs = 8_000,
    retries = 1,
    retryDelayMs = 120
  } = config

  if (inflight.has(key)) {
    return inflight.get(key) as Promise<T>
  }

  if (cancelGroup) {
    const previous = controllersByGroup.get(cancelGroup)
    previous?.abort(buildAbortError())
  }

  const groupController = new AbortController()
  if (cancelGroup) {
    controllersByGroup.set(cancelGroup, groupController)
  }

  const job = (async () => {
    const { signal: timeoutSignal, cleanup } = withTimeout(groupController.signal, timeoutMs)
    try {
      const value = await runWithRetry(request, timeoutSignal, retries, retryDelayMs)
      setCache(key, value, ttlMs, staleMs)
      return value
    } finally {
      cleanup()
      if (cancelGroup && controllersByGroup.get(cancelGroup) === groupController) {
        controllersByGroup.delete(cancelGroup)
      }
      inflight.delete(key)
    }
  })()

  inflight.set(key, job)
  return job
}

export function useEngineRequestManager() {
  async function run<T>(config: RequestConfig<T>): Promise<T> {
    const { key, ttlMs = 10_000, staleMs = 20_000 } = config

    const cached = getCache<T>(key)
    if (cached?.isFresh) {
      return cached.entry.value
    }

    if (cached?.isStaleAllowed) {
      // stale-while-revalidate: return stale immediately and refresh in background
      void startRequest({ ...config, ttlMs, staleMs }).catch(() => undefined)
      return cached.entry.value
    }

    return startRequest({ ...config, ttlMs, staleMs })
  }

  function clear(pattern?: RegExp) {
    if (!pattern) {
      cache.clear()
      return
    }
    for (const key of cache.keys()) {
      if (pattern.test(key)) {
        cache.delete(key)
      }
    }
  }

  function abortGroup(group: string) {
    const controller = controllersByGroup.get(group)
    controller?.abort(buildAbortError())
  }

  function snapshot() {
    return {
      inflight: inflight.size,
      cache: cache.size,
      groups: controllersByGroup.size
    }
  }

  return {
    run,
    clear,
    abortGroup,
    snapshot
  }
}

export type { RequestConfig }

export function __resetEngineRequestManagerForTest() {
  inflight.clear()
  cache.clear()
  controllersByGroup.clear()
}
