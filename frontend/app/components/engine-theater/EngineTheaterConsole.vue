<script setup lang='ts'>
import { nextTick, ref, watch } from 'vue'
import type { TheaterConsoleLine } from '~/types/engine'

const props = defineProps<{
  lines: TheaterConsoleLine[]
  selectedEventId: string | null
  currentEventId: string | null
}>()

const emit = defineEmits<{
  (event: 'select-event', eventId: string): void
}>()

const scrollRef = ref<HTMLElement | null>(null)

watch(
  () => [props.lines.length, props.currentEventId],
  async () => {
    await nextTick()
    if (!scrollRef.value) {
      return
    }
    scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  },
  { flush: 'post' }
)
</script>

<template>
  <section class="theater-console">
    <header class="theater-console__head">
      <div class="theater-console__title">
        <p class="theater-panel__eyebrow">
          监控终端
        </p>
        <strong>运行日志</strong>
      </div>
    </header>

    <div
      ref="scrollRef"
      class="theater-console__body"
    >
      <button
        v-for="line in props.lines"
        :key="line.id"
        type="button"
        class="theater-console__line"
        :class="[`is-${line.level}`, props.selectedEventId === line.eventId ? 'is-selected' : '', props.currentEventId === line.eventId ? 'is-live' : '']"
        @click="line.eventId && emit('select-event', line.eventId)"
      >
        <span class="theater-console__time">{{ line.timeLabel }}</span>
        <span class="theater-console__level">{{ line.level }}</span>
        <span class="theater-console__message">{{ line.message }}</span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.theater-console {
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: linear-gradient(180deg, #08111e, #111c2c);
  border-radius: 2px;
  padding: 8px;
  color: #dbeafe;
  min-height: 0;
}

.theater-console::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(180deg, rgba(103,232,249,0) 0%, rgba(103,232,249,0.025) 50%, rgba(103,232,249,0) 100%);
  animation: console-scan 8s linear infinite;
}

.theater-console__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.theater-console__title { display: grid; gap: 6px; }

.theater-panel__eyebrow {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.14em;
  color: #67e8f9;
  text-transform: uppercase;
  font-weight: 700;
}

.theater-console__head strong { font-size: 14px; }

.theater-console__body {
  position: relative;
  display: grid;
  gap: 8px;
  max-height: 300px;
  overflow: auto;
  padding-right: 4px;
}

.theater-console__line {
  position: relative;
  display: grid;
  grid-template-columns: 56px 46px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  border: 1px solid rgba(103, 232, 249, 0.08);
  background: rgba(15, 23, 42, 0.62);
  border-radius: 2px;
  padding: 6px 8px;
  text-align: left;
  color: inherit;
  transition: border-color 0.18s ease, background 0.18s ease, transform 0.18s ease;
}

.theater-console__line:hover { border-color: rgba(103, 232, 249, 0.24); }

.theater-console__line.is-selected { border-color: rgba(103, 232, 249, 0.36); }

.theater-console__line.is-live {
  border-color: rgba(52, 211, 153, 0.34);
  background: rgba(14, 24, 39, 0.86);
  box-shadow: inset 3px 0 0 #34d399;
}

.theater-console__line.is-live::after {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 42px;
  background: linear-gradient(90deg, rgba(52,211,153,0.16), rgba(52,211,153,0));
  pointer-events: none;
}

.theater-console__time { color: rgba(191, 219, 254, 0.72); font-size: 11px; }
.theater-console__level { font-size: 10px; letter-spacing: 0.1em; text-transform: uppercase; color: #7dd3fc; }
.theater-console__line.is-network .theater-console__level { color: #34d399; }
.theater-console__line.is-gate .theater-console__level { color: #fbbf24; }
.theater-console__line.is-route .theater-console__level { color: #c084fc; }
.theater-console__message { font-size: 12px; line-height: 1.55; }

@keyframes console-scan {
  from { transform: translateY(-100%); }
  to { transform: translateY(100%); }
}
</style>
