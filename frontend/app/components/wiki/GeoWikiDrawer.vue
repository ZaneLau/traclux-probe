<script setup lang="ts">
const route = useRoute()
const { state, payload, close } = useWikiDrawer()

const isOpen = computed(() => state.value.open && Boolean(payload.value))

const nextSteps = computed(() => payload.value?.object.nextSteps || [])
const formula = computed(() => payload.value?.context?.formula || '')
const interpretation = computed(() => payload.value?.context?.interpretation || [])
const rules = computed(() => payload.value?.context?.rules || [])
const boundaries = computed(() => payload.value?.context?.boundaries || [])
const commonMisreads = computed(() => payload.value?.context?.commonMisreads || [])
const usageNotes = computed(() => {
  return [
    ...rules.value.map(item => ({
      key: `rule-${item}`,
      label: '使用',
      text: item
    })),
    ...boundaries.value.map(item => ({
      key: `boundary-${item}`,
      label: '边界',
      text: item
    })),
    ...commonMisreads.value.map(item => ({
      key: `misread-${item}`,
      label: '误读',
      text: item
    }))
  ]
})

watch(
  () => route.fullPath,
  () => {
    if (state.value.open) {
      close()
    }
  }
)

watch(isOpen, (open) => {
  if (!import.meta.client) {
    return
  }
  document.body.style.overflow = open ? 'hidden' : ''
})

onBeforeUnmount(() => {
  if (import.meta.client) {
    document.body.style.overflow = ''
  }
})

