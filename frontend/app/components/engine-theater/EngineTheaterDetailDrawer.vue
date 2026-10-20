<script setup lang="ts">
import { computed } from 'vue'
import type {
  EngineEntityRef,
  TheaterDetailTab,
  TheaterEvent,
  TheaterMention,
  TheaterProbeRun,
  TheaterResultBridge,
  TheaterStateNode,
  TheaterAttempt,
  TheaterEvidence,
  TheaterBucketKey,
  TheaterStage
} from '~/types/engine'

const props = defineProps<{
  activeTab: TheaterDetailTab
  selectedNode: TheaterStateNode | null
  selectedProbeRun: TheaterProbeRun | null
  selectedAttempt: TheaterAttempt | null
  selectedEvidence: TheaterEvidence | null
  selectedMention: TheaterMention | null
  selectedEvent: TheaterEvent | null
  resultBridge: TheaterResultBridge
  actorLabel: string | null
  sourceDomainLabel: string | null
  sourceUrlLabel: string | null
}>()

const emit = defineEmits<{
  (event: 'update:tab', tab: TheaterDetailTab): void
  (event: 'navigate', path: string): void
}>()

const stageLabelMap: Record<TheaterStage, string> = {
  'task-decomposition': '任务拆解',
  'state-assembly': '状态装配',
  'orchestration': '编排成团',
  'actor-generation': '虚拟用户',
  'probe-generation': '探针生成',
  'execution': '执行探测',
  'result-bridging': '结果收束'
}

const bucketLabelMap: Record<TheaterBucketKey, string> = {
  coverage: '基础覆盖',
  mainstream: '主流机会',
  adversarial: '对抗压力'
}

const refEntries = computed(() => {
  if (props.activeTab === 'node') {
    return buildRefEntries(props.selectedNode?.refs)
  }
  if (props.activeTab === 'probe') {
    return buildRefEntries(props.selectedAttempt?.refs || props.selectedProbeRun?.refs)
  }
  if (props.activeTab === 'evidence') {
    return buildRefEntries(props.selectedEvidence?.refs || props.selectedMention?.refs || props.selectedAttempt?.refs)
  }
  return buildRefEntries(props.resultBridge.refs)
})

function buildRefEntries(refs?: EngineEntityRef) {
  if (!refs) {
    return []
  }
  return Object.entries(refs)
    .filter(([, value]) => Array.isArray(value) ? value.length > 0 : !!value)
    .map(([key, value]) => ({
      key,
      value: Array.isArray(value) ? value.join(', ') : String(value)
    }))
}
</script>

<template>
  <section class="theater-drawer">
    <header class="theater-drawer__head">
      <div>
        <p class="theater-panel__eyebrow">
          详情抽屉
        </p>
        <strong>过程详情</strong>
      </div>

      <div class="theater-drawer__tabs">
        <button
          type="button"
          class="theater-drawer__tab"
          :class="props.activeTab === 'node' ? 'is-active' : ''"
          @click="emit('update:tab', 'node')"
        >
          节点详情
        </button>
        <button
          type="button"
          class="theater-drawer__tab"
          :class="props.activeTab === 'probe' ? 'is-active' : ''"
          @click="emit('update:tab', 'probe')"
        >
          Probe 审计
        </button>
        <button
          type="button"
          class="theater-drawer__tab"
          :class="props.activeTab === 'evidence' ? 'is-active' : ''"
          @click="emit('update:tab', 'evidence')"
        >
          证据详情
        </button>
        <button
          type="button"
          class="theater-drawer__tab"
          :class="props.activeTab === 'result' ? 'is-active' : ''"
          @click="emit('update:tab', 'result')"
        >
          结果桥
        </button>
      </div>
    </header>

    <div class="theater-drawer__body">
      <section
        v-if="props.activeTab === 'node'"
        class="theater-drawer__panel"
      >
        <div class="theater-drawer__summary">
          <strong>{{ props.selectedNode?.label || '等待选择节点' }}</strong>
          <span>{{ props.selectedNode ? stageLabelMap[props.selectedNode.stage] : '点击图谱节点后显示。' }}</span>
        </div>

        <ul
          v-if="props.selectedNode?.detailLines.length"
          class="theater-drawer__list"
        >
          <li
            v-for="detail in props.selectedNode.detailLines"
            :key="detail"
          >
            {{ detail }}
          </li>
        </ul>
      </section>

      <section
        v-else-if="props.activeTab === 'probe'"
        class="theater-drawer__panel"
      >
        <div class="theater-drawer__summary">
          <strong>{{ props.selectedProbeRun?.label || '等待选择 Probe' }}</strong>
          <span>
            {{ props.selectedProbeRun ? `${bucketLabelMap[props.selectedProbeRun.bucketKey]} · ${props.selectedProbeRun.topic}` : '选择 Probe 后显示。' }}
          </span>
        </div>

        <div
          v-if="props.selectedProbeRun"
          class="theater-drawer__grid"
        >
          <article>
            <span>所属角色</span>
            <strong>{{ props.actorLabel || props.selectedProbeRun.actorId }}</strong>
          </article>
          <article>
            <span>执行状态</span>
            <strong>{{ props.selectedProbeRun.status }}</strong>
          </article>
          <article>
            <span>当前模型</span>
            <strong>{{ props.selectedAttempt?.model || '待执行' }}</strong>
          </article>
          <article>
            <span>延迟</span>
            <strong>{{ props.selectedAttempt ? `${props.selectedAttempt.latencyMs}ms` : '—' }}</strong>
          </article>
        </div>

        <div
          v-if="props.selectedAttempt"
          class="theater-drawer__note"
        >
          <strong>Prompt</strong>
          <p>{{ props.selectedAttempt.renderedPrompt }}</p>
        </div>
      </section>

      <section
        v-else-if="props.activeTab === 'evidence'"
        class="theater-drawer__panel"
      >
        <div class="theater-drawer__summary">
          <strong>{{ props.selectedEvidence?.title || '等待证据附着' }}</strong>
          <span>
            {{ props.selectedEvidence ? `${props.sourceDomainLabel || '未知域名'} · 置信度 ${Math.round(props.selectedEvidence.confidence * 100)}%` : '选择证据后显示。' }}
          </span>
        </div>

        <div
          v-if="props.selectedEvidence"
          class="theater-drawer__note"
        >
          <strong>证据片段</strong>
          <p>{{ props.selectedEvidence.snippet }}</p>
          <small>{{ props.sourceUrlLabel || 'URL -' }}</small>
        </div>

        <div
          v-if="props.selectedMention"
          class="theater-drawer__note"
        >
          <strong>品牌提及</strong>
          <p>{{ props.selectedMention.excerpt }}</p>
          <small>情感 {{ props.selectedMention.sentiment }} · 位次 {{ props.selectedMention.position }} · 置信度 {{ Math.round(props.selectedMention.confidence * 100) }}%</small>
        </div>
      </section>

      <section
        v-else
        class="theater-drawer__panel"
      >
        <div class="theater-drawer__summary">
          <strong>结果桥</strong>
          <span>关键结果汇总</span>
        </div>

        <div class="theater-drawer__grid">
          <article>
            <span>ABV Index</span>
            <strong>{{ props.resultBridge.abvIndex.toFixed(1) }}</strong>
          </article>
          <article>
            <span>AI 首选率</span>
            <strong>{{ props.resultBridge.firstChoiceRate.toFixed(1) }}%</strong>
          </article>
          <article>
            <span>AI 确认率</span>
            <strong>{{ props.resultBridge.confirmationRate.toFixed(1) }}%</strong>
          </article>
          <article>
            <span>防御完整度</span>
            <strong>{{ props.resultBridge.defenseIntegrity.toFixed(1) }}%</strong>
          </article>
        </div>

        <ul class="theater-drawer__list">
          <li
            v-for="line in props.resultBridge.summary"
            :key="line"
          >
            {{ line }}
          </li>
        </ul>

        <div class="theater-drawer__route-list">
          <button
            v-for="route in props.resultBridge.routes"
            :key="route.path"
            type="button"
            class="theater-drawer__route"
            @click="emit('navigate', route.path)"
          >
            <strong>{{ route.label }}</strong>
            <span>{{ route.description }}</span>
          </button>
        </div>
      </section>
    </div>

    <footer
      v-if="refEntries.length"
      class="theater-drawer__refs"
    >
      <span
        v-for="entry in refEntries"
        :key="entry.key"
        class="theater-drawer__ref-chip"
      >
        {{ entry.key }}: {{ entry.value }}
      </span>
    </footer>
  </section>
