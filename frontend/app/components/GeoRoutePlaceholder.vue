<script setup lang="ts">
defineProps<{
  title: string
  description: string
  next?: string
}>()
</script>

<template>
  <div class="geo-page-shell min-h-screen p-6 md:p-8">
    <div class="mx-auto max-w-4xl">
      <UCard class="geo-panel rounded-[28px]">
        <template #header>
          <div>
            <p class="font-display geo-accent-label text-xs uppercase tracking-[0.28em]">
              页面占位
            </p>
            <h1 class="font-heading mt-3 text-4xl text-white">
              {{ title }}
            </h1>
          </div>
        </template>

        <div class="space-y-4 text-slate-300">
          <p class="text-lg leading-8">
            {{ description }}
          </p>
          <div class="rounded-3xl border border-white/10 bg-white/4 p-5 text-sm leading-7 text-slate-400">
            该页面当前仅保留路由占位，后续会替换为正式业务实现与最终版 Traclux 探镜 UI。
          </div>
        </div>

        <template #footer>
          <div class="flex flex-wrap gap-3">
            <UButton to="/dashboard">
              返回 Dashboard
            </UButton>
            <UButton
              v-if="next"
              :to="next"
              color="neutral"
              variant="outline"
            >
              查看下一页
            </UButton>
          </div>
        </template>
      </UCard>
    </div>
  </div>
</template>
