// 画面に出すメッセージ文面を集約する。分類ごとに文末を統一する。
// - confirm: 確認ダイアログの問いかけ。「〜します。よろしいですか？」の疑問形
// - error  : エラー文。「〜してください。」
// - result : 完了・結果通知。「〜です。」（登録済みです。）
// 要件の2形式（〜です。／〜してください。）は通知文・エラー文の書き方で、確認はダイアログの問いかけとして疑問形にする。

export const confirmMessages = {
  register: '登録します。よろしいですか？',
  update: '更新します。よろしいですか？',
  delete: '削除します。よろしいですか？',
} as const

export const errorMessages = {
  required: (name: string) => `${name}を入力してください。`,
  select: (name: string) => `${name}を選択してください。`,
  maxLength: (name: string, max: number) => `${name}は${max}文字以内で入力してください。`,
  retry: 'しばらくしてからもう一度操作してください。',
} as const

export const resultMessages = {
  registered: '登録済みです。',
  updated: '更新済みです。',
  deleted: '削除済みです。',
} as const

/** 入力項目名（規則名）→ 画面に出す表示名。 */
export const fieldLabels: Record<string, string> = {
  quantity: '数量',
  unitPrice: '単価',
  caseQuantity: 'ケース入数',
  shelfNo: '棚番',
  productCode: '商品コード',
  productName: '商品名',
  userName: 'ユーザー名',
  userId: 'ユーザーID',
  'password': 'パスワード',
  remarks: '備考',
  reason: '理由',
}

/** 検証結果（項目名＋エラー種別）から「〜してください。」形のエラー文を作る。 */
export function validationMessage(field: string, type: string): string {
  const name = fieldLabels[field] ?? field
  switch (type) {
    case 'REQUIRED':
      return `${name}を入力してください。`
    case 'INVALID_FORMAT':
      return `${name}の形式を確認して入力してください。`
    case 'OUT_OF_RANGE':
      return `${name}の範囲を確認して入力してください。`
    case 'INVALID_LENGTH':
      return `${name}の文字数を確認して入力してください。`
    default:
      return `${name}を確認して入力してください。`
  }
}

/** ログイン画面のメッセージ。 */
export const loginMessages = {
  mismatch: 'ユーザーIDまたはパスワードが違います。確認して入力してください。',
  failed: 'ログインできませんでした。',
} as const

/** 検索結果が0件のとき: 「該当する〇〇がありません。」 */
export const notFoundMessage = (name: string): string => `該当する${name}がありません。`
