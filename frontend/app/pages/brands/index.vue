<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { GeoApiError } from '~/composables/useGeoApi'
import {
  hasBrandSpaceNeedsAttention,
  resolveBrandSpaceHealthState,
  resolveBrandSpaceTrackingState,
  resolveBrandSpaceUpdatedAt
} from '~/utils/brandSpaceState'
import {
  resolveNavPageTitleByPath
} from '~/composables/useDashboardNav'
import type { BrandSpaceArchiveItem, BrandSpaceListItem, ServiceAccess } from '~/types/brand-space'

definePageMeta({
  layout: false,
  middleware: 'auth',
  requiresAuth: true
})

useSeoMeta({
  title: `${resolveNavPageTitleByPath('/brands', '品牌管理')} · Traclux 探镜`
})

function stateWeight(item: BrandSpaceListItem) {
  const tracking = resolveBrandSpaceTrackingState(item)
  const health = resolveBrandSpaceHealthState(item)

  if (tracking.key === 'NEEDS_ATTENTION' || health.tone === 'status-pill--danger') return 4
  if (tracking.key === 'ACTIVE') return 3
  if (tracking.key === 'PAUSED') return 2
  if (health.tone === 'status-pill--warning') return 1
  return 0
}

const brandSpacesApi = useBrandSpacesApi()
const serviceAccessApi = useServiceAccessApi()
const route = useRoute()
const router = useRouter()
const { isEngineTheaterEnabled } = useFeatureAvailability()
const {
  workbenchRef,
  workbenchScrolled,
  resetWorkbenchScrolled,
  syncWorkbenchScrolled
} = useWorkbenchShellState()

const loading = ref(false)
const errorMessage = ref('')
const serviceAccess = ref<ServiceAccess | null>(null)
const serviceAccessError = ref('')
const brandSpaces = ref<BrandSpaceListItem[]>([])
const archivedBrandSpaces = ref<BrandSpaceArchiveItem[]>([])
const archivedLoading = ref(false)
const archivedErrorMessage = ref('')
const archivedPendingId = ref('')
const archivedPendingAction = ref<'restore' | 'delete' | ''>('')
const archivedActionError = ref('')
const archivedActionMessage = ref('')
const deleteConfirmId = ref('')

const isServiceBlocked = computed(() => {
  return serviceAccess.value?.status === 'inactive' || serviceAccess.value?.status === 'expired'
})

const rankedBrandSpaces = computed(() => {
  return brandSpaces.value.slice().sort((left, right) => {
    const statusDelta = stateWeight(right) - stateWeight(left)
    if (statusDelta !== 0) {
      return statusDelta
    }
    return String(resolveBrandSpaceUpdatedAt(right) || '').localeCompare(String(resolveBrandSpaceUpdatedAt(left) || ''))
  })
})

const totalCount = computed(() => rankedBrandSpaces.value.length)
const trackingContractReadyCount = computed(() =>
  rankedBrandSpaces.value.filter(item => resolveBrandSpaceTrackingState(item).contractReady).length
)
const trackingContractReady = computed(() => trackingContractReadyCount.value > 0)
const allTrackingContractsReady = computed(() =>
  totalCount.value > 0 && trackingContractReadyCount.value === totalCount.value
)
const activeTrackingCount = computed(() => rankedBrandSpaces.value.filter(item => resolveBrandSpaceTrackingState(item).key === 'ACTIVE').length)
const needsAttentionCount = computed(() => rankedBrandSpaces.value.filter(item => hasBrandSpaceNeedsAttention(item)).length)
const attentionBrandSpaces = computed(() =>
  rankedBrandSpaces.value.filter(item => hasBrandSpaceNeedsAttention(item)).slice(0, 3)
)
const resultReadyCount = computed(() =>
  rankedBrandSpaces.value.filter(item =>
    Boolean(item.latestRunId || item.lastRunAt || item.lastGeneratedAt || typeof item.visibilityScore === 'number')
  ).length
)
const consoleCards = computed(() => [
  {
    label: '全部品牌空间',
    value: `${totalCount.value}`,
    detail: '当前账号下可管理的品牌空间。'
  },
  {
    label: '跟踪中',
    value: trackingContractReady.value ? `${activeTrackingCount.value}` : '--',
    detail: trackingContractReady.value
      ? allTrackingContractsReady.value
        ? '已进入长期跟踪状态的品牌空间。'
        : `当前仅 ${trackingContractReadyCount.value} / ${totalCount.value} 个品牌空间已完成跟踪状态同步。`
      : '品牌空间状态同步中。'
  },
  {
    label: '需处理',
    value: `${needsAttentionCount.value}`,
    detail: needsAttentionCount.value ? '存在运行异常或配置缺口。' : '当前没有需要立即处理的品牌空间。'
  },
  {
    label: '已有结果',
    value: `${resultReadyCount.value}`,
    detail: '已产生运行记录或可见度结果。'
  }
])

