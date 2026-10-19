<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import type { WorkbenchBrandPerformanceCompetitorRow } from '~/types/workbench'

interface WorkbenchSentimentRow {
  rank: number
  id: string
  brandName: string
  isTarget: boolean
  positiveRate: number
  mentionRate: number | null
}

interface ChartTooltipParam {
  name?: string
  axisValue?: string
  value?: unknown
}

const props = defineProps<{
  selectedBrand: string
  workbenchCompetitorRows: WorkbenchBrandPerformanceCompetitorRow[]
}>()

const emit = defineEmits<{
  (event: 'openProvenance', anchorKey: string, metricKey: string, metricValue?: string | null, metricLabel?: string | null): void
}>()

function normalizeNullableNumber(value?: number | null) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function formatWorkbenchPercent(value?: number | null) {
  const normalized = normalizeNullableNumber(value)
  return normalized == null ? '--' : `${normalized.toFixed(1)}%`
}

function formatWorkbenchPointGap(value?: number | null) {
  const normalized = normalizeNullableNumber(value)
  return normalized == null ? '--' : `${Math.abs(normalized).toFixed(1)} 个点`
}

function resolveTooltipParam(params: unknown): ChartTooltipParam | null {
  const target = Array.isArray(params) ? params[0] : params
  if (!target || typeof target !== 'object') {
    return null
  }

  const record = target as Record<string, unknown>

  return {
    name: typeof record.name === 'string' ? record.name : undefined,
    axisValue: typeof record.axisValue === 'string' ? record.axisValue : undefined,
    value: record.value
  }
}

function formatTooltipMetric(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value.toFixed(1)
  }
  if (typeof value === 'string') {
    const numeric = Number(value)
    if (Number.isFinite(numeric)) {
      return numeric.toFixed(1)
    }
  }
  return '--'
}

function buildWorkbenchSentimentSplitOption(positiveRate?: number | null) {
  const positive = normalizeNullableNumber(positiveRate)
  if (positive == null) {
    return null
  }

  const nonPositive = Math.max(0, 100 - positive)

  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: '#111827',
      borderColor: '#1f2937',
      textStyle: { color: '#f9fafb' },
      formatter: '{b}：{c}%'
    },
    series: [
      {
        type: 'pie',
        radius: ['56%', '76%'],
        center: ['50%', '50%'],
        label: {
          color: '#4b5563',
          fontSize: 11,
          formatter: '{b}\n{c}%'
        },
        labelLine: {
          length: 10,
          length2: 10,
          lineStyle: { color: '#cbd5e1' }
        },
        data: [
          {
            name: '正向',
            value: Number(positive.toFixed(1)),
            itemStyle: { color: '#35b45a' }
          },
          {
            name: '非正向',
            value: Number(nonPositive.toFixed(1)),
            itemStyle: { color: '#9ca3af' }
          }
        ]
      }
    ]
  } satisfies EChartsOption
}

function buildWorkbenchSentimentRankingOption(rows: WorkbenchSentimentRow[]) {
  if (!rows.length) {
    return null
  }

  const chartRows = rows
    .slice(0, 8)
    .slice()
    .reverse()

  return {
    grid: {
      left: 92,
      right: 20,
      top: 14,
      bottom: 18,
      containLabel: false
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: '#111827',
      borderColor: '#1f2937',
      textStyle: { color: '#f9fafb' },
      formatter: (params: unknown) => {
        const target = resolveTooltipParam(params)
        if (!target) {
          return ''
        }
        return `${target.name || ''}<br/>正向率：${formatTooltipMetric(target.value)}%`
      }
    },
    xAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: {
        color: '#6b7280',
        fontSize: 11,
        formatter: '{value}%'
      },
      splitLine: {
        lineStyle: { color: '#f3f4f6' }
      }
    },
    yAxis: {
      type: 'category',
      data: chartRows.map(row => row.brandName),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: {
        color: '#374151',
        fontSize: 11
      }
    },
    series: [
      {
        type: 'bar',
        barWidth: 20,
        data: chartRows.map(row => ({
          value: row.positiveRate,
          itemStyle: {
            color: row.isTarget ? '#111827' : '#9ca3af',
            borderRadius: [0, 2, 2, 0]
          }
        })),
        label: {
          show: true,
          position: 'right',
          color: '#374151',
          fontSize: 11,
          formatter: '{c}%'
        }
      }
    ]
  } satisfies EChartsOption
}

