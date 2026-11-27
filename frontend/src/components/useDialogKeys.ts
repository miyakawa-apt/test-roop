import { useEffect, useRef } from 'react'
import type { KeyboardEvent } from 'react'

/** ダイアログ共通: 表示時に既定ボタンへフォーカスし、Enter/Esc を割り当てる。 */
export function useDialogKeys(onEnter: () => void, onEscape: () => void) {
  const defaultRef = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    defaultRef.current?.focus()
    return () => previous?.focus?.()
  }, [])
  const onKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault() // フォーカス中のボタンの click と二重にならないようにする
      onEnter()
    } else if (e.key === 'Escape') {
      e.preventDefault()
      onEscape()
    }
  }
  return { defaultRef, onKeyDown }
}
