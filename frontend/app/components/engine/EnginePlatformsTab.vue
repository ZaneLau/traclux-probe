<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import type { WorkbenchBrandPerformanceModelRow } from '~/types/workbench'

type WorkbenchPlatformMetricKey = 'visibility' | 'recommend-rate' | 'mention-rate' | 'top3-rate'

interface WorkbenchPlatformRankRow {
  rank: number
  label: string
  displayLabel: string
  value: number | null
  delta: number | null
  reportCount: number | null
  primary: boolean
}

interface WorkbenchPlatformProvenanceContext {
  sampleSize?: number | null
  mentionRate?: number | null
  recommendRate?: number | null
  top3Rate?: number | null
}

interface WorkbenchPlatformMetricSection {
  key: WorkbenchPlatformMetricKey
  title: string
  subtitle: string
  chartLabel: string
  rankTitle: string
  rankColumnLabel: string
  rows: WorkbenchPlatformRankRow[]
  chartOption: EChartsOption | null
}

const props = defineProps<{
  selectedBrand: string
  workbenchModelRows: WorkbenchBrandPerformanceModelRow[]
}>()

const emit = defineEmits<{
  (event: 'openProvenance', anchorKey: string, metricKey: string, metricValue?: string | null, metricLabel?: string | null, platform?: string | null, context?: WorkbenchPlatformProvenanceContext): void
}>()

function normalizeNullableNumber(value?: number | null) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function formatWorkbenchPercent(value?: number | null) {
  const normalized = normalizeNullableNumber(value)
  return normalized == null ? '--' : `${normalized.toFixed(1)}%`
}

function formatWorkbenchDelta(value?: number | null) {
  const normalized = normalizeNullableNumber(value)
  if (normalized == null) {
    return '暂无对比'
  }
  if (Math.abs(normalized) < 0.1) {
    return '持平'
  }
  return `${normalized > 0 ? '+' : ''}${normalized.toFixed(1)}%`
}

function formatWorkbenchCount(value?: number | null) {
  const normalized = normalizeNullableNumber(value)
  return normalized == null ? '--' : `${Math.round(normalized)}`
}

function provenanceContext(row: WorkbenchBrandPerformanceModelRow): WorkbenchPlatformProvenanceContext {
  return {
    sampleSize: row.sampleSize ?? row.reportCount,
    mentionRate: row.mentionRate,
    recommendRate: row.recommendRate,
    top3Rate: row.top3Rate
  }
}

function hasWorkbenchMetricValue(row: WorkbenchBrandPerformanceModelRow) {
  return [
    row.visibilityScore,
    row.recommendRate,
    row.mentionRate,
    row.top3Rate
  ].some(value => normalizeNullableNumber(value) != null)
}

function resolveWorkbenchMetricValue(row: WorkbenchBrandPerformanceModelRow, key: WorkbenchPlatformMetricKey) {
  if (key === 'visibility') {
    return normalizeNullableNumber(row.visibilityScore)
  }
  if (key === 'recommend-rate') {
    return normalizeNullableNumber(row.recommendRate)
  }
  if (key === 'mention-rate') {
    return normalizeNullableNumber(row.mentionRate)
  }
  return normalizeNullableNumber(row.top3Rate)
}

function resolveWorkbenchMetricDelta(row: WorkbenchBrandPerformanceModelRow, key: WorkbenchPlatformMetricKey) {
  if (key === 'visibility') {
    return normalizeNullableNumber(row.deltaVisibilityScore)
  }
  return null
}

