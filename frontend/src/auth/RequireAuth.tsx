import { Navigate, Outlet } from 'react-router'
import { useAuth } from './AuthContext'

/** ルートガード。未ログインは /login、権限なし（adminOnly で一般）は /menu へ（メッセージなし）。 */
export default function RequireAuth({ adminOnly }: { adminOnly: boolean }) {
  const { user, loading } = useAuth()
  if (loading) return null
  if (!user) return <Navigate to="/login" replace />
  if (adminOnly && user.role !== 'ADMIN') return <Navigate to="/menu" replace />
  return <Outlet />
}
