<script setup lang="ts">
import { GeoApiError } from '~/composables/useGeoApi'
import type { AuthSession } from '~/types/auth'

definePageMeta({
  layout: false
})

useSeoMeta({
  title: 'Start Traclux',
  description: 'Sign in and continue to Traclux.'
})

type AuthMode = 'login' | 'register'
type EntryIntent = 'sign-in' | 'start-trial' | 'create-brand-space' | null
type AccountGateStep = 'email' | 'password' | 'reset-request' | 'reset-sent' | 'reset-confirm'

declare global {
  interface Window {
    turnstile?: {
      render: (container: HTMLElement, options: {
        'sitekey': string
        'callback': (token: string) => void
        'expired-callback': () => void
        'error-callback': () => void
        'theme': 'dark' | 'light' | 'auto'
      }) => string
      reset: (widgetId: string) => void
      remove?: (widgetId: string) => void
    }
  }
}

const MIN_PASSWORD_LENGTH = 8
const TURNSTILE_RENDER_MAX_ATTEMPTS = 40

const route = useRoute()
const runtimeConfig = useRuntimeConfig()
const toast = useToast()
const authApi = useAuthApi({ autoLoadAuth: false })
const brandSpacesApi = useBrandSpacesApi()
const { load, write } = useAuthSnapshot({ autoLoad: false })

const pending = ref(false)
const resolvingEntry = ref(false)
const showPassword = ref(false)
const turnstileContainer = ref<HTMLElement | null>(null)
const turnstileToken = ref('')
const turnstileWidgetId = ref<string | null>(null)
const turnstileRenderAttempts = ref(0)
const turnstileLoadFailed = ref(false)
const accountGateStep = ref<AccountGateStep>('email')
const form = reactive({
  username: readQueryString('email'),
  password: '',
  website: normalizeWebsiteForDisplay(readQueryString('website') || readQueryString('suggestedWebsite'))
})

const entryIntent = computed<EntryIntent>(() => normalizeEntryIntent(readQueryString('intent')))
const isResetPasswordEntry = computed(() => readQueryString('intent') === 'reset-password' && Boolean(readQueryString('token')))
const authMode = ref<AuthMode>(entryIntent.value === 'sign-in' || !entryIntent.value ? 'login' : 'register')
const resetToken = computed(() => readQueryString('token'))
const turnstileSiteKey = computed(() => String(runtimeConfig.public.turnstileSiteKey || '').trim())
const isStartEntry = computed(() => entryIntent.value === 'create-brand-space' || entryIntent.value === 'start-trial')
const emailReady = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.username.trim()))
const passwordReady = computed(() => form.password.length >= MIN_PASSWORD_LENGTH)
const needsTurnstile = computed(() =>
  Boolean(turnstileSiteKey.value)
  && (
    (accountGateStep.value === 'password' && authMode.value === 'register')
    || accountGateStep.value === 'reset-request'
  )
)

const redirectTarget = computed(() => {
  const raw = route.query.redirect
  if (typeof raw !== 'string' || !raw.startsWith('/') || raw.startsWith('/login')) {
    return null
  }

  return raw
})

const headingCopy = computed(() => {
  if (accountGateStep.value === 'reset-request') {
    return 'Reset your password'
  }
  if (accountGateStep.value === 'reset-sent') {
    return 'Check your email'
  }
  if (accountGateStep.value === 'reset-confirm') {
    return 'Create a new password'
  }
  if (isStartEntry.value && accountGateStep.value === 'email' && form.website) {
    return `Start tracking ${form.website}`
  }
  return accountGateStep.value === 'email' ? 'What is your email?' : 'Enter your password'
})
const helperCopy = computed(() => {
  if (isStartEntry.value && accountGateStep.value === 'email' && form.website) {
    return `Create your workspace to review the first AI-search questions for ${form.website}.`
  }
  if (accountGateStep.value === 'email') {
    return 'Create your account or sign in.'
  }
  return ''
})
const submitLabel = computed(() => {
  if (pending.value) {
    if (accountGateStep.value === 'reset-request') {
      return 'Sending...'
    }
    if (accountGateStep.value === 'reset-confirm') {
      return 'Updating...'
    }
    return authMode.value === 'register' ? 'Creating account...' : 'Signing in...'
  }
  if (accountGateStep.value === 'reset-request') {
    return 'Send reset link'
  }
  if (accountGateStep.value === 'reset-confirm') {
    return 'Update password'
  }
  if (isStartEntry.value) {
    const website = form.website ? ` ${form.website}` : ''
    return authMode.value === 'register' ? `Create account and preview${website}` : `Sign in and preview${website}`
  }
  return authMode.value === 'register' ? 'Create account' : 'Sign in'
})
const submitReady = computed(() => emailReady.value && passwordReady.value)
const accountSubmitReady = computed(() => {
  if (accountGateStep.value === 'email') {
    return emailReady.value
  }
  if (accountGateStep.value === 'reset-request') {
    return emailReady.value && (!needsTurnstile.value || Boolean(turnstileToken.value))
  }
  if (accountGateStep.value === 'reset-confirm') {
    return Boolean(resetToken.value) && passwordReady.value
  }
  return submitReady.value && (!needsTurnstile.value || Boolean(turnstileToken.value))
})
const successToastTitle = computed(() => {
  if (isStartEntry.value) {
    return authMode.value === 'register' ? 'Account created' : 'Signed in'
  }
  return authMode.value === 'register' ? 'Account created' : 'Signed in'
})

