<script setup lang="ts">
import type { MetricFact } from './types'

defineProps<{
  metricName: string
  metricValue: string | number
  valueSuffix?: string
  windowLabel: string
  facts: MetricFact[]
}>()
</script>

<template>
  <section class="metric-panel" :aria-label="`${metricName}结果解释`">
    <div class="metric-panel__main">
      <div class="metric-panel__title">
        <h2>{{ metricName }}</h2>
        <span>{{ windowLabel }}</span>
      </div>
      <div class="metric-panel__value">
        <strong>{{ metricValue }}</strong>
        <span v-if="valueSuffix">{{ valueSuffix }}</span>
      </div>
    </div>

    <div class="metric-panel__facts" aria-label="命中概览">
      <div v-for="fact in facts" :key="fact.label">
        <strong>{{ fact.value }}</strong>
        <span>{{ fact.label }}</span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.metric-panel {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
  min-width: 0;
  border-radius: 2px;
  background: #111827;
  color: #fff;
  padding: 26px;
}

.metric-panel__title {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: baseline;
}

.metric-panel__title h2 {
  margin: 0;
  color: #fff;
  font-size: 20px;
  font-weight: 850;
  letter-spacing: -0.045em;
}

.metric-panel__title span {
  color: rgba(255, 255, 255, 0.72);
  font-size: 13px;
  font-weight: 750;
  white-space: nowrap;
}

.metric-panel__value {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 18px;
}

.metric-panel__value strong {
  color: #fff;
  font-size: clamp(76px, 10vw, 124px);
  line-height: 0.86;
  letter-spacing: -0.09em;
}

.metric-panel__value span {
  color: rgba(255, 255, 255, 0.72);
  font-size: 18px;
}

.metric-panel__facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.metric-panel__facts div {
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.08);
  padding: 10px 12px;
}

.metric-panel__facts strong {
  display: block;
  color: #5eead4;
  font-size: 28px;
  line-height: 1;
}

.metric-panel__facts span {
  display: block;
  margin-top: 5px;
  color: rgba(255, 255, 255, 0.72);
  font-size: 13px;
}
</style>
