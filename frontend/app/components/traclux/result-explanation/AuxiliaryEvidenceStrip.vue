<script setup lang="ts">
import ResultSurface from './ResultSurface.vue'
import type { AuxiliaryStat, TimelinePoint } from './types'

defineProps<{
  title: string
  note: string
  stats: AuxiliaryStat[]
  timeline: TimelinePoint[]
}>()
</script>

<template>
  <ResultSurface :title="title" :note="note" aria-label="辅助信息" tone="muted">
    <div class="support-layout">
      <div class="support-stat-grid" aria-label="辅助指标">
        <div v-for="stat in stats" :key="stat.label" class="support-stat">
          <span>{{ stat.tag }}</span>
          <strong>{{ stat.value }}</strong>
          <small>{{ stat.label }}</small>
        </div>
      </div>

      <div class="window-timeline" aria-label="近 14 天样本分布">
        <span
          v-for="day in timeline"
          :key="day.label"
          :class="{ active: day.active }"
        >
          {{ day.label }}
        </span>
      </div>
    </div>
  </ResultSurface>
</template>

<style scoped>
.support-layout {
  display: grid;
  grid-template-columns: 250px 1fr;
  gap: 14px;
  margin-top: 14px;
}

.support-stat-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.support-stat {
  border: 1px solid #d5dce3;
  border-radius: 2px;
  background: #fff;
  padding: 11px;
}

.support-stat span {
  color: #64748b;
  font-size: 12px;
  font-weight: 750;
}

.support-stat strong {
  display: block;
  color: #111827;
  font-size: 28px;
  letter-spacing: -0.06em;
}

.support-stat small {
  color: #475569;
  font-size: 12px;
}

.window-timeline {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
  align-items: end;
}

.window-timeline span {
  border-top: 3px solid #d1d5db;
  color: #64748b;
  font-size: 12px;
  padding-top: 8px;
}

.window-timeline span.active {
  border-color: #0f766e;
  color: #111827;
  font-weight: 750;
}

@media (max-width: 980px) {
  .support-layout,
  .support-stat-grid {
    grid-template-columns: 1fr;
  }
}
</style>
