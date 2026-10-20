<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import type {
  AssetRow,
  BrandTrackingSeed,
  OptionItem,
  TopicDetailQuestionRow,
  TopicVisibilityRankCell,
  TopicVisibilityRankRow
} from '~/types/engine'
import type { WorkbenchBrandPerformanceTopicSummary } from '~/types/workbench'

const props = defineProps<{
  hasCoverage: boolean
  workbenchMode: boolean
  selectedBrand: string
  dimensionLabel: string
  chartConfigOptions: OptionItem<string>[]
  selectedChartConfig: string
  setChartConfig: (value: string) => void
  visibilityScore: number | null
  visibilityDelta: number | null
  visibilityRank: number | null
  rankDelta: number | null
  trendOption: EChartsOption | null
  assetRows: AssetRow[]
  formatSigned: (value: number | null, suffix?: string) => string
  selectedSovShare: number | null
  sovOption: EChartsOption | null
  selectedAveragePosition: number | null
  averagePositionOption: EChartsOption | null
  averagePositionUnavailableText?: string | null
  headline: string
  issueCount: number | null
  evidenceCount: number | null
  topTopicLabels: string[]
  rankingColumns: number[]
  topicVisibilityRows: TopicVisibilityRankRow[]
  topicSummaryRows: WorkbenchBrandPerformanceTopicSummary[]
  isTopicExpanded: (topic: string) => boolean
  toggleTopicDetails: (topic: string) => void
  topicDetailRows: (topic: string) => TopicDetailQuestionRow[]
  openTopicInsight: (topic: string, question: string, rank: number, cell: TopicVisibilityRankCell) => void
  peecBrandRows: BrandTrackingSeed[]
}>()

const normalizedBrandRows = computed(() => {
  return (props.peecBrandRows || [])
    .filter(item => item.status !== 'rejected')
    .slice()
    .sort((a, b) => b.visibility - a.visibility)
    .slice(0, 8)
    .map((item, index) => ({
      rank: index + 1,
      brand: item.brand,
      visibility: item.visibility,
      sentiment: item.sentiment,
      position: item.position
    }))
})

const sortedTopicSummaryRows = computed(() => {
  return (props.topicSummaryRows || [])
    .slice()
    .sort((a, b) => (a.seqNo ?? 0) - (b.seqNo ?? 0))
})

const showLegacyChartConfig = computed(() => !props.workbenchMode)

const hasSovProjection = computed(() => {
  return props.selectedSovShare != null || props.sovOption != null
})

const hasAveragePositionProjection = computed(() => {
  return props.selectedAveragePosition != null || props.averagePositionOption != null
})

function formatRatio(value: number | null | undefined, suffix = '%') {
  return typeof value === 'number' && Number.isFinite(value) ? `${value.toFixed(1)}${suffix}` : '--'
}

function firstSampleQuestion(row: WorkbenchBrandPerformanceTopicSummary) {
  return row.sampleQuestions?.[0] || '--'
}

const competitorPanelTitle = computed(() => (props.workbenchMode ? '竞品对比' : '资产排名'))
const competitorNameLabel = computed(() => (props.workbenchMode ? '品牌' : '资产'))
const competitorEmptyCopy = computed(() => (props.workbenchMode ? '当前品牌暂无竞品对比数据' : '暂无资产排名数据'))
const topicSectionTitle = computed(() => (props.workbenchMode ? '主题表现摘要' : '主题可见度排名'))
const topicSectionTip = computed(() => (
  props.workbenchMode
    ? '按主题查看当前品牌的探针启用情况、提示词占比、信号占比与样例问题'
    : '按主题比较品牌在回答输出中的可见度排名'
))
const topicSectionSubcopy = computed(() => (
  props.workbenchMode
    ? '按主题查看当前品牌的结构化覆盖与问题样例'
    : '按主题对比品牌在问答结果中的可见度位次'
))
const sovTipCopy = computed(() => (
  props.workbenchMode
    ? '当前品牌在竞品集合中的提及份额'
    : '该分数基于历史数据均值计算'
))
</script>

