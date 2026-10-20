<script setup lang="ts">
import { computed } from 'vue'
import type { WorkbenchBrandPerformanceTopicSummary } from '~/types/workbench'
import { formatDateTime } from '~/utils/dateTime'

interface TopicMetricRow {
  id: string
  name: string
  seqNo: number | null
  enabled: boolean
  sampleSize: number
  visibilityScore: number | null
  mentionRate: number | null
  recommendRate: number | null
  top3Rate: number | null
  averagePosition: number | null
  mentionCount: number | null
  promptCount: number | null
  enabledPromptCount: number | null
  signalCount: number | null
  sampleQuestion: string
}

interface TopicMetricProvenanceContext {
  sampleSize?: number | null
  mentionCount?: number | null
  mentionRate?: number | null
  recommendRate?: number | null
  top3Rate?: number | null
}

type MetricTone = 'good' | 'mid' | 'bad' | 'muted'

const props = defineProps<{
  hasCoverage: boolean
  selectedBrand: string
  lastGeneratedAt?: string | null
  topicSummaryRows: WorkbenchBrandPerformanceTopicSummary[]
}>()

const emit = defineEmits<{
  (event: 'openProvenance', anchorKey: string, metricKey: string, topicId?: string | null, metricValue?: string | null, metricLabel?: string | null, context?: TopicMetricProvenanceContext): void
}>()

function normalizeNumber(value?: number | null) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function normalizeCount(value?: number | null) {
  const normalized = normalizeNumber(value)
  return normalized == null ? null : Math.max(0, Math.round(normalized))
}

function formatPercent(value?: number | null) {
  const normalized = normalizeNumber(value)
  return normalized == null ? '--' : `${normalized.toFixed(1)}%`
}

function formatScore(value?: number | null) {
  const normalized = normalizeNumber(value)
  return normalized == null ? '--' : normalized.toFixed(1)
}

function formatPosition(value?: number | null) {
  const normalized = normalizeNumber(value)
  return normalized == null ? '--' : normalized.toFixed(2)
}

function firstQuestion(row: WorkbenchBrandPerformanceTopicSummary) {
  return row.sampleQuestions?.find(Boolean) || '当前主题还没有可展示的代表问题。'
}

function metricTone(value: number | null, kind: 'visibility' | 'rate' | 'position'): MetricTone {
  if (value == null) {
    return 'muted'
  }
  if (kind === 'position') {
    if (value <= 1.5) {
      return 'good'
    }
    if (value <= 2.5) {
      return 'mid'
    }
    return 'bad'
  }
  if (value >= 55) {
    return 'good'
  }
  if (value >= 25) {
    return 'mid'
  }
  return 'bad'
}

function resolveCoverageMeta(row: TopicMetricRow) {
  const parts: string[] = []
  if (row.enabledPromptCount != null) {
    parts.push(`${row.enabledPromptCount} 个启用问题`)
  }
  if (row.promptCount != null) {
    parts.push(`${row.promptCount} 个主题问题`)
  }
  if (row.signalCount != null && row.signalCount > 0) {
    parts.push(`${row.signalCount} 条问题信号`)
  }
  return parts.join(' / ')
}

function resolveRowDiagnosis(row: TopicMetricRow) {
  if (row.sampleSize <= 0) {
    return '当前窗口还没有形成可计算样本。'
  }
  if ((row.mentionRate ?? 0) < 15) {
    return '这个主题里品牌仍很难被提及，先解决“想不起来你”。'
  }
  if ((row.mentionRate ?? 0) >= 35 && (row.recommendRate ?? 0) < 15) {
    return '用户已经会提到你，但还没进入稳定推荐位。'
  }
  if ((row.recommendRate ?? 0) >= 20 && (row.top3Rate ?? 0) < 10) {
    return '已经开始被推荐，但前三位还不稳。'
  }
  if ((row.visibilityScore ?? 0) >= 50) {
    return '这个主题已经进入相对稳定的可见区。'
  }
  return '这个主题已有样本，但可见度还没有拉开。'
}

