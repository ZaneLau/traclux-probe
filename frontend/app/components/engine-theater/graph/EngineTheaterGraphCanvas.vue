<script setup lang='ts'>
import { ref, useSlots } from 'vue'

const props = defineProps<{
  graphWidth: number
  graphHeight: number
}>()

const svgEl = ref<SVGSVGElement | null>(null)
const viewportEl = ref<SVGGElement | null>(null)
const slots = useSlots()

defineExpose({
  svgEl,
  viewportEl
})
</script>

<template>
  <div class="theater-graph__viewport">
    <div
      v-if="slots.status"
      class="theater-graph__stage-status"
    >
      <slot name="status" />
    </div>

    <svg
      ref="svgEl"
      class="theater-graph__svg"
      :viewBox="`0 0 ${props.graphWidth} ${props.graphHeight}`"
      preserveAspectRatio="xMidYMid meet"
    >
      <defs>
        <linearGradient
          id="theater-live-edge"
          x1="0%"
          y1="0%"
          x2="100%"
          y2="0%"
        >
          <stop
            offset="0%"
            stop-color="rgba(14,165,233,0.2)"
          />
          <stop
            offset="48%"
            stop-color="rgba(34,211,238,1)"
          />
          <stop
            offset="100%"
            stop-color="rgba(16,185,129,0.25)"
          />
        </linearGradient>
        <linearGradient
          id="theater-warning-edge"
          x1="0%"
          y1="0%"
          x2="100%"
          y2="0%"
        >
          <stop
            offset="0%"
            stop-color="rgba(245,158,11,0.2)"
          />
          <stop
            offset="52%"
            stop-color="rgba(249,115,22,0.9)"
          />
          <stop
            offset="100%"
            stop-color="rgba(220,38,38,0.22)"
          />
        </linearGradient>
        <marker
          id="theater-edge-arrow"
          markerWidth="10"
          markerHeight="10"
          refX="8"
          refY="5"
          orient="auto"
        >
          <path
            d="M 0 0 L 10 5 L 0 10 z"
            fill="rgba(71, 85, 105, 0.34)"
          />
        </marker>
        <marker
          id="theater-edge-arrow-live"
          markerWidth="10"
          markerHeight="10"
          refX="8"
          refY="5"
          orient="auto"
        >
          <path
            d="M 0 0 L 10 5 L 0 10 z"
            fill="rgba(34, 211, 238, 0.82)"
          />
        </marker>
        <marker
          id="theater-edge-arrow-warn"
          markerWidth="10"
          markerHeight="10"
          refX="8"
          refY="5"
          orient="auto"
        >
          <path
            d="M 0 0 L 10 5 L 0 10 z"
            fill="rgba(249, 115, 22, 0.82)"
          />
        </marker>
      </defs>

      <slot name="background" />

      <g ref="viewportEl">
        <slot />
      </g>
    </svg>
  </div>
</template>

<style scoped>
.theater-graph__viewport {
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 2px;
  min-height: 0;
  height: 100%;
  background:
    radial-gradient(circle at 16% 18%, rgba(14, 116, 144, 0.1), transparent 22%),
    radial-gradient(circle at 82% 4%, rgba(8, 145, 178, 0.12), transparent 18%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(246, 250, 253, 0.92));
}

.theater-graph__viewport::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.06) 1px, transparent 1px);
  background-size: 28px 28px;
}

.theater-graph__stage-status {
  position: absolute;
  left: 16px;
  top: 16px;
  z-index: 2;
  display: inline-grid;
  gap: 6px;
  max-width: 420px;
  padding: 12px 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(14px);
  border-radius: 18px;
  box-shadow: 0 24px 42px -30px rgba(15, 23, 42, 0.3);
}

.theater-graph__svg {
  display: block;
  width: 100%;
  height: 100%;
  cursor: grab;
}

.theater-graph__svg:active {
  cursor: grabbing;
}

:deep(.theater-graph__stage-chip) {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  border-radius: 999px;
  padding: 5px 9px;
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

:deep(.theater-graph__stage-status strong) {
  font-size: 13px;
  line-height: 1.5;
  color: #0f172a;
}
</style>