function trackingState(row?: BrandSpaceListItem | null) {
  return resolveBrandSpaceTrackingState(row)
}

function healthState(row?: BrandSpaceListItem | null) {
  return resolveBrandSpaceHealthState(row)
}

function formatPercent(value?: number | null) {
  return typeof value === 'number' ? `${value.toFixed(1)}%` : '未同步'
}

function formatDate(value?: string | null, emptyLabel = '未生成') {
  if (!value) {
    return emptyLabel
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

function observerPath(row?: BrandSpaceListItem | null) {
  if (!isEngineTheaterEnabled.value || !row?.latestRunId) {
    return ''
  }
  return `/engine-theater?runId=${encodeURIComponent(row.latestRunId)}`
}

function dashboardPath(row: BrandSpaceListItem) {
  return `/dashboard?brandSpaceId=${encodeURIComponent(row.brandSpaceId)}`
}

function settingsPath(row: BrandSpaceListItem) {
  return `/brands/${encodeURIComponent(row.brandSpaceId)}/settings`
}

function runsPath(row: BrandSpaceListItem) {
  return `/brands/${encodeURIComponent(row.brandSpaceId)}/runs`
}

function primaryActionLabel(row: BrandSpaceListItem) {
  const health = healthState(row)
  if (health.tone === 'status-pill--danger' || health.key === 'LEGACY_GENERATING') {
    return '查看运行'
  }
  return row.visibilityScore == null ? '查看运行' : '查看结果'
}

function primaryActionPath(row: BrandSpaceListItem) {
  return primaryActionLabel(row) === '查看结果' ? dashboardPath(row) : runsPath(row)
}

function runConfigSummary(row: BrandSpaceListItem) {
  if (row.configReady === false) {
    return {
      main: '方案待补',
      sub: '先补齐 topic 和问题种子'
    }
  }
  if (typeof row.activeRecurringProbeCount === 'number' && row.activeRecurringProbeCount > 0) {
    return {
      main: `每日 ${row.activeRecurringProbeCount} 条探针`,
      sub: typeof row.activePromptCount === 'number'
        ? `${row.activePromptCount} 条已启用问题种子`
        : '按当前配置持续跟踪'
    }
  }
  return {
    main: '仅手动运行',
    sub: typeof row.activePromptCount === 'number'
      ? `${row.activePromptCount} 条已启用问题种子`
      : '保留配置，自动运行关闭'
  }
}

function latestRunStatusLabel(row: BrandSpaceListItem) {
  switch (row.lastRunStatus) {
    case 'completed':
      return '已完成'
    case 'running':
      return '运行中'
    case 'created':
      return '待开始'
    case 'failed':
      return '运行失败'
    case 'cancelled':
      return '已取消'
    case 'partial':
      return '部分完成'
    default:
      return row.lastGeneratedAt ? '已产出结果' : '未运行'
  }
}

function resultSummary(row: BrandSpaceListItem) {
  if (typeof row.visibilityScore === 'number') {
    return {
      main: formatPercent(row.visibilityScore),
      sub: 'AI 可见度'
    }
  }
  if (row.latestReportId || row.lastGeneratedAt) {
    return {
      main: '结果待投影',
      sub: latestRunStatusLabel(row)
    }
  }
  const health = healthState(row)
  if (health.key === 'LEGACY_GENERATING' || health.key === 'RUN_RUNNING' || health.key === 'RUN_CREATED') {
    return {
      main: '首跑中',
      sub: '暂无结果'
    }
  }
  return {
    main: '暂无结果',
    sub: health.label
  }
}

function latestRunSummary(row: BrandSpaceListItem) {
  const base = latestRunStatusLabel(row)
  const targetProbeCount = row.latestSchedule?.targetProbeCount
  const deferredTopicCount = row.latestSchedule?.deferredTopicCount
  let detail = base

  if (typeof targetProbeCount === 'number' && targetProbeCount > 0) {
    detail = `${detail} · 执行 ${targetProbeCount} 条探针`
    if (typeof deferredTopicCount === 'number' && deferredTopicCount > 0) {
      detail = `${detail} · 延后 ${deferredTopicCount} 个主题`
    }
  }

  return {
    main: formatDate(row.lastRunAt || row.lastGeneratedAt, '未运行'),
    sub: detail
  }
}

async function loadBrandSpaces() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await brandSpacesApi.list()
    brandSpaces.value = result.items || []
  } catch (error) {
    errorMessage.value = error instanceof Error && error.message ? error.message : '品牌空间列表加载失败'
  } finally {
    loading.value = false
  }
}

