import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { Skeleton } from '../home/BlockState'
import { isApiError, useOrder, type OrderView } from './api'
import { ExpiredView, PaidView } from './OrderResults'
import { PendingView } from './PendingView'
import { btnOutline, Notice, StatusBadge } from './ui'

// FR-COM-02/03: một route cho mọi trạng thái đơn (PENDING / PAID / EXPIRED / CANCELLED), 404 khi không phải đơn của mình.
export function OrderPage() {
  const { code = '' } = useParams()
  const order = useOrder(code)
  const qc = useQueryClient()
  const status = order.data?.status

  // Vừa PAID: quyền học mới, mọi thứ phụ thuộc quyền phải tải lại.
  useEffect(() => {
    if (status !== 'PAID') return
    for (const k of ['me', 'lesson', 'catalog', 'course']) qc.invalidateQueries({ queryKey: [k] })
  }, [status, qc])

  if (order.isPending) return <Skeleton className="h-72 w-full" />
  if (order.isError)
    return isApiError(order.error, 404) ? (
      <Notice title="Không tìm thấy đơn hàng" body="Đơn hàng không tồn tại hoặc không thuộc tài khoản của bạn.">
        <Link to="/don-hang" className={btnOutline}>Về đơn hàng</Link>
      </Notice>
    ) : (
      <Notice title="Không tải được đơn hàng" body="Vui lòng thử lại.">
        <button type="button" onClick={() => order.refetch()} className={btnOutline}>Thử lại</button>
      </Notice>
    )
  return <ByStatus order={order.data} />
}

function ByStatus({ order }: { order: OrderView }) {
  switch (order.status) {
    case 'PENDING':
      return <PendingView order={order} />
    case 'PAID':
      return <PaidView order={order} />
    case 'EXPIRED':
      return <ExpiredView order={order} />
    default:
      return (
        <Notice title="Đơn hàng đã hủy" body={`Mã đơn ${order.code}.`}>
          <StatusBadge status={order.status} />
          <Link to="/don-hang" className={btnOutline}>Về đơn hàng</Link>
        </Notice>
      )
  }
}