function buildWorkbenchMetricOption(rows: WorkbenchPlatformRankRow[], label: string) {
  const visibleRows = rows.filter(row => row.value != null)
  if (!visibleRows.length) {
    return null
  }

  return {
    grid: {
      left: 16,
      right: 16,
      top: 20,
      bottom: 24,
      containLabel: true
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    xAxis: {
      type: 'category',
      data: visibleRows.map(row => row.displayLabel),
      axisTick: { show: false },
      axisLine: {
        lineStyle: {
          color: '#d1d5db'
        }
      },
      axisLabel: {
        color: '#6b7280',
        fontSize: 11,
        interval: 0
      }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      splitNumber: 4,
      axisLabel: {
        color: '#6b7280',
        fontSize: 11,
        formatter: '{value}%'
      },
      splitLine: {
        lineStyle: {
          color: '#f3f4f6'
        }
      }
    },
    series: [
      {
        name: label,
        type: 'bar',
        barWidth: 22,
        itemStyle: {
          color: '#111827',
          borderRadius: [2, 2, 0, 0]
        },
        data: visibleRows.map(row => row.value)
      }
    ]
  } satisfies EChartsOption
}

const workbenchOverviewRows = computed(() => {
  const deduped = new Map<string, WorkbenchBrandPerformanceModelRow>()

  for (const row of props.workbenchModelRows || []) {
    if (!hasWorkbenchMetricValue(row)) {
      continue
    }
    const platformLabel = row.label
    const current = deduped.get(platformLabel)
    if (!current) {
      deduped.set(platformLabel, row)
      continue
    }

    const currentPrimary = Boolean(current.primary)
    const nextPrimary = Boolean(row.primary)
    if (!currentPrimary && nextPrimary) {
      deduped.set(platformLabel, row)
      continue
    }
    if (currentPrimary && !nextPrimary) {
      continue
    }

    const currentScore = normalizeNullableNumber(current.visibilityScore) ?? -1
    const nextScore = normalizeNullableNumber(row.visibilityScore) ?? -1
    if (nextScore > currentScore) {
      deduped.set(platformLabel, row)
    }
  }

  return Array.from(deduped.values())
    .sort((left, right) => (right.visibilityScore ?? -1) - (left.visibilityScore ?? -1))
})

const hasWorkbenchPlatformRows = computed(() => workbenchOverviewRows.value.length > 0)

const workbenchMetricSections = computed<WorkbenchPlatformMetricSection[]>(() => {
  const specs = [
    {
      key: 'visibility',
      title: '模型可见度',
      subtitle: `${props.selectedBrand} 在各模型平台中的真实 AI 可见度表现`,
      chartLabel: 'AI 可见度',
      rankTitle: '模型可见度排名',
      rankColumnLabel: 'AI 可见度'
    },
    {
      key: 'recommend-rate',
      title: '模型推荐率',
      subtitle: `${props.selectedBrand} 在各模型平台回答中的推荐率`,
      chartLabel: 'AI 推荐率',
      rankTitle: '模型推荐率排名',
      rankColumnLabel: 'AI 推荐率'
    },
    {
      key: 'mention-rate',
      title: '模型提及率',
      subtitle: `${props.selectedBrand} 在各模型平台回答中的提及率`,
      chartLabel: 'AI 提及率',
      rankTitle: '模型提及率排名',
      rankColumnLabel: 'AI 提及率'
    },
    {
      key: 'top3-rate',
      title: '模型 Top3 出现率',
      subtitle: `${props.selectedBrand} 在各模型平台回答 Top3 中的出现率`,
      chartLabel: 'Top3 出现率',
      rankTitle: '模型 Top3 出现率排名',
      rankColumnLabel: 'Top3 出现率'
    }
  ] as const satisfies Array<Omit<WorkbenchPlatformMetricSection, 'rows' | 'chartOption'>>

  return specs.map((spec) => {
    const rows = workbenchOverviewRows.value
      .map(row => ({
        rank: 0,
        label: row.label,
        displayLabel: row.label,
        value: resolveWorkbenchMetricValue(row, spec.key),
        delta: resolveWorkbenchMetricDelta(row, spec.key),
        reportCount: normalizeNullableNumber(row.reportCount),
        primary: Boolean(row.primary)
      }))
      .sort((left, right) => {
        if (left.value == null && right.value == null) {
          return left.label.localeCompare(right.label)
        }
        if (left.value == null) {
          return 1
        }
        if (right.value == null) {
          return -1
        }
        return right.value - left.value
      })
      .map((row, index) => ({
        ...row,
        rank: index + 1
      }))

    return {
      ...spec,
      rows,
      chartOption: buildWorkbenchMetricOption(rows, spec.chartLabel)
    }
  })
})
</script>

<template>
  <section class="surface section-surface platform-metric-surface">
    <div class="metric-card__head">
      <div class="surface-head__title">
        <div class="title-with-help">
          <strong>
            <WikiGeoWikiTrigger entrypoint-id="engine.dimension.platform">
              模型平台总览
            </WikiGeoWikiTrigger>
          </strong>
        </div>
        <p class="platform-module-subcopy">
          查看 {{ props.selectedBrand }} 在各模型平台的当前表现。
        </p>
      </div>
    </div>

    <div
      v-if="!hasWorkbenchPlatformRows"
      class="state-block"
    >
      当前品牌暂无模型维度表现数据
    </div>
    <div
      v-else
      class="workbench-platform-overview-table"
    >
      <div class="workbench-platform-overview-table__head">
        <span>模型</span>
        <span>AI 可见度</span>
        <span>AI 推荐率</span>
        <span>AI 提及率</span>
        <span>Top3 出现率</span>
        <span>样本数</span>
      </div>

      <div
        v-for="row in workbenchOverviewRows"
        :key="`workbench-model-overview-${row.id}`"
        class="workbench-platform-overview-table__row"
      >
        <div class="workbench-platform-overview-table__model">
          <span class="workbench-platform-overview-table__model-copy">
            <strong>{{ row.label }}</strong>
          </span>
        </div>
        <span class="provenance-value">
          {{ formatWorkbenchPercent(row.visibilityScore) }}
        </span>
        <span class="provenance-value">
          {{ formatWorkbenchPercent(row.recommendRate) }}
        </span>
        <span class="provenance-value">
          {{ formatWorkbenchPercent(row.mentionRate) }}
        </span>
        <span class="provenance-value">
          {{ formatWorkbenchPercent(row.top3Rate) }}
        </span>
        <span class="workbench-platform-overview-table__sample">{{ formatWorkbenchCount(row.sampleSize ?? row.reportCount) }}</span>
      </div>
    </div>
  </section>

  <section
    v-for="section in workbenchMetricSections"
    :key="`workbench-platform-section-${section.key}`"
    class="surface section-surface platform-metric-surface"
  >
    <div class="metric-card__head">
      <div class="surface-head__title">
        <div class="title-with-help">
          <strong>{{ section.title }}</strong>
        </div>
        <p class="platform-module-subcopy">
          {{ section.subtitle }}
        </p>
      </div>
    </div>

    <div
      v-if="!section.rows.some(row => row.value != null)"
      class="state-block"
    >
      当前品牌暂无{{ section.chartLabel }}数据
    </div>
    <div
      v-else
      class="hero-grid platform-hero-grid"
    >
      <article class="surface chart-surface platform-chart-surface">
        <div class="platform-chart-head">
          <span>{{ section.chartLabel }}</span>
        </div>
        <GeoLazyEChart
          :option="section.chartOption"
          :height="316"
          empty-text="暂无平台图表数据"
        />
      </article>

      <aside class="surface side-surface platform-side-surface">
        <div class="surface-head surface-head--compact">
          <strong>{{ section.rankTitle }}</strong>
        </div>

        <div class="platform-rank-table">
          <div class="platform-rank-table__head platform-rank-table__head--workbench">
            <span />
            <span>模型</span>
            <span>{{ section.rankColumnLabel }}</span>
            <span>变化</span>
            <span>样本</span>
          </div>

          <div
            v-for="row in section.rows"
            :key="`workbench-platform-rank-${section.key}-${row.label}`"
            class="platform-rank-table__row platform-rank-table__row--workbench"
          >
            <span>{{ row.rank }}.</span>
            <span class="platform-rank-table__name">
              <span class="platform-rank-table__name-copy">
                <span>{{ row.displayLabel }}</span>
              </span>
            </span>
            <span>{{ formatWorkbenchPercent(row.value) }}</span>
            <span :class="row.delta == null ? 'platform-rank-table__delta-muted' : (row.delta >= 0 ? 'delta-up' : 'delta-down')">
              {{ formatWorkbenchDelta(row.delta) }}
            </span>
            <span class="platform-rank-table__sample">{{ formatWorkbenchCount(row.reportCount) }}</span>
          </div>
        </div>
      </aside>
    </div>
  </section>

  <section class="surface section-surface truth-gap-card">
    <div class="surface-head surface-head--compact">
      <strong>平台重点观察</strong>
    </div>
    <div class="truth-gap-note">
      这里集中看平台总览、排序和指标差距，先判断当前该优先优化哪个平台的可见度表现。
    </div>
  </section>
</template>

<style scoped>
.surface {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #ffffff;
  padding: 10px;
}

.section-surface {
  display: grid;
  gap: 10px;
}

.surface-head {
  min-height: 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.surface-head--compact {
  min-height: 24px;
}

.surface-head__title {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.title-with-help {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.surface-head__title strong {
  font-size: 16px;
  line-height: 1.3;
}

.metric-card__head {
  min-height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.state-block {
  min-height: 120px;
  border: 1px dashed #9ca3af;
  border-radius: 2px;
  display: grid;
  place-items: center;
  color: #4b5563;
  font-size: 12px;
}

.delta-up {
  color: #047857;
}

.delta-down {
  color: #b91c1c;
}

.platform-metric-surface {
  border: none;
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

.platform-module-subcopy {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: #6b7280;
}

.workbench-platform-overview-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
  background: #ffffff;
}

.workbench-platform-overview-table__head,
.workbench-platform-overview-table__row {
  display: grid;
  grid-template-columns: minmax(180px, 1.5fr) repeat(4, minmax(0, 0.9fr)) 72px;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  padding: 0 10px;
  font-size: 12px;
}

.workbench-platform-overview-table__head {
  border-bottom: 1px solid #d1d5db;
  background: #f8fafc;
  color: #6b7280;
  font-weight: 600;
}

.workbench-platform-overview-table__row {
  border-bottom: 1px solid #e5e7eb;
  color: #1f2937;
}

.workbench-platform-overview-table__row:last-child {
  border-bottom: none;
}

.workbench-platform-overview-table__model {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.workbench-platform-overview-table__model-copy,
.platform-rank-table__name-copy {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.workbench-platform-overview-table__model-copy strong,
.platform-rank-table__name-copy span:first-child {
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.workbench-platform-overview-table__model-meta,
.platform-rank-table__meta {
  color: #6b7280;
  font-size: 11px;
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.95fr);
  gap: 10px;
}

.chart-surface,
.side-surface {
  border: 1px solid #d1d5db;
}

.platform-chart-head {
  margin-bottom: 8px;
  color: #374151;
  font-size: 12px;
  font-weight: 600;
}

.platform-rank-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
}

.platform-rank-table__head,
.platform-rank-table__row {
  display: grid;
  align-items: center;
  grid-template-columns: 44px minmax(0, 1.4fr) 88px 72px 64px;
  gap: 8px;
  min-height: 42px;
  padding: 0 10px;
  font-size: 12px;
}

.platform-rank-table__head {
  background: #f8fafc;
  border-bottom: 1px solid #d1d5db;
  color: #6b7280;
  font-weight: 600;
}

.platform-rank-table__row {
  border-bottom: 1px solid #e5e7eb;
  color: #1f2937;
}

.platform-rank-table__row:last-child {
  border-bottom: none;
}

.platform-rank-table__name {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.platform-rank-table__delta-muted,
.workbench-platform-overview-table__sample,
.platform-rank-table__sample {
  color: #6b7280;
}

.truth-gap-card {
  border-style: dashed;
  border-color: #cbd5e1;
  background: #f8fafc;
}

.truth-gap-card span {
  font-size: 12px;
  color: #475569;
}

.truth-gap-note {
  font-size: 12px;
  line-height: 1.6;
  color: #475569;
}

@media (max-width: 980px) {
  .hero-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .workbench-platform-overview-table__head,
  .workbench-platform-overview-table__row {
    grid-template-columns: minmax(160px, 1.2fr) repeat(4, minmax(72px, 0.85fr)) 64px;
  }
}

@media (max-width: 760px) {
  .workbench-platform-overview-table,
  .platform-rank-table {
    overflow-x: auto;
  }

  .workbench-platform-overview-table__head,
  .workbench-platform-overview-table__row {
    min-width: 720px;
  }

  .platform-rank-table__head,
  .platform-rank-table__row {
    min-width: 520px;
  }
}
</style>
