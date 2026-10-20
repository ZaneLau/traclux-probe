<script setup lang="ts">
import ResultSurface from './ResultSurface.vue'
import type { ProbeSignalRow } from './types'

defineProps<{
  title: string
  note: string
  probes: ProbeSignalRow[]
}>()
</script>

<template>
  <ResultSurface :title="title" :note="note" aria-label="探针信号明细">
    <div class="probe-table">
      <div class="probe-head">
        <span>探针</span>
        <span>Topic</span>
        <span>提及</span>
        <span>推荐</span>
        <span>Top3</span>
        <span>查看</span>
      </div>
      <article v-for="item in probes" :key="item.id" class="probe-row">
        <div class="probe-question">
          <span>{{ item.id }}</span>
          <strong>{{ item.question }}</strong>
        </div>
        <span class="probe-topic">{{ item.topic }}</span>
        <span class="probe-mark" :class="{ active: item.mentioned }">{{ item.mentioned ? '是' : '-' }}</span>
        <span class="probe-mark" :class="{ active: item.recommended }">{{ item.recommended ? '是' : '-' }}</span>
        <span class="probe-mark" :class="{ active: item.top3 }">{{ item.top3 ? '是' : '-' }}</span>
        <NuxtLink
          class="probe-link"
          :to="{ path: '/probe-analysis', query: { probeId: item.id } }"
        >
          查看
        </NuxtLink>
      </article>
    </div>
  </ResultSurface>
</template>

<style scoped>
.probe-table {
  display: grid;
  border: 1px solid #d5dce3;
  border-radius: 2px;
  background: #fff;
  margin-top: 14px;
  overflow: hidden;
}

.probe-head,
.probe-row {
  display: grid;
  grid-template-columns: minmax(250px, 1fr) 66px 52px 52px 52px 58px;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
}

.probe-head {
  border-bottom: 1px solid #d5dce3;
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
  font-weight: 750;
}

.probe-row + .probe-row {
  border-top: 1px solid #eef2f7;
}

.probe-question {
  display: flex;
  gap: 10px;
  align-items: baseline;
  min-width: 0;
}

.probe-question span {
  color: #64748b;
  font-size: 12px;
  white-space: nowrap;
}

.probe-question strong {
  overflow: hidden;
  color: #111827;
  font-size: 14px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.probe-topic,
.probe-mark,
.probe-link {
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
  padding: 4px 8px;
  text-align: center;
  white-space: nowrap;
}

.probe-mark {
  color: #94a3b8;
}

.probe-mark.active {
  border-color: rgba(15, 118, 110, 0.24);
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
  font-weight: 800;
}

.probe-link {
  border-color: rgba(15, 118, 110, 0.28);
  background: #fff;
  color: #0f766e;
  font-weight: 800;
  text-decoration: none;
}

.probe-link:hover {
  background: rgba(15, 118, 110, 0.08);
}

@media (max-width: 980px) {
  .probe-head {
    display: none;
  }

  .probe-row {
    grid-template-columns: minmax(0, 1fr) repeat(3, 48px) 52px;
  }

  .probe-topic {
    display: none;
  }
}
</style>
