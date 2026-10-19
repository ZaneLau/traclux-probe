import type { Ref } from 'vue'

interface WorkbenchSelectionState {
  selectedBrand: Ref<string>
  selectedBrandSpaceId: Ref<string>
}

export function useWorkbenchSelectionState(): WorkbenchSelectionState {
  const selectedBrand = useState<string>('workbench-selected-brand', () => '')
  const selectedBrandSpaceId = useState<string>('workbench-selected-brand-space-id', () => '')

  return {
    selectedBrand,
    selectedBrandSpaceId
  }
}
