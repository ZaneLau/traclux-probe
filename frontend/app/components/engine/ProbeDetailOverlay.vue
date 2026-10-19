<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import BrandRankCard from '~/components/engine/BrandRankCard.vue'
import SourcesTable from '~/components/engine/SourcesTable.vue'
import type { BrandTrackingSeed } from '~/types/engine'

type ProbeStatus = 'active' | 'suggested' | 'inactive'

interface ProbeOverlayItem {
  id: string
  promptId?: string | null
  topic: string
  prompt: string
  status: ProbeStatus
  visibilityScore?: number | null
  visibilityTrend?: number[] | null
  evidenceCount?: number | null
  mentionCount?: number | null
  createdAt?: string | null
}

interface ChatOverlayItem {
  id: string
  probeId: string
  model?: string | null
  query?: string | null
  createdAt?: string | null
  citations?: Array<{
    id: string
    title: string
    url: string
    domain?: string | null
  }> | null
  refs?: {
    probeId?: string | null
  } | null
}

interface ProbeScopedSourceRow {
  id: string
  domain: string
  type: 'editorial' | 'corporate' | 'ugc' | 'reference' | 'you' | 'unknown'
  used: number
  avgCitations?: number | null
  share?: number | null
  delta?: number | null
  url?: string | null
}

const props = defineProps<{
  modelValue: string | null
  probes: ProbeOverlayItem[]
  chats: ChatOverlayItem[]
  brandRanks: BrandTrackingSeed[]
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: string | null): void
  (event: 'openChat', chatId: string): void
}>()

const currentProbe = computed(() => {
  if (!props.modelValue) {
    return null
  }
  return props.probes.find(item => item.id === props.modelValue) || null
})

const probeChats = computed(() => {
  if (!currentProbe.value) {
    return []
  }
  return props.chats
    .filter(item => (item.refs?.probeId || item.probeId) === currentProbe.value?.id)
    .slice(0, 8)
})

const trendOption = computed<EChartsOption | null>(() => {
  if (!currentProbe.value) {
    return null
  }

  const labels = ['W1', 'W2', 'W3', 'W4', 'W5', 'W6', 'W7', 'W8']
  const values = currentProbe.value.visibilityTrend || []
  if (!values.length) {
    return null
  }

  return {
    grid: { top: 20, right: 14, bottom: 30, left: 34, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#111827',
      borderColor: '#111827',
      textStyle: { color: '#f9fafb', fontSize: 11 }
    },
    xAxis: {
      type: 'category',
      data: labels,
      boundaryGap: false,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#6b7280', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#9ca3af', fontSize: 11, formatter: '{value}%' },
      splitLine: { lineStyle: { color: '#f3f4f6' } }
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { width: 2, color: '#1f2937' },
        itemStyle: { color: '#1f2937' },
        areaStyle: { color: 'rgba(17,24,39,0.04)' },
        data: values
      }
    ]
  }
})

const rankedBrands = computed(() => {
  return props.brandRanks
    .filter(item => item.status !== 'rejected')
    .map(item => ({
      brand: item.brand,
      visibility: item.visibility,
      sentiment: item.sentiment,
      position: item.position
    }))
    .sort((a, b) => b.visibility - a.visibility)
    .slice(0, 6)
    .map((item, index) => ({
      rank: index + 1,
      ...item
    }))
})

function resolveDomainFromUrl(url?: string | null) {
  if (!url) {
    return ''
  }
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return ''
  }
}

const probeCitationCount = computed(() =>
  probeChats.value.reduce((sum, chat) => sum + ((chat.citations || []).length), 0)
)

const probeScopedSourceRows = computed(() => {
  const domainMap = new Map<string, ProbeScopedSourceRow>()

  for (const chat of probeChats.value) {
    for (const citation of chat.citations || []) {
      const domain = (citation.domain || resolveDomainFromUrl(citation.url)).trim()
      if (!domain) {
        continue
      }
      const current = domainMap.get(domain) || {
        id: `probe-source-${domain}`,
        domain,
        type: 'unknown',
        used: 0,
        avgCitations: 0,
        share: 0,
        delta: null,
        url: citation.url || ''
      }
      current.used += 1
      current.avgCitations = current.used
      if (!current.url && citation.url) {
        current.url = citation.url
      }
      domainMap.set(domain, current)
    }
  }

  const total = Array.from(domainMap.values()).reduce((sum, item) => sum + item.used, 0)
  return Array.from(domainMap.values())
    .map(item => ({
      ...item,
      share: total > 0 ? Math.round((item.used / total) * 1000) / 10 : 0
    }))
    .sort((left, right) => right.used - left.used)
})

