<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  eyebrow?: string
  title: string
  subtitle?: string
  compact?: boolean
}>()

const sectionLabel = computed(() => props.eyebrow || '页面概览')
</script>

<template>
  <main class="h-screen overflow-hidden bg-[#08111f] text-white">
    <div class="h-screen overflow-hidden bg-[radial-gradient(circle_at_top_left,rgba(34,211,238,0.09),transparent_22%),radial-gradient(circle_at_bottom_right,rgba(14,165,233,0.09),transparent_20%),linear-gradient(180deg,#07101d_0%,#091423_100%)]">
      <div class="mx-auto flex h-screen max-w-[1700px] gap-5 px-3 py-3 lg:px-5">
        <aside class="hidden h-[calc(100vh-24px)] w-[274px] shrink-0 flex-col rounded-[28px] border border-white/6 bg-[linear-gradient(180deg,rgba(5,11,28,0.94),rgba(4,10,24,0.9))] shadow-[0_30px_90px_rgba(0,0,0,0.34)] backdrop-blur-xl lg:flex">
          <GeoDashboardSidebarContent />
        </aside>

        <div class="min-w-0 flex-1 overflow-y-auto pr-1">
          <header
            :class="
              props.compact
                ? 'rounded-[22px] border border-white/6 bg-[linear-gradient(180deg,rgba(7,15,31,0.78),rgba(7,15,31,0.4))] p-4 shadow-[0_18px_48px_rgba(0,0,0,0.14)] backdrop-blur-xl sm:p-4'
                : 'rounded-[32px] border border-white/8 bg-[linear-gradient(135deg,rgba(14,24,42,0.95),rgba(8,15,29,0.88))] p-6 shadow-[0_24px_80px_rgba(0,0,0,0.28)]'
            "
          >
            <div
              class="flex flex-col xl:flex-row xl:items-start xl:justify-between"
              :class="props.compact ? 'gap-2' : 'gap-5'"
            >
              <div class="max-w-4xl">
                <p
                  class="font-display text-[11px] uppercase tracking-[0.16em]"
                  :class="props.compact ? 'geo-accent-label-muted' : 'geo-accent-label'"
                >
                  {{ sectionLabel }}
                </p>
                <h2
                  class="font-heading text-white"
                  :class="props.compact ? 'mt-1 text-[24px] leading-none sm:text-[26px]' : 'mt-4 text-4xl sm:text-5xl'"
                >
                  {{ title }}
                </h2>
                <p
                  v-if="subtitle && !props.compact"
                  class="max-w-3xl text-slate-300"
                  :class="props.compact ? 'mt-1.5 text-sm leading-6 text-slate-400' : 'mt-4 text-sm leading-7 sm:text-[15px]'"
                >
                  {{ subtitle }}
                </p>
              </div>

              <div
                v-if="$slots.actions"
                class="flex shrink-0 flex-wrap gap-2 xl:justify-end"
              >
                <slot name="actions" />
              </div>
            </div>
          </header>

          <div class="mt-6">
            <slot />
          </div>
        </div>
      </div>
    </div>
  </main>
</template>
