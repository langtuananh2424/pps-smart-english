import { Link } from 'react-router'
import type { RoadmapBank, RoadmapTopic } from '../home/types'
import type { CourseDetail } from '../courses/types'
import { formatClock, lessonStatus, percentOf, remainingSec, statusLabel, type LessonStatus } from './status'

export interface SideLesson {
  id: number
  title: string
  order: number
  durationSec: number | null
  status: LessonStatus | null // khách không có trạng thái
  remaining: number | null
}

export interface TopicView {
  title: string
  lessons: SideLesson[]
  banks: RoadmapBank[]
}

export function fromRoadmap(t: RoadmapTopic): TopicView {
  return {
    title: t.title,
    banks: t.banks,
    lessons: t.lessons.map((l) => ({
      id: l.id,
      title: l.title,
      order: l.order,
      durationSec: l.durationSec,
      status: lessonStatus(l),
      remaining: remainingSec(l.durationSec, l.maxPositionSec),
    })),
  }
}

// Khách: danh mục công khai không có trạng thái, thứ tự theo vị trí.
export function fromCatalog(d: CourseDetail, topicId: number): TopicView | null {
  const t = d.levels.flatMap((l) => l.topics).find((x) => x.id === topicId)
  if (!t) return null
  return {
    title: t.title,
    banks: [],
    lessons: t.lessons.map((l, i) => ({ id: l.id, title: l.title, order: i + 1, durationSec: l.durationSec, status: null, remaining: null })),
  }
}

const tone: Record<LessonStatus | 'GUEST', string> = {
  DONE: 'bg-success-bg',
  WATCHING: 'bg-brand-soft',
  NEW: 'bg-surface',
  GUEST: 'bg-surface',
}

function caption(l: SideLesson) {
  const status = l.status
  const parts = [l.durationSec != null ? formatClock(l.durationSec) : null]
  if (status === 'WATCHING' && l.remaining != null && l.remaining > 0) parts.push(`Còn ${formatClock(l.remaining)}`)
  else if (status) parts.push(statusLabel[status])
  return parts.filter(Boolean).join(' · ')
}

// `currentStatus` là trạng thái đang cập nhật của bài đang xem, nhanh hơn roadmap đã tải.
export function LessonSidebar({ topic, currentId, currentStatus }: { topic: TopicView; currentId: number; currentStatus: LessonStatus | null }) {
  const withLive = topic.lessons.map((l) => (l.id === currentId && currentStatus ? { ...l, status: currentStatus } : l))
  const guest = withLive.every((l) => l.status == null)
  const done = withLive.filter((l) => l.status === 'DONE').length
  return (
    <aside
      aria-label="Bài trong chủ đề"
      className="flex flex-col gap-3 lg:self-start lg:rounded-[28px] lg:border lg:border-line-subtle lg:bg-[#eaf1f4] lg:p-5 lg:shadow-edge-card"
    >
      <h2 className="text-lg font-black leading-6 text-ink">
        {topic.title}
        <span className="lg:hidden">{guest ? '' : ` · Đã xem ${done} / ${withLive.length} bài`}</span>
        <span className="hidden lg:inline"> · {withLive.length} bài</span>
      </h2>
      {!guest && (
        <div className="hidden flex-col gap-3 lg:flex">
          <p className="text-xs font-extrabold leading-4 text-ink-muted">
            Đã xem {done} / {withLive.length} bài
          </p>
          <div className="h-2 overflow-hidden rounded-full bg-muted">
            <div className="h-full rounded-full bg-brand" style={{ width: `${percentOf(done, withLive.length)}%` }} />
          </div>
        </div>
      )}
      <ol className="flex flex-col gap-3 max-lg:gap-4">
        {withLive.map((l) => {
          const current = l.id === currentId
          return (
            <li key={l.id}>
              <Link
                to={`/bai-giang/${l.id}`}
                aria-current={current ? 'true' : undefined}
                className={`flex min-h-16 items-center gap-3 rounded-[18px] border border-line-subtle p-3 ${tone[l.status ?? 'GUEST']}`}
              >
                <span className={`grid size-8 shrink-0 place-items-center rounded-full text-xs font-extrabold ${current ? 'bg-brand text-white' : 'bg-muted text-ink-secondary'}`}>
                  {l.order}
                </span>
                <span className="flex min-w-0 flex-col gap-0.5">
                  <span className="text-sm font-semibold leading-5 text-ink">
                    Bài {l.order} · {l.title}
                  </span>
                  <span className="text-xs font-extrabold leading-4 text-ink-muted">{caption(l)}</span>
                </span>
              </Link>
            </li>
          )
        })}
      </ol>
    </aside>
  )
}
