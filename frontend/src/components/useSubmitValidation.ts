import { useState } from 'react'
import type { KeyboardEvent } from 'react'

/**
 * 入力チェックは「Enter キー」「ボタン押下」のときだけ行い、
 * フォーカスが離れたとき（blur）には行わない、という方針を表すフック。
 * blur 用のハンドラは意図的に提供しない。
 *
 * @param validate エラーメッセージの配列を返す（空配列ならOK）
 * @param onValid  エラーが無いときに呼ぶ（確認ダイアログを出す等）
 */
export function useSubmitValidation(validate: () => string[], onValid: () => void) {
  const [errors, setErrors] = useState<string[]>([])

  /** ボタン押下（onClick / onSubmit）から呼ぶ。 */
  const submit = () => {
    const result = validate()
    setErrors(result)
    if (result.length === 0) onValid()
  }
  /** 入力欄の onKeyDown に渡す。Enter のときだけ検証する。 */
  const onEnterKey = (e: KeyboardEvent<HTMLElement>) => {
    if (e.key !== 'Enter') return
    // IME の変換確定の Enter では検証しない
    if (e.nativeEvent.isComposing || e.keyCode === 229) return
    // textarea の Enter は改行なので潰さない（preventDefault は input のみ）
    if (e.currentTarget instanceof HTMLTextAreaElement) return
    e.preventDefault()
    submit()
  }
  const clearErrors = () => setErrors([])

  return { errors, submit, onEnterKey, clearErrors }
}
