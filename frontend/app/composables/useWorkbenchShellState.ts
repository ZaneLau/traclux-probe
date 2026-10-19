import { ref } from 'vue'

export function useWorkbenchShellState() {
  const workbenchRef = ref<HTMLElement | null>(null)
  const workbenchScrolled = ref(false)

  function syncWorkbenchScrolled(target?: HTMLElement | null) {
    workbenchScrolled.value = (target?.scrollTop || 0) > 6
  }

  function resetWorkbenchScrolled() {
    workbenchScrolled.value = false
  }

  return {
    workbenchRef,
    workbenchScrolled,
    syncWorkbenchScrolled,
    resetWorkbenchScrolled
  }
}