async function loadArchivedBrandSpaces() {
  archivedLoading.value = true
  archivedErrorMessage.value = ''
  try {
    const result = await brandSpacesApi.listArchived()
    archivedBrandSpaces.value = result.items || []
  } catch (error) {
    archivedErrorMessage.value = error instanceof Error && error.message ? error.message : '已移出品牌空间列表加载失败'
  } finally {
    archivedLoading.value = false
  }
}

async function loadServiceAccess() {
  serviceAccessError.value = ''
  try {
    serviceAccess.value = await serviceAccessApi.get()
  } catch (error) {
    serviceAccessError.value = error instanceof Error && error.message ? error.message : '服务状态暂时不可用'
  }
}

async function refreshPage() {
  await Promise.all([
    loadBrandSpaces(),
    loadArchivedBrandSpaces(),
    loadServiceAccess()
  ])
}

function isArchivedActionPending(brandSpaceId: string, action?: 'restore' | 'delete') {
  if (!brandSpaceId || archivedPendingId.value !== brandSpaceId) {
    return false
  }
  return action ? archivedPendingAction.value === action : true
}

async function restoreArchivedBrandSpace(item: BrandSpaceArchiveItem) {
  if (!item.brandSpaceId || archivedPendingId.value) {
    return
  }
  archivedPendingId.value = item.brandSpaceId
  archivedPendingAction.value = 'restore'
  archivedActionError.value = ''
  archivedActionMessage.value = ''
  try {
    await brandSpacesApi.restore(item.brandSpaceId)
    deleteConfirmId.value = ''
    archivedActionMessage.value = `${item.brandName} 已恢复到当前账号。`
    await refreshPage()
  } catch (error) {
    archivedActionError.value = error instanceof GeoApiError
      ? error.message
      : (error as Error)?.message || '品牌空间恢复失败'
  } finally {
    archivedPendingId.value = ''
    archivedPendingAction.value = ''
  }
}

function requestDeleteArchivedBrandSpace(item: BrandSpaceArchiveItem) {
  if (!item.brandSpaceId || archivedPendingId.value) {
    return
  }
  archivedActionError.value = ''
  archivedActionMessage.value = ''
  deleteConfirmId.value = deleteConfirmId.value === item.brandSpaceId ? '' : item.brandSpaceId
}

