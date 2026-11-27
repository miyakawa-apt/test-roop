import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import type { Location } from '../api/locations'
import { ADMIN_USER, installFakeServer, renderApp } from '../testUtils'
import type { FakeServer } from '../testUtils'

const A: Location = { shelfNo: '01-001-01', shelfStatus: 'EMPTY', shelfStatusLabel: '空棚', remarks: '入口側' }
const B: Location = { shelfNo: '01-001-02', shelfStatus: 'STOCKED', shelfStatusLabel: '実棚', remarks: '' }
const C: Location = { shelfNo: '01-001-03', shelfStatus: 'PROHIBITED', shelfStatusLabel: '禁止棚', remarks: '点検中' }

interface Warehouse {
  server: FakeServer
  locations: Location[]
  /** 次の POST/PUT/DELETE に返す状態（指定がなければ通常処理）。 */
  forceStatus?: { status: number; body?: unknown }
  /** 次の GET 一覧を失敗させる。 */
  failList?: boolean
  /** true の間、POST/PUT/DELETE の応答を保留する（通信中の再現）。 */
  hold?: boolean
  release?: () => void
}

const LABELS: Record<string, string> = { EMPTY: '空棚', STOCKED: '実棚', PROHIBITED: '禁止棚' }

/** ロケーションAPIのふりをする（ログインなどは testUtils の fake に任せる）。 */
function setup(initial: Location[]): Warehouse {
  const server = installFakeServer(ADMIN_USER, {})
  const base = server.fetchMock.getMockImplementation() as (path: string, init?: RequestInit) => Promise<Response>
  const house: Warehouse = { server, locations: [...initial] }
  const json = (status: number, body?: unknown) =>
    new Response(body === undefined ? null : JSON.stringify(body), { status })
  server.fetchMock.mockImplementation(async (path: string, init: RequestInit = {}) => {
    if (!String(path).startsWith('/api/admin/locations')) return base(path, init)
    const method = init.method ?? 'GET'
    const body = init.body ? JSON.parse(String(init.body)) : undefined
    server.calls.push({ method, path, headers: (init.headers ?? {}) as Record<string, string>, body })
    if (method === 'GET') {
      if (house.failList) return json(500)
      return json(200, house.locations)
    }
    if (house.hold) {
      await new Promise<void>((resolve) => {
        house.release = resolve
      })
    }
    if (house.forceStatus) return json(house.forceStatus.status, house.forceStatus.body)
    const shelfNo = decodeURIComponent(path.split('/')[4] ?? '')
    if (method === 'POST') {
      const l: Location = {
        shelfNo: body.shelfNo,
        shelfStatus: body.shelfStatus,
        shelfStatusLabel: LABELS[body.shelfStatus],
        remarks: body.remarks,
      }
      house.locations.push(l)
      return json(201, l)
    }
    const index = house.locations.findIndex((l) => l.shelfNo === shelfNo)
    if (index < 0) return json(404, { error: 'NOT_FOUND' })
    if (method === 'PUT') {
      house.locations[index] = {
        shelfNo: body.shelfNo,
        shelfStatus: body.shelfStatus,
        shelfStatusLabel: LABELS[body.shelfStatus],
        remarks: body.remarks,
      }
      return json(200, house.locations[index])
    }
    house.locations.splice(index, 1)
    return json(204)
  })
  return house
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

async function open(initial: Location[]) {
  const house = setup(initial)
  renderApp('/location-admin')
  await screen.findByRole('heading', { name: 'ロケーションメンテ（S09）' })
  await waitFor(() =>
    expect(house.server.calls.some((c) => c.method === 'GET' && c.path === '/api/admin/locations')).toBe(true),
  )
  return house
}

const input = (label: string) => screen.getByLabelText(label) as HTMLInputElement
const status = () => screen.getByLabelText('棚状況') as HTMLSelectElement
const button = (name: string) => screen.getByRole('button', { name }) as HTMLButtonElement
const type = (label: string, value: string) => fireEvent.change(input(label), { target: { value } })
const mutations = (house: Warehouse) => house.server.calls.filter((c) => c.method !== 'GET')
const optionValues = () => Array.from(status().options).map((o) => o.value)

function fillAll(shelfNo = '02-002-02', shelfStatus = 'EMPTY', remarks = '新設') {
  type('棚番', shelfNo)
  fireEvent.change(status(), { target: { value: shelfStatus } })
  type('備考', remarks)
}

describe('ロケーションメンテナンス画面（S09）一覧', () => {
  it('一覧を棚番順に表示し（3状態とも）、右上に件数を出す', async () => {
    await open([A, B, C])
    expect(await screen.findByText('入口側')).toBeInTheDocument()
    const rows = screen.getAllByRole('row')
    expect(rows).toHaveLength(4) // 見出し＋3行
    expect(within(rows[1]).getByText('01-001-01')).toBeInTheDocument()
    expect(within(rows[1]).getByText('空棚')).toBeInTheDocument()
    expect(within(rows[2]).getByText('実棚')).toBeInTheDocument()
    expect(within(rows[3]).getByText('禁止棚')).toBeInTheDocument()
    expect(screen.getByText('3件')).toBeInTheDocument()
    expect(screen.queryByText('該当する棚がありません。')).not.toBeInTheDocument()
  })

  it('0件のときは「該当する棚がありません。」と0件を出す', async () => {
    await open([])
    expect(await screen.findByText('該当する棚がありません。')).toBeInTheDocument()
    expect(screen.getByText('0件')).toBeInTheDocument()
  })

  it('一覧の取得に失敗したらエラーダイアログを出す', async () => {
    const house = setup([A])
    house.failList = true
    renderApp('/location-admin')
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('棚の一覧を取得できませんでした。')
  })

  it('全入力欄に最大文字数が付いている。棚状況の選択肢は空棚と禁止棚だけ', async () => {
    await open([])
    expect(input('棚番').maxLength).toBe(9)
    expect(input('備考').maxLength).toBe(50)
    expect(optionValues()).toEqual(['', 'EMPTY', 'PROHIBITED'])
  })
})

describe('行の選択', () => {
  it('空棚の行を選ぶと入力欄に表示され、棚番も棚状況も編集できる', async () => {
    await open([A, B, C])
    fireEvent.click(await screen.findByText('入口側'))
    expect(input('棚番').value).toBe('01-001-01')
    expect(input('棚番').disabled).toBe(false)
    expect(status().value).toBe('EMPTY')
    expect(status().disabled).toBe(false)
    expect(input('備考').value).toBe('入口側')
    expect(button('登録').disabled).toBe(true)
    expect(button('更新').disabled).toBe(false)
  })

  it('実棚の行を選ぶと棚番・棚状況は変更不可（表示のみ）、備考は編集できる', async () => {
    await open([A, B, C])
    fireEvent.click(await screen.findByText('実棚'))
    expect(input('棚番').value).toBe('01-001-02')
    expect(input('棚番').disabled).toBe(true)
    expect(status().value).toBe('STOCKED')
    expect(status().disabled).toBe(true)
    expect(optionValues()).toContain('STOCKED')
    expect(input('備考').disabled).toBe(false)
  })

  it('クリアで選択と入力が消え、棚状況の選択肢も元に戻る', async () => {
    await open([A, B])
    fireEvent.click(await screen.findByText('実棚'))
    fireEvent.click(button('クリア'))
    expect(input('棚番').value).toBe('')
    expect(input('棚番').disabled).toBe(false)
    expect(status().disabled).toBe(false)
    expect(optionValues()).toEqual(['', 'EMPTY', 'PROHIBITED'])
    expect(button('更新').disabled).toBe(true)
  })
})

describe('削除ボタンの有効・無効', () => {
  it('選択行がないときは無効', async () => {
    await open([A])
    await screen.findByText('入口側')
    expect(button('削除').disabled).toBe(true)
  })

  it('選択直後は有効で、入力を変えると無効、元に戻すと有効', async () => {
    await open([A])
    fireEvent.click(await screen.findByText('入口側'))
    expect(button('削除').disabled).toBe(false)
    type('棚番', '01-001-09')
    expect(button('削除').disabled).toBe(true)
    type('棚番', '01-001-01')
    expect(button('削除').disabled).toBe(false)
    type('備考', 'x')
    expect(button('削除').disabled).toBe(true)
    type('備考', '入口側')
    fireEvent.change(status(), { target: { value: 'PROHIBITED' } })
    expect(button('削除').disabled).toBe(true)
  })
})

describe('登録', () => {
  it('確認→登録→緑の完了メッセージ→一覧を再読込→入力クリア', async () => {
    const house = await open([A])
    await screen.findByText('入口側')
    fillAll()
    fireEvent.click(button('登録'))
    expect(screen.getByRole('dialog')).toHaveTextContent('登録します。よろしいですか？')
    expect(mutations(house)).toHaveLength(0) // 確認前は送らない

    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    const notice = await screen.findByRole('status')
    expect(notice).toHaveTextContent('登録済みです。')
    expect(notice.className).toContain('green')

    const post = mutations(house)[0]
    expect(post.method).toBe('POST')
    expect(post.path).toBe('/api/admin/locations')
    expect(post.body).toEqual({ shelfNo: '02-002-02', shelfStatus: 'EMPTY', remarks: '新設' })
    expect(post.headers['X-XSRF-TOKEN']).toBe('test-token')
    expect(await screen.findByText('新設')).toBeInTheDocument()
    expect(screen.getByText('2件')).toBeInTheDocument()
    expect(input('棚番').value).toBe('')
  })

  it('備考は空でも登録できる', async () => {
    const house = await open([])
    fillAll('02-002-02', 'PROHIBITED', '')
    fireEvent.click(button('登録'))
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    await screen.findByRole('status')
    expect(mutations(house)[0].body).toEqual({ shelfNo: '02-002-02', shelfStatus: 'PROHIBITED', remarks: '' })
  })

  it('確認で「いいえ」を選ぶと送らない（入力は残る）', async () => {
    const house = await open([])
    fillAll()
    fireEvent.click(button('登録'))
    fireEvent.click(screen.getByRole('button', { name: 'いいえ' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(mutations(house)).toHaveLength(0)
    expect(input('棚番').value).toBe('02-002-02')
  })

  it('Enterキーでも検証して確認ダイアログを出す', async () => {
    await open([])
    fillAll()
    fireEvent.keyDown(input('棚番'), { key: 'Enter' })
    expect(screen.getByRole('dialog')).toHaveTextContent('登録します。よろしいですか？')
  })

  it('入力エラーはエラーダイアログにまとめて表示し、送らない', async () => {
    const house = await open([])
    type('棚番', '1-001-01')
    type('備考', 'あ'.repeat(50))
    fireEvent.click(button('登録'))
    const dialog = await screen.findByRole('alertdialog')
    const text = dialog.textContent ?? ''
    expect(text).toContain('棚番の形式を確認して入力してください。')
    expect(text).toContain('棚状況を選択してください。')
    expect(text).not.toContain('備考')
    expect(mutations(house)).toHaveLength(0)
    fireEvent.click(screen.getByRole('button', { name: '閉じる' }))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('棚番の未入力はまとめて表示される', async () => {
    await open([])
    fireEvent.click(button('登録'))
    const dialog = await screen.findByRole('alertdialog')
    expect(dialog).toHaveTextContent('棚番を入力してください。')
    expect(dialog).toHaveTextContent('棚状況を選択してください。')
  })

  it('フォーカスが外れただけでは検証しない', async () => {
    await open([])
    type('棚番', 'abc')
    fireEvent.blur(input('棚番'))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('棚番の重複（409）は文言を出し、入力は残る', async () => {
    const house = await open([A])
    await screen.findByText('入口側')
    fillAll('01-001-01')
    house.forceStatus = { status: 409, body: { error: 'DUPLICATE' } }
    fireEvent.click(button('登録'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(dialog).toHaveTextContent('その棚番は既に登録されています。別の棚番を入力してください。')
    expect(input('備考').value).toBe('新設')
  })

  it('サーバーの検証エラー（400）もまとめて表示する', async () => {
    const house = await open([])
    fillAll()
    house.forceStatus = {
      status: 400,
      body: { errors: [{ field: 'shelfNo', type: 'INVALID_FORMAT' }, { field: 'shelfStatus', type: 'INVALID_FORMAT' }] },
    }
    fireEvent.click(button('登録'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(dialog).toHaveTextContent('棚番の形式を確認して入力してください。')
    expect(dialog).toHaveTextContent('棚状況の形式を確認して入力してください。')
  })
})

describe('更新', () => {
  it('確認→PUT（変更前の棚番はパスで指定）→完了メッセージ', async () => {
    const house = await open([A, C])
    fireEvent.click(await screen.findByText('入口側'))
    type('備考', '奥側')
    fireEvent.change(status(), { target: { value: 'PROHIBITED' } })
    fireEvent.click(button('更新'))
    expect(screen.getByRole('dialog')).toHaveTextContent('更新します。よろしいですか？')
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('status')).toHaveTextContent('更新済みです。')
    const put = mutations(house)[0]
    expect(put.method).toBe('PUT')
    expect(put.path).toBe('/api/admin/locations/01-001-01')
    expect(put.body).toEqual({ shelfNo: '01-001-01', shelfStatus: 'PROHIBITED', remarks: '奥側' })
    expect(await screen.findByText('奥側')).toBeInTheDocument()
    expect(input('棚番').disabled).toBe(false) // 選択は解除される
  })

  it('棚番を変更して更新できる（パスは旧棚番、本文は新棚番）', async () => {
    const house = await open([A])
    fireEvent.click(await screen.findByText('入口側'))
    type('棚番', '05-005-05')
    fireEvent.click(button('更新'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('status')).toHaveTextContent('更新済みです。')
    const put = mutations(house)[0]
    expect(put.path).toBe('/api/admin/locations/01-001-01')
    expect((put.body as { shelfNo: string }).shelfNo).toBe('05-005-05')
    expect(await screen.findByText('05-005-05')).toBeInTheDocument()
  })

  it('実棚の行は備考だけ更新でき、棚状況（STOCKED）はそのまま送る', async () => {
    const house = await open([B])
    fireEvent.click(await screen.findByText('実棚'))
    type('備考', '上段')
    fireEvent.click(button('更新'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('status')).toHaveTextContent('更新済みです。')
    expect(mutations(house)[0].body).toEqual({ shelfNo: '01-001-02', shelfStatus: 'STOCKED', remarks: '上段' })
  })

  it('更新の入力エラーは送らずダイアログ', async () => {
    const house = await open([A])
    fireEvent.click(await screen.findByText('入口側'))
    type('棚番', 'xx')
    type('備考', 'あ'.repeat(50))
    fireEvent.change(status(), { target: { value: '' } })
    fireEvent.click(button('更新'))
    const dialog = await screen.findByRole('alertdialog')
    expect(dialog).toHaveTextContent('棚番の形式を確認して入力してください。')
    expect(dialog).toHaveTextContent('棚状況を選択してください。')
    expect(mutations(house)).toHaveLength(0)
  })

  it('他の人に先に削除されていたら「選択された棚は存在しません。」を出して一覧を再読込する', async () => {
    const house = await open([A, C])
    fireEvent.click(await screen.findByText('入口側'))
    house.locations = [C] // 先に消される
    type('備考', 'x')
    fireEvent.click(button('更新'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('選択された棚は存在しません。')
    await waitFor(() => expect(screen.getByText('1件')).toBeInTheDocument())
    expect(input('棚番').value).toBe('')
  })

  it('棚番変更で重複（409）は重複の文言', async () => {
    const house = await open([A, C])
    fireEvent.click(await screen.findByText('入口側'))
    type('棚番', '01-001-03')
    house.forceStatus = { status: 409, body: { error: 'DUPLICATE' } }
    fireEvent.click(button('更新'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('alertdialog')).toHaveTextContent(
      'その棚番は既に登録されています。別の棚番を入力してください。',
    )
  })

  it('在庫のある棚の更新（409 HAS_STOCK）は変更できない文言', async () => {
    const house = await open([A])
    fireEvent.click(await screen.findByText('入口側'))
    fireEvent.change(status(), { target: { value: 'PROHIBITED' } })
    house.forceStatus = { status: 409, body: { error: 'HAS_STOCK' } }
    fireEvent.click(button('更新'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('alertdialog')).toHaveTextContent(
      '在庫のある棚は棚番の変更・禁止棚への変更はできません。',
    )
  })
})

describe('削除', () => {
  it('確認→DELETE→完了メッセージ→一覧から消える', async () => {
    const house = await open([A, C])
    fireEvent.click(await screen.findByText('入口側'))
    fireEvent.click(button('削除'))
    expect(screen.getByRole('dialog')).toHaveTextContent('削除します。よろしいですか？')
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('status')).toHaveTextContent('削除済みです。')
    const del = mutations(house)[0]
    expect(del.method).toBe('DELETE')
    expect(del.path).toBe('/api/admin/locations/01-001-01')
    await waitFor(() => expect(screen.queryByText('入口側')).not.toBeInTheDocument())
    expect(screen.getByText('1件')).toBeInTheDocument()
  })

  it('実棚（在庫あり。409 HAS_STOCK）は削除できない文言を出す', async () => {
    const house = await open([B])
    fireEvent.click(await screen.findByText('実棚'))
    house.forceStatus = { status: 409, body: { error: 'HAS_STOCK' } }
    fireEvent.click(button('削除'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('在庫のある棚は削除できません。')
    expect(screen.getByText('01-001-02')).toBeInTheDocument()
  })

  it('他の人に先に削除されていたら「選択された棚は存在しません。」', async () => {
    const house = await open([A])
    fireEvent.click(await screen.findByText('入口側'))
    house.locations = []
    fireEvent.click(button('削除'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('選択された棚は存在しません。')
    expect(await screen.findByText('該当する棚がありません。')).toBeInTheDocument()
  })

  it('通信エラー等はエラーダイアログ', async () => {
    const house = await open([A])
    fireEvent.click(await screen.findByText('入口側'))
    house.forceStatus = { status: 500 }
    fireEvent.click(button('削除'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('処理できませんでした。')
  })
})

describe('通信中（busy）', () => {
  it('通信中の Enter キーは無視する（確認ダイアログも二重送信も出ない）', async () => {
    const house = await open([A])
    fireEvent.click(await screen.findByText('入口側'))
    type('備考', '奥側')
    house.hold = true
    fireEvent.click(button('更新'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    await waitFor(() => expect(mutations(house)).toHaveLength(1))
    expect(button('更新').disabled).toBe(true) // 通信中はボタンも無効

    fireEvent.keyDown(input('備考'), { key: 'Enter' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    house.hold = false
    house.release?.()
    expect(await screen.findByRole('status')).toHaveTextContent('更新済みです。')
    expect(mutations(house)).toHaveLength(1)
  })
})