<template>
  <section class="surface section-surface visibility-surface">
    <div class="surface-head">
      <div class="surface-head__title">
        <div class="title-with-help">
          <strong>可见度</strong>
          <GeoInfoPopover text="这里集中看品牌在模型回答里的可见度表现，包括得分、趋势、竞品位置和主题深挖。" />
        </div>
        <div class="surface-head__context">
          <span class="surface-head__object">{{ props.selectedBrand }}</span>
          <span class="surface-head__meta">{{ props.dimensionLabel }}</span>
        </div>
      </div>
      <div class="surface-head__actions">
        <details
          v-if="showLegacyChartConfig"
          class="compact-select"
        >
          <summary class="compact-select__trigger">
            图表配置
          </summary>
          <div class="compact-select__panel">
            <label
              v-for="option in props.chartConfigOptions"
              :key="`chart-${option.key}`"
              class="compact-select__option"
            >
              <input
                :checked="props.selectedChartConfig === option.key"
                type="radio"
                :value="option.key"
                name="chart-config-mode"
                @change="props.setChartConfig(option.key)"
              >
              <span>{{ option.label }}</span>
            </label>
          </div>
        </details>
      </div>
    </div>

    <div
      v-if="!props.hasCoverage"
      class="state-block"
    >
      {{ props.workbenchMode ? '当前品牌暂无可用的品牌表现结果' : '至少选择 1 个主题和 1 个平台' }}
    </div>

    <div
      v-else
      class="hero-grid"
    >
      <article class="surface chart-surface">
        <div class="kpi-grid">
          <article class="kpi-tile">
            <span>可见度得分</span>
            <strong>{{ props.visibilityScore == null ? '--' : props.visibilityScore.toFixed(1) }}</strong>
            <small :class="props.visibilityDelta != null && props.visibilityDelta >= 0 ? 'delta-up' : 'delta-down'">
              {{ props.formatSigned(props.visibilityDelta, '%') }}
            </small>
          </article>
          <article class="kpi-tile">
            <span>排名</span>
            <strong>{{ props.visibilityRank == null ? '--' : `#${props.visibilityRank}` }}</strong>
            <small :class="props.rankDelta != null && props.rankDelta >= 0 ? 'delta-up' : 'delta-down'">
              {{ props.rankDelta == null ? '--' : `${props.rankDelta > 0 ? '+' : ''}${props.rankDelta}` }}
            </small>
          </article>
        </div>

        <GeoLazyEChart
          :option="props.trendOption"
          :height="326"
          empty-text="暂无趋势数据"
        />
      </article>

      <aside class="surface side-surface">
        <div class="surface-head surface-head--compact">
          <strong>{{ competitorPanelTitle }}</strong>
          <span>{{ props.assetRows.length }}</span>
        </div>

        <div
          v-if="props.assetRows.length"
          class="asset-table"
        >
          <div class="asset-table__head">
            <span>排名</span>
            <span>{{ competitorNameLabel }}</span>
            <span>可见度</span>
            <span>变化</span>
          </div>

          <div
            v-for="row in props.assetRows"
            :key="row.asset"
            class="asset-row"
          >
            <span>#{{ row.rank }}</span>
            <span class="asset-row__name">{{ row.asset }}</span>
            <span>{{ row.score.toFixed(1) }}</span>
            <span :class="row.change >= 0 ? 'delta-up' : 'delta-down'">{{ props.formatSigned(row.change, '%') }}</span>
          </div>
        </div>
        <div
          v-else
          class="state-block"
        >
          {{ competitorEmptyCopy }}
        </div>
      </aside>
    </div>

  </section>

  <section class="metric-two-up">
    <article class="surface section-surface metric-card">
      <div class="metric-card__head">
        <div class="sov-inline-head">
          <strong>声量占比</strong>
          <button
            type="button"
            class="info-hotspot"
            aria-label="声量占比说明"
          >
            ?
            <span class="info-hotspot__tip">{{ sovTipCopy }}</span>
          </button>
        </div>
        <button
          v-if="showLegacyChartConfig"
          type="button"
          class="btn-plain"
        >
          图表配置
        </button>
      </div>
      <p class="sov-subcopy">
        品牌在 AI 生成回答中相对竞品的提及占比
      </p>

      <div
        v-if="!props.hasCoverage"
        class="state-block"
      >
        当前过滤组合暂无声量占比数据
      </div>
      <div
        v-else-if="!hasSovProjection"
        class="state-block"
      >
        {{ props.workbenchMode ? '当前品牌暂无声量占比结果' : '当前品牌暂无声量占比数据' }}
      </div>
      <div
        v-else
        class="metric-card__body"
      >
        <div class="sov-inline-head">
          <strong>声量占比</strong>
          <button
            type="button"
            class="info-hotspot"
            aria-label="声量占比说明"
          >
            ?
            <span class="info-hotspot__tip">{{ sovTipCopy }}</span>
          </button>
        </div>
        <div class="sov-kpi">
          {{ props.selectedSovShare == null ? '--' : `${props.selectedSovShare.toFixed(1)}%` }}
        </div>
        <GeoLazyEChart
          :option="props.sovOption"
          :height="280"
          empty-text="暂无声量占比数据"
        />
      </div>
    </article>

    <article class="surface section-surface metric-card">
      <div class="metric-card__head">
        <div class="sov-inline-head">
          <strong>平均位次</strong>
        </div>
        <button
          v-if="showLegacyChartConfig"
          type="button"
          class="btn-plain"
        >
          图表配置
        </button>
      </div>
      <p class="avgpos-subcopy">
        品牌在 AI 生成回答中的平均出现位置，数值越低越靠前
      </p>

      <div
        v-if="!props.hasCoverage"
        class="state-block"
      >
        当前过滤组合暂无平均位次数据
      </div>
      <div
        v-else-if="!hasAveragePositionProjection"
        class="state-block state-block--explained"
      >
        <strong>{{ props.workbenchMode ? '当前窗口无法计算平均位次' : '当前品牌暂无平均位次数据' }}</strong>
        <span>
          {{ props.averagePositionUnavailableText || '本窗口未形成可计算的出现位次样本。' }}
        </span>
      </div>
      <div
        v-else
        class="metric-card__body"
      >
        <div class="sov-inline-head">
          <strong>平均位次</strong>
        </div>
        <div class="sov-kpi">
          {{ props.selectedAveragePosition == null ? '--' : props.selectedAveragePosition.toFixed(1) }}
        </div>
        <GeoLazyEChart
          :option="props.averagePositionOption"
          :height="280"
          empty-text="暂无平均位次数据"
        />
      </div>
    </article>
  </section>

  <section
    v-if="!props.workbenchMode"
    class="peec-overview-grid"
  >
    <article class="surface section-surface peec-card">
      <div class="surface-head">
        <strong>品牌排名</strong>
        <NuxtLink to="/brands">查看全部</NuxtLink>
      </div>
      <div
        v-if="!normalizedBrandRows.length"
        class="state-block peec-state-block"
      >
        暂无品牌排名数据
      </div>
      <div
        v-else
        class="peec-brand-table"
      >
        <div class="peec-brand-table__head">
          <span>#</span>
          <span>品牌</span>
          <span>可见度</span>
          <span>情感</span>
          <span>位次</span>
        </div>
        <div
          v-for="row in normalizedBrandRows"
          :key="`peec-brand-${row.rank}-${row.brand}`"
          class="peec-brand-table__row"
        >
          <span>#{{ row.rank }}</span>
          <span class="peec-brand-table__name">{{ row.brand }}</span>
          <span>{{ row.visibility.toFixed(1) }}%</span>
          <span>{{ row.sentiment }}</span>
          <span>{{ row.position.toFixed(1) }}</span>
        </div>
      </div>
    </article>
  </section>

  <section class="surface section-surface topic-rank-section">
    <div class="sov-inline-head">
      <strong>{{ topicSectionTitle }}</strong>
      <button
        type="button"
        class="info-hotspot"
        :aria-label="`${topicSectionTitle}说明`"
      >
        ?
        <span class="info-hotspot__tip">{{ topicSectionTip }}</span>
      </button>
    </div>
    <p class="topic-rank-subcopy">
      {{ topicSectionSubcopy }}
    </p>

    <div
      v-if="props.workbenchMode && !sortedTopicSummaryRows.length"
      class="state-block"
    >
      当前品牌暂无主题表现数据
    </div>
    <div
      v-else-if="props.workbenchMode"
      class="topic-summary-table"
    >
      <div class="topic-summary-table__head">
        <span>主题</span>
        <span>启用探针</span>
        <span>提示词占比</span>
        <span>信号占比</span>
        <span>样例问题</span>
      </div>
      <div
        v-for="row in sortedTopicSummaryRows"
        :key="row.topicId"
        class="topic-summary-table__row"
      >
        <div class="topic-summary-table__topic">
          <strong>{{ row.topicName }}</strong>
          <small>#{{ row.seqNo }}</small>
        </div>
        <span>{{ row.enabledPromptCount }}/{{ row.promptCount }}</span>
        <span>{{ formatRatio(row.promptShare) }}</span>
        <span>{{ formatRatio(row.signalShare) }}</span>
        <span class="topic-summary-table__question">{{ firstSampleQuestion(row) }}</span>
      </div>
    </div>
    <div
      v-else-if="!props.hasCoverage"
      class="state-block"
    >
      当前过滤组合暂无主题排名数据
    </div>
    <div
      v-else
      class="topic-rank-table"
    >
      <div class="topic-rank-table__head">
        <span class="topic-rank-topic topic-rank-topic--head">主题</span>
        <span
          v-for="rank in props.rankingColumns"
          :key="`head-${rank}`"
        >#{{ rank }}</span>
      </div>

      <template
        v-for="row in props.topicVisibilityRows"
        :key="`topic-rank-${row.topic}`"
      >
        <div
          class="topic-rank-row topic-rank-row--parent"
          :class="{ 'topic-rank-row--expanded': props.isTopicExpanded(row.topic) }"
          @click="props.toggleTopicDetails(row.topic)"
        >
          <button
            type="button"
            class="topic-rank-topic topic-rank-topic--button"
          >
            <span
              class="topic-rank-topic__chevron"
              :class="{ 'topic-rank-topic__chevron--open': props.isTopicExpanded(row.topic) }"
            >›</span>
            <span>{{ row.topic }}</span>
          </button>
          <div
            v-for="(cell, index) in row.ranks"
            :key="`cell-${row.topic}-${index}`"
            class="topic-rank-cell"
          >
            <button
              type="button"
              class="topic-rank-badge topic-rank-badge--interactive"
              :class="{ 'topic-rank-badge--placeholder': cell.asset === '--' }"
              :style="{ backgroundColor: cell.color }"
              :title="cell.asset === '--' ? '暂无对象' : `${cell.asset} · #${index + 1}`"
              :disabled="cell.asset === '--'"
              @click.stop="props.openTopicInsight(row.topic, '主题概览', index + 1, cell)"
            >
              {{ cell.shortLabel }}
              <span
                v-if="cell.asset !== '--'"
                class="topic-rank-badge__tooltip"
              >
                {{ cell.asset }}<br>#{{ index + 1 }} | {{ row.topic }}
              </span>
            </button>
          </div>
        </div>

        <div
          v-if="props.isTopicExpanded(row.topic)"
          class="topic-rank-detail-group"
        >
          <div
            v-for="detail in props.topicDetailRows(row.topic)"
            :key="detail.id"
            class="topic-rank-row topic-rank-row--detail"
          >
            <div class="topic-rank-topic topic-rank-topic--detail">
              {{ detail.question }}
            </div>
            <div
              v-for="(cell, index) in detail.ranks"
              :key="`detail-${detail.id}-${index}`"
              class="topic-rank-cell topic-rank-cell--detail"
            >
              <div class="topic-rank-cell-inner">
                <button
                  type="button"
                  class="topic-rank-badge topic-rank-badge--interactive"
                  :class="{ 'topic-rank-badge--placeholder': cell.asset === '--' }"
                  :style="{ backgroundColor: cell.color }"
                  :title="cell.asset === '--' ? '暂无对象' : `${cell.asset} · #${index + 1}`"
                  :disabled="cell.asset === '--'"
                  @click.stop="props.openTopicInsight(row.topic, detail.question, index + 1, cell)"
                >
                  {{ cell.shortLabel }}
                  <span
                    v-if="cell.asset !== '--'"
                    class="topic-rank-badge__tooltip"
                  >
                    {{ cell.asset }}<br>#{{ index + 1 }} | {{ row.topic }}
                  </span>
                </button>
                <button
                  v-if="cell.asset !== '--'"
                  type="button"
                  class="topic-rank-analyze"
                  @click.stop="props.openTopicInsight(row.topic, detail.question, index + 1, cell)"
                >
                  分析 ↗
                </button>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </section>

  <section
    v-if="props.workbenchMode"
    class="surface section-surface topic-truth-gap"
  >
    <div class="surface-head surface-head--compact">
      <strong>主题深挖</strong>
      <span>即将开放</span>
    </div>
    <div class="truth-gap-note">
      当前先展示主题摘要、占比和样例问题，更细的主题下钻会在后续版本开放。
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

