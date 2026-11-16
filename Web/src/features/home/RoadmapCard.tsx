import type { ReactNode } from 'react'
import { Link } from 'react-router'
import check from '../../assets/icons/check.svg'
import chevron from '../../assets/icons/chevron-right.svg'
import lock from '../../assets/icons/lock.svg'
import { Icon } from '../../components/Icon'
import { useMyCourses, useRoadmap } from './api'
import { BlockError, Skeleton } from './BlockState'
import { pickLevel } from './format'
import type { RoadmapTopic, TopicStatus } from './types'

const SKIN: Record<TopicStatus, { card: string; badge: string; chip?: string }> = {
  DONE: { card: 'bg-success-bg', badge: 'bg-success-bg text-success-fg' },
  IN_PROGRESS: { card: 'bg-brand-soft', badge: 'bg-brand text-white', chip: 'bg-surface text-brand-ink' },
  NOT_STARTED: { card: 'bg-surface', badge: 'bg-muted text-ink-secondary' },
  LOCKED: { card: 'bg-muted', badge: 'bg-muted text-ink-secondary', chip: 'bg-reward-bg text-reward-fg' },
}

function subtitle(t: RoadmapTopic) {
  const progress = `${t.completedLessons}/${t.totalLessons} bài`
  if (t.status === 'DONE') return t.xpEarned > 0 ? `Hoàn thành · +${t.xpEarned} XP` : 'Hoàn thành'
  if (t.status === 'IN_PROGRESS') return `Đang học · ${progress}`
  if (t.status === 'LOCKED') return 'Mở khóa khi mua khóa học'
  return `Chưa bắt đầu · ${progress}`
}

const chipText: Partial<Record<TopicStatus, string>> = { IN_PROGRESS: 'Đang học', LOCKED: 'Trả phí' }

function TopicItem({ topic, index }: { topic: RoadmapTopic; index: number }) {
  const skin = SKIN[topic.status]
  let badge: ReactNode = index + 1
  if (topic.status === 'DONE') badge = <Icon src={check} size={18} />
  if (topic.status === 'LOCKED') badge = <Icon src={lock} size={18} />
  const right = index % 2 === 1
  return (
    // Desktop xen kẽ trái/phải quanh trục giữa; mobile xếp dọc.
    <li className={`relative flex min-h-[72px] items-center gap-[14px] rounded-[20px] border border-line-subtle px-[14px] py-3 lg:w-[calc(50%-15px)] ${skin.card} ${right ? 'lg:ml-auto' : ''}`}>
      <span aria-hidden="true" className={`absolute top-1/2 hidden h-px w-[15px] border-t border-dashed border-brand-strong/40 lg:block ${right ? '-left-[15px]' : '-right-[15px]'}`} />
      <span className={`grid size-10 shrink-0 place-items-center rounded-[14px] text-base font-black ${skin.badge}`}>{badge}</span>
      <div className="min-w-0 flex-1">
        <p className={`truncate text-base font-extrabold leading-[22px] ${topic.status === 'LOCKED' ? 'text-ink-secondary' : 'text-ink'}`}>{topic.title}</p>
        <p className="text-sm font-semibold leading-5 text-ink-secondary">{subtitle(topic)}</p>
      </div>
      {skin.chip && <span className={`shrink-0 rounded-full px-3 py-[5px] text-xs font-extrabold leading-4 ${skin.chip}`}>{chipText[topic.status]}</span>}
    </li>
  )
}

export function RoadmapCard() {
  const courses = useMyCourses()
  const course = courses.data?.[0]
  const roadmap = useRoadmap(course?.courseId)
  if (!courses.isError && !courses.isPending && !course) return null

  const level = roadmap.data && pickLevel(roadmap.data)
  let body
  if (courses.isError) body = <BlockError onRetry={() => courses.refetch()} />
  else if (roadmap.isError) body = <BlockError onRetry={() => roadmap.refetch()} />
  else if (!level) body = <Skeleton className="h-48 w-full" />
  else
    body = (
      <ol aria-label={`Các chủ đề của ${level.title}`} className="relative flex flex-col gap-4 lg:gap-[14px]">
        <span aria-hidden="true" className="absolute inset-y-9 left-1/2 hidden border-l border-dashed border-brand-strong/40 lg:block" />
        {level.topics.map((t, i) => (
          <TopicItem key={t.id} topic={t} index={i} />
        ))}
      </ol>
    )

  return (
    <section className="rounded-[28px] border border-line-subtle bg-surface px-4 py-[22px] shadow-edge-card lg:px-6">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-lg font-black leading-6 text-ink">Lộ trình{level ? ` · ${level.title}` : ''}</h2>
        {course && (
          <Link to={`/khoa-hoc/${course.slug}`} className="inline-flex min-h-11 items-center gap-1 text-sm font-extrabold text-brand-strong">
            Xem chi tiết
            <Icon src={chevron} size={14} />
          </Link>
        )}
      </div>
      {body}
    </section>
  )
}
