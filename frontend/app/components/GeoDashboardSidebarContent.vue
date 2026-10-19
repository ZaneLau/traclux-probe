<script setup lang="ts">
import { computed } from 'vue'

interface NavigationItem {
  label: string
  path: string
  matchPrefixes: string[]
  icon: string
}

const route = useRoute()
const authApi = useAuthApi()
const { auth, clear } = useAuthSnapshot()

const navigationItems: NavigationItem[] = [
  { label: '工作台', path: '/dashboard', matchPrefixes: ['/dashboard'], icon: 'i-lucide-layout-dashboard' },
  { label: '品牌管理', path: '/brands', matchPrefixes: ['/brands'], icon: 'i-lucide-building-2' }
]

const username = computed(() => auth.value?.username || '未登录用户')

function isActive(item: NavigationItem) {
  return route.path === item.path || item.matchPrefixes.some(prefix => route.path.startsWith(prefix))
}

async function onLogout() {
  try {
    await authApi.logout()
  } finally {
    clear()
  }
  await navigateTo('/login')
}
</script>

<template>
  <div class="flex h-full flex-col p-4">
    <div class="flex items-center gap-4 px-3 pt-2">
      <GeoBrandMark
        tone="dark"
        size="sidebar"
      />
    </div>

    <div class="mt-4 px-3">
      <div class="h-px bg-[linear-gradient(90deg,transparent,rgba(255,255,255,0.14),transparent)]" />
    </div>

    <NuxtLink
      to="/brands/new"
      class="mt-6 rounded-[18px] border border-cyan-300/20 bg-[linear-gradient(135deg,rgba(6,182,212,0.18),rgba(37,99,235,0.14))] px-4 py-4 text-white shadow-[0_18px_45px_rgba(14,165,233,0.12)] transition hover:border-cyan-200/45 hover:bg-[linear-gradient(135deg,rgba(6,182,212,0.28),rgba(37,99,235,0.22))]"
    >
      <p class="font-display geo-accent-label-soft text-[10px] uppercase tracking-[0.28em]">主要操作</p>
      <p class="mt-3 text-[22px] font-semibold leading-none tracking-[0.01em]">添加品牌</p>
    </NuxtLink>

    <nav class="mt-6 grid gap-2">
      <NuxtLink
        v-for="item in navigationItems"
        :key="item.path"
        :to="item.path"
        class="flex items-center gap-4 rounded-[18px] px-3 py-3 transition"
        :class="
          isActive(item)
            ? 'bg-white/[0.08] text-white shadow-[0_12px_28px_rgba(0,0,0,0.14)]'
            : 'text-slate-200 hover:bg-white/[0.04]'
        "
      >
        <span
          class="flex h-[30px] w-[30px] shrink-0 items-center justify-center rounded-[12px]"
          :class="isActive(item) ? 'bg-[linear-gradient(180deg,#11c5d9,#2563eb)] text-white' : 'bg-[#101933] text-cyan-100/85'"
        >
          <UIcon
            :name="item.icon"
            class="size-[17px]"
          />
        </span>
        <span class="text-sm font-medium">{{ item.label }}</span>
      </NuxtLink>
    </nav>

    <div class="mt-auto px-3">
      <p class="font-display geo-accent-label-muted text-[10px] uppercase tracking-[0.24em]">
        当前登录
      </p>
    </div>

    <div class="mt-2 rounded-[20px] border border-white/6 bg-[linear-gradient(180deg,rgba(10,17,35,0.9),rgba(7,13,27,0.82))] p-4 shadow-[0_18px_40px_rgba(0,0,0,0.2)]">
      <div class="flex items-center gap-3">
        <span class="flex h-[34px] w-[34px] shrink-0 items-center justify-center rounded-[12px] bg-[#101933] text-cyan-100/85">
          <svg
            viewBox="0 0 16 16"
            class="h-4 w-4 fill-current"
          >
            <path d="M8 8a2.7 2.7 0 1 0 0-5.4A2.7 2.7 0 0 0 8 8Zm0 1.4c-2.8 0-5 1.6-5 3.6V14h10v-1c0-2-2.2-3.6-5-3.6Z" />
          </svg>
        </span>
        <div class="min-w-0">
          <p class="truncate text-sm font-medium text-white">
            {{ username }}
          </p>
        </div>
      </div>
      <button
        type="button"
        class="mt-4 inline-flex min-h-[38px] w-full items-center justify-center rounded-[14px] border border-white/8 bg-white/[0.04] px-4 text-[11px] font-semibold uppercase tracking-[0.18em] text-white transition hover:border-cyan-300/25 hover:bg-white/[0.08]"
        @click="onLogout"
      >
        退出登录
      </button>
    </div>
  </div>
</template>
