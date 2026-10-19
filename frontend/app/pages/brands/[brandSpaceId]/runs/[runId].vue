<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { resolveNavPageTitleByPath } from '~/composables/useDashboardNav'
import type { BrandSpaceRunDetailView, RunStatus } from '~/types/brand-space'
import { useWorkbenchShellState } from '~/composables/useWorkbenchShellState'

definePageMeta({
  layout: false,
  middleware: 'auth',
  requiresAuth: true
})

const route = useRoute()
const brandSpacesApi = useBrandSpacesApi()
const {
  workbenchRef,
  workbenchScrolled,
  syncWorkbenchScrolled,
  resetWorkbenchScrolled
} = useWorkbenchShellState()
const brandSpaceId = computed(() => String(route.params.brandSpaceId || ''))
const runId = computed(() => String(route.params.runId || ''))
const runtime = ref<BrandSpaceRunDetailView | null>(null)
const loading = ref(false)
const errorMessage = ref('')

const pageBrandName = computed(() => runtime.value?.brandName || '品牌空间')

useSeoMeta({
  title: () => `${pageBrandName.value} · ${resolveNavPageTitleByPath('/brands', '品牌管理')} · 运行详情 · Traclux 探镜`
})

const backToRuns = computed(() => ({
  path: `/brands/${encodeURIComponent(brandSpaceId.value)}/runs`,
  query: {
    ...(typeof route.query.dateFrom === 'string' ? { dateFrom: route.query.dateFrom } : {}),
    ...(typeof route.query.dateTo === 'string' ? { dateTo: route.query.dateTo } : {})
  }
}))

const overviewCards = computed(() => {
  const overview = runtime.value?.overview
  return [
    {
      label: '运行状态',
      value: overview?.statusLabel || '未同步',
      detail: overview?.triggerLabel || '未标记触发方式'
    },
    {
      label: '计划探针',
      value: `${overview?.plannedProbeCount ?? 0}`,
      detail: '这次运行原计划要跑的探针量。'
    },
    {
      label: '实际完成',
      value: `${overview?.completedProbeCount ?? 0}`,
      detail: '这里看真实完成的探针量。'
    },
    {
      label: '证据数',
      value: `${overview?.evidenceCount ?? 0}`,
      detail: '这次运行最终收集到的证据条数。'
    }
  ]
})

const scheduleDecisionCards = computed(() => {
  const decision = runtime.value?.scheduleDecision
  return [
    {
      label: '调度快照',
      value: resolveSnapshotLabel(decision?.snapshotAvailable),
      detail: '确认这次运行有没有留下可复盘的调度快照。'
    },
    {
      label: '触发背景',
      value: resolveScheduleSceneLabel(decision),
      detail: '帮助理解这是冷启动、刚改配置还是常规续跑。'
    }
  ]
})

const LEGACY_USER_QUOTA_KEYS = new Set([
  'quotaLimited',
  'remainingQuota'
])

