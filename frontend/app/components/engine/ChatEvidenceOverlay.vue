<script setup lang="ts">
import { computed } from 'vue'

interface ChatEvidenceCitationRow {
  id: string
  title: string
  url: string
  domain?: string | null
}

interface ChatEvidenceBrandHitRow {
  brand: string
  score: number
}

interface ChatEvidenceOverlayItem {
  id: string
  probeId: string
  model?: string | null
  query?: string | null
  answer?: string | null
  summary?: string[] | null
  createdAt?: string | null
  citations?: ChatEvidenceCitationRow[] | null
  brandHits?: ChatEvidenceBrandHitRow[] | null
  refs?: {
    probeId?: string | null
  } | null
}

const demoMode = useRuntimeConfig().public.demoMode === true

const props = defineProps<{
  modelValue: string | null
  chats: ChatEvidenceOverlayItem[]
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: string | null): void
  (event: 'openProbe', probeId: string): void
}>()

const currentIndex = computed(() => {
  if (!props.modelValue) {
    return -1
  }
  return props.chats.findIndex(item => item.id === props.modelValue)
})

const currentChat = computed(() => {
  if (currentIndex.value < 0) {
    return null
  }
  return props.chats[currentIndex.value] || null
})

const currentSummary = computed(() => currentChat.value?.summary || [])

const currentBrandHits = computed(() => {
  const rows = currentChat.value?.brandHits || []
  const brandMap = new Map<string, ChatEvidenceBrandHitRow>()
  for (const row of rows) {
    const brand = row.brand?.trim()
    if (!brand) {
      continue
    }
    const current = brandMap.get(brand)
    if (!current || row.score > current.score) {
      brandMap.set(brand, {
        brand,
        score: row.score
      })
    }
  }
  return Array.from(brandMap.values()).sort((left, right) => right.score - left.score)
})

const currentCitations = computed(() => {
  const rows = currentChat.value?.citations || []
  const seen = new Set<string>()
  const deduped: ChatEvidenceCitationRow[] = []
  for (const row of rows) {
    const url = row.url?.trim() || ''
    const title = row.title?.trim() || ''
    const domain = row.domain?.trim() || ''
    const key = (url || `${title}::${domain}`).toLowerCase()
    if (!key || seen.has(key)) {
      continue
    }
    seen.add(key)
    deduped.push(row)
  }
  return deduped
})

const hasBrandHits = computed(() => currentBrandHits.value.length > 0)
const hasCitations = computed(() => currentCitations.value.length > 0)
const hasEvidenceSidebar = computed(() => hasBrandHits.value || hasCitations.value)

const canPrev = computed(() => currentIndex.value > 0)
const canNext = computed(() => currentIndex.value >= 0 && currentIndex.value < props.chats.length - 1)

function closeOverlay() {
  emit('update:modelValue', null)
}

function goPrev() {
  if (!canPrev.value) {
    return
  }
  emit('update:modelValue', props.chats[currentIndex.value - 1]?.id || null)
}

function goNext() {
  if (!canNext.value) {
    return
  }
  emit('update:modelValue', props.chats[currentIndex.value + 1]?.id || null)
}

