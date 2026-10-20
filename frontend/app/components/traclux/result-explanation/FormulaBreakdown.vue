<script setup lang="ts">
import MetricFactorCard from './MetricFactorCard.vue'
import ResultSurface from './ResultSurface.vue'
import type { FormulaTerm, MetricFactor } from './types'

defineProps<{
  title: string
  note: string
  formulaParts: string[]
  terms: FormulaTerm[]
  result: string | number
  factors: MetricFactor[]
}>()
</script>

<template>
  <ResultSurface :title="title" :note="note" :aria-label="title" tone="formula">
    <div class="formula-line" aria-label="公式">
      <template v-for="(part, index) in formulaParts" :key="part">
        <span>{{ part }}</span>
        <small v-if="index < formulaParts.length - 1">+</small>
      </template>
    </div>

    <div class="score-equation" aria-label="计入分值">
      <template v-for="(term, index) in terms" :key="term.label">
        <span>{{ term.value }}</span>
        <small v-if="index < terms.length - 1">+</small>
      </template>
      <small>=</small>
      <strong>{{ result }}</strong>
    </div>

    <div class="factor-grid" aria-label="指标因子拆解">
      <MetricFactorCard
        v-for="factor in factors"
        :key="factor.label"
        :factor="factor"
      />
    </div>
  </ResultSurface>
</template>

<style scoped>
.formula-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-top: 16px;
}

.formula-line span {
  border: 1px solid #d5dce3;
  border-radius: 999px;
  background: #fff;
  color: #111827;
  font-size: 13px;
  font-weight: 750;
  padding: 7px 10px;
}

.formula-line small,
.score-equation small {
  color: #94a3b8;
  font-size: 14px;
  font-weight: 800;
}

.score-equation {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-top: 16px;
}

.score-equation span,
.score-equation strong {
  color: #111827;
  font-size: 34px;
  letter-spacing: -0.07em;
  line-height: 1;
}

.score-equation strong {
  color: #0f766e;
  font-size: 40px;
}

.factor-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}

@media (max-width: 980px) {
  .factor-grid {
    grid-template-columns: 1fr;
  }
}
</style>
