export interface DashboardNavItem {
  label: string
  path?: string
  sectionId?: string
  matchPrefixes?: string[]
  disabled?: boolean
  pageTitlePrefix?: string
  pageTitleSuffix?: string
}

export interface DashboardNavGroup {
  title: string
  items: DashboardNavItem[]
}

const NAV_GROUPS: DashboardNavGroup[] = [
  {
    title: '工作台',
    items: [
      { label: '可见度总览', path: '/dashboard', matchPrefixes: ['/dashboard'], pageTitlePrefix: 'AI ' }
    ]
  },
  {
    title: '分析',
    items: [
      { label: '品牌表现', path: '/engine', matchPrefixes: ['/engine/'] },
      { label: '引用来源', path: '/sources', matchPrefixes: ['/sources'] },
      { label: '问题分析', path: '/probe-analysis', matchPrefixes: ['/probe-analysis'] },
      { label: '引擎观察窗', path: '/engine-theater', matchPrefixes: ['/engine-theater'] },
      { label: '品牌管理', path: '/brands', matchPrefixes: ['/brands'] }
    ]
  }
]

function isDashboardItemVisible(item: DashboardNavItem, engineTheaterEnabled: boolean) {
  if (item.path === '/engine-theater') {
    return engineTheaterEnabled
  }
  return true
}

export function useDashboardNavGroups(): DashboardNavGroup[] {
  return NAV_GROUPS.map(group => ({
    ...group,
    items: group.items.map(item => ({
      ...item
    }))
  }))
}

export function useVisibleDashboardNavGroups(): DashboardNavGroup[] {
  const { isEngineTheaterEnabled } = useFeatureAvailability()

  return useDashboardNavGroups()
    .map(group => ({
      ...group,
      items: group.items.filter(item => isDashboardItemVisible(item, isEngineTheaterEnabled.value))
    }))
    .filter(group => group.items.length > 0)
}

function isNavItemMatched(path: string, item: DashboardNavItem): boolean {
  if (item.path && item.path === path) {
    return true
  }
  return !!item.matchPrefixes?.some((prefix) => {
    if (prefix.endsWith('/')) {
      return path.startsWith(prefix)
    }
    return path === prefix || path.startsWith(`${prefix}/`)
  })
}

function buildPageTitle(item: DashboardNavItem): string {
  return `${item.pageTitlePrefix || ''}${item.label}${item.pageTitleSuffix || ''}`.trim()
}

export function resolveNavItemByPath(path: string): DashboardNavItem | null {
  const { isEngineTheaterEnabled } = useFeatureAvailability()

  for (const group of NAV_GROUPS) {
    for (const item of group.items) {
      if (!isDashboardItemVisible(item, isEngineTheaterEnabled.value)) {
        continue
      }
      if (isNavItemMatched(path, item)) {
        return item
      }
    }
  }
  return null
}

export function resolveNavPageTitleByPath(path: string, fallback: string): string {
  const item = resolveNavItemByPath(path)
  return item ? buildPageTitle(item) : fallback
}
