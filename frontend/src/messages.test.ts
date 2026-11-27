import { describe, expect, it } from 'vitest'
import { confirmMessages, errorMessages, notFoundMessage, resultMessages } from './messages'

describe('messages', () => {
  it('notFoundMessage は「該当する〇〇がありません。」を返す', () => {
    expect(notFoundMessage('入庫予定')).toBe('該当する入庫予定がありません。')
  })
  it('分類ごとに文末が揃っている', () => {
    Object.values(confirmMessages).forEach((m) => expect(m).toMatch(/よろしいですか？$/))
    Object.values(resultMessages).forEach((m) => expect(m).toMatch(/ました。$/))
    expect(errorMessages.required('商品名')).toBe('商品名を入力してください。')
    expect(errorMessages.maxLength('商品名', 20)).toMatch(/してください。$/)
  })
})
