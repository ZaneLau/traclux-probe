<script setup lang="ts">
const props = withDefaults(defineProps<{
  compact?: boolean
}>(), {
  compact: false
})

const uid = useId()

const gridId = `${uid}-grid`
const lineGradientId = `${uid}-line-gradient`
const lineGlowId = `${uid}-line-glow`
const nodeGradientId = `${uid}-node-gradient`
const nodeGlowId = `${uid}-node-glow`
const softGlowId = `${uid}-soft-glow`
const ambientGlowId = `${uid}-ambient-glow`

const width = computed(() => props.compact ? 720 : 960)
const height = computed(() => props.compact ? 420 : 520)

const nodes = [
  { cx: 92, cy: 52, r: 12, glow: 38, tone: 'strong', delay: '0s' },
  { cx: 118, cy: 70, r: 7, glow: 24, tone: 'soft', delay: '0.4s' },
  { cx: 274, cy: 106, r: 10, glow: 30, tone: 'soft', delay: '0.9s' },
  { cx: 164, cy: 174, r: 11, glow: 28, tone: 'soft', delay: '1.1s' },
  { cx: 308, cy: 232, r: 8, glow: 22, tone: 'soft', delay: '0.8s' },
  { cx: 448, cy: 206, r: 13, glow: 40, tone: 'strong', delay: '0.2s' },
  { cx: 360, cy: 286, r: 7, glow: 20, tone: 'soft', delay: '1.4s' },
  { cx: 568, cy: 268, r: 8, glow: 22, tone: 'soft', delay: '0.6s' },
  { cx: 220, cy: 330, r: 12, glow: 36, tone: 'strong', delay: '0.7s' },
  { cx: 516, cy: 360, r: 10, glow: 28, tone: 'soft', delay: '1.2s' },
  { cx: 680, cy: 214, r: 9, glow: 24, tone: 'soft', delay: '1.6s' },
  { cx: 408, cy: 414, r: 10, glow: 26, tone: 'soft', delay: '0.3s' },
  { cx: 818, cy: 468, r: 13, glow: 42, tone: 'strong', delay: '1s' },
  { cx: 590, cy: 456, r: 8, glow: 22, tone: 'soft', delay: '1.5s' },
  { cx: 710, cy: 392, r: 10, glow: 28, tone: 'soft', delay: '0.5s' }
] as const

const links = [
  { x1: 94, y1: 54, x2: 276, y2: 108, delay: '0.1s' },
  { x1: 120, y1: 72, x2: 164, y2: 174, delay: '0.7s' },
  { x1: 164, y1: 174, x2: 448, y2: 206, delay: '1.2s' },
  { x1: 164, y1: 174, x2: 220, y2: 330, delay: '0.6s' },
  { x1: 220, y1: 330, x2: 308, y2: 232, delay: '0.3s' },
  { x1: 308, y1: 232, x2: 680, y2: 214, delay: '1.5s' },
  { x1: 308, y1: 232, x2: 516, y2: 360, delay: '1s' },
  { x1: 360, y1: 286, x2: 710, y2: 392, delay: '0.8s' },
  { x1: 516, y1: 360, x2: 818, y2: 468, delay: '0.4s' },
  { x1: 590, y1: 456, x2: 818, y2: 468, delay: '1.3s' }
] as const

const labels = [
  { x: 572, y: 84, text: 'MENTION' },
  { x: 760, y: 154, text: 'RECOMMEND' },
  { x: 54, y: 298, text: 'INTENT' },
  { x: 624, y: 426, text: 'EVIDENCE' }
] as const
</script>

