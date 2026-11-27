import type { InputHTMLAttributes } from 'react'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'maxLength'> & {
  /** 最大文字数（必須）。 */
  maxLength: number
  label: string
}

/** 入力欄。maxLength を必須にして、最大文字数の付け忘れを型で防ぐ。 */
export default function TextInput({ label, maxLength, className, ...rest }: Props) {
  return (
    <label className="flex flex-col gap-1 text-sm text-slate-700">
      {label}
      <input
        type="text"
        maxLength={maxLength}
        className={`rounded border border-slate-300 px-2 py-1 ${className ?? ''}`}
        {...rest}
      />
    </label>
  )
}
