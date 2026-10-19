<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { select, zoom, zoomIdentity, type D3ZoomEvent, type ZoomBehavior } from 'd3'
import EngineTheaterGraphCanvas from '~/components/engine-theater/graph/EngineTheaterGraphCanvas.vue'
import { useEngineTheaterGraphLayout } from '~/composables/engine/useEngineTheaterGraphLayout'
import type {
  TheaterDensityMode,
  TheaterGraphCluster,
  TheaterGraphPanelEdge,
  TheaterGraphPanelNode,
  TheaterGraphSceneEdge,
  TheaterGraphSceneNode
} from '~/types/engine'

type GraphCanvasExposed = {
  svgEl: SVGSVGElement | null
  viewportEl: SVGGElement | null
}

interface RenderedNode extends TheaterGraphSceneNode {
  x: number
  y: number
  radius: number
}

interface RenderedEdge extends TheaterGraphSceneEdge {
  path: string
  labelX: number
  labelY: number
  isSelfLoop: boolean
  selfLoopCount: number
  sourceName: string
  targetName: string
}

const props = defineProps<{
  nodes: TheaterGraphSceneNode[]
  edges: TheaterGraphSceneEdge[]
  clusters: TheaterGraphCluster[]
  panelNodes: TheaterGraphPanelNode[]
  panelEdges: TheaterGraphPanelEdge[]
  selectedNodeId: string | null
  selectedClusterId: string | null
  currentNodeId: string | null
  relatedNodeIds: Set<string>
  activeNodeIds: Set<string>
  activeEdgeIds: Set<string>
  density: TheaterDensityMode
}>()

const emit = defineEmits<{
  (event: 'select-node' | 'select-cluster', id: string): void
  (event: 'clear-selection'): void
  (event: 'update:density', density: TheaterDensityMode): void
}>()

const graphCanvasRef = ref<GraphCanvasExposed | null>(null)
const hoveredNodeId = ref<string | null>(null)
const showEdgeLabels = ref(false)
const selectedEdgeId = ref<string | null>(null)
const selectedPanelItem = ref<{ type: 'node' | 'edge', data: TheaterGraphPanelNode | TheaterGraphPanelEdge } | null>(null)

let zoomBehavior: ZoomBehavior<SVGSVGElement, unknown> | null = null

const selectedClusterIdRef = computed(() => props.selectedClusterId)
const densityRef = computed(() => props.density)
const nodesRef = computed(() => props.nodes)
const edgesRef = computed(() => props.edges)
const clustersRef = computed(() => props.clusters)
const activeNodeIdsRef = computed(() => props.activeNodeIds)

const {
  graphWidth,
  graphHeight,
  positions
} = useEngineTheaterGraphLayout({
  nodes: nodesRef,
  edges: edgesRef,
  clusters: clustersRef,
  activeNodeIds: activeNodeIdsRef,
  selectedClusterId: selectedClusterIdRef,
  hoveredClusterId: computed(() => null),
  density: densityRef
})

const nodeById = computed(() => new Map(props.panelNodes.map(node => [node.uuid, node])))
const clusterNodeIds = computed(() => {
  const clusterId = props.selectedClusterId
  if (!clusterId) {
    return null
  }
  const cluster = props.clusters.find(item => item.id === clusterId)
  return cluster ? new Set(cluster.memberNodeIds) : null
})

const focusNodeId = computed(() => hoveredNodeId.value || props.selectedNodeId || props.currentNodeId || null)
const suppressLiveHighlight = computed(() => Boolean(hoveredNodeId.value || props.selectedNodeId))

const highlightNodeIds = computed(() => {
  if (clusterNodeIds.value?.size) {
    return clusterNodeIds.value
  }
  const targetId = focusNodeId.value
  if (!targetId) {
    return props.relatedNodeIds
  }
  const ids = new Set<string>([targetId])
  props.edges.forEach((edge) => {
    if (edge.sourceId === targetId || edge.targetId === targetId) {
      ids.add(edge.sourceId)
      ids.add(edge.targetId)
    }
  })
  return ids
})

const renderedNodes = computed<RenderedNode[]>(() => {
  return props.nodes.map((node) => {
    const base = positions.value.get(node.id) || { x: 0, y: 0 }
    const radius = node.type === 'task' ? 18 : node.type === 'actor' ? 9 : node.type === 'probe' ? 7 : 6
    const isTask = node.type === 'task'
    return {
      ...node,
      x: isTask ? graphWidth.value / 2 : base.x,
      y: isTask ? graphHeight.value / 2 : base.y,
      radius
    }
  })
})

