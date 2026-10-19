<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption, TooltipComponentFormatterCallbackParams } from 'echarts'

interface SourceTypeSlice {
  key: string
  label: string
  value: number
  color: string
}

const props = defineProps<{
  total: number
  slices: SourceTypeSlice[]
  totalLabel?: string
}>()

const chartOption = computed<EChartsOption | null>(() => {
  if (!hasSlices.value) {
    return null
  }

  return {
    tooltip: {
      trigger: 'item',
      formatter: (params: TooltipComponentFormatterCallbackParams) => {
        const item = Array.isArray(params) ? params[0] : params
        if (!item) {
          return ''
        }
        const row = props.slices[item.dataIndex]
        if (!row) {
          return ''
        }
        return `${row.label}<br/>${row.value.toFixed(1)}%`
      }
    },
    color: props.slices.map(item => item.color),
    series: [
      {
        type: 'pie',
        radius: ['66%', '82%'],
        center: ['50%', '50%'],
        startAngle: 90,
        avoidLabelOverlap: true,
        itemStyle: {
          borderColor: '#ffffff',
          borderWidth: 2
        },
        label: { show: false },
        data: props.slices.map(item => ({
          value: Number(item.value.toFixed(1)),
          name: item.label
        }))
      }
    ]
  }
})

const normalizedSlices = computed(() => {
  return props.slices.filter(item => item.value > 0)
})

const hasSlices = computed(() => normalizedSlices.value.length > 0 && normalizedSlices.value.some(item => item.value > 0))
</script>

<template>
  <div class="source-type-donut">
    <GeoLazyEChart
      :option="chartOption"
      :height="220"
      empty-text="暂无类型分布"
    />

    <div
      v-if="hasSlices"
      class="source-type-donut__center"
    >
      <strong>{{ total }}</strong>
      <span>{{ totalLabel || '引用总量' }}</span>
    </div>

    <div
      v-if="hasSlices"
      class="source-type-donut__legend"
    >
      <span
        v-for="item in normalizedSlices"
        :key="`source-type-${item.key}`"
        class="source-type-donut__legend-item"
      >
        <i :style="{ background: item.color }" />
        <b>{{ item.label }}</b>
        <em>{{ item.value.toFixed(1) }}%</em>
      </span>
    </div>
  </div>
</template>

<style scoped>
.source-type-donut {
  position: relative;
  display: grid;
  gap: 8px;
  align-content: start;
}

.source-type-donut__center {
  position: absolute;
  left: 50%;
  top: 106px;
  transform: translate(-50%, -50%);
  display: grid;
  justify-items: center;
  pointer-events: none;
}

.source-type-donut__center strong {
  font-size: 32px;
  line-height: 1;
  color: #111827;
}

.source-type-donut__center span {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
}

.source-type-donut__legend {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.source-type-donut__legend-item {
  min-height: 22px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #4b5563;
}

.source-type-donut__legend-item i {
  width: 8px;
  height: 8px;
  border-radius: 999px;
}

.source-type-donut__legend-item b {
  font-weight: 600;
}

.source-type-donut__legend-item em {
  color: #6b7280;
  font-style: normal;
}
</style>