<template>
  <div
    class="geo-signal-illustration"
    :class="compact ? 'geo-signal-illustration--compact' : ''"
  >
    <svg
      class="geo-signal-illustration__svg"
      :viewBox="`0 0 ${width} ${height}`"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <pattern
          :id="gridId"
          width="48"
          height="48"
          patternUnits="userSpaceOnUse"
        >
          <path
            d="M48 0H0V48"
            stroke="rgba(148, 163, 184, 0.09)"
            stroke-width="1"
          />
        </pattern>

        <linearGradient
          :id="lineGradientId"
          x1="0"
          y1="0"
          x2="1"
          y2="0"
        >
          <stop
            offset="0%"
            stop-color="rgba(34, 211, 238, 0.02)"
          />
          <stop
            offset="38%"
            stop-color="rgba(103, 232, 249, 0.94)"
          />
          <stop
            offset="72%"
            stop-color="rgba(59, 130, 246, 0.78)"
          />
          <stop
            offset="100%"
            stop-color="rgba(34, 211, 238, 0.04)"
          />
        </linearGradient>

        <linearGradient
          :id="nodeGradientId"
          x1="0"
          y1="0"
          x2="1"
          y2="1"
        >
          <stop
            offset="0%"
            stop-color="#E0FBFF"
          />
          <stop
            offset="34%"
            stop-color="#7DEBFF"
          />
          <stop
            offset="100%"
            stop-color="#0891B2"
          />
        </linearGradient>

        <radialGradient
          :id="ambientGlowId"
          cx="0"
          cy="0"
          r="1"
          gradientUnits="userSpaceOnUse"
          gradientTransform="translate(518 290) rotate(90) scale(280 420)"
        >
          <stop
            offset="0%"
            stop-color="rgba(34, 211, 238, 0.12)"
          />
          <stop
            offset="60%"
            stop-color="rgba(15, 23, 42, 0.02)"
          />
          <stop
            offset="100%"
            stop-color="rgba(15, 23, 42, 0)"
          />
        </radialGradient>

        <filter
          :id="lineGlowId"
          x="-30%"
          y="-300%"
          width="160%"
          height="700%"
        >
          <feGaussianBlur
            stdDeviation="1.8"
            result="blur"
          />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>

        <filter
          :id="nodeGlowId"
          x="-280%"
          y="-280%"
          width="660%"
          height="660%"
        >
          <feGaussianBlur
            stdDeviation="9"
            result="blur"
          />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>

        <filter
          :id="softGlowId"
          x="-220%"
          y="-220%"
          width="540%"
          height="540%"
        >
          <feGaussianBlur
            stdDeviation="6"
            result="blur"
          />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>

      <rect
        x="18"
        y="34"
        :width="width - 36"
        :height="height - 68"
        rx="30"
        fill="url(#ambientGlowId)"
        opacity="0.82"
      />
      <rect
        x="28"
        y="36"
        :width="width - 56"
        :height="height - 92"
        rx="26"
        :fill="`url(#${gridId})`"
        opacity="0.8"
      />

      <g class="geo-signal-illustration__links">
        <line
          v-for="(link, index) in links"
          :key="`link-${index}`"
          class="geo-signal-illustration__link"
          :x1="link.x1"
          :y1="link.y1"
          :x2="link.x2"
          :y2="link.y2"
          :style="{ animationDelay: link.delay }"
          :stroke="`url(#${lineGradientId})`"
          stroke-width="1.6"
          stroke-linecap="round"
          :filter="`url(#${lineGlowId})`"
        />
      </g>

      <g class="geo-signal-illustration__nodes">
        <g
          v-for="(node, index) in nodes"
          :key="`node-${index}`"
          :transform="`translate(${node.cx} ${node.cy})`"
        >
          <g
            class="geo-signal-illustration__node"
            :style="{ animationDelay: node.delay }"
          >
            <circle
              cx="0"
              cy="0"
              :r="node.glow"
              fill="#22D3EE"
              :opacity="node.tone === 'strong' ? 0.08 : 0.05"
              :filter="`url(#${softGlowId})`"
            />
            <circle
              cx="0"
              cy="0"
              :r="node.r * 1.85"
              fill="#38BDF8"
              :opacity="node.tone === 'strong' ? 0.12 : 0.08"
              :filter="`url(#${softGlowId})`"
            />
            <circle
              cx="0"
              cy="0"
              :r="node.r"
              :fill="`url(#${nodeGradientId})`"
              :filter="`url(#${nodeGlowId})`"
            />
            <circle
              cx="-2.4"
              cy="-2.4"
              :r="Math.max(2.2, node.r * 0.22)"
              fill="#ECFEFF"
              opacity="0.82"
            />
          </g>
        </g>
      </g>

      <g class="geo-signal-illustration__labels">
        <g
          v-for="label in labels"
          :key="label.text"
          :transform="`translate(${label.x} ${label.y})`"
        >
          <g class="geo-signal-illustration__label">
            <rect
              x="0"
              y="0"
              :width="label.text === 'RECOMMEND' ? 170 : label.text === 'EVIDENCE' ? 154 : 134"
              height="52"
              rx="26"
              fill="rgba(255, 255, 255, 0.045)"
              stroke="rgba(255, 255, 255, 0.1)"
            />
            <text
              x="20"
              y="31"
              fill="rgba(224, 242, 254, 0.88)"
              font-family="var(--font-display)"
              font-size="22"
              letter-spacing="2.6"
            >
              {{ label.text }}
            </text>
          </g>
        </g>
      </g>
    </svg>
  </div>
</template>

<style scoped>
.geo-signal-illustration {
  position: relative;
  width: 100%;
  margin-top: auto;
}

.geo-signal-illustration__svg {
  display: block;
  width: 100%;
  height: auto;
}

.geo-signal-illustration__link {
  stroke-dasharray: 12 8;
  animation: geoSignalLink 7.8s ease-in-out infinite;
}

.geo-signal-illustration__node {
  transform-origin: center;
  animation: geoSignalNode 6.4s ease-in-out infinite;
}

.geo-signal-illustration__label {
  animation: geoSignalLabel 8.2s ease-in-out infinite;
}

.geo-signal-illustration--compact .geo-signal-illustration__label {
  opacity: 0.92;
}

@keyframes geoSignalNode {
  0%,
  100% {
    opacity: 0.84;
    transform: scale(1);
  }
  50% {
    opacity: 1;
    transform: scale(1.07);
  }
}

@keyframes geoSignalLink {
  0%,
  100% {
    opacity: 0.42;
    stroke-dashoffset: 0;
  }
  50% {
    opacity: 0.96;
    stroke-dashoffset: -26;
  }
}

@keyframes geoSignalLabel {
  0%,
  100% {
    opacity: 0.84;
    transform: translateY(0);
  }
  50% {
    opacity: 1;
    transform: translateY(-4px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .geo-signal-illustration__link,
  .geo-signal-illustration__node,
  .geo-signal-illustration__label {
    animation: none;
  }
}
</style>
