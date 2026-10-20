const demoRoutes = new Set(['/', '/dashboard', '/engine', '/sources', '/probe-analysis', '/brands'])

export default defineNuxtRouteMiddleware((to) => {
  if (useRuntimeConfig().public.demoMode !== true) return
  const savedBrandRoute = /^\/brands\/demo-anker\/(?:settings|runs(?:\/demo-run-20261020)?)$/.test(to.path)
  if (!demoRoutes.has(to.path) && !savedBrandRoute) {
    return navigateTo('/dashboard', { replace: true })
  }
})
