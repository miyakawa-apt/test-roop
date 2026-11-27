import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import App from './App'
import { ADMIN_SCREENS, BUSINESS_SCREENS } from './screens'
import { ADMIN_USER, GENERAL_USER, VALID_PW, installFakeServer, renderApp } from './testUtils'
import type { AuthUser } from './api/auth'

const ACCOUNTS: Record<string, AuthUser> = { A001: ADMIN_USER, B002: GENERAL_USER }

function clearCookies() {
  document.cookie = 'XSRF-TOKEN=; path=/; max-age=0'
}

beforeEach(clearCookies)
afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  clearCookies()
})

const idInput = () => screen.getByLabelText('ユーザーID') as HTMLInputElement
const pwInput = () => screen.getByLabelText('パスワード') as HTMLInputElement

function fillLogin(id: string, pw: string) {
  fireEvent.change(idInput(), { target: { value: id } })
  fireEvent.change(pwInput(), { target: { value: pw } })
}

describe('ログイン画面（S01）', () => {
  it('パスワード欄は伏せ字で、ユーザーID欄は4文字までの入力欄である', async () => {
    installFakeServer(null, ACCOUNTS)
    renderApp('/login')
    await screen.findByRole('heading', { name: 'ログイン' })
    expect(pwInput().type).toBe('password')
    expect(idInput().maxLength).toBe(4)
    expect(pwInput().maxLength).toBe(14)
  })

  it('ログイン成功でメニューへ遷移し、ログイン者が表示される', async () => {
    const server = installFakeServer(null, ACCOUNTS)
    renderApp('/login')
    await screen.findByRole('heading', { name: 'ログイン' })
    fillLogin('A001', VALID_PW)
    fireEvent.click(screen.getByRole('button', { name: 'ログイン' }))
    expect(await screen.findByRole('heading', { name: 'メニュー' })).toBeInTheDocument()
    expect(screen.getByTestId('menu-user')).toHaveTextContent('ID : A001 (管理者)')
    const login = server.calls.find((c) => c.path === '/api/auth/login')!
    expect(login.method).toBe('POST')
    const sent = login.body as Record<string, unknown>
    expect(sent.userId).toBe('A001')
    expect(Object.keys(sent).sort()).toEqual(['password', 'userId'])
    expect(sent['password']).toBe(VALID_PW)
    expect(login.headers['X-XSRF-TOKEN']).toBe('test-token')
  })

  it('Enterキーでもログインできる', async () => {
    installFakeServer(null, ACCOUNTS)
    renderApp('/login')
    await screen.findByRole('heading', { name: 'ログイン' })
    fillLogin('B002', VALID_PW)
    fireEvent.keyDown(pwInput(), { key: 'Enter' })
    expect(await screen.findByRole('heading', { name: 'メニュー' })).toBeInTheDocument()
    expect(screen.getByTestId('menu-user')).toHaveTextContent('ID : B002 (一般)')
  })

  it('不一致（401）はエラーダイアログを出し、パスワードだけクリアする', async () => {
    installFakeServer(null, ACCOUNTS)
    renderApp('/login')
    await screen.findByRole('heading', { name: 'ログイン' })
    fillLogin('A001', 'wrongpass1')
    fireEvent.click(screen.getByRole('button', { name: 'ログイン' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('ユーザーIDまたはパスワードが違います。確認して入力してください。')).toBeInTheDocument()
    expect(idInput().value).toBe('A001')
    expect(pwInput().value).toBe('')
    // 閉じても画面に留まる
    fireEvent.click(within(dialog).getByRole('button', { name: '閉じる' }))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
  })

  it('入力検証エラーは複数まとめて表示し、サーバーへは送らない', async () => {
    const server = installFakeServer(null, ACCOUNTS)
    renderApp('/login')
    await screen.findByRole('heading', { name: 'ログイン' })
    fillLogin('', 'short')
    fireEvent.click(screen.getByRole('button', { name: 'ログイン' }))
    const dialog = await screen.findByRole('alertdialog')
    const items = within(dialog).getAllByRole('listitem').map((li) => li.textContent)
    expect(items).toEqual(['ユーザーIDを入力してください。', 'パスワードの文字数を確認して入力してください。'])
    expect(server.calls.some((c) => c.path === '/api/auth/login')).toBe(false)
    // 検証エラー時は入力値を残す
    expect(pwInput().value).toBe('short')
  })

  it('形式エラー（半角英数4文字でない）も表示する', async () => {
    installFakeServer(null, ACCOUNTS)
    renderApp('/login')
    await screen.findByRole('heading', { name: 'ログイン' })
    fillLogin('A-1', VALID_PW)
    fireEvent.keyDown(idInput(), { key: 'Enter' })
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('ユーザーIDの形式を確認して入力してください。')).toBeInTheDocument()
  })

  it('入力中・フォーカスを外しただけでは検証しない（blurでは検証しない）', async () => {
    installFakeServer(null, ACCOUNTS)
    renderApp('/login')
    await screen.findByRole('heading', { name: 'ログイン' })
    fireEvent.focus(idInput())
    fireEvent.change(idInput(), { target: { value: 'A' } })
    fireEvent.blur(idInput())
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('ログイン済みで /login を開くと /menu へ戻る', async () => {
    installFakeServer(ADMIN_USER, ACCOUNTS)
    renderApp('/login')
    expect(await screen.findByRole('heading', { name: 'メニュー' })).toBeInTheDocument()
  })

  it('パスワードをブラウザに保存しない', async () => {
    installFakeServer(null, ACCOUNTS)
    const setItem = vi.spyOn(Storage.prototype, 'setItem')
    renderApp('/login')
    await screen.findByRole('heading', { name: 'ログイン' })
    fillLogin('A001', VALID_PW)
    fireEvent.click(screen.getByRole('button', { name: 'ログイン' }))
    await screen.findByRole('heading', { name: 'メニュー' })
    expect(setItem).not.toHaveBeenCalled()
    expect(document.body.textContent).not.toContain(VALID_PW)
    setItem.mockRestore()
  })
})

describe('メニュー（S02）', () => {
  const allScreens = [...BUSINESS_SCREENS, ...ADMIN_SCREENS]

  async function openMenu(user: AuthUser | null) {
    installFakeServer(user, ACCOUNTS)
    renderApp('/menu')
    await screen.findByRole('heading', { name: 'メニュー' })
    await waitFor(() => expect(screen.getByTestId('header-user')).toBeInTheDocument())
    // ログイン状態の復元完了まで待つ
    if (user) await screen.findByText(`ID : ${user.userId} (${user.roleLabel})`)
    else await screen.findByRole('button', { name: 'ログイン' })
  }
  const enabled = (name: string) => !(screen.getByRole('button', { name }) as HTMLButtonElement).disabled

  it('未ログインは業務メニューも管理者メニューも押せず、ボタンは「ログイン」', async () => {
    await openMenu(null)
    allScreens.forEach((s) => expect(enabled(s.name)).toBe(false))
    expect(screen.getByTestId('menu-user')).toHaveTextContent('未ログイン')
    expect(screen.queryByRole('button', { name: 'ログアウト' })).not.toBeInTheDocument()
  })

  it('一般は業務メニューだけ押せ、管理者メニューは押せない', async () => {
    await openMenu(GENERAL_USER)
    BUSINESS_SCREENS.forEach((s) => expect(enabled(s.name)).toBe(true))
    ADMIN_SCREENS.forEach((s) => expect(enabled(s.name)).toBe(false))
  })

  it('管理者はすべて押せる', async () => {
    await openMenu(ADMIN_USER)
    allScreens.forEach((s) => expect(enabled(s.name)).toBe(true))
  })

  it('ログイン者を「ID : A001 (管理者)」の形式で表示し、ボタンは「ログアウト」', async () => {
    await openMenu(ADMIN_USER)
    expect(screen.getByTestId('menu-user')).toHaveTextContent('ID : A001 (管理者)')
    expect(screen.getByRole('button', { name: 'ログアウト' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'ログイン' })).not.toBeInTheDocument()
  })

  it('押せる項目を押すと各画面（仮ページ）へ遷移する', async () => {
    await openMenu(ADMIN_USER)
    for (const s of allScreens) {
      fireEvent.click(screen.getByRole('button', { name: s.name }))
      expect(await screen.findByRole('heading', { name: `${s.name}（${s.code}）` })).toBeInTheDocument()
      expect(screen.getByText('準備中です。')).toBeInTheDocument()
      cleanup()
      installFakeServer(ADMIN_USER, ACCOUNTS)
      renderApp('/menu')
      await screen.findByText('ID : A001 (管理者)')
    }
  })

  it('未ログインで「ログイン」を押すとログイン画面へ', async () => {
    await openMenu(null)
    fireEvent.click(screen.getByRole('button', { name: 'ログイン' }))
    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
  })

  it('ログアウトは POST してから、メニューに留まったまま未ログイン状態になる', async () => {
    const server = installFakeServer(ADMIN_USER, ACCOUNTS)
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
    renderApp('/menu')
    await screen.findByText('ID : A001 (管理者)')
    fireEvent.click(screen.getByRole('button', { name: 'ログアウト' }))
    expect(await screen.findByRole('button', { name: 'ログイン' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'メニュー' })).toBeInTheDocument()
    expect(screen.getByTestId('menu-user')).toHaveTextContent('未ログイン')
    expect(screen.getByTestId('header-user')).toHaveTextContent('未ログイン')
    const out = server.calls.find((c) => c.path === '/api/auth/logout')!
    expect(out.method).toBe('POST')
    expect(enabledAll()).toBe(false)
  })

  function enabledAll() {
    return [...BUSINESS_SCREENS, ...ADMIN_SCREENS].some((s) => enabled(s.name))
  }

  it('ログアウト後に再ログインできる（CSRFトークンを取り直す）', async () => {
    const server = installFakeServer(ADMIN_USER, ACCOUNTS)
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
    renderApp('/menu')
    await screen.findByText('ID : A001 (管理者)')
    fireEvent.click(screen.getByRole('button', { name: 'ログアウト' }))
    fireEvent.click(await screen.findByRole('button', { name: 'ログイン' }))
    await screen.findByRole('heading', { name: 'ログイン' })
    fillLogin('B002', VALID_PW)
    fireEvent.click(screen.getByRole('button', { name: 'ログイン' }))
    await screen.findByText('ID : B002 (一般)')
    const login = server.calls.find((c) => c.path === '/api/auth/login')!
    expect(login.headers['X-XSRF-TOKEN']).toBe('test-token')
    // ログアウトで Cookie が消えたので、ログイン前に GET で取り直している
    const idxLogout = server.calls.findIndex((c) => c.path === '/api/auth/logout')
    const idxLogin = server.calls.findIndex((c) => c.path === '/api/auth/login')
    expect(server.calls.slice(idxLogout + 1, idxLogin).some((c) => c.method === 'GET')).toBe(true)
  })
})

describe('共通ヘッダー', () => {
  it('ログイン中は「A001（管理者）」、未ログインは「未ログイン」を表示する', async () => {
    installFakeServer(ADMIN_USER, ACCOUNTS)
    renderApp('/menu')
    await waitFor(() => expect(screen.getByTestId('header-user')).toHaveTextContent('A001（管理者）'))
    cleanup()
    installFakeServer(null, ACCOUNTS)
    renderApp('/login')
    await screen.findByRole('heading', { name: 'ログイン' })
    expect(screen.getByTestId('header-user')).toHaveTextContent('未ログイン')
  })

  it('現在日時を yyyy年MM月dd日 HH:mm:ss 形式で表示し、1秒ごとに更新する', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 10, 27, 9, 5, 7))
    installFakeServer(null, ACCOUNTS)
    render(
      <MemoryRouter initialEntries={['/menu']}>
        <App />
      </MemoryRouter>,
    )
    await act(async () => {})
    const clock = screen.getByTestId('header-clock')
    expect(clock).toHaveTextContent('2026年11月27日 09:05:07')
    await act(async () => {
      vi.advanceTimersByTime(1000)
    })
    expect(clock).toHaveTextContent('2026年11月27日 09:05:08')
    await act(async () => {
      vi.advanceTimersByTime(60_000)
    })
    expect(clock).toHaveTextContent('2026年11月27日 09:06:08')
  })
})

