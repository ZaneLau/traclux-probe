import { computed, type ComputedRef, type Ref } from 'vue'
import type {
  TheaterBucketKey,
  TheaterDensityMode,
  TheaterDetailTab,
  TheaterEvent,
  TheaterInspectionMode,
  TheaterSceneState,
  TheaterStage
} from '~/types/engine'

interface EngineTheaterStateRefs {
  playhead: Ref<number>
  autoplay: Ref<boolean>
  density: Ref<TheaterDensityMode>
  activeBucket: Ref<TheaterBucketKey | 'all'>
  stageFilter: Ref<TheaterStage | 'all'>
  selectedNodeId: Ref<string | null>
  selectedEventId: Ref<string | null>
  selectedClusterId: Ref<string | null>
  detailTab: Ref<TheaterDetailTab>
}

interface EngineTheaterSceneSelectors {
  currentStage: ComputedRef<TheaterStage>
  currentEvent: ComputedRef<TheaterEvent | null>
  currentNodeId: ComputedRef<string | null>
}

export function useEngineTheaterSceneState(
  state: EngineTheaterStateRefs,
  selectors: EngineTheaterSceneSelectors
) {
  const inspectionMode = computed<TheaterInspectionMode>(() => {
    if (state.selectedClusterId.value) {
      return 'cluster'
    }
    if (state.selectedEventId.value) {
      return 'event'
    }
    if (state.selectedNodeId.value) {
      return 'node'
    }
    return 'auto'
  })

  const sceneState = computed<TheaterSceneState>(() => ({
    playhead: state.playhead.value,
    autoplay: state.autoplay.value,
    density: state.density.value,
    activeBucket: state.activeBucket.value,
    stageFilter: state.stageFilter.value,
    inspectionMode: inspectionMode.value,
    selectedNodeId: state.selectedNodeId.value,
    selectedEventId: state.selectedEventId.value,
    selectedClusterId: state.selectedClusterId.value,
    currentNodeId: selectors.currentNodeId.value,
    currentEventId: selectors.currentEvent.value?.id || null
  }))

  const isInspecting = computed(() => inspectionMode.value !== 'auto')

  return {
    inspectionMode,
    sceneState,
    isInspecting
  }
}
