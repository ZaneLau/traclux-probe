import { computed, type ComputedRef } from 'vue'
import {
  forceCollide,
  forceLink,
  forceManyBody,
  forceSimulation,
  forceX,
  forceY
} from 'd3'
import type {
  TheaterGraphCluster,
  TheaterGraphSceneEdge,
  TheaterGraphSceneNode
} from '~/types/engine'

export interface TheaterGraphPosition {
  x: number
  y: number
}

export interface TheaterGraphLayerGuide {
  layer: number
  x: number
  label: string
}

export interface TheaterGraphLayerBand extends TheaterGraphLayerGuide {
  left: number
  width: number
  tone: 'is-even' | 'is-odd'
}

export interface TheaterGraphHullBox {
  id: string
  label: string
  subtitle: string
  kind: TheaterGraphCluster['kind']
  layer: number
  x: number
  y: number
  width: number
  height: number
  memberCount: number
  isActive: boolean
  isHovered: boolean
  isExpanded: boolean
  pulseLevel: 0 | 1 | 2 | 3
}

interface EngineTheaterGraphLayoutInput {
  nodes: ComputedRef<TheaterGraphSceneNode[]>
  edges: ComputedRef<TheaterGraphSceneEdge[]>
  clusters: ComputedRef<TheaterGraphCluster[]>
  activeNodeIds: ComputedRef<Set<string>>
  selectedClusterId: ComputedRef<string | null>
  hoveredClusterId: ComputedRef<string | null>
  density: ComputedRef<'overview' | 'detailed'>
}

type SimulationNode = TheaterGraphSceneNode & {
  x?: number
  y?: number
}

type SimulationLink = {
  source: string
  target: string
  edge: TheaterGraphSceneEdge
}

const LAYER_LABELS: Record<number, string> = {
  0: '任务单',
  1: '主题簇',
  2: '状态簇',
  3: 'OATS',
  4: '虚拟用户',
  5: 'Probe',
  6: 'Attempt',
  7: '证据',
  8: '来源',
  9: '提及',
  10: '结果桥'
}

function nodeBox(node: TheaterGraphSceneNode) {
  switch (node.type) {
    case 'task':
    case 'result':
      return { width: 176, height: 86 }
    case 'topic':
      return { width: 154, height: 72 }
    case 'state':
      return { width: 150, height: 76 }
    case 'bucket':
      return { width: 144, height: 74 }
    case 'actor':
      return { width: 146, height: 66 }
    case 'probe':
      return { width: 154, height: 66 }
    case 'attempt':
      return { width: 140, height: 62 }
    case 'evidence':
      return { width: 148, height: 62 }
    case 'source':
    case 'mention':
      return { width: 134, height: 58 }
    default:
      return { width: 146, height: 64 }
  }
}

function linkDistance(edge: TheaterGraphSceneEdge) {
  switch (edge.type) {
    case 'contains':
      return 82
    case 'derives':
      return 94
    case 'routes':
      return 88
    case 'executes':
      return 96
    case 'supports':
      return 102
    case 'mentions':
      return 84
    case 'converges':
      return 118
    default:
      return 92
  }
}

function localOffset(index: number, gap: number) {
  if (index === 0) {
    return 0
  }
  const step = Math.ceil(index / 2)
  const direction = index % 2 === 0 ? 1 : -1
  return direction * step * gap
}

function clusterSpacingWeight(
  clusterId: string,
  selectedClusterId: string | null,
  activeNodeIds: Set<string>,
  clusterNodes: TheaterGraphSceneNode[]
) {
  void clusterId
  void selectedClusterId
  void activeNodeIds
  void clusterNodes
  return 1
}

function phaseDrift(node: TheaterGraphSceneNode, nodeIndex: number) {
  const seed = node.id.split('').reduce((sum, ch) => sum + ch.charCodeAt(0), 0) + nodeIndex * 3
  const x = Math.sin(seed * 1.13) * 12
  const y = Math.cos(seed * 0.74) * 18
  void node
  return { x: x * 0.65, y: y * 0.6 }
}

