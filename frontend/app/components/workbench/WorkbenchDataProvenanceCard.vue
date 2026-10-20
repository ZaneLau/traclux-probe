<script setup lang="ts">
import { computed } from 'vue'
import FormulaBreakdown from '~/components/traclux/result-explanation/FormulaBreakdown.vue'
import MetricExplanationPanel from '~/components/traclux/result-explanation/MetricExplanationPanel.vue'
import type {
  FormulaTerm,
  MetricFact,
  MetricFactor
} from '~/components/traclux/result-explanation/types'
import type {
  WorkbenchDataProvenanceAnswerSample,
  WorkbenchDataProvenanceCardView
} from '~/types/workbench'
import type { RouteLocationRaw } from 'vue-router'

const props = defineProps<{
  modelValue: boolean
  loading?: boolean
  error?: string | null
  card?: WorkbenchDataProvenanceCardView | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'retry'): void
  (event: 'sample-page-change', page: number): void
}>()

interface TraceableProbe {
  rowNo?: number | null
  probeId?: string | null
  detailProbeId?: string | null
  detailAttemptId?: string | null
  topicId?: string | null
  topicName?: string | null
  seqNo?: number | null
  promptText?: string | null
  answerSnippet?: string | null
  status?: string | null
  effectiveAnswer?: boolean | null
  mentioned?: boolean | null
  recommended?: boolean | null
  top3?: boolean | null
  generatedAt?: string | null
}

