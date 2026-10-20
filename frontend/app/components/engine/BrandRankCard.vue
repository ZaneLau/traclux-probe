<script setup lang="ts">
interface BrandRankRow {
  rank: number
  brand: string
  visibility: number
  sentiment: number
  position: number
}

const props = defineProps<{
  title?: string
  rows: BrandRankRow[]
}>()
</script>

<template>
  <article class="surface section-surface brand-rank-card">
    <header class="surface-head surface-head--compact">
      <strong>{{ props.title || 'Brands 排名' }}</strong>
      <span>{{ rows.length }}</span>
    </header>

    <div class="brand-rank-table">
      <div class="brand-rank-table__head">
        <span>#</span>
        <span>品牌</span>
        <span>可见度</span>
        <span>情感</span>
        <span>位次</span>
      </div>
      <div
        v-for="row in rows"
        :key="`brand-rank-${row.rank}-${row.brand}`"
        class="brand-rank-table__row"
      >
        <span>#{{ row.rank }}</span>
        <span class="brand-rank-table__brand">{{ row.brand }}</span>
        <span>{{ row.visibility.toFixed(1) }}%</span>
        <span>{{ row.sentiment }}</span>
        <span>{{ row.position.toFixed(1) }}</span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.brand-rank-table {
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
}

.brand-rank-table__head,
.brand-rank-table__row {
  min-height: 38px;
  display: grid;
  align-items: center;
  grid-template-columns: 44px minmax(0, 1.1fr) 94px 74px 70px;
  gap: 8px;
  padding: 0 10px;
  font-size: 12px;
}

.brand-rank-table__head {
  background: #f8fafc;
  border-bottom: 1px solid #d1d5db;
  color: #6b7280;
  font-weight: 600;
}

.brand-rank-table__row {
  border-bottom: 1px solid #e5e7eb;
  color: #111827;
}

.brand-rank-table__row:last-child {
  border-bottom: none;
}

.brand-rank-table__brand {
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
