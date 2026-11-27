// 画面側の入力ルール検証（DT-01）。サーバー側 InputRule / ErrorType / ValidationResult と同じ規則。
// 値は補正しない（trim・全角→半角変換なし）。文字数はコードポイント数で数える。
// 文言は持たない（共通部品側で項目名＋エラー種別から組み立てる）。

export type ErrorType = 'REQUIRED' | 'INVALID_FORMAT' | 'OUT_OF_RANGE' | 'INVALID_LENGTH'

type RuleDef =
  | { field: string; kind: 'number'; min: number; max: number }
  | { field: string; kind: 'text'; min: number; max: number }
  | { field: string; kind: 'pattern'; pattern: RegExp }

export const INPUT_RULES = {
  quantity: { field: 'quantity', kind: 'number', min: 1, max: 99_999 },
  unitPrice: { field: 'unitPrice', kind: 'number', min: 0, max: 9_999_999 },
  caseQuantity: { field: 'caseQuantity', kind: 'number', min: 1, max: 9_999 },
  shelfNo: { field: 'shelfNo', kind: 'pattern', pattern: /^[0-9]{2}-[0-9]{3}-[0-9]{2}$/ },
  productCode: { field: 'productCode', kind: 'pattern', pattern: /^[0-9]{8}$/ },
  productName: { field: 'productName', kind: 'text', min: 1, max: 30 },
  userName: { field: 'userName', kind: 'text', min: 1, max: 15 },
  userId: { field: 'userId', kind: 'pattern', pattern: /^[A-Za-z0-9]{4}$/ },
  password: { field: 'password', kind: 'text', min: 8, max: 14 },
  remarks: { field: 'remarks', kind: 'text', min: 0, max: 50 },
  reason: { field: 'reason', kind: 'text', min: 1, max: 50 },
} as const satisfies Record<string, RuleDef>

export type RuleName = keyof typeof INPUT_RULES

/** 検証エラー1件（項目名＋エラー種別）。 */
export interface ValidationError {
  field: string
  type: ErrorType
}

const DIGITS = /^[0-9]+$/

/** 値を検証し、エラーがあれば種別を返す（なければ undefined）。null・undefined は空として扱う。 */
export function checkRule(rule: RuleName, value: string | null | undefined): ErrorType | undefined {
  const def: RuleDef = INPUT_RULES[rule]
  const empty = value == null || value === ''
  if (empty && !(def.kind === 'text' && def.min === 0)) {
    return 'REQUIRED'
  }
  const v = value ?? ''
  switch (def.kind) {
    case 'number': {
      if (!DIGITS.test(v)) return 'INVALID_FORMAT'
      // 先頭の 0 を除いた桁数が 18 を超えるものは範囲外（サーバー側と同じ）
      const significant = v.replace(/^0+(?=.)/, '')
      if (significant.length > 18) return 'OUT_OF_RANGE'
      const n = BigInt(significant)
      return n < BigInt(def.min) || n > BigInt(def.max) ? 'OUT_OF_RANGE' : undefined
    }
    case 'text': {
      const length = [...v].length
      return length < def.min || length > def.max ? 'INVALID_LENGTH' : undefined
    }
    case 'pattern':
      return def.pattern.test(v) ? undefined : 'INVALID_FORMAT'
  }
}

/** 複数エラーをまとめて保持する検証結果。 */
export class ValidationResult {
  private readonly list: ValidationError[] = []

  add(error: ValidationError): void {
    this.list.push(error)
  }

  isValid(): boolean {
    return this.list.length === 0
  }

  errors(): readonly ValidationError[] {
    return this.list
  }

  /** 指定項目のエラー種別一覧。 */
  typesOf(field: string): ErrorType[] {
    return this.list.filter((e) => e.field === field).map((e) => e.type)
  }
}

/** 検証して、エラーがあれば項目名（省略時は既定の項目名）で result に追加する。 */
export function validateRule(
  result: ValidationResult,
  rule: RuleName,
  value: string | null | undefined,
  field: string = INPUT_RULES[rule].field,
): ValidationResult {
  const type = checkRule(rule, value)
  if (type) result.add({ field, type })
  return result
}

/** 単独の値を検証して新しい結果を返す。 */
export function validateValue(rule: RuleName, value: string | null | undefined): ValidationResult {
  return validateRule(new ValidationResult(), rule, value)
}

/** フォームの値（規則名→値）をまとめて検証する。キー（既定の項目名）がそのまま項目名になる。 */
export function validateForm(
  values: Partial<Record<RuleName, string | null | undefined>>,
): ValidationResult {
  const result = new ValidationResult()
  for (const rule of Object.keys(values) as RuleName[]) {
    validateRule(result, rule, values[rule])
  }
  return result
}
