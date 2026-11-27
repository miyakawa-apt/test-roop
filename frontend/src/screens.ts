// 画面の定義（ルート・権限）。メニューとルートガードで共有する。

export interface ScreenDef {
  /** 画面番号（S03 など）。 */
  code: string
  path: string
  name: string
  /** true なら管理者のみ。 */
  adminOnly: boolean
}

export const BUSINESS_SCREENS: readonly ScreenDef[] = [
  { code: 'S03', path: '/stock-in', name: '入庫', adminOnly: false },
  { code: 'S04', path: '/stock-out', name: '出庫', adminOnly: false },
  { code: 'S05', path: '/stock-list', name: '在庫リスト', adminOnly: false },
  { code: 'S06', path: '/results', name: '実績照会', adminOnly: false },
]

export const ADMIN_SCREENS: readonly ScreenDef[] = [
  { code: 'S07', path: '/stock-edit', name: '在庫修正', adminOnly: true },
  { code: 'S08', path: '/product-admin', name: '商品メンテナンス', adminOnly: true },
  { code: 'S09', path: '/location-admin', name: 'ロケーションメンテ', adminOnly: true },
  { code: 'S10', path: '/user-admin', name: 'ユーザー管理', adminOnly: true },
]