.visibility-surface {
  border: none;
}

.topic-truth-gap {
  border-style: dashed;
  border-color: #cbd5e1;
  background: #f8fafc;
}

.truth-gap-note {
  font-size: 12px;
  line-height: 1.6;
  color: #475569;
}

.surface-head {
  min-height: 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.surface-head__title {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.title-with-help {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.surface-head__context {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: #6b7280;
}

.surface-head strong,
.sov-inline-head strong {
  font-size: 16px;
  font-weight: 700;
  line-height: 1.3;
}

.surface-head span {
  font-size: 12px;
  color: #4b5563;
}

.surface-head__actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.surface-head--compact {
  min-height: 24px;
}

.compact-select {
  position: relative;
}

.compact-select__trigger {
  list-style: none;
  min-height: 30px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #ffffff;
  color: #374151;
  font-size: 12px;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  cursor: pointer;
}

.compact-select__trigger::-webkit-details-marker {
  display: none;
}

.compact-select__trigger::after {
  content: '▾';
  font-size: 10px;
  color: #6b7280;
  margin-left: 6px;
}

.compact-select__panel {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  min-width: 136px;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 12px 24px -16px rgba(15, 23, 42, 0.45);
  padding: 8px;
  display: grid;
  gap: 4px;
  z-index: 46;
}

.compact-select__option {
  min-height: 28px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #374151;
  padding: 0 8px;
}

.compact-select__option:hover {
  background: #f3f4f6;
}

.compact-select__option input {
  width: 14px;
  height: 14px;
  accent-color: #111827;
}

.surface-head__object,
.surface-head__meta {
  white-space: nowrap;
}

.hero-grid {
  --hero-gap: 0px;
  --hero-left-fr: 1.8;
  --hero-right-fr: 0.9;
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.8fr) minmax(360px, 0.9fr);
  gap: var(--hero-gap);
}

.hero-grid::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: calc(((100% - var(--hero-gap)) * var(--hero-left-fr) / (var(--hero-left-fr) + var(--hero-right-fr))) + (var(--hero-gap) / 2));
  width: 1px;
  background: #d1d5db;
  pointer-events: none;
}

.chart-surface {
  border-right: none;
  border-radius: 2px 0 0 2px;
  padding-right: 10px;
  display: grid;
  align-content: start;
  gap: 10px;
}

.side-surface {
  border-left: none;
  border-radius: 0 2px 2px 0;
  padding-left: 10px;
  display: grid;
  align-content: start;
  gap: 8px;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.kpi-tile {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  min-height: 74px;
  padding: 8px 10px;
  display: grid;
  align-content: space-between;
  gap: 4px;
}

.kpi-tile span {
  font-size: 11px;
  color: #4b5563;
}

.kpi-tile strong {
  font-size: 28px;
  line-height: 1;
  letter-spacing: -0.02em;
}

.kpi-tile small {
  font-size: 11px;
}

.asset-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
}

.asset-table__head,
.asset-row {
  min-height: 36px;
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) 76px 70px;
  align-items: center;
  gap: 8px;
  padding: 0 8px;
  font-size: 12px;
}

