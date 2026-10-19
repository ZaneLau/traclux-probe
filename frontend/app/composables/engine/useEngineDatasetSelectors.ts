import { computed, type ComputedRef, type Ref } from 'vue'
import type {
  BrandTrackingSeed,
  ChatEvidenceSeed,
  EngineDataset,
  ProbeSeed,
  SourceDomainSeed,
  SourceUrlSeed,
  TheaterBrandDataset
} from '~/types/engine'

export function useEngineDatasetSelectors(
  dataset: EngineDataset,
  selectedBrand: Ref<string> | ComputedRef<string>
) {
  const probes = computed<ProbeSeed[]>(() => dataset.probeSeedsByBrand[selectedBrand.value] || [])
  const sourceDomains = computed<SourceDomainSeed[]>(() => dataset.sourceDomainsByBrand[selectedBrand.value] || [])
  const sourceUrls = computed<SourceUrlSeed[]>(() => dataset.sourceUrlsByBrand[selectedBrand.value] || [])
  const chats = computed<ChatEvidenceSeed[]>(() => dataset.chatEvidenceByBrand[selectedBrand.value] || [])
  const brandTracking = computed<BrandTrackingSeed[]>(() => dataset.brandTrackingByWorkspaceBrand[selectedBrand.value] || [])
  const theater = computed<TheaterBrandDataset | null>(() => dataset.theaterByBrand[selectedBrand.value] || null)

  const probeById = computed(() => new Map(probes.value.map(item => [item.id, item])))
  const sourceDomainById = computed(() => new Map(sourceDomains.value.map(item => [item.id, item])))
  const sourceUrlById = computed(() => new Map(sourceUrls.value.map(item => [item.id, item])))

  const chatsByProbeId = computed(() => {
    const map = new Map<string, ChatEvidenceSeed[]>()
    chats.value.forEach((chat) => {
      const probeId = chat.refs?.probeId || chat.probeId
      const current = map.get(probeId) || []
      current.push(chat)
      map.set(probeId, current)
    })
    return map
  })

  return {
    probes,
    sourceDomains,
    sourceUrls,
    chats,
    brandTracking,
    theater,
    probeById,
    sourceDomainById,
    sourceUrlById,
    chatsByProbeId
  }
}
