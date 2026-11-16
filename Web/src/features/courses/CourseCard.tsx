import { Link } from 'react-router'
import { bandRange, formatPrice } from './format'
import type { CourseSummary } from './types'

export type Tone = 'success' | 'brand' | 'reward' | 'plain'
export interface CardAction {
  label: string
  to?: string
  onClick?: () => void
  disabled?: boolean
}
export interface CardView {
  badge?: { text: string; tone: Tone }
  percent?: number
  action: CardAction
  highlight?: boolean
  active?: boolean // đang học: viền brand trên mobile
}

const badgeTone: Record<Tone, string> = {
  success: 'bg-success-bg text-success-fg',
  brand: 'bg-brand-soft text-brand-ink',
  reward: 'bg-reward-bg text-reward-fg',
  plain: 'bg-surface text-ink-secondary',
}
// Desktop khối màu phẳng; mobile (<lg) thumbnail vuông chuyển sắc như Figma.
const thumbTone: Record<Tone, string> = {
  success: 'bg-gradient-to-br from-success-bg to-[#86efac] lg:bg-none lg:bg-success-bg',
  brand: 'bg-gradient-to-br from-[#ffe8d4] to-[#ffc9a0] lg:bg-none lg:bg-subtle',
  reward: 'bg-gradient-to-br from-reward-bg to-[#fcd34d] lg:bg-none lg:bg-reward-bg',
  plain: 'bg-gradient-to-br from-[#e0e7ff] to-[#a5b4fc] lg:bg-none lg:bg-muted',
}
const btn = 'flex min-h-11 items-center justify-center rounded-sm border-2 border-line bg-surface px-4 py-2.5 text-sm font-semibold text-brand-strong shadow-edge-neutral'

// Thẻ khóa theo Figma: desktop thẻ dọc; mobile hàng ngang, cả thẻ bấm được qua nút hành động phủ lên thẻ.
export function CourseCardView({ course, view }: { course: CourseSummary; view: CardView }) {
  const { badge, percent, action, highlight, active } = view
  const tone = badge?.tone ?? 'plain'
  const frame = active
    ? 'border-2 border-brand bg-page lg:border lg:border-line-subtle lg:bg-surface'
    : `border border-line-subtle ${highlight ? 'bg-brand-soft' : 'bg-surface'}`
  // Mobile: nút trong suốt phủ cả thẻ (nhãn vẫn cho trình đọc màn hình); desktop: nút thường ở đáy thẻ.
  const cover =
    'max-lg:absolute max-lg:inset-0 max-lg:rounded-[22px] max-lg:border-0 max-lg:bg-transparent max-lg:text-transparent max-lg:shadow-none max-lg:focus-visible:ring-2 max-lg:focus-visible:ring-brand'
  const btn = `flex min-h-11 items-center justify-center rounded-sm border-2 border-line bg-surface px-4 py-2.5 text-sm font-semibold text-brand-strong shadow-edge-neutral ${cover}`
  return (
    <article className={`relative flex items-center gap-3 rounded-[22px] p-2.5 shadow-edge-card lg:flex-col lg:items-stretch lg:rounded-lg lg:p-5 ${frame}`}>
      <div className={`flex size-[92px] shrink-0 items-start rounded-md lg:h-[104px] lg:w-full lg:items-center lg:p-4 ${thumbTone[tone]}`}>
        <span className="m-1.5 rounded-full bg-white/90 px-2.5 py-[3px] text-xs font-black text-brand-strong lg:m-0 lg:bg-transparent lg:p-0 lg:text-2xl">
          {bandRange(course.bandFrom, course.bandTo)}
        </span>
      </div>
      <div className="flex min-w-0 flex-1 flex-col gap-1 lg:contents">
        {badge && <span className={`self-start rounded-full px-2 py-0.5 text-[11px] font-black lg:px-2.5 lg:py-[3px] lg:text-xs lg:font-extrabold ${badgeTone[tone]}`}>{badge.text}</span>}
        <h3 className="text-base font-black leading-[21px] text-ink lg:text-lg lg:leading-6">{course.title}</h3>
        <p className="line-clamp-2 text-sm font-semibold leading-5 text-ink-secondary lg:line-clamp-none">{course.description}</p>
        {course.priceFromVnd != null && <p className="hidden text-sm font-extrabold text-ink lg:block">{formatPrice(course.priceFromVnd)}</p>}
        {percent != null && (
          <div className="flex items-center gap-2.5">
            <div
              role="progressbar"
              aria-label={`Tiến độ ${course.title}`}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={percent}
              className="h-2 flex-1 overflow-hidden rounded-full bg-muted"
            >
              <div className="h-full rounded-full bg-brand" style={{ width: `${percent}%` }} />
            </div>
            <span aria-hidden="true" className="text-sm font-black text-brand-strong lg:hidden">
              {percent}%
            </span>
          </div>
        )}
        <div className="lg:mt-auto lg:pt-1">
          {action.to ? (
            <Link to={action.to} className={btn}>
              {action.label}
            </Link>
          ) : (
            <button type="button" onClick={action.onClick} disabled={action.disabled} className={`${btn} w-full disabled:opacity-60`}>
              {action.label}
            </button>
          )}
        </div>
      </div>
    </article>
  )
}