.asset-table__head {
  background: #f3f4f6;
  border-bottom: 1px solid #d1d5db;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #6b7280;
}

.asset-row {
  border-bottom: 1px solid #e5e7eb;
}

.asset-row:last-child {
  border-bottom: none;
}

.asset-row__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-two-up {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 10px;
}

.metric-two-up > * {
  min-width: 0;
}

.peec-overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 10px;
}

.peec-card {
  border: none;
}

.peec-state-block {
  min-height: 180px;
}

.surface-head a {
  font-size: 12px;
  color: #2563eb;
  text-decoration: none;
}

.surface-head a:hover {
  text-decoration: underline;
}

.peec-brand-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
}

.peec-brand-table__head,
.peec-brand-table__row {
  min-height: 36px;
  display: grid;
  align-items: center;
  grid-template-columns: 42px minmax(0, 1.1fr) 88px 70px 66px;
  gap: 8px;
  padding: 0 10px;
  font-size: 12px;
}

.peec-brand-table__head {
  background: #f8fafc;
  border-bottom: 1px solid #d1d5db;
  color: #6b7280;
  font-weight: 600;
}

.peec-brand-table__row {
  border-bottom: 1px solid #e5e7eb;
}

.peec-brand-table__row:last-child {
  border-bottom: none;
}

