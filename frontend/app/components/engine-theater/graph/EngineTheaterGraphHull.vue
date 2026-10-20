<script setup lang="ts">
import type { TheaterGraphHullBox } from '~/composables/engine/useEngineTheaterGraphLayout'

const props = defineProps<{
  hullBoxes: TheaterGraphHullBox[]
}>()

const emit = defineEmits<{
  (event: 'select-cluster' | 'hover-cluster', clusterId: string): void
  (event: 'leave-cluster'): void
}>()
</script>

<template>
  <g class="theater-graph__hulls">
    <g
      v-for="hull in props.hullBoxes"
      :key="hull.id"
      class="theater-graph__hull"
      :class="[
        `is-${hull.kind}`,
        hull.isActive ? 'is-active' : '',
        hull.isHovered ? 'is-hovered' : '',
        hull.isExpanded ? 'is-expanded' : '',
        `pulse-${hull.pulseLevel}`
      ]"
      tabindex="0"
      role="button"
      @mouseenter="emit('hover-cluster', hull.id)"
      @mouseleave="emit('leave-cluster')"
      @click.stop="emit('select-cluster', hull.id)"
      @keydown.enter.stop.prevent="emit('select-cluster', hull.id)"
      @keydown.space.stop.prevent="emit('select-cluster', hull.id)"
    >
      <rect
        :x="hull.x"
        :y="hull.y"
        :width="hull.width"
        :height="hull.height"
        rx="26"
        ry="26"
      />
    </g>
  </g>
</template>

<style scoped>
.theater-graph__hull {
  cursor: pointer;
}

.theater-graph__hull rect {
  fill: rgba(255, 255, 255, 0.12);
  stroke: rgba(148, 163, 184, 0.24);
  stroke-width: 1;
  stroke-dasharray: 8 10;
  filter: drop-shadow(0 18px 32px rgba(15, 23, 42, 0.04));
  transition: fill 0.24s ease, stroke 0.24s ease, stroke-width 0.24s ease, opacity 0.24s ease;
}

.theater-graph__hull:hover rect,
.theater-graph__hull:focus-visible rect {
  fill: rgba(255, 255, 255, 0.3);
  stroke: rgba(14, 165, 233, 0.34);
}

.theater-graph__hull.is-active rect {
  stroke-width: 1.5;
  stroke: rgba(14, 165, 233, 0.44);
  fill: rgba(240, 249, 255, 0.28);
}

.theater-graph__hull.is-expanded rect {
  stroke-dasharray: 0;
  fill: rgba(240, 249, 255, 0.34);
  filter: drop-shadow(0 20px 28px rgba(14, 165, 233, 0.12));
}

.theater-graph__hull.is-hovered rect {
  fill: rgba(248, 250, 252, 0.28);
}

.theater-graph__hull.is-topic rect { stroke: rgba(37, 99, 235, 0.28); }
.theater-graph__hull.is-state rect { stroke: rgba(22, 163, 74, 0.26); }
.theater-graph__hull.is-actor rect { stroke: rgba(124, 58, 237, 0.24); }
.theater-graph__hull.is-probe-batch rect { stroke: rgba(234, 88, 12, 0.24); }

.theater-graph__hull.pulse-1 rect {
  opacity: 0.94;
}

.theater-graph__hull.pulse-2 rect,
.theater-graph__hull.pulse-3 rect {
  animation: theater-graph-hull-breathe 2s ease-in-out infinite;
}

@keyframes theater-graph-hull-breathe {
  0%, 100% { opacity: 0.82; }
  50% { opacity: 1; }
}
</style>
