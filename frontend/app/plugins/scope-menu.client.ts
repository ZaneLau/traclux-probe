const OPEN_SCOPE_MENU_SELECTOR = 'details.scope-menu[open]'
const SCOPE_MENU_SELECTOR = 'details.scope-menu'

function getOpenScopeMenus(exclude?: HTMLDetailsElement) {
  return Array.from(document.querySelectorAll<HTMLDetailsElement>(OPEN_SCOPE_MENU_SELECTOR))
    .filter(menu => menu !== exclude)
}

function closeScopeMenus(exclude?: HTMLDetailsElement) {
  for (const menu of getOpenScopeMenus(exclude)) {
    menu.open = false
  }
}

export default defineNuxtPlugin(() => {
  const onPointerDown = (event: PointerEvent) => {
    const target = event.target
    if (!(target instanceof Node)) {
      closeScopeMenus()
      return
    }

    const ownerMenu = target instanceof Element
      ? target.closest<HTMLDetailsElement>(SCOPE_MENU_SELECTOR)
      : null

    closeScopeMenus(ownerMenu || undefined)
  }

  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key !== 'Escape') {
      return
    }
    closeScopeMenus()
  }

  document.addEventListener('pointerdown', onPointerDown, true)
  document.addEventListener('keydown', onKeyDown, true)

  if (import.meta.hot) {
    import.meta.hot.dispose(() => {
      document.removeEventListener('pointerdown', onPointerDown, true)
      document.removeEventListener('keydown', onKeyDown, true)
    })
  }
})
