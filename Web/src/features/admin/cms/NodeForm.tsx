import { useId, useState } from 'react'
import { useForm } from 'react-hook-form'
import { applyApiError } from '../../../auth/formErrors'
import { ApiError } from '../../../lib/api'
import { btnOutline, btnPrimary, FieldText, inputCls, labelCls } from '../ui'
import { writeMessage } from './ui'

export interface FieldDef {
  name: string
  label: string
  kind?: 'text' | 'number' | 'select' | 'check'
  options?: readonly string[]
  required?: boolean
}
type Values = Record<string, string | boolean>

// Giá trị form -> body JSON: ô trống thì bỏ, số đổi sang number.
export function toBody(fields: FieldDef[], v: Values) {
  const body: Record<string, unknown> = {}
  for (const f of fields) {
    const x = v[f.name]
    if (f.kind === 'check') body[f.name] = !!x
    else if (typeof x === 'string' && x.trim() !== '') body[f.name] = f.kind === 'number' ? Number(x) : x.trim()
  }
  return body
}

interface Props {
  fields: FieldDef[]
  initial?: Values
  submitLabel: string
  onSubmit: (body: Record<string, unknown>) => Promise<unknown>
  onCancel?: () => void
}

// Form nhỏ dùng chung cho cấp độ, chủ đề, bài giảng; lỗi 400/409 của máy chủ hiện tại ô hoặc dưới form.
export function NodeForm({ fields, initial, submitLabel, onSubmit, onCancel }: Props) {
  const uid = useId()
  const [formError, setFormError] = useState('')
  const [saved, setSaved] = useState(false)
  const form = useForm<Values>({ defaultValues: initial ?? {} })
  const { errors } = form.formState
  const submit = form.handleSubmit(async (v) => {
    setFormError('')
    setSaved(false)
    try {
      await onSubmit(toBody(fields, v))
      if (!initial) form.reset()
      else setSaved(true)
    } catch (e) {
      const own = e instanceof ApiError && e.status !== 401 && e.status !== 403 && e.status < 500
      setFormError(own ? applyApiError(e, form.setError, fields.map((f) => f.name)) : writeMessage(e))
    }
  })
  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-3 rounded-md border border-line-brand bg-page p-3">
      {fields.map((f) => {
        const id = `${uid}-${f.name}`
        const rules = {
          required: f.required ? `Vui lòng nhập ${f.label.toLowerCase()}` : false,
          // Số nguyên dương: chặn "abc" thay vì gửi NaN/null.
          validate: f.kind === 'number' ? (x: unknown) => !String(x ?? '').trim() || /^[1-9]\d*$/.test(String(x).trim()) || `${f.label} phải là số nguyên từ 1` : undefined,
        }
        if (f.kind === 'check')
          return (
            <label key={f.name} className="flex min-h-11 items-center gap-2 font-bold text-ink">
              <input type="checkbox" {...form.register(f.name)} className="size-5" />
              {f.label}
            </label>
          )
        if (f.kind === 'select')
          return (
            <div key={f.name} className="flex flex-col gap-2">
              <label htmlFor={id} className={labelCls}>
                {f.label}
              </label>
              <select id={id} {...form.register(f.name)} className={inputCls}>
                {!f.required && <option value="">Không chọn</option>}
                {f.options?.map((o) => (
                  <option key={o}>{o}</option>
                ))}
              </select>
            </div>
          )
        return (
          <FieldText
            key={f.name}
            id={id}
            label={f.label}
            reg={form.register(f.name, rules)}
            error={errors[f.name] as never}
          />
        )
      })}
      {formError && (
        <p role="alert" className="font-bold text-danger-fg">
          {formError}
        </p>
      )}
      {saved && (
        <p role="status" className="font-bold text-success-fg">
          Đã lưu.
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <button type="submit" disabled={form.formState.isSubmitting} className={btnPrimary}>
          {submitLabel}
        </button>
        {onCancel && (
          <button type="button" onClick={onCancel} className={btnOutline}>
            Hủy
          </button>
        )}
      </div>
    </form>
  )
}
