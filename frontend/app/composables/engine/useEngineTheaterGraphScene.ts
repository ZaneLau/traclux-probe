import { computed, type ComputedRef, type Ref } from 'vue'
import type {
  TheaterBrandDataset,
  TheaterBucketKey,
  TheaterGraphCluster,
  TheaterGraphClusterKind,
  TheaterGraphPanelEdge,
  TheaterGraphPanelNode,
  TheaterGraphEdgePhase,
  TheaterGraphNodeEmphasis,
  TheaterGraphNodePhase,
  TheaterGraphScene,
  TheaterGraphSceneEdge,
  TheaterGraphSceneNode,
  TheaterStage,
  TheaterStateEdge,
  TheaterStateNode
} from '~/types/engine'

interface EngineTheaterGraphSceneState {
  playhead: Ref<number>
  activeBucket: Ref<TheaterBucketKey | 'all'>
  stageFilter: Ref<TheaterStage | 'all'>
  selectedNodeId: Ref<string | null>
  selectedEventId: Ref<string | null>
  selectedClusterId: Ref<string | null>
}

interface EngineTheaterGraphSceneRuntime {
  theater: ComputedRef<TheaterBrandDataset | null>
  visibleNodes: ComputedRef<TheaterStateNode[]>
  visibleEdges: ComputedRef<TheaterStateEdge[]>
  currentStage: ComputedRef<TheaterStage>
  currentEvent: ComputedRef<{ id: string, sequence: number, nodeId?: string | null } | null>
  activeNodeIds: ComputedRef<Set<string>>
  activeEdgeIds: ComputedRef<Set<string>>
  relatedNodeIds: ComputedRef<Set<string>>
}

const STAGE_ORDER: TheaterStage[] = [
  'task-decomposition',
  'state-assembly',
  'orchestration',
  'actor-generation',
  'probe-generation',
  'execution',
  'result-bridging'
]

const GRAPH_FOCUS_NODE_TYPES = new Set<TheaterStateNode['type']>(['task', 'actor', 'probe'])
const MAX_GRAPH_PROBES = 10

const NODE_WEIGHT: Record<TheaterStateNode['type'], number> = {
  task: 1.6,
  topic: 1.15,
  state: 1.12,
  bucket: 1.2,
  actor: 1,
  probe: 1.08,
  attempt: 0.94,
  evidence: 0.98,
  source: 0.86,
  mention: 0.92,
  result: 1.52
}

function stageRank(stage: TheaterStage) {
  return STAGE_ORDER.indexOf(stage)
}

function pulseLevel(edge: TheaterStateEdge, currentNodeId: string | null, activeEdgeIds: Set<string>): 0 | 1 | 2 | 3 {
  if (activeEdgeIds.has(edge.id)) {
    return 3
  }
  if (currentNodeId && (edge.sourceId === currentNodeId || edge.targetId === currentNodeId)) {
    return 2
  }
  return 1
}

function resolveNodePhase(
  node: TheaterStateNode,
  runtime: EngineTheaterGraphSceneRuntime,
  state: EngineTheaterGraphSceneState
): TheaterGraphNodePhase {
  const currentNodeId = runtime.currentEvent.value?.nodeId || null
  if (state.selectedNodeId.value === node.id || runtime.activeNodeIds.value.has(node.id)) {
    return 'active'
  }
  if (node.revealedAt === state.playhead.value || currentNodeId === node.id) {
    return 'entering'
  }
  if (state.selectedClusterId.value && !runtime.relatedNodeIds.value.has(node.id)) {
    return 'dimmed'
  }
  if (state.selectedNodeId.value && !runtime.relatedNodeIds.value.has(node.id) && !runtime.activeNodeIds.value.has(node.id)) {
    return 'dimmed'
  }
  if (stageRank(node.stage) < stageRank(runtime.currentStage.value)) {
    return 'resolved'
  }
  return 'idle'
}

function resolveNodeEmphasis(node: TheaterStateNode, phase: TheaterGraphNodePhase): TheaterGraphNodeEmphasis {
  if (phase === 'active') {
    return 'focus'
  }
  if (node.status === 'warning' || node.status === 'error') {
    return 'warning'
  }
  if (node.status === 'done' || phase === 'resolved') {
    return 'success'
  }
  return 'normal'
}

