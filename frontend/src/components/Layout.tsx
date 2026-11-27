import { Outlet } from 'react-router'
import Header from './Header'

/** 全画面共通の枠（ヘッダー＋本文）。 */
export default function Layout() {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-800">
      <Header />
      <Outlet />
    </div>
  )
}
