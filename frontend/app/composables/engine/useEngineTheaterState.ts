import { onBeforeUnmount, ref, watch, type ComputedRef } from 'vue'
import type { TheaterBucketKey, TheaterDensityMode, TheaterDetailTab, TheaterStage } from '~/types/engine'

const AUTOPLAY_INTERVAL_MS = 1100

export function useEngineTheaterState(maxSequence: ComputedRef<number>) {
  const playhead = ref(0)
  const autoplay = ref(false)
  const density = ref<TheaterDensityMode>('overview')
  const activeBucket = ref<TheaterBucketKey | 'all'>('all')
  const stageFilter = ref<TheaterStage | 'all'>('all')
  const selectedNodeId = ref<string | null>(null)
  const selectedEventId = ref<string | null>(null)
  const selectedClusterId = ref<string | null>(null)
  const detailTab = ref<TheaterDetailTab>('node')

  let timer: ReturnType<typeof setInterval> | null = null

  function stopTimer() {
    if (!timer) {
      return
    }
    clearInterval(timer)
    timer = null
  }

  function startTimer() {
    stopTimer()
    if (!autoplay.value) {
      return
    }
    timer = setInterval(() => {
      if (playhead.value >= maxSequence.value) {
        autoplay.value = false
        stopTimer()
        return
      }
      playhead.value += 1
    }, AUTOPLAY_INTERVAL_MS)
  }

  function resetPlayback() {
    playhead.value = maxSequence.value
    autoplay.value = true
    startTimer()
  }

  function toggleAutoplay() {
    autoplay.value = !autoplay.value
  }

  function pausePlayback() {
    autoplay.value = false
  }

  function selectNode(nodeId: string | null, tab: TheaterDetailTab = 'node') {
    selectedNodeId.value = nodeId
    selectedClusterId.value = null
    if (nodeId) {
      pausePlayback()
      detailTab.value = tab
    }
  }

  function selectEvent(eventId: string | null, nodeId?: string | null, tab: TheaterDetailTab = 'node') {
    selectedEventId.value = eventId
    selectedClusterId.value = null
    if (nodeId) {
      selectedNodeId.value = nodeId
    }
    if (eventId) {
      pausePlayback()
      detailTab.value = tab
    }
  }

  function selectCluster(
    clusterId: string | null,
    tab: TheaterDetailTab = 'node',
    nodeId?: string | null,
    eventId?: string | null
  ) {
    if (clusterId && selectedClusterId.value === clusterId) {
      selectedClusterId.value = null
      selectedNodeId.value = null
      selectedEventId.value = null
      return
    }
    selectedClusterId.value = clusterId
    selectedNodeId.value = nodeId || null
    selectedEventId.value = eventId || null
    if (clusterId) {
      pausePlayback()
      detailTab.value = tab
    }
  }

  function clearSelection() {
    selectedNodeId.value = null
    selectedEventId.value = null
    selectedClusterId.value = null
  }

  function setBucket(bucketKey: TheaterBucketKey | 'all') {
    activeBucket.value = activeBucket.value === bucketKey ? 'all' : bucketKey
  }

  function setStageFilter(stage: TheaterStage | 'all') {
    stageFilter.value = stageFilter.value === stage ? 'all' : stage
  }

  function jumpToSequence(sequence: number) {
    playhead.value = Math.max(0, Math.min(sequence, maxSequence.value || sequence))
  }

  let initialized = false
  watch([autoplay, maxSequence], () => {
    if (!initialized) {
      playhead.value = maxSequence.value
      autoplay.value = false
      initialized = true
    } else if (playhead.value > maxSequence.value) {
      playhead.value = maxSequence.value
    }
    startTimer()
  }, { immediate: true })

  onBeforeUnmount(() => {
    stopTimer()
  })

  return {
    playhead,
    autoplay,
    density,
    activeBucket,
    stageFilter,
    selectedNodeId,
    selectedEventId,
    selectedClusterId,
    detailTab,
    toggleAutoplay,
    pausePlayback,
    resetPlayback,
    selectNode,
    selectEvent,
    selectCluster,
    clearSelection,
    setBucket,
    setStageFilter,
    jumpToSequence
  }
}
