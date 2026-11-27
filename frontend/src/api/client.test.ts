import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiGet, apiPost } from './client'

function clearCookies() {
  document.cookie = 'XSRF-TOKEN=; path=/; max-age=0'
}

beforeEach(clearCookies)
afterEach(() => {
  vi.unstubAllGlobals()
  clearCookies()
})

describe('api/client', () => {
  it('GET は CSRF ヘッダーなしで、Cookie を送る設定（same-origin）で呼ぶ', async () => {
    const f = vi.fn().mockResolvedValue(new Response(JSON.stringify({ a: 1 }), { status: 200 }))
    vi.stubGlobal('fetch', f)
    const res = await apiGet<{ a: number }>('/api/x')
    expect(res).toEqual({ status: 200, ok: true, body: { a: 1 } })
    const init = f.mock.calls[0][1]
    expect(init.credentials).toBe('same-origin')
    expect(init.headers['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('POST は XSRF-TOKEN Cookie の値を X-XSRF-TOKEN ヘッダーに載せ、JSON を送る', async () => {
    document.cookie = 'XSRF-TOKEN=abc123; path=/'
    const f = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', f)
    const res = await apiPost('/api/y', { k: 'v' })
    expect(res).toEqual({ status: 204, ok: true, body: undefined })
    const [path, init] = f.mock.calls[0]
    expect(path).toBe('/api/y')
    expect(init.method).toBe('POST')
    expect(init.headers['X-XSRF-TOKEN']).toBe('abc123')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(init.body).toBe('{"k":"v"}')
  })

  it('Cookie が無いときは、先に GET でトークンを取得してから POST する', async () => {
    const f = vi.fn().mockImplementation(async (_path: string, init: RequestInit) => {
      if (init.method === 'GET') document.cookie = 'XSRF-TOKEN=seeded; path=/'
      return new Response(null, { status: 204 })
    })
    vi.stubGlobal('fetch', f)
    await apiPost('/api/z')
    expect(f.mock.calls.map((c) => c[1].method)).toEqual(['GET', 'POST'])
    expect(f.mock.calls[1][1].headers['X-XSRF-TOKEN']).toBe('seeded')
  })

  it('エラー状態は例外にせず status と本文で返す', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"error":"X"}', { status: 401 })))
    expect(await apiGet('/api/q')).toEqual({ status: 401, ok: false, body: { error: 'X' } })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('not json', { status: 500 })))
    expect(await apiGet('/api/q')).toEqual({ status: 500, ok: false, body: undefined })
  })
})
