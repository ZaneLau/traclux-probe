import { computed } from 'vue'
import type {
  PromptColumnKey,
  PromptColumnOption,
  PromptMetricRow
} from '~/types/engine'

const promptColumnOptions: PromptColumnOption[] = [
  { key: 'visibility-rank', label: '可见度排名' },
  { key: 'visibility-score', label: '可见度得分' },
  { key: 'average-position', label: '平均位次' },
  { key: 'citation-share', label: '引用占比' }
]

export function useProblemAnalysisViewState() {
  const selectedPromptTopics = useState<string[]>('problem-analysis-selected-prompt-topics', () => [])

  const promptVisibleColumns = computed<PromptColumnOption[]>(() => promptColumnOptions)

  function resolvePromptCellValue(row: PromptMetricRow, key: PromptColumnKey) {
    if (key === 'visibility-rank') {
      if (!Number.isFinite(row.visibilityRank)) {
        return '—'
      }
      return `#${row.visibilityRank}`
    }
    if (key === 'visibility-score') {
      if (!Number.isFinite(row.visibilityScore)) {
        return '—'
      }
      return `${row.visibilityScore.toFixed(1)}%`
    }
    if (key === 'average-position') {
      if (!Number.isFinite(row.averagePosition)) {
        return '—'
      }
      return row.averagePosition.toFixed(1)
    }
    if (!Number.isFinite(row.citationShare)) {
      return '—'
    }
    return `${row.citationShare.toFixed(1)}%`
  }

  return {
    promptColumnOptions,
    promptVisibleColumns,
    resolvePromptCellValue,
    selectedPromptTopics
  }
}
