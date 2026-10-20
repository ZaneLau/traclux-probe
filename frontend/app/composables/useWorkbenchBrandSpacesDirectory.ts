import type { BrandSpaceListItem } from '~/types/brand-space'

interface WorkbenchBrandSelectionOption {
  key: string
  label: string
  brandSpaceId: string | null
}

function normalizeBrandText(value?: string | null) {
  return (value || '')
    .trim()
    .toLowerCase()
    .replace(/^https?:\/\//, '')
    .replace(/www\./g, '')
    .replace(/[^\p{L}\p{N}]+/gu, '')
}

function rankBrandSpaces(items: BrandSpaceListItem[]) {
  return items.slice().sort((left, right) => {
    const rightUpdatedAt = right.lastRunAt || right.lastGeneratedAt || ''
    const leftUpdatedAt = left.lastRunAt || left.lastGeneratedAt || ''
    const updatedAtDelta = String(rightUpdatedAt).localeCompare(String(leftUpdatedAt))
    if (updatedAtDelta !== 0) {
      return updatedAtDelta
    }

    const brandNameDelta = trimDisplayText(left.brandName).localeCompare(trimDisplayText(right.brandName), 'zh-Hans-CN')
    if (brandNameDelta !== 0) {
      return brandNameDelta
    }

    return left.brandSpaceId.localeCompare(right.brandSpaceId, 'zh-Hans-CN')
  })
}

function trimDisplayText(value?: string | null) {
  return String(value || '').trim()
}

function resolveBrandSpaceOptionLabels(items: BrandSpaceListItem[]) {
  const counts = new Map<string, number>()
  items.forEach((item) => {
    const token = normalizeBrandText(item.brandName)
    counts.set(token, (counts.get(token) || 0) + 1)
  })

  return items.map((item) => {
    const brandName = trimDisplayText(item.brandName) || '未命名品牌'
    const token = normalizeBrandText(brandName)
    if ((counts.get(token) || 0) <= 1) {
      return brandName
    }

    const category = trimDisplayText(item.category)
    const shortId = item.brandSpaceId.slice(0, 8)
    return category
      ? `${brandName} · ${category} · ${shortId}`
      : `${brandName} · ${shortId}`
  })
}

export function buildWorkbenchBrandSelectionOptions(
  items: BrandSpaceListItem[]
): WorkbenchBrandSelectionOption[] {
  if (!items.length) {
    return []
  }

  const realLabels = resolveBrandSpaceOptionLabels(items)
  return items.map((item, index) => ({
    key: item.brandSpaceId,
    label: realLabels[index] || trimDisplayText(item.brandName) || '未命名品牌',
    brandSpaceId: item.brandSpaceId
  }))
}

export function useWorkbenchBrandSpacesDirectory() {
  const brandSpacesApi = useBrandSpacesApi()
  const { auth } = useAuthSnapshot()
  const items = useState<BrandSpaceListItem[]>('workbench-brand-spaces-directory-items', () => [])
  const loading = useState<boolean>('workbench-brand-spaces-directory-loading', () => false)
  const loaded = useState<boolean>('workbench-brand-spaces-directory-loaded', () => false)
  const error = useState<string | null>('workbench-brand-spaces-directory-error', () => null)
  const loadedForUserId = useState<string>('workbench-brand-spaces-directory-user-id', () => '')

  function reset() {
    items.value = []
    loading.value = false
    loaded.value = false
    error.value = null
    loadedForUserId.value = ''
  }

  async function ensureLoaded(force = false) {
    const currentUserId = auth.value?.userId || ''

    if (loadedForUserId.value !== currentUserId) {
      reset()
    }

    if (loading.value) {
      return items.value
    }
    if (loaded.value && !force) {
      return items.value
    }

    loading.value = true
    error.value = null
    try {
      const result = await brandSpacesApi.list()
      items.value = rankBrandSpaces(result.items || [])
      loaded.value = true
      loadedForUserId.value = currentUserId
      return items.value
    } catch (cause) {
      error.value = cause instanceof Error && cause.message ? cause.message : '品牌空间列表加载失败'
      throw cause
    } finally {
      loading.value = false
    }
  }

  function resolveByBrandSpaceId(brandSpaceId?: string | null) {
    if (!brandSpaceId) {
      return null
    }
    return items.value.find(item => item.brandSpaceId === brandSpaceId) || null
  }

  return {
    items,
    loading,
    loaded,
    error,
    ensureLoaded,
    reset,
    resolveByBrandSpaceId
  }
}
