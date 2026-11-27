import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { fetchMe, postLogin, postLogout } from '../api/auth'
import type { AuthUser, LoginResult } from '../api/auth'

interface AuthState {
  /** ログイン中のユーザー。未ログインは null。 */
  user: AuthUser | null
  /** 起動時のログイン状態の復元中は true。 */
  loading: boolean
  /** ログインする。成功すると user が入る。パスワードは保持しない。 */
  login: (userId: string, pw: string) => Promise<LoginResult>
  /** ログアウトする（サーバーのセッションを破棄し user を null にする）。 */
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)

  // 起動時・再読み込み後に GET /api/auth/me でログイン状態を復元する
  useEffect(() => {
    let active = true
    fetchMe()
      .catch(() => null)
      .then((me) => {
        if (!active) return
        setUser(me)
        setLoading(false)
      })
    return () => {
      active = false
    }
  }, [])

  const login = useCallback(async (userId: string, pw: string) => {
    let result: LoginResult
    try {
      result = await postLogin(userId, pw)
    } catch {
      result = { type: 'error' }
    }
    if (result.type === 'success') setUser(result.user)
    return result
  }, [])

  const logout = useCallback(async () => {
    try {
      await postLogout()
    } finally {
      setUser(null)
    }
  }, [])

  const value = useMemo(() => ({ user, loading, login, logout }), [user, loading, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth は AuthProvider の内側で使ってください。')
  return ctx
}
