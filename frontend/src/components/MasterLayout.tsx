import type { ReactNode } from 'react'
import CountLabel from './CountLabel'

type Props = {
  /** 画面の見出し（例: 商品メンテナンス（S08））。 */
  title: string
  /** 入力欄（TextInput など）。 */
  form: ReactNode
  /** ボタン列（登録・更新・削除・クリア など）。 */
  buttons: ReactNode
  /** 一覧の件数（右上に「N件」）。 */
  count: number
  /** 一覧（テーブルや「該当なし」）。 */
  children: ReactNode
}

/**
 * マスタ画面（商品・ロケーション・ユーザー）共通の配置。
 * 上から 見出し → 入力欄 → ボタン列 → 件数（右上）→ 一覧。中身は各画面が渡す。
 */
export default function MasterLayout({ title, form, buttons, count, children }: Props) {
  return (
    <main className="mx-auto max-w-4xl p-6">
      <h1 className="mb-4 text-xl font-bold">{title}</h1>
      <section aria-label="入力" className="mb-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
        {form}
      </section>
      <div className="mb-4 flex gap-2">{buttons}</div>
      <section aria-label="一覧">
        <CountLabel count={count} />
        {children}
      </section>
    </main>
  )
}
