import { useCallback, useEffect, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { deleteProduct, fetchProducts, postProduct, putProduct } from '../api/products'
import type { Product, ProductInput } from '../api/products'
import { CATEGORY_OPTIONS } from '../codes'
import ConfirmDialog from '../components/ConfirmDialog'
import ErrorDialog from '../components/ErrorDialog'
import MasterLayout from '../components/MasterLayout'
import NotFound from '../components/NotFound'
import { NoticeProvider, useNotice } from '../components/Notice'
import TextInput from '../components/TextInput'
import { useSubmitValidation } from '../components/useSubmitValidation'
import { confirmMessages, errorMessages, productMessages, resultMessages, validationMessage } from '../messages'
import { checkRule } from '../validation'

type Action = 'register' | 'update' | 'delete'

const EMPTY_FORM: ProductInput = { productCode: '', productName: '', category: '', unitPrice: '', caseQuantity: '' }

const toForm = (p: Product): ProductInput => ({
  productCode: p.productCode,
  productName: p.productName,
  category: p.category,
  unitPrice: String(p.unitPrice),
  caseQuantity: String(p.caseQuantity),
})

const sameAs = (a: ProductInput, b: ProductInput) =>
  a.productCode === b.productCode &&
  a.productName === b.productName &&
  a.category === b.category &&
  a.unitPrice === b.unitPrice &&
  a.caseQuantity === b.caseQuantity

const CONFIRM: Record<Action, string> = {
  register: confirmMessages.register,
  update: confirmMessages.update,
  delete: confirmMessages.delete,
}
const DONE: Record<Action, string> = {
  register: resultMessages.registered,
  update: resultMessages.updated,
  delete: resultMessages.deleted,
}

/** S08 商品メンテナンス（管理者のみ）。 */
export default function ProductAdmin() {
  return (
    <NoticeProvider>
      <ProductAdminBody />
    </NoticeProvider>
  )
}

function ProductAdminBody() {
  const notice = useNotice()
  const [products, setProducts] = useState<Product[]>([])
  const [loaded, setLoaded] = useState(false)
  const [selected, setSelected] = useState<Product | null>(null)
  const [form, setForm] = useState<ProductInput>(EMPTY_FORM)
  const [pending, setPending] = useState<Action | null>(null)
  const [serverErrors, setServerErrors] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  const action = useRef<Action>('register')

  const reload = useCallback(async () => {
    try {
      const res = await fetchProducts()
      if (res.ok && Array.isArray(res.body)) {
        setProducts(res.body)
        setLoaded(true)
        return true
      }
    } catch {
      // 通信失敗は下で通知する
    }
    setServerErrors([productMessages.loadFailed, errorMessages.retry])
    return false
  }, [])

  useEffect(() => {
    // 画面を開いたときに一覧を読み込む
    void reload()
  }, [reload])

  const set = (field: keyof ProductInput) => (value: string) => setForm((f) => ({ ...f, [field]: value }))

  const clearForm = () => {
    setSelected(null)
    setForm(EMPTY_FORM)
  }

  const select = (p: Product) => {
    setSelected(p)
    setForm(toForm(p))
  }

  const canDelete = selected !== null && sameAs(form, toForm(selected))

  const validate = (): string[] => {
    if (action.current === 'delete') return []
    const messages: string[] = []
    const add = (field: string, type: string | undefined) => {
      if (type) messages.push(validationMessage(field, type))
    }
    if (!selected) add('productCode', checkRule('productCode', form.productCode))
    add('productName', checkRule('productName', form.productName))
    if (form.category === '') add('category', 'REQUIRED')
    add('unitPrice', checkRule('unitPrice', form.unitPrice))
    add('caseQuantity', checkRule('caseQuantity', form.caseQuantity))
    return messages
  }

  const { errors, submit, onEnterKey, clearErrors } = useSubmitValidation(validate, () => setPending(action.current))

  const request = (a: Action) => {
    action.current = a
    submit()
  }

  /** 入力欄の Enter: 選択中なら更新、そうでなければ登録。 */
  const onKeyDown = (e: KeyboardEvent<HTMLElement>) => {
    if (e.key === 'Enter') action.current = selected ? 'update' : 'register'
    onEnterKey(e)
  }

  const execute = async (a: Action) => {
    setPending(null)
    setBusy(true)
    try {
      const target = selected?.productCode ?? form.productCode
      const res =
        a === 'register'
          ? await postProduct(form)
          : a === 'update'
            ? await putProduct({ ...form, productCode: target })
            : await deleteProduct(target)
      if (res.ok) {
        notice.show(DONE[a])
        clearForm()
        await reload()
      } else if (res.status === 400) {
        const list = (res.body as { errors?: { field: string; type: string }[] } | undefined)?.errors ?? []
        setServerErrors(
          list.length > 0 ? list.map((e) => validationMessage(e.field, e.type)) : [productMessages.failed, errorMessages.retry],
        )
      } else if (res.status === 404) {
        setServerErrors([productMessages.notFound])
        clearForm()
        await reload()
      } else if (res.status === 409) {
        const code = (res.body as { error?: string } | undefined)?.error
        setServerErrors([code === 'HAS_STOCK' ? productMessages.hasStock : productMessages.duplicate])
      } else {
        setServerErrors([productMessages.failed, errorMessages.retry])
      }
    } catch {
      setServerErrors([productMessages.failed, errorMessages.retry])
    } finally {
      setBusy(false)
    }
  }

  const shown = errors.length > 0 ? errors : serverErrors
  const closeError = () => {
    clearErrors()
    setServerErrors([])
  }

  const buttonClass = 'rounded px-4 py-1 text-white disabled:opacity-40 '
  return (
    <>
      <MasterLayout
        title="商品メンテナンス（S08）"
        count={products.length}
        form={
          <>
            <TextInput
              label="商品コード"
              maxLength={8}
              inputMode="numeric"
              value={form.productCode}
              disabled={selected !== null}
              onChange={(e) => set('productCode')(e.target.value)}
              onKeyDown={onKeyDown}
            />
            <TextInput
              label="商品名"
              maxLength={30}
              value={form.productName}
              onChange={(e) => set('productName')(e.target.value)}
              onKeyDown={onKeyDown}
            />
            <label className="flex flex-col gap-1 text-sm text-slate-700">
              カテゴリ
              <select
                value={form.category}
                onChange={(e) => set('category')(e.target.value)}
                onKeyDown={onKeyDown}
                className="rounded border border-slate-300 px-2 py-1"
              >
                <option value="">（未選択）</option>
                {CATEGORY_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </label>
            <TextInput
              label="単価"
              maxLength={7}
              inputMode="numeric"
              value={form.unitPrice}
              onChange={(e) => set('unitPrice')(e.target.value)}
              onKeyDown={onKeyDown}
            />
            <TextInput
              label="ケース入数"
              maxLength={4}
              inputMode="numeric"
              value={form.caseQuantity}
              onChange={(e) => set('caseQuantity')(e.target.value)}
              onKeyDown={onKeyDown}
            />
          </>
        }
        buttons={
          <>
            <button
              type="button"
              className={buttonClass + 'bg-blue-600'}
              disabled={busy || selected !== null}
              onClick={() => request('register')}
            >
              登録
            </button>
            <button
              type="button"
              className={buttonClass + 'bg-blue-600'}
              disabled={busy || selected === null}
              onClick={() => request('update')}
            >
              更新
            </button>
            <button
              type="button"
              className={buttonClass + 'bg-red-600'}
              disabled={busy || !canDelete}
              onClick={() => request('delete')}
            >
              削除
            </button>
            <button
              type="button"
              className="rounded border border-slate-300 px-4 py-1 text-slate-700"
              onClick={clearForm}
            >
              クリア
            </button>
          </>
        }
      >
        {loaded && products.length === 0 ? (
          <NotFound name="商品" />
        ) : (
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="bg-slate-100 text-left">
                <th className="border px-2 py-1">商品コード</th>
                <th className="border px-2 py-1">商品名</th>
                <th className="border px-2 py-1">カテゴリ</th>
                <th className="border px-2 py-1 text-right">単価</th>
                <th className="border px-2 py-1 text-right">ケース入数</th>
              </tr>
            </thead>
            <tbody>
              {products.map((p) => {
                const isSelected = selected?.productCode === p.productCode
                return (
                  <tr
                    key={p.productCode}
                    tabIndex={0}
                    aria-selected={isSelected}
                    onClick={() => select(p)}
                    onKeyDown={(e) => {
                      if (e.key === ' ' || e.key === 'Enter') {
                        e.preventDefault()
                        select(p)
                      }
                    }}
                    className={`cursor-pointer hover:bg-blue-50 ${isSelected ? 'bg-blue-100' : ''}`}
                  >
                    <td className="border px-2 py-1">{p.productCode}</td>
                    <td className="border px-2 py-1">{p.productName}</td>
                    <td className="border px-2 py-1">{p.categoryLabel}</td>
                    <td className="border px-2 py-1 text-right">{p.unitPrice.toLocaleString('ja-JP')}</td>
                    <td className="border px-2 py-1 text-right">{p.caseQuantity.toLocaleString('ja-JP')}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </MasterLayout>
      {pending && (
        <ConfirmDialog message={CONFIRM[pending]} onYes={() => void execute(pending)} onNo={() => setPending(null)} />
      )}
      {shown.length > 0 && <ErrorDialog messages={shown} onClose={closeError} />}
    </>
  )
}
