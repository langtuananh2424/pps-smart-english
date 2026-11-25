import { useRef } from 'react'
import { Link, useParams } from 'react-router'
import { BlockError, Skeleton } from '../home/BlockState'
import { btnNeutral, btnPrimary, chip, chipTone } from '../lectures/ui'
import { useAttemptDetail, useCourseTests, useExamStatus } from './api'
import type { AttemptDetail, ExamBands } from './types'

const band = (n: number) => n.toFixed(1)
const card = 'rounded-md border-2 border-line bg-surface p-4'

function Stat({ label, value, note, tone = 'bg-surface' }: { label: string; value: string; note?: string; tone?: string }) {
  return (
    <div className={`${card} ${tone}`}>
      <p className="text-sm font-bold text-ink-secondary">{label}</p>
      <p className="text-2xl font-black text-ink">{value}</p>
      {note && <p className="text-xs font-bold text-ink-muted">{note}</p>}
    </div>
  )
}

// Số câu đúng/tổng: lượt Nghe ép chỉ có forced, còn lại lấy từ breakdown.
function correctOf(a: AttemptDetail) {
  if (a.forced) return `${a.forced.correctCount}/${a.forced.totalCount}`
  const b = a.breakdown
  return b ? `${b.correct}/${b.correct + b.wrong + b.ungraded}` : '—'
}

function SkillTable({ a, bands }: { a: AttemptDetail; bands: ExamBands | null }) {
  const ai = bands?.gradingPending ? 'Đang chấm' : '—'
  const rows: { key: string; name: string; value: string }[] = bands
    ? [
        { key: 'LISTENING', name: 'Nghe', value: bands.skills.LISTENING ? `Band ${band(bands.skills.LISTENING.band)}` : '—' },
        { key: 'READING', name: 'Đọc', value: bands.skills.READING ? `Band ${band(bands.skills.READING.band)}` : '—' },
        ...(bands.skills.GRAMMAR ? [{ key: 'GRAMMAR', name: 'Ngữ pháp', value: `Band ${band(bands.skills.GRAMMAR.band)}` }] : []),
        { key: 'WRITING', name: 'Viết', value: ai },
        { key: 'SPEAKING', name: 'Nói', value: ai },
      ]
    : (a.breakdown?.bySkill ?? []).map((m) => ({ key: m.key, name: m.label, value: `${m.correct}/${m.answered} câu đúng` }))
  if (!rows.length) return null
  return (
    <section aria-label="Điểm theo kỹ năng" className={card}>
      <h2 className="mb-2 text-lg font-black text-ink">Điểm theo kỹ năng</h2>
      <ul>
        {rows.map((r) => (
          <li key={r.key} className="flex justify-between border-b border-line-subtle py-2 font-bold text-ink last:border-0">
            <span>{r.name}</span>
            <span>{r.value}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}

// D-118: kết quả một lượt kiểm tra trong khóa. Nghe/Đọc hiện band, Viết/Nói đang chấm.
export function CourseTestResult() {
  const p = useParams()
  const courseId = Number(p.courseId)
  const attemptId = Number(p.attemptId)
  const attempt = useAttemptDetail(attemptId)
  const exam = useExamStatus(courseId)
  const tests = useCourseTests(courseId)
  const reviewRef = useRef<HTMLElement>(null)
  const back = (
    <Link to={`/bai-tap/khoa/${courseId}/kiem-tra`} className={`${btnNeutral} min-h-11 px-4 py-2.5 text-sm`}>
      Về bài kiểm tra
    </Link>
  )
  if (attempt.isError) return <BlockError onRetry={() => attempt.refetch()} />
  if (attempt.isPending) return <Skeleton className="h-60 w-full" />
  const a = attempt.data
  // shortcut: band chỉ có ở lastResult (bài thi khóa /exam và kiểm tra chương /tests), nên chỉ hiện cho lượt gần nhất; nâng cấp: backend trả band trong GET /attempts/{id}.
  const last = [exam.data?.lastResult, ...(tests.data ?? []).map((t) => t.lastResult)].find((r) => r?.attemptId === attemptId)
  const bands = last?.bands ?? null
  const results = a.results ?? []
  return (
    <div className="flex flex-col gap-4 lg:gap-6">
      <header className="flex flex-col gap-2">
        <span className={`${chip} ${chipTone.success}`}>Đã nộp</span>
        <h1 className="text-2xl font-black text-ink lg:text-[30px]">Kết quả kiểm tra</h1>
        <p className="font-semibold text-ink-secondary">Kết quả minh họa theo thang của chương trình.</p>
      </header>
      <div className="grid gap-3 md:grid-cols-3">
        <Stat label="Band tổng" value={bands ? band(bands.overall) : a.score != null ? `${a.score}/${a.maxScore ?? '—'} điểm` : '—'} />
        <Stat label="Số câu đúng" value={correctOf(a)} note="Xem chi tiết theo kỹ năng" />
        <Stat label="Writing / Speaking" value={bands?.gradingPending || (a.breakdown?.ungraded ?? 0) > 0 ? 'Đang chấm' : '—'} tone="bg-[#e8f0ff]" />
      </div>
      <SkillTable a={a} bands={bands} />
      <section ref={reviewRef} aria-label="Xem lại đáp án" className={card}>
        <h2 className="mb-2 text-lg font-black text-ink">Xem lại đáp án</h2>
        {results.length === 0 && <p className="text-ink-secondary">Bài này không hiện đáp án từng câu.</p>}
        <ul>
          {results.map((r, i) => (
            <li key={r.questionId} className="flex flex-col justify-between gap-1 border-b border-line-subtle py-2 last:border-0 md:flex-row">
              <span className="font-bold text-ink">
                Câu {i + 1} · {r.isCorrect == null ? 'Đang chấm' : r.isCorrect ? 'Đúng' : 'Sai'}
              </span>
              {r.isCorrect != null && (
                <span className="text-sm text-ink-secondary">{r.isCorrect ? 'Xem đáp án và giải thích' : 'Đối chiếu đáp án của bạn với đáp án đúng'}</span>
              )}
              {r.explanation && <span className="text-sm text-ink-secondary md:basis-full">{r.explanation}</span>}
            </li>
          ))}
        </ul>
      </section>
      <p className="text-sm font-semibold text-ink-secondary">Lượt chấm AI được trừ từ ví của khóa theo tác vụ.</p>
      <div className="flex flex-wrap gap-2">
        <button type="button" onClick={() => reviewRef.current?.scrollIntoView?.({ behavior: 'smooth' })} className={`${btnPrimary} min-h-11 px-4 py-2.5 text-sm`}>
          Xem lại đáp án
        </button>
        {back}
      </div>
    </div>
  )
}
