import { onBeforeUnmount, onMounted, toValue, type MaybeRefOrGetter } from 'vue'

function closeOpenDetails(root: HTMLElement | null) {
  if (!root) {
    return
  }
  root.querySelectorAll('details[open]').forEach((element) => {
    element.removeAttribute('open')
  })
}

export function useDetailsMenuDismiss(rootRef: MaybeRefOrGetter<HTMLElement | null | undefined>) {
  const handlePointerDown = (event: PointerEvent) => {
    const root = toValue(rootRef)
    if (!root) {
      return
    }

    if (event.target instanceof Node && root.contains(event.target)) {
      return
    }

    closeOpenDetails(root)
  }

  const handleKeyDown = (event: KeyboardEvent) => {
    if (event.key !== 'Escape') {
      return
    }
    closeOpenDetails(toValue(rootRef) || null)
  }

  onMounted(() => {
    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
  })

  onBeforeUnmount(() => {
    document.removeEventListener('pointerdown', handlePointerDown)
    document.removeEventListener('keydown', handleKeyDown)
  })
}
