import { labelOf } from '../exercises/format'
import { isAiType } from '../exercises/format'
import { hasReview, type AiOutcome } from './useAiOutcomes'
import type { Bank, QuestionResult } from './types'

const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1)
const btn = 'text-sm font-extrabold text-brand-strong'

// Giá trị cột phải của một câu Viết/Nói: điểm %, Band, bỏ qua hoặc không chấm được. Không tự quy đổi: lấy đúng số API trả.
function aiValue(o: AiOutcome | undefined, r: QuestionResult | undefined, ready: boolean): { text: string; muted: boolean } {
  if (r?.aiFailure || o?.session.status === 'FAILED') return { text: '0% · Không chấm được', muted: true }
  if (!ready) return { text: '…', muted: true }
  if (!o || o.session.status === 'NOT_SUBMITTED') return { text: '0% · Bỏ qua', muted: true }
  if (o.writing?.grade) return { text: `${Math.round(o.writing.grade.scorePercent)}%`, muted: false }
  const overall = o.speaking?.result?.overall
  if (overall != null) return { text: `Band ${overall.toFixed(1)}`, muted: false }
  return { text: '…', muted: true }
}

interface Props {
  bank: Bank
  results: QuestionResult[]
  outcomes: Map<number, AiOutcome>
  ready: boolean
  onReview: (questionId: number) => void
}

// "Kết quả từng câu" (Figma 608-17886, 614-*): trắc nghiệm Đúng/Sai/Chưa làm, Viết %, Nói Band; câu có nhận xét có liên kết mở.
export function ResultList({ bank, results, outcomes, ready, onReview }: Props) {
  const byQuestion = new Map(results.map((x) => [x.questionId, x]))
  return (
    <section aria-label="Kết quả từng câu" className="flex flex-col rounded-lg border-[1.5px] border-line bg-surface p-4 md:p-6">
      <h2 className="pb-2 text-lg font-black text-ink">Kết quả từng câu</h2>
      <ol>
        {bank.questions.map((q, i) => {
          const r = byQuestion.get(q.id)
          const o = outcomes.get(q.id)
          const ai = isAiType(q.type)
          const v = ai ? aiValue(o, r, ready) : { text: r?.isCorrect === true ? 'Đúng' : r?.isCorrect === false ? 'Sai' : 'Chưa làm', muted: false }
          const failure = ai && r?.aiFailure ? `${cap(r.aiFailure)}.${r.aiRefunded ? ' Đã hoàn 1 lượt chấm.' : ''}` : null
          return (
            <li key={q.id} className="flex items-center justify-between gap-3 border-b border-line py-3 last:border-b-0">
              <div className="min-w-0">
                <p className="text-xs font-extrabold text-ink-secondary">Câu {i + 1}</p>
                <p className="text-base font-black text-ink">{q.instruction ?? labelOf(q.type)}</p>
                {failure && <p role="status" className="text-sm font-semibold text-ink-secondary">{failure}</p>}
              </div>
              <div className="flex shrink-0 items-center gap-4">
                <span className={`text-sm font-black ${v.muted ? 'text-ink-secondary' : 'text-ink'}`}>{v.text}</span>
                {ai && hasReview(o) && (
                  <button type="button" onClick={() => onReview(q.id)} aria-label={`Xem nhận xét câu ${i + 1}`} className={btn}>
                    Xem nhận xét →
                  </button>
                )}
              </div>
            </li>
          )
        })}
      </ol>
    </section>
  )
}
