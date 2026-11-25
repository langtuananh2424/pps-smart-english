import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router'
import searchIcon from '../../assets/lectures/search.svg'
import { BlockError, Skeleton } from '../home/BlockState'
import { useMyCourses, useRoadmap } from '../home/api'
import type { Skill } from '../home/types'
import { ResumeHero } from './ResumeHero'
import { filterTopics, skillLabel, skillsIn, statusLabel, type LessonFilter, type LessonStatus } from './status'
import { TopicSection } from './TopicSection'
import { btnNeutral } from './ui'

const statuses: LessonStatus[] = ['WATCHING', 'NEW', 'DONE']

function Tab({ on, onClick, children }: { on: boolean; onClick: () => void; children: string }) {
  return (
    <button
      type="button"
      aria-pressed={on}
      onClick={onClick}
      className={`min-h-11 shrink-0 rounded-full border-2 px-3.5 text-sm font-extrabold ${on ? 'border-brand bg-[#fff8f1] text-brand-strong' : 'border-line bg-surface text-ink-secondary'}`}
    >
      {children}
    </button>
  )
}

export function CoursePage() {
  const courseId = Number(useParams().courseId)
  const courses = useMyCourses()
  const roadmap = useRoadmap(courseId)
  const [f, setF] = useState<LessonFilter>({ query: '', skill: null, status: null })
  const [searching, setSearching] = useState(false)
  const [toggled, setToggled] = useState<Record<number, boolean>>({})

  const course = courses.data?.find((c) => c.courseId === courseId)
  const levels = roadmap.data?.levels
  const resume = roadmap.data?.resume ?? null
  const topics = useMemo(() => levels?.flatMap((l) => l.topics) ?? [], [levels])
  const skills = useMemo(() => skillsIn(levels ?? []), [levels])
  const rows = useMemo(() => filterTopics(topics, f), [topics, f])
  const narrowed = !!f.query.trim() || !!f.status

  // Mặc định mở topic đang học; đang tìm/lọc thì mở hết để thấy kết quả.
  const defaultOpen = resume?.topicId ?? topics.find((t) => t.status === 'IN_PROGRESS')?.id
  const isOpen = (id: number) => narrowed || (toggled[id] ?? id === defaultOpen)

  if (courses.isSuccess && !course)
    return (
      <div className="flex flex-col items-start gap-3">
        <h1 className="text-2xl font-black text-ink">Bạn chưa có khóa học này</h1>
        <Link to="/bai-giang" className={btnNeutral}>
          Chọn khóa khác
        </Link>
      </div>
    )

  const searchBox = (
    <label className={`min-h-12 items-center gap-2.5 rounded-md border-2 border-line bg-surface px-4 lg:flex lg:w-[300px] ${searching ? 'flex' : 'hidden'}`}>
      <img src={searchIcon} alt="" width={20} height={20} />
      <input
        type="search"
        value={f.query}
        onChange={(e) => setF({ ...f, query: e.target.value })}
        placeholder="Tìm bài giảng…"
        aria-label="Tìm bài giảng"
        className="min-w-0 flex-1 bg-transparent font-bold text-ink outline-none placeholder:text-[#757575]"
      />
    </label>
  )

  return (
    <div className="flex flex-col gap-4 lg:gap-6">
      <header className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex items-center gap-3 lg:block">
          <Link to="/bai-giang" aria-label="Về danh sách khóa học" className="grid size-11 shrink-0 place-items-center rounded-md text-xl font-black text-ink lg:hidden">
            ←
          </Link>
          <div className="min-w-0 flex-1 lg:flex-none">
            <nav aria-label="Đường dẫn" className="truncate text-xs font-bold text-ink-muted lg:text-sm">
              <Link to="/bai-giang" className="max-lg:hidden">
                Bài giảng
              </Link>
              <span className="max-lg:hidden">{' / '}</span>
              <span className="font-extrabold text-ink-secondary lg:text-ink">{course?.title ?? ' '}</span>
            </nav>
            <h1 className="text-2xl font-black leading-[30px] text-ink lg:text-[30px] lg:leading-[38px]">
              <span className="lg:hidden">Bài giảng</span>
              <span className="max-lg:hidden">Hệ thống bài giảng</span>
            </h1>
          </div>
          <button type="button" aria-label="Tìm bài giảng" aria-expanded={searching} onClick={() => setSearching((s) => !s)} className="grid size-11 place-items-center rounded-md lg:hidden">
            <img src={searchIcon} alt="" width={20} height={20} />
          </button>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          {course && (
            <span className="hidden rounded-full bg-brand-soft px-3.5 py-2 text-sm font-semibold text-brand-ink lg:inline">
              Tiến độ khóa · {course.completedLessons} / {course.totalLessons} bài
            </span>
          )}
          <Link to="/bai-giang" className={`${btnNeutral} hidden rounded-md lg:inline-flex`}>
            Đổi khóa học
          </Link>
          {searchBox}
        </div>
      </header>

      {roadmap.isError ? (
        <BlockError onRetry={() => roadmap.refetch()} />
      ) : roadmap.isPending ? (
        <div className="flex flex-col gap-4">
          <Skeleton className="h-40 w-full lg:h-56" />
          <Skeleton className="h-24 w-full" />
        </div>
      ) : (
        <>
          {resume && <ResumeHero resume={resume} />}
          <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex gap-2 overflow-x-auto pb-1" role="group" aria-label="Lọc theo kỹ năng">
              <Tab on={!f.skill} onClick={() => setF({ ...f, skill: null })}>
                Mọi kỹ năng
              </Tab>
              {skills.map((s: Skill) => (
                <Tab key={s} on={f.skill === s} onClick={() => setF({ ...f, skill: s })}>
                  {skillLabel[s]}
                </Tab>
              ))}
            </div>
            <div className="flex gap-2 overflow-x-auto pb-1" role="group" aria-label="Lọc theo trạng thái">
              <Tab on={!f.status} onClick={() => setF({ ...f, status: null })}>
                Mọi trạng thái
              </Tab>
              {statuses.map((s) => (
                <Tab key={s} on={f.status === s} onClick={() => setF({ ...f, status: s })}>
                  {statusLabel[s]}
                </Tab>
              ))}
            </div>
          </div>
          <div className="flex flex-col gap-4 pb-8">
            {rows.map(({ topic, lessons }) => (
              <TopicSection key={topic.id} topic={topic} lessons={lessons} open={isOpen(topic.id)} onToggle={() => setToggled({ ...toggled, [topic.id]: !isOpen(topic.id) })} courseSlug={course?.slug ?? ''} courseId={courseId} />
            ))}
            {!rows.length && <p className="font-bold text-ink-secondary">Không có bài giảng nào khớp bộ lọc.</p>}
          </div>
        </>
      )}
    </div>
  )
}
