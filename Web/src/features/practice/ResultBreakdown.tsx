import { labelOf, percent } from '../exercises/format'
import type { Bank, Breakdown, Metric } from './types'

interface Props {
  breakdown?: Breakdown
  bank: Bank
  elementName: (code: string) => string
}

const short = (s: string) => (s.length > 60 ? `${s.slice(0, 60)}…` : s)

// FR-EX-08: phân tích kết quả; vắng breakdown (đang tải/lỗi) thì không hiện gì.
export function ResultBreakdown({ breakdown: b, bank, elementName }: Props) {
  if (!b) return null
  const groups: [string, Metric[], (m: Metric) => string][] = [
    ['Theo kỹ năng', b.bySkill, (m) => m.label || m.key],
    ['Theo dạng câu', b.byType, (m) => m.label || labelOf(m.key)],
    ['Theo yếu tố ngữ pháp', b.byGrammar, (m) => m.label || elementName(m.key)],
  ]
  const order = new Map(bank.questions.map((q, i) => [q.id, i]))
  const wrong = b.wrongQuestions.flatMap((w) => {
    const i = order.get(w.questionId)
    return i == null ? [] : [{ ...w, text: `Câu ${i + 1} · ${short(bank.questions[i].prompt)}` }]
  })
  return (
    <section aria-label="Phân tích kết quả" className="flex flex-col gap-3 rounded-lg border-[1.5px] border-line bg-surface p-4 md:p-6">
      <h2 className="text-lg font-black text-ink">Phân tích kết quả</h2>
      <p className="text-base font-semibold text-ink">
        {b.correct} đúng · {b.wrong} sai · {b.ungraded} chưa chấm
      </p>
      {groups.map(([title, items, name]) =>
        items.length === 0 ? null : (
          <div key={title} className="flex flex-col gap-2">
            <h3 className="text-[13px] font-extrabold text-ink-secondary">{title}</h3>
            <ul className="flex flex-col gap-2">
              {items.map((m) => (
                <li key={m.key} className="flex flex-col gap-1">
                  <div className="flex justify-between text-sm font-bold text-ink">
                    <span>{name(m)}</span>
                    <span>{percent(m.accuracy)}</span>
                  </div>
                  <div className="h-1.5 overflow-hidden rounded-full bg-muted">
                    <div className="h-full bg-[#f76707]" style={{ width: `${Math.round(m.accuracy ?? 0)}%` }} />
                  </div>
                </li>
              ))}
            </ul>
          </div>
        ),
      )}
      {wrong.length > 0 && (
        <div className="flex flex-col gap-2">
          <h3 className="text-[13px] font-extrabold text-ink-secondary">Câu sai nhiều</h3>
          <ul className="flex flex-col gap-1.5 text-sm font-semibold text-ink">
            {wrong.map((w) => (
              <li key={w.questionId} className="flex justify-between gap-2">
                <span>{w.text}</span>
                <span className="shrink-0 text-ink-secondary">Sai {w.wrongCount} lần tích lũy</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}
