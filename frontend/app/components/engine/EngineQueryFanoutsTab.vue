<script setup lang="ts">
import { computed, ref } from 'vue'
import type { EChartsOption, TooltipComponentFormatterCallbackParams } from 'echarts'
import { buildSeed, clamp, round1 } from '~/composables/engine/workbench/math'
import { resolveProductModelLabel } from '~/utils/productModels'

interface FanoutVariationRow {
  id: string
  prompt: string
  queryCount: number
  delta: number
}

interface FanoutPromptRow {
  id: string
  topic: string
  prompt: string
  queryCount: number
  avgQueriesPerExecution: number
  variations: FanoutVariationRow[]
}

interface FanoutPlatformRow {
  rank: number
  platform: string
  avgQueriesPerExecution: number
  delta: number
}

interface FanoutPlatformInsightRow {
  platform: string
  queryCount: number
  share: number
}

const props = defineProps<{
  hasCoverage: boolean
  selectedBrand: string
  selectedTopics: string[]
  selectedPlatforms: string[]
  promptTopicPromptBank: Record<string, string[]>
}>()

const fanoutPromptSearch = ref('')
const expandedPromptIds = ref<string[]>([])
const hoveredPromptId = ref<string | null>(null)
const focusedPromptId = ref<string | null>(null)

function shortenPrompt(value: string) {
  return value
    .replace(/[？?。.!！]+$/u, '')
    .trim()
}

function buildVariationPrompt(base: string, topic: string, brand: string, index: number) {
  const clean = shortenPrompt(base)
  const templates = [
    `${clean}，在高频使用场景下最容易出现的分歧是什么`,
    `${clean}，与同价位竞品相比差异点主要体现在哪`,
    `${clean}，若预算受限应优先关注哪些判断条件`
  ]
  const preset = templates[index]
  if (preset) {
    return preset
  }
  return `${brand} 在「${topic}」主题下还会衍生出哪些关键追问`
}

function togglePromptRow(id: string) {
  if (expandedPromptIds.value.includes(id)) {
    expandedPromptIds.value = expandedPromptIds.value.filter(item => item !== id)
    if (focusedPromptId.value === id) {
      focusedPromptId.value = null
    }
    return
  }
  expandedPromptIds.value = [...expandedPromptIds.value, id]
  focusedPromptId.value = id
}

function isPromptExpanded(id: string) {
  return expandedPromptIds.value.includes(id)
}

function expandAndFocusPrompt(id: string) {
  if (!expandedPromptIds.value.includes(id)) {
    expandedPromptIds.value = [...expandedPromptIds.value, id]
  }
  hoveredPromptId.value = id
  focusedPromptId.value = id
}

function formatFanoutDelta(value: number) {
  const rounded = Math.abs(value - Math.round(value)) < 0.05 ? Math.round(value).toString() : value.toFixed(1)
  return `${value >= 0 ? '+' : ''}${rounded}`
}

const topicOrder = computed(() => {
  const availableTopicEntries = Object.entries(props.promptTopicPromptBank)
    .filter(([, prompts]) => Array.isArray(prompts) && prompts.length > 0)
    .map(([topic]) => topic)

  if (props.selectedTopics.length) {
    const validSelected = props.selectedTopics.filter(topic => availableTopicEntries.includes(topic))
    if (validSelected.length) {
      return validSelected
    }
  }
  return availableTopicEntries.slice(0, 2)
})

const hasFanoutSeed = computed(() => topicOrder.value.length > 0)

