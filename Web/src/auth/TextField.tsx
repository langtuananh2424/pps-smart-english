import { useState, type ComponentProps, type ReactNode } from 'react'
import eye from '../assets/icons/eye.svg'
import { Icon } from '../components/Icon'
import { FieldError } from './AuthShell'

type Props = ComponentProps<'input'> & {
  id: string
  label: string
  icon: string
  error?: string
  aside?: ReactNode // vd: link "Quên mật khẩu?" cạnh nhãn
  secret?: boolean // ô mật khẩu có nút hiện/ẩn
}

const box = 'flex min-h-[52px] items-center gap-2.5 rounded-md border-2 bg-input px-4 focus-within:border-brand'
const bare = 'min-w-0 flex-1 bg-transparent px-0.5 py-px font-bold text-ink outline-none placeholder:text-[#757575]'

// Ô nhập kiểu Figma; nhận {...register()} trực tiếp (ref là prop thường ở React 19).
export function TextField({ id, label, icon, error, aside, secret, type, ...input }: Props) {
  const [shown, setShown] = useState(false)
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between">
        <label htmlFor={id} className="text-sm font-extrabold">{label}</label>
        {aside}
      </div>
      <div className={`${box} ${secret ? 'pr-1.5' : ''} ${error ? 'border-danger' : 'border-line-input'}`}>
        <Icon src={icon} className="text-ink-muted" />
        <input
          id={id}
          type={secret ? (shown ? 'text' : 'password') : type}
          aria-invalid={!!error}
          aria-describedby={error ? `${id}-err` : undefined}
          className={bare}
          {...input}
        />
        {secret && (
          <button
            type="button"
            aria-label={shown ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
            aria-pressed={shown}
            onClick={() => setShown((s) => !s)}
            className="grid size-11 place-items-center rounded-sm text-ink-muted hover:bg-subtle"
          >
            <Icon src={eye} />
          </button>
        )}
      </div>
      {error && <FieldError id={`${id}-err`} text={error} />}
    </div>
  )
}
