<script setup lang='ts'>
import EngineTheaterConsole from './EngineTheaterConsole.vue'
import type { TheaterBucket, TheaterConsoleLine, TheaterEvent, TheaterProbeRun, TheaterTask, TheaterStage } from '~/types/engine'

const props = defineProps<{
  task: TheaterTask
  currentStage: TheaterStage
  currentEvent: TheaterEvent | null
  visibleEventsCount: number
  visibleProbeCount: number
  visibleEvidenceCount: number
  visibleMentionCount: number
  activeBucketStats: TheaterBucket | null
  selectedEvent: TheaterEvent | null
  selectedProbeRun: TheaterProbeRun | null
  consoleLines: TheaterConsoleLine[]
  selectedEventId: string | null
}>()

const emit = defineEmits<{
  (event: 'select-event', eventId: string): void
}>()

const stageLabelMap: Record<TheaterStage, string> = {
  'task-decomposition': '任务拆解',
  'state-assembly': '状态装配',
  'orchestration': '编排成团',
  'actor-generation': '虚拟用户',
  'probe-generation': '探针生成',
  'execution': '执行探测',
  'result-bridging': '结果收束'
}
</script>

<template>
  <section class="theater-monitor">
    <header class="theater-monitor__head">
      <div>
        <p class="theater-panel__eyebrow">
          监控台
        </p>
        <strong>监控概览</strong>
      </div>
      <span class="theater-monitor__stage">{{ stageLabelMap[props.currentStage] }}</span>
    </header>

    <EngineTheaterConsole
      :lines="props.consoleLines"
      :selected-event-id="props.selectedEventId"
      :current-event-id="props.currentEvent?.id || null"
      @select-event="emit('select-event', $event)"
    />
  </section>
</template>

<style scoped>
.theater-monitor { display: grid; align-content: start; gap: 12px; }
.theater-monitor__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.theater-panel__eyebrow { margin: 0 0 6px; font-size: 11px; letter-spacing: 0.14em; color: #0f766e; text-transform: uppercase; font-weight: 700; }
.theater-monitor__head strong { color: #0f172a; font-size: 15px; }
.theater-panel__eyebrow { margin: 0 0 6px; font-size: 11px; color: #6b7280; font-weight: 600; letter-spacing: 0; text-transform: none; }
.theater-monitor__head strong { color: #111827; font-size: 14px; }
.theater-monitor__stage { border: 1px solid #d1d5db; background: #fff; border-radius: 2px; padding: 4px 8px; font-size: 11px; color: #6b7280; }
</style>
