import checkWhite from '../../assets/icons/check-white.svg'
import { Icon } from '../../components/Icon'
import { useMeStats } from './api'
import { BlockError, Skeleton } from './BlockState'
import { vnToday, weekDates } from './format'

export function WeekCard() {
  const stats = useMeStats()
  const s = stats.data
  const today = vnToday()
  const days = weekDates(today)
  const studied = new Set(s?.weekDays)
  const todayLabel = days.find((d) => d.date === today)?.label

  let text = ''
  if (s) {
    if (s.activeToday) text = `Hôm nay đã học. Chuỗi hiện tại ${s.streakCurrent} ngày.`
    else if (s.streakCurrent > 0) text = `Hôm nay (${todayLabel}) chưa học. Làm 1 bài để giữ chuỗi ${s.streakCurrent} ngày.`
    else text = 'Hôm nay chưa học. Làm 1 bài để bắt đầu chuỗi ngày học.'
  }

  return (
    <section className="rounded-lg border border-line-subtle bg-surface px-4 py-[22px] shadow-edge-card lg:flex lg:items-center lg:gap-5 lg:px-6">
      <div className="mb-3 min-w-0 flex-1 lg:mb-0">
        <div className="flex items-baseline justify-between gap-2">
          <h2 className="text-base font-black text-ink lg:text-lg lg:leading-6">Tiến độ tuần này</h2>
          {s && <span className="text-sm font-bold text-ink-secondary lg:hidden">{studied.size}/7 ngày</span>}
        </div>
        {s && <p className="text-sm font-semibold leading-5 text-ink-secondary">{text}</p>}
        {stats.isError && <BlockError onRetry={() => stats.refetch()} />}
        {stats.isPending && <Skeleton className="mt-1 h-5 w-3/4" />}
      </div>
      <ul className="flex justify-between gap-[14px]" aria-label="Các ngày trong tuần">
        {days.map(({ label, date }) => {
          const done = studied.has(date)
          const isToday = date === today
          return (
            <li key={date} className="flex flex-col items-center gap-[6px]">
              <span
                data-state={done ? 'done' : isToday ? 'today' : 'idle'}
                className={`grid size-8 place-items-center rounded-full lg:size-[34px] ${
                  done ? 'bg-brand text-white shadow-edge-primary' : isToday ? 'border-2 border-dashed border-brand bg-subtle' : 'bg-muted'
                }`}
              >
                {done && <Icon src={checkWhite} size={16} />}
              </span>
              <span className={`text-xs font-extrabold leading-4 ${isToday && !done ? 'text-brand-strong' : 'text-ink-secondary'}`}>
                {label}
                <span className="sr-only">{done ? ', đã học' : isToday ? ', hôm nay chưa học' : ''}</span>
              </span>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