const workbenchSentimentRows = computed<WorkbenchSentimentRow[]>(() => {
  return (props.workbenchCompetitorRows || [])
    .map(row => ({
      id: row.id,
      brandName: row.brandName,
      isTarget: Boolean(row.isTarget),
      positiveRate: normalizeNullableNumber(row.positiveRate),
      mentionRate: normalizeNullableNumber(row.mentionRate)
    }))
    .filter((row): row is Omit<WorkbenchSentimentRow, 'rank'> => Boolean(row.brandName) && row.positiveRate != null)
    .sort((left, right) => {
      if (right.positiveRate !== left.positiveRate) {
        return right.positiveRate - left.positiveRate
      }
      return (right.mentionRate ?? -1) - (left.mentionRate ?? -1)
    })
    .map((row, index) => ({
      rank: index + 1,
      ...row
    }))
})

const workbenchTargetSentimentRow = computed(() => {
  return workbenchSentimentRows.value.find(row => row.isTarget)
    || workbenchSentimentRows.value.find(row => row.brandName === props.selectedBrand)
    || null
})

const hasWorkbenchSentimentRows = computed(() => Boolean(workbenchTargetSentimentRow.value && workbenchSentimentRows.value.length))

const workbenchTargetPositiveRate = computed(() => workbenchTargetSentimentRow.value?.positiveRate ?? null)
const workbenchTargetNonPositiveRate = computed(() => {
  const positive = workbenchTargetPositiveRate.value
  return positive == null ? null : Math.max(0, 100 - positive)
})
const workbenchTargetPositiveRank = computed(() => workbenchTargetSentimentRow.value?.rank ?? null)
const workbenchCompetitorCount = computed(() => workbenchSentimentRows.value.length)

const workbenchTopPeerRow = computed(() => {
  return workbenchSentimentRows.value.find(row => !row.isTarget) || null
})

const workbenchPeerAveragePositiveRate = computed(() => {
  const peers = workbenchSentimentRows.value.filter(row => !row.isTarget)
  if (!peers.length) {
    return null
  }
  return peers.reduce((sum, row) => sum + row.positiveRate, 0) / peers.length
})

const workbenchPositiveGapToTopPeer = computed(() => {
  const current = workbenchTargetPositiveRate.value
  const leader = workbenchTopPeerRow.value?.positiveRate ?? null
  if (current == null || leader == null) {
    return null
  }
  return current - leader
})

const workbenchSentimentSplitOption = computed(() => {
  return buildWorkbenchSentimentSplitOption(workbenchTargetPositiveRate.value)
})

const workbenchSentimentRankingOption = computed(() => {
  return buildWorkbenchSentimentRankingOption(workbenchSentimentRows.value)
})

const workbenchSentimentLead = computed(() => {
  const target = workbenchTargetSentimentRow.value
  if (!target) {
    return `${props.selectedBrand} 当前暂无正向率数据。`
  }

  const total = workbenchCompetitorCount.value
  if (target.rank === 1) {
    const nextPeer = workbenchSentimentRows.value.find(row => !row.isTarget)
    if (!nextPeer) {
      return `${props.selectedBrand} 当前正向率 ${formatWorkbenchPercent(target.positiveRate)}，暂时没有可比较的竞品样本。`
    }
    return `${props.selectedBrand} 当前以 ${formatWorkbenchPercent(target.positiveRate)} 位列竞品池第 1，领先 ${nextPeer.brandName} ${formatWorkbenchPointGap(target.positiveRate - nextPeer.positiveRate)}。`
  }

  return `${props.selectedBrand} 当前正向率 ${formatWorkbenchPercent(target.positiveRate)}，在 ${total} 个品牌里排第 ${target.rank}。`
})

