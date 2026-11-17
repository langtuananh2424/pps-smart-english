import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import cam from '../../assets/checkout/cam-nhay-mung.png'
import { NETWORK_ERROR } from '../../auth/formErrors'
import { ApiError } from '../../lib/api'
import { useCreateOrder, type OrderView } from './api'
import { formatVnd } from './format'
import { btnOutline, btnPrimary, card, Field, PageHead, StatusBadge } from './ui'

// Quyền học đã mở (FR-COM-03).
export function PaidView({ order }: { order: OrderView }) {
  const course = order.items.find((i) => i.course)?.course
  return (
    <div className="flex flex-col gap-4 lg:gap-6">
      <PageHead title="Thanh toán thành công" sub={order.code} />
      <section aria-label="Kết quả thanh toán" className="flex items-center gap-3 rounded-[18px] border border-line bg-success-bg p-5">
        <div className="flex min-w-0 flex-1 flex-col gap-1.5">
          <h2 className="text-[22px] font-black leading-[30px] text-success-fg">Khóa học đã được mở</h2>
          <p className="text-[15px] font-semibold text-ink">
            {course ? `Bạn có thể bắt đầu học ${course.title}.` : 'Quyền lợi đã được cộng vào tài khoản.'}
          </p>
        </div>
        <img src={cam} alt="" className="size-[72px] shrink-0 object-contain lg:size-[92px]" />
      </section>
      <section aria-label="Thông tin đơn" className={card}>
        <StatusBadge status="PAID" />
        <dl className="flex flex-col gap-3">
          <Field label="Gói đã mua">{order.items.map((i) => i.name).join(', ')}</Field>
          <Field label="Tổng thanh toán">{formatVnd(order.totalVnd)}</Field>
          <Field label="Phương thức">Chuyển khoản QR</Field>
          <Field label="Quyền học">Đã mở khóa</Field>
        </dl>
        <div className="flex flex-wrap gap-2">
          {course && <Link to={`/bai-giang/khoa/${course.id}`} className={btnPrimary}>Vào bài giảng</Link>}
          <Link to="/don-hang" className={btnOutline}>Về đơn hàng</Link>
        </div>
      </section>
    </div>
  )
}

// Hết hạn: tạo đơn mới cùng gói (đơn cũ không dùng lại được). Một key cho cả lần xác nhận, bấm lại sau lỗi mạng giữ nguyên.
export function ExpiredView({ order }: { order: OrderView }) {
  const navigate = useNavigate()
  const create = useCreateOrder()
  const key = useRef(crypto.randomUUID())
  const [error, setError] = useState('')
  const retry = () => {
    setError('')
    create.mutate(
      { productIds: order.items.map((i) => i.productId), key: key.current },
      { onSuccess: (o) => navigate(`/don-hang/${o.code}`), onError: (e) => setError(e instanceof ApiError ? e.message : NETWORK_ERROR) },
    )
  }
  return (
    <div className="flex flex-col gap-4 lg:gap-6">
      <PageHead title="Mã thanh toán đã hết hạn" />
      <section className={card}>
        <StatusBadge status="EXPIRED" />
        <p className="text-[17px] font-extrabold text-ink">Tạo đơn mới để nhận mã thanh toán mới.</p>
        <p className="text-[15px] font-semibold text-ink-secondary">Đơn chưa được xác nhận thanh toán.</p>
        {error && <p role="alert" className="text-sm font-bold text-danger">{error}</p>}
        <div className="flex flex-wrap gap-2">
          <button type="button" onClick={retry} disabled={create.isPending} className={btnPrimary}>
            {create.isPending ? 'Đang tạo đơn...' : 'Tạo đơn mới'}
          </button>
          <Link to="/don-hang" className={btnOutline}>Về đơn hàng</Link>
        </div>
        <dl><Field label="Mã đơn">{order.code}</Field></dl>
      </section>
    </div>
  )
}
