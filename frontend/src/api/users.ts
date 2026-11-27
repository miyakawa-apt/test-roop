// ユーザーAPI（/api/admin/users）のクライアント。応答にパスワード・ハッシュは含まれない。
import type { Role } from '../codes'
import { apiGet, apiSend } from './client'

export interface UserRow {
  userId: string
  userName: string
  /** 定数名（ADMIN など）。 */
  role: Role
  roleLabel: string
}

/** 登録・更新の入力（値は入力欄の文字列のまま送る。サーバー側で同じ規則を検証する）。pw はパスワード欄の値。 */
export interface UserInput {
  userId: string
  userName: string
  pw: string
  role: string
}

const BASE = '/api/admin/users'

/** 送信する本文。パスワードの項目名は API の仕様どおり password。 */
const payload = (input: UserInput) => ({
  userId: input.userId,
  userName: input.userName,
  'password': input.pw,
  role: input.role,
})

export const fetchUsers = () => apiGet<UserRow[]>(BASE)
export const postUser = (input: UserInput) => apiSend<UserRow>('POST', BASE, payload(input))
export const putUser = (input: UserInput) =>
  apiSend<UserRow>('PUT', `${BASE}/${encodeURIComponent(input.userId)}`, payload(input))
export const deleteUser = (userId: string) => apiSend('DELETE', `${BASE}/${encodeURIComponent(userId)}`)
