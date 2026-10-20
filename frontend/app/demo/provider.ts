import snapshot from './workbench.json'

export class DemoReadOnlyError extends Error {
  code = 403
  status = 403
  constructor() {
    super('演示为只读模式。请自行部署后生成或执行新探针。')
    this.name = 'DemoReadOnlyError'
  }
}

/** Explicit read-only source. Unknown reads and every mutation fail closed. */
export function readDemoResponse<T>(path: string, method = 'GET', query?: Record<string, unknown>): T {
  if (method.toUpperCase() !== 'GET') throw new DemoReadOnlyError()
  if (path.includes('://') || path.includes('?') || path.includes('#')) throw new Error('演示未收录此请求')
  const responses: Record<string, unknown> = snapshot
  if (!Object.hasOwn(responses, path)) throw new Error('演示未收录此数据')
  if (path.startsWith('/workbench/') && query?.brandSpaceId !== 'demo-anker') throw new Error('演示中没有这个品牌空间')
  if (path.startsWith('/workbench/') || path === '/brand-spaces/demo-anker/runs') {
    if ((query?.dateFrom && query.dateFrom !== '2026-10-07') || (query?.dateTo && query.dateTo !== '2026-10-20')) {
      throw new Error('演示仅包含 2026-10-07 至 2026-10-20 的已保存数据')
    }
  }
  return structuredClone(responses[path]) as T
}