function resolvePriorityLabel(row: TopicMetricRow) {
  if (row.sampleSize <= 0) {
    return '待补样本'
  }
  if ((row.mentionRate ?? 0) < 15) {
    return '先抢提及'
  }
  if ((row.mentionRate ?? 0) >= 35 && (row.recommendRate ?? 0) < 15) {
    return '先提推荐'
  }
  if ((row.recommendRate ?? 0) >= 20 && (row.top3Rate ?? 0) < 10) {
    return '先冲前三'
  }
  return '持续放大'
}

function provenanceContext(row: TopicMetricRow): TopicMetricProvenanceContext {
  return {
    sampleSize: row.sampleSize,
    mentionCount: row.mentionCount,
    mentionRate: row.mentionRate,
    recommendRate: row.recommendRate,
    top3Rate: row.top3Rate
  }
}

const metricRows = computed<TopicMetricRow[]>(() => {
  return (props.topicSummaryRows || [])
    .map((row, index) => ({
      id: row.topicId || `${row.topicName}-${index + 1}`,
      name: row.topicName || `主题 ${index + 1}`,
      seqNo: row.seqNo ?? null,
      enabled: row.enabled !== false,
      sampleSize: normalizeCount(row.sampleSize) ?? 0,
      visibilityScore: normalizeNumber(row.visibilityScore),
      mentionRate: normalizeNumber(row.mentionRate),
      recommendRate: normalizeNumber(row.recommendRate),
      top3Rate: normalizeNumber(row.top3Rate),
      averagePosition: normalizeNumber(row.averagePosition),
      mentionCount: normalizeCount(row.mentionCount),
      promptCount: normalizeCount(row.promptCount),
      enabledPromptCount: normalizeCount(row.enabledPromptCount),
      signalCount: normalizeCount(row.signalCount),
      sampleQuestion: firstQuestion(row)
    }))
    .sort((left, right) => {
      if (right.sampleSize !== left.sampleSize) {
        return right.sampleSize - left.sampleSize
      }
      const leftVisibility = left.visibilityScore ?? -1
      const rightVisibility = right.visibilityScore ?? -1
      if (rightVisibility !== leftVisibility) {
        return rightVisibility - leftVisibility
      }
      if ((left.seqNo ?? 9999) !== (right.seqNo ?? 9999)) {
        return (left.seqNo ?? 9999) - (right.seqNo ?? 9999)
      }
      return left.name.localeCompare(right.name, 'zh-Hans-CN')
    })
})

const realMetricRows = computed(() =>
  metricRows.value.filter(row =>
    row.sampleSize > 0
    && row.visibilityScore != null
    && row.mentionRate != null
    && row.recommendRate != null
    && row.top3Rate != null
  )
)

const pendingRows = computed(() =>
  metricRows.value.filter(row => !realMetricRows.value.some(candidate => candidate.id === row.id))
)

const totalSampleSize = computed(() =>
  realMetricRows.value.reduce((sum, row) => sum + row.sampleSize, 0)
)

const averageVisibilityScore = computed(() => {
  if (!realMetricRows.value.length) {
    return null
  }
  const total = realMetricRows.value.reduce((sum, row) => sum + (row.visibilityScore || 0), 0)
  return total / realMetricRows.value.length
})

const lowestVisibilityRow = computed(() => {
  return realMetricRows.value
    .slice()
    .sort((left, right) => (left.visibilityScore ?? 999) - (right.visibilityScore ?? 999))[0] || null
})

const highestSampleRow = computed(() => {
  return realMetricRows.value
    .slice()
    .sort((left, right) => right.sampleSize - left.sampleSize)[0] || null
})

const latestTimestampLabel = computed(() => {
  return props.lastGeneratedAt ? formatDateTime(props.lastGeneratedAt) : '--'
})