const renderedNodeById = computed(() => new Map(renderedNodes.value.map(node => [node.id, node])))

const renderedEdges = computed<RenderedEdge[]>(() => {
  const pairCount = new Map<string, number>()
  const pairIndex = new Map<string, number>()
  props.edges.forEach((edge) => {
    if (edge.sourceId === edge.targetId) {
      return
    }
    const key = [edge.sourceId, edge.targetId].sort().join('__')
    pairCount.set(key, (pairCount.get(key) || 0) + 1)
  })

  const selfLoopGrouped = new Set<string>()

  const rows: RenderedEdge[] = []
  props.edges.forEach((edge) => {
    const source = renderedNodeById.value.get(edge.sourceId)
    const target = renderedNodeById.value.get(edge.targetId)
    if (!source || !target) {
      return
    }

    const sourceInfo = nodeById.value.get(edge.sourceId)
    const targetInfo = nodeById.value.get(edge.targetId)

    if (edge.sourceId === edge.targetId) {
      if (selfLoopGrouped.has(edge.sourceId)) {
        return []
      }
      selfLoopGrouped.add(edge.sourceId)
      const count = props.edges.filter(item => item.sourceId === edge.sourceId && item.targetId === edge.targetId).length
      const radius = 28
      const startX = source.x + 10
      const startY = source.y - 8
      const endX = source.x + 10
      const endY = source.y + 8
      rows.push({
        ...edge,
        id: `self-loop-${edge.sourceId}`,
        path: `M${startX},${startY} A${radius},${radius} 0 1,1 ${endX},${endY}`,
        labelX: source.x + 56,
        labelY: source.y,
        isSelfLoop: true,
        selfLoopCount: count,
        sourceName: sourceInfo?.name || edge.sourceId,
        targetName: targetInfo?.name || edge.targetId
      })
      return
    }

    const key = [edge.sourceId, edge.targetId].sort().join('__')
    const total = pairCount.get(key) || 1
    const index = pairIndex.get(key) || 0
    pairIndex.set(key, index + 1)
    const reversed = edge.sourceId > edge.targetId
    const baseSign = ((edge.sourceId.charCodeAt(0) + edge.targetId.charCodeAt(0)) % 2 === 0) ? 1 : -1
    const baseCurvature = 0.16 * baseSign
    let curvature = baseCurvature
    if (total > 1) {
      curvature += ((index / (total - 1)) - 0.5) * 1.2
    }
    if (reversed) {
      curvature = -curvature
    }

    const sx = source.x
    const sy = source.y
    const tx = target.x
    const ty = target.y
    const dx = tx - sx
    const dy = ty - sy
    const dist = Math.max(1, Math.hypot(dx, dy))
    const offset = Math.max(36, dist * 0.22) * curvature
    const cx = (sx + tx) / 2 + (-dy / dist) * offset
    const cy = (sy + ty) / 2 + (dx / dist) * offset
    const labelX = 0.25 * sx + 0.5 * cx + 0.25 * tx
    const labelY = 0.25 * sy + 0.5 * cy + 0.25 * ty

    rows.push({
      ...edge,
      path: curvature === 0 ? `M${sx},${sy} L${tx},${ty}` : `M${sx},${sy} Q${cx},${cy} ${tx},${ty}`,
      labelX,
      labelY,
      isSelfLoop: false,
      selfLoopCount: 0,
      sourceName: sourceInfo?.name || edge.sourceId,
      targetName: targetInfo?.name || edge.targetId
    })
  })
  return rows
})

const selectedNodeOverlay = computed(() => {
  if (!props.selectedNodeId) {
    return null
  }
  const node = renderedNodeById.value.get(props.selectedNodeId)
  if (!node) {
    return null
  }
  const panel = nodeById.value.get(node.id)
  if (!panel) {
    return null
  }
  const width = 280
  const attrs = Object.entries(panel.attributes || {})
  const summaryLines = wrapMixedText(panel.summary || '', 34)
  const summaryLineCount = Math.max(summaryLines.length, 1)
  const attrsStartY = 62 + summaryLineCount * 14 + 16
  const wrappedAttrs = attrs.map(([key, value]) => ({
    key,
    lines: wrapMixedText(String(value ?? ''), 26)
  }))
  const attrsLineCount = wrappedAttrs.reduce((sum, item) => sum + Math.max(item.lines.length, 1), 0)
  const height = attrsStartY + attrsLineCount * 14 + 16
  const centerCard = node.type === 'task'
  const x = centerCard
    ? Math.max(16, (graphWidth.value - width) / 2)
    : Math.min(Math.max(16, node.x + 20), Math.max(16, graphWidth.value - width - 16))
  const y = centerCard
    ? Math.max(16, (graphHeight.value - height) / 2)
    : Math.min(Math.max(16, node.y - height / 2), Math.max(16, graphHeight.value - height - 16))
  return { node, panel, x, y, width, height, wrappedAttrs, summaryLines, attrsStartY }
})

