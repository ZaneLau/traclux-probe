<script setup lang="ts">
defineProps<{
  eyebrow: string
  title: string
  description?: string
}>()
</script>

<template>
  <header class="rounded-[22px] border border-white/6 bg-[linear-gradient(180deg,rgba(7,15,31,0.78),rgba(7,15,31,0.4))] p-4 shadow-[0_18px_48px_rgba(0,0,0,0.14)] backdrop-blur-xl">
    <div class="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
      <div class="max-w-4xl">
        <p class="font-display geo-accent-label-muted text-[11px] uppercase tracking-[0.16em]">
          {{ eyebrow }}
        </p>
        <h1 class="font-heading mt-1 text-[24px] leading-none text-white sm:text-[26px]">
          {{ title }}
        </h1>
        <p
          v-if="description"
          class="mt-2 max-w-3xl text-sm leading-6 text-slate-400"
        >
          {{ description }}
        </p>
      </div>
      <div
        v-if="$slots.actions"
        class="geo-workspace-header__actions flex shrink-0 flex-wrap gap-2 xl:justify-end"
      >
        <slot name="actions" />
      </div>
    </div>
  </header>
</template>

<style scoped>
.geo-workspace-header__actions :deep(.geo-workspace-header-action) {
  color: oklch(37.2% 0.044 257.287);
}
</style>
