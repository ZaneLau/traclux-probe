<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { resolveNavPageTitleByPath } from '~/composables/useDashboardNav'
import type { BrandSpaceRunHistoryView, RunStatus } from '~/types/brand-space'
import {
  type BrandSpaceRunDatePreset,
  resolveBrandSpaceRunDateRange,
  resolveBrandSpaceRunPreset
} from '~/utils/brandSpaceRunFilters'
import { useWorkbenchShellState } from '~/composables/useWorkbenchShellState'

definePageMeta({
  layout: false,
  middleware: 'auth',
  requiresAuth: true
})

const route = useRoute()
const demoMode = useRuntimeConfig().public.demoMode === true
const router = useRouter()
const brandSpacesApi = useBrandSpacesApi()
const brandSpaceDirectory = useWorkbenchBrandSpacesDirectory()
const {
  workbenchRef,
  workbenchScrolled,
  syncWorkbenchScrolled,
  resetWorkbenchScrolled
} = useWorkbenchShellState()
const brandSpaceId = computed(() => String(route.params.brandSpaceId || ''))
const runtime = ref<BrandSpaceRunHistoryView | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const dateFrom = ref('')
const dateTo = ref('')
const datePreset = ref<BrandSpaceRunDatePreset>('last_14_days')

const currentBrandSpace = computed(() => brandSpaceDirectory.resolveByBrandSpaceId(brandSpaceId.value))
const pageBrandName = computed(() => runtime.value?.brandName || currentBrandSpace.value?.brandName || '品牌空间')

useSeoMeta({
  title: () => `${pageBrandName.value} · ${resolveNavPageTitleByPath('/brands', '品牌管理')} · 运行记录 · Traclux 探镜`
})

const presetOptions: Array<{ key: BrandSpaceRunDatePreset, label: string }> = [
  { key: 'today', label: '今天' },
  { key: 'yesterday', label: '昨天' },
  { key: 'last_7_days', label: '近 7 天' },
  { key: 'last_14_days', label: '近 14 天' },
  { key: 'custom', label: '自定义' }
]

const summaryCards = computed(() => {
  const items = runtime.value?.items || []
  const latest = items[0] || null
  const evidenceTotal = items.reduce((sum, item) => sum + Number(item.evidenceCount || 0), 0)
  const completedTotal = items.reduce((sum, item) => sum + Number(item.completedProbeCount || 0), 0)
  return [
    {
      label: '当前窗口',
      value: runtime.value?.dateFrom && runtime.value?.dateTo ? `${runtime.value.dateFrom} ~ ${runtime.value.dateTo}` : '未设置',
      detail: '按运行实例查询，不把同一天强行合并。'
    },
    {
      label: '运行次数',
      value: `${runtime.value?.totalRuns || 0}`,
      detail: '同一天多次运行会分别保留。'
    },
    {
      label: '完成探针',
      value: `${completedTotal}`,
      detail: '当前窗口内真实完成的探针累计。'
    },
    {
      label: '证据总量',
      value: `${evidenceTotal}`,
      detail: latest?.statusLabel || '当前窗口内累计沉淀的证据条数。'
    }
  ]
})

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

function detailPath(runId: string) {
  return {
    path: `/brands/${encodeURIComponent(brandSpaceId.value)}/runs/${encodeURIComponent(runId)}`,
    query: {
      ...(dateFrom.value ? { dateFrom: dateFrom.value } : {}),
      ...(dateTo.value ? { dateTo: dateTo.value } : {})
    }
  }
}

function settingsPath() {
  return `/brands/${encodeURIComponent(brandSpaceId.value)}/settings`
}

function backToBrands() {
  return '/brands'
}

function syncPresetRange(preset: Exclude<BrandSpaceRunDatePreset, 'custom'>) {
  const range = resolveBrandSpaceRunDateRange(preset)
  dateFrom.value = range.dateFrom
  dateTo.value = range.dateTo
  datePreset.value = preset
}

