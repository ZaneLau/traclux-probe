import { useEngineDatasetRuntimeState } from './engineDatasetShared'
import { useEngineDataProvider } from './useEngineDataProvider'

let datasetInflight: Promise<void> | null = null

export function useEngineDatasetBootstrap() {
  const runtime = useEngineDatasetRuntimeState()
  const { provider } = useEngineDataProvider()

  async function ensureDataset(options?: { force?: boolean }) {
    const force = options?.force === true

    if (!force && runtime.value.hydrated) {
      return
    }

    if (datasetInflight) {
      return datasetInflight
    }

    runtime.value.loading = true
    runtime.value.error = null

    datasetInflight = (async () => {
      try {
        await provider.getDataset()
      } catch (error) {
        runtime.value.error = (error as Error)?.message || '数据集同步失败'
        throw error
      } finally {
        runtime.value.loading = false
        datasetInflight = null
      }
    })()

    return datasetInflight
  }

  return {
    runtime,
    ensureDataset
  }
}
