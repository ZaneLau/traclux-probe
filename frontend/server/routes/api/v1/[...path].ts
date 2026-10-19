// Keep the user workbench and backend on one origin, including session cookies.
export default defineEventHandler((event) => {
  const config = useRuntimeConfig(event)
  const incoming = getRequestURL(event)
  const target = new URL(incoming.pathname + incoming.search, config.apiProxyTarget)
  return proxyRequest(event, target.toString())
})