const workbenchSentimentBenchmarkCopy = computed(() => {
  const peerAverage = workbenchPeerAveragePositiveRate.value
  const leader = workbenchTopPeerRow.value
  const gap = workbenchPositiveGapToTopPeer.value

  if (peerAverage == null && !leader) {
    return '当前竞品池还不足以形成稳定基准。'
  }

  if (!leader || gap == null) {
    return `竞品池当前正向率均值约为 ${formatWorkbenchPercent(peerAverage)}。`
  }

  if (gap >= 0) {
    return `竞品池均值约为 ${formatWorkbenchPercent(peerAverage)}，当前领先最高竞品 ${leader.brandName} ${formatWorkbenchPointGap(gap)}。`
  }

  return `竞品池均值约为 ${formatWorkbenchPercent(peerAverage)}，距离 ${leader.brandName} 还差 ${formatWorkbenchPointGap(gap)}。`
})
</script>

<template>
  <section class="sentiment-surface">
    <header class="sentiment-head">
      <div class="sentiment-title">
        <span class="title-with-help">
          <strong>
            <WikiGeoWikiTrigger entrypoint-id="engine.dimension.sentiment">
              情绪总览
            </WikiGeoWikiTrigger>
          </strong>
        </span>
        <p>基于品牌在主要竞品回答中的正向表现，查看 {{ selectedBrand }} 当前的整体情绪倾向。</p>
      </div>
    </header>

    <div
      v-if="!hasWorkbenchSentimentRows"
      class="state-block"
    >
      当前品牌还没有可用的情绪对比数据
    </div>
    <div
      v-else
      class="sentiment-grid workbench-sentiment-grid"
    >
      <article class="sentiment-chart-panel">
        <div class="sentiment-chart-head">
          正向率
        </div>
        <div class="sentiment-kpi">
          {{ formatWorkbenchPercent(workbenchTargetPositiveRate) }}
        </div>
        <p class="sentiment-truth-note">
          这里用正向率和非正向占比判断当前整体口碑方向，先看回答倾向是偏正向还是偏弱。
        </p>
        <GeoLazyEChart
          :option="workbenchSentimentSplitOption"
          :height="286"
          empty-text="暂无情绪图表数据"
        />
      </article>

      <aside class="sentiment-side-panel">
        <article class="sentiment-brief sentiment-brief--positive">
          <span>#{{ workbenchTargetPositiveRank || '--' }} / {{ workbenchCompetitorCount || '--' }} 正向率排名</span>
          <p>{{ workbenchSentimentLead }}</p>
        </article>

        <article class="sentiment-brief sentiment-brief--neutral">
          <span>{{ formatWorkbenchPercent(workbenchPeerAveragePositiveRate) }} 竞品均值</span>
          <p>{{ workbenchSentimentBenchmarkCopy }}</p>
        </article>

        <div class="sentiment-ratio">
          <div class="sentiment-ratio__bar">
            <span
              class="sentiment-ratio__non-positive sentiment-ratio__segment"
              :style="{ width: `${Math.max(0, Math.min(100, workbenchTargetNonPositiveRate || 0))}%` }"
            >
              <span class="sentiment-ratio__tip">
                <i class="sentiment-ratio__dot sentiment-ratio__dot--non-positive" />
                非正向 {{ formatWorkbenchPercent(workbenchTargetNonPositiveRate) }}
              </span>
            </span>
            <span
              class="sentiment-ratio__positive sentiment-ratio__segment"
              :style="{ width: `${Math.max(0, Math.min(100, workbenchTargetPositiveRate || 0))}%` }"
            >
              <span class="sentiment-ratio__tip">
                <i class="sentiment-ratio__dot sentiment-ratio__dot--positive" />
                正向 {{ formatWorkbenchPercent(workbenchTargetPositiveRate) }}
              </span>
            </span>
          </div>
          <div class="sentiment-ratio__meta">
            <span>非正向 {{ formatWorkbenchPercent(workbenchTargetNonPositiveRate) }}</span>
            <span>正向 {{ formatWorkbenchPercent(workbenchTargetPositiveRate) }}</span>
          </div>
        </div>
      </aside>
    </div>
  </section>

  <section class="sentiment-surface">
    <header class="sentiment-head">
      <div class="sentiment-title">
        <span class="title-with-help">
          <strong>竞品情绪排名</strong>
        </span>
        <p>按竞品池正向率对比 {{ selectedBrand }} 与主要对手的回答情绪表现。</p>
      </div>
    </header>

    <div
      v-if="!hasWorkbenchSentimentRows"
      class="state-block"
    >
      当前品牌还没有可用的情绪对比数据
    </div>
    <div
      v-else
      class="sentiment-ranking-grid"
    >
      <article class="sentiment-chart-panel sentiment-chart-panel--ranking">
        <div class="sentiment-chart-head">
          竞品池正向率
        </div>
        <GeoLazyEChart
          :option="workbenchSentimentRankingOption"
          :height="320"
          empty-text="暂无竞品情绪图表数据"
        />
      </article>

      <aside class="sentiment-ranking-panel">
        <div class="sentiment-ranking-table">
          <div class="sentiment-ranking-table__head">
            <span />
            <span>品牌</span>
            <span>正向率</span>
            <span>提及率</span>
          </div>

          <div
            v-for="row in workbenchSentimentRows"
            :key="`sentiment-rank-${row.id}`"
            class="sentiment-ranking-table__row"
            :class="{ 'sentiment-ranking-table__row--target': row.isTarget }"
          >
            <span>{{ row.rank }}.</span>
            <span class="sentiment-ranking-table__brand">
              {{ row.brandName }}
              <span
                v-if="row.isTarget"
                class="sentiment-ranking-table__badge"
              >
                当前品牌
              </span>
            </span>
            <span class="provenance-value">
              {{ formatWorkbenchPercent(row.positiveRate) }}
            </span>
            <span class="provenance-value">
              {{ formatWorkbenchPercent(row.mentionRate) }}
            </span>
          </div>
        </div>
      </aside>
    </div>
  </section>

  <section class="sentiment-surface">
    <header class="sentiment-head">
      <div class="sentiment-title">
        <span class="title-with-help">
          <strong>细分情绪</strong>
        </span>
        <p>这里继续补充当前情绪结构，和整体正向率一起判断口碑压力分布。</p>
      </div>
    </header>

    <div class="truth-gap-grid">
      <article class="truth-gap-card truth-gap-card--wide">
        <strong>当前以整体口碑方向为主</strong>
        <ul class="truth-gap-card__list">
          <li>正向率用于判断当前品牌回答整体偏向。</li>
          <li>非正向占比用于识别当前口碑压力是否集中。</li>
          <li>竞品正向率排名用于判断相对位置和追赶距离。</li>
        </ul>
      </article>
    </div>
  </section>
