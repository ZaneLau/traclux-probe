import { realpathSync } from 'node:fs'
import { resolve } from 'node:path'

function resolveBooleanEnv(value: string | undefined, fallback: boolean) {
  if (value == null || value === '') {
    return fallback
  }

  const normalized = value.trim().toLowerCase()
  if (['1', 'true', 'yes', 'on'].includes(normalized)) {
    return true
  }
  if (['0', 'false', 'no', 'off'].includes(normalized)) {
    return false
  }

  return fallback
}

const demoMode = process.env.NUXT_PUBLIC_DEMO_MODE === 'true'

const engineTheaterEnabled = resolveBooleanEnv(
  process.env.NUXT_PUBLIC_ENABLE_ENGINE_THEATER,
  process.env.NODE_ENV !== 'production'
)

const devProxyApiTarget = process.env.NUXT_DEV_PROXY_API_TARGET || 'http://127.0.0.1:8080/api/v1'
const workspaceRoot = process.cwd()
const linkedNodeModulesRoot = realpathSync(resolve(workspaceRoot, 'node_modules'))
const linkedDependencyWorkspaceRoot = resolve(linkedNodeModulesRoot, '..')

// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  ...(demoMode ? {
    ssr: false,
    router: { options: { hashMode: true } },
    icon: { clientBundle: { scan: true } }
  } : {}),
  modules: [
    '@nuxt/eslint',
    '@nuxt/ui'
  ],

  devtools: {
    enabled: false
  },

  css: ['~/assets/css/main.css'],

  ui: {
    fonts: false
  },

  runtimeConfig: {
    apiProxyTarget: 'http://127.0.0.1:8080',
    public: {
      demoMode,
      apiBase: process.env.NUXT_PUBLIC_API_BASE || '/api/v1',
      engineTheaterEnabled,
      turnstileSiteKey: process.env.NUXT_PUBLIC_TURNSTILE_SITE_KEY || ''
    }
  },

  routeRules: {
    ...(!demoMode ? { '/': { redirect: '/login' } } : {}),
    '/dashboard': { ssr: false },
    '/brands': { ssr: false },
    '/brands/**': { ssr: false },
    '/engine': { ssr: false },
    '/engine-theater': { ssr: false },
    '/probe-analysis': { ssr: false },
    '/sources': { ssr: false },
    '/tags': { ssr: false },
    '/credits/**': { ssr: false },
    '/settings/**': { ssr: false }
  },

  compatibilityDate: '2025-01-15',

  nitro: {
    devProxy: {
      '/api/v1': {
        target: devProxyApiTarget,
        changeOrigin: true
      }
    }
  },

  vite: {
    server: {
      allowedHosts: true,
      fs: {
        allow: [workspaceRoot, linkedNodeModulesRoot, linkedDependencyWorkspaceRoot]
      }
    }
  },

  eslint: {
    config: {
      stylistic: {
        commaDangle: 'never',
        braceStyle: '1tbs'
      }
    }
  }
})
