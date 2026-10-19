<script setup lang="ts">
import type { WikiTriggerKind } from '~/types/wiki'

const props = withDefaults(defineProps<{
  entrypointId: string
  ariaLabel?: string
  variant?: 'auto' | 'inline-underline' | 'quiet'
}>(), {
  variant: 'auto'
})

const { canOpen, resolveEntry, resolveObject } = useWikiRegistry()
const { openByEntrypoint } = useWikiDrawer()

const entrypoint = computed(() => resolveEntry(props.entrypointId))
const object = computed(() => resolveObject(props.entrypointId))
const isOpenable = computed(() => canOpen(props.entrypointId))

function resolveVariant(triggerKind?: WikiTriggerKind | null) {
  if (props.variant !== 'auto') {
    return props.variant
  }
  if (triggerKind === 'title-help' || triggerKind === 'empty-state-link' || triggerKind === 'action-explainer') {
    return 'quiet'
  }
  return 'inline-underline'
}

const normalizedVariant = computed(() => resolveVariant(entrypoint.value?.triggerKind || null))

function handleClick() {
  if (!isOpenable.value) {
    return
  }
  openByEntrypoint(props.entrypointId)
}
</script>

<template>
  <button
    v-if="isOpenable"
    type="button"
    :aria-label="props.ariaLabel || undefined"
    :class="[
      'geo-wiki-trigger',
      normalizedVariant === 'quiet' ? 'geo-wiki-trigger--quiet' : 'geo-wiki-trigger--underline'
    ]"
    @click="handleClick"
  >
    <slot>{{ object?.displayName }}</slot>
  </button>
  <span
    v-else
    class="geo-wiki-trigger geo-wiki-trigger--plain"
  >
    <slot>{{ object?.displayName }}</slot>
  </span>
</template>

<style scoped>
.geo-wiki-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
}

.geo-wiki-trigger--underline {
  cursor: pointer;
  text-decoration-line: underline;
  text-decoration-style: solid;
  text-decoration-color: rgba(17, 24, 39, 0.55);
  text-decoration-thickness: 1px;
  text-underline-offset: 3px;
}

.geo-wiki-trigger--underline:hover {
  text-decoration-color: rgba(17, 24, 39, 0.95);
}

.geo-wiki-trigger--quiet {
  cursor: pointer;
  color: #374151;
  text-decoration-line: underline;
  text-decoration-style: dotted;
  text-decoration-color: rgba(55, 65, 81, 0.6);
  text-underline-offset: 3px;
}

.geo-wiki-trigger--plain {
  cursor: inherit;
  text-decoration: none;
}
</style>
