<script setup lang="ts">
import type { TheaterGraphSceneNode } from '~/types/engine'

interface RenderedNode extends TheaterGraphSceneNode {
  x: number
  y: number
  width: number
  height: number
  isLive: boolean
  classList: string[]
}

const props = defineProps<{
  nodes: RenderedNode[]
}>()

const emit = defineEmits<{
  (event: 'hover-node' | 'select-node', nodeId: string): void
  (event: 'leave-node'): void
}>()
</script>

<template>
  <g
    v-for="node in props.nodes"
    :key="node.id"
    class="theater-node"
    :class="node.classList"
    :transform="`translate(${node.x}, ${node.y})`"
    @mouseenter="emit('hover-node', node.id)"
    @mouseleave="emit('leave-node')"
    @click="emit('select-node', node.id)"
  >
    <rect
      :x="-node.width / 2"
      :y="-node.height / 2"
      :width="node.width"
      :height="node.height"
      rx="14"
      ry="14"
    />
    <rect
      class="theater-node__accent"
      :x="-node.width / 2 + 8"
      :y="-node.height / 2 + 8"
      width="4"
      :height="node.height - 16"
      rx="2"
      ry="2"
    />
    <rect
      v-if="node.isLive"
      class="theater-node__live-shell"
      :x="-node.width / 2 - 6"
      :y="-node.height / 2 - 6"
      :width="node.width + 12"
      :height="node.height + 12"
      rx="18"
      ry="18"
    />
    <circle
      class="theater-node__pulse"
      :cx="-node.width / 2 + 14"
      :cy="-node.height / 2 + 14"
      r="4"
    />
    <path
      class="theater-node__status-line"
      :d="`M ${-node.width / 2 + 20} ${node.height / 2 - 10} L ${node.width / 2 - 20} ${node.height / 2 - 10}`"
    />
    <circle
      v-if="node.isLive"
      class="theater-node__halo"
      cx="0"
      cy="0"
      :r="Math.max(node.width, node.height) / 1.45"
    />
    <circle
      v-if="node.isLive"
      class="theater-node__orbit"
      cx="0"
      cy="0"
      :r="Math.max(node.width, node.height) / 1.18"
    />
    <text
      class="theater-node__title"
      :x="-node.width / 2 + 24"
      y="-6"
    >
      {{ node.label }}
    </text>
    <text
      class="theater-node__subtitle"
      :x="-node.width / 2 + 24"
      y="12"
    >
      {{ node.subtitle }}
    </text>
  </g>
</template>

<style scoped>
.theater-node {
  cursor: pointer;
  transition: opacity 0.24s ease;
  animation: theater-node-enter 0.38s cubic-bezier(0.22, 1, 0.36, 1);
}

.theater-node:hover {
  opacity: 1;
}

.theater-node.is-muted {
  opacity: 0.18;
}

.theater-node.phase-entering {
  animation: theater-node-enter 0.42s cubic-bezier(0.22, 1, 0.36, 1), theater-node-breathe 1.8s ease-in-out infinite;
}

.theater-node.phase-resolved rect {
  opacity: 0.88;
}

.theater-node.phase-dimmed rect,
.theater-node.phase-dimmed .theater-node__title,
.theater-node.phase-dimmed .theater-node__subtitle {
  opacity: 0.72;
}

.theater-node.emphasis-focus rect {
  stroke-width: 1.8;
}

.theater-node.emphasis-success rect {
  stroke: rgba(16, 185, 129, 0.34);
}

.theater-node.emphasis-warning rect {
  stroke: rgba(217, 119, 6, 0.34);
}

.theater-node rect {
  fill: rgba(255, 255, 255, 0.94);
  stroke: rgba(148, 163, 184, 0.24);
  stroke-width: 1;
  filter: drop-shadow(0 16px 24px rgba(15, 23, 42, 0.08));
}

.theater-node__accent {
  fill: currentColor;
  opacity: 0.16;
  stroke: none;
}