function formatDateTime(value?: string | null, fallback = '--') {
  if (!value) {
    return fallback
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

function formatDuration(value?: number | null) {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return '--'
  }
  if (value < 60) {
    return `${Math.round(value)} 秒`
  }
  const minutes = Math.floor(value / 60)
  const seconds = Math.round(value % 60)
  return `${minutes} 分 ${seconds} 秒`
}

function resolveSnapshotLabel(value?: boolean | null) {
  if (value === true) {
    return '已记录'
  }
  if (value === false) {
    return '缺失'
  }
  return '--'
}

function resolveScheduleSceneLabel(
  decision?: {
    coldStart?: boolean | null
    justEdited?: boolean | null
  } | null
) {
  if (!decision) {
    return '--'
  }
  const labels: string[] = []
  if (decision.coldStart) {
    labels.push('冷启动')
  }
  if (decision.justEdited) {
    labels.push('刚改配置')
  }
  return labels.length ? labels.join(' / ') : '常规续跑'
}

function resolveStatusTone(status?: RunStatus | null) {
  const normalized = String(status || '').trim().toLowerCase()
  if (normalized === 'completed') {
    return 'status-pill--success'
  }
  if (normalized === 'partial' || normalized === 'running' || normalized === 'created') {
    return 'status-pill--warning'
  }
  if (normalized === 'failed' || normalized === 'cancelled') {
    return 'status-pill--danger'
  }
  return 'status-pill--default'
}

function resolveProbeStatusTone(status?: string | null) {
  const normalized = String(status || '').trim().toLowerCase()
  if (['succeeded', 'success', 'completed', 'complete'].includes(normalized)) {
    return 'status-pill--success'
  }
  if (normalized === 'running') {
    return 'status-pill--warning'
  }
  if (['failed', 'error'].includes(normalized)) {
    return 'status-pill--danger'
  }
  return 'status-pill--default'
}

function formatLatency(value?: number | null) {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return '--'
  }
  if (value < 1000) {
    return `${Math.round(value)} ms`
  }
  return `${(value / 1000).toFixed(1)} s`
}

function stringifyBlock(value: unknown) {
  const sanitized = sanitizeUserFacingRawValue(value)
  if (sanitized == null) {
    return '暂无记录'
  }
  try {
    return JSON.stringify(sanitized, null, 2)
  } catch {
    return String(sanitized)
  }
}

function sanitizeUserFacingRawValue(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(item => sanitizeUserFacingRawValue(item))
  }
  if (!value || typeof value !== 'object') {
    return value
  }
  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>)
      .filter(([key]) => !LEGACY_USER_QUOTA_KEYS.has(key))
      .map(([key, nestedValue]) => [key, sanitizeUserFacingRawValue(nestedValue)])
  )
}

async function loadDetail() {
  if (!brandSpaceId.value || !runId.value) {
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    runtime.value = await brandSpacesApi.runDetail(brandSpaceId.value, runId.value)
  } catch (error) {
    errorMessage.value = error instanceof Error && error.message ? error.message : '运行详情加载失败'
  } finally {
    loading.value = false
  }
}

function onWorkbenchScroll(event: Event) {
  syncWorkbenchScrolled(event.target as HTMLElement | null)
}

onMounted(async () => {
  resetWorkbenchScrolled()
  await loadDetail()
})
</script>

