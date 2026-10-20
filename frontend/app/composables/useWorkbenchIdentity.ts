import type { WorkbenchIdentity } from '~/types/workbench'

type WorkbenchIdentityCarrier = Partial<WorkbenchIdentity> & {
  identity?: Partial<WorkbenchIdentity> | null
}

export function resolveWorkbenchIdentity(view?: WorkbenchIdentityCarrier | null): WorkbenchIdentity | null {
  if (!view) {
    return null
  }

  const identity = view.identity || null
  const brandSpaceId = identity?.brandSpaceId || view.brandSpaceId || ''
  const brandName = identity?.brandName || view.brandName || ''

  if (!brandSpaceId) {
    return null
  }

  return {
    brandSpaceId,
    brandId: identity?.brandId ?? view.brandId ?? null,
    brandName,
    category: identity?.category ?? view.category ?? null,
    officialWebsite: identity?.officialWebsite ?? view.officialWebsite ?? null,
    primaryDomain: identity?.primaryDomain ?? view.primaryDomain ?? null
  }
}

export function mergeWorkbenchIdentity<T extends WorkbenchIdentityCarrier>(view: T): T {
  const identity = resolveWorkbenchIdentity(view)
  if (!identity) {
    return view
  }

  return {
    ...view,
    ...identity,
    identity
  } as T
}

export function syncWorkbenchIdentity(view?: WorkbenchIdentityCarrier | null) {
  const identity = resolveWorkbenchIdentity(view)
  if (!identity) {
    return null
  }

  const { selectedBrand, selectedBrandSpaceId } = useWorkbenchSelectionState()
  if (identity.brandSpaceId && selectedBrandSpaceId.value !== identity.brandSpaceId) {
    selectedBrandSpaceId.value = identity.brandSpaceId
  }
  if (identity.brandName && selectedBrand.value !== identity.brandName) {
    selectedBrand.value = identity.brandName
  }

  return identity
}
