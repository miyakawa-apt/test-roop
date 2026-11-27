// 画面に出すメッセージ文面を集約する。分類ごとに文末を統一する。
// - confirm: 確認ダイアログ。疑問形「〜よろしいですか？」
// - error  : エラー。「〜してください。」
// - result : 完了・結果通知。「〜です。」系の言い切り

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
  registered: '登録しました。',
  updated: '更新しました。',
  deleted: '削除しました。',
} as const

/** 検索結果が0件のとき: 「該当する〇〇がありません。」 */
export const notFoundMessage = (name: string): string => `該当する${name}がありません。`
