import { useEffect, useRef } from 'react'

/**
 * ダイアログ共通: 表示時に既定ボタンへフォーカスし、Enter/Esc を割り当てる。
 * キーは document レベルで受けるので、フォーカスがダイアログ外に出ても効く。
 * キーの押しっぱなし（e.repeat）は無視して二重呼び出しを防ぐ。
 */
export function useDialogKeys(onEnter: () => void, onEscape: () => void) {
  const defaultRef = useRef<HTMLButtonElement>(null)
  const handlers = useRef({ onEnter, onEscape })
  useEffect(() => {
    handlers.current = { onEnter, onEscape }
  })

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    defaultRef.current?.focus()
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key !== 'Enter' && e.key !== 'Escape') return
      e.preventDefault() // フォーカス中のボタンの click と二重にならないようにする
      if (e.repeat) return
      if (e.key === 'Enter') handlers.current.onEnter()
      else handlers.current.onEscape()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      previous?.focus?.()
    }
  }, [])
  return { defaultRef }
}
