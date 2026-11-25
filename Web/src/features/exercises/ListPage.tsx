import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { BlockError, Skeleton } from '../home/BlockState'
import { useMyCourses, useQuota, useRoadmap } from '../home/api'
import { btnNeutral } from '../lectures/ui'
import { useExamStatus, useExerciseStats } from './api'
import { CamCard } from './CamCard'
import { EmptyCard } from './EmptyCard'
import { ExamCard } from './ExamCard'
import { Header } from './Header'
import { LectureList } from './LectureList'
import { PracticeBanner } from './PracticeBanner'
import { StatTiles } from './StatTiles'
import { TypeGroups } from './TypeGroups'

// FR-EX-04, FR-EXAM-02, D-62..D-64: hệ thống bài tập của một khóa.
export function ExerciseListPage() {
  const courseId = Number(useParams().courseId)
  const courses = useMyCourses()
  const stats = useExerciseStats(courseId)
  const exam = useExamStatus(courseId)
  const roadmap = useRoadmap(courseId)
  const quota = useQuota()
  const [query, setQuery] = useState('')

  const noBanks = !!stats.data && !!roadmap.data && stats.data.byType.length === 0 && !roadmap.data.levels.some((l) => l.topics.some((t) => t.banks.length > 0))
  const course = courses.data?.find((c) => c.courseId === courseId)
  if (courses.isSuccess && !course)
    return (
      <div className="flex flex-col items-start gap-3">
        <h1 className="text-2xl font-black text-ink">Bạn chưa có khóa học này</h1>
        <Link to="/bai-tap" className={btnNeutral}>
          Chọn khóa khác
        </Link>
      </div>
    )

  return (
    <div className="flex flex-col gap-4 lg:gap-6">
      <Header courseTitle={course?.title ?? ''} answered={stats.data?.answered ?? null} accuracy={stats.data?.accuracy ?? null} query={query} onQuery={setQuery} />
      {stats.isError ? (
        <BlockError onRetry={() => stats.refetch()} />
      ) : stats.isPending ? (
        <Skeleton className="h-32" />
      ) : (
        <>
          <CamCard weakest={stats.data.weakest} />
          <StatTiles stats={stats.data} exam={exam.data} />
        </>
      )}
      <ExamCard status={exam.data} courseId={courseId} />
      {noBanks && <EmptyCard courseId={courseId} />}
      {roadmap.data && <LectureList roadmap={roadmap.data} query={query} />}
      <PracticeBanner />
      {stats.data && <TypeGroups stats={stats.data} query={query} quota={quota.data} courseId={courseId} />}
    </div>
  )
}
