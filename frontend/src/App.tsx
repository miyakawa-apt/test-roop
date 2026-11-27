import type { ReactElement } from 'react'
import { Navigate, Route, Routes } from 'react-router'
import { AuthProvider, useAuth } from './auth/AuthContext'
import RequireAuth from './auth/RequireAuth'
import Layout from './components/Layout'
import LocationAdmin from './pages/LocationAdmin'
import Login from './pages/Login'
import Menu from './pages/Menu'
import Placeholder from './pages/Placeholder'
import ProductAdmin from './pages/ProductAdmin'
import UserAdmin from './pages/UserAdmin'
import { ADMIN_SCREENS, BUSINESS_SCREENS } from './screens'
import type { ScreenDef } from './screens'

/** 実装済みの画面（パス → 画面）。ここに無いパスは仮ページ（Placeholder）を出す。 */
const IMPLEMENTED_PAGES: Record<string, ReactElement> = {
  '/product-admin': <ProductAdmin />,
  '/location-admin': <LocationAdmin />,
  '/user-admin': <UserAdmin />,
}

const pageOf = (s: ScreenDef) => IMPLEMENTED_PAGES[s.path] ?? <Placeholder screen={s} />

/** ログイン済みで /login を開いたら /menu へ。 */
function LoginRoute() {
  const { user, loading } = useAuth()
  if (loading) return null
  return user ? <Navigate to="/menu" replace /> : <Login />
}

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<Navigate to="/menu" replace />} />
          <Route path="/login" element={<LoginRoute />} />
          <Route path="/menu" element={<Menu />} />
          <Route element={<RequireAuth adminOnly={false} />}>
            {BUSINESS_SCREENS.map((s) => (
              <Route key={s.code} path={s.path} element={<Placeholder screen={s} />} />
            ))}
          </Route>
          <Route element={<RequireAuth adminOnly />}>
            {ADMIN_SCREENS.map((s) => (
              <Route key={s.code} path={s.path} element={pageOf(s)} />
            ))}
          </Route>
          <Route path="*" element={<Navigate to="/menu" replace />} />
        </Route>
      </Routes>
    </AuthProvider>
  )
}