.peec-brand-table__name {
  font-weight: 600;
}

.peec-sources-grid {
  display: grid;
  grid-template-columns: minmax(240px, 0.9fr) minmax(0, 1.2fr);
  gap: 8px;
  align-items: start;
}

.peec-sources-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
}

.peec-sources-table__head,
.peec-sources-table__row {
  min-height: 36px;
  display: grid;
  align-items: center;
  grid-template-columns: minmax(0, 1.2fr) 104px 84px 84px;
  gap: 8px;
  padding: 0 10px;
  font-size: 12px;
}

.peec-sources-table__head {
  background: #f8fafc;
  border-bottom: 1px solid #d1d5db;
  color: #6b7280;
  font-weight: 600;
}

.peec-sources-table__row {
  border-bottom: 1px solid #e5e7eb;
}

.peec-sources-table__row:last-child {
  border-bottom: none;
}

.peec-sources-table__domain {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.peec-chat-surface {
  border: none;
}

.peec-chat-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
}

.peec-chat-table__head,
.peec-chat-table__row {
  min-height: 38px;
  display: grid;
  align-items: center;
  grid-template-columns: 108px 98px minmax(0, 1.8fr) 116px 140px;
  gap: 8px;
  padding: 0 10px;
  font-size: 12px;
}

.peec-chat-table__head {
  background: #f8fafc;
  border-bottom: 1px solid #d1d5db;
  color: #6b7280;
  font-weight: 600;
}

