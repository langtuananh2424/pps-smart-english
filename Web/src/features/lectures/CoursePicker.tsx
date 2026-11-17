import { useState } from 'react'
import { Link } from 'react-router'
import lock from '../../assets/icons/lock.svg'
import { Icon } from '../../components/Icon'
import searchIcon from '../../assets/lectures/search.svg'
import { fold } from '../courses/format'
import { BlockError, Skeleton } from '../home/BlockState'
import { useMyCourses } from '../home/api'
import type { MyCourse } from '../home/types'
import { courseChip, percentOf } from './status'
import { btnNeutral, chip, chipTone } from './ui'

// Màu nền thẻ khóa đang học luân phiên như Figma; khóa xong nền trắng.
const frames = ['bg-brand-soft', 'bg-[#eaf1f4]']

export interface TileAction {
  to: string
  label: string
  /** Thay chip trạng thái khi có (vd độ chính xác); không có thì dùng chip trạng thái khóa. */
  badge?: string
  /** Dòng phụ dưới thanh tiến độ. */
  note?: string
}

export function CourseTile({ c, index, action }: { c: MyCourse; index: number; action: (c: MyCourse) => TileAction }) {
  const act = action(c)
  const expired = c.status === 'EXPIRED'
  const chipView = courseChip(c)
  const done = chipView.tone === 'success'
  const percent = percentOf(c.completedLessons, c.totalLessons)
  const frame = expired || done ? 'bg-surface' : frames[index % frames.length]
  const thumb = done ? 'bg-success-bg' : 'bg-subtle'
  const slug = `/khoa-hoc/${c.slug}`
  return (
    <article className={`flex gap-3.5 rounded-lg border border-line-subtle p-3.5 shadow-edge-card lg:flex-col lg:gap-3 lg:p-5 ${frame}`}>
      <div className={`grid size-[68px] shrink-0 place-items-center rounded-md px-1 text-center text-xs font-extrabold leading-4 text-brand-strong lg:size-auto lg:h-[104px] lg:justify-items-start lg:px-4 lg:text-2xl lg:font-black lg:leading-[30px] ${thumb}`}>
        {expired && <Icon src={lock} size={20} className="lg:hidden" />}
        <span className={expired ? 'max-lg:hidden' : ''}>{c.targetType}</span>
      </div>
      <div className="flex min-w-0 flex-1 flex-col items-start gap-1.5 lg:gap-3">
        <div className="flex flex-wrap gap-1.5">
          <span className={`${chip} ${act.badge ? chipTone.brand : chipTone[chipView.tone]}`}>{act.badge ?? chipView.text}</span>
          {c.status === 'TRIAL' && <span className={`${chip} bg-reward-bg text-reward-fg`}>Học thử</span>}
        </div>
        <h2 className="text-lg font-black leading-6 text-ink">{c.title}</h2>
        {!expired && (
          <div
            role="progressbar"
            aria-label={`Tiến độ ${c.title}`}
            aria-valuemin={0}
            aria-valuemax={c.totalLessons}
            aria-valuenow={c.completedLessons}
            className="h-2 w-full overflow-hidden rounded-full bg-muted"
          >
            <div className="h-full rounded-full bg-brand" style={{ width: `${percent}%` }} />
          </div>
        )}
        {act.note && !expired && <p className="text-xs font-extrabold leading-4 text-ink-muted">{act.note}</p>}
        {expired ? (
          <Link to={slug} className={`${btnNeutral} lg:w-full`}>
            Gia hạn
          </Link>
        ) : (
          <Link to={act.to} className={`${btnNeutral} lg:w-full`}>
            {act.label}
          </Link>
        )}
      </div>
    </article>
  )
}

export function MoreTile() {
  return (
    <section className="flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed border-line px-4 py-5 text-center lg:min-h-[300px] lg:gap-3 lg:border lg:border-line-subtle">
      <h2 className="text-lg font-black leading-6 text-ink">Muốn học thêm?</h2>
      <p className="text-sm font-semibold leading-5 text-ink-secondary">Xem tất cả khóa học và đăng ký khóa mới.</p>
      <Link to="/khoa-hoc" className={`${btnNeutral} mt-1`}>
        Đến trang Khóa học →
      </Link>
    </section>
  )
}


// Màn chọn khóa dùng chung cho Bài giảng và Bài tập: tiêu đề, ô tìm, lưới thẻ khóa.
export function CoursePicker({ title, subtitle, action }: { title: string; subtitle: string; action: (c: MyCourse) => TileAction }) {
  const courses = useMyCourses()
  const [q, setQ] = useState('')
  const shown = (courses.data ?? []).filter((c) => !q.trim() || fold(c.title).includes(fold(q.trim())))

  return (
    <div className="flex flex-col gap-4 lg:gap-6">
      <header className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="text-[30px] font-black leading-[38px] text-ink">{title}</h1>
          <p className="text-sm font-semibold leading-5 text-ink-secondary lg:text-base lg:leading-6">{subtitle}</p>
        </div>
        <label className="hidden min-h-12 w-[300px] items-center gap-2.5 rounded-md border-2 border-line bg-surface px-4 lg:flex">
          <img src={searchIcon} alt="" width={20} height={20} />
          <input
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Tìm khóa học…"
            aria-label="Tìm khóa học"
            className="min-w-0 flex-1 bg-transparent font-bold text-ink outline-none placeholder:text-[#757575]"
          />
        </label>
      </header>

      {courses.isError ? (
        <BlockError onRetry={() => courses.refetch()} />
      ) : courses.isPending ? (
        <div className="grid gap-4 lg:grid-cols-2">
          <Skeleton className="h-36 lg:h-80" />
          <Skeleton className="h-36 lg:h-80" />
        </div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2 lg:gap-5">
          {shown.map((c, i) => (
            <CourseTile key={c.courseId} c={c} index={i} action={action} />
          ))}
          {!shown.length && courses.data.length > 0 && <p className="font-bold text-ink-secondary">Không có khóa nào khớp.</p>}
          <MoreTile />
        </div>
      )}
    </div>
  )
}
