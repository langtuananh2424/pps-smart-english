import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import { applyApiError } from '../../../auth/formErrors'
import { ApiError } from '../../../lib/api'
import { FieldText, inputCls, labelCls, PageHead, Section } from '../ui'
import { cmsWrites, useCmsWrite, type CourseView } from './api'
import { courseUrl, CMS, pillOutline, pillPrimary, StatusPill, writeMessage } from './ui'

const schema = z.object({
  slug: z.string().trim().max(120).regex(/^[a-z0-9]+(-[a-z0-9]+)*$/, 'Chỉ gồm chữ thường, số và dấu gạch ngang'),
  title: z.string().trim().min(1, 'Vui lòng nhập tên khóa học').max(200, 'Tên tối đa 200 ký tự'),
  description: z.string().max(4000, 'Mô tả tối đa 4000 ký tự'),
  targetType: z.string().trim().max(30, 'Tối đa 30 ký tự'),
})
type Values = z.infer<typeof schema>
const FIELDS = ['slug', 'title', 'description', 'targetType']

// FR-ADM-01: tạo khóa học (luôn là bản nháp). Bước kiểm tra là zod phía trước; máy chủ báo thêm 400/409 (slug trùng) tại ô.
export function CourseCreatePage() {
  const [created, setCreated] = useState<CourseView | null>(null)
  const [formError, setFormError] = useState('')
  const write = useCmsWrite(cmsWrites.createCourse)
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { slug: '', title: '', description: '', targetType: '' } })
  const { errors } = form.formState

  const submit = form.handleSubmit(async (v) => {
    setFormError('')
    try {
      setCreated(await write.mutateAsync({ ...v, description: v.description || undefined, targetType: v.targetType || undefined }))
    } catch (e) {
      setFormError(e instanceof ApiError && e.status < 500 && e.status !== 401 && e.status !== 403 ? applyApiError(e, form.setError, FIELDS) : writeMessage(e))
    }
  })

  if (created) {
    return (
      <div className="flex flex-col gap-4">
        <PageHead title="Đã tạo khóa học" sub="Khóa học ở trạng thái bản nháp, chưa hiển thị cho học viên." />
        <Section title={created.title} tone="brand">
          <p className="font-semibold text-ink-secondary">{created.slug}</p>
          <StatusPill status={created.status} />
        </Section>
        <div className="flex flex-wrap gap-3">
          <Link to={courseUrl(created.id)} className={pillPrimary}>
            Soạn nội dung
          </Link>
          <Link to={CMS} className={pillOutline}>
            Về danh sách
          </Link>
        </div>
      </div>
    )
  }
  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      <PageHead title="Tạo khóa học" sub="Nhập thông tin cơ bản để tạo bản nháp." />
      <div className="max-w-[760px]">
        <Section title="Thông tin khóa học">
          <FieldText id="title" label="Tên khóa học *" reg={form.register('title')} error={errors.title} placeholder="Nhập tên khóa học" />
          <FieldText id="slug" label="Slug *" reg={form.register('slug')} error={errors.slug} placeholder="ví dụ: ielts-foundation" />
          <FieldText id="targetType" label="Mục tiêu học (không bắt buộc)" reg={form.register('targetType')} error={errors.targetType} placeholder="Nhập mục tiêu học" />
          <div className="flex flex-col gap-2">
            <label htmlFor="description" className={labelCls}>
              Mô tả (không bắt buộc)
            </label>
            <textarea id="description" rows={3} placeholder="Giới thiệu ngắn về khóa học" className={`${inputCls} py-3`} {...form.register('description')} />
            {errors.description && (
              <p role="alert" className="text-sm font-bold text-danger-fg">
                {errors.description.message}
              </p>
            )}
          </div>
        </Section>
      </div>
      {formError && (
        <p role="alert" className="font-bold text-danger-fg">
          {formError}
        </p>
      )}
      <div className="flex flex-wrap gap-2.5">
        <Link to={CMS} className={pillOutline}>
          Hủy
        </Link>
        <button type="submit" disabled={form.formState.isSubmitting} className={pillPrimary}>
          Tạo bản nháp
        </button>
      </div>
    </form>
  )
}
