<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { TheaterConsoleLine, TheaterEvent } from '~/types/engine'

const props = defineProps<{
  events: TheaterEvent[]
  selectedEventId: string | null
  currentEventId: string | null
  relatedEventIds: Set<string>
  autoplay: boolean
  playhead: number
  consoleLines: TheaterConsoleLine[]
}>()

const emit = defineEmits<{
  (event: 'select-event', eventId: string, nodeId?: string | null): void
}>()

const scrollRef = ref<HTMLElement | null>(null)

const eventTypeLabelMap: Record<TheaterEvent['type'], string> = {
  task_ingested: 'TASK',
  state_assembled: 'STATE',
  bucket_bound: 'BUCKET',
  actor_generated: 'ACTOR',
  probe_generated: 'PROBE',
  attempt_started: 'EXEC',
  evidence_attached: 'EVID',
  brand_mentioned: 'MENTION',
  round_summary: 'ROUND',
  final_gate: 'GATE',
  engine_log: 'ENGINE'
}

const levelLabelMap: Record<string, string> = {
  system: 'SYS',
  route: 'ROUTE',
  network: 'NET',
  gate: 'GATE'
}

type UnifiedRow
  = | { kind: 'event', data: TheaterEvent }
    | { kind: 'log', data: TheaterConsoleLine }

const mergedRows = computed<UnifiedRow[]>(() => {
  const rows: UnifiedRow[] = []
  const visibleEvents = props.events.slice(-8)
  for (const ev of visibleEvents) {
    rows.push({ kind: 'event', data: ev })
  }
  for (const line of props.consoleLines) {
    rows.push({ kind: 'log', data: line })
  }
  rows.sort((a, b) => {
    const seqA = a.kind === 'event' ? a.data.sequence : a.data.sequence
    const seqB = b.kind === 'event' ? b.data.sequence : b.data.sequence
    return seqA - seqB
  })
  return rows
})

function isRowSelected(row: UnifiedRow): boolean {
  if (row.kind === 'event') {
    return props.selectedEventId === row.data.id
  }
  return Boolean(row.data.eventId && props.selectedEventId === row.data.eventId)
}

function isRowCurrent(row: UnifiedRow): boolean {
  if (row.kind === 'event') {
    return props.currentEventId === row.data.id
  }
  return Boolean(row.data.eventId && props.currentEventId === row.data.eventId)
}

watch(
  () => [props.events.length, props.consoleLines.length, props.currentEventId],
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
  <section class="theater-waterfall">
    <header class="theater-waterfall__head">
      <div class="theater-waterfall__title">
        <p class="theater-waterfall__eyebrow">
          MONITOR
        </p>
        <strong>引擎终端</strong>
      </div>

      <div class="theater-waterfall__meta">
        <span
          class="theater-waterfall__badge"
          :class="props.autoplay ? 'is-running' : 'is-paused'"
        >{{ props.autoplay ? 'LIVE' : 'PAUSED' }}</span>
      </div>
    </header>

    <div
      ref="scrollRef"
      class="theater-waterfall__body"
    >
      <div
        v-for="row in mergedRows"
        :key="row.kind === 'event' ? row.data.id : row.data.id"
        class="theater-waterfall__row"
        :class="[
          row.kind === 'event' ? `is-event is-${row.data.status}` : `is-log is-${row.data.level}`,
          isRowSelected(row) ? 'is-selected' : '',
          isRowCurrent(row) ? 'is-current' : '',
          row.kind === 'event' && props.relatedEventIds.has(row.data.id) ? 'is-related' : ''
        ]"
        role="button"
        tabindex="0"
        @click="row.kind === 'event' ? emit('select-event', row.data.id, row.data.nodeId) : (row.data.eventId && emit('select-event', row.data.eventId))"
      >
        <span class="theater-waterfall__accent" />
        <span class="theater-waterfall__time">{{ row.kind === 'event' ? row.data.timeLabel : row.data.timeLabel }}</span>
        <span
          class="theater-waterfall__tag"
          :class="row.kind === 'event' ? `is-${row.data.status}` : `is-${row.data.level}`"
        >{{ row.kind === 'event' ? eventTypeLabelMap[row.data.type] : levelLabelMap[row.data.level] || row.data.level }}</span>
        <span class="theater-waterfall__msg">
          <template v-if="row.kind === 'event'">
            {{ row.data.title }}<template v-if="row.data.summary"> — {{ row.data.summary }}</template>
          </template>
          <template v-else>{{ row.data.message }}</template>
        </span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.theater-waterfall {
  height: 100%;
  min-height: 0;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: linear-gradient(180deg, #08111e, #111c2c);
  border-radius: 2px;
  padding: 10px;
  color: #dbeafe;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 6px;
  position: relative;
  overflow: hidden;
}

.theater-waterfall::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(180deg, rgba(103,232,249,0) 0%, rgba(103,232,249,0.02) 50%, rgba(103,232,249,0) 100%);
  animation: terminal-scan 8s linear infinite;
}