</template>

<style scoped>
.title-with-help {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.sentiment-surface {
  display: grid;
  gap: 10px;
}

.sentiment-head {
  min-height: 32px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.sentiment-title strong {
  display: block;
  font-size: 16px;
  line-height: 1.3;
  color: #111827;
}

.sentiment-title p {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.55;
  color: #6b7280;
}

.state-block {
  min-height: 120px;
  border: 1px dashed #9ca3af;
  border-radius: 2px;
  display: grid;
  place-items: center;
  color: #4b5563;
  font-size: 12px;
  background: #ffffff;
}

.sentiment-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: 10px;
}

.workbench-sentiment-grid {
  align-items: stretch;
}

.sentiment-chart-panel,
.sentiment-side-panel,
.sentiment-ranking-panel,
.truth-gap-card {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #ffffff;
  padding: 10px;
}

.sentiment-chart-head {
  color: #374151;
  font-size: 12px;
  font-weight: 600;
}

.sentiment-kpi {
  margin: 8px 0 6px;
  font-size: 28px;
  line-height: 1;
  font-weight: 700;
  color: #111827;
}

.sentiment-truth-note {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.55;
  color: #6b7280;
}

.sentiment-side-panel {
  display: grid;
  gap: 10px;
  align-content: start;
}

.sentiment-brief {
  border-radius: 2px;
  padding: 10px;
  display: grid;
  gap: 6px;
}

.sentiment-brief span {
  font-size: 12px;
  font-weight: 700;
}

.sentiment-brief p {
  margin: 0;
  font-size: 12px;
  line-height: 1.55;
}

.sentiment-brief--positive {
  background: #f0fdf4;
  color: #166534;
}

.sentiment-brief--neutral {
  background: #f8fafc;
  color: #334155;
}

.sentiment-ratio {
  display: grid;
  gap: 8px;
}

.sentiment-ratio__bar {
  display: flex;
  min-height: 24px;
  overflow: hidden;
  border-radius: 999px;
  background: #e5e7eb;
}

.sentiment-ratio__segment {
  position: relative;
  min-width: 0;
}

.sentiment-ratio__non-positive {
  background: #9ca3af;
}

.sentiment-ratio__positive {
  background: #35b45a;
}

.sentiment-ratio__tip {
  position: absolute;
  left: 8px;
  top: -34px;
  display: none;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 999px;
  background: #111827;
  color: #f9fafb;
  font-size: 11px;
  white-space: nowrap;
}

.sentiment-ratio__segment:hover .sentiment-ratio__tip {
  display: inline-flex;
}

.sentiment-ratio__dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
}

