<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import {
  buildProductModelDisplayMap,
  resolveProductModelDisplayLabel
} from '~/utils/productModels'
import type {
  TopicInsightCadence,
  TopicInsightExecutionRow,
  TopicInsightMetric,
  TopicInsightOverlayPayload,
  TopicInsightTableMode,
  TopicInsightWindow
} from '~/types/engine'

const props = defineProps<{
  topicInsightOverlay: TopicInsightOverlayPayload | null
  topicInsightExecutionCount: number
  topicInsightWindow: TopicInsightWindow
  setTopicInsightWindow: (value: TopicInsightWindow) => void
  topicInsightCadence: TopicInsightCadence
  setTopicInsightCadence: (value: TopicInsightCadence) => void
  topicInsightPlatformFilter: string
  setTopicInsightPlatformFilter: (value: string) => void
  platformOptions: string[]
  topicInsightMetric: TopicInsightMetric
  setTopicInsightMetric: (value: TopicInsightMetric) => void
  topicInsightMetricLabel: string
  topicInsightMetricValue: string
  topicInsightTrendOption: EChartsOption | null
  topicInsightPlatformOption: EChartsOption | null
  topicInsightTableMode: TopicInsightTableMode
  setTopicInsightTableMode: (value: TopicInsightTableMode) => void
  topicInsightTableRows: TopicInsightExecutionRow[]
  closeTopicInsight: () => void
}>()

const platformDisplayMap = computed(() =>
  buildProductModelDisplayMap((props.topicInsightTableRows || []).map(row => row.platform))
)
</script>

<template>
  <div
    v-if="props.topicInsightOverlay"
    class="insight-overlay"
    @click.self="props.closeTopicInsight"
  >
    <article class="insight-overlay__panel">
      <button
        type="button"
        class="insight-overlay__close"
        @click="props.closeTopicInsight"
      >
        ×
      </button>

      <header class="insight-modal-head">
        <p class="insight-modal-head__label">
          探针
        </p>
        <h2 class="insight-modal-head__title">
          {{ props.topicInsightOverlay.question }}
        </h2>
        <p class="insight-modal-head__meta">
          本周期执行 {{ props.topicInsightExecutionCount }} 次
        </p>
      </header>

      <section class="insight-modal-toolbar">
        <div class="insight-modal-toolbar__left">
          <select
            :value="props.topicInsightWindow"
            class="select-input filter-select filter-select--narrow"
            @change="props.setTopicInsightWindow(($event.target as HTMLSelectElement).value as TopicInsightWindow)"
          >
            <option value="today">
              今日
            </option>
            <option value="7d">
              近 7 天
            </option>
          </select>
          <select
            :value="props.topicInsightCadence"
            class="select-input filter-select filter-select--narrow"
            @change="props.setTopicInsightCadence(($event.target as HTMLSelectElement).value as TopicInsightCadence)"
          >
            <option value="daily">
              日
            </option>
            <option value="weekly">
              周
            </option>
          </select>
        </div>
        <div class="insight-modal-toolbar__right">
          <label class="insight-modal-platform">
            <span>◫</span>
            <select
              :value="props.topicInsightPlatformFilter"
              class="select-input filter-select filter-select--narrow"
              @change="props.setTopicInsightPlatformFilter(($event.target as HTMLSelectElement).value)"
            >
              <option value="all">平台</option>
              <option
                v-for="platform in props.platformOptions"
                :key="`overlay-platform-${platform}`"
                :value="platform"
              >
                {{ platform }}
              </option>
            </select>
          </label>
        </div>
      </section>

      <nav class="insight-modal-tabs">
        <button
          type="button"
          class="insight-modal-tabs__item"
          :class="{ 'insight-modal-tabs__item--active': props.topicInsightMetric === 'visibility' }"
          @click="props.setTopicInsightMetric('visibility')"
        >
          可见度
        </button>
        <button
          type="button"
          class="insight-modal-tabs__item"
          :class="{ 'insight-modal-tabs__item--active': props.topicInsightMetric === 'avg-position' }"
          @click="props.setTopicInsightMetric('avg-position')"
        >
          平均位次
        </button>
        <button
          type="button"
          class="insight-modal-tabs__item"
          :class="{ 'insight-modal-tabs__item--active': props.topicInsightMetric === 'citation-share' }"
          @click="props.setTopicInsightMetric('citation-share')"
        >
          引用占比
        </button>
      </nav>

      <section class="insight-modal-charts">
        <article class="insight-chart-card">
          <div class="insight-chart-card__head">
            <span>{{ props.topicInsightMetricLabel }}</span>
            <strong>{{ props.topicInsightMetricValue }}</strong>
          </div>
          <GeoLazyEChart
            :option="props.topicInsightTrendOption"
            :height="236"
            empty-text="暂无趋势数据"
          />
        </article>

        <article class="insight-chart-card">
          <div class="insight-chart-card__head">
            <span>{{ props.topicInsightMetricLabel }}（按平台）</span>
          </div>
          <GeoLazyEChart
            :option="props.topicInsightPlatformOption"
            :height="236"
            empty-text="暂无平台数据"
          />
        </article>
      </section>

      <section class="insight-modal-table">
        <div class="insight-modal-table__tabs">
          <button
            type="button"
            class="insight-modal-table__tab"
            :class="{ 'insight-modal-table__tab--active': props.topicInsightTableMode === 'executions' }"
            @click="props.setTopicInsightTableMode('executions')"
          >
            执行记录
          </button>
          <button
            type="button"
            class="insight-modal-table__tab"
            :class="{ 'insight-modal-table__tab--active': props.topicInsightTableMode === 'citations' }"
            @click="props.setTopicInsightTableMode('citations')"
          >
            引用来源
          </button>
        </div>

        <div class="insight-modal-table__grid">
          <div class="insight-modal-table__head">
            <span>日期</span>
            <span>是否提及</span>
            <span>位次</span>
            <span>全部提及</span>
            <span>回答</span>
            <span>平台</span>
          </div>

          <div
            v-for="row in props.topicInsightTableRows"
            :key="row.id"
            class="insight-modal-table__row"
          >
            <span>{{ row.date }}</span>
            <span :class="row.mentioned ? 'insight-yes' : 'insight-no'">{{ row.mentioned ? '是' : '否' }}</span>
            <span>#{{ row.position }}</span>
            <span>{{ row.allMentions }}</span>
            <span class="insight-modal-table__response">{{ row.response }}</span>
            <span>{{ resolveProductModelDisplayLabel(row.platform, platformDisplayMap) }}</span>
          </div>
        </div>
      </section>
    </article>
  </div>
