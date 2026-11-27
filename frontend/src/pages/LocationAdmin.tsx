import { useCallback, useEffect, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { deleteLocation, fetchLocations, postLocation, putLocation } from '../api/locations'
import type { Location, LocationInput } from '../api/locations'
import { SHELF_STATUS_OPTIONS } from '../codes'
import ConfirmDialog from '../components/ConfirmDialog'
import ErrorDialog from '../components/ErrorDialog'
import MasterLayout from '../components/MasterLayout'
import NotFound from '../components/NotFound'
import { NoticeProvider, useNotice } from '../components/Notice'
import TextInput from '../components/TextInput'
import { useSubmitValidation } from '../components/useSubmitValidation'
import { confirmMessages, errorMessages, locationMessages, resultMessages, validationMessage } from '../messages'
import { checkRule } from '../validation'

type Action = 'register' | 'update' | 'delete'

const EMPTY_FORM: LocationInput = { shelfNo: '', shelfStatus: '', remarks: '' }

const toForm = (l: Location): LocationInput => ({
  shelfNo: l.shelfNo,
  shelfStatus: l.shelfStatus,
  remarks: l.remarks,
})

const sameAs = (a: LocationInput, b: LocationInput) =>
  a.shelfNo === b.shelfNo && a.shelfStatus === b.shelfStatus && a.remarks === b.remarks

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

/** 登録・更新で選べる棚状況（実棚は在庫が入ったときだけ変わるので選べない）。 */
const SELECTABLE_STATUS = SHELF_STATUS_OPTIONS.filter((o) => o.value !== 'STOCKED')

/** サーバーの検証エラー（項目名＋種別）を画面の文面にする。 */
const serverMessage = (field: string, type: string) =>
  field === 'shelfStatus' ? locationMessages.shelfStatus(type) : validationMessage(field, type)

/** S09 ロケーションメンテナンス（管理者のみ）。 */
export default function LocationAdmin() {
  return (
    <NoticeProvider>
      <LocationAdminBody />
    </NoticeProvider>
  )
}

function LocationAdminBody() {
  const notice = useNotice()
  const [locations, setLocations] = useState<Location[]>([])
  const [loaded, setLoaded] = useState(false)
  const [selected, setSelected] = useState<Location | null>(null)
  const [form, setForm] = useState<LocationInput>(EMPTY_FORM)
  const [pending, setPending] = useState<Action | null>(null)
  const [serverErrors, setServerErrors] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  const action = useRef<Action>('register')

  /** 実棚（在庫のある棚）を選択中は、棚番・棚状況を変えられない（備考だけ変えられる）。 */
  const stockedSelected = selected?.shelfStatus === 'STOCKED'

  const reload = useCallback(async () => {
    try {
      const res = await fetchLocations()
      if (res.ok && Array.isArray(res.body)) {
        setLocations(res.body)
        setLoaded(true)
        return true
      }
    } catch {
      // 通信失敗は下で通知する
    }
    setServerErrors([locationMessages.loadFailed, errorMessages.retry])
    return false
  }, [])

  useEffect(() => {
    // 画面を開いたときに一覧を読み込む
    void reload()
  }, [reload])

  const set = (field: keyof LocationInput) => (value: string) => setForm((f) => ({ ...f, [field]: value }))

  const clearForm = () => {
    setSelected(null)
    setForm(EMPTY_FORM)
  }

  const select = (l: Location) => {
    setSelected(l)
    setForm(toForm(l))
  }

  const canDelete = selected !== null && sameAs(form, toForm(selected))

  const validate = (): string[] => {
    if (action.current === 'delete') return []
    const messages: string[] = []
    if (!stockedSelected) {
      const shelfNo = checkRule('shelfNo', form.shelfNo)
      if (shelfNo) messages.push(validationMessage('shelfNo', shelfNo))
      if (form.shelfStatus === '') messages.push(locationMessages.shelfStatus('REQUIRED'))
    }
    const remarks = checkRule('remarks', form.remarks)
    if (remarks) messages.push(validationMessage('remarks', remarks))
    return messages
  }

  const { errors, submit, onEnterKey, clearErrors } = useSubmitValidation(validate, () => setPending(action.current))

  const request = (a: Action) => {
    action.current = a
    submit()
  }

  /** 入力欄の Enter: 選択中なら更新、そうでなければ登録。通信中は無視する（ボタンと同じ）。 */
  const onKeyDown = (e: KeyboardEvent<HTMLElement>) => {
    if (e.key === 'Enter') {
      if (busy) {
        e.preventDefault()
        return
      }
      action.current = selected ? 'update' : 'register'
    }
    onEnterKey(e)
  }

  const execute = async (a: Action) => {
    setPending(null)
    setBusy(true)
    try {
      const target = selected?.shelfNo ?? form.shelfNo
      const res =
        a === 'register'
          ? await postLocation(form)
          : a === 'update'
            ? await putLocation(target, form)
            : await deleteLocation(target)
      if (res.ok) {
        notice.show(DONE[a])
        clearForm()
        await reload()
      } else if (res.status === 400) {
        const list = (res.body as { errors?: { field: string; type: string }[] } | undefined)?.errors ?? []
        setServerErrors(
          list.length > 0
            ? list.map((e) => serverMessage(e.field, e.type))
            : [locationMessages.failed, errorMessages.retry],
        )
      } else if (res.status === 404) {
        setServerErrors([locationMessages.notFound])
        clearForm()
        await reload()
      } else if (res.status === 409) {
        const code = (res.body as { error?: string } | undefined)?.error
        setServerErrors([
          code !== 'HAS_STOCK'
            ? locationMessages.duplicate
            : a === 'delete'
              ? locationMessages.hasStockDelete
              : locationMessages.hasStockUpdate,
        ])
      } else {
        setServerErrors([locationMessages.failed, errorMessages.retry])
      }
    } catch {
      setServerErrors([locationMessages.failed, errorMessages.retry])
    } finally {
      setBusy(false)
    }
  }

  const shown = errors.length > 0 ? errors : serverErrors
  const closeError = () => {
    clearErrors()
    setServerErrors([])
  }

  const statusOptions = stockedSelected ? SHELF_STATUS_OPTIONS : SELECTABLE_STATUS

  const buttonClass = 'rounded px-4 py-1 text-white disabled:opacity-40 '
  return (
    <>
      <MasterLayout
        title="ロケーションメンテ（S09）"
        count={locations.length}
        form={
          <>
            <TextInput
              label="棚番"
              maxLength={9}
              value={form.shelfNo}
              disabled={stockedSelected}
              onChange={(e) => set('shelfNo')(e.target.value)}
              onKeyDown={onKeyDown}
            />
            <label className="flex flex-col gap-1 text-sm text-slate-700">
              棚状況
              <select
                value={form.shelfStatus}
                disabled={stockedSelected}
                onChange={(e) => set('shelfStatus')(e.target.value)}
                onKeyDown={onKeyDown}
                className="rounded border border-slate-300 px-2 py-1"
              >
                <option value="">（未選択）</option>
                {statusOptions.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </label>
            <TextInput
              label="備考"
              maxLength={50}
              value={form.remarks}
              onChange={(e) => set('remarks')(e.target.value)}
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
        {loaded && locations.length === 0 ? (
          <NotFound name="棚" />
        ) : (
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="bg-slate-100 text-left">
                <th className="border px-2 py-1">棚番</th>
                <th className="border px-2 py-1">棚状況</th>
                <th className="border px-2 py-1">備考</th>
              </tr>
            </thead>
            <tbody>
              {locations.map((l) => {
                const isSelected = selected?.shelfNo === l.shelfNo
                return (
                  <tr
                    key={l.shelfNo}
                    tabIndex={0}
                    aria-selected={isSelected}
                    onClick={() => select(l)}
                    onKeyDown={(e) => {
                      if (e.key === ' ' || e.key === 'Enter') {
                        e.preventDefault()
                        select(l)
                      }
                    }}
                    className={`cursor-pointer hover:bg-blue-50 ${isSelected ? 'bg-blue-100' : ''}`}
                  >
                    <td className="border px-2 py-1">{l.shelfNo}</td>
                    <td className="border px-2 py-1">{l.shelfStatusLabel}</td>
                    <td className="border px-2 py-1">{l.remarks}</td>
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
