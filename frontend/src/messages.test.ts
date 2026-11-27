import { describe, expect, it } from 'vitest'
import {
  confirmMessages,
  errorMessages,
  fieldLabels,
  loginMessages,
  notFoundMessage,
  resultMessages,
  validationMessage,
} from './messages'
import { INPUT_RULES } from './validation'

describe('messages', () => {
  it('notFoundMessage は「該当する〇〇がありません。」を返す', () => {
    expect(notFoundMessage('入庫予定')).toBe('該当する入庫予定がありません。')
  })
  it('分類ごとに文末が揃っている', () => {
    Object.values(confirmMessages).forEach((m) => expect(m).toMatch(/よろしいですか？$/))
    Object.values(resultMessages).forEach((m) => expect(m).toMatch(/済みです。$/))
    expect(errorMessages.required('商品名')).toBe('商品名を入力してください。')
    expect(errorMessages.maxLength('商品名', 20)).toMatch(/してください。$/)
  })
  it('validationMessage は項目の表示名とエラー種別から文面を作る', () => {
    expect(validationMessage('userId', 'REQUIRED')).toBe('ユーザーIDを入力してください。')
    expect(validationMessage('password', 'INVALID_LENGTH')).toBe('パスワードの文字数を確認して入力してください。')
    expect(validationMessage('productCode', 'INVALID_FORMAT')).toBe('商品コードの形式を確認して入力してください。')
    expect(validationMessage('quantity', 'OUT_OF_RANGE')).toBe('数量の範囲を確認して入力してください。')
  })
  it('未知の項目名はそのまま、未知の種別も「してください。」で終わる', () => {
    expect(validationMessage('unknownField', 'REQUIRED')).toBe('unknownFieldを入力してください。')
    expect(validationMessage('userId', 'OTHER')).toMatch(/してください。$/)
  })
  it('すべての入力ルールに表示名があり、全エラー種別の文面が「してください。」で終わる', () => {
    for (const field of Object.keys(INPUT_RULES)) {
      expect(fieldLabels[field]).toBeTruthy()
      for (const t of ['REQUIRED', 'INVALID_FORMAT', 'OUT_OF_RANGE', 'INVALID_LENGTH']) {
        expect(validationMessage(field, t)).toMatch(/してください。$/)
      }
    }
  })
  it('ログイン不一致の文言を持つ', () => {
    expect(loginMessages.mismatch).toBe('ユーザーIDまたはパスワードが違います。確認して入力してください。')
  })
})