const cardTitle = computed(() => '这条数据是怎么来的')
const resultExplanation = computed(() => props.card?.resultExplanation || null)
const anchor = computed(() => props.card?.anchor || null)
const plan = computed(() => props.card?.plan || null)
const evidence = computed(() => props.card?.evidence || null)
const samplePage = computed(() => props.card?.samplePage || null)
const composition = computed(() => resultExplanation.value?.composition || [])
const brandMentions = computed(() => evidence.value?.brandMentions || [])
const sampleRows = computed(() => samplePage.value?.rows || [])
const hasPagedSamples = computed(() => sampleRows.value.length > 0)
const provenanceStatus = computed(() => resultExplanation.value?.status || samplePage.value?.sourceStatus || null)
const isPendingProvenance = computed(() => ['second_batch', 'not_in_scope', 'pending'].includes(String(provenanceStatus.value || '')))
const hasMetricSurface = computed(() => Boolean(resultExplanation.value || anchor.value?.metricLabel || anchor.value?.metricValue))
const metricPanelName = computed(() => resultExplanation.value?.metricLabel || anchor.value?.metricLabel || '当前结果')
const resultPointSampleSize = computed(() => {
  const contextItem = composition.value.find(item => item?.source === 'topic_metric_context' && item.denominator)
  const parsed = Number(contextItem?.denominator)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
})
const traceableProbes = computed<TraceableProbe[]>(() => {
  if (hasPagedSamples.value) {
    return sampleRows.value.map(toTraceableSample)
  }
  return []
})
const signalSectionTitle = computed(() => hasPagedSamples.value ? '回答样本' : resultPointSampleSize.value != null ? '可查看探针' : '信号从哪来')
const sampleStatus = computed(() => samplePage.value?.sourceStatus || null)
const sampleStatusLabel = computed(() => samplePage.value?.sourceStatusLabel || null)
const sampleStatusNote = computed(() => samplePage.value?.sourceNote || null)
const samplePageNumber = computed(() => samplePage.value?.page || 1)
const samplePageCount = computed(() => samplePage.value?.pageCount || 0)
const canPrevSamplePage = computed(() => samplePageNumber.value > 1)
const canNextSamplePage = computed(() => samplePageCount.value > 0 && samplePageNumber.value < samplePageCount.value)
const sampleCountLabel = computed(() => {
  const total = samplePage.value?.totalCount
  if (typeof total === 'number' && total > 0) {
    return `${formatNumber(total)} 条`
  }
  return `${signalRows.value.length} 条`
})
const formulaRows = computed(() => [
  { label: '口径', value: resultExplanation.value?.calculation || '' },
  { label: '边界', value: resultExplanation.value?.limitationNote || '' }
].filter(row => row.value))
const resultNoteRows = computed(() => {
  if (isPendingProvenance.value) {
    return [
      {
        label: resultExplanation.value?.statusLabel || '口径待接入',
        value: resultExplanation.value?.meaning || '这个入口还没有稳定的数据溯源定义。'
      },
      {
        label: '处理方式',
        value: resultExplanation.value?.statusNote || resultExplanation.value?.limitationNote || '接入后再开放正式穿透。'
      }
    ]
  }
  if (formulaRows.value.length) {
    return formulaRows.value
  }
  if (!hasMetricSurface.value) {
    return []
  }
  return [
    {
      label: '口径',
      value: '该指标暂未配置可展示口径；需要先补齐后端口径，再开放正式穿透。'
    }
  ]
})
const metricPanelFacts = computed<MetricFact[]>(() => {
  if (isPendingProvenance.value) {
    return []
  }
  if (samplePage.value) {
    return [
      { label: '有效回答', value: samplePage.value.effectiveAnswerCount ?? '--' },
      { label: '提及', value: samplePage.value.mentionHitCount ?? '--' },
      { label: '推荐', value: samplePage.value.recommendHitCount ?? '--' },
      { label: 'Top3', value: samplePage.value.top3HitCount ?? '--' }
    ]
  }
  const mentioned = brandMentions.value.filter(item => item.mentioned).length
  const recommended = brandMentions.value.filter(item => item.recommended).length
  const top3 = brandMentions.value.filter(item => typeof item.positionIndex === 'number' && item.positionIndex > 0 && item.positionIndex <= 3).length
  return [
    { label: '提及', value: mentioned || '--' },
    { label: '推荐', value: recommended || '--' },
    { label: 'Top3', value: top3 || '--' }
  ]
})
const metricPanelValue = computed(() => resultExplanation.value?.metricValue || anchor.value?.metricValue || '--')
const metricPanelWindow = computed(() => resultExplanation.value?.timeWindowLabel || anchor.value?.timeWindowLabel || '当前筛选窗口')
const formulaFactors = computed<MetricFactor[]>(() => {
  if (isPendingProvenance.value || !composition.value.length) {
    return []
  }
  const total = resolveResultDenominator()
  return composition.value
    .filter(item => item?.label && !['口径', '边界'].includes(String(item.label)))
    .map((item, index) => {
      const rate = normalizeNumericFactor(item.rateValue) ?? parseNumber(item.value) ?? parseRatio(item.numerator, item.denominator) ?? 0
      const weight = normalizeNumericFactor(item.weightPercent) ?? resolveFactorWeight(item.label, item.description, index)
      const score = normalizeNumericFactor(item.contributionValue) ?? resolveFactorScore(item.description) ?? Number((rate * weight / 100).toFixed(1))
      return {
        label: item.label || `因子 ${index + 1}`,
        count: parseNumber(item.numerator) ?? 0,
        total: parseNumber(item.denominator) ?? total,
        rate,
        weight,
        score,
        tone: resolveFactorTone(item.label, index)
      }
    })
    .filter(item => item.rate || item.score || item.count || item.total)
})
const formulaParts = computed(() => {
  if (!formulaFactors.value.length) {
    return []
  }
  return formulaFactors.value.map(item => `${item.label} × ${formatNumber(item.weight, '0')}%`)
})
const formulaTerms = computed<FormulaTerm[]>(() => formulaFactors.value.map(item => ({
  label: item.label,
  value: item.score.toFixed(1)
})))
const formulaResult = computed(() => {
  const parsed = parseNumber(metricPanelValue.value)
  return parsed == null ? metricPanelValue.value : parsed.toFixed(1)
})
const signalRows = computed(() => traceableProbes.value.slice(0, hasPagedSamples.value ? traceableProbes.value.length : 8).map((probe, index) => {
  const mention = brandMentions.value.find(item => item.probeId === probe.probeId)
  return {
    key: probe.probeId || `${probe.promptText || 'probe'}-${index}`,
    id: probe.detailProbeId || probe.probeId || `#${probe.seqNo ?? index + 1}`,
    rowNo: probe.rowNo,
    seqNo: probe.seqNo,
    question: probe.promptText || '未记录探针问法',
    answerSnippet: probe.answerSnippet || '',
    topic: formatTopicLabel(probe.topicName || anchor.value?.topicName || probe.topicId || '当前 Topic'),
    topicTitle: probe.topicName || anchor.value?.topicName || probe.topicId || '当前 Topic',
    mentioned: hasPagedSamples.value ? Boolean(probe.mentioned) : Boolean(mention?.mentioned),
    recommended: hasPagedSamples.value ? Boolean(probe.recommended) : Boolean(mention?.recommended),
    top3: hasPagedSamples.value ? Boolean(probe.top3) : typeof mention?.positionIndex === 'number' && mention.positionIndex > 0 && mention.positionIndex <= 3,
    effectiveAnswer: probe.effectiveAnswer,
    generatedAt: probe.generatedAt,
    route: probeDetailRoute(probe.detailProbeId || probe.probeId, probe.detailAttemptId)
  }
}))