function cancelDeleteArchivedBrandSpace(item?: BrandSpaceArchiveItem) {
  if (!item?.brandSpaceId || deleteConfirmId.value === item.brandSpaceId) {
    deleteConfirmId.value = ''
  }
}

async function deleteArchivedBrandSpace(item: BrandSpaceArchiveItem) {
  if (!item.brandSpaceId || archivedPendingId.value) {
    return
  }
  archivedPendingId.value = item.brandSpaceId
  archivedPendingAction.value = 'delete'
  archivedActionError.value = ''
  archivedActionMessage.value = ''
  try {
    await brandSpacesApi.deleteArchived(item.brandSpaceId)
    deleteConfirmId.value = ''
    archivedActionMessage.value = `${item.brandName} 已从当前账号删除，自助恢复已关闭。`
    await refreshPage()
  } catch (error) {
    archivedActionError.value = error instanceof GeoApiError
      ? error.message
      : (error as Error)?.message || '品牌空间删除失败'
  } finally {
    archivedPendingId.value = ''
    archivedPendingAction.value = ''
  }
}

function onWorkbenchScroll(event: Event) {
  const target = event.target as HTMLElement | null
  syncWorkbenchScrolled(target)
}

async function goNewBrand() {
  if (isServiceBlocked.value) {
    return
  }
  await navigateTo('/brands/new')
}

function clearLegacyCreatedQuery() {
  if (!route.query.created) {
    return
  }

  const nextQuery = { ...route.query }
  delete nextQuery.created
  void router.replace({ path: route.path, query: nextQuery })
}

onMounted(() => {
  resetWorkbenchScrolled()
  clearLegacyCreatedQuery()
  void refreshPage()
})
</script>

