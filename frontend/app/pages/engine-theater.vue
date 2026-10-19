<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import EngineTheaterGraphStage from '~/components/engine-theater/EngineTheaterGraphStage.vue'
import EngineTheaterHeader from '~/components/engine-theater/EngineTheaterHeader.vue'
import EngineTheaterStageStepper from '~/components/engine-theater/EngineTheaterStageStepper.vue'
import EngineTheaterWaterfall from '~/components/engine-theater/EngineTheaterWaterfall.vue'
import type { RunEvent, RunSummary } from '~/types/brand-space'
import { getEngineDatasetShared } from '~/composables/engine/engineDatasetShared'
import { useEngineSharedState } from '~/composables/engine/useEngineSharedState'
import { useEngineTheaterSelectors } from '~/composables/engine/useEngineTheaterSelectors'
import { useEngineTheaterState } from '~/composables/engine/useEngineTheaterState'
import { useWorkbenchBrandSelection } from '~/composables/useWorkbenchBrandSelection'
import { resolveNavPageTitleByPath } from '~/composables/useDashboardNav'
import type { TheaterNodeType } from '~/types/engine'

definePageMeta({
  layout: false,
  middleware: ['engine-theater-access', 'auth', 'engine-dataset'],
  requiresAuth: true
})

useSeoMeta({
  title: `${resolveNavPageTitleByPath('/engine-theater', '引擎观察窗')} · Traclux 探镜`
})

const route = useRoute()
const runsApi = useRunsApi()
const brandSpaceDirectory = useWorkbenchBrandSpacesDirectory()
const { selectedBrand, selectedBrandSpaceId, workbenchRef, workbenchScrolled } = useEngineSharedState()
const engineDataset = getEngineDatasetShared()
const {
  brandSelectionOptions,
  requestedBrandMissingOption,
  selectedBrandRouteOptionKey
} = useWorkbenchBrandSelection(
  computed(() => typeof route.query.brandSpaceId === 'string' ? route.query.brandSpaceId : '')
)
const theaterBrandOptions = computed(() => {
  if (!requestedBrandMissingOption.value) {
    return brandSelectionOptions.value
  }
  return [requestedBrandMissingOption.value, ...brandSelectionOptions.value]
})

const theaterDataset = computed(() => engineDataset.theaterByBrand[selectedBrand.value] || null)
const maxSequence = computed(() => theaterDataset.value?.events.at(-1)?.sequence || 0)
const theaterState = useEngineTheaterState(maxSequence)
const theaterSelectors = useEngineTheaterSelectors(engineDataset, selectedBrand, theaterState)
const loadingRun = ref(false)
const runErrorMessage = ref('')
const run = ref<RunSummary | null>(null)
const runEvents = ref<RunEvent[]>([])
let pollTimer: ReturnType<typeof setInterval> | null = null

const runId = computed(() => {
  const value = route.query.runId
  return Array.isArray(value) ? value[0] || '' : value || ''
})

const theaterEmptyTitle = computed(() => {
  if (runId.value && run.value?.status === 'running') {
    return '当前运行正在生成剧场任务'
  }
  if (runId.value && isTerminalStatus(run.value?.status)) {
    return '当前运行未生成剧场图谱'
  }
  return '当前品牌暂无剧场任务'
})

const theaterEmptyDescription = computed(() => {
  if (runId.value && run.value?.status === 'running') {
    return '进度已在上方更新，待探针与节点产出后这里会自动刷新。'
  }
  if (runId.value && isTerminalStatus(run.value?.status)) {
    return '可先查看上方运行事件，或返回品牌表现与问题分析确认结果是否已落地。'
  }
  return '请切换品牌。'
})

function statusLabel(status?: string | null) {
  if (status === 'created') return '已创建'
  if (status === 'running') return '运行中'
  if (status === 'completed') return '已完成'
  if (status === 'failed') return '失败'
  if (status === 'cancelled') return '已取消'
  if (status === 'partial') return '部分完成'
  return status || '-'
}

function eventTypeOf(event: RunEvent) {
  return event.type || event.eventType || ''
}

function eventLabel(eventType: string) {
  const map: Record<string, string> = {
    run_started: '运行开始',
    step_changed: '阶段更新',
    probe_generated: '生成提示词',
    model_called: '模型调用',
    citations_collected: '采集引用',
    aggregation_completed: '聚合完成',
    run_completed: '运行完成',
    run_failed: '运行失败'
  }
  return map[eventType] || eventType
}