function wrapMixedText(text: string, maxUnitsPerLine: number) {
  if (!text) {
    return []
  }
  const lines: string[] = []
  let line = ''
  let lineUnits = 0

  for (const ch of text) {
    const unit = /[\u4e00-\u9fff\u3400-\u4dbf\u3040-\u30ff\uac00-\ud7af]/.test(ch) ? 2 : 1
    if (lineUnits + unit > maxUnitsPerLine) {
      lines.push(line)
      line = ''
      lineUnits = 0
    }
    line += ch
    lineUnits += unit
  }

  if (line) {
    lines.push(line)
  }

  return lines
}

function overlayAttrOffset(attrs: Array<{ key: string, lines: string[] }>, currentIdx: number) {
  let offset = 0
  for (let i = 0; i < currentIdx; i += 1) {
    offset += Math.max(attrs[i]?.lines.length || 1, 1) * 14
  }
  return offset
}

function edgeIsHighlighted(edge: RenderedEdge) {
  if (selectedEdgeId.value && selectedEdgeId.value === edge.id) {
    return true
  }
  return highlightNodeIds.value.has(edge.sourceId) && highlightNodeIds.value.has(edge.targetId)
}

function resetSelection() {
  selectedEdgeId.value = null
  selectedPanelItem.value = null
  emit('clear-selection')
}

function applyZoom() {
  const svg = graphCanvasRef.value?.svgEl || null
  const viewport = graphCanvasRef.value?.viewportEl || null
  if (!svg || !viewport) {
    return
  }
  zoomBehavior = zoom<SVGSVGElement, unknown>()
    .scaleExtent([0.1, 4])
    .on('zoom', (event: D3ZoomEvent<SVGSVGElement, unknown>) => {
      select(viewport).attr('transform', event.transform.toString())
    })

  select(svg)
    .call(zoomBehavior as ZoomBehavior<SVGSVGElement, unknown>)
    .call(zoomBehavior.transform, zoomIdentity.translate(36, 24).scale(0.92))
}

function resetView() {
  const svg = graphCanvasRef.value?.svgEl || null
  if (!svg || !zoomBehavior) {
    return
  }
  select(svg).transition().duration(320).call(zoomBehavior.transform, zoomIdentity.translate(36, 24).scale(0.92))
}

function selectNode(nodeId: string) {
  emit('select-node', nodeId)
  selectedEdgeId.value = null
  const node = nodeById.value.get(nodeId)
  if (node) {
    selectedPanelItem.value = { type: 'node', data: node }
  }
}

function selectEdge(edge: RenderedEdge) {
  selectedEdgeId.value = edge.id
  const detail = props.panelEdges.find(item => item.uuid === edge.id || (edge.isSelfLoop && item.sourceNodeUuid === edge.sourceId && item.targetNodeUuid === edge.targetId))
  selectedPanelItem.value = {
    type: 'edge',
    data: detail || {
      uuid: edge.id,
      name: edge.type,
      fact: `${edge.sourceName} -> ${edge.targetName}`,
      factType: edge.type,
      sourceNodeUuid: edge.sourceId,
      targetNodeUuid: edge.targetId,
      sourceNodeName: edge.sourceName,
      targetNodeName: edge.targetName,
      attributes: {},
      episodes: [],
      createdAt: null,
      validAt: null
    }
  }
}

watch(
  () => renderedEdges.value.map(edge => `${edge.id}:${edge.labelX}:${edge.labelY}`).join('|'),
  async () => {
    await nextTick()
    renderedEdges.value.forEach((edge) => {
      const textEl = document.getElementById(`edge-text-${edge.id}`) as SVGTextElement | null
      const bgEl = document.getElementById(`edge-bg-${edge.id}`) as SVGRectElement | null
      if (!textEl || !bgEl) {
        return
      }
      const bbox = textEl.getBBox()
      bgEl.setAttribute('x', String(edge.labelX - bbox.width / 2 - 4))
      bgEl.setAttribute('y', String(edge.labelY - bbox.height / 2 - 2))
      bgEl.setAttribute('width', String(bbox.width + 8))
      bgEl.setAttribute('height', String(bbox.height + 4))
    })
  },
  { flush: 'post' }
)

