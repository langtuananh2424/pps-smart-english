import { useState, type ReactNode } from 'react'
import { labelCls } from '../../ui'
import { nextId } from './util'

export const smBtn =
  'inline-flex min-h-9 items-center justify-center rounded-sm border border-line bg-surface px-3 text-sm font-extrabold text-brand-strong disabled:opacity-50'
export const smInput = 'min-h-10 w-full min-w-0 rounded-sm border border-line-input bg-input px-3 font-semibold text-ink outline-none focus:border-brand'

export const Err = ({ list }: { list: string[] }) =>
  list.length ? (
    <ul role="alert" className="text-sm font-bold text-danger-fg">
      {[...new Set(list)].map((m) => (
        <li key={m}>{m}</li>
      ))}
    </ul>
  ) : null

export function Labeled({ label, htmlFor, children, hint }: { label: string; htmlFor?: string; children: ReactNode; hint?: string }) {
  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <label htmlFor={htmlFor} className={labelCls}>
        {label}
      </label>
      {children}
      {hint && <p className="text-sm font-semibold text-ink-secondary">{hint}</p>}
    </div>
  )
}

// Mỗi dòng một giá trị; giữ chữ gõ ở state riêng để không mất dòng trống đang gõ.
export function LinesField({ id, label, value, onChange, rows = 3, hint }: { id: string; label: string; value: string[]; onChange: (v: string[]) => void; rows?: number; hint?: string }) {
  const [raw, setRaw] = useState(value.join('\n'))
  return (
    <Labeled label={label} htmlFor={id} hint={hint ?? 'Mỗi dòng một giá trị.'}>
      <textarea
        id={id}
        rows={rows}
        value={raw}
        className={`${smInput} py-2`}
        onChange={(e) => {
          setRaw(e.target.value)
          onChange(e.target.value.split('\n').map((s) => s.trim()).filter(Boolean))
        }}
      />
    </Labeled>
  )
}

export interface IdText {
  id: string
  text: string
}

// Danh sách phần tử {id,text}; id tự đánh, người soạn chỉ thấy chữ. movable: có nút đổi thứ tự.
export function IdTextList({ label, items, onChange, prefix, min = 1, movable = false }: { label: string; items: IdText[]; onChange: (v: IdText[]) => void; prefix: string; min?: number; movable?: boolean }) {
  const set = (i: number, text: string) => onChange(items.map((x, j) => (j === i ? { ...x, text } : x)))
  const move = (i: number, d: number) => {
    const next = [...items]
    ;[next[i], next[i + d]] = [next[i + d], next[i]]
    onChange(next)
  }
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className={labelCls}>{label}</legend>
      {items.map((it, i) => (
        <div key={it.id} className="flex items-center gap-2">
          <input aria-label={`${label} ${i + 1}`} value={it.text} onChange={(e) => set(i, e.target.value)} className={smInput} />
          {movable && (
            <>
              <button type="button" className={smBtn} aria-label={`Đưa ${label} ${i + 1} lên`} disabled={i === 0} onClick={() => move(i, -1)}>
                ↑
              </button>
              <button type="button" className={smBtn} aria-label={`Đưa ${label} ${i + 1} xuống`} disabled={i === items.length - 1} onClick={() => move(i, 1)}>
                ↓
              </button>
            </>
          )}
          <button type="button" className={smBtn} aria-label={`Xóa ${label} ${i + 1}`} disabled={items.length <= min} onClick={() => onChange(items.filter((_, j) => j !== i))}>
            Xóa
          </button>
        </div>
      ))}
      <button type="button" className={`${smBtn} self-start`} onClick={() => onChange([...items, { id: nextId(items.map((x) => x.id), prefix), text: '' }])}>
        Thêm {label.toLowerCase()}
      </button>
    </fieldset>
  )
}

export const Pill = ({ children, tone }: { children: ReactNode; tone: 'draft' | 'live' }) => (
  <span className={`inline-flex rounded-full px-2.5 py-[5px] text-[13px] font-extrabold ${tone === 'live' ? 'bg-success-bg text-success-fg' : 'bg-subtle text-brand-strong'}`}>{children}</span>
)