function openProbe() {
  if (!currentChat.value) {
    return
  }
  emit('openProbe', currentChat.value.refs?.probeId || currentChat.value.probeId)
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
</script>

<template>
  <Teleport to="body">
    <div
      v-if="currentChat"
      class="evidence-overlay"
      @click.self="closeOverlay"
    >
      <article class="evidence-overlay__panel">
        <header class="evidence-overlay__head">
          <div class="evidence-overlay__meta">
            <strong class="evidence-overlay__eyebrow">聊天证据链</strong>
            <h2 class="evidence-overlay__title">
              {{ currentChat.query || '未记录查询' }}
            </h2>
            <div class="evidence-overlay__meta-line">
              <span class="evidence-overlay__meta-chip">{{ resolveChatPlatformLabel(currentChat.model) }}</span>
              <span>{{ formatTime(currentChat.createdAt) }}</span>
            </div>
          </div>
          <div class="evidence-overlay__actions">
            <button
              type="button"
              class="inline-mini"
              @click="openProbe"
            >
              查看探针
            </button>
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
          class="evidence-overlay__body"
          :class="{ 'evidence-overlay__body--single': !hasEvidenceSidebar }"
        >
          <article
            class="evidence-overlay__left"
            :class="{ 'evidence-overlay__left--full': !hasEvidenceSidebar }"
          >
            <section class="evidence-section">
              <div class="evidence-section__head">
                <strong>{{ demoMode ? '回答摘录' : '原始回答' }}</strong>
                <span>{{ currentChat.answer ? '已记录' : '未记录' }}</span>
              </div>
              <div class="evidence-overlay__answer">
                {{ currentChat.answer || '当前未返回原始回答。' }}
              </div>
            </section>

            <section class="evidence-section">
              <div class="evidence-section__head">
                <strong>结构化摘要</strong>
                <span>{{ currentSummary.length }} 条</span>
              </div>
              <div
                v-if="currentSummary.length"
                class="evidence-overlay__summary"
              >
                <ul>
                  <li
                    v-for="(item, index) in currentSummary"
                    :key="`chat-summary-${currentChat.id}-${index}`"
                  >
                    {{ item }}
                  </li>
                </ul>
              </div>
              <div
                v-else
                class="evidence-empty-state"
              >
                当前只记录了原始回答，尚未返回结构化摘要。
              </div>
            </section>

            <div
              v-if="!hasEvidenceSidebar"
              class="evidence-inline-note"
            >
              这次回答已记录，但暂未挂载品牌命中或引用证据。
            </div>
          </article>

          <aside
            v-if="hasEvidenceSidebar"
            class="evidence-overlay__right"
          >
            <section
              v-if="hasBrandHits"
              class="evidence-card"
            >
              <div class="evidence-card__head">
                <strong>品牌命中</strong>
                <span>{{ currentBrandHits.length }} 个</span>
              </div>
              <div class="evidence-score-list">
                <div
                  v-for="hit in currentBrandHits"
                  :key="`chat-hit-${hit.brand}`"
                  class="evidence-score-list__row"
                >
                  <span>{{ hit.brand }}</span>
                  <b>{{ hit.score }}</b>
                </div>
              </div>
            </section>

            <section
              v-if="hasCitations"
              class="evidence-card"
            >
              <div class="evidence-card__head">
                <strong>引用证据</strong>
                <span>{{ currentCitations.length }} 条</span>
              </div>
              <div class="evidence-citation-list">
                <a
                  v-for="item in currentCitations"
                  :key="`chat-citation-${item.url}`"
                  :href="item.url"
                  target="_blank"
                  rel="noreferrer"
                  class="evidence-citation-list__row"
                >
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.domain || item.url }}</span>
                </a>
              </div>
            </section>
          </aside>
        </section>

        <footer class="evidence-overlay__foot">
          <button
            type="button"
            class="inline-mini"
            :disabled="!canPrev"
            @click="goPrev"
          >
            上一条
          </button>
          <span>{{ currentIndex + 1 }} / {{ chats.length }}</span>
          <button
            type="button"
            class="inline-mini"
            :disabled="!canNext"
            @click="goNext"
          >
            下一条
          </button>
        </footer>
      </article>
    </div>
  </Teleport>
</template>

<style scoped>
.evidence-overlay {
  position: fixed;
  inset: 0;
  z-index: 120;
  background: rgba(15, 23, 42, 0.54);
  padding: 32px;
  display: grid;
  place-items: center;
}

.evidence-overlay__panel {
  border: 1px solid #d1d5db;
  border-radius: 18px;
  background: #ffffff;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  min-height: 0;
  width: min(1180px, 100%);
  max-height: min(900px, calc(100vh - 64px));
  box-shadow: 0 28px 80px -44px rgba(15, 23, 42, 0.7);
}

