<script setup lang='ts'>
import type { TheaterGraphSceneEdge } from '~/types/engine'

interface RenderedEdge extends TheaterGraphSceneEdge {
  path: string
  isLive: boolean
  isHighlighted: boolean
  isMuted: boolean
  markerId: string
}

const props = defineProps<{
  edges: RenderedEdge[]
}>()
</script>

<template>
  <g
    v-for="edge in props.edges"
    :key="edge.id"
  >
    <path
      class="theater-edge"
      :class="[
        `is-${edge.type}`,
        `phase-${edge.phase}`,
        `pulse-${edge.pulseLevel}`,
        edge.isHighlighted ? 'is-highlighted' : '',
        edge.isLive ? 'is-live' : '',
        edge.isMuted ? 'is-muted' : ''
      ]"
      :d="edge.path"
      :marker-end="`url(#${edge.markerId})`"
    />
    <path
      v-if="edge.isHighlighted"
      class="theater-edge theater-edge--highlight"
      :d="edge.path"
    />
    <path
      v-if="edge.isLive || edge.pulseLevel >= 2"
      class="theater-edge theater-edge--live"
      :class="[`pulse-${edge.pulseLevel}`]"
      :d="edge.path"
      :marker-end="`url(#${edge.markerId})`"
    />
  </g>
</template>

<style scoped>
.theater-edge {
  fill: none;
  stroke: rgba(71, 85, 105, 0.18);
  stroke-width: 1.2;
  transition: opacity 0.24s ease, stroke 0.24s ease, stroke-width 0.24s ease;
  stroke-linecap: round;
}

.theater-edge.phase-entering {
  stroke-opacity: 0.94;
  stroke-width: 1.8;
}

.theater-edge.phase-resolved {
  stroke-opacity: 0.54;
}

.theater-edge.is-highlighted {
  stroke: rgba(15, 23, 42, 0.42);
  stroke-width: 1.8;
}

.theater-edge--highlight {
  stroke: rgba(15, 23, 42, 0.22);
  stroke-width: 4.2;
  filter: drop-shadow(0 0 10px rgba(15, 23, 42, 0.08));
}

.theater-edge.is-live {
  stroke: url(#theater-live-edge);
  stroke-width: 2.2;
}

.theater-edge--live {
  stroke: rgba(34, 211, 238, 0.76);
  stroke-width: 3.2;
  stroke-linecap: round;
  stroke-dasharray: 14 16;
  animation: theater-edge-flow 1.2s linear infinite;
}

.theater-edge--live.pulse-2 {
  stroke-width: 2.6;
  stroke-dasharray: 10 14;
  animation-duration: 1.5s;
}

.theater-edge--live.pulse-3 {
  stroke-width: 3.6;
  animation-duration: 1.05s;
}

.theater-edge.is-muted {
  opacity: 0.16;
}

.theater-edge.is-supports,
.theater-edge.is-mentions {
  stroke-dasharray: 5 7;
}

.theater-edge.is-converges {
  stroke-width: 1.8;
}

.theater-edge.phase-flowing.is-supports,
.theater-edge.phase-flowing.is-mentions {
  stroke: url(#theater-warning-edge);
}

.theater-edge.pulse-1 {
  stroke-opacity: 0.62;
}

.theater-edge.pulse-2 {
  stroke-opacity: 0.82;
}

.theater-edge.pulse-3 {
  stroke-opacity: 1;
}

@keyframes theater-edge-flow {
  from { stroke-dashoffset: 0; }
  to { stroke-dashoffset: -30; }
}
</style>
