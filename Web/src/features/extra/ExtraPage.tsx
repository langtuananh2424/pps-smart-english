import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../lib/api'
import { BlockError, Skeleton } from '../home/BlockState'
import { useRoadmap } from '../home/api'
import { btnNeutral } from '../lectures/ui'
import { startExtra, useExtraOverview } from './api'
import { ChallengeCard, ReviewCard } from './TopicCard'
import type { ExtraKind } from './types'
import { inLevel, KIND_LABEL, reviewCount } from './view'

const chipBtn = (on: boolean) =>
  `min-h-10 rounded-full border-2 px-4 text-sm font-extrabold ${on ? 'border-brand bg-subtle text-brand-strong' : 'border-line bg-surface text-ink-secondary'}`

// FR-EX-07, D-152: Luyện thêm theo Topic (Figma 10): lọc Level và dạng bài, Ôn lại, Thử thách.
export function ExtraPracticePage() {
  const courseId = Number(useParams().courseId)
  const q = useExtraOverview(courseId)
  const roadmap = useRoadmap(courseId)
  const navigate = useNavigate()
  const [level, setLevel] = useState<number | null>(null)
  const [kind, setKind] = useState<ExtraKind | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (q.isError)
    return q.error instanceof ApiError && q.error.status === 404 ? (
      <div className="flex flex-col items-start gap-3">
        <h1 className="text-2xl font-black text-ink">Không tìm thấy khóa học</h1>
        <Link to="/bai-tap" className={btnNeutral}>Chọn khóa khác</Link>
      </div>
    ) : (
      <BlockError onRetry={() => q.refetch()} />
    )
  if (q.isPending) return <Skeleton className="h-64" />
  const o = q.data
  const review = inLevel(o.review, level)
  const challenge = inLevel(o.challenge, level)
  const bankOf = (topicId: number) => roadmap.data?.levels.flatMap((l) => l.topics).find((t) => t.id === topicId)?.banks[0]?.id ?? null
  const n = reviewCount(o, review, kind)

  const practice = async () => {
    setBusy(true)
    setError(null)
    try {
      navigate(`/bai-tap/luyen-them/${(await startExtra(courseId, { scope: 'REVIEW', ...(kind ? { kind } : {}) })).id}`)
    } catch (e) {
      const resume = e instanceof ApiError && e.code === 'attempt_in_progress' ? (e.details as { attemptId?: number } | undefined)?.attemptId : null
      if (resume) navigate(`/bai-tap/luyen-them/${resume}`)
      else setError(e instanceof Error ? e.message : 'Không bắt đầu được, thử lại nhé.')
      setBusy(false)
    }
  }

  return (
    <div className="flex flex-col gap-5 lg:gap-6">
      <header className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div className="flex flex-col gap-1">
          <p className="text-sm font-bold text-ink-muted">Bài tập / {o.courseTitle}</p>
          <h1 className="text-[32px] font-black leading-[38px] text-ink">Luyện thêm theo Topic</h1>
          <p className="font-semibold text-ink-secondary">Ôn lại chủ đề đã học hoặc thử sức với Level và chủ đề mới, khó hơn.</p>
        </div>
        <div className="flex flex-wrap gap-3">
          <Link to={`/bai-tap/khoa/${courseId}`} className={btnNeutral}>← Bài tập theo bài giảng</Link>
          <Link to="/bai-tap" className={btnNeutral}>Đổi khóa học</Link>
        </div>
      </header>

      {o.openAttemptId != null && (
        <div role="status" className="flex flex-col gap-3 rounded-lg border border-line-subtle bg-reward-bg p-4 shadow-edge-card sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm font-bold text-ink">Bạn đang có lượt Luyện thêm chưa nộp. Kết quả Viết/Nói của các câu trong lượt sẽ hiện lại sau khi bạn nộp.</p>
          <Link to={`/bai-tap/luyen-them/${o.openAttemptId}`} className={btnNeutral}>Tiếp tục</Link>
        </div>
      )}

      <div className="flex flex-col gap-3">
        <div role="group" aria-label="Lọc theo Level" className="flex flex-wrap gap-2">
          <button type="button" aria-pressed={level == null} onClick={() => setLevel(null)} className={chipBtn(level == null)}>Tất cả Level</button>
          {o.levels.map((l) => (
            <button key={l.id} type="button" aria-pressed={level === l.id} onClick={() => setLevel(l.id)} className={chipBtn(level === l.id)}>{l.title}</button>
          ))}
        </div>
        <div role="group" aria-label="Lọc theo dạng bài" className="flex flex-wrap gap-2">
          <button type="button" aria-pressed={kind == null} onClick={() => setKind(null)} className={chipBtn(kind == null)}>Mọi dạng</button>
          {(Object.keys(KIND_LABEL) as ExtraKind[]).map((k) => (
            <button key={k} type="button" aria-pressed={kind === k} onClick={() => setKind(k)} className={chipBtn(kind === k)}>{KIND_LABEL[k]}</button>
          ))}
        </div>
      </div>

      <section aria-labelledby="extra-review" className="flex flex-col gap-3">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 id="extra-review" className="text-2xl font-black text-ink">Ôn lại</h2>
            <p className="text-sm font-semibold text-ink-secondary">Làm lại bài tập của Level và chủ đề đã học để nhớ lâu hơn.</p>
          </div>
          {kind && (
            <button type="button" disabled={n === 0 || busy} onClick={() => void practice()} className={btnNeutral}>
              {busy ? 'Đang tạo…' : n > 0 ? `Luyện ${n} câu ${KIND_LABEL[kind]}` : 'Chưa có câu dạng này'}
            </button>
          )}
        </div>
        {error && <p role="alert" className="text-sm font-bold text-danger">{error}</p>}
        {review.length === 0 ? (
          <p className="rounded-lg border border-line-subtle bg-surface p-5 font-semibold text-ink-secondary">Chưa có Topic nào hoàn thành. Hoàn thành một Topic để ôn lại ở đây.</p>
        ) : (
          <div className="grid gap-4 lg:grid-cols-3">
            {review.map((c) => <ReviewCard key={c.topicId} c={c} bankId={bankOf(c.topicId)} kind={kind} />)}
          </div>
        )}
      </section>

      <section aria-labelledby="extra-challenge" className="flex flex-col gap-3">
        <div>
          <h2 id="extra-challenge" className="text-2xl font-black text-ink">Thử thách</h2>
          <p className="text-sm font-semibold text-ink-secondary">Thử sức với Level và chủ đề mới, khó hơn.</p>
        </div>
        {challenge.length === 0 ? (
          <p className="rounded-lg border border-line-subtle bg-surface p-5 font-semibold text-ink-secondary">Không có Topic nào để thử thách.</p>
        ) : (
          <div className="grid gap-4 lg:grid-cols-3">
            {challenge.map((c) => <ChallengeCard key={c.topicId} c={c} o={o} kind={kind} />)}
          </div>
        )}
      </section>
    </div>
  )
}
