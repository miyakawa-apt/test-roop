import { describe, expect, it } from 'vitest'
import { checkRule, INPUT_RULES, validateForm, validateRule, validateValue, ValidationResult } from './inputRules'
import type { ErrorType, RuleName } from './inputRules'

function ok(rule: RuleName, ...values: (string | null)[]) {
  for (const v of values) expect(checkRule(rule, v), `${rule} / [${v}]`).toBeUndefined()
}
function ng(rule: RuleName, type: ErrorType, ...values: (string | null)[]) {
  for (const v of values) expect(checkRule(rule, v), `${rule} / [${v}]`).toBe(type)
}

describe('入力ルール（サーバー側 InputRuleTest と同じ判定）', () => {
  it('数量', () => {
    ok('quantity', '1', '99999', '00001')
    ng('quantity', 'OUT_OF_RANGE', '0', '100000', '99999999999999999999999')
    ng('quantity', 'INVALID_FORMAT', '-1', '1.5', '１２', '12 ', ' 12', '1,000', 'abc')
    ng('quantity', 'REQUIRED', null, '')
  })
  it('単価', () => {
    ok('unitPrice', '0', '9999999')
    ng('unitPrice', 'OUT_OF_RANGE', '10000000')
    ng('unitPrice', 'INVALID_FORMAT', '１００', ' 100', '100 ', '-0')
    ng('unitPrice', 'REQUIRED', null, '')
  })
  it('ケース入数', () => {
    ok('caseQuantity', '1', '9999')
    ng('caseQuantity', 'OUT_OF_RANGE', '0', '10000')
    ng('caseQuantity', 'INVALID_FORMAT', '９', '1 ', 'x')
    ng('caseQuantity', 'REQUIRED', null, '')
  })
  it('棚番', () => {
    ok('shelfNo', '01-001-01', '99-999-99')
    ng(
      'shelfNo',
      'INVALID_FORMAT',
      '1-001-01',
      '01-01-01',
      '01-001-1',
      '010010 1',
      '01-001-011',
      '01001001',
      '０１-００１-０１',
      ' 01-001-01',
      '01-001-01 ',
      '01-001-01\n',
      'AB-001-01',
    )
    ng('shelfNo', 'REQUIRED', null, '')
  })
  it('商品コード', () => {
    ok('productCode', '00000000', '12345678')
    ng(
      'productCode',
      'INVALID_FORMAT',
      '1234567',
      '123456789',
      '１２３４５６７８',
      ' 12345678',
      '12345678 ',
      '1234567a',
    )
    ng('productCode', 'REQUIRED', null, '')
  })
  it('商品名（サロゲートペアは1文字）', () => {
    ok('productName', 'a', 'あ'.repeat(30), '𠮷'.repeat(30))
    ng('productName', 'INVALID_LENGTH', 'あ'.repeat(31), '𠮷'.repeat(31))
    ng('productName', 'REQUIRED', null, '')
  })
  it('ユーザー名', () => {
    ok('userName', '山', 'あ'.repeat(15))
    ng('userName', 'INVALID_LENGTH', 'あ'.repeat(16))
    ng('userName', 'REQUIRED', null, '')
  })
  it('ユーザーID', () => {
    ok('userId', 'ab12', 'AB12', '0000')
    ng('userId', 'INVALID_FORMAT', 'abc', 'abcde', 'ａｂ12', 'ab1 ', ' ab1', 'ab-1', 'あいうえ')
    ng('userId', 'REQUIRED', null, '')
  })
  it('パスワード（前後空白も文字として数える）', () => {
    ok('password', 'a'.repeat(8), 'a'.repeat(14), 'あ'.repeat(8))
    ng('password', 'INVALID_LENGTH', 'a'.repeat(7), 'a'.repeat(15), ' ' + 'a'.repeat(14))
    ng('password', 'REQUIRED', null, '')
  })
  it('備考（空・null可）', () => {
    ok('remarks', null, '', 'a', 'あ'.repeat(50))
    ng('remarks', 'INVALID_LENGTH', 'あ'.repeat(51))
  })
  it('理由', () => {
    ok('reason', 'a', 'あ'.repeat(50))
    ng('reason', 'INVALID_LENGTH', 'あ'.repeat(51))
    ng('reason', 'REQUIRED', null, '')
  })
  it('備考以外は空・null・undefined が必須エラー', () => {
    for (const rule of Object.keys(INPUT_RULES) as RuleName[]) {
      if (rule === 'remarks') continue
      for (const v of [null, undefined, '']) expect(checkRule(rule, v), rule).toBe('REQUIRED')
    }
  })
  it('全角数字と空白は数字ルールで不正', () => {
    for (const v of ['１', '１２３４５６７８', ' 1', '1 ']) ng('quantity', 'INVALID_FORMAT', v)
  })
  it('空白だけの値は補正されない（数字ルールは形式不正、文字数ルールは1文字として数える）', () => {
    ng('quantity', 'INVALID_FORMAT', ' ')
    ok('productName', ' ')
    ng('productName', 'INVALID_LENGTH', ' '.repeat(31))
  })
  it('項目名はサーバー側の識別子と一致する', () => {
    expect(Object.keys(INPUT_RULES)).toEqual([
      'quantity',
      'unitPrice',
      'caseQuantity',
      'shelfNo',
      'productCode',
      'productName',
      'userName',
      'userId',
      'password',
      'remarks',
      'reason',
    ])
    for (const [key, def] of Object.entries(INPUT_RULES)) expect(def.field).toBe(key)
  })
})

describe('検証結果', () => {
  it('複数エラーをまとめて返せる', () => {
    const result = new ValidationResult()
    validateRule(result, 'quantity', '0')
    validateRule(result, 'productCode', 'abc')
    validateRule(result, 'productName', '')
    validateRule(result, 'remarks', 'ok')
    validateRule(result, 'quantity', '１', 'outQuantity')

    expect(result.isValid()).toBe(false)
    expect(result.errors()).toEqual([
      { field: 'quantity', type: 'OUT_OF_RANGE' },
      { field: 'productCode', type: 'INVALID_FORMAT' },
      { field: 'productName', type: 'REQUIRED' },
      { field: 'outQuantity', type: 'INVALID_FORMAT' },
    ])
    expect(result.typesOf('quantity')).toEqual(['OUT_OF_RANGE'])
    expect(result.typesOf('none')).toEqual([])
  })
  it('問題なければ有効', () => {
    expect(validateValue('quantity', '10').isValid()).toBe(true)
  })
  it('フォーム一括検証', () => {
    const r = validateForm({ userId: 'ab', password: 'a'.repeat(8), remarks: undefined })
    expect(r.errors()).toEqual([{ field: 'userId', type: 'INVALID_FORMAT' }])
  })
})