<template>
  <main class="dashboard-shell">
    <GeoWorkbenchSidebar />

    <section
      ref="workbenchRef"
      class="workbench run-detail-workbench"
      @scroll.passive="onWorkbenchScroll"
    >
      <div
        class="workbench-sticky-layer"
        :class="{ 'workbench-sticky-layer--scrolled': workbenchScrolled }"
      >
        <header class="overview-head">
          <div class="brand-zone brand-zone--stacked">
            <div class="brand-zone__copy">
              <h1>{{ pageBrandName }}</h1>
              <span>品牌空间运行详情</span>
            </div>
          </div>

          <div class="head-actions">
            <NuxtLink
              class="btn-plain"
              :to="backToRuns"
            >
              返回运行记录
            </NuxtLink>
          </div>
        </header>
      </div>

      <section class="surface notice-surface notice-surface--muted">
        <div class="notice-copy">
          <strong>这里把单次运行拆成四层：概览、调度、执行、原始记录</strong>
          <span>先让人看懂这次为什么这样跑，再决定是否继续深挖底层原始数据。</span>
        </div>
      </section>

      <section class="summary-grid">
        <article
          v-for="card in overviewCards"
          :key="card.label"
          class="surface mini-surface"
        >
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
          <small>{{ card.detail }}</small>
        </article>
      </section>

      <section
        v-if="errorMessage"
        class="surface section-surface empty-state"
      >
        <strong>运行详情暂时不可用</strong>
        <span>{{ errorMessage }}</span>
      </section>

      <section
        v-else-if="loading && !runtime"
        class="surface section-surface empty-state"
      >
        <strong>正在读取运行详情</strong>
        <span>我正在把这次运行的概览、调度、执行和原始记录都组装出来。</span>
      </section>

      <template v-else-if="runtime">
        <section class="surface section-surface detail-section">
          <div class="surface-head surface-head--compact">
            <div class="surface-head__title">
              <strong>运行概览</strong>
              <span>先回答这次什么时候跑、跑了多少、是否成功。</span>
            </div>
            <i
              class="status-pill"
              :class="resolveStatusTone(runtime.overview?.status)"
            >
              {{ runtime.overview?.statusLabel || '未同步' }}
            </i>
          </div>

          <div class="fact-grid">
            <article class="fact-card">
              <span>运行实例 ID</span>
              <strong>{{ runtime.runId }}</strong>
            </article>
            <article class="fact-card">
              <span>运行日期</span>
              <strong>{{ runtime.overview?.runDate || '--' }}</strong>
            </article>
            <article class="fact-card">
              <span>开始时间</span>
              <strong>{{ formatDateTime(runtime.overview?.startedAt) }}</strong>
            </article>
            <article class="fact-card">
              <span>结束时间</span>
              <strong>{{ formatDateTime(runtime.overview?.completedAt) }}</strong>
            </article>
            <article class="fact-card">
              <span>总耗时</span>
              <strong>{{ formatDuration(runtime.overview?.durationSeconds) }}</strong>
            </article>
            <article class="fact-card">
              <span>失败数</span>
              <strong>{{ runtime.overview?.failedProbeCount ?? 0 }}</strong>
            </article>
          </div>

          <section
            v-if="runtime.overview?.failureMessage"
            class="surface notice-surface notice-surface--error"
          >
            <div class="notice-copy">
              <strong>这次运行留下了失败说明</strong>
              <span>{{ runtime.overview.failureMessage }}</span>
            </div>
          </section>
        </section>

        <section class="surface section-surface detail-section">
          <div class="surface-head surface-head--compact">
            <div class="surface-head__title">
              <strong>调度决策</strong>
              <span>这里解释为什么这次会跑这些主题、分到这些量。</span>
            </div>
          </div>

          <div class="decision-grid">
            <article class="decision-card">
              <span>覆盖策略</span>
              <strong>{{ runtime.scheduleDecision?.coverageStrategy || '--' }}</strong>
            </article>
            <article
              v-for="card in scheduleDecisionCards"
              :key="card.label"
              class="decision-card"
            >
              <span>{{ card.label }}</span>
              <strong>{{ card.value }}</strong>
              <small>{{ card.detail }}</small>
            </article>
          </div>

          <div class="topic-columns">
            <article class="topic-panel">
              <div class="topic-panel__head">
                <strong>本轮选中主题</strong>
                <span>{{ runtime.scheduleDecision?.selectedTopics?.length || 0 }} 个</span>
              </div>
              <p
                v-if="runtime.scheduleDecision?.schedulingReason"
                class="topic-panel__summary"
              >
                {{ runtime.scheduleDecision.schedulingReason }}
              </p>
              <div
                v-if="!(runtime.scheduleDecision?.selectedTopics || []).length"
                class="empty-state empty-state--compact"
              >
                <span>这次运行里没有可读的选中主题快照。</span>
              </div>
              <div
                v-for="topic in runtime.scheduleDecision?.selectedTopics || []"
                :key="`${topic.topicId || topic.topicName}-selected`"
                class="topic-card"
              >
                <strong>{{ topic.topicName || '未命名主题' }}</strong>
                <span>计划分配 {{ topic.plannedProbeCount ?? 0 }} 个探针</span>
                <small>{{ (topic.reasonTags || []).join(' / ') || '未记录优先原因' }}</small>
              </div>
            </article>

            <article class="topic-panel topic-panel--muted">
              <div class="topic-panel__head">
                <strong>本轮延后主题</strong>
                <span>{{ runtime.scheduleDecision?.deferredTopics?.length || 0 }} 个</span>
              </div>
              <div
                v-if="!(runtime.scheduleDecision?.deferredTopics || []).length"
                class="empty-state empty-state--compact"
              >
                <span>这次运行里没有延后主题。</span>
              </div>
              <div
                v-for="topic in runtime.scheduleDecision?.deferredTopics || []"
                :key="`${topic.topicId || topic.topicName}-deferred`"
                class="topic-card topic-card--muted"
              >
                <strong>{{ topic.topicName || '未命名主题' }}</strong>
                <span>本轮未进入执行</span>
                <small>{{ (topic.reasonTags || []).join(' / ') || '未记录延后原因' }}</small>
              </div>
            </article>
          </div>
        </section>

        <section class="surface section-surface detail-section">
          <div class="surface-head surface-head--compact">
            <div class="surface-head__title">
              <strong>执行记录</strong>
              <span>这里先看结构化执行结果，明确区分计划分配和实际完成。</span>
            </div>
          </div>

          <div class="execution-summary">
            <article class="surface mini-surface">
              <span>选中主题</span>
              <strong>{{ runtime.executionRecord?.selectedTopicCount ?? 0 }}</strong>
              <small>本轮进入执行的主题数</small>
            </article>
            <article class="surface mini-surface">
              <span>延后主题</span>
              <strong>{{ runtime.executionRecord?.deferredTopicCount ?? 0 }}</strong>
              <small>本轮被顺延到后续的主题数</small>
            </article>
            <article class="surface mini-surface">
              <span>完成探针</span>
              <strong>{{ runtime.executionRecord?.completedProbeCount ?? 0 }}</strong>
              <small>真实完成的探针数</small>
            </article>
            <article class="surface mini-surface">
              <span>失败探针</span>
              <strong>{{ runtime.executionRecord?.failedProbeCount ?? 0 }}</strong>
              <small>执行过程中的失败数</small>
            </article>
          </div>

          <section class="probe-table-panel">
            <div class="topic-panel__head">
              <strong>探针执行明细</strong>
              <span>{{ runtime.executionRecord?.probeExecutions?.length || 0 }} 条</span>
            </div>
            <div
              v-if="!(runtime.executionRecord?.probeExecutions || []).length"
              class="empty-state empty-state--compact"
            >
              <span>这次运行还没有可读的探针级执行明细。</span>
            </div>
            <div
              v-else
              class="probe-table-shell"
            >
              <table class="probe-table">
                <thead>
                  <tr>
                    <th>探针序号</th>
                    <th>主题</th>
                    <th>实际探针内容</th>
                    <th>执行状态</th>
                    <th>模型</th>
                    <th>证据数</th>
                    <th>耗时</th>
                    <th>发生时间</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="probe in runtime.executionRecord?.probeExecutions || []"
                    :key="probe.probeId || `${probe.seqNo}-${probe.createdAt}`"
                  >
                    <td>
                      <strong>#{{ probe.seqNo ?? '--' }}</strong>
                    </td>
                    <td>
                      <div class="probe-topic-cell">
                        <strong>{{ probe.topicName || '未归类主题' }}</strong>
                        <span>{{ probe.promptId || probe.probeRef || '未记录探针引用' }}</span>
                      </div>
                    </td>
                    <td class="probe-text-cell">
                      {{ probe.renderedPrompt || '未记录实际探针内容' }}
                    </td>
                    <td>
                      <div class="probe-status-cell">
                        <i
                          class="status-pill"
                          :class="resolveProbeStatusTone(probe.status)"
                        >
                          {{ probe.statusLabel || '未记录' }}
                        </i>
                        <span v-if="probe.provisional">临时事件</span>
                      </div>
                    </td>
                    <td>{{ probe.modelId || '--' }}</td>
                    <td>{{ probe.evidenceCount ?? 0 }}</td>
                    <td>{{ formatLatency(probe.latencyMs) }}</td>
                    <td>{{ formatDateTime(probe.createdAt) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <div
            v-if="(runtime.executionRecord?.topicExecutions || []).length"
            class="topic-execution-grid"
          >
            <article
              v-for="topic in runtime.executionRecord?.topicExecutions || []"
              :key="`${topic.topicId || topic.topicName}-execution`"
              class="topic-execution-card"
            >
              <strong>{{ topic.topicName || '未命名主题' }}</strong>
              <span>计划投放 {{ topic.plannedProbeCount ?? '--' }} 个探针</span>
              <span>挂载问题模板 {{ topic.promptCount ?? 0 }} 条</span>
              <small>{{ (topic.reasonTags || []).join(' / ') || '未记录优先原因' }}</small>
              <ul v-if="(topic.promptSamples || []).length">
                <li
                  v-for="prompt in topic.promptSamples || []"
                  :key="prompt"
                >
                  {{ prompt }}
                </li>
              </ul>
            </article>
          </div>

          <div class="timeline-panel">
            <div class="topic-panel__head">
              <strong>关键阶段时间线</strong>
              <span>{{ runtime.executionRecord?.milestones?.length || 0 }} 个节点</span>
            </div>
            <div
              v-if="!(runtime.executionRecord?.milestones || []).length"
              class="empty-state empty-state--compact"
            >
              <span>这次运行没有可读的阶段事件。</span>
            </div>
            <div
              v-for="milestone in runtime.executionRecord?.milestones || []"
              :key="`${milestone.type}-${milestone.createdAt}`"
              class="timeline-row"
            >
              <strong>{{ milestone.label || '运行事件' }}</strong>
              <span>{{ formatDateTime(milestone.createdAt) }}</span>
              <small>{{ milestone.summary || '当前节点没有额外结构化摘要。' }}</small>
            </div>
          </div>
        </section>

        <section class="surface section-surface detail-section">
          <div class="surface-head surface-head--compact">
            <div class="surface-head__title">
              <strong>原始记录</strong>
              <span>这里是证据底座，不抢主阅读层。</span>
            </div>
          </div>

          <div class="raw-records">
            <details class="raw-block">
              <summary>原始运行计划</summary>
              <pre>{{ stringifyBlock(runtime.rawRecord?.taskOrder) }}</pre>
            </details>
            <details class="raw-block">
              <summary>原始调度快照</summary>
              <pre>{{ stringifyBlock(runtime.rawRecord?.scheduleDecision) }}</pre>
            </details>
            <details class="raw-block">
              <summary>原始报告摘要</summary>
              <pre>{{ stringifyBlock(runtime.rawRecord?.reportSummary) }}</pre>
            </details>
            <details class="raw-block">
              <summary>原始运行事件</summary>
              <pre>{{ stringifyBlock(runtime.rawRecord?.events) }}</pre>
            </details>
          </div>
        </section>
      </template>
    </section>
  </main>
</template>

<style scoped src="~/assets/css/engine-shell.css"></style>

<style scoped>
.run-detail-workbench {
  display: grid;
  gap: 14px;
}

.summary-grid,
.execution-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.detail-section {
  display: grid;
  gap: 12px;
}

.fact-grid,
.decision-grid,
.topic-columns,
.topic-execution-grid {
  display: grid;
  gap: 10px;
}

.fact-grid,
.decision-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.topic-columns,
.topic-execution-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.fact-card,
.decision-card,
.topic-card,
.topic-execution-card,
.timeline-row,
.raw-block,
.topic-panel {
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  background: #ffffff;
}

.fact-card,
.decision-card,
.topic-card,
.topic-execution-card {
  display: grid;
  gap: 6px;
  padding: 12px;
}

.fact-card span,
.decision-card span,
.topic-panel__head span,
.timeline-row span {
  font-size: 12px;
  color: #6b7280;
}

.topic-panel__summary {
  margin: 0;
  padding: 0 12px;
  font-size: 13px;
  line-height: 1.6;
  color: #475569;
}

.fact-card strong,
.decision-card strong,
.topic-card strong,
.topic-execution-card strong,
.timeline-row strong {
  color: #111827;
}

.fact-card strong {
  font-size: 15px;
  line-height: 1.4;
  word-break: break-word;
}

.decision-card strong {
  font-size: 16px;
  line-height: 1.45;
}

.decision-card--highlight {
  grid-column: span 2;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.topic-panel {
  padding: 12px;
  display: grid;
  gap: 10px;
}

.topic-panel--muted {
  background: #fcfcfd;
}

.probe-table-panel {
  display: grid;
  gap: 10px;
}

.topic-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.topic-card {
  background: #ffffff;
}

.topic-card span,
.topic-execution-card span {
  font-size: 13px;
  color: #374151;
}

.topic-card small,
.topic-execution-card small,
.timeline-row small {
  color: #6b7280;
  line-height: 1.6;
}

.topic-card--muted {
  background: #f8fafc;
}

.topic-execution-card {
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
}

.probe-table-shell {
  overflow: auto;
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94) 0%, rgba(248, 250, 252, 0.96) 100%);
}

.probe-table {
  width: 100%;
  min-width: 980px;
  border-collapse: collapse;
}

.probe-table th,
.probe-table td {
  padding: 12px 14px;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
  vertical-align: top;
}

.probe-table th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: rgba(248, 250, 252, 0.94);
  color: #475569;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.probe-table td {
  color: #111827;
  font-size: 13px;
  line-height: 1.65;
}

