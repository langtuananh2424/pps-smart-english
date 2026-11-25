import { useEffect, useRef, useState } from 'react'
import checkIcon from '../../assets/icons/check.svg'
import { ApiError } from '../../lib/api'
import { btnOutline, btnPrimary, inputCls, labelCls } from './ui'
import { exportOrdersCsv } from './ordersApi'

export const MAX_DAYS = 366
const COLUMNS = ['Mã đơn', 'Thời điểm thanh toán', 'Email người mua', 'Họ tên', 'Tên sản phẩm', 'Loại sản phẩm', 'Thành tiền (VND)', 'Cổng thanh toán', 'Mã giao dịch cổng']

// Số ngày tính cả hai đầu; ISO date so theo UTC nên không lệch múi giờ.
const spanDays = (from: string, to: string) => Math.round((Date.parse(to) - Date.parse(from)) / 86_400_000) + 1

export function validateRange(from: string, to: string): { from?: string; to?: string } {
  const e: { from?: string; to?: string } = {}
  if (!from) e.from = 'Vui lòng chọn ngày bắt đầu'
  if (!to) e.to = 'Vui lòng chọn ngày kết thúc'
  if (from && to) {
    if (to < from) e.to = 'Ngày kết thúc phải sau hoặc bằng ngày bắt đầu'
    else if (spanDays(from, to) > MAX_DAYS) e.to = `Khoảng xuất tối đa ${MAX_DAYS} ngày`
  }
  return e
}

// D-38: chỉ đơn đã thanh toán, lọc theo ngày thanh toán; không có ô lý do, backend tự ghi nhật ký EXPORT.
export function ExportCsvDialog({ onClose }: { onClose: () => void }) {
  const ref = useRef<HTMLDivElement>(null)
  const [f, setF] = useState({ from: '', to: '' })
  const [errors, setErrors] = useState<{ from?: string; to?: string }>({})
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)

  useEffect(() => {
    ref.current?.focus()
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && !busy && onClose()
    window.addEventListener('keydown', esc)
    return () => window.removeEventListener('keydown', esc)
  }, [busy, onClose])

  async function submit() {
    const e = validateRange(f.from, f.to)
    setErrors(e)
    setFailure(null)
    if (e.from || e.to) return
    setBusy(true)
    try {
      const { blob, filename } = await exportOrdersCsv(f.from, f.to)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      a.click()
      URL.revokeObjectURL(url)
      setDone(true)
    } catch (err) {
      setFailure(err instanceof ApiError ? (err.status === 403 ? 'Bạn không có quyền xuất đơn hàng.' : err.message) : 'Không thể xuất file. Kiểm tra kết nối rồi thử lại.')
    } finally {
      setBusy(false)
    }
  }

  const field = (key: 'from' | 'to', label: string) => (
    <div className="flex min-w-0 flex-col gap-2">
      <label htmlFor={`x-${key}`} className={labelCls}>
        {label}
      </label>
      <input
        id={`x-${key}`}
        type="date"
        value={f[key]}
        aria-invalid={!!errors[key]}
        aria-describedby={errors[key] ? `x-${key}-err` : undefined}
        onChange={(e) => {
          setF((s) => ({ ...s, [key]: e.target.value }))
          setDone(false)
        }}
        className={inputCls}
      />
      {errors[key] && (
        <p id={`x-${key}-err`} role="alert" className="text-sm font-bold text-danger-fg">
          {errors[key]}
        </p>
      )}
    </div>
  )

  const shell = (children: React.ReactNode) => (
    <div className="fixed inset-0 z-30 grid place-items-center overflow-y-auto bg-ink/50 p-4">
      <div ref={ref} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby="export-title" className="flex w-full max-w-lg flex-col gap-4 rounded-lg bg-surface p-5 outline-none md:p-6">
        {children}
      </div>
    </div>
  )

  // Figma 586:15625: xuất xong thì thu gọn hộp thoại.
  if (done) {
    return shell(
      <>
        <header className="flex items-center justify-between gap-3">
          <h2 id="export-title" className="text-2xl font-black text-ink">
            Xuất CSV
          </h2>
          <button type="button" onClick={onClose} aria-label="Đóng hộp thoại" className="inline-flex size-11 items-center justify-center rounded-full text-xl font-black text-ink-secondary hover:bg-subtle">
            ×
          </button>
        </header>
        <div role="status" className="flex flex-col items-center gap-2 py-4 text-center">
          <span className="grid size-14 place-items-center rounded-full bg-success-bg">
            <img src={checkIcon} alt="" className="size-7" />
          </span>
          <p className="text-lg font-black text-ink">Đã xuất CSV</p>
          <p className="font-semibold text-ink-secondary">Trình duyệt đã bắt đầu tải file về.</p>
        </div>
        <button type="button" onClick={onClose} className={`${btnOutline} self-center`}>
          Đóng
        </button>
      </>,
    )
  }

  return shell(
    <>
        <header className="flex flex-col gap-1">
          <h2 id="export-title" className="text-2xl font-black text-ink">
            Xuất CSV đơn đã thanh toán
          </h2>
          <p className="font-semibold text-ink-secondary">Chọn khoảng ngày thanh toán, tối đa {MAX_DAYS} ngày.</p>
        </header>
        <div className="grid gap-3 sm:grid-cols-2">
          {field('from', 'Từ ngày')}
          {field('to', 'Đến ngày')}
        </div>
        <section aria-label="Các cột trong file" className="flex flex-col gap-1.5 rounded-md bg-subtle p-4">
          <h3 className="font-extrabold text-ink">Các cột trong file</h3>
          <p className="text-sm font-semibold text-ink-secondary">Mỗi sản phẩm trong đơn là một dòng.</p>
          <ul className="flex flex-wrap gap-1.5">
            {COLUMNS.map((c) => (
              <li key={c} className="rounded-full bg-surface px-2.5 py-1 text-[13px] font-bold text-ink-secondary">
                {c}
              </li>
            ))}
          </ul>
        </section>
        <section aria-label="Nhật ký" className="rounded-md bg-reward-bg p-4">
          <p className="font-extrabold text-ink">Sẽ ghi nhật ký</p>
          <p className="text-sm font-semibold text-ink-secondary">Mỗi lần xuất được ghi với thao tác EXPORT.</p>
        </section>
        {failure && (
          <p role="alert" className="font-bold text-danger-fg">
            {failure}
          </p>
        )}
        <div className="flex justify-end gap-3">
          <button type="button" onClick={onClose} disabled={busy} className={btnOutline}>
            Hủy
          </button>
          <button type="button" onClick={submit} disabled={busy} className={btnPrimary}>
            {busy ? 'Đang xuất…' : 'Xuất CSV'}
          </button>
        </div>

    </>,
  )
}
