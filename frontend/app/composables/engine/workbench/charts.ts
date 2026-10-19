import type { EChartsOption } from 'echarts'
import type { PlatformMetricRankRow, PlatformMetricSection } from '~/types/engine'
import { round1 } from './math'

export function buildLineSeries(
  name: string,
  color: string,
  data: number[],
  lineType: 'solid' | 'dashed' = 'solid',
  yAxisIndex = 0
) {
  return {
    name,
    type: 'line' as const,
    smooth: true,
    symbol: 'circle',
    symbolSize: 6,
    yAxisIndex,
    data,
    lineStyle: {
      width: 2,
      type: lineType,
      color
    },
    itemStyle: {
      color
    }
  }
}

export function buildPlatformMetricOption(
  spec: Pick<PlatformMetricSection, 'key' | 'chartLabel' | 'isPercent' | 'axisMax'>,
  rows: PlatformMetricRankRow[]
): EChartsOption | null {
  if (!rows.length) {
    return null
  }

  const maxValue = Math.max(...rows.map(row => row.value))
  const axisMax = spec.key === 'average-position'
    ? round1(Math.max(spec.axisMax, maxValue + 0.25))
    : round1(Math.max(spec.axisMax, maxValue * 1.1))

  return {
    backgroundColor: 'transparent',
    grid: {
      left: 34,
      right: 18,
      top: 20,
      bottom: 34,
      containLabel: true
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      },
      backgroundColor: '#111827',
      borderColor: '#1f2937',
      textStyle: {
        color: '#f9fafb'
      }
    },
    xAxis: {
      type: 'category',
      data: rows.map(row => row.platform),
      axisLabel: {
        color: '#6b7280',
        fontSize: 11
      },
      axisLine: {
        lineStyle: {
          color: '#e5e7eb'
        }
      }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: axisMax,
      splitNumber: 4,
      axisLabel: {
        color: '#6b7280',
        fontSize: 11,
        formatter: spec.isPercent ? '{value}%' : '{value}'
      },
      splitLine: {
        lineStyle: {
          color: '#f3f4f6'
        }
      }
    },
    series: [
      {
        name: spec.chartLabel,
        type: 'bar',
        barWidth: Math.max(18, Math.min(34, 42 - rows.length * 2)),
        itemStyle: {
          color: '#111827',
          borderRadius: [2, 2, 0, 0]
        },
        data: rows.map(row => row.value)
      }
    ]
  }
}