.evidence-overlay__head,
.evidence-overlay__foot {
  min-height: 72px;
  border-bottom: 1px solid #e5e7eb;
  padding: 0 22px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.evidence-overlay__foot {
  border-top: 1px solid #e5e7eb;
  border-bottom: none;
}

.evidence-overlay__meta {
  min-width: 0;
  display: grid;
  gap: 8px;
}

.evidence-overlay__eyebrow {
  font-size: 12px;
  line-height: 1;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.evidence-overlay__title {
  margin: 0;
  font-size: 24px;
  line-height: 1.35;
  letter-spacing: -0.02em;
  color: #0f172a;
}

.evidence-overlay__meta-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: #6b7280;
  font-size: 13px;
}

.evidence-overlay__meta-chip {
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  display: inline-flex;
  align-items: center;
  font-weight: 600;
}

.evidence-overlay__actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.evidence-overlay__body {
  min-height: 0;
  overflow: hidden;
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
}

.evidence-overlay__body--single {
  grid-template-columns: minmax(0, 1fr);
}

.evidence-overlay__left {
  min-height: 0;
  overflow: auto;
  padding: 22px 24px;
  border-right: 1px solid #e5e7eb;
  display: grid;
  align-content: start;
  gap: 16px;
}

.evidence-overlay__left--full {
  border-right: none;
}

.evidence-section {
  display: grid;
  gap: 10px;
}

.evidence-section__head,
.evidence-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.evidence-section__head strong,
.evidence-card__head strong {
  font-size: 13px;
  line-height: 1.2;
  color: #111827;
}

.evidence-section__head span,
.evidence-card__head span {
  color: #6b7280;
  font-size: 12px;
}

.evidence-overlay__answer {
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: #f8fafc;
  padding: 14px 16px;
  font-size: 14px;
  line-height: 1.65;
  color: #111827;
  white-space: pre-wrap;
}

.evidence-overlay__summary {
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: #ffffff;
  padding: 12px 16px;
}

.evidence-overlay__summary ul {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 8px;
  font-size: 14px;
  line-height: 1.6;
  color: #374151;
}

.evidence-overlay__right {
  min-height: 0;
  overflow: auto;
  padding: 22px 20px;
  display: grid;
  align-content: start;
  gap: 14px;
}

.evidence-card {
  border: 1px solid #d1d5db;
  border-radius: 14px;
  background: #ffffff;
  padding: 14px;
  display: grid;
  gap: 10px;
}

.evidence-score-list {
  display: grid;
  gap: 6px;
}

.evidence-score-list__row {
  min-height: 38px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
}

.evidence-score-list__row b {
  min-width: 34px;
  text-align: right;
  color: #111827;
}

.evidence-citation-list {
  display: grid;
  gap: 6px;
}

.evidence-citation-list__row {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 10px 12px;
  display: grid;
  gap: 4px;
  text-decoration: none;
  color: inherit;
  transition: border-color 140ms ease, background 140ms ease, transform 140ms ease;
}

.evidence-citation-list__row:hover {
  border-color: #94a3b8;
  background: #f9fafb;
  transform: translateY(-1px);
}

.evidence-citation-list__row strong {
  font-size: 13px;
  color: #111827;
  line-height: 1.45;
}

.evidence-citation-list__row span {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
  word-break: break-all;
}

.evidence-empty-state {
  min-height: 96px;
  border: 1px dashed #cbd5e1;
  border-radius: 14px;
  background: #f8fafc;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
  display: grid;
  place-items: center;
  text-align: center;
  padding: 14px;
}

.evidence-inline-note {
  border: 1px dashed #cbd5e1;
  border-radius: 14px;
  background: #f8fafc;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
  padding: 12px 14px;
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
  transition: border-color 140ms ease, color 140ms ease, background 140ms ease;
}

.inline-mini:hover:not(:disabled) {
  border-color: #111827;
  color: #111827;
}

.inline-mini:disabled {
  color: #9ca3af;
  cursor: not-allowed;
}
</style>