function readQueryString(key: string) {
  const raw = route.query[key]
  if (Array.isArray(raw)) {
    return raw[0] || ''
  }
  return typeof raw === 'string' ? raw : ''
}

function normalizeEntryIntent(raw: string): EntryIntent {
  if (raw === 'sign-in' || raw === 'start-trial' || raw === 'create-brand-space') {
    return raw
  }
  return null
}

function normalizeWebsiteForDisplay(raw: string) {
  return raw
    .replace(/\s+/g, '')
    .replace(/^https?:\/\//i, '')
    .replace(/\/+$/g, '')
    .slice(0, 200)
}

function resolvePostLoginTarget(role?: number | null) {
  if (redirectTarget.value) {
    return redirectTarget.value
  }
  return '/dashboard'
}

function resolveBrandCreationTarget() {
  const query: Record<string, string> = {}
  const website = normalizeWebsiteForDisplay(form.website)
  if (website) {
    query.officialWebsite = website
  }
  return {
    path: '/brands/new',
    query
  }
}

async function resolveAccountNextStep(session: AuthSession) {
  if (session.role === 1) {
    return { kind: 'navigate' as const, target: resolvePostLoginTarget(session.role) }
  }

  try {
    const result = await brandSpacesApi.list()
    const items = Array.isArray(result.items) ? result.items : []

    if (items.length > 0) {
      return { kind: 'navigate' as const, target: resolvePostLoginTarget(session.role) }
    }

    return { kind: 'navigate' as const, target: resolveBrandCreationTarget() }
  } catch (error) {
    console.warn('Failed to resolve start entry brand-space state', error)
    return { kind: 'navigate' as const, target: resolveBrandCreationTarget() }
  }
}

async function continueAfterAccountReady(session: AuthSession) {
  if (!isStartEntry.value && entryIntent.value !== 'sign-in') {
    await navigateTo(resolvePostLoginTarget(session.role))
    return
  }

  resolvingEntry.value = true
  try {
    const next = await resolveAccountNextStep(session)
    if (next.kind === 'navigate') {
      await navigateTo(next.target)
      return
    }

    await navigateTo(next.target)
  } finally {
    resolvingEntry.value = false
  }
}

useHead(() => turnstileSiteKey.value
  ? {
      script: [
        {
          src: 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit',
          async: true,
          defer: true
        }
      ]
    }
  : {})

function resolveSafeAuthErrorMessage(error: unknown, fallback: string) {
  if (error instanceof GeoApiError) {
    if (error.code === 1001) {
      return 'Continue by signing in with this email.'
    }
    if (error.code === 1002) {
      return 'The email or password is not correct.'
    }
    if (error.code === 1003 || error.status === 429) {
      return 'Too many attempts. Please wait a moment and try again.'
    }
    if (error.status === 403 || error.code === 403) {
      return 'Complete the security check and try again.'
    }
    if (error.code === 400) {
      return `Use a valid email and a password with at least ${MIN_PASSWORD_LENGTH} characters.`
    }
    if (error.status === 0) {
      return 'We could not reach Traclux. Please check your connection and try again.'
    }
  }
  return fallback
}

function isInvalidCredentialError(error: unknown) {
  return error instanceof GeoApiError && error.code === 1002
}

function resolveDisplayName() {
  const username = form.username.trim()
  return username.includes('@') ? username.split('@')[0] : username
}

async function renderTurnstile() {
  if (!needsTurnstile.value || !turnstileContainer.value || turnstileWidgetId.value) {
    return
  }
  if (turnstileLoadFailed.value) {
    return
  }
  if (!window.turnstile) {
    turnstileRenderAttempts.value += 1
    if (turnstileRenderAttempts.value >= TURNSTILE_RENDER_MAX_ATTEMPTS) {
      turnstileLoadFailed.value = true
      return
    }
    window.setTimeout(() => {
      void renderTurnstile()
    }, 250)
    return
  }
  turnstileWidgetId.value = window.turnstile.render(turnstileContainer.value, {
    'sitekey': turnstileSiteKey.value,
    'theme': 'dark',
    'callback': (token) => {
      turnstileLoadFailed.value = false
      turnstileToken.value = token
    },
    'expired-callback': () => {
      turnstileToken.value = ''
    },
    'error-callback': () => {
      turnstileToken.value = ''
      turnstileLoadFailed.value = true
    }
  })
}

function resetTurnstile() {
  turnstileToken.value = ''
  turnstileRenderAttempts.value = 0
  turnstileLoadFailed.value = false
  if (!turnstileWidgetId.value || !window.turnstile) {
    turnstileWidgetId.value = null
    return
  }
  window.turnstile.remove?.(turnstileWidgetId.value)
  turnstileWidgetId.value = null
}

async function syncTurnstile() {
  if (!import.meta.client) {
    return
  }
  if (!needsTurnstile.value) {
    resetTurnstile()
    return
  }
  await nextTick()
  await renderTurnstile()
}

async function retryTurnstile() {
  turnstileToken.value = ''
  turnstileRenderAttempts.value = 0
  turnstileLoadFailed.value = false
  if (turnstileWidgetId.value && window.turnstile) {
    window.turnstile.remove?.(turnstileWidgetId.value)
    turnstileWidgetId.value = null
  }
  await syncTurnstile()
}

async function continueFromEmail() {
  if (!emailReady.value) {
    toast.add({
      title: 'Enter a valid work email',
      color: 'error'
    })
    return
  }
  pending.value = true
  try {
    const result = await authApi.lookup({ username: form.username.trim() })
    authMode.value = result.nextAction === 'login' ? 'login' : 'register'
    form.password = ''
    showPassword.value = false
    accountGateStep.value = 'password'
  } catch (error) {
    toast.add({
      title: 'Could not continue',
      description: resolveSafeAuthErrorMessage(error, 'Please try again in a moment.'),
      color: 'error'
    })
  } finally {
    pending.value = false
  }
}

function returnToEmailStep() {
  form.password = ''
  showPassword.value = false
  resetTurnstile()
  accountGateStep.value = 'email'
}

function startPasswordReset() {
  form.password = ''
  showPassword.value = false
  accountGateStep.value = 'reset-request'
}

async function requestPasswordReset() {
  if (!emailReady.value) {
    toast.add({
      title: 'Enter a valid work email',
      color: 'error'
    })
    return
  }
  if (needsTurnstile.value && !turnstileToken.value) {
    toast.add({
      title: 'Complete the security check',
      color: 'error'
    })
    return
  }

  pending.value = true
  try {
    await authApi.requestPasswordReset({
      username: form.username.trim(),
      turnstileToken: turnstileToken.value || undefined
    })
    resetTurnstile()
    accountGateStep.value = 'reset-sent'
  } catch (error) {
    resetTurnstile()
    toast.add({
      title: 'Could not send reset link',
      description: resolveSafeAuthErrorMessage(error, 'Please try again in a moment.'),
      color: 'error'
    })
  } finally {
    pending.value = false
  }
}

async function confirmPasswordReset() {
  if (!resetToken.value || !passwordReady.value) {
    toast.add({
      title: 'Password is incomplete',
      description: `Use at least ${MIN_PASSWORD_LENGTH} characters.`,
      color: 'error'
    })
    return
  }

  pending.value = true
  try {
    await authApi.confirmPasswordReset({
      token: resetToken.value,
      password: form.password
    })
    toast.add({
      title: 'Password updated',
      color: 'success'
    })
    form.password = ''
    showPassword.value = false
    authMode.value = 'login'
    accountGateStep.value = emailReady.value ? 'password' : 'email'
    await navigateTo({
      path: '/login',
      query: emailReady.value ? { email: form.username.trim(), intent: 'sign-in' } : { intent: 'sign-in' }
    }, { replace: true })
  } catch (error) {
    toast.add({
      title: 'Could not update password',
      description: resolveSafeAuthErrorMessage(error, 'Please request a new link.'),
      color: 'error'
    })
  } finally {
    pending.value = false
  }
}

async function handleAccountSubmit() {
  if (accountGateStep.value === 'email') {
    await continueFromEmail()
    return
  }
  if (accountGateStep.value === 'reset-request') {
    await requestPasswordReset()
    return
  }
  if (accountGateStep.value === 'reset-confirm') {
    await confirmPasswordReset()
    return
  }
  await handleSubmit()
}

async function handleSubmit() {
  if (!submitReady.value) {
    toast.add({
      title: 'Account details incomplete',
      description: `Use a valid email and a password with at least ${MIN_PASSWORD_LENGTH} characters.`,
      color: 'error'
    })
    return
  }

  if (needsTurnstile.value && !turnstileToken.value) {
    toast.add({
      title: 'Complete the security check',
      color: 'error'
    })
    return
  }

  pending.value = true

  const payload = {
    username: form.username.trim(),
    password: form.password
  }

  try {
    const authResult = authMode.value === 'register'
      ? await authApi.register({
          ...payload,
          displayName: resolveDisplayName(),
          companyName: normalizeWebsiteForDisplay(form.website) || undefined,
          turnstileToken: turnstileToken.value || undefined
        })
      : await authApi.login(payload)
    resetTurnstile()
    write(authResult)
    toast.add({
      title: successToastTitle.value,
      color: 'success'
    })
    await continueAfterAccountReady(authResult)
    return
  } catch (error) {
    resetTurnstile()
    toast.add({
      title: authMode.value === 'register' ? 'Could not create account' : 'Could not sign in',
      description: isInvalidCredentialError(error)
        ? 'The email or password is not correct.'
        : resolveSafeAuthErrorMessage(error, 'Please try again in a moment.'),
      color: 'error'
    })
  } finally {
    pending.value = false
  }
}

onMounted(async () => {
  if (isResetPasswordEntry.value) {
    accountGateStep.value = 'reset-confirm'
    authMode.value = 'login'
    form.password = ''
    return
  }
  const session = await load()
  if (isStartEntry.value) {
    if (session) {
      await continueAfterAccountReady(session)
    }
    return
  }
  if (!session) {
    return
  }
  if (entryIntent.value === 'sign-in') {
    await continueAfterAccountReady(session)
    return
  }
  await navigateTo(resolvePostLoginTarget(session.role))
})

watch(
  () => [needsTurnstile.value, accountGateStep.value, authMode.value],
  () => {
    void syncTurnstile()
  },
  { flush: 'post' }
)
</script>

<template>
  <main class="login-page">
    <video
      class="traclux-login-video"
      autoplay
      muted
      loop
      playsinline
      preload="metadata"
      poster="/media/landing/aurora-poster-desktop.jpg"
      aria-hidden="true"
    >
      <source
        src="/media/landing/aurora-mobile-720x1280-natural-loop.mp4"
        media="(max-width: 640px)"
        type="video/mp4"
      >
      <source
        src="/media/landing/aurora-desktop-1080p-natural-loop.mp4"
        type="video/mp4"
      >
    </video>
    <div class="traclux-video-vignette" />
    <div class="traclux-video-scrim" />

    <div class="login-shell">
      <section class="login-card">
        <div class="login-card__brand">
          <a
            class="login-card__home-link"
            href="https://www.traclux.com/"
            aria-label="Back to Traclux homepage"
          >
            <img
              class="login-card__logo"
              src="/identity/traclux-logo-light.svg"
              alt="Traclux"
            >
          </a>
        </div>

        <h1 class="login-card__title">
          {{ headingCopy }}
        </h1>

        <p
          v-if="helperCopy"
          class="login-card__copy"
        >
          {{ helperCopy }}
        </p>

        <form
          class="login-form"
          @submit.prevent="handleAccountSubmit"
        >
          <label
            v-if="accountGateStep === 'email' || accountGateStep === 'reset-request'"
            class="login-field"
          >
            <span class="login-field__label">Email</span>
            <div
              class="login-field__control"
              :class="{ 'login-field__control--active': emailReady }"
            >
              <input
                v-model="form.username"
                class="login-field__input"
                type="email"
                autocomplete="email"
                placeholder="name@company.com"
              >
            </div>
          </label>

          <div
            v-else-if="accountGateStep === 'password'"
            class="account-return"
          >
            <span>{{ form.username }}</span>
            <button
              type="button"
              @click="returnToEmailStep"
            >
              Change
            </button>
          </div>

          <template v-if="accountGateStep === 'password' || accountGateStep === 'reset-confirm'">
            <input
              v-model="form.username"
              autocomplete="username"
              class="sr-only"
              name="username"
              tabindex="-1"
              type="text"
            >

            <label class="login-field">
              <span class="login-field__label">{{ authMode === 'register' || accountGateStep === 'reset-confirm' ? 'Create password' : 'Password' }}</span>
              <div
                class="login-field__control"
                :class="{ 'login-field__control--active': passwordReady }"
              >
                <input
                  v-model="form.password"
                  class="login-field__input"
                  :type="showPassword ? 'text' : 'password'"
                  :autocomplete="authMode === 'register' ? 'new-password' : 'current-password'"
                  :placeholder="`At least ${MIN_PASSWORD_LENGTH} characters`"
                >
                <button
                  type="button"
                  class="login-field__toggle"
                  @click="showPassword = !showPassword"
                >
                  <UIcon
                    :name="showPassword ? 'i-lucide-eye-off' : 'i-lucide-eye'"
                    class="size-4"
                  />
                </button>
              </div>
            </label>

            <button
              v-if="authMode === 'login' && accountGateStep === 'password'"
              type="button"
              class="login-link-button"
              @click="startPasswordReset"
            >
              Forgot password?
            </button>
          </template>

          <div
            v-if="needsTurnstile"
            ref="turnstileContainer"
            class="turnstile-slot"
          >
            <div
              v-if="turnstileLoadFailed"
              class="turnstile-fallback"
            >
              <span>Security check unavailable.</span>
              <button
                type="button"
                @click="retryTurnstile"
              >
                Retry
              </button>
            </div>
          </div>

          <div
            v-if="accountGateStep === 'reset-sent'"
            class="account-state"
          >
            <p>If an account exists, we sent a reset link.</p>
            <button
              type="button"
              class="login-form__submit"
              @click="returnToEmailStep"
            >
              Back to sign in
            </button>
          </div>

          <template v-else>
            <button
              type="submit"
              class="login-form__submit"
              :disabled="pending || !accountSubmitReady"
            >
              <span
                v-if="pending"
                class="login-form__spinner"
                aria-hidden="true"
              />
              {{ accountGateStep === 'email' ? 'Continue' : submitLabel }}
            </button>
          </template>
        </form>
      </section>

      <aside
        class="login-proof"
        aria-hidden="true"
      />
    </div>
  </main>
</template>

<style scoped>
@font-face {
  font-family: "Instrument Sans";
  font-display: swap;
  font-style: normal;
  font-weight: 100 900;
  src: url("/landing-nexus/fonts/InstrumentSansVariable.woff2") format("woff2");
}

@font-face {
  font-family: "Instrument Serif";
  font-display: swap;
  font-style: normal;
  font-weight: 400;
  src: url("/media/landing/fonts/instrument-serif-latin.woff2") format("woff2");
}

@font-face {
  font-family: "JetBrains Mono";
  font-display: swap;
  font-style: normal;
  font-weight: 100 800;
  src: url("/media/landing/fonts/jetbrains-mono-latin.woff2") format("woff2");
}

.login-page {
  position: relative;
  isolation: isolate;
  display: grid;
  min-height: 100vh;
  place-items: center;
  overflow: hidden;
  background: #05090d;
  color: #e6f7f4;
  font-family: "Instrument Sans", system-ui, sans-serif;
  padding: 18px;
}

.login-page::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
  background: linear-gradient(180deg, rgba(5, 9, 13, 0) 74%, #05090d 100%);
}

.traclux-login-video {
  position: absolute;
  inset: 0;
  z-index: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: saturate(1.12) brightness(0.96) contrast(1.04);
}

.traclux-video-vignette,
.traclux-video-scrim {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
}

.traclux-video-vignette {
  background:
    linear-gradient(90deg, rgba(3, 7, 10, 0.7) 0%, rgba(4, 9, 12, 0.28) 42%, rgba(3, 7, 10, 0.42) 100%),
    linear-gradient(180deg, rgba(3, 7, 10, 0.12) 0%, rgba(3, 7, 10, 0.16) 58%, rgba(3, 7, 10, 0.78) 100%);
}

.traclux-video-scrim {
  background:
    linear-gradient(180deg, rgba(5, 9, 13, 0.08) 0%, rgba(5, 9, 13, 0) 30%, rgba(5, 9, 13, 0.42) 100%),
    radial-gradient(ellipse at 50% 48%, transparent 0%, rgba(3, 7, 10, 0.06) 58%, rgba(3, 7, 10, 0.32) 100%);
}

.login-shell {
  position: relative;
  z-index: 3;
  display: grid;
  grid-template-columns: minmax(500px, 0.74fr) minmax(360px, 1fr);
  width: min(100%, 1440px);
  min-height: calc(100vh - 36px);
  overflow: hidden;
  border: 1px solid rgba(178, 244, 233, 0.12);
  border-radius: 28px;
  background: rgba(7, 17, 22, 0.62);
  box-shadow:
    0 24px 80px rgba(0, 0, 0, 0.3),
    inset 0 1px 0 rgba(218, 255, 248, 0.08);
  backdrop-filter: blur(24px) saturate(126%);
}

.login-card {
  position: relative;
  box-sizing: border-box;
  width: 100%;
  max-height: calc(100vh - 36px);
  overflow: auto;
  border-right: 1px solid rgba(178, 244, 233, 0.1);
  background: rgba(5, 10, 13, 0.66);
  padding: 56px 64px;
}

.login-card__brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  max-width: 100%;
  margin-bottom: 44px;
}

