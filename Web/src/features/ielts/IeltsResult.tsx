import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import cam from '../../assets/exercises/cam-kinh-lup.webp'
import { describeAnswer } from '../practice/describe'
import { QuestionStem } from '../practice/QuestionStem'
import { pillNeutral, pillPrimary } from '../practice/questions/ui'
import { ErrorCard } from '../practice/StatusCard'
import type { Bank, QuestionResult } from '../practice/types'
import { fetchIeltsResult } from './api'
import { buildParts } from './parts'
import type { AttemptBands } from './types'

const SKILL: Record<string, string> = { READING: 'Reading', LISTENING: 'Listening', GRAMMAR: 'Grammar' }
const band = (n: number) => n.toFixed(1)

function Bands({ bands }: { bands: AttemptBands | null | undefined }) {
  if (!bands) return <p className="text-sm font-semibold text-ink-secondary">Bài này chưa có bảng quy đổi band.</p>
  return (
    <div className="flex flex-col items-center gap-1">
      <p className="text-sm font-extrabold uppercase text-ink-secondary">Band</p>
      <p aria-label="Band" className="text-5xl font-black text-brand-strong">{band(bands.overall)}</p>
      {Object.entries(bands.skills).map(([k, s]) => (
        <p key={k} className="text-sm font-semibold text-ink-secondary">{SKILL[k] ?? k}: {s.correct}/{s.total} câu đúng · band {band(s.band)}</p>
      ))}
      {bands.gradingPending && <p role="status" className="text-sm font-semibold text-ink-secondary">Còn câu đang chấm, band có thể tăng.</p>}
    </div>
  )
}

// Figma 00.03 · Đã nộp bài + kết quả Reading/Listening (FR-EX-05): số câu đúng, band (FR-EXAM-03), giải thích từng câu.
export function IeltsResult({ bank, attemptId, onRetry }: { bank: Bank; attemptId: number; onRetry: () => void }) {
  const q = useQuery({ queryKey: ['attempt', attemptId, 'ielts-result'], queryFn: () => fetchIeltsResult(attemptId), retry: false })
  if (q.isError) return <ErrorCard error={q.error} onRetry={() => q.refetch()} />
  if (!q.data) return <p role="status" className="p-8 text-center font-bold text-ink-secondary">Đang tải kết quả…</p>
  const r = q.data
  const pending = r.score == null // D-147: còn câu AI chưa chốt thì chưa có điểm
  const byQuestion = new Map<number, QuestionResult>((r.results ?? []).map((x) => [x.questionId, x]))
  const { ordered } = buildParts(bank)
  const correct = (r.results ?? []).filter((x) => x.isCorrect === true).length
  return (
    <div className="min-h-dvh bg-page px-4 py-8">
      <div className="mx-auto flex max-w-[720px] flex-col gap-4">
        <section className="flex flex-col items-center gap-3 rounded-lg border border-line-subtle bg-surface p-6 text-center shadow-edge-card">
          <img src={cam} alt="" width={96} height={96} />
          <h1 className="text-2xl font-black text-ink">Bạn đã nộp bài</h1>
          {pending ? <p role="status" className="font-semibold text-ink-secondary">Bài đang được chấm.</p> : (
            <>
              <p className="text-xl font-black text-ink">{correct}/{r.maxScore ?? ordered.length} câu đúng</p>
              <Bands bands={r.bands} />
            </>
          )}
        </section>
        {!pending && (
          <section aria-label="Giải thích từng câu" className="flex flex-col rounded-lg border-[1.5px] border-line bg-surface p-4">
            <h2 className="pb-2 text-lg font-black text-ink">Giải thích từng câu</h2>
            <ol>
              {ordered.map((x, i) => {
                const res = byQuestion.get(x.id)
                const right = res?.correctAnswer ? describeAnswer(x, res.correctAnswer, undefined, bank.groups?.find((g) => g.id === x.groupId)?.layout) : null
                return (
                  <li key={x.id} className="flex flex-col gap-1 border-b border-line py-3 last:border-b-0">
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex min-w-0 flex-col gap-2">
                        <p className="font-black text-ink">Câu {i + 1}.</p>
                        <QuestionStem question={x} className="font-black text-ink" />
                      </div>
                      <span className={`shrink-0 text-sm font-black ${res?.isCorrect ? 'text-success-fg' : 'text-danger-fg'}`}>{res?.isCorrect === true ? 'Đúng' : res?.isCorrect === false ? 'Sai' : 'Chưa làm'}</span>
                    </div>
                    {right && <p className="text-sm font-semibold text-ink">Đáp án: {right}</p>}
                    {res?.explanation && <p className="text-sm font-semibold text-ink-secondary">{res.explanation}</p>}
                  </li>
                )
              })}
            </ol>
          </section>
        )}
        <div className="flex flex-wrap justify-center gap-3">
          <Link to="/bai-tap" className={pillNeutral}>Chọn bài luyện</Link>
          <button type="button" onClick={onRetry} className={pillPrimary}>Làm lại</button>
        </div>
      </div>
    </div>
  )
}
