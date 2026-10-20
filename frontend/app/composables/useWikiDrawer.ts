interface WikiDrawerState {
  open: boolean
  entrypointId: string | null
}

export function useWikiDrawer() {
  const state = useState<WikiDrawerState>('geo-wiki-drawer', () => ({
    open: false,
    entrypointId: null
  }))

  const { resolvePayload } = useWikiRegistry()

  const payload = computed(() => {
    if (!state.value.entrypointId) {
      return null
    }
    return resolvePayload(state.value.entrypointId)
  })

  function openByEntrypoint(entrypointId: string) {
    const resolved = resolvePayload(entrypointId)
    if (!resolved) {
      return
    }
    state.value = {
      open: true,
      entrypointId
    }
  }

  function close() {
    state.value = {
      open: false,
      entrypointId: null
    }
  }

  return {
    state,
    payload,
    openByEntrypoint,
    close
  }
}
