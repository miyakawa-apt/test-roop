import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import type { UserRow } from '../api/users'
import { ADMIN_USER, VALID_PW, installFakeServer, renderApp } from '../testUtils'
import type { FakeServer } from '../testUtils'

const ADMIN: UserRow = { userId: 'A001', userName: '管理者', role: 'ADMIN', roleLabel: '管理者' }
const TARO: UserRow = { userId: 'B002', userName: '一般太郎', role: 'GENERAL', roleLabel: '一般' }

interface Directory {
  server: FakeServer
  users: UserRow[]
  /** 次の POST/PUT/DELETE に返す状態（指定がなければ通常処理）。 */
  forceStatus?: { status: number; body?: unknown }
  /** 次の GET 一覧を失敗させる。 */
  failList?: boolean
  /** 送信を保留する（通信中の状態を作る）。解放は release を呼ぶ。 */
  hold?: Promise<void>
}

/** ユーザーAPIのふりをする（ログインなどは testUtils の fake に任せる）。 */
function setup(initial: UserRow[]): Directory {
  const server = installFakeServer(ADMIN_USER, {})
  const base = server.fetchMock.getMockImplementation() as (path: string, init?: RequestInit) => Promise<Response>
  const dir: Directory = { server, users: [...initial] }
  const json = (status: number, body?: unknown) =>
    new Response(body === undefined ? null : JSON.stringify(body), { status })
  server.fetchMock.mockImplementation(async (path: string, init: RequestInit = {}) => {
    if (!String(path).startsWith('/api/admin/users')) return base(path, init)
    const method = init.method ?? 'GET'
    const body = init.body ? JSON.parse(String(init.body)) : undefined
    server.calls.push({ method, path, headers: (init.headers ?? {}) as Record<string, string>, body })
    if (method === 'GET') {
      if (dir.failList) return json(500)
      return json(200, dir.users)
    }
    if (dir.hold) await dir.hold
    if (dir.forceStatus) return json(dir.forceStatus.status, dir.forceStatus.body)
    const id = path.split('/')[4]
    if (method === 'POST') {
      const u: UserRow = {
        userId: body.userId,
        userName: body.userName,
        role: body.role,
        roleLabel: body.role === 'ADMIN' ? '管理者' : '一般',
      }
      dir.users.push(u)
      return json(201, u)
    }
    const index = dir.users.findIndex((u) => u.userId === id)
    if (index < 0) return json(404, { error: 'NOT_FOUND' })
    if (method === 'PUT') {
      dir.users[index] = { ...dir.users[index], userName: body.userName }
      return json(200, dir.users[index])
    }
    dir.users.splice(index, 1)
    return json(204)
  })
  return dir
}

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

async function open(initial: UserRow[]) {
  const dir = setup(initial)
  renderApp('/user-admin')
  await screen.findByRole('heading', { name: 'ユーザー管理（S10）' })
  await waitFor(() => expect(dir.server.calls.some((c) => c.method === 'GET' && c.path === '/api/admin/users')).toBe(true))
  return dir
}

const input = (label: string) => screen.getByLabelText(label) as HTMLInputElement
const roleSelect = () => screen.getByLabelText('権限') as HTMLSelectElement
const button = (name: string) => screen.getByRole('button', { name }) as HTMLButtonElement
const type = (label: string, value: string) => fireEvent.change(input(label), { target: { value } })
const mutations = (dir: Directory) => dir.server.calls.filter((c) => c.method !== 'GET')

function fillAll(userId = 'C003', name = '佐藤花子') {
  type('ユーザーID', userId)
  type('氏名', name)
  type('パスワード', VALID_PW)
  fireEvent.change(roleSelect(), { target: { value: 'ADMIN' } })
}

describe('ユーザー管理画面（S10）一覧', () => {
  it('一覧を表示し、右上に件数を出す。パスワード列はない', async () => {
    await open([ADMIN, TARO])
    expect(await screen.findByText('一般太郎')).toBeInTheDocument()
    const rows = screen.getAllByRole('row')
    expect(rows).toHaveLength(3) // 見出し＋2行
    expect(within(rows[1]).getByText('A001')).toBeInTheDocument()
    expect(within(rows[1]).getAllByText('管理者')).toHaveLength(2) // 氏名と権限
    expect(within(rows[2]).getByText('一般')).toBeInTheDocument()
    expect(screen.getByText('2件')).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: 'パスワード' })).not.toBeInTheDocument()
    expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['ユーザーID', '氏名', '権限'])
  })

  it('0件のときは「該当するユーザーがありません。」と0件を出す', async () => {
    await open([])
    expect(await screen.findByText('該当するユーザーがありません。')).toBeInTheDocument()
    expect(screen.getByText('0件')).toBeInTheDocument()
  })

  it('一覧の取得に失敗したらエラーダイアログを出す', async () => {
    const dir = setup([ADMIN])
    dir.failList = true
    renderApp('/user-admin')
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('ユーザーの一覧を取得できませんでした。')
  })

  it('入力欄の最大文字数・パスワードは伏せ字・権限は一般／管理者', async () => {
    await open([])
    expect(input('ユーザーID').maxLength).toBe(4)
    expect(input('氏名').maxLength).toBe(15)
    expect(input('パスワード').maxLength).toBe(14)
    expect(input('パスワード').type).toBe('password')
    expect(screen.getByText(/空のままなら変更しません/)).toBeInTheDocument()
    expect(within(roleSelect()).getAllByRole('option').map((o) => o.textContent)).toEqual(['一般', '管理者'])
    expect(roleSelect().value).toBe('GENERAL')
  })
})