function onMaskClick(event: MouseEvent) {
  if (event.target === event.currentTarget) {
    close()
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="isOpen && payload"
      class="geo-wiki-layer"
      @click="onMaskClick"
    >
      <aside
        class="geo-wiki-drawer"
        aria-label="Wiki 说明抽屉"
      >
        <header class="geo-wiki-drawer__head">
          <div class="geo-wiki-drawer__meta">
            <span class="geo-wiki-drawer__eyebrow">统一说明</span>
            <h2>{{ payload.object.displayName }}</h2>
            <p>{{ payload.object.summary }}</p>
          </div>
          <button
            type="button"
            class="geo-wiki-drawer__close"
            aria-label="关闭说明"
            @click="close"
          >
            关闭
          </button>
        </header>

        <div class="geo-wiki-drawer__body">
          <section class="geo-wiki-drawer__facts">
            <article>
              <span>更新时间</span>
              <strong>{{ payload.updatedAtText }}</strong>
            </article>
            <article>
              <span>适用范围</span>
              <strong>{{ payload.scopeText }}</strong>
            </article>
            <article v-if="payload.sourceLabel">
              <span>定义依据</span>
              <strong>{{ payload.sourceLabel }}</strong>
            </article>
            <article v-if="payload.contextTitle">
              <span>当前页位置</span>
              <strong>{{ payload.contextTitle }}</strong>
            </article>
          </section>

          <section
            v-if="payload.context"
            class="geo-wiki-drawer__section"
          >
            <h3>这是什么</h3>
            <p>{{ payload.context.definition }}</p>
          </section>

          <section
            v-if="payload.context"
            class="geo-wiki-drawer__section"
          >
            <h3>为什么这里要看它</h3>
            <p>{{ payload.context.importance }}</p>
          </section>

          <section
            v-if="payload.context"
            class="geo-wiki-drawer__section"
          >
            <h3>当前页怎么读</h3>
            <p>{{ payload.context.readingGuide }}</p>
          </section>

          <section
            v-if="formula"
            class="geo-wiki-drawer__section"
          >
            <h3>计算口径</h3>
            <p>{{ formula }}</p>
          </section>

          <section
            v-if="interpretation.length"
            class="geo-wiki-drawer__section"
          >
            <h3>怎么看这个对象</h3>
            <ul>
              <li
                v-for="item in interpretation"
                :key="`reading-${item.label}`"
              >
                <strong>{{ item.label }}</strong>：{{ item.text }}
              </li>
            </ul>
          </section>

          <section
            v-if="usageNotes.length"
            class="geo-wiki-drawer__section"
          >
            <h3>使用与边界</h3>
            <ul>
              <li
                v-for="item in usageNotes"
                :key="item.key"
              >
                <strong>{{ item.label }}</strong>：{{ item.text }}
              </li>
            </ul>
          </section>

          <section class="geo-wiki-drawer__section">
            <h3>下一步</h3>
            <div
              v-if="nextSteps.length"
              class="geo-wiki-drawer__next-steps"
            >
              <template
                v-for="(item, index) in nextSteps"
                :key="`next-step-${index}-${item.label}`"
              >
                <NuxtLink
                  v-if="item.route"
                  class="geo-wiki-drawer__next-link"
                  :to="{ path: item.route, query: item.query }"
                  @click="close"
                >
                  <strong>{{ item.label }}</strong>
                  <span v-if="item.note">{{ item.note }}</span>
                </NuxtLink>
                <article
                  v-else
                  class="geo-wiki-drawer__next-link geo-wiki-drawer__next-link--static"
                >
                  <strong>{{ item.label }}</strong>
                  <span v-if="item.note">{{ item.note }}</span>
                </article>
              </template>
            </div>
            <p v-else>当前对象没有额外跳转动作，但你可以保留当前页继续判断。</p>
          </section>
        </div>
      </aside>
    </div>
  </Teleport>
</template>

<style scoped>
.geo-wiki-layer {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: flex;
  justify-content: flex-end;
  background: rgba(15, 23, 42, 0.14);
}

.geo-wiki-drawer {
  width: min(460px, 100%);
  height: 100%;
  overflow-y: auto;
  background: #fffdf9;
  border-left: 1px solid rgba(17, 24, 39, 0.08);
  box-shadow: -18px 0 48px rgba(15, 23, 42, 0.12);
}

.geo-wiki-drawer__head {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 24px 18px;
  background: rgba(255, 253, 249, 0.95);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(17, 24, 39, 0.08);
}

.geo-wiki-drawer__meta {
  display: grid;
  gap: 8px;
}

.geo-wiki-drawer__eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #92400e;
}

.geo-wiki-drawer__meta h2 {
  margin: 0;
  font-size: 26px;
  line-height: 1.2;
  color: #111827;
}

.geo-wiki-drawer__meta p {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: #4b5563;
}

.geo-wiki-drawer__close {
  flex: none;
  padding: 8px 12px;
  border: 1px solid #d6d3d1;
  background: #fff;
  color: #374151;
  font-size: 12px;
  cursor: pointer;
}

.geo-wiki-drawer__body {
  display: grid;
  gap: 18px;
  padding: 22px 24px 28px;
}

.geo-wiki-drawer__facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.geo-wiki-drawer__facts article {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid rgba(17, 24, 39, 0.08);
}

.geo-wiki-drawer__facts span {
  font-size: 11px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #6b7280;
}

.geo-wiki-drawer__facts strong {
  font-size: 13px;
  line-height: 1.6;
  color: #111827;
}

.geo-wiki-drawer__section {
  display: grid;
  gap: 10px;
  padding: 16px 18px;
  background: #fff;
  border: 1px solid rgba(17, 24, 39, 0.08);
}

.geo-wiki-drawer__section h3 {
  margin: 0;
  font-size: 13px;
  color: #111827;
}

.geo-wiki-drawer__section p,
.geo-wiki-drawer__section li {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: #374151;
}

.geo-wiki-drawer__section ul {
  display: grid;
  gap: 8px;
  margin: 0;
  padding-left: 18px;
}

.geo-wiki-drawer__next-steps {
  display: grid;
  gap: 10px;
}

.geo-wiki-drawer__next-link {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  color: #111827;
  text-decoration: none;
  border: 1px solid rgba(17, 24, 39, 0.08);
  background: #fcfcfb;
}

.geo-wiki-drawer__next-link strong {
  font-size: 14px;
}

.geo-wiki-drawer__next-link span {
  font-size: 12px;
  line-height: 1.7;
  color: #6b7280;
}

.geo-wiki-drawer__next-link:hover {
  border-color: rgba(17, 24, 39, 0.18);
}

@media (max-width: 768px) {
  .geo-wiki-drawer {
    width: 100%;
  }

  .geo-wiki-drawer__facts {
    grid-template-columns: 1fr;
  }
}
</style>