</template>

<style scoped>
.theater-drawer {
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(248, 250, 252, 0.92));
  border-radius: 24px;
  padding: 18px;
  display: grid;
  gap: 14px;
}

.theater-drawer__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.theater-panel__eyebrow {
  margin: 0 0 6px;
  font-size: 11px;
  letter-spacing: 0.14em;
  color: #0f766e;
  text-transform: uppercase;
  font-weight: 700;
}

.theater-drawer__head strong {
  color: #0f172a;
  font-size: 15px;
}

.theater-drawer__tabs {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
}

.theater-drawer__tab {
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.82);
  border-radius: 999px;
  padding: 7px 11px;
  font-size: 12px;
  color: #475569;
}

.theater-drawer__tab.is-active {
  border-color: rgba(15, 23, 42, 0.18);
  color: #0f172a;
}

.theater-drawer__body {
  min-height: 238px;
}

.theater-drawer__panel {
  display: grid;
  gap: 14px;
}

.theater-drawer__summary {
  display: grid;
  gap: 4px;
}

.theater-drawer__summary strong {
  color: #0f172a;
  font-size: 18px;
}

.theater-drawer__summary span {
  color: #64748b;
  font-size: 12px;
}

.theater-drawer__grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.theater-drawer__grid article {
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(255, 255, 255, 0.84);
  border-radius: 18px;
  padding: 12px;
  display: grid;
  gap: 4px;
}

.theater-drawer__grid span {
  color: #64748b;
  font-size: 11px;
}

.theater-drawer__grid strong {
  color: #0f172a;
  font-size: 18px;
}

.theater-drawer__note {
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(255, 255, 255, 0.82);
  border-radius: 18px;
  padding: 14px;
  display: grid;
  gap: 8px;
}

.theater-drawer__note strong {
  font-size: 13px;
  color: #0f172a;
}

.theater-drawer__note p,
.theater-drawer__note small,
.theater-drawer__list {
  margin: 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.6;
}

.theater-drawer__list {
  padding-left: 16px;
  display: grid;
  gap: 4px;
}

.theater-drawer__route-list {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.theater-drawer__route {
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.84);
  border-radius: 18px;
  padding: 14px;
  text-align: left;
  display: grid;
  gap: 4px;
}

.theater-drawer__route strong {
  font-size: 14px;
  color: #0f172a;
}

.theater-drawer__route span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.theater-drawer__refs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.theater-drawer__ref-chip {
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(248, 250, 252, 0.92);
  border-radius: 999px;
  padding: 5px 9px;
  font-size: 11px;
  color: #64748b;
}
</style>
