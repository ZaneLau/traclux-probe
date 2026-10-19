<script setup lang='ts'>
import type { OptionItem, TheaterStage, TheaterTask } from '~/types/engine'

const props = defineProps<{
  task: TheaterTask
  selectedBrand: string
  brandOptions: OptionItem<string>[]
  currentStage: TheaterStage
  autoplay: boolean
  playhead: number
  maxSequence: number
}>()

const emit = defineEmits<{
  (event: 'update:selectedBrand', value: string): void
  (event: 'toggle-autoplay' | 'reset'): void
}>()
</script>

<template>
  <header class="theater-head">
    <div class="theater-head__identity">
      <div class="theater-head__title">
        <div>
          <p class="theater-head__eyebrow">
            引擎观察窗
          </p>
          <h1>引擎观察窗</h1>
        </div>
        <label
          class="brand-select-wrap theater-head__brand-select"
          for="engine-theater-brand-select"
        >
          <select
            id="engine-theater-brand-select"
            class="select-input"
            :value="props.selectedBrand"
            @change="emit('update:selectedBrand', ($event.target as HTMLSelectElement).value)"
          >
            <option
              value=""
              disabled
            >
              {{ props.brandOptions.length ? '选择品牌空间' : '暂无品牌空间' }}
            </option>
            <option
              v-for="brand in props.brandOptions"
              :key="`engine-theater-${brand.key}`"
              :value="brand.key"
            >
              {{ brand.label }}
            </option>
          </select>
        </label>
      </div>
    </div>

    <div class="theater-head__controls">
      <div class="theater-head__progress">
        <div class="theater-head__progress-copy">
          <strong>进度</strong>
          <span>{{ props.playhead }} / {{ props.maxSequence }}</span>
        </div>
        <div class="theater-head__progress-bar">
          <span :style="{ width: `${(props.playhead / Math.max(props.maxSequence, 1)) * 100}%` }" />
        </div>
      </div>

      <div class="head-actions">
        <button
          type="button"
          class="btn-plain"
          @click="emit('toggle-autoplay')"
        >
          {{ props.autoplay ? '暂停' : '播放' }}
        </button>
        <button
          type="button"
          class="btn-plain"
          @click="emit('reset')"
        >
          重置
        </button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.theater-head {
  display: grid;
  gap: 10px;
}

.theater-head__identity {
  display: grid;
  gap: 10px;
}

.theater-head__title {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.theater-head__eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  color: #6b7280;
  font-weight: 600;
}

.theater-head h1 {
  margin: 0;
  font-size: 24px;
  line-height: 1;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: #111827;
}

.theater-head__brand-select {
  min-width: 172px;
}

.theater-head__summary {
  display: none;
}

.theater-head__pill-group {
  display: none;
}

.theater-head__pill {
  display: inline-flex;
  align-items: center;
  border: 1px solid #d1d5db;
  background: #ffffff;
  color: #4b5563;
  border-radius: 2px;
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 500;
}

.theater-head__meta-grid {
  display: none;
}

.theater-head__meta-card {
  border: 1px solid #d1d5db;
  background: #ffffff;
  border-radius: 2px;
  padding: 8px 10px;
}

.theater-head__meta-card small {
  display: block;
  margin-bottom: 4px;
  font-size: 10px;
  color: #6b7280;
}

.theater-head__meta-card strong {
  font-size: 13px;
  color: #111827;
}

.theater-head__controls {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: flex-end;
}

.theater-head__progress {
  min-width: 260px;
  display: grid;
  gap: 6px;
}

.theater-head__progress-copy {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #6b7280;
}

.theater-head__progress-copy strong {
  color: #111827;
}

.theater-head__progress-bar {
  position: relative;
  overflow: hidden;
  height: 6px;
  border-radius: 2px;
  background: #e5e7eb;
}

.theater-head__progress-bar span {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: inherit;
  background: #111827;
  transition: width 0.35s ease;
}
</style>
