interface ApiResult<T> {
  code: number
  msg: string
  data: T
}

export class GeoApiError extends Error {
  code: number
  status: number

  constructor(message: string, code: number, status: number) {
    super(message)
    this.name = 'GeoApiError'
    this.code = code
    this.status = status
  }
}

function normalizeRequestError(error: unknown) {
  if (error instanceof GeoApiError) {
    return error
  }

  const message = error instanceof Error && error.message ? error.message : ''
  const isNetworkError = /failed to fetch|load failed|networkerror/i.test(message)

  if (isNetworkError) {
    return new GeoApiError('网络连接失败，请确认本地后端服务可用后重试', -1, 0)
  }

  return new GeoApiError(message || '请求失败', -1, 0)
}

interface UseGeoApiOptions {
  autoLoadAuth?: boolean
}

export function useGeoApi(options: UseGeoApiOptions = {}) {
  const { autoLoadAuth = true } = options
  const runtimeConfig = useRuntimeConfig()
  const { auth, clear } = useAuthSnapshot({ autoLoad: autoLoadAuth })
  const apiBaseUrl = resolveGeoApiBaseUrl(String(runtimeConfig.public.apiBase || '/api/v1'))
  const authRequestPaths = ['/auth/login', '/auth/register', '/auth/lookup', '/auth/password-reset/request', '/auth/password-reset/confirm']

  const client = $fetch.create({
    baseURL: apiBaseUrl,
    onRequest({ options }) {
      options.credentials = 'include'
      const token = auth.value?.token
      if (token) {
        const headers = new Headers(options.headers)
        headers.set('Authorization', `Bearer ${token}`)
        options.headers = headers
      }
    },
    async onResponseError({ response }) {
      const status = response?.status || 0
      const payload = response?._data as Partial<ApiResult<unknown>> | undefined
      const code = payload?.code ?? status
      const message = payload?.msg || '请求失败'

      if (import.meta.client && (status === 401 || code === 401)) {
        clear()
        await navigateTo('/login')
      }

      const requestPath = import.meta.client && typeof response?.url === 'string'
        ? new URL(response.url, window.location.origin).pathname
        : ''
      const isAuthRequest = authRequestPaths.some(path => requestPath.endsWith(path))

      if (import.meta.client && !isAuthRequest && (status === 403 || code === 403)) {
        await navigateTo('/dashboard')
      }

      throw new GeoApiError(message, Number(code || 0), status)
    }
  })

  return async function request<T>(request: string, options?: Parameters<typeof client<T>>[1]): Promise<T> {
    let payload: ApiResult<T>

    try {
      payload = await client<ApiResult<T>>(request, options)
    } catch (error) {
      throw normalizeRequestError(error)
    }

    if (typeof payload?.code !== 'number') {
      throw new GeoApiError('响应格式错误', -1, 200)
    }

    if (payload.code !== 0) {
      throw new GeoApiError(payload.msg || '业务请求失败', payload.code, 200)
    }

    return payload.data
  }
}