.login-card__home-link {
  display: inline-flex;
  align-items: center;
  border-radius: 8px;
  opacity: 0.92;
  transition: opacity 160ms ease, filter 160ms ease;
}

.login-card__home-link:hover,
.login-card__home-link:focus-visible {
  opacity: 1;
  outline: none;
  filter: drop-shadow(0 0 12px rgba(91, 255, 215, 0.18));
}

.login-card__logo {
  display: block;
  width: auto;
  height: 22px;
}

.login-card__badge {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  border: 1px solid rgba(230, 247, 244, 0.28);
  border-radius: 999px;
  color: rgba(230, 247, 244, 0.78);
  font-family: "JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  line-height: 1;
  padding: 0 8px;
  text-transform: uppercase;
}

.login-card__title {
  max-width: min(100%, 22ch);
  margin: 0;
  color: #f6fffc;
  font-family: "Instrument Sans", system-ui, sans-serif;
  font-size: clamp(28px, 3vw, 38px);
  font-weight: 720;
  letter-spacing: -0.035em;
  line-height: 1.06;
}

.login-card__copy {
  max-width: 35ch;
  margin: 12px 0 0;
  color: rgba(226, 247, 242, 0.72);
  font-size: 15px;
  line-height: 1.7;
}

.account-return,
.login-field__control {
  border: 1px solid rgba(178, 244, 233, 0.12);
  background: rgba(7, 17, 22, 0.52);
  box-shadow: inset 0 1px 0 rgba(218, 255, 248, 0.04);
}