describe('行の選択', () => {
  it('行を選ぶと入力欄に表示され、ユーザーIDは編集不可・パスワード欄は空', async () => {
    await open([ADMIN, TARO])
    type('パスワード', VALID_PW) // 選択前に入力していても、選択でクリアされる
    fireEvent.click(await screen.findByText('一般太郎'))
    expect(input('ユーザーID').value).toBe('B002')
    expect(input('ユーザーID').disabled).toBe(true)
    expect(input('氏名').value).toBe('一般太郎')
    expect(roleSelect().value).toBe('GENERAL')
    expect(input('パスワード').value).toBe('')
    expect(button('登録').disabled).toBe(true)
    expect(button('更新').disabled).toBe(false)
  })

  it('クリアで選択と入力が消え、ユーザーIDがまた編集できる', async () => {
    await open([ADMIN])
    fireEvent.click(await screen.findByText('管理者', { selector: 'td:nth-child(2)' }))
    type('パスワード', VALID_PW)
    fireEvent.click(button('クリア'))
    expect(input('ユーザーID').value).toBe('')
    expect(input('ユーザーID').disabled).toBe(false)
    expect(input('パスワード').value).toBe('')
    expect(button('更新').disabled).toBe(true)
  })
})

describe('削除ボタンの有効・無効', () => {
  it('選択行がないときは無効', async () => {
    await open([TARO])
    await screen.findByText('一般太郎')
    expect(button('削除').disabled).toBe(true)
  })

  it('選択直後は有効で、氏名・権限・パスワードを変えると無効、元に戻すと有効', async () => {
    await open([TARO])
    fireEvent.click(await screen.findByText('一般太郎'))
    expect(button('削除').disabled).toBe(false)
    type('氏名', '一般太郎X')
    expect(button('削除').disabled).toBe(true)
    type('氏名', '一般太郎')
    expect(button('削除').disabled).toBe(false)
    fireEvent.change(roleSelect(), { target: { value: 'ADMIN' } })
    expect(button('削除').disabled).toBe(true)
    fireEvent.change(roleSelect(), { target: { value: 'GENERAL' } })
    expect(button('削除').disabled).toBe(false)
    // パスワード欄に何か入っていれば（一致しないので）無効
    type('パスワード', VALID_PW)
    expect(button('削除').disabled).toBe(true)
    type('パスワード', '')
    expect(button('削除').disabled).toBe(false)
  })
})

