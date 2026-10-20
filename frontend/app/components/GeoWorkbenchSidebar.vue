<script setup lang="ts">
import { computed, onMounted } from 'vue'

const route = useRoute()
const demoMode = useRuntimeConfig().public.demoMode === true
const navGroups = computed(() => useVisibleDashboardNavGroups())
const authApi = useAuthApi()
const { auth, load, clear } = useAuthSnapshot()

const accountDisplayName = computed(() => auth.value?.username || '未登录')
const accountDisplayId = computed(() => {
  const id = auth.value?.userId || ''
  if (!id) {
    return '—'
  }
  if (id.length <= 12) {
    return id
  }
  return `${id.slice(0, 6)}...${id.slice(-4)}`
})

function isNavItemActive(item: DashboardNavItem) {
  if (item.sectionId) {
    return route.path === '/dashboard' && (route.hash === `#${item.sectionId}` || (!route.hash && item.sectionId === 'data-overview'))
  }
  if (!item.path) {
    return false
  }
  if (route.path === item.path) {
    return true
  }
  return (item.matchPrefixes || []).some(prefix => route.path.startsWith(prefix))
}

async function onNavItemClick(item: DashboardNavItem) {
  if (item.disabled) {
    return
  }
  if (item.sectionId) {
    await navigateTo('/dashboard')
    return
  }
  if (item.path) {
    await navigateTo(item.path)
  }
}

async function signOut() {
  try {
    await authApi.logout()
  } finally {
    clear()
  }
  await navigateTo('/login')
}

onMounted(() => {
  void load()
})
</script>

<template>
  <aside class="nav-rail">
    <div class="nav-brand">
      <GeoBrandMark size="sidebar" />
    </div>

    <section
      v-for="group in navGroups"
      :key="group.title"
      class="nav-group"
    >
      <h2>{{ group.title }}</h2>
      <button
        v-for="item in group.items"
        :key="`${group.title}-${item.label}`"
        type="button"
        class="nav-link"
        :class="[
          isNavItemActive(item) ? 'nav-link--active' : '',
          item.disabled ? 'nav-link--disabled' : ''
        ]"
        :disabled="item.disabled"
        @click="onNavItemClick(item)"
      >
        <span>{{ item.label }}</span>
      </button>
    </section>

    <section class="nav-account-card">
      <h2>{{ demoMode ? '公开案例' : '当前账号' }}</h2>
      <div class="nav-account-card__meta">
        <span class="nav-account-card__name">{{ demoMode ? 'Anker · USB-C 充电器' : accountDisplayName }}</span>
        <span v-if="!demoMode" class="nav-account-card__id">ID: {{ accountDisplayId }}</span>
      </div>
      <div v-if="!demoMode" class="nav-account-card__actions">
        <button
          type="button"
          class="nav-link nav-account-card__logout"
          @click="signOut"
        >
          <span>退出登录</span>
        </button>
      </div>
    </section>
  </aside>
</template>

<style scoped>
.nav-rail {
  border-right: 1px solid var(--engine-shell-border-soft, #d1d5db);
  background: var(--engine-shell-surface-rail, #f8fafc);
  padding: 14px 10px;
  position: sticky;
  top: 0;
  align-self: start;
  height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
}

.nav-brand {
  min-height: var(--engine-shell-brand-height, 42px);
  display: inline-flex;
  align-items: center;
  gap: var(--engine-shell-brand-gap, 8px);
  padding: var(--engine-shell-brand-padding, 2px 8px 0);
}

.nav-brand :deep(.geo-brand-mark) {
  max-width: 100%;
}

.nav-group {
  display: grid;
  gap: 4px;
}

.nav-group h2 {
  margin: 0;
  min-height: 28px;
  display: inline-flex;
  align-items: center;
  padding: 0 8px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--engine-shell-text-secondary, #6b7280);
}

.nav-link {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  border: 1px solid transparent;
  border-radius: var(--engine-shell-radius, 2px);
  color: var(--engine-shell-text-tertiary, #4b5563);
  font-size: 12px;
  padding: 0 8px;
  background: transparent;
  width: 100%;
  cursor: pointer;
  text-decoration: none;
}

.nav-link:hover {
  border-color: var(--engine-shell-border-soft, #d1d5db);
  background: var(--engine-shell-surface-card, #ffffff);
  color: var(--engine-shell-text-primary, #111827);
}

.nav-link--active {
  border-color: var(--engine-shell-border-strong, #9ca3af);
  background: var(--engine-shell-surface-card, #ffffff);
  color: var(--engine-shell-text-primary, #111827);
}

.nav-link--disabled {
  color: var(--engine-shell-border-strong, #9ca3af);
  cursor: not-allowed;
}

.nav-link--disabled:hover {
  border-color: transparent;
  background: transparent;
}

.nav-account-card {
  margin-top: auto;
  border: 1px solid var(--engine-shell-border-soft, #d1d5db);
  border-radius: var(--engine-shell-radius, 2px);
  background: var(--engine-shell-surface-card, #ffffff);
  padding: 10px;
  display: grid;
  gap: 8px;
}

.nav-account-card h2 {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  color: var(--engine-shell-text-primary, #111827);
}

.nav-account-card__meta {
  display: grid;
  gap: 2px;
  padding: 0 8px;
}

.nav-account-card__name {
  font-size: 12px;
  font-weight: 700;
  color: var(--engine-shell-text-primary, #111827);
}

.nav-account-card__id {
  font-size: 11px;
  color: var(--engine-shell-text-secondary, #6b7280);
}

.nav-account-card__actions {
  display: grid;
  gap: 4px;
}

.nav-account-card__logout {
  border-color: var(--engine-shell-border-soft, #d1d5db);
  color: var(--engine-shell-text-primary, #111827);
  background: var(--engine-shell-surface-card, #ffffff);
}

.nav-account-card__logout:hover {
  border-color: var(--engine-shell-border-strong, #9ca3af);
  background: var(--engine-shell-surface-card, #ffffff);
}
</style>
