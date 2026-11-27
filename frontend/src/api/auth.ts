import type { Role } from '../codes'
import { apiGet, apiPost } from './client'

export interface AuthUser {
  userId: string
  userName: string
  role: Role
  roleLabel: string
}

export type LoginResult =
  | { type: 'success'; user: AuthUser }
  /** 入力検証エラー（400）。 */
  | { type: 'invalid'; errors: { field: string; type: string }[] }
  /** ユーザーIDまたはパスワードの不一致（401）。 */
  | { type: 'mismatch' }
  | { type: 'error' }

/** ログイン中のユーザーを取得する。未ログインは null。 */
export async function fetchMe(): Promise<AuthUser | null> {
  const res = await apiGet<AuthUser>('/api/auth/me')
  return res.ok && res.body ? res.body : null
}

export async function postLogin(userId: string, pw: string): Promise<LoginResult> {
  const res = await apiPost<AuthUser & { errors?: { field: string; type: string }[] }>('/api/auth/login', {
    userId,
    'password': pw,
  })
  if (res.status === 200 && res.body) return { type: 'success', user: res.body }
  if (res.status === 400 && res.body?.errors) return { type: 'invalid', errors: res.body.errors }
  if (res.status === 401) return { type: 'mismatch' }
  return { type: 'error' }
}

export async function postLogout(): Promise<void> {
  await apiPost('/api/auth/logout')
}
