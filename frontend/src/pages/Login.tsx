import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import ErrorDialog from '../components/ErrorDialog'
import TextInput from '../components/TextInput'
import { useSubmitValidation } from '../components/useSubmitValidation'
import { errorMessages, loginMessages, validationMessage } from '../messages'
import { validateForm } from '../validation'

/** S01 ログイン。入力検証は Enter キー・ボタン押下のときだけ行う。 */
export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [userId, setUserId] = useState('')
  const [password, setPassword] = useState('')
  const [serverErrors, setServerErrors] = useState<string[]>([])

  const validate = () =>
    validateForm({ userId, password })
      .errors()
      .map((e) => validationMessage(e.field, e.type))

  const doLogin = async () => {
    const sentPassword = password
    setPassword('') // パスワードは送信後に state から消す
    const result = await login(userId, sentPassword)
    if (result.type === 'success') {
      navigate('/menu')
    } else if (result.type === 'mismatch') {
      setServerErrors([loginMessages.mismatch])
    } else if (result.type === 'invalid') {
      setServerErrors(result.errors.map((e) => validationMessage(e.field, e.type)))
    } else {
      setServerErrors([loginMessages.failed, errorMessages.retry])
    }
  }

  const { errors, submit, onEnterKey, clearErrors } = useSubmitValidation(validate, () => void doLogin())
  const shown = errors.length > 0 ? errors : serverErrors
  const closeDialog = () => {
    clearErrors()
    setServerErrors([])
  }

  return (
    <main className="mx-auto mt-16 w-80 rounded bg-white p-6 shadow">
      <h1 className="mb-4 text-xl font-bold">ログイン</h1>
      <div className="flex flex-col gap-4">
        <TextInput
          label="ユーザーID"
          maxLength={4}
          value={userId}
          autoComplete="username"
          onChange={(e) => setUserId(e.target.value)}
          onKeyDown={onEnterKey}
        />
        <TextInput
          label="パスワード"
          type="password"
          maxLength={14}
          value={password}
          autoComplete="off"
          onChange={(e) => setPassword(e.target.value)}
          onKeyDown={onEnterKey}
        />
        <button type="button" onClick={submit} className="rounded bg-blue-600 px-4 py-2 text-white">
          ログイン
        </button>
      </div>
      {shown.length > 0 && <ErrorDialog messages={shown} onClose={closeDialog} />}
    </main>
  )
}
