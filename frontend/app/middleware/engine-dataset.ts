import { useEngineDatasetBootstrap } from '~/composables/engine/useEngineDatasetBootstrap'

const ENGINE_DATASET_ROUTES = new Set([
  '/engine-theater'
])

export default defineNuxtRouteMiddleware(async (to) => {
  if (!ENGINE_DATASET_ROUTES.has(to.path)) {
    return
  }

  const { ensureDataset } = useEngineDatasetBootstrap()

  try {
    await ensureDataset()
  } catch {
    // Keep the route reachable; pages surface the degraded state themselves.
  }
})
