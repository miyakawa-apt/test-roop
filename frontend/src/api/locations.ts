// ロケーションAPI（/api/admin/locations）のクライアント。
import type { ShelfStatus } from '../codes'
import { apiGet, apiSend } from './client'

export interface Location {
  shelfNo: string
  /** 定数名（EMPTY / STOCKED / PROHIBITED）。 */
  shelfStatus: ShelfStatus
  shelfStatusLabel: string
  /** 備考（未入力は空文字）。 */
  remarks: string
}

/** 登録・更新の要求（値は入力欄の文字列のまま送る。サーバー側で同じ規則を検証する）。 */
export interface LocationInput {
  /** 更新では「新しい棚番」。 */
  shelfNo: string
  shelfStatus: string
  remarks: string
}

const BASE = '/api/admin/locations'

export const fetchLocations = () => apiGet<Location[]>(BASE)
export const postLocation = (input: LocationInput) => apiSend<Location>('POST', BASE, input)
/** 更新。oldShelfNo は変更前の棚番（パス）、input.shelfNo は新しい棚番。 */
export const putLocation = (oldShelfNo: string, input: LocationInput) =>
  apiSend<Location>('PUT', `${BASE}/${encodeURIComponent(oldShelfNo)}`, input)
export const deleteLocation = (shelfNo: string) => apiSend('DELETE', `${BASE}/${encodeURIComponent(shelfNo)}`)
