import { vi } from 'vitest'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import App from './App'
import type { AuthUser } from './api/auth'

export const ADMIN_USER: AuthUser = { userId: 'A001', userName: '管理者', role: 'ADMIN', roleLabel: '管理者' }
export const GENERAL_USER: AuthUser = { userId: 'B002', userName: '一般太郎', role: 'GENERAL', roleLabel: '一般' }
/** テスト用の正しい入力値（本物の資格情報ではない）。 */
export const VALID_PW = 'testpass1'

export interface FakeServer {
  fetchMock: ReturnType<typeof vi.fn>
  /** 現在のセッションのユーザー（null は未ログイン）。 */
  session: { user: AuthUser | null }
  calls: { method: string; path: string; headers: Record<string, string>; body: unknown }[]
}

/** fetch を差し替えて API サーバーのふりをする。 */
export function installFakeServer(initial: AuthUser | null, accounts: Record<string, AuthUser> = {}): FakeServer {
  const server: FakeServer = { fetchMock: vi.fn(), session: { user: initial }, calls: [] }
  const json = (status: number, body?: unknown) =>
    new Response(body === undefined ? null : JSON.stringify(body), { status })
  server.fetchMock.mockImplementation(async (path: string, init: RequestInit = {}) => {
    const method = init.method ?? 'GET'
    const body = init.body ? JSON.parse(String(init.body)) : undefined
    server.calls.push({ method, path, headers: (init.headers ?? {}) as Record<string, string>, body })
    if (method === 'GET' && path === '/api/auth/me') {
      return server.session.user ? json(200, server.session.user) : json(401)
    }
    if (method === 'GET') {
      document.cookie = 'XSRF-TOKEN=test-token; path=/'
      return json(200, { status: 'UP' })
    }
    if (path === '/api/auth/login') {
      const user = accounts[body.userId]
      if (user && body.password === VALID_PW) {
        server.session.user = user
        return json(200, user)
      }
      return json(401, { error: 'AUTHENTICATION_FAILED' })
    }
    if (path === '/api/auth/logout') {
      server.session.user = null
      document.cookie = 'XSRF-TOKEN=; path=/; max-age=0'
      return json(204)
    }
    return json(404)
  })
  vi.stubGlobal('fetch', server.fetchMock)
  return server
}

export function renderApp(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
}
