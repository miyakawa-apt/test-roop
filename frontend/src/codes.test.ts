import { describe, expect, it } from 'vitest'
import {
  CATEGORY_LABELS,
  CATEGORY_OPTIONS,
  isCategory,
  isRole,
  RESULT_TYPE_LABELS,
  ROLE_LABELS,
  SHELF_STATUS_LABELS,
} from './codes'

describe('区分値の表示名（サーバー側 enum と一致）', () => {
  it('表示名と定数名の対応', () => {
    expect(ROLE_LABELS).toEqual({ ADMIN: '管理者', GENERAL: '一般' })
    expect(SHELF_STATUS_LABELS).toEqual({ EMPTY: '空棚', STOCKED: '実棚', PROHIBITED: '禁止棚' })
    expect(RESULT_TYPE_LABELS).toEqual({ RECEIPT: '入庫', ISSUE: '出庫', CORRECTION: '修正', DELETION: '削除' })
    expect(CATEGORY_LABELS).toEqual({
      FOOD: '食品',
      DAILY_GOODS: '日用品',
      TOOL: '工具',
      PARTS: '部材',
      OTHER: 'その他',
    })
  })
  it('選択肢は定義順', () => {
    expect(CATEGORY_OPTIONS.map((o) => o.label)).toEqual(['食品', '日用品', '工具', '部材', 'その他'])
  })
  it('定数名の判定', () => {
    expect(isRole('ADMIN')).toBe(true)
    expect(isRole('管理者')).toBe(false)
    expect(isRole('toString')).toBe(false)
    expect(isCategory(null)).toBe(false)
  })
})