function resolveEdgePhase(
  edge: TheaterStateEdge,
  runtime: EngineTheaterGraphSceneRuntime,
  state: EngineTheaterGraphSceneState
): TheaterGraphEdgePhase {
  if (runtime.activeEdgeIds.value.has(edge.id)) {
    return 'flowing'
  }
  if (edge.revealedAt === state.playhead.value) {
    return 'entering'
  }
  if (state.selectedClusterId.value && !runtime.relatedNodeIds.value.has(edge.sourceId) && !runtime.relatedNodeIds.value.has(edge.targetId)) {
    return 'dimmed'
  }
  if (state.selectedNodeId.value && !runtime.relatedNodeIds.value.has(edge.sourceId) && !runtime.relatedNodeIds.value.has(edge.targetId)) {
    return 'dimmed'
  }
  if (stageRank(edge.stage) < stageRank(runtime.currentStage.value)) {
    return 'resolved'
  }
  return 'idle'
}

function buildActorClusters(theater: TheaterBrandDataset | null, visibleNodes: TheaterStateNode[]): TheaterGraphCluster[] {
  if (!theater) {
    return []
  }

  const visibleNodeIds = new Set(visibleNodes.map(node => node.id))
  const actorNodes = visibleNodes.filter(node => node.type === 'actor')

  return actorNodes.map((actorNode) => {
    const actorId = actorNode.id.replace(/-node$/, '')
    const probeIds = new Set(theater.probeRuns.filter(run => run.actorId === actorId).map(run => run.probeId))
    const memberNodeIds = visibleNodes
      .filter(node => node.id === actorNode.id || !!(node.refs.probeId && probeIds.has(node.refs.probeId)))
      .map(node => node.id)
      .filter(nodeId => visibleNodeIds.has(nodeId))

    return {
      id: `cluster-actor-${actorId}`,
      kind: 'actor',
      label: actorNode.label,
      subtitle: `${probeIds.size} 条 Probe 正跟随该角色`,
      layer: actorNode.layer,
      stage: actorNode.stage,
      memberNodeIds,
      bucketKey: actorNode.bucketKey,
      revealedAt: actorNode.revealedAt,
      refs: actorNode.refs
    } satisfies TheaterGraphCluster
  })
}

function buildProbeBatchClusters(theater: TheaterBrandDataset | null, visibleNodes: TheaterStateNode[]): TheaterGraphCluster[] {
  if (!theater) {
    return []
  }

  const visibleNodeIds = new Set(visibleNodes.map(node => node.id))
  return theater.buckets.map((bucket) => {
    const memberNodeIds = visibleNodes
      .filter(node => node.bucketKey === bucket.key && ['probe', 'attempt', 'evidence', 'mention'].includes(node.type))
      .map(node => node.id)
      .filter(nodeId => visibleNodeIds.has(nodeId))

    return {
      id: `cluster-probe-batch-${bucket.key}`,
      kind: 'probe-batch',
      label: `${bucket.label} 批次`,
      subtitle: `${memberNodeIds.length} 个执行实体`,
      layer: 5,
      stage: memberNodeIds.length ? 'probe-generation' : 'orchestration',
      memberNodeIds,
      bucketKey: bucket.key,
      revealedAt: 7,
      refs: bucket.refs
    } satisfies TheaterGraphCluster
  }).filter(cluster => cluster.memberNodeIds.length)
}