.login-form {
  margin-top: 36px;
}

.login-field {
  display: block;
  margin-bottom: 18px;
}

.login-field__label {
  display: block;
  margin-bottom: 10px;
  color: rgba(230, 247, 244, 0.88);
  font-size: 13px;
  font-weight: 650;
  line-height: 1;
}

.login-field__control {
  display: flex;
  align-items: center;
  min-height: 58px;
  border-radius: 18px;
  padding: 0 18px;
  transition: box-shadow 160ms ease, background 160ms ease;
}

.login-field__control:hover {
  background: rgba(7, 17, 22, 0.62);
}

.login-field__control:focus-within {
  background: rgba(7, 17, 22, 0.68);
  box-shadow:
    0 0 0 1px rgba(108, 255, 219, 0.52),
    0 0 28px rgba(96, 242, 211, 0.2),
    inset 0 1px 0 rgba(218, 255, 248, 0.08);
}

.login-field__control--active:not(:focus-within) {
  box-shadow:
    inset 0 1px 0 rgba(218, 255, 248, 0.08),
    inset 0 0 0 1px rgba(108, 255, 219, 0.18);
}

.login-field__input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: #f6fffc;
  font-family: "Instrument Sans", system-ui, sans-serif;
  font-size: 16px;
}

.login-field__input::placeholder {
  color: rgba(163, 199, 194, 0.72);
}