function initFilters() {
  if (demoMode) {
    dateFrom.value = '2026-10-07'
    dateTo.value = '2026-10-20'
    datePreset.value = 'custom'
    return
  }
  const queryDateFrom = typeof route.query.dateFrom === 'string' ? route.query.dateFrom : ''
  const queryDateTo = typeof route.query.dateTo === 'string' ? route.query.dateTo : ''

  if (!queryDateFrom || !queryDateTo) {
    syncPresetRange('last_14_days')
    return
  }

  dateFrom.value = queryDateFrom
  dateTo.value = queryDateTo
  datePreset.value = resolveBrandSpaceRunPreset(queryDateFrom, queryDateTo)
}

async function loadHistory() {
  if (!brandSpaceId.value) {
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    runtime.value = await brandSpacesApi.runs(brandSpaceId.value, {
      dateFrom: dateFrom.value,
      dateTo: dateTo.value
    })
  } catch (error) {
    errorMessage.value = error instanceof Error && error.message ? error.message : '运行记录加载失败'
  } finally {
    loading.value = false
  }
}

async function applyFilters() {
  datePreset.value = resolveBrandSpaceRunPreset(dateFrom.value, dateTo.value)
  await router.replace({
    query: {
      ...(dateFrom.value ? { dateFrom: dateFrom.value } : {}),
      ...(dateTo.value ? { dateTo: dateTo.value } : {})
    }
  })
  await loadHistory()
}

async function selectPreset(preset: BrandSpaceRunDatePreset) {
  if (preset === 'custom') {
    datePreset.value = 'custom'
    return
  }
  syncPresetRange(preset)
  await applyFilters()
}

function onWorkbenchScroll(event: Event) {
  syncWorkbenchScrolled(event.target as HTMLElement | null)
}

onMounted(async () => {
  resetWorkbenchScrolled()
  initFilters()
  try {
    await brandSpaceDirectory.ensureLoaded()
  } catch {
    // ignore directory load failure and rely on runtime response
  }
  await loadHistory()
})
</script>

<template>
  <main class="dashboard-shell">
    <GeoWorkbenchSidebar />

    <section
      ref="workbenchRef"
      class="workbench run-history-workbench"
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
              <span>品牌空间日度运行原始数据</span>
            </div>
          </div>

          <div class="head-actions">
            <NuxtLink
              class="btn-plain"
              :to="backToBrands()"
            >
              返回品牌管理
            </NuxtLink>
            <NuxtLink
              class="btn-plain"
              :to="settingsPath()"
            >
              空间配置
            </NuxtLink>
          </div>
        </header>
      </div>

      <section class="surface notice-surface notice-surface--muted">
        <div class="notice-copy">
          <strong>这里按单次运行回看品牌空间每天到底怎么跑</strong>
          <span>先定位某一次运行，再下钻看调度决策、执行记录和原始记录。</span>
        </div>
      </section>

      <section class="summary-grid">
        <article
          v-for="card in summaryCards"
          :key="card.label"
          class="surface mini-surface"
        >
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
          <small>{{ card.detail }}</small>
        </article>
      </section>

      <section class="surface section-surface" :inert="demoMode ? true : undefined">
        <div class="surface-head surface-head--compact">
          <div class="surface-head__title">
            <strong>筛选运行窗口</strong>
            <span>日期是查询维度，不是假设一天只有一条运行。</span>
          </div>
        </div>

        <div class="filters-panel">
          <div class="preset-row">
            <button
              v-for="option in presetOptions"
              :key="option.key"
              type="button"
              class="inline-mini preset-chip"
              :class="{ 'preset-chip--active': datePreset === option.key }"
              @click="selectPreset(option.key)"
            >
              {{ option.label }}
            </button>
          </div>

          <div class="filter-controls">
            <label class="date-field">
              <span>开始日期</span>
              <input
                v-model="dateFrom"
                type="date"
              >
            </label>
            <label class="date-field">
              <span>结束日期</span>
              <input
                v-model="dateTo"
                type="date"
              >
            </label>
            <button
              type="button"
              class="cta-primary filter-submit"
              :disabled="loading || !dateFrom || !dateTo"
              @click="applyFilters"
            >
              {{ loading ? '查询中...' : '查询运行记录' }}
            </button>
          </div>
        </div>
      </section>

      <section class="surface section-surface">
        <div class="surface-head surface-head--compact">
          <div class="surface-head__title">
            <strong>运行列表</strong>
            <span>{{ runtime?.totalRuns || 0 }} 条运行实例</span>
          </div>
        </div>

        <div
          v-if="errorMessage"
          class="empty-state"
        >
          <strong>运行记录暂时不可用</strong>
          <span>{{ errorMessage }}</span>
        </div>

        <div
          v-else-if="loading && !runtime"
          class="empty-state"
        >
          <strong>正在读取运行记录</strong>
          <span>我正在把当前时间窗口内的运行实例拉出来。</span>
        </div>

        <div
          v-else-if="!(runtime?.items || []).length"
          class="empty-state"
        >
          <strong>当前时间范围内暂无运行记录</strong>
          <span>可以切换日期范围，或等待下一轮品牌空间运行生成新记录。</span>
        </div>

        <div
          v-else
          class="run-table-scroll"
        >
          <div class="run-table-shell">
            <div class="run-table__head">
              <span>运行日期</span>
              <span>运行时间</span>
              <span>触发方式</span>
              <span>运行状态</span>
              <span>计划探针</span>
              <span>实际完成</span>
              <span>覆盖主题</span>
              <span>证据数</span>
              <span>动作</span>
            </div>

            <div
              v-for="item in runtime?.items || []"
              :key="item.runId"
              class="run-table__row"
            >
              <span class="run-table__strong">{{ item.runDate || '--' }}</span>
              <span>{{ formatDateTime(item.startedAt || item.updatedAt) }}</span>
              <span>{{ item.triggerLabel || '未标记' }}</span>
              <span>
                <i
                  class="status-pill"
                  :class="resolveStatusTone(item.status)"
                >
                  {{ item.statusLabel || '未知状态' }}
                </i>
              </span>
              <span>{{ item.plannedProbeCount ?? 0 }}</span>
              <span>{{ item.completedProbeCount ?? 0 }}</span>
              <span>{{ item.coverageTopicCount ?? 0 }}</span>
              <span>{{ item.evidenceCount ?? 0 }}</span>
              <span class="row-actions">
                <NuxtLink
                  class="inline-mini"
                  :to="detailPath(item.runId)"
                >
                  查看详情
                </NuxtLink>
              </span>
            </div>
          </div>
        </div>
      </section>
    </section>
  </main>
