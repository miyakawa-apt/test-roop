import { useDialogKeys } from './useDialogKeys'

type Props = {
  message: string
  onYes: () => void
  onNo: () => void
}

/** 確認ダイアログ（登録・更新・削除の前）。Enter=はい、Esc=いいえ。既定フォーカスは「はい」。 */
export default function ConfirmDialog({ message, onYes, onNo }: Props) {
  const { defaultRef, onKeyDown } = useDialogKeys(onYes, onNo)
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-message"
        onKeyDown={onKeyDown}
        className="w-96 rounded bg-white p-6 shadow-lg"
      >
        <p id="confirm-dialog-message" className="mb-6 text-slate-800">
          {message}
        </p>
        <div className="flex justify-end gap-2">
          <button
            type="button"
            ref={defaultRef}
            onClick={onYes}
            className="rounded bg-blue-600 px-4 py-1 text-white"
          >
            はい
          </button>
          <button
            type="button"
            onClick={onNo}
            className="rounded border border-slate-300 px-4 py-1 text-slate-700"
          >
            いいえ
          </button>
        </div>
      </div>
    </div>
  )
}