onMounted(() => {
  nextTick(applyZoom)
})
</script>

<template>
  <section class="theater-graph">
    <header class="theater-graph__head">
      <div>
        <p class="theater-panel__eyebrow">
          知识图谱
        </p>
        <strong>过程主舞台</strong>
      </div>
      <div class="theater-graph__controls">
        <label class="edge-toggle">
          <input
            v-model="showEdgeLabels"
            type="checkbox"
          >
          <span>显示关系标签</span>
        </label>
        <div class="theater-graph__density">
          <button
            type="button"
            class="theater-graph__density-btn"
            :class="{ 'is-active': props.density === 'overview' }"
            @click="emit('update:density', 'overview')"
          >
            概览
          </button>
          <button
            type="button"
            class="theater-graph__density-btn"
            :class="{ 'is-active': props.density === 'detailed' }"
            @click="emit('update:density', 'detailed')"
          >
            详细
          </button>
        </div>
        <button
          type="button"
          class="theater-graph__reset"
          @click="resetView"
        >
          重置视图
        </button>
      </div>
    </header>

    <div class="theater-graph__layout">
      <EngineTheaterGraphCanvas
        ref="graphCanvasRef"
        :graph-width="graphWidth"
        :graph-height="graphHeight"
      >
        <rect
          class="graph-clear-hit"
          :width="graphWidth"
          :height="graphHeight"
          @click="resetSelection"
        />

        <g
          v-for="edge in renderedEdges"
          :key="edge.id"
          class="graph-edge-group"
        >
          <path
            class="graph-edge"
            :class="[
              `is-${edge.type}`,
              edgeIsHighlighted(edge) ? 'is-highlighted' : '',
              !suppressLiveHighlight && props.activeEdgeIds.has(edge.id) ? 'is-live' : ''
            ]"
            :d="edge.path"
            @click.stop="selectEdge(edge)"
          />
          <rect
            v-if="showEdgeLabels"
            :id="`edge-bg-${edge.id}`"
            class="graph-edge-label-bg"
            rx="3"
            ry="3"
            @click.stop="selectEdge(edge)"
          />
          <text
            v-if="showEdgeLabels"
            :id="`edge-text-${edge.id}`"
            class="graph-edge-label"
            :x="edge.labelX"
            :y="edge.labelY"
            @click.stop="selectEdge(edge)"
          >
            {{ edge.isSelfLoop ? `SELF_LOOP(${edge.selfLoopCount})` : edge.type }}
          </text>
        </g>

        <g
          v-for="node in renderedNodes"
          :key="node.id"
          class="graph-node"
          :class="[
            `is-${node.type}`,
            props.selectedNodeId === node.id ? 'is-selected' : '',
            !suppressLiveHighlight && props.activeNodeIds.has(node.id) ? 'is-live' : '',
            highlightNodeIds.size > 0 && !highlightNodeIds.has(node.id) && (suppressLiveHighlight || !props.activeNodeIds.has(node.id)) ? 'is-muted' : ''
          ]"
          :transform="`translate(${node.x}, ${node.y})`"
          @click.stop="selectNode(node.id)"
          @mouseenter="hoveredNodeId = node.id"
          @mouseleave="hoveredNodeId = null"
        >
          <circle :r="node.radius" />
          <text
            class="graph-node-dot-label"
            :x="node.radius + 6"
            y="4"
          >
            {{ node.label }}
          </text>
        </g>

        <g
          v-if="selectedNodeOverlay"
          class="graph-node-overlay"
          :transform="`translate(${selectedNodeOverlay.x}, ${selectedNodeOverlay.y})`"
        >
          <rect
            class="graph-node-overlay__card"
            :width="selectedNodeOverlay.width"
            :height="selectedNodeOverlay.height"
            rx="8"
            ry="8"
          />
          <text
            class="graph-node-overlay__title"
            x="14"
            y="24"
          >
            {{ selectedNodeOverlay.panel.name }}
          </text>
          <text
            class="graph-node-overlay__subtitle"
            x="14"
            y="42"
          >
            {{ selectedNodeOverlay.node.label }}
          </text>
          <text
            v-for="(line, idx) in selectedNodeOverlay.summaryLines"
            :key="`summary-${idx}`"
            class="graph-node-overlay__summary"
            x="14"
            :y="62 + idx * 14"
          >
            {{ line }}
          </text>
          <g
            v-for="(item, idx) in selectedNodeOverlay.wrappedAttrs"
            :key="item.key"
            :transform="`translate(0, ${selectedNodeOverlay.attrsStartY + overlayAttrOffset(selectedNodeOverlay.wrappedAttrs, idx)})`"
          >
            <text
              class="graph-node-overlay__key"
              x="14"
              y="0"
            >
              {{ item.key }}
            </text>
            <text
              v-for="(line, lineIdx) in item.lines"
              :key="`${item.key}-${lineIdx}`"
              class="graph-node-overlay__value"
              x="92"
              :y="lineIdx * 14"
            >
              {{ line }}
            </text>
          </g>
        </g>
      </EngineTheaterGraphCanvas>
    </div>
  </section>