.theater-node__live-shell {
  fill: none;
  stroke: rgba(34, 211, 238, 0.42);
  stroke-width: 1.6;
  filter: drop-shadow(0 0 10px rgba(34, 211, 238, 0.18));
}

.theater-node__status-line {
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  opacity: 0.18;
}

.theater-node.is-selected rect {
  stroke: rgba(15, 23, 42, 0.44);
  stroke-width: 1.8;
}

.theater-node.is-cluster-focus rect {
  stroke-width: 1.5;
}

.theater-node.is-live rect {
  stroke: rgba(34, 211, 238, 0.46);
}

.theater-node.is-live .theater-node__status-line {
  opacity: 0.52;
}

.theater-node.is-task rect,
.theater-node.is-result rect {
  fill: rgba(15, 23, 42, 0.96);
  stroke: rgba(34, 211, 238, 0.4);
}

.theater-node.is-topic rect { fill: rgba(239, 246, 255, 0.96); }
.theater-node.is-state rect { fill: rgba(236, 253, 245, 0.96); }
.theater-node.is-bucket rect { fill: rgba(255, 247, 237, 0.98); }
.theater-node.is-actor rect { fill: rgba(245, 243, 255, 0.96); }
.theater-node.is-probe rect { fill: rgba(248, 250, 252, 0.98); }
.theater-node.is-attempt rect { fill: rgba(241, 245, 249, 0.98); }
.theater-node.is-evidence rect { fill: rgba(240, 253, 250, 0.98); }
.theater-node.is-source rect { fill: rgba(239, 246, 255, 0.98); }
.theater-node.is-mention rect { fill: rgba(254, 249, 195, 0.98); }

.theater-node.is-warning rect { stroke: rgba(217, 119, 6, 0.46); }
.theater-node.is-error rect { stroke: rgba(220, 38, 38, 0.52); }

.theater-node__pulse {
  fill: currentColor;
  animation: theater-node-pulse 1.6s ease-in-out infinite;
}

.theater-node__halo {
  fill: rgba(34, 211, 238, 0.08);
  animation: theater-node-halo 1.8s ease-out infinite;
  transform-origin: center;
}

.theater-node__orbit {
  fill: none;
  stroke: rgba(34, 211, 238, 0.26);
  stroke-width: 1;
  stroke-dasharray: 5 7;
  animation: theater-node-orbit 6s linear infinite;
  transform-origin: center;
}

.theater-node.is-task,
.theater-node.is-result { color: #22d3ee; }
.theater-node.is-topic { color: #2563eb; }
.theater-node.is-state { color: #16a34a; }
.theater-node.is-bucket { color: #ea580c; }
.theater-node.is-actor { color: #7c3aed; }
.theater-node.is-probe { color: #0f172a; }
.theater-node.is-attempt { color: #0f766e; }
.theater-node.is-evidence { color: #0d9488; }
.theater-node.is-source { color: #0369a1; }
.theater-node.is-mention { color: #a16207; }

.theater-node__title,
.theater-node__subtitle {
  user-select: none;
}

.theater-node__title {
  fill: #0f172a;
  font-size: 12px;
  font-weight: 700;
}

.theater-node__subtitle {
  fill: #64748b;
  font-size: 10px;
}

.theater-node.is-task .theater-node__title,
.theater-node.is-task .theater-node__subtitle,
.theater-node.is-result .theater-node__title,
.theater-node.is-result .theater-node__subtitle {
  fill: #f8fafc;
}

@keyframes theater-node-enter {
  from { opacity: 0; transform: translateY(8px) scale(0.96); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

@keyframes theater-node-breathe {
  0%, 100% { opacity: 0.94; }
  50% { opacity: 1; }
}

@keyframes theater-node-pulse {
  0%, 100% { opacity: 0.35; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.4); }
}

@keyframes theater-node-halo {
  0% { opacity: 0.6; transform: scale(0.88); }
  100% { opacity: 0; transform: scale(1.24); }
}

@keyframes theater-node-orbit {
  from { stroke-dashoffset: 0; }
  to { stroke-dashoffset: -48; }
}
</style>