function close() {
  emit('update:modelValue', false)
}

function toTraceableSample(item: WorkbenchDataProvenanceAnswerSample): TraceableProbe {
  return {
    rowNo: item.rowNo,
    probeId: item.probeId,
    detailProbeId: item.detailProbeId || item.sourceProbeId || item.probeId,
    detailAttemptId: item.detailAttemptId || item.sourceAttemptId || item.attemptId,
    topicId: item.topicId,
    topicName: item.topicName,
    seqNo: item.seqNo,
    promptText: item.promptText,
    answerSnippet: item.answerSnippet,
    status: item.effectiveAnswer ? 'completed' : item.excludedReason || 'invalid',
    effectiveAnswer: item.effectiveAnswer,
    mentioned: item.mentioned,
    recommended: item.recommended,
    top3: item.top3,
    generatedAt: item.generatedAt
  }
}

function changeSamplePage(page: number) {
  if (page < 1 || (samplePageCount.value > 0 && page > samplePageCount.value)) {
    return
  }
  emit('sample-page-change', page)
}

function formatNumber(value?: number | null, emptyLabel = '--') {
  return typeof value === 'number' && Number.isFinite(value) ? value.toLocaleString('zh-CN') : emptyLabel
}

function formatTopicLabel(value?: string | null) {
  const normalized = String(value || '').trim()
  const chars = Array.from(normalized)
  return chars.length > 4 ? `${chars.slice(0, 4).join('')}...` : normalized
}

function parseNumber(value?: string | number | null) {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }
  const matched = String(value || '').replace(/,/g, '').match(/-?\d+(?:\.\d+)?/)
  if (!matched) {
    return null
  }
  const parsed = Number(matched[0])
  return Number.isFinite(parsed) ? parsed : null
}

function parseRatio(numerator?: string | null, denominator?: string | null) {
  const n = parseNumber(numerator)
  const d = parseNumber(denominator)
  if (n == null || d == null || d <= 0) {
    return null
  }
  return Number((n / d * 100).toFixed(1))
}

function normalizeNumericFactor(value?: number | null) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function resolveResultDenominator() {
  const contextDenominator = parseNumber(composition.value.find(item => item?.denominator)?.denominator)
  return contextDenominator && contextDenominator > 0
    ? contextDenominator
    : evidence.value?.probeCount || plan.value?.probeCount || 0
}

function resolveFactorWeight(label?: string | null, description?: string | null, index = 0) {
  const explicit = String(description || '').match(/权重\s*([0-9.]+)/)
  if (explicit) {
    return Number(explicit[1])
  }
  const normalized = String(label || '')
  if (normalized.includes('推荐')) {
    return 40
  }
  if (normalized.includes('提及')) {
    return 35
  }
  if (normalized.includes('Top3') || normalized.includes('前三')) {
    return 25
  }
  return index === 0 ? 35 : index === 1 ? 40 : 25
}

function resolveFactorScore(description?: string | null) {
  const explicit = String(description || '').match(/计入\s*([0-9.]+)/)
  return explicit ? Number(explicit[1]) : null
}

function resolveFactorTone(label?: string | null, index = 0): MetricFactor['tone'] {
  const normalized = String(label || '')
  if (normalized.includes('推荐')) {
    return 'blue'
  }
  if (normalized.includes('Top3') || normalized.includes('前三')) {
    return 'amber'
  }
  return index === 1 ? 'blue' : index === 2 ? 'amber' : 'teal'
}