</template>

<style scoped>
.theater-graph { display: grid; grid-template-rows: auto minmax(0, 1fr); gap: 10px; height: 100%; border: 1px solid #d1d5db; border-radius: 2px; background: #ffffff; padding: 10px; min-height: 0; }
.theater-panel__eyebrow { margin: 0 0 4px; font-size: 11px; color: #6b7280; font-weight: 600; }
.theater-graph__head { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.theater-graph__head strong { font-size: 14px; color: #111827; }
.theater-graph__controls { display: flex; align-items: center; gap: 8px; }
.edge-toggle { display: inline-flex; align-items: center; gap: 6px; font-size: 11px; color: #4b5563; }
.theater-graph__density { display: inline-flex; border: 1px solid #d1d5db; border-radius: 2px; background: #fff; }
.theater-graph__density-btn { border: none; border-right: 1px solid #e5e7eb; background: #fff; padding: 4px 8px; font-size: 11px; color: #6b7280; }
.theater-graph__density-btn:last-child { border-right: none; }
.theater-graph__density-btn.is-active { background: #111827; color: #fff; }
.theater-graph__reset { border: 1px solid #d1d5db; background: #fff; border-radius: 2px; font-size: 11px; padding: 6px 10px; }
.theater-graph__layout { display: grid; gap: 10px; grid-template-columns: minmax(0, 1fr); min-height: 0; height: 100%; }
.graph-clear-hit { fill: transparent; pointer-events: all; }
.graph-edge { fill: none; stroke: #9ca3af; stroke-width: 1.2; cursor: pointer; }
.graph-edge.is-highlighted { stroke: #3498db; stroke-width: 2.8; }
.graph-edge.is-live { stroke: #0ea5e9; stroke-width: 2.4; }
.graph-edge-label-bg { fill: rgba(255,255,255,.95); stroke: #e5e7eb; stroke-width: .8; cursor: pointer; }
.graph-edge-label { font-size: 9px; fill: #4b5563; text-anchor: middle; dominant-baseline: middle; cursor: pointer; }
.graph-node { cursor: pointer; transition: opacity .2s ease; }
.graph-node.is-muted { opacity: 1; }
.graph-node circle { fill: #64748b; stroke: rgba(255, 255, 255, 0.95); stroke-width: 1.4; }
.graph-node.is-selected circle { fill: #e11d48; stroke: #fff; stroke-width: 2; }
.graph-node.is-live circle { fill: #0284c7; }
.graph-node.is-task circle { fill: #111827; stroke: #f8fafc; stroke-width: 2.4; }
.graph-node.is-actor circle { fill: #7c3aed; }
.graph-node.is-probe circle { fill: #0f766e; }
.graph-node-dot-label { fill: #334155; font-size: 11px; font-weight: 600; pointer-events: none; user-select: none; }
.graph-node-overlay { pointer-events: none; }
.graph-node-overlay__card { fill: rgba(255, 255, 255, 0.96); stroke: #d1d5db; stroke-width: 1; }
.graph-node-overlay__title { fill: #111827; font-size: 14px; font-weight: 700; }
.graph-node-overlay__subtitle { fill: #6b7280; font-size: 11px; }
.graph-node-overlay__summary { fill: #334155; font-size: 11px; }
.graph-node-overlay__key { fill: #64748b; font-size: 10px; }
.graph-node-overlay__value { fill: #111827; font-size: 10px; }
</style>
