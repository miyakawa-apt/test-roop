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
  userName: '氏名',
  userId: 'ユーザーID',
  role: '権限',
  'password': 'パスワード',
  remarks: '備考',
  reason: '理由',
  category: 'カテゴリ',
}

/** 選択式の項目（未入力のとき「〜を選択してください。」にする）。 */
const SELECT_FIELDS: ReadonlySet<string> = new Set(['category'])

/** 検証結果（項目名＋エラー種別）から「〜してください。」形のエラー文を作る。 */
export function validationMessage(field: string, type: string): string {
  const name = fieldLabels[field] ?? field
  switch (type) {
    case 'REQUIRED':
      return SELECT_FIELDS.has(field) ? errorMessages.select(name) : `${name}を入力してください。`
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

/** 商品メンテナンス画面（S08）のメッセージ。 */
export const productMessages = {
  /** 更新・削除の対象が他の人に先に削除されていた（404）。 */
  notFound: '選択された商品は存在しません。',
  /** 商品コードの重複（409 DUPLICATE）。 */
  duplicate: 'その商品コードは既に登録されています。別の商品コードを入力してください。',
  /** 在庫のある商品の削除（409 HAS_STOCK）。 */
  hasStock: '在庫のある商品は削除できません。',
  loadFailed: '商品の一覧を取得できませんでした。',
  failed: '処理できませんでした。',
} as const

/** ロケーションメンテナンス画面（S09）のメッセージ。 */
export const locationMessages = {
  /** 更新・削除の対象が他の人に先に削除されていた（404）。 */
  notFound: '選択された棚は存在しません。',
  /** 棚番の重複（409 DUPLICATE）。 */
  duplicate: 'その棚番は既に登録されています。別の棚番を入力してください。',
  /** 在庫のある棚の削除（409 HAS_STOCK）。 */
  hasStockDelete: '在庫のある棚は削除できません。',
  /** 在庫のある棚の棚番変更・禁止棚への更新（409 HAS_STOCK）。 */
  hasStockUpdate: '在庫のある棚は棚番の変更・禁止棚への変更はできません。',
  loadFailed: '棚の一覧を取得できませんでした。',
  failed: '処理できませんでした。',
  /** 棚状況の検証エラー文（棚状況は選択式）。 */
  shelfStatus: (type: string): string =>
    type === 'REQUIRED' ? errorMessages.select('棚状況') : '棚状況の形式を確認して入力してください。',
} as const

/** ユーザー管理画面（S10）のメッセージ。 */
export const userMessages = {
  /** 更新・削除の対象が他の人に先に削除されていた（404）。 */
  notFound: '選択されたユーザーは存在しません。',
  /** ユーザーIDの重複（409 DUPLICATE）。 */
  duplicate: 'そのユーザーIDは既に登録されています。別のユーザーIDを入力してください。',
  /** ログイン中の本人の削除（409 SELF）。 */
  self: 'ログイン中のユーザーは削除できません。',
  /** 最後の管理者の削除・一般への変更（409 LAST_ADMIN）。 */
  lastAdmin: '最後の管理者は削除できません。また、一般に変更することもできません。',
  /** 実績の履歴があるユーザーの削除（409 HAS_RESULTS）。 */
  hasResults: '実績のあるユーザーは削除できません。',
  loadFailed: 'ユーザーの一覧を取得できませんでした。',
  failed: '処理できませんでした。',
} as const