export function useEngineTheaterGraphLayout(input: EngineTheaterGraphLayoutInput) {
  const layers = computed(() => Array.from(new Set(input.nodes.value.map(node => node.layer))).sort((a, b) => a - b))
  const graphWidth = computed(() => Math.max(980, layers.value.length * 220 + 180))
  const graphHeight = computed(() => {
    const maxNodesPerLayer = Math.max(...layers.value.map(layer => input.nodes.value.filter(node => node.layer === layer).length), 8)
    const densityBoost = input.density.value === 'detailed' ? 24 : 0
    const clusterBoost = input.selectedClusterId.value ? 36 : 0
    return Math.min(920, Math.max(700, maxNodesPerLayer * 60 + 180 + densityBoost + clusterBoost))
  })

  const layerGuides = computed<TheaterGraphLayerGuide[]>(() => {
    const step = layers.value.length > 1 ? (graphWidth.value - 220) / (layers.value.length - 1) : 0
    return layers.value.map((layer, index) => ({
      layer,
      x: 110 + index * step,
      label: LAYER_LABELS[layer] || `层 ${layer}`
    }))
  })

  const layerBands = computed<TheaterGraphLayerBand[]>(() => {
    return layerGuides.value.map((guide, index) => {
      const prev = layerGuides.value[index - 1]
      const next = layerGuides.value[index + 1]
      const left = index === 0 ? 28 : (guide.x + (prev?.x || 28)) / 2
      const right = index === layerGuides.value.length - 1 ? graphWidth.value - 28 : (guide.x + (next?.x || graphWidth.value - 28)) / 2
      return {
        ...guide,
        left,
        width: Math.max(96, right - left),
        tone: index % 2 === 0 ? 'is-even' : 'is-odd'
      }
    })
  })

  const positions = computed(() => {
    const map = new Map<string, TheaterGraphPosition>()
    const nodes = input.nodes.value
    if (!nodes.length) {
      return map
    }

    const clusterOrderById = new Map(input.clusters.value.map((cluster, index) => [cluster.id, index]))
    const layerIndexByValue = new Map(layers.value.map((layer, index) => [layer, index]))
    const targetMap = new Map<string, TheaterGraphPosition>()

    layers.value.forEach((layer) => {
      const layerNodes = nodes
        .filter(node => node.layer === layer)
        .sort((a, b) => {
          const clusterA = clusterOrderById.get(a.clusterId || '') ?? Number.MAX_SAFE_INTEGER
          const clusterB = clusterOrderById.get(b.clusterId || '') ?? Number.MAX_SAFE_INTEGER
          if (clusterA !== clusterB) {
            return clusterA - clusterB
          }
          return a.slot - b.slot
        })

      const layerGuide = layerGuides.value.find(guide => guide.layer === layer)
      const grouped = new Map<string, TheaterGraphSceneNode[]>()
      layerNodes.forEach((node) => {
        const key = node.clusterId || `layer-${layer}-${node.type}`
        const rows = grouped.get(key) || []
        rows.push(node)
        grouped.set(key, rows)
      })

      const groups = Array.from(grouped.entries())
      const verticalRange = graphHeight.value - 220
      const groupWeights = groups.map(([key, clusterNodes]) => clusterSpacingWeight(
        key,
        input.selectedClusterId.value,
        input.activeNodeIds.value,
        clusterNodes
      ))
      const weightTotal = groupWeights.reduce((sum, value) => sum + value, 0) || 1
      const baseInnerGap = input.density.value === 'overview' ? 34 : 48

      let currentY = 110
      groups.forEach(([key, clusterNodes], groupIndex) => {
        const sliceHeight = verticalRange * ((groupWeights[groupIndex] ?? 1) / weightTotal)
        const centroidY = currentY + sliceHeight / 2
        const innerGap = baseInnerGap

        clusterNodes.forEach((node, nodeIndex) => {
          const baseOffset = localOffset(nodeIndex, innerGap)
          const layerIndex = layerIndexByValue.get(layer) || 0
          const anchorDrift = ((layerIndex % 2 === 0 ? 1 : -1) * ((clusterOrderById.get(key) ?? 0) % 3)) * 8
          const drift = phaseDrift(node, nodeIndex)
          targetMap.set(node.id, {
            x: (layerGuide?.x || 110) + anchorDrift + drift.x,
            y: centroidY + baseOffset + drift.y
          })
        })
        currentY += sliceHeight
      })
    })

    const simulationNodes: SimulationNode[] = nodes.map(node => ({
      ...node,
      x: targetMap.get(node.id)?.x || 0,
      y: targetMap.get(node.id)?.y || graphHeight.value / 2
    }))

    const simulationLinks: SimulationLink[] = input.edges.value.map(edge => ({
      source: edge.sourceId,
      target: edge.targetId,
      edge
    }))

    const simulation = forceSimulation(simulationNodes)
      .force('charge', forceManyBody<SimulationNode>().strength(() => -170))
      .force('x', forceX<SimulationNode>(node => targetMap.get(node.id)?.x || 0).strength(() => 0.72))
      .force('y', forceY<SimulationNode>(node => targetMap.get(node.id)?.y || graphHeight.value / 2).strength(() => 0.24))
      .force('collision', forceCollide<SimulationNode>((node) => {
        const base = Math.max(nodeBox(node).width, nodeBox(node).height) / 2 + (input.density.value === 'overview' ? 20 : 28)
        return base
      }))
      .force('link', forceLink<SimulationNode, SimulationLink>(simulationLinks).id(node => node.id).distance((link) => {
        const base = linkDistance(link.edge)
        return base
      }).strength((link) => {
        void link
        return 0.22
      }))
      .stop()

    for (let tick = 0; tick < 108; tick += 1) {
      simulation.tick()
    }

    simulationNodes.forEach((node) => {
      const box = nodeBox(node)
      const x = Math.max(48 + box.width / 2, Math.min(graphWidth.value - 48 - box.width / 2, node.x || 0))
      const y = Math.max(74 + box.height / 2, Math.min(graphHeight.value - 42 - box.height / 2, node.y || graphHeight.value / 2))
      map.set(node.id, { x, y })
    })

    return map
  })

  const hullBoxes = computed<TheaterGraphHullBox[]>(() => {
    return input.clusters.value
      .map((cluster) => {
        const memberNodes = cluster.memberNodeIds
          .map(nodeId => input.nodes.value.find(node => node.id === nodeId))
          .filter((node): node is TheaterGraphSceneNode => Boolean(node))

        if (!memberNodes.length) {
          return null
        }

        const measured = memberNodes
          .map((node) => {
            const position = positions.value.get(node.id)
            if (!position) {
              return null
            }
            const box = nodeBox(node)
            return {
              x1: position.x - box.width / 2,
              y1: position.y - box.height / 2,
              x2: position.x + box.width / 2,
              y2: position.y + box.height / 2
            }
          })
          .filter((item): item is NonNullable<typeof item> => Boolean(item))

        if (!measured.length) {
          return null
        }

        const minX = Math.min(...measured.map(item => item.x1))
        const maxX = Math.max(...measured.map(item => item.x2))
        const minY = Math.min(...measured.map(item => item.y1))
        const maxY = Math.max(...measured.map(item => item.y2))

        return {
          id: cluster.id,
          label: cluster.label,
          subtitle: cluster.subtitle,
          kind: cluster.kind,
          layer: cluster.layer,
          x: minX - (cluster.id === input.selectedClusterId.value ? 28 : 18),
          y: minY - (cluster.id === input.selectedClusterId.value ? 30 : 22),
          width: Math.max(94, maxX - minX + (cluster.id === input.selectedClusterId.value ? 56 : 36)),
          height: Math.max(76, maxY - minY + (cluster.id === input.selectedClusterId.value ? 64 : 44)),
          memberCount: cluster.memberNodeIds.length,
          isActive: cluster.id === input.selectedClusterId.value || cluster.memberNodeIds.some(nodeId => input.activeNodeIds.value.has(nodeId)),
          isHovered: cluster.id === input.hoveredClusterId.value,
          isExpanded: cluster.id === input.selectedClusterId.value,
          pulseLevel: cluster.id === input.selectedClusterId.value
            ? 3
            : cluster.memberNodeIds.some(nodeId => input.activeNodeIds.value.has(nodeId))
              ? 2
              : cluster.id === input.hoveredClusterId.value
                ? 1
                : 0
        } satisfies TheaterGraphHullBox
      })
      .filter((cluster): cluster is TheaterGraphHullBox => Boolean(cluster))
  })

  return {
    graphWidth,
    graphHeight,
    layerGuides,
    layerBands,
    positions,
    hullBoxes,
    nodeBox
  }
}