.peec-chat-table__row {
  border-bottom: 1px solid #e5e7eb;
}

.peec-chat-table__row:last-child {
  border-bottom: none;
}

.peec-chat-table__query {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.peec-chat-table__hit {
  font-weight: 600;
}

.peec-chat-table__action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.inline-btn {
  min-height: 24px;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #ffffff;
  color: #374151;
  font-size: 11px;
  padding: 0 8px;
}

.inline-btn:hover {
  border-color: #111827;
  color: #111827;
}

.metric-card {
  display: grid;
  align-content: start;
  gap: 10px;
  min-width: 0;
  border: none;
}

.metric-card__head {
  min-height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.metric-card__body {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  padding: 10px;
  display: grid;
  align-content: start;
  gap: 8px;
  min-width: 0;
  overflow: hidden;
}

.sov-inline-head {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.info-hotspot {
  position: relative;
  width: 16px;
  height: 16px;
  border: 1px solid #9ca3af;
  border-radius: 999px;
  background: #ffffff;
  color: #4b5563;
  font-size: 10px;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: default;
  padding: 0;
}

.info-hotspot__tip {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  bottom: calc(100% + 8px);
  z-index: 10;
  width: max-content;
  max-width: 240px;
  border: 1px solid #d1d5db;
  border-radius: 0;
  background: #ffffff;
  color: #111827;
  padding: 12px 14px;
  white-space: normal;
  font-size: 12px;
  line-height: 1.6;
  text-align: left;
  box-shadow: none;
  opacity: 0;
  pointer-events: none;
}

.info-hotspot:hover .info-hotspot__tip {
  opacity: 1;
}

.sov-subcopy,
.avgpos-subcopy,
.topic-rank-subcopy {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: #6b7280;
}

.sov-kpi {
  font-size: 34px;
  line-height: 1;
  letter-spacing: -0.02em;
  font-weight: 650;
}

.topic-rank-section {
  gap: 8px;
  border: none;
}

.topic-summary-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
  background: #ffffff;
}

.topic-summary-table__head,
.topic-summary-table__row {
  min-height: 42px;
  display: grid;
  grid-template-columns: minmax(150px, 0.9fr) 112px 112px 112px minmax(0, 1.4fr);
  align-items: center;
  gap: 10px;
  padding: 0 10px;
  font-size: 12px;
}

.topic-summary-table__head {
  background: #f3f4f6;
  border-bottom: 1px solid #d1d5db;
  color: #6b7280;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.topic-summary-table__row {
  border-bottom: 1px solid #e5e7eb;
}

.topic-summary-table__row:last-child {
  border-bottom: none;
}

.topic-summary-table__topic {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.topic-summary-table__topic strong {
  font-size: 12px;
  font-weight: 600;
  color: #111827;
}

.topic-summary-table__topic small {
  font-size: 11px;
  color: #6b7280;
}

.topic-summary-table__question {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #374151;
}

.topic-rank-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
  background: #ffffff;
}

.topic-rank-table__head,
.topic-rank-row {
  display: grid;
  grid-template-columns: minmax(220px, 2.4fr) repeat(10, minmax(0, 1fr));
  align-items: center;
  min-height: 44px;
  column-gap: 0;
  padding: 0 10px;
}

.topic-rank-table__head {
  background: #f3f4f6;
  border-bottom: 1px solid #d1d5db;
  color: #6b7280;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.topic-rank-row {
  border-bottom: 1px solid #e5e7eb;
}

.topic-rank-row--parent {
  cursor: pointer;
}

.topic-rank-row--parent:hover {
  background: #f8fafc;
}

.topic-rank-row--expanded {
  background: #f8fafc;
}

.topic-rank-row:last-child {
  border-bottom: none;
}

.topic-rank-detail-group {
  background: #fbfcfe;
}

.topic-rank-row--detail {
  min-height: 40px;
}

.topic-rank-table__head > span:not(.topic-rank-topic) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: #6b7280;
}

.topic-rank-topic {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-size: 12px;
  color: #1f2937;
  padding-right: 10px;
  border-right: 1px solid #e5e7eb;
}

.topic-rank-topic--head {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #6b7280;
}

.topic-rank-topic--button {
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  font: inherit;
  padding: 0;
  margin: 0;
}

.topic-rank-topic--detail {
  padding-left: 22px;
  font-size: 12px;
  color: #4b5563;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.topic-rank-topic__chevron {
  color: #9ca3af;
  font-size: 12px;
  line-height: 1;
  transition: transform 150ms ease;
}

.topic-rank-topic__chevron--open {
  transform: rotate(90deg);
}

.topic-rank-cell {
  min-height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-left: 1px solid rgba(229, 231, 235, 0.55);
}

.topic-rank-row .topic-rank-cell:first-of-type {
  border-left: none;
}

.topic-rank-cell--detail {
  position: relative;
}

.topic-rank-cell-inner {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.topic-rank-badge {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.38);
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  letter-spacing: 0.02em;
}

.topic-rank-badge--interactive {
  position: relative;
  border: none;
  cursor: pointer;
  transition: transform 120ms ease, box-shadow 120ms ease;
}

.topic-rank-badge--interactive:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 12px -8px rgba(15, 23, 42, 0.7);
}

