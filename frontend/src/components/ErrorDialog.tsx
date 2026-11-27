import { useDialogKeys } from './useDialogKeys'

type Props = {
  messages: string[]
  onClose: () => void
}

/** エラーダイアログ。複数のエラーをまとめて表示する。Enter か Esc で閉じる。 */
export default function ErrorDialog({ messages, onClose }: Props) {
  const { defaultRef } = useDialogKeys(onClose, onClose)
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="error-dialog-title"
        aria-describedby="error-dialog-list"
        className="w-96 rounded bg-white p-6 shadow-lg"
      >
        <h2 id="error-dialog-title" className="mb-3 font-bold text-red-700">
          エラー
        </h2>
        <ul id="error-dialog-list" className="mb-6 list-disc pl-5 text-slate-800">
          {messages.map((m, i) => (
            <li key={i}>{m}</li>
          ))}
        </ul>
        <div className="flex justify-end">
          <button
            type="button"
            ref={defaultRef}
            onClick={onClose}
            className="rounded bg-blue-600 px-4 py-1 text-white"
          >
            閉じる
          </button>
        </div>
      </div>
    </div>
  )
}
