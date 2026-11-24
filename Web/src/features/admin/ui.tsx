import type { ReactNode } from 'react'
import type { FieldError, UseFormRegisterReturn } from 'react-hook-form'
import { z } from 'zod'
import cam from '../../assets/cam-chao.png'
import { NETWORK_ERROR } from '../../auth/formErrors'
import { ApiError } from '../../lib/api'
import { btnOutline, btnPrimary } from '../checkout/ui'

export { btnOutline, btnPrimary }
export const inputCls =
  'min-h-[52px] w-full min-w-0 rounded-md border-[1.5px] border-line-input bg-input px-4 font-bold text-ink outline-none placeholder:text-[#757575] focus:border-brand'
export const labelCls = 'text-sm font-extrabold text-ink'

export const reasonSchema = z.object({ reason: z.string().trim().min(1, 'Vui lòng nhập lý do thay đổi').max(500, 'Lý do tối đa 500 ký tự') })

export function PageHead({ title, sub }: { title: string; sub?: string }) {
  return (
    <header className="flex flex-col gap-1.5">
      <p className="text-sm font-semibold text-brand-strong">Quản trị</p>
      <h1 className="text-2xl font-black leading-[30px] text-ink lg:text-3xl lg:leading-[38px]">{title}</h1>
      {sub && <p className="font-semibold text-ink-secondary">{sub}</p>}
    </header>
  )
}

export function Section({ title, children, tone = 'plain' }: { title: string; children: ReactNode; tone?: 'plain' | 'brand' }) {
  return (
    <section
      className={`flex flex-col gap-2.5 rounded-lg border border-line-brand p-5 shadow-edge-card ${tone === 'brand' ? 'bg-subtle' : 'bg-surface'}`}
    >
      <h2 className={`text-lg font-black leading-6 ${tone === 'brand' ? 'text-brand-strong' : 'text-ink'}`}>{title}</h2>
      {children}
    </section>
  )
}

export function Loading({ text = 'Đang tải...' }: { text?: string }) {
  return (
    <p role="status" className="font-bold text-ink-secondary">
      {text}
    </p>
  )
}

// Lỗi tải: 403 báo không có quyền, 4xx khác hiện thông điệp máy chủ (thử lại vô ích); lỗi mạng/5xx mới có nút Thử lại.
export function RetryError({ onRetry, error }: { onRetry: () => void; error?: unknown }) {
  if (error instanceof ApiError && error.status < 500) {
    return (
      <p role="alert" className="font-bold text-ink-secondary">
        {error.status === 403 ? 'Bạn không có quyền xem nội dung này.' : error.message}
      </p>
    )
  }
  return (
    <p role="alert" className="font-bold text-ink-secondary">
      {NETWORK_ERROR}{' '}
      <button type="button" onClick={onRetry} className="min-h-11 font-extrabold text-brand-strong underline">
        Thử lại
      </button>
    </p>
  )
}

export const isLastAdmin = (e: unknown) => e instanceof ApiError && e.status === 409 && e.code === 'last_admin'

// Lỗi lệnh ghi: 403 không có quyền, lỗi mạng kèm thử lại, còn lại hiện thông điệp của máy chủ (400, 409 role_taken...).
export function WriteError({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  if (!(error instanceof ApiError)) return <RetryError onRetry={onRetry ?? (() => {})} />
  return (
    <p role="alert" className="font-bold text-danger-fg">
      {error.status === 403 ? 'Bạn không có quyền thực hiện thao tác này.' : error.message}
    </p>
  )
}

interface FieldTextProps {
  id: string
  label: string
  reg: UseFormRegisterReturn
  error?: FieldError
  placeholder?: string
  hint?: string
}

export function FieldText({ id, label, reg, error, placeholder, hint }: FieldTextProps) {
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={id} className={labelCls}>
        {label}
      </label>
      <input id={id} placeholder={placeholder} aria-invalid={!!error} aria-describedby={error ? `${id}-err` : undefined} className={inputCls} {...reg} />
      {hint && !error && <p className="text-sm font-semibold text-ink-secondary">{hint}</p>}
      {error && (
        <p id={`${id}-err`} role="alert" className="text-sm font-bold text-danger-fg">
          {error.message}
        </p>
      )}
    </div>
  )
}

// Khối "Nhật ký" của mọi form ghi RBAC: lý do bắt buộc (Figma 482:15786).
export const ReasonSection = ({ reg, error }: { reg: UseFormRegisterReturn; error?: FieldError }) => (
  <>
    <Section title="Nhật ký">
      <FieldText id="reason" label="Lý do · Bắt buộc" reg={reg} error={error} placeholder="Nhập lý do thay đổi" />
    </Section>
    <p className="font-semibold text-ink-secondary">Sẽ ghi nhật ký.</p>
  </>
)

export const LoadMore = ({ onClick, busy }: { onClick: () => void; busy: boolean }) => (
  <button type="button" onClick={onClick} disabled={busy} className={`${btnOutline} self-start`}>
    {busy ? 'Đang tải...' : 'Tải thêm'}
  </button>
)

// Figma 482:16225: thao tác bị chặn vì sẽ gỡ quyền quản trị cuối cùng.
export function LastAdminBlock({ onBack }: { onBack: () => void }) {
  return (
    <section role="alert" className="mx-auto mt-6 flex w-full max-w-[400px] flex-col items-center gap-3 rounded-lg border border-line-brand bg-page p-6 text-center shadow-edge-card">
      <img src={cam} alt="" className="size-28 rounded-full bg-danger-bg object-contain" />
      <span className="rounded-full bg-danger-bg px-2.5 py-[5px] text-[13px] font-extrabold text-danger-fg">Cập nhật bị chặn</span>
      <p className="text-sm font-semibold text-ink-secondary">Quản trị</p>
      <h2 className="text-2xl font-black leading-[30px] text-ink">Không thể gỡ quyền quản trị cuối cùng</h2>
      <p className="font-semibold text-ink-secondary">Cần giữ ít nhất một người có quyền quản trị.</p>
      <p className="font-semibold text-ink-secondary">Gán quyền quản trị cho một nhân sự khác trước khi tiếp tục.</p>
      <button type="button" onClick={onBack} className={btnPrimary}>
        Quay lại phân quyền
      </button>
    </section>
  )
}

// Hàng bảng: cột đều nhau trên desktop, xếp dọc trên mobile.
export const rowCls = 'grid gap-1 border-b border-line-brand p-2.5 last:border-b-0 md:grid-cols-3 md:items-center md:gap-4'
export const headCls = 'hidden gap-4 rounded-sm bg-muted p-2.5 text-[15px] font-extrabold text-ink-secondary md:grid md:grid-cols-3'
