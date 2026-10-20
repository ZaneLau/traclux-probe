export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  displayName?: string
  companyName?: string
  turnstileToken?: string
}

export interface AuthLookupRequest {
  username: string
}

export interface AuthLookupResponse {
  registered: boolean
  nextAction: 'login' | 'register'
}

export interface AuthSession {
  userId: string
  username: string
  role?: number | null
  token?: string | null
}

export interface LoginResponse extends AuthSession {
  token: string
}

export interface PasswordResetRequest {
  username: string
  turnstileToken?: string
}

export interface PasswordResetConfirmRequest {
  token: string
  password: string
}
