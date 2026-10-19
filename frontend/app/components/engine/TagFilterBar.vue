<script setup lang="ts">
import type { ProbeTagSeed } from '~/types/engine'

type TagFilterMode = 'or' | 'and'

const props = defineProps<{
  tags: ProbeTagSeed[]
  modelValue: string[]
  mode: TagFilterMode
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: string[]): void
  (event: 'update:mode', value: TagFilterMode): void
}>()

function toggleTag(id: string) {
  const set = new Set(props.modelValue)
  if (set.has(id)) {
    set.delete(id)
  } else {
    set.add(id)
  }
  emit('update:modelValue', Array.from(set))
}

function clearTags() {
  emit('update:modelValue', [])
}

function setMode(mode: TagFilterMode) {
  if (mode === props.mode) {
    return
  }
  emit('update:mode', mode)
}
</script>

<template>
  <section class="tag-filter-bar">
    <div class="tag-filter-bar__head">
      <span class="tag-filter-bar__label">标签筛选</span>
      <div class="tag-filter-bar__mode">
        <button
          type="button"
          class="tag-filter-bar__mode-btn"
          :class="{ 'tag-filter-bar__mode-btn--active': mode === 'or' }"
          @click="setMode('or')"
        >
          任一
        </button>
        <button
          type="button"
          class="tag-filter-bar__mode-btn"
          :class="{ 'tag-filter-bar__mode-btn--active': mode === 'and' }"
          @click="setMode('and')"
        >
          全部
        </button>
      </div>
    </div>

    <div class="tag-filter-bar__chips">
      <button
        v-for="tag in tags"
        :key="`tag-filter-${tag.id}`"
        type="button"
        class="tag-filter-bar__chip"
        :class="{ 'tag-filter-bar__chip--active': modelValue.includes(tag.id) }"
        @click="toggleTag(tag.id)"
      >
        <span
          class="tag-filter-bar__dot"
          :style="{ background: tag.color }"
        />
        <span>{{ tag.label }}</span>
      </button>

      <button
        v-if="modelValue.length"
        type="button"
        class="tag-filter-bar__clear"
        @click="clearTags"
      >
        清空
      </button>
    </div>
  </section>
</template>

<style scoped>
.tag-filter-bar {
  display: grid;
  gap: 6px;
}

.tag-filter-bar__head {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.tag-filter-bar__label {
  font-size: 11px;
  color: #6b7280;
}

.tag-filter-bar__mode {
  min-height: 24px;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  background: #ffffff;
  padding: 1px;
  display: inline-flex;
  align-items: center;
}

.tag-filter-bar__mode-btn {
  min-height: 20px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: #6b7280;
  font-size: 11px;
  padding: 0 8px;
  cursor: pointer;
}

.tag-filter-bar__mode-btn--active {
  background: #111827;
  color: #f9fafb;
}

.tag-filter-bar__chips {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.tag-filter-bar__chip {
  min-height: 24px;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  background: #ffffff;
  color: #4b5563;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 8px;
  cursor: pointer;
}

.tag-filter-bar__chip--active {
  border-color: #111827;
  color: #111827;
}

.tag-filter-bar__dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
}

.tag-filter-bar__clear {
  min-height: 24px;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  background: #f9fafb;
  color: #6b7280;
  font-size: 11px;
  padding: 0 8px;
  cursor: pointer;
}

.tag-filter-bar__clear:hover {
  border-color: #d1d5db;
  color: #111827;
}
</style>