const sourceRows = computed(() => {
  return probeScopedSourceRows.value.map(item => ({
      id: item.id,
      label: item.domain,
      type: item.type,
      used: item.used,
      avgCitations: item.avgCitations ?? item.used,
      share: item.share ?? 0,
      delta: item.delta ?? null,
      url: item.url || undefined
    }))
})

const probeSourceEmptyText = computed(() => {
  if (probeChats.value.length) {
    return '这条探针最近的执行已记录，但没有挂载可展开的引用证据。'
  }
  return '这条探针暂时还没有最近执行记录。'
})

const probeChatEmptyText = computed(() => '当前还没有与这条探针绑定的最近执行记录。')

const trendEmptyText = computed(() => {
  if (currentProbe.value?.createdAt) {
    return '这条探针已入链，但当前还没有可展示的趋势切片。'
  }
  return '暂无趋势数据'
})

const hasBrandRanks = computed(() => rankedBrands.value.length > 0)

function closeOverlay() {
  emit('update:modelValue', null)
}

function openChat(chatId: string) {
  emit('openChat', chatId)
}

function formatTime(value?: string | null) {
  if (!value) {
    return '未记录时间'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return `${date.getFullYear()}/${String(date.getMonth() + 1).padStart(2, '0')}/${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function resolveChatPlatformLabel(value?: string | null) {
  return typeof value === 'string' && value.trim() ? value.trim() : '未记录平台'
}

function resolveStatusLabel(status: ProbeStatus) {
  if (status === 'active') {
    return '已启用'
  }
  if (status === 'inactive') {
    return '已停用'
  }
  return '建议启用'
}

function formatVisibility(value?: number | null) {
  if (value == null) {
    return '—'
  }
  return `${value.toFixed(1)}%`
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="currentProbe"
      class="probe-overlay"
      @click.self="closeOverlay"
    >
      <article class="probe-overlay__panel">
        <header class="probe-overlay__head">
          <div class="probe-overlay__meta">
            <strong class="probe-overlay__eyebrow">探针详情</strong>
            <h2>{{ currentProbe.prompt }}</h2>
            <div class="probe-overlay__meta-line">
              <span class="probe-overlay__topic-chip">{{ currentProbe.topic }}</span>
              <span
                class="probe-overlay__status-chip"
                :class="`probe-overlay__status-chip--${currentProbe.status}`"
              >
                {{ resolveStatusLabel(currentProbe.status) }}
              </span>
              <span>{{ probeChats.length }} 次执行</span>
              <span>{{ probeCitationCount }} 条证据</span>
            </div>
          </div>
          <div class="probe-overlay__actions">
            <button
              type="button"
              class="inline-mini"
              @click="closeOverlay"
            >
              关闭
            </button>
          </div>
        </header>

        <section
          class="probe-overlay__body"
          :class="{ 'probe-overlay__body--single': !hasBrandRanks }"
        >
          <article class="surface section-surface probe-overlay__trend">
            <div class="surface-head surface-head--compact">
              <strong>可见度趋势</strong>
              <span>{{ formatVisibility(currentProbe.visibilityScore) }}</span>
            </div>
            <GeoLazyEChart
              v-if="trendOption"
              :option="trendOption"
              :height="280"
            />
            <div
              v-else
              class="probe-overlay__empty probe-overlay__empty--compact"
            >
              {{ trendEmptyText }}
            </div>
          </article>

          <BrandRankCard
            v-if="hasBrandRanks"
            class="probe-overlay__rank"
            title="品牌排名"
            :rows="rankedBrands"
          />

          <article class="surface section-surface probe-overlay__sources">
            <div class="surface-head surface-head--compact">
              <strong>高占比引用域名</strong>
              <span>{{ sourceRows.length }}</span>
            </div>
            <SourcesTable
              :rows="sourceRows"
              :empty-text="probeSourceEmptyText"
            />
          </article>

          <article class="surface section-surface probe-overlay__chats">
            <div class="surface-head surface-head--compact">
              <strong>最近问答记录</strong>
              <span>{{ probeChats.length }}</span>
            </div>

            <div
              v-if="!probeChats.length"
              class="probe-overlay__empty"
            >
              {{ probeChatEmptyText }}
            </div>

            <div
              v-else
              class="probe-chat-table"
            >
              <div class="probe-chat-table__head">
                <span>时间</span>
                <span>平台</span>
                <span>问题</span>
                <span>动作</span>
              </div>
              <button
                v-for="chat in probeChats"
                :key="`probe-chat-${chat.id}`"
                type="button"
                class="probe-chat-table__row"
                @click="openChat(chat.id)"
              >
                <span>{{ formatTime(chat.createdAt) }}</span>
                <span>{{ resolveChatPlatformLabel(chat.model) }}</span>
                <span class="probe-chat-table__query">{{ chat.query || '未记录查询' }}</span>
                <span class="probe-chat-table__action">查看证据链</span>
              </button>
            </div>
          </article>
        </section>
      </article>
    </div>
  </Teleport>
</template>

<style scoped>
.probe-overlay {
  position: fixed;
  inset: 0;
  z-index: 110;
  background: rgba(15, 23, 42, 0.52);
  padding: 28px;
  display: grid;
  place-items: center;
}

.probe-overlay__panel {
  border: 1px solid #d1d5db;
  border-radius: 18px;
  background: #ffffff;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-height: 0;
  width: min(1220px, 100%);
  max-height: min(900px, calc(100vh - 56px));
  box-shadow: 0 28px 80px -44px rgba(15, 23, 42, 0.7);
}

.probe-overlay__head {
  min-height: 82px;
  border-bottom: 1px solid #e5e7eb;
  padding: 16px 22px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.probe-overlay__meta {
  min-width: 0;
  display: grid;
  gap: 8px;
}

.probe-overlay__eyebrow {
  font-size: 12px;
  line-height: 1;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.probe-overlay__meta h2 {
  margin: 0;
  font-size: 24px;
  line-height: 1.38;
  letter-spacing: -0.02em;
  color: #0f172a;
}

.probe-overlay__meta-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #6b7280;
}

.probe-overlay__topic-chip,
.probe-overlay__status-chip {
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  font-weight: 600;
}

.probe-overlay__topic-chip {
  background: #eef2ff;
  color: #4338ca;
}

.probe-overlay__status-chip--active {
  background: #ecfdf5;
  color: #047857;
}

.probe-overlay__status-chip--suggested {
  background: #eff6ff;
  color: #1d4ed8;
}

.probe-overlay__status-chip--inactive {
  background: #f3f4f6;
  color: #4b5563;
}

.probe-overlay__actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.probe-overlay__body {
  min-height: 0;
  overflow: auto;
  padding: 18px;
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
}

.probe-overlay__body--single {
  grid-template-columns: minmax(0, 1fr);
}

.probe-overlay__trend,
.probe-overlay__sources,
.probe-overlay__chats {
  border: 1px solid #d1d5db;
  border-radius: 16px;
  background: #ffffff;
  padding: 14px;
  display: grid;
  gap: 12px;
}

.probe-overlay__rank {
  align-self: start;
}

.probe-overlay__sources,
.probe-overlay__chats {
  grid-column: 1 / -1;
}

.probe-overlay__empty {
  min-height: 112px;
  border: 1px dashed #cbd5e1;
  border-radius: 14px;
  background: #f8fafc;
  display: grid;
  place-items: center;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
  text-align: center;
  padding: 14px;
}

.probe-overlay__empty--compact {
  min-height: 180px;
}

.probe-chat-table {
  border: 1px solid #d1d5db;
  border-radius: 14px;
  overflow: hidden;
}

.probe-chat-table__head,
.probe-chat-table__row {
  min-height: 46px;
  display: grid;
  align-items: center;
  grid-template-columns: 158px 112px minmax(0, 1fr) 94px;
  gap: 8px;
  padding: 0 14px;
  font-size: 12px;
  text-align: left;
}

.probe-chat-table__head {
  background: #f8fafc;
  border-bottom: 1px solid #d1d5db;
  color: #6b7280;
  font-weight: 600;
}

.probe-chat-table__row {
  width: 100%;
  border: none;
  border-bottom: 1px solid #e5e7eb;
  background: #ffffff;
  color: #111827;
  cursor: pointer;
  transition: background 140ms ease;
}

.probe-chat-table__row:last-child {
  border-bottom: none;
}

.probe-chat-table__row:hover {
  background: #f8fafc;
}

.probe-chat-table__query {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #1f2937;
}

.probe-chat-table__action {
  color: #1d4ed8;
  font-weight: 600;
}

.inline-mini {
  min-height: 34px;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  background: #ffffff;
  color: #4b5563;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 600;
}

.inline-mini:hover {
  border-color: #111827;
  color: #111827;
}
</style>