export function useEngineTheaterGraphScene(
  runtime: EngineTheaterGraphSceneRuntime,
  state: EngineTheaterGraphSceneState
) {
  const focusedNodes = computed(() => {
    const rows = runtime.visibleNodes.value.filter(node => GRAPH_FOCUS_NODE_TYPES.has(node.type))
    const task = rows.find(node => node.type === 'task') || null
    const actors = rows.filter(node => node.type === 'actor')
    const probes = rows
      .filter(node => node.type === 'probe')
      .sort((a, b) => b.revealedAt - a.revealedAt)
      .slice(0, MAX_GRAPH_PROBES)
    return task ? [task, ...actors, ...probes] : [...actors, ...probes]
  })

  const focusedNodeIds = computed(() => new Set(focusedNodes.value.map(node => node.id)))

  const focusedEdges = computed(() => {
    return runtime.visibleEdges.value.filter(edge => focusedNodeIds.value.has(edge.sourceId) && focusedNodeIds.value.has(edge.targetId))
  })

  const layers = computed(() => Array.from(new Set(focusedNodes.value.map(node => node.layer))).sort((a, b) => a - b))
  const layerIndexByValue = computed(() => new Map(layers.value.map((layer, index) => [layer, index])))

  const graphClusters = computed<TheaterGraphCluster[]>(() => {
    const visibleNodes = focusedNodes.value
    const theater = runtime.theater.value
    const rows = [
      ...buildActorClusters(theater, visibleNodes),
      ...buildProbeBatchClusters(theater, visibleNodes)
    ]
    return rows.sort((a, b) => {
      if (a.layer !== b.layer) {
        return a.layer - b.layer
      }
      return a.revealedAt - b.revealedAt
    })
  })

  const primaryClusterByNodeId = computed(() => {
    const map = new Map<string, string>()
    const clusterPriority: TheaterGraphClusterKind[] = ['topic', 'state', 'actor', 'probe-batch']

    clusterPriority.forEach((kind) => {
      graphClusters.value
        .filter(cluster => cluster.kind === kind)
        .forEach((cluster) => {
          cluster.memberNodeIds.forEach((nodeId) => {
            if (!map.has(nodeId)) {
              map.set(nodeId, cluster.id)
            }
          })
        })
    })

    return map
  })

  const graphNodes = computed<TheaterGraphSceneNode[]>(() => {
    return focusedNodes.value.map((node) => {
      const phase = resolveNodePhase(node, runtime, state)
      const emphasis = resolveNodeEmphasis(node, phase)
      const layerIndex = layerIndexByValue.value.get(node.layer) || 0
      const anchorX = layers.value.length <= 1 ? 0.5 : layerIndex / (layers.value.length - 1)
      const clusterId = primaryClusterByNodeId.value.get(node.id)
      return {
        id: node.id,
        sourceNodeId: node.id,
        type: node.type,
        label: node.label,
        subtitle: node.subtitle,
        detailLines: node.detailLines,
        stage: node.stage,
        layer: node.layer,
        slot: node.slot,
        clusterId,
        bucketKey: node.bucketKey,
        status: node.status,
        phase,
        emphasis,
        anchorX,
        weight: NODE_WEIGHT[node.type],
        revealedAt: node.revealedAt,
        refs: node.refs
      }
    })
  })

  const graphEdges = computed<TheaterGraphSceneEdge[]>(() => {
    const currentNodeId = runtime.currentEvent.value?.nodeId || null
    return focusedEdges.value.map(edge => ({
      id: edge.id,
      sourceId: edge.sourceId,
      targetId: edge.targetId,
      type: edge.type,
      stage: edge.stage,
      bucketKey: edge.bucketKey,
      phase: resolveEdgePhase(edge, runtime, state),
      pulseLevel: pulseLevel(edge, currentNodeId, runtime.activeEdgeIds.value),
      revealedAt: edge.revealedAt,
      refs: edge.refs
    }))
  })

  const graphScene = computed<TheaterGraphScene>(() => ({
    nodes: graphNodes.value,
    edges: graphEdges.value,
    clusters: graphClusters.value
  }))

  const graphPanelNodes = computed<TheaterGraphPanelNode[]>(() => {
    return graphNodes.value.map(node => ({
      uuid: node.id,
      name: node.label,
      labels: [node.type, node.stage],
      summary: node.detailLines.join('；') || node.subtitle,
      attributes: {
        nodeType: node.type,
        sourceNodeId: node.sourceNodeId,
        stage: node.stage,
        layer: node.layer,
        slot: node.slot,
        status: node.status,
        bucketKey: node.bucketKey || null,
        phase: node.phase,
        labels: node.detailLines.join(' | ') || null,
        revealedAt: node.revealedAt,
        refs: JSON.stringify(node.refs || {})
      },
      createdAt: `sequence:${node.revealedAt}`
    }))
  })

  const graphNodeNameById = computed(() => {
    return new Map(graphPanelNodes.value.map(node => [node.uuid, node.name]))
  })

  const graphPanelEdges = computed<TheaterGraphPanelEdge[]>(() => {
    return graphEdges.value.map(edge => ({
      uuid: edge.id,
      name: edge.type,
      fact: `${edge.type} @ ${edge.stage}`,
      factType: edge.type,
      sourceNodeUuid: edge.sourceId,
      targetNodeUuid: edge.targetId,
      sourceNodeName: graphNodeNameById.value.get(edge.sourceId) || edge.sourceId,
      targetNodeName: graphNodeNameById.value.get(edge.targetId) || edge.targetId,
      attributes: {
        stage: edge.stage,
        bucketKey: edge.bucketKey || null,
        phase: edge.phase,
        pulseLevel: edge.pulseLevel
      },
      episodes: [edge.stage],
      createdAt: `sequence:${edge.revealedAt}`,
      validAt: null
    }))
  })

  return {
    graphScene,
    graphNodes,
    graphEdges,
    graphClusters,
    graphPanelNodes,
    graphPanelEdges
  }
}
