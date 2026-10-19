import { createHash, randomUUID } from 'node:crypto'
import { getHeader, setHeader, setResponseStatus, type H3Event } from 'h3'
import type { EngineApiEnvelope } from '../../app/types/engine'

interface EngineCachePolicy {
  maxAge?: number
  staleWhileRevalidate?: number
}

function stableJson(value: unknown) {
  return JSON.stringify(value)
}

function buildEtag(value: unknown) {
  return `"${createHash('sha1').update(stableJson(value)).digest('hex')}"`
}

export function applyEngineCacheHeaders(event: H3Event, policy: EngineCachePolicy = {}) {
  const maxAge = policy.maxAge ?? 15
  const staleWhileRevalidate = policy.staleWhileRevalidate ?? 30
  setHeader(event, 'Cache-Control', `private, max-age=${maxAge}, stale-while-revalidate=${staleWhileRevalidate}`)
}

export function replyEngineSuccess<T>(
  event: H3Event,
  data: T,
  cachePolicy?: EngineCachePolicy
): EngineApiEnvelope<T> | null {
  applyEngineCacheHeaders(event, cachePolicy)
  const etag = buildEtag(data)
  const incoming = getHeader(event, 'if-none-match')
  setHeader(event, 'ETag', etag)

  if (incoming && incoming === etag) {
    setResponseStatus(event, 304)
    return null
  }

  return {
    ok: true,
    traceId: randomUUID(),
    data
  }
}

export function replyEngineError(
  event: H3Event,
  code: string,
  message: string,
  statusCode = 500
) {
  setResponseStatus(event, statusCode)
  return {
    ok: false,
    traceId: randomUUID(),
    error: {
      code,
      message
    }
  } satisfies EngineApiEnvelope<never>
}
