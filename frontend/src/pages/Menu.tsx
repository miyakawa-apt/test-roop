import { useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { ADMIN_SCREENS, BUSINESS_SCREENS } from '../screens'
import type { ScreenDef } from '../screens'

/** S02 メニュー。 */
export default function Menu() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const canOpen = (s: ScreenDef) => user !== null && (!s.adminOnly || user.role === 'ADMIN')

  const group = (title: string, screens: readonly ScreenDef[]) => (
    <section aria-label={title} className="mb-6">
      <h2 className="mb-2 font-bold">{title}</h2>
      <div className="flex flex-wrap gap-3">
        {screens.map((s) => (
          <button
            key={s.code}
            type="button"
            disabled={!canOpen(s)}
            onClick={() => navigate(s.path)}
            className="w-40 rounded border border-slate-300 bg-white px-3 py-3 enabled:hover:bg-blue-50 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400"
          >
            {s.name}
          </button>
        ))}
      </div>
    </section>
  )

  return (
    <main className="p-6">
      <div className="mb-6 flex items-center gap-4">
        <h1 className="text-xl font-bold">メニュー</h1>
        <span data-testid="menu-user">
          {user ? `ID : ${user.userId} (${user.roleLabel})` : '未ログイン'}
        </span>
        {user ? (
          <button
            type="button"
            onClick={() => void logout()}
            className="rounded bg-slate-600 px-4 py-1 text-white"
          >
            ログアウト
          </button>
        ) : (
          <button
            type="button"
            onClick={() => navigate('/login')}
            className="rounded bg-blue-600 px-4 py-1 text-white"
          >
            ログイン
          </button>
        )}
      </div>
      {group('業務メニュー', BUSINESS_SCREENS)}
      {group('管理者メニュー', ADMIN_SCREENS)}
    </main>
  )
}