function probeDetailRoute(probeId?: string | null, attemptId?: string | null): RouteLocationRaw | null {
  if (!probeId && !attemptId) {
    return null
  }
  return {
    path: '/probe-analysis',
    query: {
      brandSpaceId: props.card?.brandSpaceId,
      ...(probeId ? { probeId } : {}),
      ...(attemptId ? { attemptId } : {})
    }
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="modelValue"
      class="provenance-mask"
      @click.self="close"
    >
      <aside class="provenance-panel" aria-label="数据溯源">
        <header class="provenance-head">
          <div>
            <span class="provenance-eyebrow">探镜数据溯源</span>
            <h2>{{ cardTitle }}</h2>
          </div>
          <button
            type="button"
            class="provenance-close"
            @click="close"
          >
            关闭
          </button>
        </header>

        <section
          v-if="loading"
          class="provenance-state"
        >
          正在读取数据来源...
        </section>
        <section
          v-else-if="error"
          class="provenance-state provenance-state--error"
        >
          <strong>读取失败</strong>
          <span>{{ error }}</span>
          <button
            type="button"
            class="provenance-retry"
            @click="emit('retry')"
          >
            重试
          </button>
        </section>
        <section
          v-else-if="!card"
          class="provenance-state"
        >
          暂无溯源数据。
        </section>

        <div
          v-else
          class="provenance-body"
        >
          <section
            v-if="hasMetricSurface"
            class="result-explanation-stack"
          >
            <MetricExplanationPanel
              :metric-name="metricPanelName"
              :metric-value="metricPanelValue"
              :window-label="metricPanelWindow"
              :facts="metricPanelFacts"
            />

            <FormulaBreakdown
              v-if="formulaFactors.length"
              title="计算口径"
              :note="`${formulaFactors.length} 个因子计入当前结果`"
              :formula-parts="formulaParts"
              :terms="formulaTerms"
              :result="formulaResult"
              :factors="formulaFactors"
            />

            <dl
              v-if="resultNoteRows.length"
              class="result-explanation-notes"
            >
              <div
                v-for="row in resultNoteRows"
                :key="row.label"
              >
                <dt>{{ row.label }}</dt>
                <dd>{{ row.value }}</dd>
              </div>
            </dl>
          </section>

          <section
            v-else
            class="provenance-state provenance-state--error"
          >
            <strong>缺少结果解释</strong>
            <span>这条数据还没有返回指标值、窗口和计算口径，不能用其他信息替代。</span>
          </section>

          <section
            v-if="resultExplanation && composition.length && !formulaFactors.length"
            class="provenance-section"
          >
            <div class="provenance-section__head">
              <strong>组成拆解</strong>
              <span>{{ composition.length }} 项</span>
            </div>
            <div class="composition-grid">
              <article
                v-for="item in composition"
                :key="`${item.label || 'item'}-${item.value || 'value'}`"
                class="composition-card"
              >
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
                <small v-if="item.numerator || item.denominator">
                  {{ item.numerator || '--' }} / {{ item.denominator || '--' }}
                </small>
                <p v-if="item.description">{{ item.description }}</p>
              </article>
            </div>
          </section>

          <section
            v-if="signalRows.length"
            class="provenance-section"
          >
            <div class="provenance-section__head">
              <strong>{{ signalSectionTitle }}</strong>
              <span>{{ sampleCountLabel }}</span>
            </div>
            <div
              v-if="samplePage"
              class="sample-status"
              :class="`sample-status--${sampleStatus || 'unknown'}`"
            >
              <strong>{{ sampleStatusLabel }}</strong>
              <span>{{ sampleStatusNote }}</span>
            </div>
            <div class="probe-table">
              <div class="probe-head">
                <span>探针</span>
                <span>Topic</span>
                <span>Top3</span>
                <span>推荐</span>
                <span>提及</span>
                <span>查看</span>
              </div>
              <article
                v-for="row in signalRows"
                :key="row.key"
                class="probe-row"
              >
                <div class="probe-question">
                  <span>#{{ row.rowNo ?? row.seqNo ?? '--' }}</span>
                  <strong>{{ row.question }}</strong>
                  <small v-if="row.answerSnippet">{{ row.answerSnippet }}</small>
                </div>
                <span class="probe-topic" :title="row.topicTitle">{{ row.topic }}</span>
                <span class="probe-mark" :class="{ active: row.top3 }">{{ row.top3 ? '是' : '-' }}</span>
                <span class="probe-mark" :class="{ active: row.recommended }">{{ row.recommended ? '是' : '-' }}</span>
                <span class="probe-mark" :class="{ active: row.mentioned }">{{ row.mentioned ? '是' : '-' }}</span>
                <NuxtLink
                  v-if="row.route"
                  class="probe-link"
                  :to="row.route"
                >
                  查看
                </NuxtLink>
                <span v-else class="probe-link probe-link--disabled">--</span>
              </article>
            </div>
            <div
              v-if="samplePage && samplePageCount > 1"
              class="sample-pagination"
            >
              <button
                type="button"
                :disabled="!canPrevSamplePage"
                @click="changeSamplePage(samplePageNumber - 1)"
              >
                上一页
              </button>
              <span>第 {{ samplePageNumber }} / {{ samplePageCount }} 页</span>
              <button
                type="button"
                :disabled="!canNextSamplePage"
                @click="changeSamplePage(samplePageNumber + 1)"
              >
                下一页
              </button>
            </div>
          </section>

        </div>
      </aside>
    </div>
  </Teleport>
</template>

<style scoped>
.provenance-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  justify-content: flex-end;
  background: rgba(15, 23, 42, 0.22);
  backdrop-filter: blur(3px);
}

.provenance-panel {
  width: min(620px, 100vw);
  height: 100vh;
  overflow-y: auto;
  border-left: 1px solid #d1d5db;
  background: #f3f7f4;
  box-shadow: -24px 0 60px rgba(15, 23, 42, 0.18);
}

.provenance-head {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 22px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.45);
  background: rgba(243, 247, 244, 0.9);
  backdrop-filter: blur(14px);
}