const overviewStats = computed(() => [
  {
    label: '可计算主题',
    value: String(realMetricRows.value.length),
    note: pendingRows.value.length ? `另有 ${pendingRows.value.length} 个主题还没出结果` : '当前主题都已出结果'
  },
  {
    label: '主题总样本',
    value: String(totalSampleSize.value),
    note: highestSampleRow.value ? `最高样本主题 ${highestSampleRow.value.name} · ${highestSampleRow.value.sampleSize}` : '还没有样本'
  },
  {
    label: '平均可见度',
    value: formatScore(averageVisibilityScore.value),
    note: averageVisibilityScore.value == null ? '暂无结果' : '按已出结果主题计算'
  },
  {
    label: '当前最弱主题',
    value: lowestVisibilityRow.value?.name || '--',
    note: lowestVisibilityRow.value ? formatScore(lowestVisibilityRow.value.visibilityScore) : '暂无可比较主题'
  }
])

</script>

<template>
  <section class="surface section-surface topics-surface">
    <div class="surface-head">
      <div class="surface-head__title">
        <div class="title-with-help">
          <strong>
            <WikiGeoWikiTrigger entrypoint-id="engine.dimension.topic">
              主题
            </WikiGeoWikiTrigger>
          </strong>
        </div>
        <div class="surface-head__context">
          <span class="surface-head__object">{{ props.selectedBrand }}</span>
          <span class="topics-timestamp">更新时间 {{ latestTimestampLabel }}</span>
        </div>
      </div>
    </div>

    <div
      v-if="!props.hasCoverage"
      class="state-block"
    >
      当前品牌暂无可用的品牌表现结果。
    </div>

    <template v-else>
      <section class="topics-hero">
        <div class="topics-overview">
          <article
            v-for="item in overviewStats"
            :key="item.label"
            class="topics-overview__card"
          >
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <small>{{ item.note }}</small>
          </article>
        </div>
      </section>

      <section class="topics-layout">
        <div class="topics-matrix">
        <div class="section-head">
          <div class="title-with-help">
            <strong>主题 × 4 指标</strong>
          </div>
        </div>

          <div
            v-if="!metricRows.length"
            class="state-block state-block--panel"
          >
            当前没有可展示的主题结果。
          </div>

          <div
            v-else-if="!realMetricRows.length"
            class="state-block state-block--panel"
          >
            当前窗口已返回主题目录，但主题结果还在补齐中，请稍后再看这一页。
          </div>

          <div
            v-else
            class="matrix-table"
          >
            <div class="matrix-table__head">
              <span class="matrix-col-topic">主题</span>
              <span class="matrix-col-metric">AI 可见度</span>
              <span class="matrix-col-metric">AI 提及率</span>
              <span class="matrix-col-metric">AI 推荐率</span>
              <span class="matrix-col-metric">Top3 率</span>
              <span class="matrix-col-judgment">样本与判断</span>
            </div>

            <article
              v-for="row in realMetricRows"
              :key="row.id"
              class="matrix-row"
            >
              <div class="matrix-topic">
                <div class="matrix-topic__head">
                  <strong>{{ row.name }}</strong>
                  <span class="status-chip">{{ resolvePriorityLabel(row) }}</span>
                </div>
                <p>{{ resolveCoverageMeta(row) || '当前主题未返回额外覆盖信息。' }}</p>
              </div>

              <div
                class="metric-cell"
                :class="`metric-cell--${metricTone(row.visibilityScore, 'visibility')}`"
              >
                <span class="metric-cell__value provenance-value">
                  {{ formatScore(row.visibilityScore) }}
                </span>
                <small>按 3 项权重计算</small>
              </div>

              <div
                class="metric-cell"
                :class="`metric-cell--${metricTone(row.mentionRate, 'rate')}`"
              >
                <span class="metric-cell__value provenance-value">
                  {{ formatPercent(row.mentionRate) }}
                </span>
                <small>先被想起的概率</small>
              </div>

              <div
                class="metric-cell"
                :class="`metric-cell--${metricTone(row.recommendRate, 'rate')}`"
              >
                <span class="metric-cell__value provenance-value">
                  {{ formatPercent(row.recommendRate) }}
                </span>
                <small>进入推荐位的概率</small>
              </div>

              <div
                class="metric-cell"
                :class="`metric-cell--${metricTone(row.top3Rate, 'rate')}`"
              >
                <span class="metric-cell__value provenance-value">
                  {{ formatPercent(row.top3Rate) }}
                </span>
                <small>进入前三的概率</small>
              </div>

              <div class="judgment-cell">
                <div class="judgment-cell__stats">
                  <span>样本量 {{ row.sampleSize }}</span>
                  <span>
                    <WikiGeoWikiTrigger entrypoint-id="engine.metric.average_position">
                      平均位次
                    </WikiGeoWikiTrigger>
                    {{ formatPosition(row.averagePosition) }}
                  </span>
                  <span>提及次数 {{ row.mentionCount ?? '--' }}</span>
                </div>
                <strong>{{ resolveRowDiagnosis(row) }}</strong>
                <small>{{ row.sampleQuestion }}</small>
              </div>
            </article>
          </div>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.topics-surface {
  border: 1px solid #d1d5db;
  border-radius: 2px;
}

