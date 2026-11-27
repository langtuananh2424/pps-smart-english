import type { ReactNode } from 'react'
import { Link } from 'react-router'
import aiBadge from '../../assets/icons/ai-badge.svg'
import { Icon } from '../../components/Icon'
import { useMyCourses, useToday } from './api'
import { BlockError, Skeleton } from './BlockState'
import { currentCourse, formatMmSs } from './format'
import type { TodayTask } from './types'

const KIND = {
  VIDEO: { title: () => 'Xem video', sub: (t: TodayTask) => t.title, path: (t: TodayTask) => t.lessonId != null && `/bai-giang/${t.lessonId}` },
  EXERCISE: {
    title: () => 'Làm bài tập',
    sub: (t: TodayTask) => (t.questionCount != null ? `${t.title} · ${t.questionCount} câu` : t.title),
    path: (t: TodayTask) => t.bankId != null && `/bai-tap/${t.bankId}`,
  },
  WRITING: { title: () => 'Viết phát triển ý', sub: (t: TodayTask) => t.subtitle ?? t.title, path: (t: TodayTask) => t.questionId != null && `/viet/${t.questionId}` },
}

const chipCls = 'self-start rounded-full px-3 py-[5px] text-xs font-extrabold leading-4'

function Chip({ task, first }: { task: TodayTask; first: boolean }) {
  if (task.doneToday) return <span className={`${chipCls} bg-success-bg text-success-fg`}>Xong</span>
  if (task.kind === 'WRITING')
    return (
      <span className={`${chipCls} inline-flex items-center gap-1.5 bg-ai-bg text-ai-edge`}>
        <Icon src={aiBadge} size={12} />
        Cam
      </span>
    )
  if (task.kind === 'VIDEO' && task.remainingSec && task.remainingSec > 0)
    return <span className={`${chipCls} bg-surface text-brand-ink`}>Đang học · còn {formatMmSs(task.remainingSec)}</span>
  return first ? null : <span className={`${chipCls} bg-muted text-ink-secondary`}>Tiếp theo</span>
}

function TaskCard({ task, first }: { task: TodayTask; first: boolean }) {
  const k = KIND[task.kind]
  const path = k.path(task)
  const cls = `flex min-h-[110px] flex-col items-start gap-2.5 rounded-[22px] border border-line-subtle p-[18px] ${
    first ? 'bg-brand-soft' : task.kind === 'WRITING' ? 'bg-ai-bg shadow-edge-card' : 'bg-surface shadow-edge-card'
  }`
  const inner: ReactNode = (
    <>
      <Chip task={task} first={first} />
      <span className="text-lg font-extrabold leading-6 text-ink">{k.title()}</span>
      <span className="text-sm leading-[21px] text-ink-secondary">{k.sub(task)}</span>
    </>
  )
  // Thiếu id đích thì không có đường dẫn hợp lệ, hiện thẻ không bấm được.
  return <li>{path ? <Link to={path} className={cls}>{inner}</Link> : <div className={cls}>{inner}</div>}</li>
}

export function TodayTasks() {
  const courses = useMyCourses()
  const courseId = currentCourse(courses.data)?.courseId
  const today = useToday(courseId)
  if (courseId == null || (!today.isError && today.data?.tasks.length === 0)) return null

  const tasks = today.data?.tasks ?? []
  const firstOpen = tasks.findIndex((t) => !t.doneToday)
  return (
    <section>
      <div className="mb-[10px] flex items-baseline justify-between gap-2">
        <h2 className="text-lg font-black leading-6 text-ink xl:text-2xl xl:leading-[30px]">Việc hôm nay</h2>
        {today.data && (
          <span className="text-sm font-bold text-ink-secondary">
            {today.data.doneCount}/{today.data.total} hoàn thành
          </span>
        )}
      </div>
      {today.isError ? (
        <BlockError onRetry={() => today.refetch()} />
      ) : today.isPending ? (
        <Skeleton className="h-[126px] w-full" />
      ) : (
        <ul className="flex flex-col gap-[14px]">
          {tasks.map((t, i) => (
            <TaskCard key={`${t.kind}-${i}`} task={t} first={i === firstOpen} />
          ))}
        </ul>
      )}
    </section>
  )
}