import { zodResolver } from '@hookform/resolvers/zod'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { ApiError } from '../../lib/api'
import { btnPrimary, FieldText, Section, WriteError } from '../admin/ui'
import { useGrade } from './api'

// Điểm: không âm, tối đa 2 chữ số thập phân, không quá thang; Nói theo bước 0,5 (D-170). Chấp nhận dấu phẩy.
export function gradeSchema(max: number | null, speaking: boolean) {
  return z
    .object({ score: z.string().trim(), comment: z.string().max(4000, 'Nhận xét tối đa 4000 ký tự') })
    .superRefine((v, ctx) => {
      const issue = (path: 'score' | 'comment', message: string) => ctx.addIssue({ code: 'custom', path: [path], message })
      const raw = v.score.replace(',', '.')
      if (!raw && !v.comment.trim()) return issue('score', 'Nhập điểm mới hoặc nhận xét')
      if (!raw) return
      const n = Number(raw)
      if (!/^\d+(\.\d{1,2})?$/.test(raw)) return issue('score', 'Điểm là số không âm, tối đa 2 chữ số thập phân')
      const cap = max ?? (speaking ? 9 : null) // thang Nói 0 đến 9 (D-170)
      if (cap != null && n > cap) return issue('score', `Điểm tối đa ${cap}`)
      if (speaking && (n * 2) % 1 !== 0) issue('score', 'Điểm Nói theo bước 0,5')
    })
}

// Form "Chấm của giáo viên" (Figma 480:14071); readOnly khi bài đã hủy (D-169).
export function GradeForm({ classId, id, attemptId, max, speaking, readOnly, onCancelled }: {
  classId: number; id: number; attemptId: number; max: number | null; speaking: boolean; readOnly: boolean; onCancelled: () => void
}) {
  const grade = useGrade(classId, id, attemptId)
  const schema = useMemo(() => gradeSchema(max, speaking), [max, speaking])
  const form = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema), defaultValues: { score: '', comment: '' } })
  const [saved, setSaved] = useState(false)
  const { errors } = form.formState
  const e = grade.error
  const submit = form.handleSubmit((v) => {
    setSaved(false)
    const raw = v.score.replace(',', '.')
    grade.mutate(
      { score: raw ? Number(raw) : undefined, comment: v.comment.trim() || undefined },
      {
        onSuccess: () => { form.reset(); setSaved(true) },
        onError: (err) => err instanceof ApiError && err.code === 'assignment_cancelled' && onCancelled(),
      },
    )
  })
  if (readOnly) return <Section title="Chấm của giáo viên"><p role="status" className="font-semibold text-ink-secondary">Bài giao đã hủy nên chỉ xem, không chấm thêm được.</p></Section>
  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-3">
      <Section title="Chấm của giáo viên">
        <FieldText id="g-score" label="Điểm mới" reg={form.register('score')} error={errors.score} placeholder={max ? `Thang ${max}${speaking ? ', bước 0,5' : ''}` : 'Theo thang chương trình'} />
        <div className="flex flex-col gap-2">
          <label htmlFor="g-comment" className="text-sm font-extrabold text-ink">Nhận xét</label>
          <textarea id="g-comment" rows={4} aria-invalid={!!errors.comment} className="w-full rounded-md border-[1.5px] border-line-input bg-input p-4 font-bold text-ink outline-none focus:border-brand" placeholder="Nhập nhận xét" {...form.register('comment')} />
          {errors.comment && <p role="alert" className="text-sm font-bold text-danger-fg">{errors.comment.message}</p>}
        </div>
      </Section>
      {e && (e instanceof ApiError && e.code === 'grade_not_supported'
        ? <p role="alert" className="font-bold text-danger-fg">Bài giao Nghe ép không có điểm nên không sửa điểm được.</p>
        : e instanceof ApiError && e.code === 'assignment_cancelled' ? null : <WriteError error={e} />)}
      {saved && <p role="status" className="font-bold text-success-fg">Đã lưu nhận xét.</p>}
      <button type="submit" disabled={grade.isPending} className={`${btnPrimary} self-start`}>Lưu nhận xét</button>
    </form>
  )
}
