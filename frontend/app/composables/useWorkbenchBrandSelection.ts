import { computed, toValue, watch, type MaybeRefOrGetter } from 'vue'
import type { BrandSpaceListItem } from '~/types/brand-space'

export const WORKBENCH_BRAND_MISSING_OPTION_KEY = '__missing_brand_space__'

export function resolveDefaultWorkbenchBrandSpace(
  items: BrandSpaceListItem[],
  selectedBrandSpaceId?: string | null
) {
  if (!items.length) {
    return null
  }

  if (selectedBrandSpaceId) {
    const current = items.find(item => item.brandSpaceId === selectedBrandSpaceId)
    if (current) {
      return current
    }
  }

  return items[0] || null
}

export function useWorkbenchBrandSelection(
  requestedBrandSpaceId?: MaybeRefOrGetter<string | null | undefined>
) {
  const brandSpaceDirectory = useWorkbenchBrandSpacesDirectory()
  const { selectedBrand, selectedBrandSpaceId } = useWorkbenchSelectionState()
  const route = useRoute()

  const requestedId = computed(() => {
    const value = toValue(requestedBrandSpaceId)
    return typeof value === 'string' ? value : ''
  })

  const brandSelectionOptions = computed(() => {
    return buildWorkbenchBrandSelectionOptions(brandSpaceDirectory.items.value)
  })

  const requestedBrandSpaceMissing = computed(() => {
    if (!requestedId.value) {
      return false
    }
    if (brandSpaceDirectory.loading.value || !brandSpaceDirectory.loaded.value) {
      return false
    }
    return !brandSpaceDirectory.resolveByBrandSpaceId(requestedId.value)
  })

  const matchedBrandSpace = computed(() => {
    if (requestedId.value) {
      return brandSpaceDirectory.resolveByBrandSpaceId(requestedId.value)
    }
    return brandSpaceDirectory.resolveByBrandSpaceId(selectedBrandSpaceId.value)
  })

  const workbenchBoundBrandSpaceId = computed(() => matchedBrandSpace.value?.brandSpaceId || '')

  const selectedBrandLabel = computed(() => matchedBrandSpace.value?.brandName || '')

  const selectedBrandOptionKey = computed({
    get() {
      return matchedBrandSpace.value?.brandSpaceId || ''
    },
    set(value: string) {
      const next = brandSpaceDirectory.resolveByBrandSpaceId(value)
      if (!next) {
        return
      }
      if (selectedBrandSpaceId.value !== next.brandSpaceId) {
        selectedBrandSpaceId.value = next.brandSpaceId
      }
      if (selectedBrand.value !== next.brandName) {
        selectedBrand.value = next.brandName
      }
    }
  })

  const requestedBrandMissingOption = computed(() => {
    if (!requestedBrandSpaceMissing.value) {
      return null
    }
    return {
      key: WORKBENCH_BRAND_MISSING_OPTION_KEY,
      label: '未找到对应品牌空间'
    }
  })

  const selectedBrandRouteOptionKey = computed({
    get() {
      if (requestedBrandSpaceMissing.value) {
        return WORKBENCH_BRAND_MISSING_OPTION_KEY
      }
      return selectedBrandOptionKey.value
    },
    set(value: string) {
      if (!value || value === WORKBENCH_BRAND_MISSING_OPTION_KEY) {
        return
      }

      const next = brandSpaceDirectory.resolveByBrandSpaceId(value)
      if (!next) {
        return
      }

      selectedBrandOptionKey.value = next.brandSpaceId

      const currentRouteBrandSpaceId = typeof route.query.brandSpaceId === 'string' ? route.query.brandSpaceId : ''
      if (currentRouteBrandSpaceId === next.brandSpaceId) {
        return
      }

      const nextQuery = { ...route.query, brandSpaceId: next.brandSpaceId }
      void navigateTo(
        {
          path: route.path,
          query: nextQuery,
          hash: route.hash
        },
        {
          replace: true
        }
      )
    }
  })

  function syncSharedSelection(next: BrandSpaceListItem) {
    if (selectedBrandSpaceId.value !== next.brandSpaceId) {
      selectedBrandSpaceId.value = next.brandSpaceId
    }
    if (selectedBrand.value !== next.brandName) {
      selectedBrand.value = next.brandName
    }
  }

  function syncRouteBrandSpaceId(brandSpaceId: string) {
    if (!brandSpaceId) {
      return
    }

    const currentRouteBrandSpaceId = typeof route.query.brandSpaceId === 'string' ? route.query.brandSpaceId : ''
    if (currentRouteBrandSpaceId === brandSpaceId) {
      return
    }

    void navigateTo(
      {
        path: route.path,
        query: {
          ...route.query,
          brandSpaceId
        },
        hash: route.hash
      },
      {
        replace: true
      }
    )
  }

  watch(
    [brandSelectionOptions, requestedId],
    ([options, routeBrandSpaceId]) => {
      if (!options.length) {
        if (selectedBrandSpaceId.value) {
          selectedBrandSpaceId.value = ''
        }
        if (selectedBrand.value) {
          selectedBrand.value = ''
        }
        return
      }

      const realItems = brandSpaceDirectory.items.value
      if (realItems.length) {
        if (routeBrandSpaceId) {
          const requested = brandSpaceDirectory.resolveByBrandSpaceId(routeBrandSpaceId)
          if (requested) {
            syncSharedSelection(requested)
          }
          return
        }

        const defaultBrandSpace = resolveDefaultWorkbenchBrandSpace(realItems, selectedBrandSpaceId.value)
        if (!defaultBrandSpace) {
          return
        }
        syncSharedSelection(defaultBrandSpace)
        syncRouteBrandSpaceId(defaultBrandSpace.brandSpaceId)
      }
    },
    { immediate: true }
  )

  return {
    brandSelectionOptions,
    matchedBrandSpace,
    requestedBrandSpaceId: requestedId,
    requestedBrandMissingOption,
    requestedBrandSpaceMissing,
    selectedBrandLabel,
    selectedBrandOptionKey,
    selectedBrandRouteOptionKey,
    workbenchBoundBrandSpaceId
  }
}
