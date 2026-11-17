import { useMemo, useState } from 'react'
import { Link } from 'react-router'
import lessonIcon from '../../assets/exercises/section-writing.svg'
import { fold } from '../courses/format'
import type { Roadmap, RoadmapBank, RoadmapLesson, RoadmapTopic } from '../home/types'
import { lessonStatus, statusLabel } from '../lectures/status'
import { btnNeutral, chip, statusBadge } from '../lectures/ui'

export interface LectureRow {
  lesson: RoadmapLesson
  bank: RoadmapBank
}

// Chỉ các bài của topic có bộ bài tập; mỗi bài lấy bộ đầu tiên.
export const rowsOf = (t: RoadmapTopic): LectureRow[] =>
  t.lessons.flatMap((lesson) => {
    const bank = t.banks.find((b) => b.lessonId === lesson.id)
    return bank ? [{ lesson, bank }] : []
  })

// Topic mặc định: topic đang học (resume), không có thì topic mở khóa đầu tiên có bài tập.
export function defaultTopic(road: Roadmap, withBanks: RoadmapTopic[]): RoadmapTopic | undefined {
  return withBanks.find((t) => t.id === road.resume?.topicId) ?? withBanks.find((t) => !t.locked) ?? withBanks[0]
}

export function LectureList({ roadmap, query }: { roadmap: Roadmap; query: string }) {
  const withBanks = useMemo(() => roadmap.levels.flatMap((l) => l.topics).filter((t) => rowsOf(t).length > 0), [roadmap])
  const [picked, setPicked] = useState<number | null>(null)
  const topic = withBanks.find((t) => t.id === picked) ?? defaultTopic(roadmap, withBanks)
  if (!topic) return null

  const q = fold(query.trim())
  const rows = rowsOf(topic).filter((r) => !q || fold(r.lesson.title).includes(q))
  return (
    <section aria-labelledby="lecture-list" className="flex flex-col gap-3 lg:gap-4">
      <div className="flex items-center gap-3">
        <span className="hidden size-10 shrink-0 place-items-center rounded-sm bg-reward-bg lg:grid">
          <img src={lessonIcon} alt="" width={20} height={20} />
        </span>
        <div>
          <h2 id="lecture-list" className="text-lg font-black leading-6 text-ink lg:text-2xl lg:leading-[30px]">
            Làm theo bài giảng
          </h2>
          <p className="hidden text-sm font-semibold text-ink-secondary lg:block">Làm bài tập ngay sau khi xem từng bài trong {topic.title}</p>
        </div>
      </div>
      {withBanks.length > 1 && (
        <div role="group" aria-label="Chọn chủ đề" className="flex gap-2 overflow-x-auto">
          {withBanks.map((t) => (
            <button
              key={t.id}
              type="button"
              aria-pressed={t.id === topic.id}
              onClick={() => setPicked(t.id)}
              className={`min-h-9 shrink-0 rounded-full border-2 px-3 text-sm font-extrabold ${t.id === topic.id ? 'border-brand bg-[#fff8f1] text-brand-strong' : 'border-line bg-surface text-ink-secondary'}`}
            >
              {t.title}
            </button>
          ))}
        </div>
      )}
      <ul className="grid gap-2 lg:grid-cols-2 lg:gap-4">
        {rows.map(({ lesson, bank }) => {
          const st = lessonStatus(lesson)
          return (
            <li key={lesson.id} className="flex items-center gap-3 rounded-[20px] border-2 border-line bg-surface px-3.5 py-3 shadow-edge-card lg:gap-4 lg:px-5 lg:py-4">
              <div className="flex min-w-0 flex-1 flex-col items-start gap-1 lg:gap-1.5">
                <span className={`${chip} ${statusBadge[st]}`}>{statusLabel[st]}</span>
                <p className="text-sm font-semibold leading-5 text-ink lg:text-lg lg:font-black lg:leading-6">
                  Bài {lesson.order} · {lesson.title}
                </p>
              </div>
              <Link to={`/bai-tap/${bank.id}`} aria-label={`Làm bài tập Bài ${lesson.order}`} className={`${btnNeutral} min-h-0 px-3 lg:min-h-11 lg:px-4`}>
                <span className="lg:hidden">Làm bài</span>
                <span className="max-lg:hidden">Làm bài tập →</span>
              </Link>
            </li>
          )
        })}
        {!rows.length && <li className="font-bold text-ink-secondary">Không có bài nào khớp.</li>}
      </ul>
    </section>
  )
}
