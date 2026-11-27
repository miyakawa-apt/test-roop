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

/** 検索結果が0件のとき: 「該当する〇〇がありません。」 */
export const notFoundMessage = (name: string): string => `該当する${name}がありません。`
