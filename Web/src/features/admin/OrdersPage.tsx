import { useState } from 'react'
import { Link } from 'react-router'
import { STATUS_BADGE, formatVnd } from '../checkout/format'
import { StatusBadge } from '../checkout/ui'
import { ExportCsvDialog } from './ExportCsvDialog'
import { useAdminOrders, type OrderFilters } from './ordersApi'
import { useDebounced } from './useDebounced'
import { btnPrimary, headCls, inputCls, labelCls, Loading, LoadMore, PageHead, RetryError, Section } from './ui'

const COLS = 'md:grid-cols-[1.3fr_1fr_1fr_1.6fr_0.8fr_1.2fr_1.2fr_auto]'
export const fmtTime = (iso: string | null) => (iso ? new Date(iso).toLocaleString('vi-VN') : '—')

// FR-ADM-03, D-38: danh sách đơn cho nhân sự (order.view); đơn mới nhất trước.
export function OrdersPage() {
  const [f, setF] = useState<OrderFilters>({ status: '', from: '', to: '', email: '' })
  const set = (k: keyof OrderFilters) => (e: { target: { value: string } }) => setF((s) => ({ ...s, [k]: e.target.value }))
  const d = useDebounced(f)
  const list = useAdminOrders({ ...d, email: d.email.trim() })
  const rows = list.data?.pages.flatMap((p) => p.data) ?? []
  const [exporting, setExporting] = useState(false)

  return (
    <>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <PageHead title="Danh sách đơn hàng" sub="Đơn mới nhất hiển thị trước." />
        <button type="button" onClick={() => setExporting(true)} className={btnPrimary}>
          Xuất CSV
        </button>
      </div>
      <Section title="Bộ lọc">
        <div className="grid gap-3 md:grid-cols-4">
          <div className="flex min-w-0 flex-col gap-2">
            <label htmlFor="o-status" className={labelCls}>
              Trạng thái
            </label>
            <select id="o-status" value={f.status} onChange={set('status')} className={inputCls}>
              <option value="">Tất cả</option>
              {(['PAID', 'PENDING', 'EXPIRED', 'CANCELLED'] as const).map((s) => (
                <option key={s} value={s}>
                  {STATUS_BADGE[s].label}
                </option>
              ))}
            </select>
          </div>
          <div className="flex min-w-0 flex-col gap-2">
            <label htmlFor="o-from" className={labelCls}>
              Từ ngày tạo
            </label>
            <input id="o-from" type="date" value={f.from} onChange={set('from')} className={inputCls} />
          </div>
          <div className="flex min-w-0 flex-col gap-2">
            <label htmlFor="o-to" className={labelCls}>
              Đến ngày tạo
            </label>
            <input id="o-to" type="date" value={f.to} onChange={set('to')} className={inputCls} />
          </div>
          <div className="flex min-w-0 flex-col gap-2">
            <label htmlFor="o-email" className={labelCls}>
              Email người mua
            </label>
            <input id="o-email" value={f.email} onChange={set('email')} placeholder="Nhập một phần email" className={inputCls} />
          </div>
        </div>
        <p className="text-sm font-semibold text-ink-secondary">Khoảng ngày tạo đơn tính cả ngày bắt đầu và ngày kết thúc.</p>
      </Section>

      <Section title="Đơn hàng">
        {list.isPending ? (
          <Loading text="Đang tải đơn hàng…" />
        ) : list.isError ? (
          <RetryError error={list.error} onRetry={() => list.refetch()} title="Không thể tải đơn hàng" />
        ) : rows.length === 0 ? (
          <p className="font-semibold text-ink-secondary">Không có đơn hàng nào khớp bộ lọc.</p>
        ) : (
          <>
            <div className={`${headCls} ${COLS}`}>
              <span>Mã đơn</span>
              <span>Trạng thái</span>
              <span>Tổng (VND)</span>
              <span>Người mua</span>
              <span>Cổng thanh toán</span>
              <span>Thời điểm tạo</span>
              <span>Thanh toán lúc</span>
              <span aria-hidden />
            </div>
            <ul>
              {rows.map((o) => (
                <li key={o.id} className={`grid gap-1 border-b border-line-brand p-2.5 last:border-b-0 md:items-center md:gap-4 ${COLS}`}>
                  <Link to={`/quan-tri/don-hang/${o.id}`} className="font-extrabold text-brand-strong underline">
                    {o.code}
                  </Link>
                  <StatusBadge status={o.status} />
                  <span className="font-extrabold text-ink">{formatVnd(o.totalVnd)}</span>
                  <span className="min-w-0 break-words font-semibold text-ink-secondary">
                    {o.displayName && <span className="block font-extrabold text-ink">{o.displayName}</span>}
                    {o.email}
                  </span>
                  <span className="font-semibold text-ink-secondary">{o.provider ?? '—'}</span>
                  <span className="font-semibold text-ink-secondary">{fmtTime(o.createdAt)}</span>
                  <span className="font-semibold text-ink-secondary">{fmtTime(o.paidAt)}</span>
                  <span aria-hidden className="hidden font-black text-ink-secondary md:block">
                    &gt;
                  </span>
                </li>
              ))}
            </ul>
          </>
        )}
      </Section>
      {list.hasNextPage && <LoadMore onClick={() => list.fetchNextPage()} busy={list.isFetchingNextPage} />}
      {exporting && <ExportCsvDialog onClose={() => setExporting(false)} />}
    </>
  )
}
