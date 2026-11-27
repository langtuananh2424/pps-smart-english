import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { Link, useParams } from 'react-router'
import { z } from 'zod'
import { applyApiError } from '../../../auth/formErrors'
import { ApiError } from '../../../lib/api'
import { FieldText, Loading, PageHead, RetryError, Section } from '../ui'
import { cmsWrites, useCmsWrite, useCourseTree, type CourseView } from './api'
import { courseUrl, pillOutline, pillPrimary, writeMessage } from './ui'

const int = (min: number, max: number, label: string) =>
  z.string().regex(/^\d+$/, `${label} phải là số nguyên`).refine((v) => Number(v) >= min && Number(v) <= max, `${label} từ ${min} đến ${max}`)
const schema = z.object({
  deliveryMode: z.enum(['SELF_PACED', 'CLASS_BASED']),
  sequential: z.boolean(),
  ielts: z.boolean(),
  lessonCompletePercent: int(1, 100, 'Tỷ lệ xem bài giảng'),
  examUnlockPercent: int(0, 100, 'Ngưỡng mở kiểm tra cuối khóa'),
  examRetakes: int(0, 1000, 'Số lần thi lại'),
})
type Values = z.infer<typeof schema>
const FIELDS = Object.keys(schema.shape)

const MODES = [
  { value: 'SELF_PACED', label: 'Tự học' },
  { value: 'CLASS_BASED', label: 'Có lớp' },
] as const

function SettingsForm({ course }: { course: CourseView }) {
  const [msg, setMsg] = useState('')
  const [saved, setSaved] = useState(false)
  const write = useCmsWrite((b: object) => cmsWrites.patchCourse(course.id, b))
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      deliveryMode: course.deliveryMode === 'CLASS_BASED' ? 'CLASS_BASED' : 'SELF_PACED',
      sequential: course.sequential,
      ielts: !!course.ielts,
      lessonCompletePercent: String(course.lessonCompletePercent),
      examUnlockPercent: String(course.examUnlockPercent),
      examRetakes: String(course.examRetakes),
    },
  })
  const { errors } = form.formState
  const submit = form.handleSubmit(async (v) => {
    setMsg('')
    setSaved(false)
    try {
      // Chỉ gửi trường đã đổi để không ghi đè giá trị người khác vừa sửa.
      const d = form.formState.dirtyFields
      const body: Record<string, unknown> = {}
      if (d.deliveryMode) body.deliveryMode = v.deliveryMode
      if (d.sequential) body.sequential = v.sequential
      if (d.ielts) body.ielts = v.ielts
      for (const k of ['lessonCompletePercent', 'examUnlockPercent', 'examRetakes'] as const) if (d[k]) body[k] = Number(v[k])
      await write.mutateAsync(body)
      form.reset(v) // giá trị vừa lưu thành mốc mới, để lần lưu sau so đúng với máy chủ
      setSaved(true)
    } catch (e) {
      const own = e instanceof ApiError && e.status < 500 && e.status !== 401 && e.status !== 403
      setMsg(own ? applyApiError(e, form.setError, FIELDS) : writeMessage(e))
    }
  })
  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      <Section title="Loại khóa">
        <Controller
          control={form.control}
          name="deliveryMode"
          render={({ field }) => (
            <div role="radiogroup" aria-label="Loại khóa" className="flex flex-col gap-2 sm:flex-row">
              {MODES.map((m) => (
                <button
                  key={m.value}
                  type="button"
                  role="radio"
                  aria-checked={field.value === m.value}
                  onClick={() => field.onChange(m.value)}
                  className={`min-h-12 flex-1 rounded-md border px-3 text-left font-extrabold text-ink ${field.value === m.value ? 'border-brand-strong bg-subtle' : 'border-line-input bg-input'}`}
                >
                  {m.label}
                </button>
              ))}
            </div>
          )}
        />
      </Section>
      <label className="flex min-h-12 items-center justify-between gap-4 rounded-md bg-input p-3 font-extrabold text-ink">
        Học tuần tự
        <input type="checkbox" role="switch" {...form.register('sequential')} className="size-6 accent-[#c2410c]" />
      </label>
      <label className="flex min-h-12 items-center justify-between gap-4 rounded-md bg-input p-3 font-extrabold text-ink">
        Khóa IELTS
        <input type="checkbox" role="switch" {...form.register('ielts')} className="size-6 accent-[#c2410c]" />
      </label>
      <Section title="Điều kiện và lịch">
        <div className="grid gap-3 md:grid-cols-2">
          <FieldText id="lessonCompletePercent" label="Tỷ lệ xem bài giảng để mở Unit" reg={form.register('lessonCompletePercent')} error={errors.lessonCompletePercent} placeholder="Nhập tỷ lệ %" hint="Từ 1 đến 100" />
          <FieldText id="examUnlockPercent" label="Ngưỡng mở kiểm tra cuối khóa" reg={form.register('examUnlockPercent')} error={errors.examUnlockPercent} placeholder="80% mặc định" hint="Từ 0 đến 100" />
        </div>
      </Section>
      <Section title="Bài kiểm tra trong khóa">
        <FieldText id="examRetakes" label="Kiểm tra cuối khóa · Số lần thi lại" reg={form.register('examRetakes')} error={errors.examRetakes} placeholder="0 lần thi lại mặc định" hint="Không tính lần thi đầu" />
      </Section>
      {msg && (
        <p role="alert" className="font-bold text-danger-fg">
          {msg}
        </p>
      )}
      {saved && (
        <p role="status" className="font-bold text-success-fg">
          Đã lưu cấu hình.
        </p>
      )}
      <div className="flex flex-wrap gap-3">
        <button type="submit" disabled={form.formState.isSubmitting || !form.formState.isDirty} className={pillPrimary}>
          Lưu cấu hình
        </button>
        <Link to={courseUrl(course.id)} className={pillOutline}>
          Hủy
        </Link>
      </div>
    </form>
  )
}

// FR-LMS-04 / FR-EXAM-02: loại khóa, học tuần tự, tỷ lệ xem bài, ngưỡng và số lần thi lại cuối khóa (PATCH course). Figma 482:17563.
export function CourseSettingsPage() {
  const { courseId = '' } = useParams()
  const q = useCourseTree(courseId)
  if (q.isPending) return <Loading />
  if (q.isError) return <RetryError error={q.error} onRetry={() => q.refetch()} />
  return (
    <div className="flex flex-col gap-4">
      <PageHead title="Cấu hình học và kiểm tra" sub={q.data.course.title} />
      <SettingsForm course={q.data.course} />
    </div>
  )
}