</template>

<style scoped>
.insight-overlay {
  position: fixed;
  inset: 0;
  z-index: 120;
  display: grid;
  place-items: center;
  background: rgba(15, 23, 42, 0.2);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  padding: 18px;
}

.insight-overlay__panel {
  width: min(1280px, calc(100vw - 36px));
  max-height: calc(100vh - 36px);
  overflow: auto;
  border: 1px solid #d1d5db;
  background: #ffffff;
  box-shadow: 0 36px 44px -30px rgba(15, 23, 42, 0.48);
  padding: 16px 24px 20px;
  display: grid;
  gap: 14px;
}

.insight-overlay__close {
  justify-self: end;
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: #9ca3af;
  font-size: 20px;
  line-height: 1;
  padding: 0;
  cursor: pointer;
}

.insight-overlay__close:hover {
  color: #374151;
}

.insight-modal-head {
  display: grid;
  gap: 6px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e5e7eb;
}

.insight-modal-head__label {
  margin: 0;
  font-size: 12px;
  color: #6b7280;
}

.insight-modal-head__title {
  margin: 0;
  font-size: 38px;
  font-weight: 700;
  line-height: 1.12;
  letter-spacing: -0.02em;
  color: #111827;
  max-width: 1120px;
}

.insight-modal-head__meta {
  margin: 0;
  font-size: 12px;
  color: #9ca3af;
}

.insight-modal-toolbar {
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.insight-modal-toolbar__left,
.insight-modal-toolbar__right {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.insight-modal-platform {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #6b7280;
  font-size: 12px;
}

.insight-modal-tabs {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 16px;
  border-bottom: 1px solid #e5e7eb;
}

.insight-modal-tabs__item {
  min-height: 34px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: #6b7280;
  font-size: 12px;
  padding: 0 1px;
  cursor: pointer;
}

.insight-modal-tabs__item--active {
  color: #111827;
  border-bottom-color: #111827;
}

.insight-modal-charts {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 10px;
}

.insight-chart-card {
  min-height: 320px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 12px;
  display: grid;
  align-content: start;
  gap: 8px;
}

.insight-chart-card__head {
  display: grid;
  gap: 2px;
}

.insight-chart-card__head span {
  font-size: 12px;
  color: #6b7280;
}

.insight-chart-card__head strong {
  font-size: 34px;
  line-height: 1;
  letter-spacing: -0.02em;
  color: #111827;
}

.insight-modal-table {
  display: grid;
  gap: 8px;
}

.insight-modal-table__tabs {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid #e5e7eb;
}

.insight-modal-table__tab {
  min-height: 34px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: #6b7280;
  font-size: 12px;
  padding: 0 1px;
  cursor: pointer;
}

.insight-modal-table__tab--active {
  border-bottom-color: #111827;
  color: #111827;
}

.insight-modal-table__grid {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  overflow: hidden;
}

.insight-modal-table__head,
.insight-modal-table__row {
  display: grid;
  grid-template-columns: 120px 120px 92px 178px minmax(0, 1fr) 120px;
  align-items: center;
  column-gap: 10px;
  min-height: 42px;
  padding: 0 12px;
}

.insight-modal-table__head {
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
  color: #6b7280;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.insight-modal-table__row {
  border-bottom: 1px solid #f1f5f9;
  color: #374151;
  font-size: 12px;
}

.insight-modal-table__row:last-child {
  border-bottom: none;
}

.insight-modal-table__response {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.insight-yes {
  color: #047857;
  font-weight: 600;
}

.insight-no {
  color: #9ca3af;
}

.select-input {
  min-height: 32px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #ffffff;
  color: #111827;
  font-size: 12px;
  padding: 0 10px;
}

.filter-select--narrow {
  min-width: 120px;
}
</style>
