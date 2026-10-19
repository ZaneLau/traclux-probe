import { computed, type ComputedRef, type Ref } from 'vue'
import { useEngineTheaterGraphScene } from '~/composables/engine/useEngineTheaterGraphScene'
import { useEngineTheaterSceneState } from '~/composables/engine/useEngineTheaterSceneState'
import type {
  EngineDataset,
  TheaterBrandDataset,
  TheaterBucketKey,
  TheaterDensityMode,
  TheaterDetailTab,
  TheaterGraphPanelEdge,
  TheaterGraphPanelNode,
  TheaterStage,
  TheaterStateEdge,
  TheaterStateNode
} from '~/types/engine'

interface EngineTheaterSelectorState {
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

const OVERVIEW_NODE_TYPES = new Set(['task', 'topic', 'state', 'bucket', 'actor', 'probe', 'result'])
const ESSENTIAL_NODE_TYPES = new Set(['task', 'topic', 'state', 'bucket', 'result'])

export function useEngineTheaterSelectors(
  dataset: EngineDataset,
  selectedBrand: Ref<string> | ComputedRef<string>,
  state: EngineTheaterSelectorState
) {
  const theater = computed<TheaterBrandDataset | null>(() => dataset.theaterByBrand[selectedBrand.value] || null)
  const maxSequence = computed(() => theater.value?.events.at(-1)?.sequence || 0)

  const currentStage = computed<TheaterStage>(() => {
    const latestEvent = theater.value?.events
      .filter(event => event.sequence <= state.playhead.value)
      .sort((a, b) => b.sequence - a.sequence)[0]
    return latestEvent?.stage || 'task-decomposition'
  })

  const visibleEvents = computed(() => {
    const rows = theater.value?.events || []
    return rows.filter((event) => {
      if (event.sequence > state.playhead.value) {
        return false
      }
      if (state.stageFilter.value !== 'all' && event.stage !== state.stageFilter.value) {
        return false
      }
      if (state.activeBucket.value !== 'all' && event.bucketKey && event.bucketKey !== state.activeBucket.value) {
        return false
      }
      return true
    })
  })

  const visibleConsoleLines = computed(() => {
    const rows = theater.value?.consoleLines || []
    return rows.filter((line) => {
      if (line.sequence > state.playhead.value) {
        return false
      }
      if (state.stageFilter.value !== 'all' && line.stage !== state.stageFilter.value) {
        return false
      }
      return true
    })
  })

  const visibleNodes = computed<TheaterStateNode[]>(() => {
    const rows = theater.value?.nodes || []
    return rows.filter((node) => {
      if (node.revealedAt > state.playhead.value) {
        return false
      }
      if (state.stageFilter.value !== 'all' && node.stage !== state.stageFilter.value && !ESSENTIAL_NODE_TYPES.has(node.type)) {
        return false
      }
      if (state.activeBucket.value !== 'all' && node.bucketKey && node.bucketKey !== state.activeBucket.value) {
        return false
      }
      if (state.density.value === 'overview' && !OVERVIEW_NODE_TYPES.has(node.type)) {
        return false
      }
      return true
    })
  })

  const visibleNodeIds = computed(() => new Set(visibleNodes.value.map(node => node.id)))

  // Graph should be persistent once nodes/edges are revealed.
  // Stage filter is only for timeline/log reading, not graph topology.
  const graphVisibleNodes = computed<TheaterStateNode[]>(() => {
    const rows = theater.value?.nodes || []
    return rows.filter((node) => {
      if (node.revealedAt > state.playhead.value) {
        return false
      }
      if (state.activeBucket.value !== 'all' && node.bucketKey && node.bucketKey !== state.activeBucket.value) {
        return false
      }
      if (state.density.value === 'overview' && !OVERVIEW_NODE_TYPES.has(node.type)) {
        return false
      }
      return true
    })
  })

  const graphVisibleNodeIds = computed(() => new Set(graphVisibleNodes.value.map(node => node.id)))

  const visibleEdges = computed<TheaterStateEdge[]>(() => {
    const rows = theater.value?.edges || []
    return rows.filter((edge) => {
      if (edge.revealedAt > state.playhead.value) {
        return false
      }
      if (state.stageFilter.value !== 'all' && edge.stage !== state.stageFilter.value) {
        return false
      }
      if (state.activeBucket.value !== 'all' && edge.bucketKey && edge.bucketKey !== state.activeBucket.value) {
        return false
      }
      return visibleNodeIds.value.has(edge.sourceId) && visibleNodeIds.value.has(edge.targetId)
    })
  })

  const graphVisibleEdges = computed<TheaterStateEdge[]>(() => {
    const rows = theater.value?.edges || []
    return rows.filter((edge) => {
      if (edge.revealedAt > state.playhead.value) {
        return false
      }
      if (state.activeBucket.value !== 'all' && edge.bucketKey && edge.bucketKey !== state.activeBucket.value) {
        return false
      }
      return graphVisibleNodeIds.value.has(edge.sourceId) && graphVisibleNodeIds.value.has(edge.targetId)
    })
  })

  const currentEvent = computed(() => visibleEvents.value.at(-1) || null)

  const selectedEvent = computed(() => {
    if (!state.selectedEventId.value) {
      return null
    }
    return theater.value?.events.find(event => event.id === state.selectedEventId.value) || null
  })

  const selectedNode = computed(() => {
    const nodeId = state.selectedNodeId.value || selectedEvent.value?.nodeId || null
    if (!nodeId) {
      return null
    }
    return theater.value?.nodes.find(node => node.id === nodeId) || null
  })

  const selectedProbeRun = computed(() => {
    const probeId = selectedEvent.value?.probeId || selectedNode.value?.refs?.probeId
    if (!probeId) {
      return null
    }
    return theater.value?.probeRuns.find(run => run.probeId === probeId) || null
  })

  const selectedAttempt = computed(() => {
    const nodeId = selectedNode.value?.id || ''
    const probeId = selectedProbeRun.value?.probeId || selectedEvent.value?.probeId
    if (nodeId.includes('-attempt-')) {
      const attemptId = nodeId.replace(/-node$/, '')
      return theater.value?.attempts.find(item => item.id === attemptId) || null
    }
    if (!probeId) {
      return null
    }
    return theater.value?.attempts.find(item => item.probeId === probeId) || null
  })

  const selectedEvidence = computed(() => {
    const nodeId = selectedNode.value?.id || ''
    if (nodeId.includes('-evidence')) {
      const evidenceId = nodeId.replace(/-node$/, '')
      return theater.value?.evidences.find(item => item.id === evidenceId) || null
    }
    if (selectedAttempt.value) {
      return theater.value?.evidences.find(item => item.attemptId === selectedAttempt.value?.id) || null
    }
    return null
  })

  const selectedMention = computed(() => {
    const nodeId = selectedNode.value?.id || ''
    if (nodeId.includes('-mention')) {
      const mentionId = nodeId.replace(/-node$/, '')
      return theater.value?.mentions.find(item => item.id === mentionId) || null
    }
    if (selectedAttempt.value) {
      return theater.value?.mentions.find(item => item.attemptId === selectedAttempt.value?.id) || null
    }
    return null
  })

  const currentNodeId = computed(() => currentEvent.value?.nodeId || null)

  const activeBucketStats = computed(() => {
    if (state.activeBucket.value === 'all') {
      return null
    }
    return theater.value?.buckets.find(bucket => bucket.key === state.activeBucket.value) || null
  })

  const activeNodeIds = computed(() => {
    const nodeId = currentNodeId.value
    if (!nodeId) {
      return new Set<string>()
    }
    const related = new Set<string>([nodeId])
    visibleEdges.value.forEach((edge) => {
      if (edge.sourceId === nodeId || edge.targetId === nodeId) {
        related.add(edge.sourceId)
        related.add(edge.targetId)
      }
    })
    return related
  })

  const activeEdgeIds = computed(() => {
    const nodeId = currentNodeId.value
    if (!nodeId) {
      return new Set<string>()
    }
    return new Set(
      visibleEdges.value
        .filter(edge => edge.sourceId === nodeId || edge.targetId === nodeId)
        .map(edge => edge.id)
    )
  })

  const relatedNodeIds = computed(() => {
    const node = selectedNode.value
    if (!node) {
      return activeNodeIds.value
    }
    const related = new Set<string>([node.id])
    visibleEdges.value.forEach((edge) => {
      if (edge.sourceId === node.id || edge.targetId === node.id) {
        related.add(edge.sourceId)
        related.add(edge.targetId)
      }
    })
    return related
  })

  const { inspectionMode, sceneState, isInspecting } = useEngineTheaterSceneState(state, {
    currentStage,
    currentEvent,
    currentNodeId
  })

  const { graphScene, graphNodes, graphEdges, graphClusters, graphPanelNodes, graphPanelEdges } = useEngineTheaterGraphScene({
    theater,
    visibleNodes: graphVisibleNodes as ComputedRef<TheaterStateNode[]>,
    visibleEdges: graphVisibleEdges as ComputedRef<TheaterStateEdge[]>,
    currentStage,
    currentEvent,
    activeNodeIds,
    activeEdgeIds,
    relatedNodeIds
  }, state)

  const selectedCluster = computed(() => {
    if (!state.selectedClusterId.value) {
      return null
    }
    return graphClusters.value.find(cluster => cluster.id === state.selectedClusterId.value) || null
  })

  const selectedClusterNodeIds = computed(() => {
    return new Set(selectedCluster.value?.memberNodeIds || [])
  })

  const selectedClusterProbeIds = computed(() => {
    const ids = new Set<string>()
    selectedClusterNodeIds.value.forEach((nodeId) => {
      const node = theater.value?.nodes.find(item => item.id === nodeId)
      if (node?.refs.probeId) {
        ids.add(node.refs.probeId)
      }
    })
    return ids
  })

  const selectedClusterEventIds = computed(() => {
    if (!state.selectedClusterId.value) {
      return new Set<string>()
    }
    return new Set(
      visibleEvents.value
        .filter((event) => {
          if (event.nodeId && selectedClusterNodeIds.value.has(event.nodeId)) {
            return true
          }
          if (event.probeId && selectedClusterProbeIds.value.has(event.probeId)) {
            return true
          }
          if (selectedCluster.value?.bucketKey && event.bucketKey === selectedCluster.value.bucketKey) {
            return true
          }
          return false
        })
        .map(event => event.id)
    )
  })

  const displayedEvents = computed(() => {
    if (!state.selectedClusterId.value) {
      return visibleEvents.value
    }
    return visibleEvents.value.filter(event => selectedClusterEventIds.value.has(event.id))
  })

  const displayedConsoleLines = computed(() => {
    if (!state.selectedClusterId.value) {
      return visibleConsoleLines.value
    }
    return visibleConsoleLines.value.filter((line) => {
      if (line.eventId && selectedClusterEventIds.value.has(line.eventId)) {
        return true
      }
      if (line.refs.probeId && selectedClusterProbeIds.value.has(line.refs.probeId)) {
        return true
      }
      return false
    })
  })

  const relatedEventIds = computed(() => {
    const nodeId = selectedNode.value?.id
    if (nodeId) {
      const probeId = selectedProbeRun.value?.probeId
      return new Set(
        visibleEvents.value
          .filter(event => event.nodeId === nodeId || (probeId && event.probeId === probeId))
          .map(event => event.id)
      )
    }
    if (state.selectedClusterId.value) {
      return selectedClusterEventIds.value
    }
    return new Set<string>()
  })

  const focusEvent = computed(() => {
    return selectedEvent.value || displayedEvents.value.at(-1) || currentEvent.value
  })

  return {
    theater,
    maxSequence,
    currentStage,
    visibleNodes,
    graphVisibleNodes,
    visibleEdges,
    graphVisibleEdges,
    visibleEvents,
    visibleConsoleLines,
    displayedEvents,
    displayedConsoleLines,
    selectedNode,
    currentEvent,
    focusEvent,
    currentNodeId,
    selectedEvent,
    selectedCluster,
    selectedProbeRun,
    selectedAttempt,
    selectedEvidence,
    selectedMention,
    activeBucketStats,
    activeNodeIds,
    activeEdgeIds,
    relatedNodeIds,
    relatedEventIds,
    inspectionMode,
    sceneState,
    isInspecting,
    graphScene,
    graphNodes,
    graphEdges,
    graphClusters,
    graphPanelNodes: graphPanelNodes as ComputedRef<TheaterGraphPanelNode[]>,
    graphPanelEdges: graphPanelEdges as ComputedRef<TheaterGraphPanelEdge[]>
  }
}
