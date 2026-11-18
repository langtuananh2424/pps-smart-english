import { Link } from 'react-router'
import bell from '../../assets/icons/bell.svg'
import flame from '../../assets/icons/flame.svg'
import { Icon } from '../../components/Icon'
import { useUnreadCount } from '../notifications/api'
import { useMeStats } from './api'
import { BlockError } from './BlockState'
import { formatXp } from './format'

const chip = 'inline-flex min-h-10 items-center gap-1 rounded-full border-2 border-line px-3 font-black lg:min-h-[46px] lg:px-4 lg:font-extrabold lg:text-base'

export function HomeHeader({ name }: { name: string }) {
  const stats = useMeStats()
  const unread = useUnreadCount()
  const count = unread.data?.count ?? 0
  return (
    <header className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
      <div>
        <h1 className="text-2xl font-black leading-[30px] text-ink">Xin chào, {name}!</h1>
        <p className="font-semibold text-ink-secondary">Cùng chinh phục tiếng Anh mỗi ngày nhé.</p>
      </div>
      <div className="flex items-center gap-2 lg:gap-[10px]">
        {stats.data ? (
          <>
            <span className={`${chip} bg-surface text-ink shadow-edge-card`}>
              <Icon src={flame} size={18} className="text-brand" />
              {stats.data.streakCurrent}
              <span className="max-sm:sr-only">&nbsp;ngày</span>
            </span>
            <span className={`${chip} border-transparent bg-reward-bg text-reward-fg lg:border-line`}>{formatXp(stats.data.xp)}</span>
          </>
        ) : stats.isError ? (
          <BlockError onRetry={() => stats.refetch()} />
        ) : (
          <span aria-hidden="true" className="h-10 w-40 animate-pulse rounded-full bg-muted" />
        )}
        <Link
          to="/thong-bao"
          aria-label={count > 0 ? `Thông báo, ${count} chưa đọc` : 'Thông báo'}
          className="relative grid size-11 place-items-center rounded-full border-2 border-line bg-surface text-ink shadow-edge-card"
        >
          <Icon src={bell} size={20} />
          {count > 0 && (
            <span aria-hidden="true" className="absolute -right-1 -top-1 grid min-w-5 place-items-center rounded-full bg-danger px-1 text-xs font-extrabold leading-5 text-white">
              {count > 99 ? '99+' : count}
            </span>
          )}
        </Link>
      </div>
    </header>
  )
}