function payloadText(payload: Record<string, unknown> | null | undefined, key: string) {
  const value = payload?.[key]
  return typeof value === 'string' && value.trim() ? value.trim() : ''
}

function resolveEventText(event: RunEvent) {
  if (event.title || event.message || event.step) {
    return event.title || event.message || event.step || '-'
  }

  const payload = event.payload || {}
  const type = eventTypeOf(event)
  if (type === 'step_changed') {
    return payloadText(payload, 'currentStepLabel') || payloadText(payload, 'currentStep') || '阶段更新'
  }
  if (type === 'probe_generated') {
    return payloadText(payload, 'renderedPrompt') || payloadText(payload, 'probeId') || '提示词已生成'
  }
  if (type === 'model_called') {
    const modelId = payloadText(payload, 'modelId')
    const status = payloadText(payload, 'status')
    return [modelId || '模型调用', status].filter(Boolean).join(' · ')
  }
  if (type === 'citations_collected') {
    const evidenceCount = payload.evidenceCount
    return typeof evidenceCount === 'number' ? `采集 ${evidenceCount} 条引用` : '引用来源已采集'
  }
  if (type === 'aggregation_completed') {
    return '结果聚合完成'
  }
  if (type === 'run_completed') {
    return '首轮数据已生成'
  }
  if (type === 'run_failed') {
    return payloadText(payload, 'failureMessage') || '运行失败'
  }
  return payloadText(payload, 'brandName') || '-'
}

function formatDate(value?: string | null) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }).format(date)
}

function isTerminalStatus(status?: string | null) {
  return status === 'completed' || status === 'failed' || status === 'cancelled' || status === 'partial'
}

function stopPolling() {
  if (!pollTimer) {
    return
  }
  clearInterval(pollTimer)
  pollTimer = null
}

function startPolling() {
  stopPolling()
  if (!runId.value || isTerminalStatus(run.value?.status)) {
    return
  }
  pollTimer = setInterval(() => {
    if (isTerminalStatus(run.value?.status)) {
      stopPolling()
      return
    }
    void loadRun({ silent: true })
  }, 5000)
}

function handleRunRefreshClick() {
  void loadRun()
}

async function loadRun(options: { silent?: boolean } = {}) {
  if (!runId.value) {
    run.value = null
    runEvents.value = []
    runErrorMessage.value = ''
    return
  }

  if (!options.silent) {
    loadingRun.value = true
  }
  runErrorMessage.value = ''
  try {
    const [runResult, eventResult] = await Promise.all([
      runsApi.get(runId.value),
      runsApi.events(runId.value)
    ])
    run.value = runResult
    runEvents.value = eventResult.events || eventResult.items || []
    if (runResult.brandSpaceId) {
      selectedBrandSpaceId.value = runResult.brandSpaceId
    }
    if (runResult.brandName) {
      selectedBrand.value = runResult.brandName
    }
  } catch (error) {
    runErrorMessage.value = error instanceof Error && error.message ? error.message : '引擎观察窗加载失败'
  } finally {
    if (!options.silent) {
      loadingRun.value = false
    }
  }
}

function onWorkbenchScroll(event: Event) {
  const target = event.target as HTMLElement | null
  workbenchScrolled.value = (target?.scrollTop || 0) > 6
}

function onSelectNode(nodeId: string) {
  theaterState.selectNode(nodeId, 'probe')
}

function onSelectCluster(clusterId: string) {
  const cluster = theaterSelectors.graphClusters.value.find(item => item.id === clusterId)
  const clusterNodes = (cluster?.memberNodeIds || [])
    .map(nodeId => theaterSelectors.theater.value?.nodes.find(item => item.id === nodeId))
    .filter(Boolean)

  const nodeTypePriority: Record<TheaterNodeType, number> = { probe: 0, actor: 1, state: 2, topic: 3, bucket: 4, attempt: 5, evidence: 6, mention: 7, source: 8, result: 9, task: 10 }

  const focusNode = clusterNodes
    .sort((a, b) => {
      const typeDelta = nodeTypePriority[a!.type] - nodeTypePriority[b!.type]
      if (typeDelta !== 0) {
        return typeDelta
      }
      return a!.revealedAt - b!.revealedAt
    })[0] || null

  const focusProbeId = focusNode?.refs.probeId || null
  const focusEvent = theaterSelectors.displayedEvents.value
    .filter((event) => {
      if (focusNode?.id && event.nodeId === focusNode.id) {
        return true
      }
      if (focusProbeId && event.probeId === focusProbeId) {
        return true
      }
      if (cluster?.bucketKey && event.bucketKey === cluster.bucketKey) {
        return true
      }
      return false
    })
    .at(-1) || null

  if (focusEvent) {
    theaterState.jumpToSequence(focusEvent.sequence)
  }

  theaterState.selectCluster(
    clusterId,
    'probe',
    focusNode?.id || null,
    focusEvent?.id || null
  )
}

