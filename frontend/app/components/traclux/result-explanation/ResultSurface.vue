<script setup lang="ts">
import ResultSectionTitle from './ResultSectionTitle.vue'

withDefaults(defineProps<{
  title: string
  note: string
  ariaLabel?: string
  tone?: 'default' | 'formula' | 'muted'
}>(), {
  ariaLabel: undefined,
  tone: 'default'
})
</script>

<template>
  <section class="result-surface" :data-tone="tone" :aria-label="ariaLabel || title">
    <ResultSectionTitle :title="title" :note="note" />
    <slot />
  </section>
</template>

<style scoped>
.result-surface {
  min-width: 0;
  border: 1px solid #d5dce3;
  border-radius: 2px;
  background: #fff;
  box-shadow: 0 14px 28px -24px rgba(15, 23, 42, 0.42);
  padding: 18px;
}

.result-surface[data-tone="formula"] {
  background: #fbfcfd;
}

.result-surface[data-tone="muted"] {
  background: #f8fafc;
}
</style>
