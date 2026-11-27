import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import ConfirmDialog from './ConfirmDialog'
import ErrorDialog from './ErrorDialog'
import { NOTICE_DURATION_MS, NoticeProvider, useNotice } from './Notice'
import NotFound from './NotFound'
import CountLabel from './CountLabel'
import TextInput from './TextInput'
import { useSubmitValidation } from './useSubmitValidation'

afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

describe('ConfirmDialog', () => {
  it('dialog 属性があり、既定フォーカスは「はい」', () => {
    render(<ConfirmDialog message="登録します。よろしいですか？" onYes={vi.fn()} onNo={vi.fn()} />)
    expect(screen.getByRole('dialog')).toHaveAttribute('aria-modal', 'true')
    expect(screen.getByText('登録します。よろしいですか？')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'はい' })).toHaveFocus()
  })
  it('Enter で「はい」、Esc で「いいえ」', () => {
    const onYes = vi.fn()
    const onNo = vi.fn()
    render(<ConfirmDialog message="m" onYes={onYes} onNo={onNo} />)
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Enter' })
    expect(onYes).toHaveBeenCalledTimes(1)
    expect(onNo).not.toHaveBeenCalled()
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(onNo).toHaveBeenCalledTimes(1)
  })
  it('「いいえ」にフォーカスがあっても Enter は「はい」', () => {
    const onYes = vi.fn()
    render(<ConfirmDialog message="m" onYes={onYes} onNo={vi.fn()} />)
    fireEvent.keyDown(screen.getByRole('button', { name: 'いいえ' }), { key: 'Enter' })
    expect(onYes).toHaveBeenCalledTimes(1)
  })
})

describe('ErrorDialog', () => {
  it('複数エラーをまとめて表示する', () => {
    render(<ErrorDialog messages={['A を入力してください。', 'B を選択してください。']} onClose={vi.fn()} />)
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getByText('B を選択してください。')).toBeInTheDocument()
  })
  it('Enter でも Esc でも閉じる', () => {
    const onClose = vi.fn()
    render(<ErrorDialog messages={['x']} onClose={onClose} />)
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Enter' })
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(onClose).toHaveBeenCalledTimes(2)
  })
})

function NoticeDemo() {
  const { show, clear } = useNotice()
  return (
    <>
      <button onClick={() => show('登録しました。')}>one</button>
      <button onClick={() => show('更新しました。')}>two</button>
      <button onClick={clear}>clear</button>
    </>
  )
}

describe('Notice', () => {
  it('7.5秒後に自動で消える', () => {
    vi.useFakeTimers()
    render(
      <NoticeProvider>
        <NoticeDemo />
      </NoticeProvider>,
    )
    fireEvent.click(screen.getByText('one'))
    expect(screen.getByRole('status')).toHaveTextContent('登録しました。')
    expect(screen.getByRole('status').className).toContain('bg-green')
    act(() => {
      vi.advanceTimersByTime(NOTICE_DURATION_MS - 1)
    })
    expect(screen.getByRole('status')).toBeInTheDocument()
    act(() => {
      vi.advanceTimersByTime(1)
    })
    expect(screen.queryByRole('status')).toBeNull()
  })
  it('表示中に新しいメッセージが来たら置き換えて7.5秒を数え直す', () => {
    vi.useFakeTimers()
    render(
      <NoticeProvider>
        <NoticeDemo />
      </NoticeProvider>,
    )
    fireEvent.click(screen.getByText('one'))
    act(() => {
      vi.advanceTimersByTime(5000)
    })
    fireEvent.click(screen.getByText('two'))
    expect(screen.getByRole('status')).toHaveTextContent('更新しました。')
    act(() => {
      vi.advanceTimersByTime(5000) // 最初から10秒だが、2通目からは5秒
    })
    expect(screen.getByRole('status')).toBeInTheDocument()
    act(() => {
      vi.advanceTimersByTime(2500)
    })
    expect(screen.queryByRole('status')).toBeNull()
  })
  it('clear で即座に消える', () => {
    render(
      <NoticeProvider>
        <NoticeDemo />
      </NoticeProvider>,
    )
    fireEvent.click(screen.getByText('one'))
    fireEvent.click(screen.getByText('clear'))
    expect(screen.queryByRole('status')).toBeNull()
  })
})

describe('NotFound / CountLabel', () => {
  it('該当する〇〇がありません。', () => {
    render(<NotFound name="商品" />)
    expect(screen.getByText('該当する商品がありません。')).toBeInTheDocument()
  })
  it('N件', () => {
    render(<CountLabel count={12} />)
    expect(screen.getByText('12件')).toBeInTheDocument()
  })
})

function Form({ validate, onValid }: { validate: () => string[]; onValid: () => void }) {
  const [v, setV] = useState('')
  const { errors, submit, onEnterKey } = useSubmitValidation(validate, onValid)
  return (
    <div>
      <TextInput label="名称" maxLength={5} value={v} onChange={(e) => setV(e.target.value)} onKeyDown={onEnterKey} />
      <button onClick={submit}>登録</button>
      <input aria-label="other" />
      {errors.length > 0 && <ErrorDialog messages={errors} onClose={() => {}} />}
    </div>
  )
}

describe('TextInput / useSubmitValidation', () => {
  it('maxLength が input に付く', () => {
    render(<TextInput label="名称" maxLength={5} />)
    expect(screen.getByLabelText('名称')).toHaveAttribute('maxlength', '5')
  })
  it('blur では検証しない', () => {
    const validate = vi.fn(() => [])
    render(<Form validate={validate} onValid={vi.fn()} />)
    const input = screen.getByLabelText('名称')
    input.focus()
    fireEvent.blur(input)
    screen.getByLabelText('other').focus()
    expect(validate).not.toHaveBeenCalled()
  })
  it('Enter とボタン押下で検証し、エラーがあれば onValid は呼ばない', () => {
    const validate = vi.fn(() => ['名称を入力してください。', '別項目を選択してください。'])
    const onValid = vi.fn()
    render(<Form validate={validate} onValid={onValid} />)
    fireEvent.keyDown(screen.getByLabelText('名称'), { key: 'Enter' })
    expect(validate).toHaveBeenCalledTimes(1)
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
    fireEvent.click(screen.getByText('登録'))
    expect(validate).toHaveBeenCalledTimes(2)
    expect(onValid).not.toHaveBeenCalled()
  })
  it('エラーが無ければ onValid を呼ぶ', () => {
    const onValid = vi.fn()
    render(<Form validate={() => []} onValid={onValid} />)
    fireEvent.click(screen.getByText('登録'))
    expect(onValid).toHaveBeenCalledTimes(1)
  })
})
