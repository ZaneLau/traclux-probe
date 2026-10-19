<script setup lang="ts">
import type { MetricFactor } from './types'

defineProps<{
  factor: MetricFactor
}>()
</script>

<template>
  <article class="factor-card" :data-tone="factor.tone">
    <div class="factor-card__top">
      <h3>{{ factor.label }}</h3>
      <strong>{{ factor.rate.toFixed(1) }}%</strong>
    </div>
    <div class="factor-card__meta">
      <span>{{ factor.count }}/{{ factor.total }}</span>
      <span>权重 {{ factor.weight }}%</span>
      <span>计入 {{ factor.score.toFixed(1) }}</span>
    </div>
    <div class="factor-track">
      <span :style="{ width: `${factor.rate}%` }" />
    </div>
  </article>
</template>

<style scoped>
.factor-card {
  min-width: 0;
  border: 1px solid #d5dce3;
  border-radius: 2px;
  background: #fff;
  padding: 13px;
}

.factor-card__top {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: baseline;
}

.factor-card__top h3 {
  margin: 0;
  color: #111827;
  font-size: 16px;
  letter-spacing: -0.04em;
}

.factor-card__top strong {
  color: #111827;
  font-size: 26px;
  letter-spacing: -0.06em;
}

.factor-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.factor-card__meta span {
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
  padding: 4px 7px;
}

.factor-track {
  height: 7px;
  margin-top: 12px;
  overflow: hidden;
  background: #e5e7eb;
}

.factor-track span {
  display: block;
  height: 100%;
  background: #0f766e;
}

.factor-card[data-tone="blue"] .factor-track span {
  background: #2563eb;
}

.factor-card[data-tone="amber"] .factor-track span {
  background: #d97706;
}
</style>
