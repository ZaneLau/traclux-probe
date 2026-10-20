import { describe, expect, it } from 'vitest'
import { DemoReadOnlyError, readDemoResponse } from '../../app/demo/provider'
import snapshot from '../../app/demo/workbench.json'
import publicCase from '../../../docs/demo/case.json'

describe('static workbench demo boundary', () => {
  it('rejects every mutation on each saved route and never changes its data', () => {
    const before = JSON.stringify(snapshot)
    for (const method of ['POST', 'PUT', 'PATCH', 'DELETE', 'post']) {
      for (const path of Object.keys(snapshot)) {
        expect(() => readDemoResponse(path, method)).toThrow(DemoReadOnlyError)
      }
    }
    expect(JSON.stringify(snapshot)).toBe(before)
  })

  it('fails closed for unknown reads, provider URLs and live action routes', () => {
    for (const path of ['/runs/demo-run-20261020', '/runs/demo-run-20261020/events', '/auth/session', '/credits/balance', '/brand-spaces/demo-anker/probe-plan/execute-once', '/admin/overview', 'https://example.com/api']) {
      expect(() => readDemoResponse(path)).toThrow()
    }
  })

  it('returns isolated copies and keeps the original five-probe case', () => {
    const runs = readDemoResponse<{ items: Array<{runId: string}> }>('/brand-spaces/demo-anker/runs')
    expect(runs.items[0]?.runId).toBe('demo-run-20261020')
    runs.items.length = 0
    expect(readDemoResponse<typeof runs>('/brand-spaces/demo-anker/runs').items).toHaveLength(1)
    const data = readDemoResponse<{probeRows: Array<{promptText: string,rawAnswer: string}>}>('/workbench/problem-analysis', 'GET', { brandSpaceId: 'demo-anker', dateFrom: '2026-10-07', dateTo: '2026-10-20' })
    expect(data.probeRows.map(p => p.promptText).sort()).toEqual(publicCase.questions.map(q => q.question).sort())
    const reviewed = new Set(publicCase.questions.map(q => q.answerExcerpt || '此回答未在公开案例中展示。'))
    for (const row of data.probeRows) expect(reviewed.has(row.rawAnswer)).toBe(true)
  })

  it('does not present a different brand or date as the captured sample', () => {
    expect(() => readDemoResponse('/workbench/overview', 'GET', {brandSpaceId: 'other'})).toThrow()
    expect(() => readDemoResponse('/workbench/overview', 'GET', {brandSpaceId: 'demo-anker', dateFrom: '2027-01-01'})).toThrow()
  })

  it('contains no account, provider, billing or raw task payload fields', () => {
    const check = (value: unknown): void => {
      if (Array.isArray(value)) { value.forEach(check); return }
      if (value && typeof value === 'object') {
        for (const [key, item] of Object.entries(value)) {
          expect(key).not.toMatch(/token|password|secret|cookie|apiKey|endpoint|credit|balance|organizationId|membershipId|rawRecord|taskOrder|taskMetadata/i)
          check(item)
        }
      }
    }
    check(snapshot)
  })
})
