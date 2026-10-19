import { describe, expect, it } from 'vitest'
import { resolveDefaultWorkbenchBrandSpace } from '../../app/composables/useWorkbenchBrandSelection'
import type { BrandSpaceListItem } from '../../app/types/brand-space'

function buildBrandSpace(overrides: Partial<BrandSpaceListItem>): BrandSpaceListItem {
  return {
    brandSpaceId: overrides.brandSpaceId || 'space-1',
    brandName: overrides.brandName || '默认品牌',
    status: overrides.status || 'active',
    category: overrides.category ?? null,
    officialWebsite: overrides.officialWebsite ?? null,
    primaryDomain: overrides.primaryDomain ?? null,
    ...overrides
  }
}

describe('resolveDefaultWorkbenchBrandSpace', () => {
  it('uses the current selected brand space when it is still valid', () => {
    const items = [
      buildBrandSpace({ brandSpaceId: 'space-1', brandName: '比亚迪' }),
      buildBrandSpace({ brandSpaceId: 'space-2', brandName: '小米' })
    ]

    expect(resolveDefaultWorkbenchBrandSpace(items, 'space-2')?.brandSpaceId).toBe('space-2')
  })

  it('falls back to the first brand space when there is no valid selection', () => {
    const items = [
      buildBrandSpace({ brandSpaceId: 'space-1', brandName: '比亚迪' }),
      buildBrandSpace({ brandSpaceId: 'space-2', brandName: '小米' })
    ]

    expect(resolveDefaultWorkbenchBrandSpace(items, '')?.brandSpaceId).toBe('space-1')
    expect(resolveDefaultWorkbenchBrandSpace(items, 'missing-space')?.brandSpaceId).toBe('space-1')
  })
})
