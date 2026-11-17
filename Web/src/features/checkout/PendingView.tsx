import { useEffect, useState } from 'react'
import QRCode from 'qrcode'
import { Link } from 'react-router'
import { ApiError } from '../../lib/api'
import { useSimulatePay, type OrderView } from './api'
import { formatCountdown, formatVnd } from './format'
import { btnOutline, card, Field, PageHead, Pill } from './ui'

function useCountdown(expiresAt: string) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(t)
  }, [])
  return new Date(expiresAt).getTime() - now
}

// QR vẽ ở client từ chuỗi backend trả về (D-78).
function Qr({ content }: { content: string }) {
  const [src, setSrc] = useState('')
  useEffect(() => {
    let live = true
    QRCode.toDataURL(content, { margin: 1, width: 360 }).then(
      (u) => live && setSrc(u),
      () => live && setSrc(''),
    )
    return () => {
      live = false
    }
  }, [content])
  return src ? (
    <img src={src} alt="Mã QR thanh toán" className="size-[184px] rounded-md border border-line bg-white" />
  ) : (
    <div aria-hidden="true" className="size-[184px] animate-pulse rounded-md bg-muted" />
  )
}

export function PendingView({ order }: { order: OrderView }) {
  const left = useCountdown(order.expiresAt)
  const pay = useSimulatePay(order.code)
  const payment = order.payment
  return (
    <div className="flex flex-col gap-4 lg:gap-6">
      <PageHead title="Thanh toán đơn hàng" sub="Quét mã bằng ứng dụng ngân hàng để thanh toán." />
      <div className="grid gap-4 lg:grid-cols-2">
        <section aria-label="Mã QR" className={`${card} items-center`}>
          <Pill className="bg-reward-bg text-ink">Đang chờ thanh toán</Pill>
          {payment && <Qr content={payment.qrContent} />}
          <p role="timer" className="self-start text-sm font-semibold text-ink-secondary">
            {left > 0 ? (
              <>Mã hết hạn sau <b className="font-extrabold text-ink">{formatCountdown(left)}</b></>
            ) : (
              'Mã đã hết hạn, đang cập nhật...'
            )}
          </p>
        </section>
        <div className="flex flex-col gap-3">
          <section aria-label="Thông tin chuyển khoản" className={card}>
            <dl className="flex flex-col gap-3">
              <Field label="Số tiền">{formatVnd(order.totalVnd)}</Field>
              <Field label="Nội dung chuyển khoản">{order.transferContent}</Field>
              <Field label="Mã đơn">{order.code}</Field>
            </dl>
            <p className="text-[15px] font-semibold text-ink-secondary">
              Giữ nguyên số tiền và nội dung. Quyền học sẽ mở sau khi giao dịch được xác nhận.
            </p>
            <Link to="/don-hang" className={btnOutline}>Xem đơn hàng</Link>
          </section>
          {payment?.simulatable && (
            <section aria-label="Giả lập thanh toán" className="flex flex-col gap-3 rounded-md border border-line bg-ai-bg p-3.5">
              <p className="text-sm font-extrabold text-ai-edge">Môi trường thử: giả lập thanh toán</p>
              <button type="button" onClick={() => pay.mutate()} disabled={pay.isPending} className={`${btnOutline} self-start`}>
                {pay.isPending ? 'Đang xử lý...' : 'Giả lập đã thanh toán'}
              </button>
              {pay.isError && (
                <p role="alert" className="text-sm font-bold text-danger">
                  {pay.error instanceof ApiError ? pay.error.message : 'Không giả lập được.'}
                </p>
              )}
            </section>
          )}
        </div>
      </div>
    </div>
  )
}
