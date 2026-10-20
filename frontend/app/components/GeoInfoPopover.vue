<script setup lang="ts">
const props = withDefaults(defineProps<{
  text: string
  ariaLabel?: string
  side?: 'top' | 'right' | 'bottom' | 'left'
  align?: 'start' | 'center' | 'end'
}>(), {
  ariaLabel: '查看说明',
  side: 'bottom',
  align: 'start'
})
</script>

<template>
  <UPopover
    :content="{ side: props.side, align: props.align, sideOffset: 8 }"
    :ui="{
      content: 'rounded-none border border-gray-200 bg-white p-0 shadow-none'
    }"
  >
    <slot>
      <button
        type="button"
        class="geo-info-trigger"
        :aria-label="props.ariaLabel"
      >
        ?
      </button>
    </slot>

    <template #content>
      <div class="geo-info-popover">
        {{ props.text }}
      </div>
    </template>
  </UPopover>
</template>

<style scoped>
.geo-info-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  background: #ffffff;
  color: #4b5563;
  font-size: 11px;
  line-height: 1;
  font-weight: 600;
  cursor: help;
}

.geo-info-popover {
  max-width: 272px;
  padding: 14px 16px;
  font-size: 12px;
  line-height: 1.7;
  color: #111827;
  text-align: left;
}
</style>