function onSelectEvent(eventId: string, nodeId?: string | null) {
  const event = theaterSelectors.theater.value?.events.find(item => item.id === eventId)
  if (!event) {
    return
  }
  theaterState.selectEvent(eventId, nodeId || event.nodeId, 'probe')
}

function onClearGraphSelection() {
  theaterState.clearSelection()
}

onMounted(() => {
  workbenchScrolled.value = false
  void brandSpaceDirectory.ensureLoaded().catch(() => undefined)
  void loadRun().then(startPolling)
})

watch(runId, () => {
  void loadRun().then(startPolling)
})

onBeforeUnmount(() => {
  stopPolling()
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
        <EngineTheaterHeader
          v-if="theaterSelectors.theater.value"
          :task="theaterSelectors.theater.value.task"
          :selected-brand="selectedBrandRouteOptionKey"
          :brand-options="theaterBrandOptions"
          :current-stage="theaterSelectors.currentStage.value"
          :autoplay="theaterState.autoplay.value"
          :playhead="theaterState.playhead.value"
          :max-sequence="theaterSelectors.maxSequence.value"
          @update:selected-brand="selectedBrandRouteOptionKey = $event"
          @toggle-autoplay="theaterState.toggleAutoplay()"
          @reset="theaterState.resetPlayback()"
        />

        <EngineTheaterStageStepper
          v-if="theaterSelectors.theater.value"
          :stages="theaterSelectors.theater.value.stageSteps"
          :current-stage="theaterSelectors.currentStage.value"
          :stage-filter="theaterState.stageFilter.value"
          @toggle-stage="theaterState.setStageFilter($event)"
        />

        <section
          v-if="runId"
          class="theater-run-observer"
        >
          <div class="theater-run-observer__head">
            <div>
              <strong>实时运行</strong>
              <span>{{ run?.brandName || '读取运行状态' }}</span>
            </div>
            <button
              type="button"
              class="btn-plain"
              @click="handleRunRefreshClick"
            >
              刷新
            </button>
          </div>

          <div
            v-if="runErrorMessage"
            class="theater-run-observer__error"
          >
            {{ runErrorMessage }}
          </div>

          <div class="theater-run-summary">
            <div class="theater-run-summary__item">
              <span>运行状态</span>
              <strong>{{ loadingRun ? '加载中' : statusLabel(run?.status) }}</strong>
            </div>
            <div class="theater-run-summary__item theater-run-summary__item--wide">
              <span>当前阶段</span>
              <strong>{{ run?.currentStepLabel || run?.currentStep || '-' }}</strong>
            </div>
            <div class="theater-run-summary__item">
              <span>进度</span>
              <strong>{{ typeof run?.progressPercent === 'number' ? `${run.progressPercent.toFixed(1)}%` : '-' }}</strong>
            </div>
            <div class="theater-run-summary__item">
              <span>开始时间</span>
              <strong>{{ formatDate(run?.startedAt) }}</strong>
            </div>
          </div>

          <div class="theater-run-events">
            <div class="theater-run-events__head">
              <strong>运行事件</strong>
              <span>{{ runEvents.length }} 条</span>
            </div>
            <div
              v-if="!runEvents.length"
              class="theater-run-events__empty"
            >
              暂无事件
            </div>
            <div
              v-else
              class="theater-run-events__list"
            >
              <article
                v-for="(event, index) in runEvents"
                :key="event.id || event.seq || index"
                class="theater-run-event"
              >
                <span>{{ eventLabel(eventTypeOf(event)) }}</span>
                <strong>{{ resolveEventText(event) }}</strong>
                <small>{{ formatDate(event.createdAt) }}</small>
              </article>
            </div>
          </div>
        </section>
      </div>

      <section
        v-if="theaterSelectors.theater.value"
        class="theater-process-shell"
      >
        <EngineTheaterGraphStage
          :nodes="theaterSelectors.graphNodes.value"
          :edges="theaterSelectors.graphEdges.value"
          :clusters="theaterSelectors.graphClusters.value"
          :panel-nodes="theaterSelectors.graphPanelNodes.value"
          :panel-edges="theaterSelectors.graphPanelEdges.value"
          :selected-node-id="theaterState.selectedNodeId.value || theaterSelectors.selectedEvent.value?.nodeId || null"
          :selected-cluster-id="theaterState.selectedClusterId.value"
          :current-node-id="theaterSelectors.currentNodeId.value"
          :related-node-ids="theaterSelectors.relatedNodeIds.value"
          :active-node-ids="theaterSelectors.activeNodeIds.value"
          :active-edge-ids="theaterSelectors.activeEdgeIds.value"
          :density="theaterState.density.value"
          @select-node="onSelectNode"
          @select-cluster="onSelectCluster"
          @clear-selection="onClearGraphSelection"
          @update:density="theaterState.density.value = $event"
        />

        <div class="theater-side-overlay">
          <EngineTheaterWaterfall
            :events="theaterSelectors.displayedEvents.value"
            :selected-event-id="theaterState.selectedEventId.value"
            :current-event-id="theaterSelectors.focusEvent.value?.id || null"
            :related-event-ids="theaterSelectors.relatedEventIds.value"
            :autoplay="theaterState.autoplay.value"
            :playhead="theaterState.playhead.value"
            :console-lines="theaterSelectors.displayedConsoleLines.value"
            @select-event="onSelectEvent"
          />
        </div>
      </section>

      <section
        v-else
        class="surface section-surface theater-empty"
      >
        <strong>{{ theaterEmptyTitle }}</strong>
        <span>{{ theaterEmptyDescription }}</span>
      </section>
    </section>
  </main>
</template>

<style scoped src="~/assets/css/engine-shell.css"></style>

<style scoped>
.dashboard-shell {
  height: 100vh;
  overflow: hidden;
}

.workbench {
  grid-template-rows: auto minmax(0, 1fr);
  overflow-y: hidden;
}

.theater-run-observer {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #ffffff;
  padding: 10px;
  display: grid;
  gap: 10px;
}

.theater-run-observer__head,
.theater-run-events__head {
  min-height: 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.theater-run-observer__head div {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.theater-run-observer__head strong,
.theater-run-events__head strong {
  font-size: 13px;
  color: #111827;
}

.theater-run-observer__head span,
.theater-run-events__head span,
.theater-run-summary__item span,
.theater-run-event small {
  font-size: 12px;
  color: #6b7280;
}

.theater-run-observer__error {
  min-height: 30px;
  border: 1px solid #fca5a5;
  border-radius: 2px;
  background: #fef2f2;
  color: #991b1b;
  display: flex;
  align-items: center;
  padding: 0 10px;
  font-size: 12px;
}

.theater-run-summary {
  display: grid;
  grid-template-columns: 120px minmax(220px, 1fr) 120px 140px;
  gap: 8px;
}

.theater-run-summary__item {
  min-height: 56px;
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  display: grid;
  align-content: center;
  gap: 5px;
  padding: 0 10px;
}

.theater-run-summary__item strong {
  color: #111827;
  font-size: 15px;
  line-height: 1.25;
}

.theater-run-events {
  display: grid;
  gap: 8px;
}

.theater-run-events__list {
  max-height: 154px;
  overflow-y: auto;
  display: grid;
  gap: 6px;
}

.theater-run-event {
  min-height: 36px;
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 110px;
  align-items: center;
  gap: 8px;
  padding: 0 8px;
}

.theater-run-event span {
  color: #0f766e;
  font-size: 12px;
  font-weight: 650;
}

.theater-run-event strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #111827;
  font-size: 12px;
}

.theater-run-events__empty {
  min-height: 36px;
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  display: grid;
  place-items: center;
  color: #6b7280;
  font-size: 12px;
}

.theater-process-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 0;
  align-items: start;
  position: relative;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.theater-side-overlay {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 380px;
  height: calc(100% - 20px);
  z-index: 20;
}

.theater-empty {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  background: #ffffff;
  padding: 20px;
  display: grid;
  gap: 6px;
}

.theater-empty strong {
  font-size: 16px;
  color: #111827;
}

.theater-empty span {
  font-size: 12px;
  color: #6b7280;
}

@media (max-width: 1600px) {
  .theater-process-shell {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 1260px) {
  .theater-run-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .theater-run-event {
    grid-template-columns: 90px minmax(0, 1fr);
  }

  .theater-run-event small {
    display: none;
  }
}
</style>