.provenance-eyebrow {
  display: inline-flex;
  margin-bottom: 7px;
  color: #0f766e;
  font-size: 11px;
  font-weight: 850;
  letter-spacing: 0.16em;
}

.provenance-head h2 {
  margin: 0;
  color: #111827;
  font-size: 22px;
  line-height: 1.1;
}

.provenance-close,
.provenance-retry {
  min-height: 34px;
  border: 1px solid #cbd5e1;
  background: #fff;
  padding: 0 12px;
  color: #111827;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.provenance-body {
  display: grid;
  gap: 12px;
  padding: 14px;
}

.result-explanation-stack {
  display: grid;
  gap: 12px;
}

.result-hero,
.provenance-section,
.provenance-state {
  border: 1px solid #d5dce3;
  border-radius: 2px;
  background: #fff;
}

.result-hero {
  position: relative;
  display: grid;
  gap: 18px;
  overflow: hidden;
  padding: 22px;
}

.result-hero::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    linear-gradient(120deg, rgba(15, 23, 42, 0.08), rgba(20, 184, 166, 0.12) 32%, rgba(255, 255, 255, 0) 62%);
}

.result-hero__title,
.provenance-section__head {
  position: relative;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.result-hero__title h3,
.provenance-section__head strong {
  margin: 0;
  color: #111827;
  font-size: 20px;
  font-weight: 850;
  letter-spacing: -0.04em;
}

.result-hero__title span,
.provenance-section__head span {
  color: #0f766e;
  font-size: 13px;
  font-weight: 800;
  white-space: nowrap;
}

.result-hero__value {
  position: relative;
  color: #0f172a;
  font-size: clamp(64px, 12vw, 104px);
  font-weight: 900;
  letter-spacing: -0.08em;
  line-height: 0.9;
}

.sample-strip {
  position: relative;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.sample-strip div,
.composition-card {
  border: 1px solid #e2e8f0;
  border-radius: 2px;
  background: rgba(248, 250, 252, 0.82);
  padding: 10px 12px;
}

.sample-strip strong {
  display: block;
  color: #0f172a;
  font-size: 24px;
  line-height: 1;
}

.sample-strip span,
.composition-card span {
  display: block;
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  font-weight: 750;
}

.formula-strip,
.result-explanation-notes {
  position: relative;
  display: grid;
  gap: 8px;
  margin: 0;
}

.formula-strip div,
.result-explanation-notes div {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
}

.formula-strip div {
  border-top: 1px solid rgba(148, 163, 184, 0.32);
  padding-top: 8px;
}

.result-explanation-notes {
  border: 1px solid #d5dce3;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.86);
  padding: 12px;
}

.result-explanation-notes div + div {
  border-top: 1px solid rgba(148, 163, 184, 0.26);
  padding-top: 8px;
}

.formula-strip dt,
.formula-strip dd,
.result-explanation-notes dt,
.result-explanation-notes dd {
  margin: 0;
}

.formula-strip dt,
.result-explanation-notes dt {
  color: #0f766e;
  font-size: 12px;
  font-weight: 850;
}

.formula-strip dd,
.result-explanation-notes dd {
  color: #334155;
  font-size: 13px;
  line-height: 1.55;
}

.provenance-section {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.composition-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.composition-card {
  display: grid;
  gap: 6px;
}

.composition-card span {
  margin-top: 0;
}

.composition-card strong {
  color: #111827;
  font-size: 20px;
  line-height: 1.1;
}

.composition-card small,
.composition-card p,
.provenance-state,
.sample-status,
.probe-head,
.probe-topic,
.probe-mark,
.probe-link {
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.composition-card p {
  margin: 0;
}

.sample-status {
  display: grid;
  gap: 4px;
  border: 1px solid #d5dce3;
  border-radius: 2px;
  background: #f8fafc;
  padding: 10px 12px;
}

.sample-status strong {
  color: #111827;
  font-size: 13px;
}

.sample-status span {
  color: #475569;
}

.sample-status--metric_context_mismatch {
  border-color: rgba(245, 158, 11, 0.38);
  background: rgba(255, 251, 235, 0.82);
}

.probe-table {
  display: grid;
  overflow: hidden;
  border: 1px solid #d5dce3;
  border-radius: 2px;
  background: #fff;
}

.probe-head,
.probe-row {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 86px 48px 48px 48px 58px;
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
}

.probe-head {
  border-bottom: 1px solid #d5dce3;
  background: #f8fafc;
  font-weight: 800;
}

.probe-row + .probe-row {
  border-top: 1px solid #eef2f7;
}

.probe-question {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 4px 10px;
  align-items: start;
  min-width: 0;
}

.probe-question span {
  color: #64748b;
  font-size: 12px;
  white-space: nowrap;
}

.probe-question strong {
  overflow: hidden;
  color: #111827;
  font-size: 14px;
  font-weight: 750;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.probe-question small {
  grid-column: 2;
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.probe-topic,
.probe-mark,
.probe-link {
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  background: #f8fafc;
  padding: 4px 8px;
  text-align: center;
  text-decoration: none;
  white-space: nowrap;
}

.probe-mark {
  color: #94a3b8;
}

.probe-mark.active {
  border-color: rgba(15, 118, 110, 0.24);
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
  font-weight: 850;
}

.probe-link {
  border-color: rgba(15, 118, 110, 0.28);
  background: #fff;
  color: #0f766e;
  font-weight: 850;
}

.probe-link:hover {
  background: rgba(15, 118, 110, 0.08);
}

.probe-link--disabled {
  color: #94a3b8;
}

.sample-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.sample-pagination span {
  color: #475569;
  font-size: 12px;
  font-weight: 750;
}

.sample-pagination button {
  min-height: 30px;
  border: 1px solid #cbd5e1;
  border-radius: 2px;
  background: #fff;
  padding: 0 10px;
  color: #111827;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.sample-pagination button:disabled {
  cursor: not-allowed;
  opacity: 0.42;
}

.provenance-state {
  display: grid;
  gap: 10px;
  margin: 14px;
  padding: 18px;
}

.provenance-state--error {
  border-color: #fecaca;
  background: #fff7f7;
  color: #991b1b;
}

@media (max-width: 640px) {
  .provenance-panel {
    width: 100vw;
  }

  .sample-strip,
  .formula-strip div,
  .result-explanation-notes div,
  .composition-grid {
    grid-template-columns: 1fr 1fr;
  }

  .formula-strip div,
  .result-explanation-notes div {
    grid-template-columns: 1fr;
  }

  .probe-head {
    display: none;
  }

  .probe-row {
    grid-template-columns: minmax(0, 1fr) repeat(3, 46px) 52px;
  }

  .probe-topic {
    display: none;
  }
}
</style>
