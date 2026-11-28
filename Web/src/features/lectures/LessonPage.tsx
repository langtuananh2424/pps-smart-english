import { useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { Skeleton } from '../home/BlockState'
import { useRoadmap } from '../home/api'
import { useCatalogDetail, useLesson, useLessonProgress, type GlossaryEntry, type LessonDetail, type LessonProgress } from './api'
import { FinishedCard } from './FinishedCard'
import { LessonError } from './LessonStates'
import { fromCatalog, fromRoadmap, LessonSidebar, type TopicView } from './LessonSidebar'
import type { LessonStatus } from './status'
import { btnNeutral, chip, chipTone } from './ui'
import { useProgressReporting } from './useProgressReporting'
import { SubtitleBlock } from './SubtitleBlock'
import { VideoPlayer, type PlayerControl } from './VideoPlayer'

const EMPTY: GlossaryEntry[] = []

const statusChip: Record<LessonStatus, { text: string; tone: keyof typeof chipTone }> = {
  DONE: { text: 'Đã xem xong', tone: 'success' },
  WATCHING: { text: 'Đang xem', tone: 'brand' },
  NEW: { text: 'Chưa xem', tone: 'plain' },
}

function Watch({ lesson, progress, topic, bankId, reload }: { lesson: LessonDetail; progress?: LessonProgress; topic: TopicView | null; bankId: number | null; reload: () => void }) {
  const { user } = useAuth()
  // Vị trí bắt đầu chốt một lần: tiến độ cập nhật khi đang xem không được làm video nhảy.
  const [startAt] = useState(() => (progress && !progress.completed ? progress.maxPositionSec : 0))
  const position = useRef<() => number>(() => 0)
  const { onPlay, onPause } = useProgressReporting(lesson.id, !!user && !!lesson.video, position)
  const [played, setPlayed] = useState(false)
  const [ended, setEnded] = useState(false)
  const [failed, setFailed] = useState(false)
  const retried = useRef(false)
  // Phụ đề: thời gian phát đi từ trình phát sang SubtitleBlock; playingRef để popup biết có cần phát tiếp.
  const timeRef = useRef<(sec: number) => void>(() => {})
  const control = useRef<PlayerControl | null>(null)
  const playingRef = useRef(false)

  // URL ký hết hạn: lấy lại bài đúng một lần rồi mới báo lỗi.
  const onMediaError = () => {
    if (lesson.video?.source === 'YOUTUBE' || retried.current) return setFailed(true)
    retried.current = true
    reload()
  }

  const status: LessonStatus = progress?.completed || ended ? 'DONE' : played || (progress?.maxPositionSec ?? 0) > 0 ? 'WATCHING' : 'NEW'
  const current = topic?.lessons.find((l) => l.id === lesson.id)
  const heading = current ? `Bài ${current.order} · ${lesson.title}` : lesson.title
  const listHref = `/bai-giang/khoa/${lesson.courseId}`

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-center justify-between gap-3">
        <Link to={listHref} aria-label="Quay lại danh sách bài" className="grid size-11 place-items-center rounded-md text-xl font-black text-ink lg:hidden">
          ←
        </Link>
        <nav aria-label="Đường dẫn" className="min-w-0 flex-1 text-sm font-semibold leading-5 text-ink-secondary">
          <span className="hidden lg:inline">
            <Link to="/bai-giang">Bài giảng</Link>
            {'  /  '}
            <Link to={listHref}>{lesson.courseTitle}</Link>
            {'  /  '}
            {lesson.topicTitle}
          </span>
          <span className="block truncate text-xs font-bold text-ink-muted lg:hidden">{lesson.courseTitle}</span>
          <span className="block truncate lg:hidden">{lesson.topicTitle}</span>
        </nav>
        <Link to={listHref} className={`${btnNeutral} hidden rounded-md lg:inline-flex`}>
          ← Danh sách bài giảng
        </Link>
      </div>

      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_380px] lg:gap-6">
        <div className="flex flex-col gap-5">
          <VideoPlayer
            video={lesson.video}
            startAt={startAt}
            positionRef={position}
            failed={failed}
            onPlay={() => {
              playingRef.current = true
              setPlayed(true)
              onPlay()
            }}
            onPause={() => {
              playingRef.current = false
              onPause()
            }}
            onTime={(s) => timeRef.current(s)}
            controlRef={control}
            onEnded={() => setEnded(true)}
            onMediaError={onMediaError}
          />
          {lesson.video && lesson.subtitles && (
            <SubtitleBlock subtitles={lesson.subtitles} glossary={lesson.glossary ?? EMPTY} timeRef={timeRef} control={control} playingRef={playingRef} />
          )}
          <header className="flex flex-col gap-2">
            <div className="flex flex-wrap gap-2">
              <span className={`${chip} ${chipTone[statusChip[status].tone]}`}>{statusChip[status].text}</span>
              <span className={`${chip} ${chipTone.plain}`}>{lesson.topicTitle}</span>
            </div>
            <h1 className="text-2xl font-black leading-[30px] text-ink lg:text-[30px] lg:leading-[38px]">{heading}</h1>
          </header>
          {status === 'DONE' && <FinishedCard order={current?.order ?? null} bankId={bankId} listHref={listHref} />}
        </div>
        {topic && <LessonSidebar topic={topic} currentId={lesson.id} currentStatus={user ? status : null} />}
      </div>
    </div>
  )
}

function LessonView({ id }: { id: number }) {
  const { user } = useAuth()
  const lesson = useLesson(id)
  const progress = useLessonProgress(id, !!user)
  const roadmap = useRoadmap(user ? lesson.data?.courseId : undefined)
  const catalog = useCatalogDetail(lesson.data?.courseSlug, !user)

  if (lesson.isError) return <LessonError error={lesson.error} onRetry={() => lesson.refetch()} />
  if (lesson.isPending || (user && progress.isPending)) return <Skeleton className="mt-4 aspect-video w-full max-w-3xl" />

  const d = lesson.data
  const rTopic = roadmap.data?.levels.flatMap((l) => l.topics).find((t) => t.id === d.topicId)
  const topic = rTopic ? fromRoadmap(rTopic) : catalog.data ? fromCatalog(catalog.data, d.topicId) : null
  const bank = rTopic?.banks.find((b) => b.lessonId === d.id)
  return <Watch lesson={d} progress={progress.data} topic={topic} bankId={bank?.id ?? null} reload={() => lesson.refetch()} />
}

export function LessonPage() {
  const id = Number(useParams().lessonId)
  return <LessonView key={id} id={id} />
}
