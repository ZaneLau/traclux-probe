import type {
  AuthLookupRequest,
  AuthLookupResponse,
  LoginRequest,
  LoginResponse,
  PasswordResetConfirmRequest,
  PasswordResetRequest,
  RegisterRequest
} from '~/types/auth'

interface UseAuthApiOptions {
  autoLoadAuth?: boolean
}

export function useAuthApi(options: UseAuthApiOptions = {}) {
  const api = useGeoApi({ autoLoadAuth: options.autoLoadAuth })

  return {
    lookup(payload: AuthLookupRequest) {
      return api<AuthLookupResponse>('/auth/lookup', {
        method: 'POST',
        body: payload
      })
    },
    login(payload: LoginRequest) {
      return api<LoginResponse>('/auth/login', {
        method: 'POST',
        body: payload
      })
    },
    register(payload: RegisterRequest) {
      return api<LoginResponse>('/auth/register', {
        method: 'POST',
        body: payload
      })
    },
    requestPasswordReset(payload: PasswordResetRequest) {
      return api<null>('/auth/password-reset/request', {
        method: 'POST',
        body: payload
      })
    },
    confirmPasswordReset(payload: PasswordResetConfirmRequest) {
      return api<null>('/auth/password-reset/confirm', {
        method: 'POST',
        body: payload
      })
    },
    logout() {
      return api<null>('/auth/logout', {
        method: 'POST'
      })
    }
  }
}
