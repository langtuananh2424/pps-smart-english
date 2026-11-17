import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { z } from 'zod'
import { FieldError } from '../../auth/AuthShell'
import { applyApiError } from '../../auth/formErrors'
import { Skeleton } from '../home/BlockState'
import { vnToday } from '../home/format'
import { GoalCourse } from '../profile/GoalCourse'
import { BANDS, formatDate, useGoalTarget, useSaveIeltsGoal } from './api'

const schema = z.object({
  targetBand: z.string().min(1, 'Vui lòng chọn band mục tiêu'),
  targetDate: z.string().refine((d) => !d || d > vnToday(), 'Ngày mục tiêu phải sau hôm nay'),
})
type Values = z.infer<typeof schema>

const card = 'flex flex-col gap-3 rounded-[18px] border border-line bg-surface p-5 lg:rounded-[22px]'
const label = 'text-sm font-extrabold leading-[19px] text-ink'
const box = 'h-12 w-full rounded-sm border bg-surface px-3 text-base font-semibold text-ink outline-none focus:border-brand'
const btn = 'flex h-12 items-center justify-center rounded-sm px-4 text-[15px] font-extrabold max-sm:w-full'

function Item({ label: l, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <p className="text-[13px] font-extrabold leading-[18px] text-ink-secondary">{l}</p>
      <div className="text-base font-extrabold leading-[22px] text-ink">{children}</div>
    </div>
  )
}

function Saved({ band, date }: { band: number; date: string }) {
  return (
    <section aria-label="Mục tiêu đã lưu" className={card}>
      <p role="status" className="self-start rounded-full bg-success-bg px-2.5 py-[5px] text-[13px] font-extrabold leading-[18px] text-success-fg">Đã lưu mục tiêu</p>
      <Item label="Khóa học"><GoalCourse /></Item>
      <Item label="Band mục tiêu">IELTS {band.toFixed(1)}</Item>
      {date && <Item label="Ngày mục tiêu">{formatDate(date)}</Item>}
      <Link to="/ho-so" className={`${btn} bg-brand-strong text-white lg:self-start`}>Về hồ sơ</Link>
    </section>
  )
}

function GoalForm({ band, date, onSaved }: { band?: number; date: string; onSaved: (b: number, d: string) => void }) {
  const save = useSaveIeltsGoal()
  const navigate = useNavigate()
  const [serverError, setServerError] = useState('')
  const initial = band != null && BANDS.includes(band.toFixed(1)) ? band.toFixed(1) : ''
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { targetBand: initial, targetDate: date } })

  const onSubmit = async (v: Values) => {
    setServerError('')
    try {
      await save.mutateAsync({ targetBand: Number(v.targetBand), ...(v.targetDate && { targetDate: v.targetDate }) })
      onSaved(Number(v.targetBand), v.targetDate)
    } catch (e) {
      setServerError(applyApiError(e, setError, ['targetBand', 'targetDate']))
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className={card}>
      <Item label="Khóa học"><GoalCourse /></Item>
      <div className="flex flex-col gap-1.5">
        <label htmlFor="targetBand" className={label}>Band mục tiêu</label>
        <select
          id="targetBand"
          aria-invalid={!!errors.targetBand}
          aria-describedby={errors.targetBand ? 'band-err' : undefined}
          className={`${box} ${errors.targetBand ? 'border-danger' : 'border-line-input'}`}
          {...register('targetBand')}
        >
          <option value="" disabled>Chọn band</option>
          {BANDS.map((b) => <option key={b} value={b}>{b}</option>)}
        </select>
        {errors.targetBand && <FieldError id="band-err" text={errors.targetBand.message} />}
      </div>
      <div className="flex flex-col gap-1.5">
        <label htmlFor="targetDate" className={label}>Ngày mục tiêu · Không bắt buộc</label>
        <input
          id="targetDate"
          type="date"
          aria-invalid={!!errors.targetDate}
          aria-describedby={errors.targetDate ? 'date-err' : undefined}
          className={`${box} ${errors.targetDate ? 'border-danger' : 'border-line-input'}`}
          {...register('targetDate')}
        />
        {errors.targetDate && <FieldError id="date-err" text={errors.targetDate.message} />}
      </div>
      {serverError && <p role="alert" className="rounded-sm bg-danger-bg px-4 py-3 text-sm font-bold text-danger-edge">{serverError}</p>}
      <div className="flex flex-col gap-2 sm:flex-row">
        <button type="submit" disabled={save.isPending} className={`${btn} bg-brand-strong text-white disabled:opacity-60`}>
          {save.isPending ? 'Đang lưu…' : 'Lưu mục tiêu'}
        </button>
        <button type="button" onClick={() => navigate('/ho-so')} className={`${btn} border border-line bg-surface text-brand-strong`}>Hủy</button>
      </div>
    </form>
  )
}

// FR-GOAL-02, D-80: sửa band và ngày của mục tiêu IELTS; khóa học chỉ xem.
export function GoalPage() {
  const target = useGoalTarget()
  const [saved, setSaved] = useState<{ band: number; date: string }>()
  return (
    <div className="flex flex-col gap-4 lg:max-w-[720px]">
      <h1 className="text-[27px] font-black leading-[37px] text-ink lg:text-4xl lg:leading-[50px]">
        {saved ? 'Mục tiêu học tập' : 'Chỉnh sửa mục tiêu'}
      </h1>
      {saved ? (
        <Saved {...saved} />
      ) : target.isPending ? (
        <Skeleton className="h-[320px] w-full rounded-lg" />
      ) : (
        // Lỗi tải giá trị ban đầu vẫn cho nhập mới (band để trống).
        <GoalForm band={target.band} date={target.date ?? ''} onSaved={(band, date) => setSaved({ band, date })} />
      )}
    </div>
  )
}