const fanoutPromptRows = computed<FanoutPromptRow[]>(() => {
  const rows: FanoutPromptRow[] = []

  topicOrder.value.forEach((topic, topicIndex) => {
    const prompts = props.promptTopicPromptBank[topic] || []

    prompts.slice(0, 4).forEach((prompt, promptIndex) => {
      const seed = buildSeed([
        props.selectedBrand,
        topic,
        prompt,
        props.selectedPlatforms.join('|'),
        String(topicIndex),
        String(promptIndex)
      ])

      const queryCount = clamp(4 + (seed % 5), 4, 8)
      const avgQueriesPerExecution = round1(
        clamp(1.8 + queryCount * 0.24 + Math.sin(seed * 0.00073 + promptIndex * 0.39) * 0.4, 1.4, 4.2)
      )

      const variationSize = 3
      const variations: FanoutVariationRow[] = Array.from({ length: variationSize }, (_, variationIndex) => {
        const variationSeed = buildSeed([
          String(seed),
          topic,
          prompt,
          String(variationIndex)
        ])
        const delta = round1(clamp(Math.cos(variationSeed * 0.00087 + variationIndex * 0.58) * 2.7, -1.9, 3.6))
        const count = clamp(
          Math.max(1, Math.round(queryCount / variationSize) + ((variationSeed + variationIndex) % 2)),
          1,
          6
        )

        return {
          id: `${topic}-${promptIndex}-${variationIndex}`,
          prompt: buildVariationPrompt(prompt, topic, props.selectedBrand, variationIndex),
          queryCount: count,
          delta
        }
      })

      rows.push({
        id: `${topic}-${promptIndex}`,
        topic,
        prompt,
        queryCount,
        avgQueriesPerExecution,
        variations
      })
    })
  })

  return rows
})

const filteredFanoutPromptRows = computed(() => {
  const keyword = fanoutPromptSearch.value.trim().toLowerCase()
  if (!keyword) {
    return fanoutPromptRows.value
  }

  return fanoutPromptRows.value.filter((row) => {
    const haystack = `${row.prompt} ${row.topic} ${row.variations.map(item => item.prompt).join(' ')}`.toLowerCase()
    return haystack.includes(keyword)
  })
})

const focusedPromptRow = computed<FanoutPromptRow | null>(() => {
  if (!focusedPromptId.value) {
    return null
  }
  return fanoutPromptRows.value.find(row => row.id === focusedPromptId.value) || null
})

const focusedPlatformInsightRows = computed<FanoutPlatformInsightRow[]>(() => {
  const focused = focusedPromptRow.value
  if (!focused) {
    return []
  }

  const platforms = props.selectedPlatforms.length ? props.selectedPlatforms : []
  const seed = buildSeed([
    props.selectedBrand,
    focused.topic,
    focused.prompt,
    platforms.join('|')
  ])

  const raw = platforms.map((platform, index) => {
    const ratio = clamp(0.26 + Math.sin(seed * 0.00077 + index * 0.78) * 0.12 + index * 0.04, 0.14, 0.54)
    const queryCount = clamp(Math.round(focused.queryCount * ratio + 1), 1, focused.queryCount + 2)
    return { platform, queryCount }
  })
  const total = raw.reduce((sum, item) => sum + item.queryCount, 0)

  return raw
    .map(item => ({
      platform: resolveProductModelLabel(item.platform),
      queryCount: item.queryCount,
      share: total > 0 ? round1((item.queryCount / total) * 100) : 0
    }))
    .sort((a, b) => b.queryCount - a.queryCount)
})

const fanoutByPlatformRows = computed<FanoutPlatformRow[]>(() => {
  const platforms = props.selectedPlatforms.length ? props.selectedPlatforms : []
  const baseline = fanoutPromptRows.value.length
    ? fanoutPromptRows.value.reduce((sum, row) => sum + row.avgQueriesPerExecution, 0) / fanoutPromptRows.value.length
    : 2.6
  const seed = buildSeed([
    props.selectedBrand,
    topicOrder.value.join('|'),
    platforms.join('|')
  ])

  const rows = platforms.map((platform, index) => {
    const value = round1(
      clamp(
        baseline + Math.cos(seed * 0.00093 + index * 0.74) * 0.5 + (platforms.length - index) * 0.08,
        1.2,
        4.3
      )
    )
    const delta = round1(clamp(Math.sin(seed * 0.0011 + index * 0.57) * 2.6, -2.6, 3.4))

    return {
      rank: 0,
      platform: resolveProductModelLabel(platform),
      avgQueriesPerExecution: value,
      delta
    }
  })

  return rows
    .sort((a, b) => b.avgQueriesPerExecution - a.avgQueriesPerExecution)
    .map((row, index) => ({
      ...row,
      rank: index + 1
    }))
})