</template>

<style scoped src="~/assets/css/engine-shell.css"></style>

<style scoped>
.run-history-workbench {
  display: grid;
  gap: 14px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.filters-panel {
  display: grid;
  gap: 12px;
}

.preset-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.preset-chip {
  min-width: 72px;
}

.preset-chip--active {
  border-color: #111827;
  background: #111827;
  color: #ffffff;
}

.filter-controls {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 220px)) auto;
  gap: 10px;
  align-items: end;
}

.date-field {
  display: grid;
  gap: 6px;
}

.date-field span {
  font-size: 12px;
  color: #6b7280;
}

.date-field input {
  min-height: 34px;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #ffffff;
  padding: 0 10px;
  color: #111827;
}

.date-field input:focus {
  outline: none;
  border-color: #111827;
}

.filter-submit {
  min-width: 118px;
}

.run-table-scroll {
  overflow-x: auto;
}

.run-table-shell {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
  background: #ffffff;
}

.run-table__head,
.run-table__row {
  display: grid;
  grid-template-columns: 110px 120px 110px 96px 84px 84px 84px 72px 92px;
  gap: 10px;
  align-items: center;
  min-height: 48px;
  padding: 0 12px;
  font-size: 12px;
}

.run-table__head {
  background: #f8fafc;
  border-bottom: 1px solid #d1d5db;
  color: #6b7280;
  font-weight: 600;
}

.run-table__row {
  border-bottom: 1px solid #e5e7eb;
  color: #111827;
}

.run-table__row:last-child {
  border-bottom: none;
}

.run-table__strong {
  font-weight: 600;
}

.row-actions {
  display: inline-flex;
  align-items: center;
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
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .run-table-shell {
    min-width: 920px;
  }
}

@media (max-width: 960px) {
  .filter-controls {
    grid-template-columns: 1fr;
  }

  .filter-submit {
    width: 100%;
  }
}

@media (max-width: 720px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
