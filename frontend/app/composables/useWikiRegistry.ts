import { resolveWikiEntrypoint } from '~/content/wiki/entrypoints'
import {
  canPublishWikiObject,
  resolveWikiBlockPayload,
  resolveWikiRuntimeObject
} from '~/content/wiki/registry'

export function useWikiRegistry() {
  function resolveEntry(entrypointId: string) {
    return resolveWikiEntrypoint(entrypointId)
  }

  function resolveObject(entrypointId: string) {
    const entrypoint = resolveEntry(entrypointId)
    if (!entrypoint) {
      return null
    }
    return resolveWikiRuntimeObject(entrypoint.objectId)
  }

  function canOpen(entrypointId: string) {
    const entrypoint = resolveEntry(entrypointId)
    if (!entrypoint?.enabled) {
      return false
    }
    return canPublishWikiObject(resolveObject(entrypointId))
  }

  function resolvePayload(entrypointId: string) {
    const entrypoint = resolveEntry(entrypointId)
    if (!entrypoint) {
      return null
    }

    const object = resolveObject(entrypointId)
    if (!canPublishWikiObject(object)) {
      return null
    }

    const block = resolveWikiBlockPayload(object, entrypoint.pageContext)
    if (!block) {
      return null
    }

    return {
      entrypoint,
      ...block
    }
  }

  return {
    resolveEntry,
    resolveObject,
    canOpen,
    resolvePayload
  }
}