<template>
  <main class="dashboard-shell">
    <GeoWorkbenchSidebar />

    <section
      ref="workbenchRef"
      class="workbench"
      @scroll.passive="onWorkbenchScroll"
    >
      <div
        class="workbench-sticky-layer"
        :class="{ 'workbench-sticky-layer--scrolled': workbenchScrolled }"
      >
        <header class="overview-head">
          <div class="brand-zone">
            <h1>{{ resolveNavPageTitleByPath('/brands', '品牌管理') }}</h1>
          </div>

          <div class="head-actions">
            <button
              type="button"
              class="btn-plain"
              :disabled="loading"
              @click="refreshPage"
            >
              {{ loading ? '刷新中...' : '刷新' }}
            </button>
            <button
              type="button"
              class="cta-primary"
              :disabled="isServiceBlocked"
              @click="goNewBrand"
            >
              新增品牌
            </button>
          </div>
        </header>
      </div>

      <section
        v-if="isServiceBlocked"
        class="surface notice-surface notice-surface--warn"
      >
        <div class="notice-copy">
          <strong>当前账号未开通服务</strong>
          <span>品牌空间仍可查看，新增品牌入口已关闭。</span>
        </div>
      </section>

      <section
        v-else-if="serviceAccessError"
        class="surface notice-surface notice-surface--muted"
      >
        <div class="notice-copy">
          <strong>服务状态暂未同步</strong>
          <span>{{ serviceAccessError }}</span>
        </div>
      </section>

      <section class="control-console-grid">
        <article
          v-for="card in consoleCards"
          :key="card.label"
          class="surface console-card"
        >
          <strong>{{ card.value }}</strong>
          <span>{{ card.label }}</span>
          <small>{{ card.detail }}</small>
        </article>
      </section>

      <section
        v-if="errorMessage"
        class="surface notice-surface notice-surface--error"
      >
        <div class="notice-copy">
          <strong>品牌空间加载失败</strong>
          <span>{{ errorMessage }}</span>
        </div>
      </section>

      <section
        v-if="attentionBrandSpaces.length"
        class="surface section-surface attention-panel"
      >
        <div class="surface-head surface-head--compact">
          <div class="surface-head__title">
            <strong>需处理</strong>
            <span>{{ needsAttentionCount }} 个品牌空间需要先看运行或方案</span>
          </div>
        </div>

        <div class="attention-list">
          <article
            v-for="row in attentionBrandSpaces"
            :key="`attention-${row.brandSpaceId}`"
            class="attention-row"
          >
            <div class="attention-row__main">
              <strong>{{ row.brandName }}</strong>
              <span>{{ healthState(row).label }} · {{ latestRunSummary(row).main }}</span>
            </div>
            <div class="row-actions">
              <NuxtLink
                class="inline-mini"
                :to="runsPath(row)"
              >
                查看运行
              </NuxtLink>
              <NuxtLink
                class="inline-mini"
                :to="settingsPath(row)"
              >
                调整方案
              </NuxtLink>
            </div>
          </article>
        </div>
      </section>

      <section class="surface section-surface">
        <div class="surface-head surface-head--compact">
          <div class="surface-head__title">
            <strong>
              <WikiGeoWikiTrigger entrypoint-id="brands.object.brand_space">
                品牌空间
              </WikiGeoWikiTrigger>
            </strong>
            <span>{{ loading ? '加载中' : `${totalCount} 个` }}</span>
          </div>
        </div>

        <div
          v-if="errorMessage && !rankedBrandSpaces.length"
          class="empty-state"
        >
          <strong>品牌空间暂时不可用</strong>
          <span>当前列表尚未返回可用数据，先刷新一次；如仍失败，再检查登录态与后端服务。</span>
          <button
            type="button"
            class="btn-plain"
            :disabled="loading"
            @click="refreshPage"
          >
            重新加载
          </button>
        </div>

        <div
          v-else-if="!loading && !rankedBrandSpaces.length"
          class="empty-state"
        >
          <strong>还没有品牌空间</strong>
          <span>添加品牌后，系统会先生成首轮配置，再进入后续日常运行。</span>
          <button
            type="button"
            class="cta-primary"
            :disabled="isServiceBlocked"
            @click="goNewBrand"
          >
            新增品牌
          </button>
        </div>

        <div
          v-else
          class="brand-table"
        >
          <div class="brand-table__head">
            <span>品牌空间</span>
            <span>跟踪状态</span>
            <span>运行配置</span>
            <span>最近运行</span>
            <span>最新结果</span>
            <span>动作</span>
          </div>

          <div
            v-for="row in rankedBrandSpaces"
            :key="row.brandSpaceId"
          class="brand-table__row"
        >
            <span class="brand-table__name">
              {{ row.brandName }}
              <small>{{ row.category || '未分类' }}</small>
            </span>

          <span
            class="brand-state-cell"
            :class="`brand-state-cell--${healthState(row).tone.replace('status-pill--', '')}`"
          >
            <i aria-hidden="true" />
            <strong>{{ trackingState(row).label }}</strong>
            <small>最近运行：{{ latestRunStatusLabel(row) }}</small>
          </span>

            <span class="brand-table__metric">
              <strong>{{ runConfigSummary(row).main }}</strong>
              <small>{{ runConfigSummary(row).sub }}</small>
            </span>

            <span class="brand-table__metric">
              <strong>{{ latestRunSummary(row).main }}</strong>
              <small>{{ latestRunSummary(row).sub }}</small>
            </span>

            <span class="brand-table__metric">
              <strong>{{ resultSummary(row).main }}</strong>
              <small>{{ resultSummary(row).sub }}</small>
            </span>

            <span class="row-actions">
              <NuxtLink
                class="inline-mini inline-mini--primary"
                :to="primaryActionPath(row)"
              >
                {{ primaryActionLabel(row) }}
              </NuxtLink>
              <NuxtLink
                class="inline-mini"
                :to="runsPath(row)"
              >
                运行记录
              </NuxtLink>
              <NuxtLink
                class="inline-mini"
                :to="settingsPath(row)"
              >
                方案设置
              </NuxtLink>
            </span>
          </div>
        </div>
      </section>

      <section
        v-if="archivedBrandSpaces.length || archivedErrorMessage || archivedActionMessage || archivedActionError"
        class="surface section-surface"
      >
        <div class="surface-head surface-head--compact">
          <div class="surface-head__title">
            <strong>已移出品牌空间</strong>
            <span>{{ archivedLoading ? '加载中' : `${archivedBrandSpaces.length} 个` }}</span>
          </div>
        </div>

        <div
          v-if="archivedErrorMessage"
          class="empty-state empty-state--compact"
        >
          <strong>恢复区暂时不可用</strong>
          <span>{{ archivedErrorMessage }}</span>
        </div>

        <div
          v-else
          class="archived-list"
        >
          <article
            v-for="row in archivedBrandSpaces"
            :key="row.brandSpaceId"
            class="archived-card"
          >
            <div class="archived-card__meta">
              <strong>{{ row.brandName }}</strong>
              <span>移出时间 {{ formatDate(row.archivedAt, '未记录') }}</span>
              <span>在 {{ formatDate(row.retentionUntil, '未记录') }} 之前可恢复，逾期后退出自助恢复区</span>
              <span v-if="row.internalRestoreUntil">平台内部保留至 {{ formatDate(row.internalRestoreUntil, '未记录') }}</span>
            </div>

            <div class="archived-card__actions">
              <div class="archived-card__action-row">
                <button
                  type="button"
                  class="inline-mini"
                  :disabled="Boolean(archivedPendingId)"
                  @click="restoreArchivedBrandSpace(row)"
                >
                  {{ isArchivedActionPending(row.brandSpaceId, 'restore') ? '恢复中...' : '恢复到当前账号' }}
                </button>
                <button
                  type="button"
                  class="inline-mini inline-mini--danger"
                  :disabled="Boolean(archivedPendingId)"
                  @click="requestDeleteArchivedBrandSpace(row)"
                >
                  直接删除
                </button>
              </div>

              <div
                v-if="deleteConfirmId === row.brandSpaceId"
                class="archived-confirm"
              >
                <span>删除后会立刻退出当前账号的自助恢复区，但平台内部仍保留至 {{ formatDate(row.internalRestoreUntil, '未记录') }}。</span>
                <div class="archived-confirm__actions">
                  <button
                    type="button"
                    class="inline-mini"
                    :disabled="Boolean(archivedPendingId)"
                    @click="cancelDeleteArchivedBrandSpace(row)"
                  >
                    取消
                  </button>
                  <button
                    type="button"
                    class="inline-mini inline-mini--danger"
                    :disabled="Boolean(archivedPendingId)"
                    @click="deleteArchivedBrandSpace(row)"
                  >
                    {{ isArchivedActionPending(row.brandSpaceId, 'delete') ? '删除中...' : '确认删除' }}
                  </button>
                </div>
              </div>
            </div>
          </article>
        </div>

        <p
          v-if="archivedActionMessage"
          class="archived-feedback archived-feedback--success"
        >
          {{ archivedActionMessage }}
        </p>

        <p
          v-if="archivedActionError"
          class="archived-feedback archived-feedback--error"
        >
          {{ archivedActionError }}
        </p>
      </section>
    </section>
  </main>
