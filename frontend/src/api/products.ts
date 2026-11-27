// 商品マスタAPI（/api/admin/products）のクライアント。
import type { Category } from '../codes'
import { apiGet, apiSend } from './client'

export interface Product {
  productCode: string
  productName: string
  /** 定数名（FOOD など）。 */
  category: Category
  categoryLabel: string
  unitPrice: number
  caseQuantity: number
}

/** 登録・更新の要求（値は入力欄の文字列のまま送る。サーバー側で同じ規則を検証する）。 */
export interface ProductInput {
  productCode: string
  productName: string
  category: string
  unitPrice: string
  caseQuantity: string
}

const BASE = '/api/admin/products'

export const fetchProducts = () => apiGet<Product[]>(BASE)
export const postProduct = (input: ProductInput) => apiSend<Product>('POST', BASE, input)
export const putProduct = (input: ProductInput) =>
  apiSend<Product>('PUT', `${BASE}/${encodeURIComponent(input.productCode)}`, input)
export const deleteProduct = (code: string) => apiSend('DELETE', `${BASE}/${encodeURIComponent(code)}`)
