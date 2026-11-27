import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'

/** 完了メッセージを表示する時間（ミリ秒） */
export const NOTICE_DURATION_MS = 7500

type NoticeApi = {
  /** 画面下に緑で表示する。表示中なら置き換えて7.5秒を数え直す。 */
  show: (message: string) => void
  /** すぐ消す（画面遷移時など）。 */
  clear: () => void
}

const NoticeContext = createContext<NoticeApi | null>(null)

/**
 * 完了メッセージの Provider。表示中の画面（ページ）の中に置く。
 * ページを離れて Provider がアンマウントされればメッセージも消える。
 */
export function NoticeProvider({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState<string | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const stop = useCallback(() => {
    if (timer.current !== null) clearTimeout(timer.current)
    timer.current = null
  }, [])
  const clear = useCallback(() => {
    stop()
    setMessage(null)
  }, [stop])
  const show = useCallback(
    (m: string) => {
      stop()
      setMessage(m)
      timer.current = setTimeout(() => {
        timer.current = null
        setMessage(null)
      }, NOTICE_DURATION_MS)
    },
    [stop],
  )
  useEffect(() => stop, [stop])
  const api = useMemo(() => ({ show, clear }), [show, clear])

  return (
    <NoticeContext.Provider value={api}>
      {children}
      {message !== null && (
        <div
          role="status"
          className="fixed inset-x-0 bottom-0 z-40 bg-green-700 px-4 py-3 text-center text-white"
        >
          {message}
        </div>
      )}
    </NoticeContext.Provider>
  )
}

export function useNotice(): NoticeApi {
  const ctx = useContext(NoticeContext)
  if (!ctx) throw new Error('useNotice は NoticeProvider の中で使ってください。')
  return ctx
}
