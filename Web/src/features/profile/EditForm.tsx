import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { FieldError } from '../../auth/AuthShell'
import { applyApiError } from '../../auth/formErrors'
import { SELF_LEVELS } from '../qa/content'
import { selfLevelSchema } from '../qa/schema'
import { useUpdateMe, type Me } from './api'

const schema = z.object({
  displayName: z.string().trim().min(1, 'Vui lòng nhập tên hiển thị').max(100, 'Tên hiển thị tối đa 100 ký tự'),
  selfLevel: z.union([selfLevelSchema, z.literal('')]),
})
type Values = z.infer<typeof schema>

const label = 'text-sm font-extrabold leading-[19px] text-ink'
const box = 'h-12 w-full rounded-sm border bg-surface px-3 text-base font-semibold text-ink outline-none focus:border-brand'
const btn = 'flex h-12 flex-1 items-center justify-center rounded-[14px] px-4 text-[15px] font-extrabold lg:flex-none'

export function EditForm({ me, onCancel, onSaved }: { me: Me; onCancel: () => void; onSaved: (name: string) => void }) {
  const update = useUpdateMe()
  const [serverError, setServerError] = useState('')
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { displayName: me.displayName, selfLevel: me.selfLevel ?? '' } })

  const onSubmit = async (v: Values) => {
    setServerError('')
    try {
      const saved = await update.mutateAsync({ displayName: v.displayName, ...(v.selfLevel && { selfLevel: v.selfLevel }) })
      onSaved(saved.displayName)
    } catch (e) {
      setServerError(applyApiError(e, setError, ['displayName', 'selfLevel']))
    }
  }

  return (
    <div className="flex flex-col gap-4 lg:gap-6">
      <h1 className="text-[28px] font-black leading-[38px] text-ink lg:text-4xl lg:leading-[49px]">Chỉnh sửa hồ sơ</h1>
      <form
        onSubmit={handleSubmit(onSubmit)}
        noValidate
        className="flex flex-col gap-4 rounded-[22px] border border-line bg-surface p-4 lg:max-w-[620px] lg:rounded-[20px] lg:p-6"
      >
        <div className="flex flex-col gap-1.5">
          <label htmlFor="displayName" className={label}>Tên hiển thị</label>
          <input
            id="displayName"
            autoComplete="name"
            aria-invalid={!!errors.displayName}
            aria-describedby={errors.displayName ? 'displayName-err' : undefined}
            className={`${box} ${errors.displayName ? 'border-danger' : 'border-line-input'}`}
            {...register('displayName')}
          />
          {errors.displayName && <FieldError id="displayName-err" text={errors.displayName.message} />}
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor="email" className={label}>Email</label>
          <input id="email" readOnly value={me.email} aria-describedby="email-note" className={`${box} border-line-input bg-subtle text-ink-secondary`} />
          <p id="email-note" className="text-xs font-semibold text-ink-secondary">Email dùng để đăng nhập, chưa đổi được.</p>
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor="selfLevel" className={label}>Trình độ tự đánh giá</label>
          <select id="selfLevel" className={`${box} border-line-input`} {...register('selfLevel')}>
            <option value="" disabled>Chọn trình độ</option>
            {SELF_LEVELS.map((l) => (
              <option key={l.value} value={l.value}>{l.label}</option>
            ))}
          </select>
          {errors.selfLevel && <FieldError id="selfLevel-err" text={errors.selfLevel.message} />}
        </div>
        {serverError && (
          <p role="alert" className="rounded-sm bg-danger-bg px-4 py-3 text-sm font-bold text-danger-edge">{serverError}</p>
        )}
        <div className="flex gap-2">
          <button type="button" onClick={onCancel} className={`${btn} border border-line bg-surface text-brand-strong`}>Hủy</button>
          <button type="submit" disabled={update.isPending} className={`${btn} bg-brand-strong text-white disabled:opacity-60`}>
            {update.isPending ? 'Đang lưu…' : 'Lưu thay đổi'}
          </button>
        </div>
      </form>
    </div>
  )
}
