<script setup lang="ts">
const ASSET_ALT = '探镜 Traclux'
const runtimeConfig = useRuntimeConfig()
const baseURL = runtimeConfig.app.baseURL || '/'
const logoSrc = `${baseURL.replace(/\/$/, '')}/identity/traclux-logo.svg`

withDefaults(defineProps<{
  tone?: 'dark' | 'light'
  size?: 'compact' | 'sidebar' | 'hero'
  showPreviewBadge?: boolean
}>(), {
  tone: 'light',
  size: 'compact',
  showPreviewBadge: true
})
</script>

<template>
  <div
    class="geo-brand-mark"
    :class="[
      `geo-brand-mark--${tone}`,
      `geo-brand-mark--${size}`
    ]"
  >
    <span class="geo-brand-mark__surface">
      <img
        class="geo-brand-mark__logo"
        :src="logoSrc"
        :alt="ASSET_ALT"
      >
    </span>
    <span
      v-if="showPreviewBadge"
      class="geo-brand-mark__badge"
    >
      <span class="geo-brand-mark__badge-item geo-brand-mark__badge-item--preview">预览版</span>
    </span>
  </div>
</template>

<style scoped>
.geo-brand-mark {
  display: inline-flex;
  align-items: flex-start;
  max-width: 100%;
}

.geo-brand-mark__surface {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  max-width: 100%;
  transition:
    background-color 180ms ease,
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.geo-brand-mark__logo {
  display: block;
  width: auto;
  max-width: 100%;
}

.geo-brand-mark__badge {
  margin-left: 6px;
  display: inline-flex;
  align-self: center;
  justify-items: start;
  gap: 0;
  min-height: 0;
  font-size: 10px;
  line-height: 1;
  color: #6b7280;
  letter-spacing: 0.05em;
  white-space: nowrap;
}

.geo-brand-mark__badge-item {
  display: inline-flex;
  align-items: center;
}

.geo-brand-mark__badge-item--preview {
  min-height: 12px;
  padding: 1px 6px;
  border: 1px solid #d1d5db;
  background: #ffffff;
  color: #6b7280;
  font-size: 10px;
  letter-spacing: 0.06em;
}

.geo-brand-mark--light .geo-brand-mark__surface {
  padding: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.geo-brand-mark--dark .geo-brand-mark__surface {
  padding: 8px 11px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  background: rgba(242, 238, 231, 0.96);
  box-shadow: 0 16px 42px -30px rgba(15, 23, 42, 0.62);
}

.geo-brand-mark--compact .geo-brand-mark__logo {
  height: 16px;
}

.geo-brand-mark--sidebar .geo-brand-mark__logo {
  height: 18px;
}

.geo-brand-mark--hero .geo-brand-mark__logo {
  height: 20px;
}

.geo-brand-mark--light.geo-brand-mark--compact .geo-brand-mark__logo {
  height: 18px;
}

.geo-brand-mark--light.geo-brand-mark--sidebar .geo-brand-mark__logo {
  height: 19px;
}

.geo-brand-mark--light.geo-brand-mark--hero .geo-brand-mark__logo {
  height: 24px;
}
</style>