.topics-timestamp {
  color: #6b7280;
  font-size: 12px;
}

.topics-hero {
  margin-top: 12px;
  border: 1px solid #d1d5db;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  padding: 16px;
  display: grid;
  gap: 16px;
}

.topics-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.topics-overview__card {
  border: 1px solid #d1d5db;
  background: #ffffff;
  padding: 12px;
  display: grid;
  gap: 6px;
}

.topics-overview__card span,
.topics-overview__card small {
  font-size: 12px;
  color: #6b7280;
}

.topics-overview__card strong {
  font-size: 22px;
  color: #111827;
  line-height: 1.15;
}

.topics-layout {
  margin-top: 18px;
  display: block;
}

.topics-matrix {
  border: 1px solid #d1d5db;
  background: #ffffff;
  padding: 14px;
}

.section-head {
  margin-bottom: 12px;
}

.matrix-table {
  display: grid;
  gap: 8px;
}

.matrix-table__head,
.matrix-row {
  display: grid;
  grid-template-columns: minmax(220px, 1.45fr) repeat(4, minmax(96px, 0.62fr)) minmax(260px, 1.2fr);
  gap: 8px;
}

.matrix-table__head {
  align-items: center;
  font-size: 12px;
  color: #6b7280;
}

.matrix-row {
  align-items: stretch;
}

.matrix-topic,
.metric-cell,
.judgment-cell {
  min-height: 96px;
  border: 1px solid #d1d5db;
  background: #fff;
}

.matrix-topic,
.judgment-cell {
  padding: 12px;
}

.matrix-topic {
  display: grid;
  gap: 10px;
}

.matrix-topic__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.matrix-topic p {
  margin: 0;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.6;
}

.metric-cell {
  padding: 12px 10px;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 6px;
}

.metric-cell strong,
.metric-cell__value {
  font-size: 28px;
  line-height: 1;
  color: #111827;
}

.provenance-value {
  font: inherit;
  font-weight: 700;
  padding: 0 2px;
}

.metric-cell small {
  font-size: 12px;
  color: #6b7280;
  text-align: center;
}

.metric-cell--good {
  background: #f0fdf4;
}

.metric-cell--mid {
  background: #fffbeb;
}

.metric-cell--bad {
  background: #fef2f2;
}

.metric-cell--muted {
  background: #f8fafc;
}

.judgment-cell {
  display: grid;
  gap: 8px;
}

.judgment-cell__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.judgment-cell__stats span {
  border: 1px solid #d1d5db;
  padding: 2px 8px;
  font-size: 12px;
  color: #4b5563;
  background: #f8fafc;
}

.judgment-cell strong {
  color: #111827;
}

.judgment-cell small {
  color: #6b7280;
  font-size: 12px;
  line-height: 1.6;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
  padding: 2px 8px;
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap;
}

@media (max-width: 1100px) {
  .topics-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .matrix-table__head {
    display: none;
  }

  .matrix-row {
    grid-template-columns: 1fr;
  }

  .metric-cell {
    justify-items: start;
  }

  .metric-cell small {
    text-align: left;
  }
}

@media (max-width: 720px) {
  .topics-overview {
    grid-template-columns: 1fr;
  }

  .matrix-topic__head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
