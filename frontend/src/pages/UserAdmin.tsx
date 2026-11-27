import { useCallback, useEffect, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { deleteUser, fetchUsers, postUser, putUser } from '../api/users'
import type { UserInput, UserRow } from '../api/users'
import { ROLE_OPTIONS } from '../codes'
import ConfirmDialog from '../components/ConfirmDialog'
import ErrorDialog from '../components/ErrorDialog'
import MasterLayout from '../components/MasterLayout'
import NotFound from '../components/NotFound'
import { NoticeProvider, useNotice } from '../components/Notice'
import TextInput from '../components/TextInput'
import { useSubmitValidation } from '../components/useSubmitValidation'
import { confirmMessages, errorMessages, resultMessages, userMessages, validationMessage } from '../messages'
import { checkRule } from '../validation'

type Action = 'register' | 'update' | 'delete'

const EMPTY_FORM: UserInput = { userId: '', userName: '', pw: '', role: 'GENERAL' }

/** 行を入力欄に表示する形にする（パスワードは常に空）。 */
const toForm = (u: UserRow): UserInput => ({ userId: u.userId, userName: u.userName, pw: '', role: u.role })

const sameAs = (a: UserInput, b: UserInput) =>
  a.userId === b.userId && a.userName === b.userName && a.pw === b.pw && a.role === b.role

/** 権限の選択肢（一般・管理者の順）。 */
const ROLE_CHOICES = [...ROLE_OPTIONS].reverse()

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

/** S10 ユーザー管理（管理者のみ）。パスワードは入力欄だけに持ち、送信後に必ず空にする。 */
export default function UserAdmin() {
  return (
    <NoticeProvider>
      <UserAdminBody />
    </NoticeProvider>
  )
}

function UserAdminBody() {
  const notice = useNotice()
  const [users, setUsers] = useState<UserRow[]>([])
  const [loaded, setLoaded] = useState(false)
  const [selected, setSelected] = useState<UserRow | null>(null)
  const [form, setForm] = useState<UserInput>(EMPTY_FORM)
  const [pending, setPending] = useState<Action | null>(null)
  const [serverErrors, setServerErrors] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  const action = useRef<Action>('register')

  const reload = useCallback(async () => {
    try {
      const res = await fetchUsers()
      if (res.ok && Array.isArray(res.body)) {
        setUsers(res.body)
        setLoaded(true)
        return true
      }
    } catch {
      // 通信失敗は下で通知する
    }
    setServerErrors([userMessages.loadFailed, errorMessages.retry])
    return false
  }, [])

  useEffect(() => {
    // 画面を開いたときに一覧を読み込む
    void reload()
  }, [reload])

  const set = (field: keyof UserInput) => (value: string) => setForm((f) => ({ ...f, [field]: value }))

  const clearForm = () => {
    setSelected(null)
    setForm(EMPTY_FORM)
  }

  const select = (u: UserRow) => {
    setSelected(u)
    setForm(toForm(u))
  }

  /** パスワード欄だけを空にする（送信後は成否にかかわらず必ず呼ぶ）。 */
  const clearPw = () => setForm((f) => ({ ...f, pw: '' }))

  // 削除は、選択行があり、入力内容（パスワード欄が空であることを含む）が選択行と一致しているときだけ
  const canDelete = selected !== null && sameAs(form, toForm(selected))

  const validate = (): string[] => {
    if (action.current === 'delete') return []
    const messages: string[] = []
    const add = (field: string, type: string | undefined) => {
      if (type) messages.push(validationMessage(field, type))
    }
    if (!selected) add('userId', checkRule('userId', form.userId))
    add('userName', checkRule('userName', form.userName))
    // 更新では空なら変更しない。入力があれば登録と同じ規則で検証する
    if (!selected || form.pw !== '') add('password', checkRule('password', form.pw))
    if (form.role === '') add('role', 'REQUIRED')
    return messages
  }

  const { errors, submit, onEnterKey, clearErrors } = useSubmitValidation(validate, () => setPending(action.current))

  const request = (a: Action) => {
    action.current = a
    submit()
  }

  /** 入力欄の Enter: 選択中なら更新、そうでなければ登録。通信中・確認中の Enter は無視する。 */
  const onKeyDown = (e: KeyboardEvent<HTMLElement>) => {
    if (e.key === 'Enter') {
      if (busy || pending !== null) {
        e.preventDefault()
        return
      }
      action.current = selected ? 'update' : 'register'
    }
    onEnterKey(e)
  }

  const execute = async (a: Action) => {
    if (busy) return
    setPending(null)
    setBusy(true)
    try {
      const target = selected?.userId ?? form.userId
      const res =
        a === 'register'
          ? await postUser(form)
          : a === 'update'
            ? await putUser({ ...form, userId: target })
            : await deleteUser(target)
      if (res.ok) {
        notice.show(DONE[a])
        clearForm()
        await reload()
      } else if (res.status === 400) {
        const list = (res.body as { errors?: { field: string; type: string }[] } | undefined)?.errors ?? []
        setServerErrors(
          list.length > 0 ? list.map((e) => validationMessage(e.field, e.type)) : [userMessages.failed, errorMessages.retry],
        )
      } else if (res.status === 404) {
        setServerErrors([userMessages.notFound])
        clearForm()
        await reload()
      } else if (res.status === 409) {
        const code = (res.body as { error?: string } | undefined)?.error
        setServerErrors([
          code === 'SELF'
            ? userMessages.self
            : code === 'LAST_ADMIN'
              ? userMessages.lastAdmin
              : code === 'HAS_RESULTS'
                ? userMessages.hasResults
                : userMessages.duplicate,
        ])
      } else {
        setServerErrors([userMessages.failed, errorMessages.retry])
      }
    } catch {
      setServerErrors([userMessages.failed, errorMessages.retry])
    } finally {
      clearPw()
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
        title="ユーザー管理（S10）"
        count={users.length}
        form={
          <>
            <TextInput
              label="ユーザーID"
              maxLength={4}
              value={form.userId}
              disabled={selected !== null}
              onChange={(e) => set('userId')(e.target.value)}
              onKeyDown={onKeyDown}
            />
            <TextInput
              label="氏名"
              maxLength={15}
              value={form.userName}
              onChange={(e) => set('userName')(e.target.value)}
              onKeyDown={onKeyDown}
            />
            <div className="flex flex-col gap-1">
              <TextInput
                label="パスワード"
                type="password"
                maxLength={14}
                autoComplete="new-password"
                aria-describedby="user-admin-pw-hint"
                value={form.pw}
                onChange={(e) => set('pw')(e.target.value)}
                onKeyDown={onKeyDown}
              />
              <p id="user-admin-pw-hint" className="text-xs text-slate-500">
                8〜14文字。更新のときは、空のままなら変更しません。
              </p>
            </div>
            <label className="flex flex-col gap-1 text-sm text-slate-700">
              権限
              <select
                value={form.role}
                onChange={(e) => set('role')(e.target.value)}
                onKeyDown={onKeyDown}
                className="rounded border border-slate-300 px-2 py-1"
              >
                {ROLE_CHOICES.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </label>
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
        {loaded && users.length === 0 ? (
          <NotFound name="ユーザー" />
        ) : (
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="bg-slate-100 text-left">
                <th className="border px-2 py-1">ユーザーID</th>
                <th className="border px-2 py-1">氏名</th>
                <th className="border px-2 py-1">権限</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => {
                const isSelected = selected?.userId === u.userId
                return (
                  <tr
                    key={u.userId}
                    tabIndex={0}
                    aria-selected={isSelected}
                    onClick={() => select(u)}
                    onKeyDown={(e) => {
                      if (e.key === ' ' || e.key === 'Enter') {
                        e.preventDefault()
                        select(u)
                      }
                    }}
                    className={`cursor-pointer hover:bg-blue-50 ${isSelected ? 'bg-blue-100' : ''}`}
                  >
                    <td className="border px-2 py-1">{u.userId}</td>
                    <td className="border px-2 py-1">{u.userName}</td>
                    <td className="border px-2 py-1">{u.roleLabel}</td>
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
