import { useState } from 'react'
import { Link } from 'react-router'
import chevronDown from '../../assets/lectures/chevron-down.svg'
import chevronUp from '../../assets/lectures/chevron-up.svg'
import dotDone from '../../assets/lectures/dot-done.svg'
import dotCurrent from '../../assets/lectures/dot-current.svg'
import dotTodo from '../../assets/lectures/dot-todo.svg'
import lock from '../../assets/icons/lock.svg'
import { Icon } from '../../components/Icon'
import type { RoadmapLesson, RoadmapTopic } from '../home/types'
import { LessonCard } from './LessonCard'
import { noSkill, skillMeta } from './skills'
import { lessonStatus, percentOf, topicBadge, type LessonStatus } from './status'
import { btnNeutral, chip, chipTone } from './ui'

const dot: Record<LessonStatus, string> = { DONE: dotDone, WATCHING: dotCurrent, NEW: dotTodo }
const COLLAPSED_MOBILE = 3

// Đường lộ trình: mobile một cột lệch phải; desktop hai cột so le, đường nét đứt ở giữa.
function Timeline({ lessons, banks }: { lessons: RoadmapLesson[]; banks: Map<number, RoadmapTopic['banks'][number]> }) {
  const [all, setAll] = useState(false)
  const extra = lessons.length - COLLAPSED_MOBILE
  return (
    <div className="flex flex-col gap-4">
      <ol className="relative flex flex-col gap-4 pl-6 lg:grid lg:grid-cols-2 lg:gap-x-12 lg:gap-y-5 lg:pb-[72px] lg:pl-0">
        <span aria-hidden="true" className="absolute bottom-10 left-[11px] top-10 border-l border-dashed border-[#8f9ca1] lg:bottom-[90px] lg:left-1/2 lg:top-[75px]" />
        {lessons.map((l, i) => {
          const right = i % 2 === 1
          const side = right ? 'lg:left-[-28.5px]' : 'lg:left-auto lg:right-[-28.5px]'
          return (
            <li key={l.id} className={`relative ${right ? 'lg:-mb-[72px] lg:mt-[72px]' : ''} ${!all && i >= COLLAPSED_MOBILE ? 'max-lg:hidden' : ''}`}>
              <img src={dot[lessonStatus(l)]} alt="" className={`absolute left-[-17px] top-1/2 size-2 -translate-y-1/2 lg:size-[9px] ${side}`} />
              <span aria-hidden="true" className={`absolute left-[-13px] top-1/2 w-[13px] border-t border-dashed border-[#8f9ca1] lg:w-6 ${right ? 'lg:left-[-24px]' : 'lg:left-auto lg:right-[-24px]'}`} />
              <LessonCard lesson={l} bank={banks.get(l.id)} />
            </li>
          )
        })}
      </ol>
      {!all && extra > 0 && (
        <button type="button" onClick={() => setAll(true)} className="min-h-11 self-center text-sm font-extrabold text-brand-strong lg:hidden">
          Xem thêm {extra} bài
        </button>
      )}
    </div>
  )
}

interface Props {
  topic: RoadmapTopic
  lessons: RoadmapLesson[]
  open: boolean
  onToggle: () => void
  courseSlug: string
  courseId: number
}

export function TopicSection({ topic, lessons, open, onToggle, courseSlug, courseId }: Props) {
  const meta = topic.skill ? skillMeta[topic.skill] : noSkill
  const badge = topicBadge(topic)
  const locked = topic.locked || topic.status === 'LOCKED'
  const banks = new Map(topic.banks.map((b) => [b.lessonId, b]))
  const bodyId = `topic-${topic.id}`

  const head = (
    <>
      <span className={`grid size-12 shrink-0 place-items-center rounded-sm ${meta.bg} max-lg:size-10`}>
        <img src={meta.icon} alt="" width={22} height={22} />
      </span>
      <span className="flex min-w-0 flex-1 flex-col items-start gap-1 text-left">
        <span className="flex flex-wrap items-center gap-2">
          <span className="text-lg font-black leading-6 text-ink">{topic.title}</span>
          <span className={`${chip} ${chipTone[badge.tone]}`}>{badge.text}</span>
        </span>
        <span className="text-sm font-semibold leading-5 text-ink-secondary">{topic.totalLessons} bài giảng</span>
        <span className="h-1.5 w-60 max-w-full overflow-hidden rounded-full bg-muted max-lg:hidden">
          <span className="block h-full rounded-full bg-gradient-to-r from-brand to-[#ff8a3d]" style={{ width: `${percentOf(topic.completedLessons, topic.totalLessons)}%` }} />
        </span>
      </span>
    </>
  )

  return (
    <section className="rounded-lg border-2 border-line bg-surface p-5 shadow-edge-card max-lg:p-4">
      <div className="flex items-center gap-3.5">
        {locked ? (
          <>
            <div className="flex min-w-0 flex-1 items-center gap-3.5">{head}</div>
            {topic.lockReason === 'SEQUENTIAL' ? (
              <Link to={`/bai-giang/khoa/${courseId}/unit/${topic.id}`} className={`${btnNeutral} gap-2`}>
                <Icon src={lock} size={16} />
                Xem điều kiện mở
              </Link>
            ) : (
              <Link to={`/thanh-toan?khoa=${courseSlug}`} className={`${btnNeutral} gap-2`}>
                <Icon src={lock} size={16} />
                Mua khóa học
              </Link>
            )}
          </>
        ) : (
          <button type="button" onClick={onToggle} aria-expanded={open} aria-controls={bodyId} className="flex min-h-12 min-w-0 flex-1 items-center gap-3.5 rounded-sm">
            {head}
            <span className="grid size-9 shrink-0 place-items-center rounded-sm bg-page">
              <img src={open ? chevronUp : chevronDown} alt="" width={18} height={18} />
            </span>
          </button>
        )}
      </div>
      {!locked && open && (
        <div id={bodyId} className="mt-4">
          {lessons.length ? <Timeline lessons={lessons} banks={banks} /> : <p className="text-sm font-semibold text-ink-secondary">Chưa có bài giảng.</p>}
        </div>
      )}
    </section>
  )
}