</template>

<style scoped src="~/assets/css/engine-shell.css"></style>

<style scoped>
.control-console-grid,
.status-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.console-card {
  display: grid;
  gap: 4px;
  align-content: start;
}

.console-card strong {
  font-size: 24px;
  line-height: 1;
  color: #111827;
}

.console-card span {
  font-size: 12px;
  font-weight: 700;
  color: #334155;
}

.console-card small {
  font-size: 11px;
  line-height: 1.45;
  color: #64748b;
}

.attention-panel {
  display: grid;
  gap: 10px;
}

.attention-list {
  display: grid;
  gap: 8px;
}

.attention-row {
  min-height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 12px;
  border: 1px solid #fed7aa;
  border-radius: 2px;
  background: #fff7ed;
}

.attention-row__main {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.attention-row__main strong {
  color: #111827;
  font-size: 14px;
}

.attention-row__main span {
  color: #9a3412;
  font-size: 12px;
}

.brand-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
}

.brand-table__head,
.brand-table__row {
  min-height: 44px;
  display: grid;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  font-size: 12px;
}

.brand-table__head,
.brand-table__row {
  grid-template-columns: minmax(0, 1.25fr) 150px minmax(112px, 0.8fr) minmax(118px, 0.85fr) minmax(118px, 0.85fr) 224px;
}