const avgFanoutPerExecution = computed(() => {
  if (!fanoutByPlatformRows.value.length) {
    return null
  }
  return fanoutByPlatformRows.value[0]?.avgQueriesPerExecution ?? null
})

const fanoutPerExecutionOption = computed<EChartsOption | null>(() => {
  if (!props.hasCoverage || !fanoutByPlatformRows.value.length) {
    return null
  }

  const values = fanoutByPlatformRows.value.map(row => row.avgQueriesPerExecution)
  const labels = fanoutByPlatformRows.value.map(row => row.platform)
  const ymax = round1(Math.max(3.2, Math.max(...values) + 0.6))

  return {
    backgroundColor: 'transparent',
    grid: {
      left: 34,
      right: 16,
      top: 22,
      bottom: 30,
      containLabel: true
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: '#111827',
      borderColor: '#1f2937',
      textStyle: { color: '#f9fafb' },
      formatter: (params: TooltipComponentFormatterCallbackParams) => {
        const item = Array.isArray(params) ? params[0] : params
        if (!item) {
          return ''
        }
        const row = fanoutByPlatformRows.value[item.dataIndex]
        if (!row) {
          return ''
        }
        return `${row.platform}<br/>平均扩展数：${row.avgQueriesPerExecution.toFixed(1)}`
      }
    },
    xAxis: {
      type: 'category',
      data: labels,
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#6b7280', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: ymax,
      axisLine: { show: false },
      axisLabel: { color: '#6b7280', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f3f4f6' } }
    },
    series: [
      {
        type: 'bar',
        barWidth: 42,
        itemStyle: {
          color: '#111827',
          borderRadius: [2, 2, 0, 0]
        },
        data: values
      }
    ]
  }
})
</script>

<template>
  <section class="surface section-surface fanouts-module">
    <div class="metric-card__head">
      <div class="surface-head__title">
        <strong>问题扩展（按探针）</strong>
        <p class="module-subcopy">
          查看每条探针触发了哪些扩展问法，以及扩展规模。
        </p>
      </div>
      <label class="prompt-search fanouts-search">
        <span class="prompt-search__icon">⌕</span>
        <input
          v-model="fanoutPromptSearch"
          type="search"
          placeholder="搜索探针"
        >
      </label>
    </div>

    <div
      v-if="!props.hasCoverage"
      class="state-block"
    >
      至少选择 1 个主题和 1 个平台
    </div>
    <div
      v-else-if="!hasFanoutSeed"
      class="state-block"
    >
      当前品牌暂无探针数据，请先切换品牌或重置筛选
    </div>
    <div
      v-else-if="!filteredFanoutPromptRows.length"
      class="state-block"
    >
      未命中问题扩展数据，尝试清空搜索关键词
    </div>

    <div
      v-else
      class="fanouts-table"
    >
      <div class="fanouts-table__head fanouts-table__row">
        <span>探针</span>
        <span>扩展次数</span>
        <span>单次执行平均扩展</span>
      </div>

      <template
        v-for="row in filteredFanoutPromptRows"
        :key="`fanout-row-${row.id}`"
      >
        <div
          class="fanouts-table__row fanouts-table__row--parent"
          :class="{ 'fanouts-table__row--expanded': isPromptExpanded(row.id) }"
          @mouseenter="hoveredPromptId = row.id"
          @mouseleave="hoveredPromptId = null"
          @click="togglePromptRow(row.id)"
        >
          <div class="fanout-prompt-cell">
            <button
              type="button"
              class="fanout-prompt-toggle"
            >
              <span
                class="fanout-prompt-toggle__chevron"
                :class="{ 'fanout-prompt-toggle__chevron--open': isPromptExpanded(row.id) }"
              >›</span>
              <span class="fanout-prompt-toggle__text">{{ row.prompt }}</span>
              <span class="fanout-prompt-toggle__meta">{{ row.variations.length }} 条扩展问法</span>
            </button>

            <button
              v-if="hoveredPromptId === row.id"
              type="button"
              class="inline-mini fanout-analyze-btn"
              @click.stop="expandAndFocusPrompt(row.id)"
            >
              分析 ↗
            </button>
          </div>

          <span>{{ row.queryCount }}</span>
          <span>{{ row.avgQueriesPerExecution.toFixed(1) }}</span>
        </div>

        <div
          v-if="isPromptExpanded(row.id)"
          class="fanouts-table__group"
        >
          <div
            v-for="variation in row.variations"
            :key="`fanout-variation-${variation.id}`"
            class="fanouts-table__row fanouts-table__row--child"
          >
            <span class="fanout-variation-text">{{ variation.prompt }}</span>
            <span>
              {{ variation.queryCount }}
              <em :class="variation.delta >= 0 ? 'delta-up' : 'delta-down'">{{ formatFanoutDelta(variation.delta) }}</em>
            </span>
            <span class="fanout-cell-muted">-</span>
          </div>
        </div>
      </template>
    </div>

    <section
      v-if="focusedPromptRow"
      class="fanout-insight"
    >
      <header class="fanout-insight__head">
        <div class="surface-head__title">
          <strong>扩展洞察</strong>
          <p class="module-subcopy">
            {{ focusedPromptRow.prompt }}
          </p>
        </div>
        <button
          type="button"
          class="inline-mini"
          @click="focusedPromptId = null"
        >
          收起
        </button>
      </header>

      <div class="fanout-insight__grid">
        <article class="fanout-insight__card">
          <div class="fanout-insight__title">
            扩展问法明细
          </div>
          <div class="fanout-insight-list">
            <div
              v-for="variation in focusedPromptRow.variations"
              :key="`focused-variation-${variation.id}`"
              class="fanout-insight-list__row"
            >
              <span class="fanout-insight-list__text">{{ variation.prompt }}</span>
              <span class="fanout-insight-list__meta">
                {{ variation.queryCount }}
                <em :class="variation.delta >= 0 ? 'delta-up' : 'delta-down'">{{ formatFanoutDelta(variation.delta) }}</em>
              </span>
            </div>
          </div>
        </article>

        <aside class="fanout-insight__card">
          <div class="fanout-insight__title">
            平台扩展贡献
          </div>
          <div class="fanout-platform-list">
            <div
              v-for="platformRow in focusedPlatformInsightRows"
              :key="`focused-platform-${platformRow.platform}`"
              class="fanout-platform-list__row"
            >
              <div class="fanout-platform-list__meta">
                <span>{{ platformRow.platform }}</span>
                <span>{{ platformRow.queryCount }} 次</span>
              </div>
              <div class="fanout-platform-list__track">
                <div
                  class="fanout-platform-list__value"
                  :style="{ width: `${Math.max(platformRow.share, 4)}%` }"
                />
              </div>
              <span class="fanout-platform-list__share">{{ platformRow.share.toFixed(1) }}%</span>
            </div>
          </div>
        </aside>
      </div>
    </section>
  </section>

  <section class="surface section-surface fanouts-module">
    <div class="metric-card__head">
      <div class="surface-head__title">
        <strong>单次执行扩展规模</strong>
        <p class="module-subcopy">
          不同模型平台下，单次探针执行平均会扩展出多少追问。
        </p>
      </div>
      <button
        type="button"
        class="btn-plain"
      >
        图表配置
      </button>
    </div>

    <div
      v-if="!props.hasCoverage"
      class="state-block"
    >
      当前过滤组合暂无问题扩展数据
    </div>

    <div
      v-else
      class="hero-grid fanouts-hero-grid"
    >
      <article class="surface chart-surface fanouts-chart-surface">
        <div class="fanouts-kpi-head">
          <div>
            <span class="fanouts-kpi-title">单次执行平均扩展</span>
            <strong v-if="avgFanoutPerExecution != null">{{ avgFanoutPerExecution.toFixed(1) }}</strong>
            <strong v-else>-</strong>
          </div>
        </div>
        <GeoLazyEChart
          :option="fanoutPerExecutionOption"
          :height="312"
          empty-text="暂无平台扩展数据"
        />
      </article>

      <aside class="surface side-surface fanouts-rank-surface">
        <div class="surface-head surface-head--compact">
          <div class="surface-head__title">
            <span class="fanouts-rank-title">平台排名</span>
          </div>
        </div>

        <div class="fanouts-rank-table">
          <div class="fanouts-rank-table__head">
            <span />
            <span>平台</span>
            <span>单次执行平均扩展</span>
            <span />
          </div>
          <div
            v-for="row in fanoutByPlatformRows"
            :key="`fanout-platform-${row.platform}`"
            class="fanouts-rank-table__row"
          >
            <span>{{ row.rank }}.</span>
            <span>{{ row.platform }}</span>
            <span>{{ row.avgQueriesPerExecution.toFixed(1) }}</span>
            <span :class="row.delta >= 0 ? 'delta-up' : 'delta-down'">
              {{ formatFanoutDelta(row.delta) }}
            </span>
          </div>
        </div>
      </aside>
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

.metric-card__head {
  min-height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
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

.module-subcopy {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: #6b7280;
}

.prompt-search {
  min-width: 210px;
  min-height: 32px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #ffffff;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
}

.prompt-search__icon {
  color: #9ca3af;
  font-size: 12px;
}

.prompt-search input {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  color: #111827;
  font-size: 12px;
}

.prompt-search input::placeholder {
  color: #9ca3af;
}

.btn-plain,
.inline-mini {
  min-height: 30px;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #ffffff;
  color: #374151;
  font-size: 12px;
  padding: 0 10px;
}

.inline-mini {
  min-height: 24px;
  font-size: 11px;
  padding: 0 8px;
}

.btn-plain:hover,
.inline-mini:hover {
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

.fanouts-module {
  border: none;
  gap: 8px;
}

.fanouts-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
  background: #ffffff;
}

.fanouts-table__row {
  min-height: 44px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 160px 180px;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  font-size: 12px;
}

.fanouts-table__head {
  color: #6b7280;
  border-bottom: 1px solid #d1d5db;
  background: #f8fafc;
}

.fanouts-table__row--parent {
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  cursor: pointer;
}

.fanouts-table__row--parent:hover {
  background: #f9fafb;
}

.fanouts-table__row--expanded {
  background: #f9fafb;
}

.fanout-prompt-cell {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.fanout-prompt-toggle {
  min-width: 0;
  border: none;
  background: transparent;
  padding: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #111827;
  font-size: 13px;
  cursor: pointer;
}

.fanout-prompt-toggle__chevron {
  color: #6b7280;
  font-size: 14px;
  transform: translateY(-1px);
  transition: transform 140ms ease;
}

.fanout-prompt-toggle__chevron--open {
  transform: rotate(90deg) translateY(0);
}

.fanout-prompt-toggle__text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fanout-prompt-toggle__meta {
  color: #9ca3af;
  font-size: 11px;
  white-space: nowrap;
}

.fanout-analyze-btn {
  margin-left: auto;
}

.fanouts-table__group {
  border-bottom: 1px solid #e5e7eb;
}

.fanouts-table__row--child {
  min-height: 40px;
  padding-left: 30px;
  background: #ffffff;
  border-top: 1px solid #f3f4f6;
}

.fanout-variation-text {
  color: #374151;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.fanout-cell-muted {
  color: #9ca3af;
}

.delta-up {
  color: #059669;
}

.delta-down {
  color: #dc2626;
}

.fanouts-hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 560px;
  gap: 0;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
}

.fanouts-hero-grid > article,
.fanouts-hero-grid > aside {
  border: none;
}

.fanouts-hero-grid > aside {
  border-left: 1px solid #d1d5db;
}

.fanouts-kpi-title,
.fanouts-rank-title {
  color: #6b7280;
  font-size: 12px;
}

.fanouts-kpi-head strong {
  display: block;
  margin-top: 2px;
  font-size: 40px;
  line-height: 1;
  font-weight: 650;
  letter-spacing: -0.01em;
}

.fanouts-rank-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
}

.fanouts-rank-table__head,
.fanouts-rank-table__row {
  min-height: 42px;
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) 132px 76px;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  font-size: 12px;
}

.fanouts-rank-table__head {
  color: #6b7280;
  border-bottom: 1px solid #d1d5db;
}

.fanouts-rank-table__row + .fanouts-rank-table__row {
  border-top: 1px solid #e5e7eb;
}

@media (max-width: 1400px) {
  .fanouts-table__row {
    grid-template-columns: minmax(0, 1fr) 130px 150px;
  }
}
</style>
