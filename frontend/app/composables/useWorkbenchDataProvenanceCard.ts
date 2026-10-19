import { ref } from 'vue'
import type {
  WorkbenchDataProvenanceCardQuery,
  WorkbenchDataProvenanceCardView
} from '~/types/workbench'

export function useWorkbenchDataProvenanceCard() {
  const workbenchApi = useWorkbenchApi()
  const provenanceOpen = ref(false)
  const provenanceLoading = ref(false)
  const provenanceError = ref('')
  const provenanceCard = ref<WorkbenchDataProvenanceCardView | null>(null)
  const lastProvenanceQuery = ref<WorkbenchDataProvenanceCardQuery | null>(null)
  let requestSeq = 0

  async function openProvenanceCard(query: WorkbenchDataProvenanceCardQuery) {
    if (!query.brandSpaceId) {
      return
    }
    const currentRequestSeq = requestSeq + 1
    requestSeq = currentRequestSeq
    lastProvenanceQuery.value = query
    provenanceOpen.value = true
    provenanceLoading.value = true
    provenanceError.value = ''
    provenanceCard.value = null

    try {
      const nextCard = await workbenchApi.dataProvenanceCard(query)
      if (currentRequestSeq === requestSeq) {
        provenanceCard.value = withQueryAnchor(nextCard, query)
      }
    } catch (error) {
      if (currentRequestSeq === requestSeq) {
        provenanceError.value = error instanceof Error && error.message ? error.message : '数据溯源读取失败'
      }
    } finally {
      if (currentRequestSeq === requestSeq) {
        provenanceLoading.value = false
      }
    }
  }

  function retryProvenanceCard() {
    if (!lastProvenanceQuery.value) {
      return
    }
    void openProvenanceCard(lastProvenanceQuery.value)
  }

  function openProvenanceSamplePage(page: number) {
    if (!lastProvenanceQuery.value) {
      return
    }
    void openProvenanceCard({
      ...lastProvenanceQuery.value,
      samplePage: page
    })
  }

  return {
    provenanceCard,
    provenanceError,
    provenanceLoading,
    provenanceOpen,
    openProvenanceCard,
    openProvenanceSamplePage,
    retryProvenanceCard
  }
}

function withQueryAnchor(
  card: WorkbenchDataProvenanceCardView,
  query: WorkbenchDataProvenanceCardQuery
): WorkbenchDataProvenanceCardView {
  const existingAnchor = card.anchor || {}
  const hasMetricAnchor = existingAnchor.metricLabel || existingAnchor.metricValue

  if (hasMetricAnchor) {
    return card
  }

  return {
    ...card,
    brandSpaceId: card.brandSpaceId || query.brandSpaceId,
    anchor: {
      ...existingAnchor,
      anchorType: existingAnchor.anchorType || query.anchorType || null,
      anchorKey: existingAnchor.anchorKey || query.anchorKey || null,
      metricKey: existingAnchor.metricKey || query.metricKey || null,
      metricLabel: existingAnchor.metricLabel || query.metricLabel || null,
      metricValue: existingAnchor.metricValue || query.metricValue || null,
      timeWindowLabel: existingAnchor.timeWindowLabel || resolveQueryWindowLabel(query) || null,
      topicId: existingAnchor.topicId || query.topicId || null,
      topicName: existingAnchor.topicName || query.topicName || null,
      probeId: existingAnchor.probeId || query.probeId || null
    }
  }
}

function resolveQueryWindowLabel(query: WorkbenchDataProvenanceCardQuery) {
  if (query.dateFrom || query.dateTo) {
    return [query.dateFrom, query.dateTo].filter(Boolean).join(' 至 ')
  }
  const preset = String(query.timePreset || '').trim()
  const map: Record<string, string> = {
    '7d': '近 7 天',
    '14d': '近 14 天',
    '30d': '近 30 天',
    last7d: '近 7 天',
    last14d: '近 14 天',
    last30d: '近 30 天',
    last_7_days: '近 7 天',
    last_14_days: '近 14 天',
    last_30_days: '近 30 天'
  }
  return map[preset] || preset || null
}
