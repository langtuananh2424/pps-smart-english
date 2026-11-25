import { Link, useParams } from 'react-router'
import { ApiError } from '../../lib/api'
import { formatVnd } from '../checkout/format'
import { Field, StatusBadge } from '../checkout/ui'
import { fmtTime } from './OrdersPage'
import { useMyPermissions } from './api'
import { useAdminOrder } from './ordersApi'
import { headCls, Loading, PageHead, RetryError, Section } from './ui'

const TYPE: Record<string, string> = { LEVEL: 'Gói khóa học (LEVEL)', AI_ADDON: 'Gói lượt AI (AI_ADDON)' }
const RESULT: Record<string, string> = {
  APPLIED: 'Đã ghi nhận',
  MISMATCH: 'Sai số tiền',
  ORDER_NOT_FOUND: 'Không tìm thấy đơn',
  ORDER_CANCELLED: 'Đơn đã hủy',
  ORDER_ALREADY_PAID: 'Đơn đã thanh toán trước đó',
}

// D-38: chi tiết đơn chỉ để xem (order.view). Không có raw_payload.
// Link "Tra cứu học viên" (FR-SUP-03) chỉ hiện khi có quyền student.view.
export function OrderDetailPage() {
  const { id = '' } = useParams()
  const q = useAdminOrder(id)
  const o = q.data
  const canLookup = useMyPermissions().data?.has('student.view')

  return (
    <>
      <Link to="/quan-tri/don-hang" className="inline-flex min-h-11 items-center self-start font-extrabold text-brand-strong underline">
        Danh sách đơn hàng
      </Link>
      <PageHead title="Chi tiết đơn hàng" />
      {q.isPending ? (
        <Loading text="Đang tải đơn hàng…" />
      ) : q.error instanceof ApiError && q.error.status === 404 ? (
        <p role="alert" className="font-bold text-ink-secondary">
          Không tìm thấy đơn hàng.
        </p>
      ) : q.isError || !o ? (
        <RetryError error={q.error} onRetry={() => q.refetch()} title="Không thể tải đơn hàng" />
      ) : (
        <>
          <Section title="Thông tin đơn">
            <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <Field label="Mã đơn">{o.code}</Field>
              <Field label="Trạng thái">
                <StatusBadge status={o.status} />
              </Field>
              <Field label="Tổng tiền (VND)">{formatVnd(o.totalVnd)}</Field>
              <Field label="Cổng thanh toán">{o.provider ?? '—'}</Field>
              <Field label="Thời điểm tạo">{fmtTime(o.createdAt)}</Field>
              <Field label="Hạn thanh toán">{fmtTime(o.expiresAt)}</Field>
              <Field label="Thời điểm thanh toán">{o.paidAt ? fmtTime(o.paidAt) : ''}</Field>
            </dl>
          </Section>
          <Section title="Người mua">
            <dl className="grid gap-3 sm:grid-cols-2">
              <Field label="Họ tên">{o.buyer.displayName ?? '—'}</Field>
              <Field label="Email">{o.buyer.email}</Field>
            </dl>
            {canLookup && (
              <Link to={`/quan-tri/hoc-vien/${o.buyer.id}`} className="inline-flex min-h-11 items-center self-start font-extrabold text-brand-strong underline">
                Tra cứu học viên
              </Link>
            )}
          </Section>
          <Section title="Sản phẩm">
            <div className={`${headCls} md:grid-cols-4`}>
              <span>Tên sản phẩm</span>
              <span>Loại sản phẩm</span>
              <span>Khóa học kèm theo</span>
              <span>Giá lúc mua (VND)</span>
            </div>
            <ul>
              {o.items.map((i, n) => (
                <li key={n} className="grid gap-1 border-b border-line-brand p-2.5 last:border-b-0 md:grid-cols-4 md:items-center md:gap-4">
                  <span className="font-extrabold text-ink">{i.productName}</span>
                  <span className="font-semibold text-ink-secondary">{TYPE[i.productType] ?? i.productType}</span>
                  <span className="font-semibold text-ink-secondary">{i.courseTitle ?? '—'}</span>
                  <span className="font-extrabold text-ink">{formatVnd(i.priceVnd)}</span>
                </li>
              ))}
            </ul>
          </Section>
          <Section title="Giao dịch thanh toán">
            <p className="font-semibold text-ink-secondary">Các lần cổng thanh toán gửi kết quả cho đơn này.</p>
            {o.transactions.length === 0 ? (
              <p className="font-semibold text-ink-secondary">Chưa có giao dịch nào.</p>
            ) : (
              <>
                <div className={`${headCls} md:grid-cols-5`}>
                  <span>Thời điểm nhận</span>
                  <span>Cổng thanh toán</span>
                  <span>Mã giao dịch cổng</span>
                  <span>Số tiền (VND)</span>
                  <span>Kết quả</span>
                </div>
                <ul>
                  {o.transactions.map((t, n) => (
                    <li key={n} className="grid gap-1 border-b border-line-brand p-2.5 last:border-b-0 md:grid-cols-5 md:items-center md:gap-4">
                      <span className="font-semibold text-ink-secondary">{fmtTime(t.receivedAt)}</span>
                      <span className="font-semibold text-ink-secondary">{t.provider}</span>
                      <span className="break-all font-semibold text-ink-secondary">{t.providerRef}</span>
                      <span className="font-extrabold text-ink">{formatVnd(t.amountVnd)}</span>
                      <span className="font-extrabold text-ink">{RESULT[t.status] ?? t.status}</span>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </Section>
        </>
      )}
    </>
  )
}