.login-field__toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  background: transparent;
  color: rgba(186, 244, 234, 0.72);
  cursor: pointer;
}

.login-link-button {
  display: inline-flex;
  margin: -2px 0 20px;
  border: 0;
  background: transparent;
  color: rgba(91, 255, 215, 0.9);
  cursor: pointer;
  font-size: 13px;
  font-weight: 800;
}

.login-link-button:hover,
.login-link-button:focus-visible {
  color: #f6fffc;
}

.turnstile-slot {
  display: flex;
  align-items: center;
  min-height: 65px;
  margin: 2px 0 18px;
}

.turnstile-fallback {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.08);
  padding: 14px 16px;
  color: rgba(226, 247, 242, 0.8);
  font-size: 13px;
}

.turnstile-fallback button {
  border: 0;
  background: transparent;
  color: rgba(91, 255, 215, 0.94);
  cursor: pointer;
  font-weight: 800;
}

.turnstile-fallback button:hover,
.turnstile-fallback button:focus-visible {
  color: #f6fffc;
}

.account-state {
  display: grid;
  gap: 24px;
}

.account-state p {
  margin: 0;
  color: rgba(226, 247, 242, 0.76);
  font-size: 14px;
  line-height: 1.6;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}

.account-return {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 20px;
  border-radius: 18px;
  padding: 13px 14px;
}

