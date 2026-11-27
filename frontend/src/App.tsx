import { Navigate, Route, Routes } from 'react-router'
import { AuthProvider, useAuth } from './auth/AuthContext'
import RequireAuth from './auth/RequireAuth'
import Layout from './components/Layout'
import Login from './pages/Login'
import Menu from './pages/Menu'
import Placeholder from './pages/Placeholder'
import { ADMIN_SCREENS, BUSINESS_SCREENS } from './screens'

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
              <Route key={s.code} path={s.path} element={<Placeholder screen={s} />} />
            ))}
          </Route>
          <Route path="*" element={<Navigate to="/menu" replace />} />
        </Route>
      </Routes>
    </AuthProvider>
  )
}
