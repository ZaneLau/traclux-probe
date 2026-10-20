<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type CSSProperties } from 'vue'
import type { ECharts, EChartsOption } from 'echarts'

const props = withDefaults(
  defineProps<{
    option?: EChartsOption | null
    height?: number | string
    emptyText?: string
    xAxisBandClickSeriesIndex?: number | null
  }>(),
  {
    height: 320,
    emptyText: '暂无图表数据',
    xAxisBandClickSeriesIndex: null
  }
)

const emit = defineEmits<{
  (event: 'chart-click', payload: unknown): void
}>()

const hostRef = ref<HTMLDivElement | null>(null)
const { isVisible } = useLazyChart(hostRef)

const styleObject = computed<CSSProperties>(() => ({
  height: typeof props.height === 'number' ? `${props.height}px` : props.height,
  width: '100%'
}))

let chart: ECharts | null = null
let echartsLib: Awaited<typeof import('echarts')> | null = null
let resizeObserver: ResizeObserver | null = null

function resolveXAxisCategories(option?: EChartsOption | null) {
  const rawXAxis = Array.isArray(option?.xAxis) ? option.xAxis[0] : option?.xAxis
  const categories = (rawXAxis as { data?: unknown[] } | undefined)?.data
  return Array.isArray(categories) ? categories : []
}

function resolveBandClickDataIndex(event: { offsetX?: number, offsetY?: number }) {
  if (!chart || props.xAxisBandClickSeriesIndex === null || props.xAxisBandClickSeriesIndex === undefined) {
    return null
  }

  const offsetX = Number(event.offsetX)
  const offsetY = Number(event.offsetY)
  if (!Number.isFinite(offsetX) || !Number.isFinite(offsetY)) {
    return null
  }

  const pixelPoint: [number, number] = [offsetX, offsetY]
  if (!chart.containPixel({ gridIndex: 0 }, pixelPoint)) {
    return null
  }

  const converted = chart.convertFromPixel({ seriesIndex: props.xAxisBandClickSeriesIndex }, pixelPoint)
  const axisValue = Array.isArray(converted) ? converted[0] : converted
  const categories = resolveXAxisCategories(props.option)

  if (typeof axisValue === 'number' && Number.isFinite(axisValue)) {
    const rounded = Math.round(axisValue)
    if (!categories.length) {
      return rounded >= 0 ? rounded : null
    }
    return rounded >= 0 && rounded < categories.length ? rounded : null
  }

  if (axisValue !== null && axisValue !== undefined && categories.length) {
    const matchedIndex = categories.findIndex(category => String(category) === String(axisValue))
    return matchedIndex >= 0 ? matchedIndex : null
  }

  return null
}

function hasUsableOption(option?: EChartsOption | null) {
  if (!option) {
    return false
  }

  return Object.keys(option).length > 0
}

async function ensureChart() {
  if (!hostRef.value || !hasUsableOption(props.option)) {
    return
  }

  await nextTick()

  const host = hostRef.value
  if (!host || !host.isConnected || host.clientWidth === 0 || host.clientHeight === 0) {
    return
  }

  if (!echartsLib) {
    echartsLib = await import('echarts')
  }

  if (!chart) {
    chart = echartsLib.init(host)
    if (props.xAxisBandClickSeriesIndex === null || props.xAxisBandClickSeriesIndex === undefined) {
      chart.on('click', (params) => {
        emit('chart-click', params)
      })
    } else {
      chart.getZr().on('click', (event) => {
        const dataIndex = resolveBandClickDataIndex(event)
        if (dataIndex === null) {
          return
        }
        emit('chart-click', {
          dataIndex,
          componentType: 'xAxis-band'
        })
      })
    }
  }

  chart.setOption(props.option ?? {}, true)
  await nextTick()
  chart.resize()
}

function handleResize() {
  chart?.resize()
}

watch(
  [isVisible, () => props.option],
  async ([visible]) => {
    if (!visible || !hasUsableOption(props.option)) {
      return
    }

    await ensureChart()
  },
  { deep: true, immediate: true }
)

watch(
  () => props.height,
  async () => {
    if (!chart) {
      return
    }

    await nextTick()
    chart.resize()
  }
)

if (import.meta.client) {
  window.addEventListener('resize', handleResize)
}

onMounted(() => {
  if (!import.meta.client || !hostRef.value || typeof ResizeObserver === 'undefined') {
    return
  }

  resizeObserver = new ResizeObserver(() => {
    chart?.resize()
  })
  resizeObserver.observe(hostRef.value)
})

onBeforeUnmount(() => {
  if (import.meta.client) {
    window.removeEventListener('resize', handleResize)
  }
  resizeObserver?.disconnect()
  resizeObserver = null
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div
    ref="hostRef"
    class="relative w-full"
    :style="styleObject"
  >
    <div
      v-if="!hasUsableOption(option)"
      class="flex h-full items-center justify-center rounded-[20px] border border-white/8 bg-white/5 text-sm text-slate-400"
    >
      {{ emptyText }}
    </div>
  </div>
</template>