.account-return span {
  overflow: hidden;
  color: rgba(246, 255, 252, 0.94);
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-return button {
  border: 0;
  background: transparent;
  color: #5bffd7;
  cursor: pointer;
  font-size: 13px;
  font-weight: 800;
}

.login-form__submit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  min-height: 58px;
  margin-top: 20px;
  border: 0;
  border-radius: 18px;
  background: #5bffd7;
  box-shadow: 0 0 30px rgba(91, 255, 215, 0.18);
  color: #05100d;
  cursor: pointer;
  font-size: 16px;
  font-weight: 800;
  transition: filter 160ms ease, transform 160ms ease, box-shadow 160ms ease;
}

.login-form__submit:hover:not(:disabled),
.login-form__submit:focus-visible:not(:disabled) {
  filter: brightness(0.94);
  transform: translateY(-1px);
}

.login-form__submit:disabled {
  background: rgba(178, 244, 233, 0.18);
  box-shadow: none;
  color: rgba(230, 247, 244, 0.46);
  cursor: not-allowed;
}

.login-form__spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(5, 16, 13, 0.28);
  border-top-color: rgba(5, 16, 13, 0.9);
  border-radius: 999px;
  animation: login-spin 0.9s linear infinite;
}

.login-proof {
  position: relative;
  min-height: 100%;
  overflow: hidden;
  padding: 48px;
}

.login-proof::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(circle at 50% 35%, rgba(91, 255, 215, 0.14), transparent 32%),
    radial-gradient(circle at 72% 62%, rgba(157, 215, 255, 0.12), transparent 28%),
    linear-gradient(90deg, rgba(5, 9, 13, 0.42), rgba(5, 9, 13, 0));
}

@keyframes login-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 920px) {
  .login-shell {
    grid-template-columns: minmax(0, 1fr);
  }

  .login-card {
    max-height: none;
    border-right: 0;
  }

  .login-proof {
    min-height: 360px;
    border-top: 1px solid rgba(178, 244, 233, 0.1);
  }
}

@media (max-width: 640px) {
  .login-page {
    align-items: start;
    justify-items: stretch;
    padding: 18px;
  }

  .login-shell {
    display: block;
    min-height: auto;
    border-radius: 28px;
  }

  .login-card {
    width: 100%;
    max-width: none;
    max-height: none;
    border-right: 0;
    border-radius: 28px;
    padding: 26px 20px;
  }

  .login-card__brand {
    margin-bottom: 26px;
  }

  .login-card__title {
    font-size: 32px;
  }

  .login-proof {
    display: none;
  }
}
</style>
