export default defineNuxtRouteMiddleware(async (to) => {
  const requiresAuth = Boolean(to.meta.requiresAuth)
  if (!requiresAuth) {
    return
  }

  const { isAuthenticated, load } = useAuthSnapshot()
  await load()

  if (!isAuthenticated.value) {
    return navigateTo(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
  }
})
