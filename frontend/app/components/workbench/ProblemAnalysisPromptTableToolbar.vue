<script setup lang="ts">
import { computed, ref } from 'vue'
import type {
  OptionItem,
  PromptColumnKey,
  PromptColumnOption,
  PromptGroupMode
} from '~/types/engine'
import { useDetailsMenuDismiss } from '~/composables/useDetailsMenuDismiss'

const props = defineProps<{
  promptSearch: string
  setPromptSearch: (value: string) => void
  promptGroupMode: PromptGroupMode
  promptGroupModeOptions: OptionItem<PromptGroupMode>[]
  setPromptGroupMode: (value: PromptGroupMode) => void
  selectedPromptColumns: PromptColumnKey[]
  promptColumnOptions: PromptColumnOption[]
  togglePromptColumn: (key: PromptColumnKey) => void
}>()

const activePromptGroupModeLabel = computed(() => {
  return props.promptGroupModeOptions.find(option => option.key === props.promptGroupMode)?.label || '按主题分组'
})

const selectedColumnCount = computed(() => props.selectedPromptColumns.length)
const toolbarRoot = ref<HTMLElement | null>(null)

useDetailsMenuDismiss(toolbarRoot)
</script>

<template>
  <div
    ref="toolbarRoot"
    class="prompt-toolbar prompt-toolbar--local"
  >
    <div class="prompt-toolbar__left">
      <details class="scope-menu scope-menu--local-card">
        <summary class="scope-menu__trigger scope-menu__trigger--local-card">
          <span class="local-toolbar-chip__icon">T</span>
          <span class="local-toolbar-chip__copy">
            <span class="local-toolbar-chip__label">分组方式</span>
            <span class="local-toolbar-chip__value">{{ activePromptGroupModeLabel }}</span>
          </span>
        </summary>
        <div class="scope-menu__panel">
          <label
            v-for="option in props.promptGroupModeOptions"
            :key="`prompt-group-mode-${option.key}`"
            class="scope-menu__option"
          >
            <input
              type="radio"
              :checked="props.promptGroupMode === option.key"
              name="problem-analysis-group-mode"
              @change="props.setPromptGroupMode(option.key)"
            >
            <span class="scope-menu__option-label">{{ option.label }}</span>
          </label>
        </div>
      </details>

      <details class="scope-menu scope-menu--local-card">
        <summary class="scope-menu__trigger scope-menu__trigger--local-card">
          <span class="local-toolbar-chip__icon">#</span>
          <span class="local-toolbar-chip__copy">
            <span class="local-toolbar-chip__label">显示列</span>
            <span class="local-toolbar-chip__value">自定义列</span>
          </span>
          <span class="scope-menu__badge scope-menu__badge--local">{{ selectedColumnCount }}</span>
        </summary>
        <div class="scope-menu__panel">
          <label
            v-for="column in props.promptColumnOptions"
            :key="`prompt-column-${column.key}`"
            class="scope-menu__option"
          >
            <input
              type="checkbox"
              :checked="props.selectedPromptColumns.includes(column.key)"
              @change="props.togglePromptColumn(column.key)"
            >
            <span class="scope-menu__option-label">{{ column.label }}</span>
          </label>
        </div>
      </details>
    </div>

    <div class="prompt-toolbar__right">
      <label class="prompt-search prompt-search--local">
        <span class="prompt-search__icon prompt-search__icon--local">⌕</span>
        <input
          :value="props.promptSearch"
          type="search"
          placeholder="搜索主题或探针"
          @input="props.setPromptSearch(($event.target as HTMLInputElement).value)"
        >
      </label>
    </div>
  </div>
</template>

<style scoped>
.prompt-toolbar--local {
  margin: 12px 0 14px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: linear-gradient(180deg, #fcfcfd 0%, #f8fafc 100%);
  align-items: stretch;
  gap: 12px;
}

.prompt-toolbar--local .prompt-toolbar__left,
.prompt-toolbar--local .prompt-toolbar__right {
  flex-wrap: wrap;
  align-items: stretch;
}

.prompt-toolbar--local .prompt-toolbar__left {
  flex: 1 1 auto;
}

.prompt-toolbar--local .prompt-toolbar__right {
  flex: 1 1 260px;
}

.prompt-toolbar--local .prompt-search {
  min-width: 0;
  width: 100%;
  min-height: 56px;
  padding: 0 14px;
  border-radius: 12px;
  border-color: #dbe2ea;
  background: #ffffff;
}

.prompt-toolbar--local .prompt-search:focus-within {
  border-color: #111827;
  box-shadow: 0 0 0 3px rgba(15, 23, 42, 0.08);
}

.prompt-toolbar--local .prompt-search input {
  font-size: 13px;
}

.prompt-search__icon--local {
  font-size: 13px;
  color: #6b7280;
}

.scope-menu--local-card {
  flex: 0 0 auto;
}

.prompt-toolbar--local .scope-menu__trigger--local-card {
  min-height: 56px;
  min-width: 188px;
  padding: 10px 12px;
  border-radius: 12px;
  border-color: #dbe2ea;
  background: #ffffff;
  gap: 10px;
}

.prompt-toolbar--local .scope-menu__trigger--local-card::after {
  margin-left: auto;
}

.local-toolbar-chip__icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: #111827;
  color: #f9fafb;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  flex: 0 0 auto;
}

.local-toolbar-chip__copy {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.local-toolbar-chip__label {
  color: #6b7280;
  font-size: 11px;
  line-height: 1;
}

.local-toolbar-chip__value {
  color: #111827;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
}

.scope-menu__badge--local {
  min-width: 24px;
  min-height: 22px;
  margin-left: 2px;
  border-color: #dbe2ea;
  background: #f8fafc;
  color: #111827;
  font-size: 11px;
  font-weight: 600;
}

.prompt-toolbar--local .scope-menu[open] .scope-menu__trigger {
  border-color: #cbd5e1;
  box-shadow: 0 0 0 3px rgba(148, 163, 184, 0.12);
}

@media (max-width: 960px) {
  .prompt-toolbar--local .prompt-toolbar__right {
    flex-basis: 100%;
  }

  .prompt-toolbar--local .scope-menu__trigger--local-card {
    min-width: 164px;
  }
}

@media (max-width: 720px) {
  .prompt-toolbar--local .prompt-toolbar__left {
    width: 100%;
  }

  .scope-menu--local-card,
  .prompt-toolbar--local .scope-menu__trigger--local-card {
    width: 100%;
  }
}
</style>