.topic-rank-badge--placeholder {
  cursor: default;
  opacity: 0.56;
}

.topic-rank-badge__tooltip {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  bottom: calc(100% + 10px);
  z-index: 10;
  min-width: 112px;
  border: 1px solid #d1d5db;
  border-radius: 0;
  background: #ffffff;
  color: #111827;
  font-size: 11px;
  line-height: 1.5;
  text-align: left;
  padding: 10px 12px;
  opacity: 0;
  pointer-events: none;
  white-space: nowrap;
  box-shadow: none;
}

.topic-rank-badge--interactive:hover .topic-rank-badge__tooltip {
  opacity: 1;
}

.topic-rank-analyze {
  position: absolute;
  top: 50%;
  right: calc(100% + 8px);
  transform: translateY(-50%);
  min-height: 28px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #ffffff;
  color: #374151;
  font-size: 12px;
  padding: 0 12px;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: opacity 120ms ease, transform 120ms ease;
}

.topic-rank-cell--detail:hover .topic-rank-analyze {
  opacity: 1;
  pointer-events: auto;
  transform: translate(-2px, -50%);
}

.btn-plain {
  min-height: 30px;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #ffffff;
  color: #374151;
  font-size: 12px;
  padding: 0 10px;
}

.btn-plain:hover {
  border-color: #111827;
  color: #111827;
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

.state-block--explained {
  padding: 18px 24px;
  gap: 8px;
  text-align: center;
  line-height: 1.7;
}

.state-block--explained strong {
  color: #111827;
  font-size: 13px;
}

.delta-up {
  color: #047857;
}

.delta-down {
  color: #b91c1c;
}

@media (max-width: 1480px) {
  .peec-overview-grid {
    grid-template-columns: 1fr;
  }

  .peec-sources-grid {
    grid-template-columns: 1fr;
  }

}

@media (max-width: 1200px) {
  .topic-summary-table__head,
  .topic-summary-table__row {
    grid-template-columns: minmax(150px, 1fr) 96px 96px 96px minmax(0, 1.2fr);
  }
}
</style>
