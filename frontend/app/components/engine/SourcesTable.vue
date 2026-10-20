<script setup lang="ts">
import { computed } from 'vue'
import type { SourceType } from '~/types/engine'

interface SourcesTableRow {
  id: string
  label: string
  rawLabel?: string
  hostKey?: string
  type: SourceType | 'unknown'
  used: number
  avgCitations: number
  share: number
  delta: number | null
  url?: string
}

const props = withDefaults(defineProps<{
  rows: SourcesTableRow[]
  showUrlAction?: boolean
  emptyText?: string
  visibleRows?: number | null
  shareEntrypointId?: string | null
}>(), {
  showUrlAction: true,
  emptyText: '暂无来源数据',
  visibleRows: null,
  shareEntrypointId: null
})

const emit = defineEmits<{
  (event: 'select', rowId: string): void
  (event: 'open-provenance', row: SourcesTableRow): void
}>()

const typeLabel: Record<SourceType | 'unknown', string> = {
  editorial: '编辑媒体',
  corporate: '企业站点',
  ugc: '社区 UGC',
  reference: '参考资料',
  you: '自有资产',
  unknown: '待分类'
}

function onSelect(id: string) {
  emit('select', id)
}

function openProvenance(row: SourcesTableRow) {
  emit('open-provenance', row)
}

function formatDelta(value: number | null) {
  if (value == null) {
    return '--'
  }
  return `${value >= 0 ? '+' : ''}${value.toFixed(1)}%`
}

const isScrollable = computed(() => Boolean(props.visibleRows && props.visibleRows > 0))

const tableStyle = computed(() => {
  if (!isScrollable.value || !props.visibleRows) {
    return {}
  }
  return {
    '--sources-table-visible-rows': `${props.visibleRows}`
  }
})
</script>

<template>
  <div
    class="sources-table"
    :class="{ 'sources-table--scrollable': isScrollable }"
    :style="tableStyle"
  >
    <div class="sources-table__head">
      <span>#</span>
      <span>来源</span>
      <span>类型</span>
      <span>引用次数</span>
      <span>均次引用</span>
      <span>
        <WikiGeoWikiTrigger
          v-if="props.shareEntrypointId"
          :entrypoint-id="props.shareEntrypointId"
        >
          引用占比
        </WikiGeoWikiTrigger>
        <template v-else>
          引用占比
        </template>
      </span>
      <span>变化</span>
      <span v-if="showUrlAction">操作</span>
    </div>

    <div class="sources-table__body">
      <div
        v-for="(row, index) in rows"
        :key="`sources-row-${row.id}`"
        class="sources-table__row"
        role="button"
        tabindex="0"
        @click="onSelect(row.id)"
        @keydown.enter.prevent="onSelect(row.id)"
        @keydown.space.prevent="onSelect(row.id)"
      >
        <span>{{ index + 1 }}</span>
        <span class="sources-table__name">{{ row.label }}</span>
        <span class="sources-table__type">{{ typeLabel[row.type] }}</span>
        <span>{{ row.used.toFixed(0) }}</span>
        <span>{{ row.avgCitations.toFixed(1) }}</span>
        <span class="sources-table__share-trigger">
          {{ row.share.toFixed(1) }}%
        </span>
        <span :class="row.delta == null ? 'delta-muted' : row.delta >= 0 ? 'delta-up' : 'delta-down'">
          {{ formatDelta(row.delta) }}
        </span>
        <span
          v-if="showUrlAction"
          class="sources-table__action"
        >
          <a
            v-if="row.url"
            :href="row.url"
            target="_blank"
            rel="noreferrer"
            @click.stop
          >访问</a>
          <i v-else>-</i>
        </span>
      </div>

      <div
        v-if="!rows.length"
        class="sources-table__empty"
      >
        {{ props.emptyText }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.sources-table {
  --sources-table-row-height: 38px;
  border: 1px solid #d1d5db;
  border-radius: 2px;
  overflow: hidden;
}

.sources-table--scrollable .sources-table__body {
  height: calc(var(--sources-table-row-height) * var(--sources-table-visible-rows));
  overflow-y: auto;
  scrollbar-gutter: stable;
}

.sources-table__head,
.sources-table__row {
  min-height: var(--sources-table-row-height);
  display: grid;
  align-items: center;
  grid-template-columns: 40px minmax(0, 1.3fr) 104px 88px 88px 82px 78px 68px;
  gap: 8px;
  padding: 0 10px;
  font-size: 12px;
  text-align: left;
}

.sources-table__head {
  background: #f8fafc;
  border-bottom: 1px solid #d1d5db;
  color: #6b7280;
  font-weight: 600;
}

.sources-table__body {
  min-height: 0;
}

.sources-table__row {
  width: 100%;
  border: none;
  border-bottom: 1px solid #e5e7eb;
  background: #ffffff;
  color: #111827;
  cursor: pointer;
}

.sources-table__row:last-child {
  border-bottom: none;
}

.sources-table__row:hover {
  background: #f9fafb;
}

.sources-table__name {
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sources-table__type {
  color: #4b5563;
}

.sources-table__action a {
  color: #2563eb;
  text-decoration: none;
}

.sources-table__action a:hover {
  text-decoration: underline;
}

.sources-table__share-trigger {
  width: fit-content;
  color: #111827;
  font: inherit;
  font-weight: 700;
  line-height: 1.05;
  padding: 0 2px;
  text-align: left;
}

.sources-table__action i {
  color: #9ca3af;
  font-style: normal;
}

.sources-table__empty {
  min-height: 86px;
  display: grid;
  place-items: center;
  color: #6b7280;
  font-size: 13px;
}

.sources-table--scrollable .sources-table__empty {
  min-height: 100%;
}

.delta-up {
  color: #047857;
}

.delta-down {
  color: #b91c1c;
}

.delta-muted {
  color: #9ca3af;
}
</style>
