import { Err, IdTextList, LinesField, smBtn, smInput } from '../fields'
import { RichText } from '../RichText'
import type { Obj } from '../types'
import { errAt, type Details } from '../util'
import { LIMITS } from './layoutModel'

export interface LayoutProps {
  layout: Obj
  set: (p: Obj) => void
  errors: Details
}

const Reuse = ({ layout, set }: Omit<LayoutProps, 'errors'>) => (
  <label className="flex min-h-9 items-center gap-2 font-bold text-ink">
    <input type="checkbox" checked={!!layout.reuse} onChange={(e) => set({ reuse: e.target.checked })} />
    Một lựa chọn được dùng cho nhiều câu
  </label>
)

export function OptionsLayout({ layout, set, errors }: LayoutProps) {
  return (
    <>
      <IdTextList label="Lựa chọn" prefix="o" min={2} items={layout.options} onChange={(options) => set({ options })} />
      <Err list={errAt(errors, 'layout.options')} />
      <Reuse layout={layout} set={set} />
      <Err list={errAt(errors, 'layout.reuse')} />
    </>
  )
}

export function TextLayout({ layout, set, errors }: LayoutProps) {
  return (
    <>
      <RichText label="Đoạn văn (chỗ trống viết dạng {tên})" value={layout.html ?? ''} onChange={(html) => set({ html })} />
      <Err list={errAt(errors, 'layout.html')} />
    </>
  )
}

export function TextBankLayout({ layout, set, errors }: LayoutProps) {
  return (
    <>
      <TextLayout layout={layout} set={set} errors={errors} />
      <IdTextList label="Hộp từ" prefix="w" min={2} items={layout.words} onChange={(words) => set({ words })} />
      <Err list={errAt(errors, 'layout.words')} />
      <Reuse layout={layout} set={set} />
    </>
  )
}

export function NoteLayout({ layout, set, errors }: LayoutProps) {
  const lines: Obj[] = layout.lines
  const put = (i: number, html: string) => set({ lines: lines.map((l, j) => (j === i ? { html } : l)) })
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="text-sm font-extrabold text-ink">Các dòng ghi chú (chỗ trống viết dạng {'{tên}'})</legend>
      {lines.map((l, i) => (
        // Đổi số dòng thì dựng lại các ô soạn để chúng đọc lại nội dung đúng dòng.
        <div key={`${lines.length}-${i}`} className="flex items-start gap-2">
          <div className="min-w-0 flex-1">
            <RichText compact label={`Dòng ghi chú ${i + 1}`} value={l.html ?? ''} onChange={(h) => put(i, h)} />
            <Err list={errAt(errors, `layout.lines[${i}]`)} />
          </div>
          <button type="button" className={smBtn} aria-label={`Xóa dòng ghi chú ${i + 1}`} disabled={lines.length <= 1} onClick={() => set({ lines: lines.filter((_, j) => j !== i) })}>
            Xóa
          </button>
        </div>
      ))}
      <button type="button" className={`${smBtn} self-start`} disabled={lines.length >= LIMITS.rows} onClick={() => set({ lines: [...lines, { html: '' }] })}>
        Thêm dòng ghi chú
      </button>
      <Err list={errAt(errors, 'layout.lines')} />
    </fieldset>
  )
}

export function FlowLayout({ layout, set, errors }: LayoutProps) {
  return (
    <>
      <LinesField id="flow-steps" label="Các bước" hint="Mỗi dòng một bước; chỗ trống viết dạng {tên}." value={layout.steps ?? []} onChange={(steps) => set({ steps })} rows={5} />
      <Err list={errAt(errors, 'layout.steps')} />
    </>
  )
}

const MODES = [
  ['', 'Chỉ có nhãn'],
  ['slot', 'Chỗ trống'],
  ['value', 'Điền sẵn'],
] as const

export function FormLayout({ layout, set, errors }: LayoutProps) {
  const rows: Obj[] = layout.rows
  const put = (i: number, row: Obj) => set({ rows: rows.map((r, j) => (j === i ? row : r)) })
  const mode = (r: Obj) => (r.slot !== undefined ? 'slot' : r.value !== undefined ? 'value' : '')
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="text-sm font-extrabold text-ink">Các dòng của phiếu</legend>
      {rows.map((r, i) => (
        <div key={i} className="flex flex-wrap items-center gap-2">
          <input aria-label={`Nhãn dòng ${i + 1}`} placeholder="Nhãn (ví dụ Name)" value={r.label ?? ''} onChange={(e) => put(i, { ...r, label: e.target.value })} className={`${smInput} min-w-32 flex-1`} />
          <select
            aria-label={`Kiểu dòng ${i + 1}`}
            value={mode(r)}
            className={`${smInput} w-auto`}
            onChange={(e) => put(i, e.target.value ? { label: r.label, [e.target.value]: '' } : { label: r.label })}
          >
            {MODES.map(([c, l]) => (
              <option key={c} value={c}>
                {l}
              </option>
            ))}
          </select>
          {mode(r) && (
            <input
              aria-label={mode(r) === 'slot' ? `Tên chỗ trống dòng ${i + 1}` : `Giá trị dòng ${i + 1}`}
              placeholder={mode(r) === 'slot' ? 'Tên chỗ trống (ví dụ q1)' : 'Giá trị điền sẵn'}
              value={r.slot ?? r.value ?? ''}
              onChange={(e) => put(i, { label: r.label, [mode(r)]: e.target.value })}
              className={`${smInput} min-w-32 flex-1`}
            />
          )}
          <button type="button" className={smBtn} aria-label={`Xóa dòng ${i + 1}`} disabled={rows.length <= 1} onClick={() => set({ rows: rows.filter((_, j) => j !== i) })}>
            Xóa
          </button>
          <Err list={errAt(errors, `layout.rows[${i}]`)} />
        </div>
      ))}
      <button type="button" className={`${smBtn} self-start`} disabled={rows.length >= LIMITS.rows} onClick={() => set({ rows: [...rows, { label: '' }] })}>
        Thêm dòng
      </button>
      <Err list={errors['layout.rows'] ? [errors['layout.rows']] : []} />
    </fieldset>
  )
}