/* ---- Header ---- */
.theater-waterfall__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  z-index: 1;
}

.theater-waterfall__title { display: grid; gap: 2px; }

.theater-waterfall__eyebrow {
  margin: 0;
  font-size: 9px;
  letter-spacing: 0.18em;
  color: #67e8f9;
  text-transform: uppercase;
  font-weight: 700;
}

.theater-waterfall__head strong { font-size: 13px; color: #e2e8f0; }

.theater-waterfall__meta { display: flex; align-items: center; gap: 6px; }

.theater-waterfall__badge {
  padding: 3px 8px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.1em;
  border-radius: 2px;
}

.theater-waterfall__badge.is-running {
  color: #34d399;
  background: rgba(52, 211, 153, 0.1);
  border: 1px solid rgba(52, 211, 153, 0.2);
}

.theater-waterfall__badge.is-paused {
  color: #94a3b8;
  background: rgba(148, 163, 184, 0.06);
  border: 1px solid rgba(148, 163, 184, 0.12);
}

/* ---- Body ---- */
.theater-waterfall__body {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 2px;
  align-content: start;
  min-height: 0;
  height: 100%;
  overflow-y: auto;
  padding-right: 2px;
}

/* ---- Row (unified for both event & log) ---- */
.theater-waterfall__row {
  position: relative;
  display: grid;
  grid-template-columns: 48px 52px minmax(0, 1fr);
  gap: 10px;
  align-items: baseline;
  border: 1px solid rgba(103, 232, 249, 0.06);
  background: rgba(15, 23, 42, 0.45);
  border-radius: 2px;
  padding: 5px 8px 5px 10px;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

/* Left accent line */
.theater-waterfall__accent {
  position: absolute;
  left: 0;
  top: 2px;
  bottom: 2px;
  width: 2px;
  border-radius: 1px;
  background: #475569;
}

/* Status accents */
.is-event.is-info .theater-waterfall__accent { background: #38bdf8; }
.is-event.is-running .theater-waterfall__accent { background: #818cf8; }
.is-event.is-success .theater-waterfall__accent { background: #34d399; }
.is-event.is-warning .theater-waterfall__accent { background: #fbbf24; }
.is-event.is-error .theater-waterfall__accent { background: #f87171; }
.is-log.is-system .theater-waterfall__accent { background: #475569; }
.is-log.is-route .theater-waterfall__accent { background: #c084fc; }
.is-log.is-network .theater-waterfall__accent { background: #2dd4bf; }
.is-log.is-gate .theater-waterfall__accent { background: #fbbf24; }

/* Interactive states */
.theater-waterfall__row:hover {
  border-color: rgba(103, 232, 249, 0.16);
}

.theater-waterfall__row.is-selected {
  border-color: rgba(103, 232, 249, 0.28);
  background: rgba(15, 23, 42, 0.72);
}

.theater-waterfall__row.is-current {
  border-color: rgba(52, 211, 153, 0.28);
  background: rgba(14, 24, 39, 0.82);
}

.theater-waterfall__row.is-related:not(.is-selected):not(.is-current) {
  border-color: rgba(52, 211, 153, 0.14);
}

/* ---- Cells ---- */
.theater-waterfall__time {
  font-size: 11px;
  color: rgba(148, 163, 184, 0.72);
  font-variant-numeric: tabular-nums;
  line-height: 1.55;
}

.theater-waterfall__tag {
  font-size: 9px;
  letter-spacing: 0.1em;
  font-weight: 600;
  text-transform: uppercase;
  color: #94a3b8;
  line-height: 1.55;
}

/* Tag color by status/level */
.theater-waterfall__tag.is-info { color: #38bdf8; }
.theater-waterfall__tag.is-running { color: #818cf8; }
.theater-waterfall__tag.is-success { color: #34d399; }
.theater-waterfall__tag.is-warning { color: #fbbf24; }
.theater-waterfall__tag.is-error { color: #f87171; }
.theater-waterfall__tag.is-system { color: #64748b; }
.theater-waterfall__tag.is-route { color: #c084fc; }
.theater-waterfall__tag.is-network { color: #2dd4bf; }
.theater-waterfall__tag.is-gate { color: #fbbf24; }

.theater-waterfall__msg {
  font-size: 11px;
  line-height: 1.55;
  color: #cbd5e1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.is-log .theater-waterfall__msg {
  color: rgba(203, 213, 225, 0.6);
}

@keyframes terminal-scan {
  from { transform: translateY(-100%); }
  to { transform: translateY(100%); }
}
</style>
