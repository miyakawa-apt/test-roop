import type { ScreenDef } from '../screens'

/** 業務画面の仮ページ（画面名だけを表示する）。 */
export default function Placeholder({ screen }: { screen: ScreenDef }) {
  return (
    <main className="p-6">
      <h1 className="mb-2 text-xl font-bold">
        {screen.name}（{screen.code}）
      </h1>
      <p className="text-slate-500">準備中です。</p>
    </main>
  )
}
