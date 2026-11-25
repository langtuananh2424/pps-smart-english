import type { InputHTMLAttributes } from 'react'
import { ApiError } from '../../../../lib/api'
import { inputCls, labelCls } from '../../ui'

export const STATUS_LABEL: Record<string, string> = { PUBLISHED: 'Đã xuất bản', DRAFT: 'Bản nháp' }
export const bankPath = (id: number) => `/quan-tri/cms/de-thi/${id}`

// Lỗi ghi: thông điệp máy chủ kèm chi tiết từng trường (error.details là map trường -> lý do).
export function ErrorText({ error }: { error: unknown }) {
  if (!error) return null
  if (!(error instanceof ApiError)) {
    return (
      <p role="alert" className="font-bold text-danger-fg">
        Có lỗi xảy ra, vui lòng thử lại.
      </p>
    )
  }
  const details = error.details && typeof error.details === 'object' ? Object.values(error.details as Record<string, unknown>).map(String) : []
  return (
    <div role="alert" className="font-bold text-danger-fg">
      <p>{error.status === 403 ? 'Bạn không có quyền thực hiện thao tác này.' : error.message}</p>
      {details.map((d) => (
        <p key={d}>{d}</p>
      ))}
    </div>
  )
}

export function Field({ label, hint, ...rest }: { label: string; hint?: string } & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="flex min-w-0 flex-1 flex-col gap-2">
      <span className={labelCls}>{label}</span>
      <input {...rest} className={inputCls} />
      {hint && <span className="text-sm font-semibold text-ink-secondary">{hint}</span>}
    </label>
  )
}
