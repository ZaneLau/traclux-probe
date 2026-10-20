import { beforeEach, describe, expect, it } from 'vitest'
import {
  __resetEngineRequestManagerForTest,
  useEngineRequestManager
} from '../../app/composables/engine/useEngineRequestManager'

function sleep(ms: number) {
  return new Promise<void>(resolve => setTimeout(resolve, ms))
}

describe('useEngineRequestManager', () => {
  beforeEach(() => {
    __resetEngineRequestManagerForTest()
  })

  it('dedupes in-flight requests with same key', async () => {
    const manager = useEngineRequestManager()
    let calls = 0

    const request = async () => {
      calls += 1
      await sleep(20)
      return 'ok'
    }

    const p1 = manager.run({
      key: 'same-key',
      request: () => request()
    })
    const p2 = manager.run({
      key: 'same-key',
      request: () => request()
    })

    const [r1, r2] = await Promise.all([p1, p2])
    expect(r1).toBe('ok')
    expect(r2).toBe('ok')
    expect(calls).toBe(1)
  })

  it('cancels previous request in same cancel group', async () => {
    const manager = useEngineRequestManager()

    const slow = manager.run({
      key: 'group-old',
      cancelGroup: 'overview',
      timeoutMs: 2_000,
      request: signal =>
        new Promise<string>((resolve, reject) => {
          const timer = setTimeout(() => resolve('slow'), 120)
          signal.addEventListener('abort', () => {
            clearTimeout(timer)
            reject(Object.assign(new Error('aborted'), { name: 'AbortError' }))
          })
        })
    })
    const slowHandled = slow.catch(error => error)

    await sleep(8)

    const fast = manager.run({
      key: 'group-new',
      cancelGroup: 'overview',
      request: async () => {
        await sleep(10)
        return 'fast'
      }
    })

    await expect(fast).resolves.toBe('fast')
    await expect(slowHandled).resolves.toMatchObject({ name: 'AbortError' })
  })

  it('returns stale cache and refreshes in background (SWR)', async () => {
    const manager = useEngineRequestManager()
    let calls = 0

    const request = async () => {
      calls += 1
      await sleep(10)
      return calls
    }

    const v1 = await manager.run({
      key: 'cache-key',
      ttlMs: 30,
      staleMs: 120,
      request: () => request()
    })
    expect(v1).toBe(1)
    expect(calls).toBe(1)

    const fresh = await manager.run({
      key: 'cache-key',
      ttlMs: 30,
      staleMs: 120,
      request: () => request()
    })
    expect(fresh).toBe(1)
    expect(calls).toBe(1)

    await sleep(45)

    const stale = await manager.run({
      key: 'cache-key',
      ttlMs: 30,
      staleMs: 120,
      request: () => request()
    })
    expect(stale).toBe(1)

    await sleep(30)
    expect(calls).toBe(2)

    const refreshed = await manager.run({
      key: 'cache-key',
      ttlMs: 30,
      staleMs: 120,
      request: () => request()
    })
    expect(refreshed).toBe(2)
  })
})
