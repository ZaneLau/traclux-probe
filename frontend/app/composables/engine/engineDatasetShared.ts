import type { EngineDataset } from '~/types/engine'
import { cloneEngineDataset } from '~~/shared/engine/engineDatasetBase'

interface EngineDatasetRuntime {
  mode: 'local'
  hydrated: boolean
  loading: boolean
  error: string | null
  lastSync: number
}

let runtimeSingleton: ReturnType<typeof useState<EngineDatasetRuntime>> | null = null
let datasetSingleton: ReturnType<typeof useState<EngineDataset>> | null = null

function cloneDatasetValue(dataset: EngineDataset): EngineDataset {
  return JSON.parse(JSON.stringify(dataset)) as EngineDataset
}

function replaceArray<T>(target: T[], source: T[]) {
  target.splice(0, target.length, ...source)
}

function replaceObject<T extends object>(target: T, source: T) {
  for (const key of Object.keys(target) as Array<keyof T>) {
    if (!(key in source)) {
      Reflect.deleteProperty(target as Record<PropertyKey, unknown>, key)
    }
  }

  for (const key of Object.keys(source) as Array<keyof T>) {
    target[key] = source[key]
  }
}

function replaceRecord<T>(target: Record<string, T>, source: Record<string, T>) {
  for (const key of Object.keys(target)) {
    if (!(key in source)) {
      Reflect.deleteProperty(target, key)
    }
  }

  for (const [key, value] of Object.entries(source)) {
    target[key] = value as T
  }
}

export function useEngineDatasetRuntimeState() {
  if (runtimeSingleton) {
    return runtimeSingleton
  }

  runtimeSingleton = useState<EngineDatasetRuntime>('engine-dataset-runtime', () => ({
    mode: 'local',
    hydrated: false,
    loading: false,
    error: null,
    lastSync: 0
  }))

  return runtimeSingleton
}

export function useEngineDatasetSharedState() {
  if (datasetSingleton) {
    return datasetSingleton
  }

  datasetSingleton = useState<EngineDataset>('engine-dataset-shared', () => cloneEngineDataset())

  return datasetSingleton
}

export function getEngineDatasetShared(): EngineDataset {
  return useEngineDatasetSharedState().value
}

export function markEngineDatasetHydrated(timestamp = Date.now()) {
  const runtime = useEngineDatasetRuntimeState()
  runtime.value.hydrated = true
  runtime.value.error = null
  runtime.value.lastSync = timestamp
}

export function hydrateEngineDatasetShared(dataset: EngineDataset): EngineDataset {
  const current = useEngineDatasetSharedState().value

  if (dataset === current) {
    markEngineDatasetHydrated()
    return current
  }

  const next = cloneDatasetValue(dataset)

  replaceObject(current.meta, next.meta)
  replaceArray(current.brandOptions, next.brandOptions)
  replaceRecord(current.brandKeywordSeeds, next.brandKeywordSeeds)
  replaceRecord(current.competitorByBrand, next.competitorByBrand)
  replaceRecord(current.brandPromptTopicSeeds, next.brandPromptTopicSeeds)
  replaceArray(current.platformScopeOptions, next.platformScopeOptions)
  replaceRecord(current.citationDomainSeedsByBrand, next.citationDomainSeedsByBrand)
  replaceArray(current.probeTags, next.probeTags)
  replaceRecord(current.probeSeedsByBrand, next.probeSeedsByBrand)
  replaceRecord(current.sourceDomainsByBrand, next.sourceDomainsByBrand)
  replaceRecord(current.sourceUrlsByBrand, next.sourceUrlsByBrand)
  replaceRecord(current.chatEvidenceByBrand, next.chatEvidenceByBrand)
  replaceRecord(current.brandTrackingByWorkspaceBrand, next.brandTrackingByWorkspaceBrand)
  replaceRecord(current.theaterByBrand, next.theaterByBrand)
  markEngineDatasetHydrated()

  return current
}