.sentiment-ratio__dot--non-positive {
  background: #9ca3af;
}

.sentiment-ratio__dot--positive {
  background: #35b45a;
}

.sentiment-ratio__meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #6b7280;
  font-size: 12px;
}

.sentiment-ranking-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
  gap: 10px;
}

.sentiment-chart-panel--ranking {
  min-width: 0;
}

.provenance-value {
  width: fit-content;
  color: #111827;
  font: inherit;
  font-weight: 700;
  line-height: 1.05;
  padding: 0 2px;
  text-align: left;
}

.sentiment-ranking-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
}

.sentiment-ranking-table__head,
.sentiment-ranking-table__row {
  display: grid;
  grid-template-columns: 40px minmax(0, 1.2fr) 88px 88px;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 10px;
  font-size: 12px;
}

.sentiment-ranking-table__head {
  background: #f8fafc;
  border-bottom: 1px solid #d1d5db;
  color: #6b7280;
  font-weight: 600;
}

.sentiment-ranking-table__row {
  border-bottom: 1px solid #e5e7eb;
  color: #111827;
}

.sentiment-ranking-table__row:last-child {
  border-bottom: none;
}

.sentiment-ranking-table__row--target {
  background: #f8fafc;
}

.sentiment-ranking-table__brand {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.sentiment-ranking-table__badge {
  flex: none;
  padding: 2px 6px;
  border-radius: 999px;
  background: #111827;
  color: #ffffff;
  font-size: 11px;
  line-height: 1;
}

.truth-gap-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}

.truth-gap-card {
  border-style: dashed;
  border-color: #cbd5e1;
  background: #f8fafc;
}

.truth-gap-card strong {
  display: block;
  font-size: 14px;
  color: #111827;
}

.truth-gap-card__list {
  margin: 8px 0 0;
  padding-left: 18px;
  display: grid;
  gap: 6px;
  color: #475569;
  font-size: 12px;
  line-height: 1.55;
}

@media (max-width: 980px) {
  .sentiment-grid,
  .sentiment-ranking-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 760px) {
  .sentiment-ranking-table {
    overflow-x: auto;
  }

  .sentiment-ranking-table__head,
  .sentiment-ranking-table__row {
    min-width: 420px;
  }
}
</style>
