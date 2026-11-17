import { Link } from 'react-router'
import { BlockError, Skeleton } from '../home/BlockState'
import { useMyOrders } from './api'
import { formatVnd } from './format'
import { btnOutline, card, PageHead, StatusBadge } from './ui'

// D-75: lịch sử đơn của học viên, phân trang con trỏ.
export function OrdersPage() {
  const q = useMyOrders()
  const orders = q.data?.pages.flatMap((p) => p.data) ?? []
  return (
    <div className="flex flex-col gap-4 lg:gap-6">
      <PageHead title="Đơn hàng của tôi" sub="Theo dõi giao dịch và trạng thái mở khóa." />
      {q.isPending ? (
        <Skeleton className="h-40 w-full" />
      ) : q.isError ? (
        <BlockError onRetry={() => q.refetch()} />
      ) : orders.length === 0 ? (
        <section className={card}>
          <h2 className="text-xl font-black text-ink">Bạn chưa có đơn hàng nào</h2>
          <p className="font-semibold text-ink-secondary">Chọn một khóa học để bắt đầu.</p>
          <Link to="/khoa-hoc" className={`${btnOutline} self-start`}>Khóa học</Link>
        </section>
      ) : (
        <ul className="flex flex-col overflow-hidden rounded-[18px] border border-line bg-surface lg:rounded-lg">
          {orders.map((o) => (
            <li key={o.code} className="flex flex-wrap items-center gap-x-2.5 gap-y-2 border-b border-line p-5 last:border-b-0 max-lg:p-4">
              <div className="flex min-w-0 flex-1 basis-48 flex-col gap-[5px] break-words">
                <span className="text-[17px] font-extrabold leading-[23px] text-ink">{o.code}</span>
                <span className="text-sm font-semibold text-ink-secondary">
                  {o.items.map((i) => i.name).join(', ')} · {formatVnd(o.totalVnd)}
                </span>
              </div>
              <StatusBadge status={o.status} />
              <Link to={`/don-hang/${o.code}`} className={btnOutline}>
                {o.status === 'PENDING' ? 'Tiếp tục thanh toán' : 'Xem chi tiết'}
              </Link>
            </li>
          ))}
        </ul>
      )}
      {q.hasNextPage && (
        <button type="button" onClick={() => q.fetchNextPage()} disabled={q.isFetchingNextPage} className={`${btnOutline} self-start`}>
          {q.isFetchingNextPage ? 'Đang tải...' : 'Tải thêm'}
        </button>
      )}
      <Link to="/ho-so" className={`${btnOutline} self-start`}>Về hồ sơ</Link>
    </div>
  )
}
