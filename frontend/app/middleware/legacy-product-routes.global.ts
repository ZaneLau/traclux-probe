const LEGACY_PRODUCT_ROUTE_REDIRECTS: Record<string, string> = {
  '/tags': '/brands',
  '/filter-lab': '/dashboard',
  '/settings/account': '/dashboard',
  '/settings/tracking': '/dashboard',
  '/settings/allowance': '/dashboard',
  '/credits/purchase': '/dashboard'
}

export default defineNuxtRouteMiddleware((to) => {
  const target = LEGACY_PRODUCT_ROUTE_REDIRECTS[to.path]
  if (!target || target === to.path) {
    return
  }

  return navigateTo({
    path: target,
    query: to.query,
    hash: to.hash
  }, {
    replace: true,
    redirectCode: 301
  })
})