describe('登録', () => {
  it('確認→登録→緑の完了メッセージ→一覧を再読込→入力クリア（パスワードも空）', async () => {
    const dir = await open([ADMIN])
    await screen.findByText('管理者', { selector: 'td:nth-child(2)' })
    fillAll()
    fireEvent.click(button('登録'))
    expect(screen.getByRole('dialog')).toHaveTextContent('登録します。よろしいですか？')
    expect(mutations(dir)).toHaveLength(0) // 確認前は送らない

    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    const status = await screen.findByRole('status')
    expect(status).toHaveTextContent('登録済みです。')
    expect(status.className).toContain('green')

    const post = mutations(dir)[0]
    expect(post.method).toBe('POST')
    expect(post.path).toBe('/api/admin/users')
    expect(post.body).toEqual({ userId: 'C003', userName: '佐藤花子', 'password': VALID_PW, role: 'ADMIN' })
    expect(post.headers['X-XSRF-TOKEN']).toBe('test-token')
    expect(await screen.findByText('佐藤花子')).toBeInTheDocument()
    expect(screen.getByText('2件')).toBeInTheDocument()
    expect(input('ユーザーID').value).toBe('')
    expect(input('パスワード').value).toBe('')
    expect(roleSelect().value).toBe('GENERAL')
    // 画面（DOM）のどこにも平文のパスワードが残らない
    expect(document.body.innerHTML).not.toContain(VALID_PW)
  })

  it('確認で「いいえ」を選ぶと送らない（入力は残る）', async () => {
    const dir = await open([])
    fillAll()
    fireEvent.click(button('登録'))
    fireEvent.click(screen.getByRole('button', { name: 'いいえ' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(mutations(dir)).toHaveLength(0)
    expect(input('氏名').value).toBe('佐藤花子')
  })

  it('Enterキーでも検証して確認ダイアログを出す', async () => {
    await open([])
    fillAll()
    fireEvent.keyDown(input('氏名'), { key: 'Enter' })
    expect(screen.getByRole('dialog')).toHaveTextContent('登録します。よろしいですか？')
  })

  it('入力エラーはエラーダイアログにまとめて表示し、送らない（登録ではパスワード必須）', async () => {
    const dir = await open([])
    type('ユーザーID', 'ab')
    type('氏名', '')
    type('パスワード', 'short')
    fireEvent.click(button('登録'))
    const dialog = await screen.findByRole('alertdialog')
    const text = dialog.textContent ?? ''
    expect(text).toContain('ユーザーIDの形式を確認して入力してください。')
    expect(text).toContain('氏名を入力してください。')
    expect(text).toContain('パスワードの文字数を確認して入力してください。')
    expect(mutations(dir)).toHaveLength(0)
    fireEvent.click(screen.getByRole('button', { name: '閉じる' }))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('登録でパスワードが空なら「パスワードを入力してください。」', async () => {
    await open([])
    type('ユーザーID', 'C003')
    type('氏名', '佐藤花子')
    fireEvent.click(button('登録'))
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('パスワードを入力してください。')
  })

  it('フォーカスが外れただけでは検証しない', async () => {
    await open([])
    type('ユーザーID', 'a')
    fireEvent.blur(input('ユーザーID'))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('ユーザーIDの重複（409）は文言を出し、入力は残るがパスワードは消える', async () => {
    const dir = await open([ADMIN])
    await screen.findByText('管理者', { selector: 'td:nth-child(2)' })
    fillAll('A001')
    dir.forceStatus = { status: 409, body: { error: 'DUPLICATE' } }
    fireEvent.click(button('登録'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(dialog).toHaveTextContent('そのユーザーIDは既に登録されています。別のユーザーIDを入力してください。')
    expect(input('氏名').value).toBe('佐藤花子')
    expect(input('パスワード').value).toBe('')
  })

  it('サーバーの検証エラー（400）もまとめて表示する', async () => {
    const dir = await open([])
    fillAll()
    dir.forceStatus = {
      status: 400,
      body: { errors: [{ field: 'userName', type: 'INVALID_LENGTH' }, { field: 'role', type: 'INVALID_FORMAT' }] },
    }
    fireEvent.click(button('登録'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(dialog).toHaveTextContent('氏名の文字数を確認して入力してください。')
    expect(dialog).toHaveTextContent('権限の形式を確認して入力してください。')
    expect(input('パスワード').value).toBe('')
  })
})

describe('Enterキーの扱い', () => {
  it('通信中（busy）の Enter は無視する（二重送信しない）', async () => {
    const dir = await open([])
    let release: () => void = () => {}
    dir.hold = new Promise<void>((resolve) => {
      release = resolve
    })
    fillAll()
    fireEvent.click(button('登録'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    await waitFor(() => expect(mutations(dir)).toHaveLength(1))
    expect(button('登録').disabled).toBe(true) // 通信中

    // 通信中に Enter を押しても確認ダイアログは出ず、送信も増えない
    type('ユーザーID', 'D004')
    type('氏名', '次の人')
    type('パスワード', VALID_PW)
    fireEvent.keyDown(input('氏名'), { key: 'Enter' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(mutations(dir)).toHaveLength(1)

    release()
    expect(await screen.findByRole('status')).toHaveTextContent('登録済みです。')
    expect(mutations(dir)).toHaveLength(1)
  })
})

describe('更新', () => {
  it('パスワード欄が空なら空のまま送る（変更しない）。確認→PUT（ユーザーIDはパスで指定）→完了', async () => {
    const dir = await open([ADMIN, TARO])
    fireEvent.click(await screen.findByText('一般太郎'))
    type('氏名', '一般次郎')
    fireEvent.click(button('更新'))
    expect(screen.getByRole('dialog')).toHaveTextContent('更新します。よろしいですか？')
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('status')).toHaveTextContent('更新済みです。')
    const put = mutations(dir)[0]
    expect(put.method).toBe('PUT')
    expect(put.path).toBe('/api/admin/users/B002')
    expect(put.body).toEqual({ userId: 'B002', userName: '一般次郎', 'password': '', role: 'GENERAL' })
    expect(await screen.findByText('一般次郎')).toBeInTheDocument()
    expect(input('ユーザーID').disabled).toBe(false) // 選択は解除される
  })

  it('パスワードを入れて更新すると送られ、完了後は欄が空になる', async () => {
    const dir = await open([TARO])
    fireEvent.click(await screen.findByText('一般太郎'))
    type('パスワード', VALID_PW)
    fireEvent.click(button('更新'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('status')).toHaveTextContent('更新済みです。')
    expect((mutations(dir)[0].body as Record<string, string>)['password']).toBe(VALID_PW)
    expect(input('パスワード').value).toBe('')
    expect(document.body.innerHTML).not.toContain(VALID_PW)
  })

  it('更新でパスワードが入っていて短い場合はエラー（空なら検証しない）', async () => {
    const dir = await open([TARO])
    fireEvent.click(await screen.findByText('一般太郎'))
    type('パスワード', 'short')
    fireEvent.click(button('更新'))
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('パスワードの文字数を確認して入力してください。')
    expect(mutations(dir)).toHaveLength(0)
  })

  it('更新の入力エラーは送らずダイアログ（ユーザーIDは検証対象外）', async () => {
    const dir = await open([TARO])
    fireEvent.click(await screen.findByText('一般太郎'))
    type('氏名', '')
    fireEvent.click(button('更新'))
    const dialog = await screen.findByRole('alertdialog')
    expect(dialog).toHaveTextContent('氏名を入力してください。')
    expect(dialog).not.toHaveTextContent('ユーザーID')
    expect(dialog).not.toHaveTextContent('パスワード')
    expect(mutations(dir)).toHaveLength(0)
  })

  it('他の人に先に削除されていたら「選択されたユーザーは存在しません。」を出して一覧を再読込する', async () => {
    const dir = await open([ADMIN, TARO])
    fireEvent.click(await screen.findByText('一般太郎'))
    dir.users = [ADMIN] // 先に消される
    type('氏名', '一般次郎')
    fireEvent.click(button('更新'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('選択されたユーザーは存在しません。')
    await waitFor(() => expect(screen.getByText('1件')).toBeInTheDocument())
    expect(screen.queryByText('一般太郎')).not.toBeInTheDocument()
    expect(input('氏名').value).toBe('')
  })

  it('最後の管理者の降格（409 LAST_ADMIN）は文言を出す', async () => {
    const dir = await open([ADMIN])
    fireEvent.click(await screen.findByText('管理者', { selector: 'td:nth-child(2)' }))
    fireEvent.change(roleSelect(), { target: { value: 'GENERAL' } })
    dir.forceStatus = { status: 409, body: { error: 'LAST_ADMIN' } }
    fireEvent.click(button('更新'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('alertdialog')).toHaveTextContent(
      '最後の管理者は削除できません。また、一般に変更することもできません。',
    )
  })
})

describe('削除', () => {
  it('確認→DELETE→完了メッセージ→一覧から消える', async () => {
    const dir = await open([ADMIN, TARO])
    fireEvent.click(await screen.findByText('一般太郎'))
    fireEvent.click(button('削除'))
    expect(screen.getByRole('dialog')).toHaveTextContent('削除します。よろしいですか？')
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('status')).toHaveTextContent('削除済みです。')
    const del = mutations(dir)[0]
    expect(del.method).toBe('DELETE')
    expect(del.path).toBe('/api/admin/users/B002')
    await waitFor(() => expect(screen.queryByText('一般太郎')).not.toBeInTheDocument())
    expect(screen.getByText('1件')).toBeInTheDocument()
  })

  it.each([
    ['SELF', 'ログイン中のユーザーは削除できません。'],
    ['LAST_ADMIN', '最後の管理者は削除できません。また、一般に変更することもできません。'],
    ['HAS_RESULTS', '実績のあるユーザーは削除できません。'],
  ])('409 %s は専用の文言を出し、一覧は残る', async (error, message) => {
    const dir = await open([TARO])
    fireEvent.click(await screen.findByText('一般太郎'))
    dir.forceStatus = { status: 409, body: { error } }
    fireEvent.click(button('削除'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('alertdialog')).toHaveTextContent(message)
    expect(screen.getByText('一般太郎')).toBeInTheDocument()
  })

  it('他の人に先に削除されていたら「選択されたユーザーは存在しません。」', async () => {
    const dir = await open([TARO])
    fireEvent.click(await screen.findByText('一般太郎'))
    dir.users = []
    fireEvent.click(button('削除'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('選択されたユーザーは存在しません。')
    expect(await screen.findByText('該当するユーザーがありません。')).toBeInTheDocument()
  })

  it('通信エラー等はエラーダイアログ', async () => {
    const dir = await open([TARO])
    fireEvent.click(await screen.findByText('一般太郎'))
    dir.forceStatus = { status: 500 }
    fireEvent.click(button('削除'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('処理できませんでした。')
  })
})
