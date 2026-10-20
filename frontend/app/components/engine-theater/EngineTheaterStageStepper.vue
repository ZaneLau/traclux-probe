<script setup lang='ts'>
import type { TheaterStage, TheaterStageStep } from '~/types/engine'

const props = defineProps<{
  stages: TheaterStageStep[]
  currentStage: TheaterStage
  stageFilter: TheaterStage | 'all'
}>()

const emit = defineEmits<{
  (event: 'toggle-stage', stage: TheaterStage | 'all'): void
}>()

function onStepClick(stage: TheaterStage) {
  emit('toggle-stage', stage)
}
</script>

<template>
  <section class="theater-stepper">
    <button
      v-for="step in props.stages"
      :key="step.key"
      type="button"
      class="theater-stepper__item"
      :class="[
        `is-${step.status}`,
        props.currentStage === step.key ? 'is-current' : '',
        props.stageFilter === step.key ? 'is-filtered' : ''
      ]"
      @click="onStepClick(step.key)"
    >
      <span class="theater-stepper__index">{{ props.stages.findIndex(item => item.key === step.key) + 1 }}</span>
      <span class="theater-stepper__copy">
        <strong>{{ step.label }}</strong>
        <small>{{ step.description }}</small>
      </span>
    </button>
  </section>
</template>

<style scoped>
.theater-stepper {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
}
.theater-stepper__item {
  border: 1px solid #d1d5db;
  background: #ffffff;
  color: #111827;
  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    transform 0.2s ease;
}

.theater-stepper__item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-height: 44px;
  padding: 6px 8px;
  border-radius: 2px;
  text-align: left;
}

.theater-stepper__item:hover {
  border-color: #9ca3af;
}
.theater-stepper__item.is-filtered {
  border-color: #9ca3af;
  background: #ffffff;
}

.theater-stepper__item.is-current {
  border-color: #111827;
}

.theater-stepper__item.is-done {
  border-color: rgba(22, 163, 74, 0.18);
}

.theater-stepper__item.is-active {
  border-color: rgba(14, 116, 144, 0.28);
}

.theater-stepper__index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 999px;
  background: #111827;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  flex: 0 0 20px;
}

.theater-stepper__item.is-done .theater-stepper__index {
  background: #4b5563;
}

.theater-stepper__item.is-active .theater-stepper__index,
.theater-stepper__item.is-current .theater-stepper__index {
  background: #111827;
}

.theater-stepper__copy {
  display: grid;
  gap: 2px;
}

.theater-stepper__copy strong {
  font-size: 11px;
  line-height: 1.2;
  letter-spacing: -0.02em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.theater-stepper__copy small {
  color: #64748b;
  display: none;
}
</style>
