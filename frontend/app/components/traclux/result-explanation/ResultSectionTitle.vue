<script setup lang="ts">
defineProps<{
  title: string
  note: string
}>()
</script>

<template>
  <div class="section-title">
    <h2>{{ title }}</h2>
    <span>{{ note }}</span>
  </div>
</template>

<style scoped>
.section-title {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: baseline;
}

.section-title h2 {
  margin: 0;
  color: #111827;
  font-size: 20px;
  font-weight: 850;
  letter-spacing: -0.045em;
}

.section-title span {
  color: #0f766e;
  font-size: 13px;
  font-weight: 750;
  white-space: nowrap;
}

@media (max-width: 980px) {
  .section-title {
    align-items: start;
  }
}
</style>
