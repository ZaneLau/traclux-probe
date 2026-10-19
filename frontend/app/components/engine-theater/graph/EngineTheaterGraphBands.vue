<script setup lang='ts'>
import type {
  TheaterGraphLayerBand,
  TheaterGraphLayerGuide
} from '~/composables/engine/useEngineTheaterGraphLayout'

const props = defineProps<{
  graphHeight: number
  activeLayer: number | null
  layerBands: TheaterGraphLayerBand[]
  layerGuides: TheaterGraphLayerGuide[]
}>()
</script>

<template>
  <g class="theater-graph__bands">
    <g
      v-for="band in props.layerBands"
      :key="`band-${band.layer}`"
    >
      <rect
        :x="band.left"
        y="46"
        :width="band.width"
        :height="props.graphHeight - 86"
        class="theater-graph__band"
        :class="[band.tone, props.activeLayer === band.layer ? 'is-active' : '']"
        rx="24"
        ry="24"
      />
    </g>
  </g>

  <g class="theater-graph__guides">
    <g
      v-for="guide in props.layerGuides"
      :key="`guide-${guide.layer}`"
    >
      <line
        :x1="guide.x"
        y1="34"
        :x2="guide.x"
        :y2="props.graphHeight - 26"
        :class="{ 'is-active': props.activeLayer === guide.layer }"
      />
    </g>
  </g>
</template>

<style scoped>
.theater-graph__band {
  fill: transparent;
  stroke: rgba(148, 163, 184, 0.12);
  stroke-width: 1;
}

.theater-graph__band.is-even {
  fill: transparent;
}

.theater-graph__band.is-odd {
  fill: transparent;
}

.theater-graph__band.is-active {
  fill: transparent;
  stroke: rgba(14, 165, 233, 0.28);
  stroke-dasharray: 6 8;
  filter: none;
  animation: none;
}

.theater-graph__guides line {
  stroke: rgba(148, 163, 184, 0.1);
  stroke-dasharray: 4 8;
}

.theater-graph__guides line.is-active {
  stroke: rgba(14, 165, 233, 0.2);
}
</style>
