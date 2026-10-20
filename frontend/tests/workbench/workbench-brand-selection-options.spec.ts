import { describe, expect, it } from 'vitest'
import { buildWorkbenchBrandSelectionOptions } from '../../app/composables/useWorkbenchBrandSpacesDirectory'
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

describe('buildWorkbenchBrandSelectionOptions', () => {
  it('uses brandSpaceId as the option key', () => {
    const options = buildWorkbenchBrandSelectionOptions([
      buildBrandSpace({
        brandSpaceId: 'space-byd',
        brandName: '比亚迪'
      })
    ])

    expect(options).toEqual([
      {
        key: 'space-byd',
        label: '比亚迪',
        brandSpaceId: 'space-byd'
      }
    ])
  })

  it('disambiguates same-name brand spaces with stable detail labels', () => {
    const options = buildWorkbenchBrandSelectionOptions([
      buildBrandSpace({
        brandSpaceId: 'space-1',
        brandName: 'Lululemon',
        primaryDomain: 'www.lululemon.cn',
        category: '运动服饰'
      }),
      buildBrandSpace({
        brandSpaceId: 'space-2',
        brandName: 'Lululemon',
        primaryDomain: 'www.lululemon.com',
        category: '运动服饰'
      }),
      buildBrandSpace({
        brandSpaceId: 'space-3',
        brandName: 'Lululemon',
        primaryDomain: null,
        officialWebsite: 'https://regional.lululemon.com/',
        category: '区域试验'
      })
    ])

    expect(options.map(item => item.label)).toEqual([
      'Lululemon · 运动服饰 · space-1',
      'Lululemon · 运动服饰 · space-2',
      'Lululemon · 区域试验 · space-3'
    ])
  })
})
