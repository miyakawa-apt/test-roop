// 区分値の表示名一覧。サーバー側（jp.co.apt.mony.common.code）の enum 定数名と表示名に一致させる。

export const ROLE_LABELS = {
  ADMIN: '管理者',
  GENERAL: '一般',
} as const
export type Role = keyof typeof ROLE_LABELS

export const SHELF_STATUS_LABELS = {
  EMPTY: '空棚',
  STOCKED: '実棚',
  PROHIBITED: '禁止棚',
} as const
export type ShelfStatus = keyof typeof SHELF_STATUS_LABELS

export const RESULT_TYPE_LABELS = {
  RECEIPT: '入庫',
  ISSUE: '出庫',
  CORRECTION: '修正',
  DELETION: '削除',
} as const
export type ResultType = keyof typeof RESULT_TYPE_LABELS

export const CATEGORY_LABELS = {
  FOOD: '食品',
  DAILY_GOODS: '日用品',
  TOOL: '工具',
  PARTS: '部材',
  OTHER: 'その他',
} as const
export type Category = keyof typeof CATEGORY_LABELS

/** 定数名（例: ADMIN）か判定する。 */
function isName<T extends Record<string, string>>(labels: T, value: unknown): value is keyof T {
  return typeof value === 'string' && Object.hasOwn(labels, value)
}

export const isRole = (v: unknown): v is Role => isName(ROLE_LABELS, v)
export const isShelfStatus = (v: unknown): v is ShelfStatus => isName(SHELF_STATUS_LABELS, v)
export const isResultType = (v: unknown): v is ResultType => isName(RESULT_TYPE_LABELS, v)
export const isCategory = (v: unknown): v is Category => isName(CATEGORY_LABELS, v)

/** 選択肢（定数名と表示名）の一覧。セレクトボックス用。 */
function options<T extends Record<string, string>>(labels: T) {
  return (Object.keys(labels) as (keyof T & string)[]).map((value) => ({ value, label: labels[value] }))
}

export const ROLE_OPTIONS = options(ROLE_LABELS)
export const SHELF_STATUS_OPTIONS = options(SHELF_STATUS_LABELS)
export const RESULT_TYPE_OPTIONS = options(RESULT_TYPE_LABELS)
export const CATEGORY_OPTIONS = options(CATEGORY_LABELS)
