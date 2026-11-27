import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import type { Product } from '../api/products'
import { ADMIN_USER, installFakeServer, renderApp } from '../testUtils'
import type { FakeServer } from '../testUtils'

const MIKAN: Product = {
  productCode: '00000001',
  productName: 'みかん',
  category: 'FOOD',
  categoryLabel: '食品',
  unitPrice: 1200,
  caseQuantity: 24,
}
const NEJI: Product = {
  productCode: '00000002',
  productName: 'ねじ',
  category: 'PARTS',
  categoryLabel: '部材',
  unitPrice: 5,
  caseQuantity: 100,
}

interface Shop {
  server: FakeServer
  products: Product[]
  /** 次の POST/PUT/DELETE に返す状態（指定がなければ通常処理）。 */
  forceStatus?: { status: number; body?: unknown }
  /** 次の GET 一覧を失敗させる。 */
  failList?: boolean
}

/** 商品APIのふりをする（ログインなどは testUtils の fake に任せる）。 */
function setup(initial: Product[]): Shop {
  const server = installFakeServer(ADMIN_USER, {})
  const base = server.fetchMock.getMockImplementation() as (path: string, init?: RequestInit) => Promise<Response>
  const shop: Shop = { server, products: [...initial] }
  const json = (status: number, body?: unknown) =>
    new Response(body === undefined ? null : JSON.stringify(body), { status })
  server.fetchMock.mockImplementation(async (path: string, init: RequestInit = {}) => {
    if (!String(path).startsWith('/api/admin/products')) return base(path, init)
    const method = init.method ?? 'GET'
    const body = init.body ? JSON.parse(String(init.body)) : undefined
    server.calls.push({ method, path, headers: (init.headers ?? {}) as Record<string, string>, body })
    if (method === 'GET') {
      if (shop.failList) return json(500)
      return json(200, shop.products)
    }
    if (shop.forceStatus) return json(shop.forceStatus.status, shop.forceStatus.body)
    const code = path.split('/')[4]
    if (method === 'POST') {
      const p: Product = {
        productCode: body.productCode,
        productName: body.productName,
        category: body.category,
        categoryLabel: body.category === 'FOOD' ? '食品' : '部材',
        unitPrice: Number(body.unitPrice),
        caseQuantity: Number(body.caseQuantity),
      }
      shop.products.push(p)
      return json(201, p)
    }
    const index = shop.products.findIndex((p) => p.productCode === code)
    if (index < 0) return json(404, { error: 'NOT_FOUND' })
    if (method === 'PUT') {
      shop.products[index] = { ...shop.products[index], productName: body.productName }
      return json(200, shop.products[index])
    }
    shop.products.splice(index, 1)
    return json(204)
  })
  return shop
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

async function open(initial: Product[]) {
  const shop = setup(initial)
  renderApp('/product-admin')
  await screen.findByRole('heading', { name: '商品メンテナンス（S08）' })
  await waitFor(() => expect(shop.server.calls.some((c) => c.method === 'GET' && c.path === '/api/admin/products')).toBe(true))
  return shop
}

const input = (label: string) => screen.getByLabelText(label) as HTMLInputElement
const button = (name: string) => screen.getByRole('button', { name }) as HTMLButtonElement
const type = (label: string, value: string) => fireEvent.change(input(label), { target: { value } })
const mutations = (shop: Shop) => shop.server.calls.filter((c) => c.method !== 'GET')

function fillAll(code = '00000003', name = 'たわし') {
  type('商品コード', code)
  type('商品名', name)
  fireEvent.change(screen.getByLabelText('カテゴリ'), { target: { value: 'FOOD' } })
  type('単価', '300')
  type('ケース入数', '10')
}

describe('商品メンテナンス画面（S08）一覧', () => {
  it('一覧を商品コード順に表示し、右上に件数を出す', async () => {
    await open([MIKAN, NEJI])
    expect(await screen.findByText('みかん')).toBeInTheDocument()
    const rows = screen.getAllByRole('row')
    expect(rows).toHaveLength(3) // 見出し＋2行
    expect(within(rows[1]).getByText('00000001')).toBeInTheDocument()
    expect(within(rows[1]).getByText('食品')).toBeInTheDocument()
    expect(within(rows[1]).getByText('1,200')).toBeInTheDocument()
    expect(within(rows[2]).getByText('部材')).toBeInTheDocument()
    expect(screen.getByText('2件')).toBeInTheDocument()
    expect(screen.queryByText('該当する商品がありません。')).not.toBeInTheDocument()
  })

  it('0件のときは「該当する商品がありません。」と0件を出す', async () => {
    await open([])
    expect(await screen.findByText('該当する商品がありません。')).toBeInTheDocument()
    expect(screen.getByText('0件')).toBeInTheDocument()
  })

  it('一覧の取得に失敗したらエラーダイアログを出す', async () => {
    const shop = setup([MIKAN])
    shop.failList = true
    renderApp('/product-admin')
    const dialog = await screen.findByRole('alertdialog')
    expect(dialog).toHaveTextContent('商品の一覧を取得できませんでした。')
  })

  it('全入力欄に最大文字数が付いている', async () => {
    await open([])
    expect(input('商品コード').maxLength).toBe(8)
    expect(input('商品名').maxLength).toBe(30)
    expect(input('単価').maxLength).toBe(7)
    expect(input('ケース入数').maxLength).toBe(4)
  })
})

describe('行の選択', () => {
  it('行を選ぶと入力欄に表示され、商品コードは編集不可になる', async () => {
    await open([MIKAN, NEJI])
    fireEvent.click(await screen.findByText('ねじ'))
    expect(input('商品コード').value).toBe('00000002')
    expect(input('商品コード').disabled).toBe(true)
    expect(input('商品名').value).toBe('ねじ')
    expect((screen.getByLabelText('カテゴリ') as HTMLSelectElement).value).toBe('PARTS')
    expect(input('単価').value).toBe('5')
    expect(input('ケース入数').value).toBe('100')
    expect(button('登録').disabled).toBe(true)
    expect(button('更新').disabled).toBe(false)
  })

  it('クリアで選択と入力が消え、商品コードがまた編集できる', async () => {
    await open([MIKAN])
    fireEvent.click(await screen.findByText('みかん'))
    fireEvent.click(button('クリア'))
    expect(input('商品コード').value).toBe('')
    expect(input('商品コード').disabled).toBe(false)
    expect(button('更新').disabled).toBe(true)
  })
})

describe('削除ボタンの有効・無効', () => {
  it('選択行がないときは無効', async () => {
    await open([MIKAN])
    await screen.findByText('みかん')
    expect(button('削除').disabled).toBe(true)
  })

  it('選択直後は有効で、入力を変えると無効、元に戻すと有効', async () => {
    await open([MIKAN])
    fireEvent.click(await screen.findByText('みかん'))
    expect(button('削除').disabled).toBe(false)
    type('商品名', 'みかんX')
    expect(button('削除').disabled).toBe(true)
    type('商品名', 'みかん')
    expect(button('削除').disabled).toBe(false)
    type('単価', '1201')
    expect(button('削除').disabled).toBe(true)
    type('単価', '1200')
    fireEvent.change(screen.getByLabelText('カテゴリ'), { target: { value: 'TOOL' } })
    expect(button('削除').disabled).toBe(true)
  })
})

describe('登録', () => {
  it('確認→登録→緑の完了メッセージ→一覧を再読込→入力クリア', async () => {
    const shop = await open([MIKAN])
    await screen.findByText('みかん')
    fillAll()
    fireEvent.click(button('登録'))
    expect(screen.getByRole('dialog')).toHaveTextContent('登録します。よろしいですか？')
    expect(mutations(shop)).toHaveLength(0) // 確認前は送らない

    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    const status = await screen.findByRole('status')
    expect(status).toHaveTextContent('登録済みです。')
    expect(status.className).toContain('green')

    const post = mutations(shop)[0]
    expect(post.method).toBe('POST')
    expect(post.path).toBe('/api/admin/products')
    expect(post.body).toEqual({
      productCode: '00000003',
      productName: 'たわし',
      category: 'FOOD',
      unitPrice: '300',
      caseQuantity: '10',
    })
    expect(post.headers['X-XSRF-TOKEN']).toBe('test-token')
    expect(await screen.findByText('たわし')).toBeInTheDocument()
    expect(screen.getByText('2件')).toBeInTheDocument()
    expect(input('商品コード').value).toBe('')
    expect(input('商品名').value).toBe('')
  })

  it('確認で「いいえ」を選ぶと送らない（入力は残る）', async () => {
    const shop = await open([])
    fillAll()
    fireEvent.click(button('登録'))
    fireEvent.click(screen.getByRole('button', { name: 'いいえ' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(mutations(shop)).toHaveLength(0)
    expect(input('商品名').value).toBe('たわし')
  })

  it('Enterキーでも検証して確認ダイアログを出す', async () => {
    await open([])
    fillAll()
    fireEvent.keyDown(input('商品名'), { key: 'Enter' })
    expect(screen.getByRole('dialog')).toHaveTextContent('登録します。よろしいですか？')
  })

  it('入力エラーはエラーダイアログにまとめて表示し、送らない', async () => {
    const shop = await open([])
    type('商品コード', '12ab')
    type('単価', '10000000')
    type('ケース入数', '0')
    fireEvent.click(button('登録'))
    const dialog = await screen.findByRole('alertdialog')
    const text = dialog.textContent ?? ''
    expect(text).toContain('商品コードの形式を確認して入力してください。')
    expect(text).toContain('商品名を入力してください。')
    expect(text).toContain('カテゴリを選択してください。')
    expect(text).toContain('単価の範囲を確認して入力してください。')
    expect(text).toContain('ケース入数の範囲を確認して入力してください。')
    expect(mutations(shop)).toHaveLength(0)
    fireEvent.click(screen.getByRole('button', { name: '閉じる' }))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('フォーカスが外れただけでは検証しない', async () => {
    await open([])
    type('商品コード', 'abc')
    fireEvent.blur(input('商品コード'))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('商品コードの重複（409）は文言を出し、入力は残る', async () => {
    const shop = await open([MIKAN])
    await screen.findByText('みかん')
    fillAll('00000001')
    shop.forceStatus = { status: 409, body: { error: 'DUPLICATE' } }
    fireEvent.click(button('登録'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(dialog).toHaveTextContent('その商品コードは既に登録されています。別の商品コードを入力してください。')
    expect(input('商品名').value).toBe('たわし')
  })

  it('サーバーの検証エラー（400）もまとめて表示する', async () => {
    const shop = await open([])
    fillAll()
    shop.forceStatus = {
      status: 400,
      body: { errors: [{ field: 'productName', type: 'INVALID_LENGTH' }, { field: 'category', type: 'INVALID_FORMAT' }] },
    }
    fireEvent.click(button('登録'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(dialog).toHaveTextContent('商品名の文字数を確認して入力してください。')
    expect(dialog).toHaveTextContent('カテゴリの形式を確認して入力してください。')
  })
})

describe('更新', () => {
  it('確認→PUT（商品コードはパスで指定）→完了メッセージ', async () => {
    const shop = await open([MIKAN, NEJI])
    fireEvent.click(await screen.findByText('みかん'))
    type('商品名', 'ぶどう')
    fireEvent.click(button('更新'))
    expect(screen.getByRole('dialog')).toHaveTextContent('更新します。よろしいですか？')
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('status')).toHaveTextContent('更新済みです。')
    const put = mutations(shop)[0]
    expect(put.method).toBe('PUT')
    expect(put.path).toBe('/api/admin/products/00000001')
    expect((put.body as { productName: string }).productName).toBe('ぶどう')
    expect(await screen.findByText('ぶどう')).toBeInTheDocument()
    expect(input('商品コード').disabled).toBe(false) // 選択は解除される
  })

  it('更新の入力エラーは送らずダイアログ（商品コードは検証対象外）', async () => {
    const shop = await open([MIKAN])
    fireEvent.click(await screen.findByText('みかん'))
    type('商品名', '')
    fireEvent.click(button('更新'))
    const dialog = await screen.findByRole('alertdialog')
    expect(dialog).toHaveTextContent('商品名を入力してください。')
    expect(dialog).not.toHaveTextContent('商品コード')
    expect(mutations(shop)).toHaveLength(0)
  })

  it('他の人に先に削除されていたら「選択された商品は存在しません。」を出して一覧を再読込する', async () => {
    const shop = await open([MIKAN, NEJI])
    fireEvent.click(await screen.findByText('みかん'))
    shop.products = [NEJI] // 先に消される
    type('商品名', 'ぶどう')
    fireEvent.click(button('更新'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(dialog).toHaveTextContent('選択された商品は存在しません。')
    await waitFor(() => expect(screen.getByText('1件')).toBeInTheDocument())
    expect(screen.queryByText('みかん')).not.toBeInTheDocument()
    expect(input('商品名').value).toBe('')
  })
})

describe('削除', () => {
  it('確認→DELETE→完了メッセージ→一覧から消える', async () => {
    const shop = await open([MIKAN, NEJI])
    fireEvent.click(await screen.findByText('みかん'))
    fireEvent.click(button('削除'))
    expect(screen.getByRole('dialog')).toHaveTextContent('削除します。よろしいですか？')
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('status')).toHaveTextContent('削除済みです。')
    const del = mutations(shop)[0]
    expect(del.method).toBe('DELETE')
    expect(del.path).toBe('/api/admin/products/00000001')
    await waitFor(() => expect(screen.queryByText('みかん')).not.toBeInTheDocument())
    expect(screen.getByText('1件')).toBeInTheDocument()
  })

  it('在庫のある商品（409）は削除できない文言を出す', async () => {
    const shop = await open([MIKAN])
    fireEvent.click(await screen.findByText('みかん'))
    shop.forceStatus = { status: 409, body: { error: 'HAS_STOCK' } }
    fireEvent.click(button('削除'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(dialog).toHaveTextContent('在庫のある商品は削除できません。')
    expect(screen.getByText('みかん')).toBeInTheDocument()
  })

  it('他の人に先に削除されていたら「選択された商品は存在しません。」', async () => {
    const shop = await open([MIKAN])
    fireEvent.click(await screen.findByText('みかん'))
    shop.products = []
    fireEvent.click(button('削除'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('選択された商品は存在しません。')
    expect(await screen.findByText('該当する商品がありません。')).toBeInTheDocument()
  })

  it('通信エラー等はエラーダイアログ', async () => {
    const shop = await open([MIKAN])
    fireEvent.click(await screen.findByText('みかん'))
    shop.forceStatus = { status: 500 }
    fireEvent.click(button('削除'))
    fireEvent.click(screen.getByRole('button', { name: 'はい' }))
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('処理できませんでした。')
  })
})
