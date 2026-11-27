import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'

const pad = (n: number) => String(n).padStart(2, '0')

/** yyyy年MM月dd日 HH:mm:ss */
export function formatDateTime(d: Date): string {
  return (
    `${d.getFullYear()}年${pad(d.getMonth() + 1)}月${pad(d.getDate())}日 ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  )
}

/** 共通ヘッダー（全画面）。ログイン者（ID（権限）または未ログイン）と現在日時（1秒ごとに更新）。 */
export default function Header() {
  const { user } = useAuth()
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000)
    return () => clearInterval(id)
  }, [])
  return (
    <header className="flex items-center justify-between bg-slate-800 px-4 py-2 text-white">
      <span className="font-bold">Mony</span>
      <div className="flex gap-6 text-sm">
        <span data-testid="header-user">{user ? `${user.userId}（${user.roleLabel}）` : '未ログイン'}</span>
        <time data-testid="header-clock">{formatDateTime(now)}</time>
      </div>
    </header>
  )
}
