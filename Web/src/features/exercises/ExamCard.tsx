import { Link } from 'react-router'
import arrow from '../../assets/icons/arrow-right.svg'
import laptop from '../../assets/exercises/exam-laptop.webp'
import lock from '../../assets/icons/lock.svg'
import { Icon } from '../../components/Icon'
import { btnPrimary } from '../lectures/ui'
import { examView } from './format'
import type { ExamStatus } from './types'

// Bài thi khóa (FR-EXAM-02). Ẩn khi không có đề; khóa thì nút vô hiệu kèm lý do.
export function ExamCard({ status, courseId }: { status: ExamStatus | null | undefined; courseId: number }) {
  const view = examView(status)
  if (view.kind === 'hidden') return null
  const open = view.kind === 'open'
  const btn = 'gap-2 max-lg:size-9 max-lg:min-h-0 max-lg:p-0 max-lg:shadow-none lg:min-h-[50px] lg:px-[22px] lg:text-base'
  return (
    <section aria-label="Bài thi khóa" className="relative flex items-center gap-3 overflow-hidden rounded-md border border-line bg-[#fff1e3] p-3.5 lg:gap-5 lg:rounded-[28px] lg:border-line-subtle lg:px-6 lg:py-[22px]">
      <img src={laptop} alt="" className="size-11 shrink-0 rounded-[10px] object-cover lg:size-14 lg:rounded-md" />
      <div className="min-w-0 flex-1">
        <h2 className="text-base font-black leading-[22px] text-ink lg:text-2xl lg:leading-[30px]">Bài thi khóa</h2>
        <p className="text-xs font-bold text-ink-secondary lg:text-sm">{view.subtitle}</p>
      </div>
      <img src={laptop} alt="" className="pointer-events-none absolute inset-y-0 right-[190px] hidden h-full w-[260px] object-cover opacity-60 [mask-image:linear-gradient(to_right,transparent,#000_30%,#000_70%,transparent)] xl:block" />
      {open ? (
        <Link to={`/bai-tap/${view.bankId}?khoa=${courseId}`} aria-label={view.label} className={`${btnPrimary} ${btn} relative bg-brand-strong shadow-edge-primary`}>
          <span className="max-lg:hidden">{view.label}</span>
          <Icon src={arrow} size={18} />
        </Link>
      ) : (
        <button type="button" disabled aria-label="Bài thi khóa chưa mở" className={`${btn} relative inline-flex cursor-not-allowed items-center justify-center rounded-md bg-muted px-5 py-3 text-lg font-black text-ink-muted`}>
          <span className="max-lg:hidden">Làm bài thi</span>
          <Icon src={lock} size={18} className="lg:hidden" />
        </button>
      )}
    </section>
  )
}
