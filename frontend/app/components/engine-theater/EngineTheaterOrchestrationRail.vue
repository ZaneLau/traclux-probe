<script setup lang='ts'>
import type { TheaterBucket, TheaterBucketKey } from '~/types/engine'

const props = defineProps<{
  buckets: TheaterBucket[]
  activeBucket: TheaterBucketKey | 'all'
}>()

const emit = defineEmits<{
  (event: 'toggle-bucket', bucket: TheaterBucketKey | 'all'): void
}>()

const toneClassMap: Record<TheaterBucketKey, string> = {
  coverage: 'is-coverage',
  mainstream: 'is-mainstream',
  adversarial: 'is-adversarial'
}
</script>

<template>
  <section class="theater-rail">
    <header class="theater-rail__head">
      <div>
        <p class="theater-panel__eyebrow">
          OATS 编排带
        </p>
        <strong>桶编排</strong>
      </div>
    </header>

    <div class="theater-rail__lanes">
      <button
        v-for="bucket in props.buckets"
        :key="bucket.key"
        type="button"
        class="theater-rail__lane"
        :class="[toneClassMap[bucket.key], props.activeBucket === bucket.key ? 'is-selected' : '']"
        @click="emit('toggle-bucket', bucket.key)"
      >
        <div class="theater-rail__lane-head">
          <strong>{{ bucket.label }}</strong>
          <span>{{ bucket.tone }}</span>
        </div>
        <p>{{ bucket.generatedProbeCount }}/{{ bucket.targetProbeCount }}</p>
      </button>
    </div>
  </section>
</template>

<style scoped>
.theater-rail {
  border: 1px solid #d1d5db;
  background: #ffffff;
  border-radius: 2px;
  padding: 10px;
}

.theater-rail__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}

.theater-panel__eyebrow {
  margin: 0 0 6px;
  font-size: 11px;
  color: #6b7280;
  font-weight: 600;
}

.theater-rail__head strong {
  font-size: 15px;
  color: #0f172a;
}

.theater-rail__lanes {
  display: flex;
  gap: 8px;
  overflow-x: auto;
}

.theater-rail__lane {
  border: 1px solid #d1d5db;
  background: #ffffff;
  border-radius: 2px;
  padding: 8px 10px;
  min-width: 120px;
  text-align: left;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.theater-rail__lane:hover {
  border-color: #9ca3af;
}

.theater-rail__lane.is-selected {
  border-color: #111827;
}

.theater-rail__lane.is-coverage {
  box-shadow: inset 2px 0 0 #0891b2;
}

.theater-rail__lane.is-mainstream {
  box-shadow: inset 2px 0 0 #16a34a;
}

.theater-rail__lane.is-adversarial {
  box-shadow: inset 2px 0 0 #dc2626;
}

.theater-rail__lane-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.theater-rail__lane-head strong {
  font-size: 16px;
  color: #0f172a;
}

.theater-rail__lane-head span {
  font-size: 11px;
  letter-spacing: 0.08em;
  color: #64748b;
  text-transform: uppercase;
}

.theater-rail__lane p {
  margin: 4px 0 0;
  color: #475569;
  font-size: 11px;
  line-height: 1.3;
}
</style>