.brand-table__head {
  background: #f8fafc;
  border-bottom: 1px solid #d1d5db;
  color: #6b7280;
  font-weight: 600;
}

.brand-table__row {
  border-bottom: 1px solid #e5e7eb;
}

.brand-table__row:last-child {
  border-bottom: none;
}

.brand-table__name {
  display: grid;
  gap: 2px;
  font-weight: 600;
  min-width: 0;
}

.brand-table__name small {
  font-size: 11px;
  font-weight: 400;
  color: #6b7280;
}

.brand-table__metric {
  min-width: 0;
  display: grid;
  gap: 4px;
  justify-items: start;
}

.brand-state-cell {
  min-width: 0;
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr);
  grid-template-areas:
    "dot primary"
    "dot secondary";
  align-items: center;
  column-gap: 8px;
  row-gap: 1px;
}

.brand-state-cell i {
  grid-area: dot;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #64748b;
}

.brand-state-cell strong {
  grid-area: primary;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  line-height: 1.2;
  color: #111827;
}

.brand-state-cell small {
  grid-area: secondary;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  color: #64748b;
}

.brand-state-cell--success i {
  background: #10b981;
}

.brand-state-cell--warning i {
  background: #f59e0b;
}

.brand-state-cell--danger i {
  background: #ef4444;
}

.brand-table__metric strong {
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: #111827;
}

.brand-table__metric small {
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  color: #64748b;
}

.row-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.inline-mini--primary {
  color: #f8fafc;
  border-color: #111827;
  background: #111827;
}

.inline-mini--primary:hover:not(:disabled) {
  color: #fff;
  border-color: #0f172a;
  background: #0f172a;
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

.archived-list {
  display: grid;
  gap: 10px;
}

.archived-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #fcfcfd;
}

.archived-card__meta {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.archived-card__meta strong {
  font-size: 14px;
  color: #111827;
}

.archived-card__meta span {
  font-size: 12px;
  color: #6b7280;
}

.archived-card__actions {
  min-width: 236px;
  display: grid;
  gap: 8px;
  justify-items: end;
}

.archived-card__action-row,
.archived-confirm__actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.archived-confirm {
  display: grid;
  gap: 8px;
  max-width: 300px;
}

.archived-confirm span {
  font-size: 12px;
  line-height: 1.5;
  color: #6b7280;
  text-align: right;
}

.archived-feedback {
  margin: 12px 0 0;
  font-size: 12px;
}

.archived-feedback--success {
  color: #047857;
}

.archived-feedback--error {
  color: #b91c1c;
}

.inline-mini--danger {
  color: #b91c1c;
  border-color: #fecaca;
  background: #fef2f2;
}

.inline-mini--danger:hover:not(:disabled) {
  color: #991b1b;
  border-color: #fca5a5;
}

@media (max-width: 1360px) {
  .brand-table__head,
  .brand-table__row {
    grid-template-columns: minmax(0, 1.2fr) 132px minmax(104px, 0.8fr) minmax(110px, 0.85fr) minmax(110px, 0.85fr) 188px;
  }

  .control-console-grid,
  .status-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .archived-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .archived-card__actions,
  .archived-confirm,
  .archived-confirm span {
    width: 100%;
    max-width: none;
    justify-items: start;
    text-align: left;
  }

  .archived-card__action-row,
  .archived-confirm__actions {
    justify-content: flex-start;
  }
}
</style>