.probe-table tbody tr:hover {
  background: rgba(248, 250, 252, 0.9);
}

.probe-topic-cell,
.probe-status-cell {
  display: grid;
  gap: 4px;
}

.probe-topic-cell span,
.probe-status-cell span {
  font-size: 12px;
  color: #6b7280;
}

.probe-text-cell {
  min-width: 320px;
}

.topic-execution-card ul {
  margin: 4px 0 0;
  padding-left: 18px;
  display: grid;
  gap: 4px;
  color: #374151;
  font-size: 12px;
  line-height: 1.6;
}

.timeline-panel {
  display: grid;
  gap: 10px;
}

.timeline-row {
  padding: 12px 14px;
  display: grid;
  gap: 4px;
  border-left: 3px solid #d1d5db;
}

.raw-records {
  display: grid;
  gap: 10px;
}

.raw-block {
  overflow: hidden;
}

.raw-block summary {
  cursor: pointer;
  list-style: none;
  padding: 12px 14px;
  font-size: 13px;
  font-weight: 700;
  color: #111827;
  background: #f8fafc;
}

.raw-block summary::-webkit-details-marker {
  display: none;
}

.raw-block[open] summary {
  border-bottom: 1px solid #e5e7eb;
}

.raw-block pre {
  margin: 0;
  padding: 14px;
  background: #0f172a;
  color: #e2e8f0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.6;
}

.status-pill {
  min-height: 22px;
  padding: 0 10px;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-style: normal;
  font-size: 11px;
  font-weight: 600;
}

.status-pill--success {
  background: #ecfdf5;
  border-color: #a7f3d0;
  color: #047857;
}

.status-pill--warning {
  background: #fffbeb;
  border-color: #fde68a;
  color: #b45309;
}

.status-pill--danger {
  background: #fef2f2;
  border-color: #fecaca;
  color: #b91c1c;
}

.status-pill--default {
  background: #f8fafc;
  color: #475569;
}

@media (max-width: 1360px) {
  .summary-grid,
  .execution-summary,
  .fact-grid,
  .decision-grid,
  .topic-columns,
  .topic-execution-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .summary-grid,
  .execution-summary,
  .fact-grid,
  .decision-grid,
  .topic-columns,
  .topic-execution-grid {
    grid-template-columns: 1fr;
  }

  .decision-card--highlight {
    grid-column: span 1;
  }
}
</style>