describe('ルートとガード', () => {
  it('/ は /menu へ', async () => {
    installFakeServer(null, ACCOUNTS)
    renderApp('/')
    expect(await screen.findByRole('heading', { name: 'メニュー' })).toBeInTheDocument()
  })

  it('未ログインで業務・管理者の各画面を開くと /login へ', async () => {
    for (const s of [...BUSINESS_SCREENS, ...ADMIN_SCREENS]) {
      installFakeServer(null, ACCOUNTS)
      renderApp(s.path)
      expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
      cleanup()
    }
  })

  it('一般が管理者画面（/user-admin）を直接開くと、メッセージなしで /menu へ', async () => {
    installFakeServer(GENERAL_USER, ACCOUNTS)
    renderApp('/user-admin')
    expect(await screen.findByRole('heading', { name: 'メニュー' })).toBeInTheDocument()
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(screen.queryByText('準備中です。')).not.toBeInTheDocument()
  })

  it('一般は S07〜S10 のすべてで /menu へ、業務画面は開ける', async () => {
    for (const s of ADMIN_SCREENS) {
      installFakeServer(GENERAL_USER, ACCOUNTS)
      renderApp(s.path)
      expect(await screen.findByRole('heading', { name: 'メニュー' })).toBeInTheDocument()
      cleanup()
    }
    installFakeServer(GENERAL_USER, ACCOUNTS)
    renderApp('/stock-in')
    expect(await screen.findByRole('heading', { name: '入庫（S03）' })).toBeInTheDocument()
  })

  it('管理者は管理者画面を開ける', async () => {
    installFakeServer(ADMIN_USER, ACCOUNTS)
    renderApp('/user-admin')
    expect(await screen.findByRole('heading', { name: 'ユーザー管理（S10）' })).toBeInTheDocument()
  })

  it('再読み込み（アプリの再描画）でも GET /api/auth/me でログイン状態を復元する', async () => {
    const server = installFakeServer(ADMIN_USER, ACCOUNTS)
    renderApp('/stock-in')
    expect(await screen.findByRole('heading', { name: '入庫（S03）' })).toBeInTheDocument()
    expect(server.calls[0]).toMatchObject({ method: 'GET', path: '/api/auth/me' })
    cleanup()
    // 再読み込み相当: 新しい描画でもセッションが残っていれば未ログインに戻らない
    renderApp('/menu')
    expect(await screen.findByText('ID : A001 (管理者)')).toBeInTheDocument()
  })

  it('復元中は何も描画しない（ガード対象画面で /login に飛ばない）', async () => {
    let release: (r: Response) => void = () => {}
    const pending = new Promise<Response>((resolve) => {
      release = resolve
    })
    vi.stubGlobal('fetch', vi.fn(() => pending))
    renderApp('/stock-in')
    expect(screen.queryByRole('heading', { name: 'ログイン' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '入庫（S03）' })).not.toBeInTheDocument()
    await act(async () => {
      release(new Response(JSON.stringify(ADMIN_USER), { status: 200 }))
    })
    expect(await screen.findByRole('heading', { name: '入庫（S03）' })).toBeInTheDocument()
  })

  it('復元の通信に失敗したら未ログインとして扱う', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network')))
    renderApp('/stock-in')
    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
  })
})
