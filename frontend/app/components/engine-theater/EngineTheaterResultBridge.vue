<script setup lang="ts">
import type { TheaterResultBridge } from '~/types/engine'

const props = defineProps<{
  resultBridge: TheaterResultBridge
}>()

const emit = defineEmits<{
  (event: 'navigate', path: string): void
}>()
</script>

<template>
  <section class="theater-result-bridge">
    <header class="theater-result-bridge__head">
      <div>
        <p class="theater-panel__eyebrow">
          结果桥
        </p>
        <strong>结果摘要</strong>
      </div>
    </header>

    <div class="theater-result-bridge__grid">
      <article class="theater-result-bridge__metric">
        <span>ABV Index</span>
        <strong>{{ props.resultBridge.abvIndex.toFixed(1) }}</strong>
      </article>
      <article class="theater-result-bridge__metric">
        <span>AI 首选率</span>
        <strong>{{ props.resultBridge.firstChoiceRate.toFixed(1) }}%</strong>
      </article>
      <article class="theater-result-bridge__metric">
        <span>AI 确认率</span>
        <strong>{{ props.resultBridge.confirmationRate.toFixed(1) }}%</strong>
      </article>
      <article class="theater-result-bridge__metric">
        <span>防御完整度</span>
        <strong>{{ props.resultBridge.defenseIntegrity.toFixed(1) }}%</strong>
      </article>
    </div>

    <ul class="theater-result-bridge__list">
      <li
        v-for="line in props.resultBridge.summary"
        :key="line"
      >
        {{ line }}
      </li>
    </ul>

    <div class="theater-result-bridge__routes">
      <button
        v-for="route in props.resultBridge.routes"
        :key="route.path"
        type="button"
        class="theater-result-bridge__route"
        @click="emit('navigate', route.path)"
      >
        <strong>{{ route.label }}</strong>
        <span>{{ route.description }}</span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.theater-result-bridge {
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.9));
  border-radius: 24px;
  padding: 18px;
  display: grid;
  gap: 16px;
}

.theater-panel__eyebrow {
  margin: 0 0 6px;
  font-size: 11px;
  letter-spacing: 0.14em;
  color: #0f766e;
  text-transform: uppercase;
  font-weight: 700;
}

.theater-result-bridge__head strong {
  color: #0f172a;
  font-size: 15px;
}

.theater-result-bridge__grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.theater-result-bridge__metric {
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(255, 255, 255, 0.84);
  border-radius: 18px;
  padding: 14px;
  display: grid;
  gap: 4px;
}

.theater-result-bridge__metric span {
  color: #64748b;
  font-size: 11px;
}

.theater-result-bridge__metric strong {
  color: #0f172a;
  font-size: 24px;
}

.theater-result-bridge__list {
  margin: 0;
  padding-left: 16px;
  display: grid;
  gap: 4px;
  color: #475569;
  font-size: 12px;
  line-height: 1.6;
}

.theater-result-bridge__routes {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.theater-result-bridge__route {
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.86);
  border-radius: 18px;
  padding: 14px;
  text-align: left;
  display: grid;
  gap: 4px;
}

.theater-result-bridge__route strong {
  color: #0f172a;
  font-size: 14px;
}

.theater-result-bridge__route span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}
</style>
