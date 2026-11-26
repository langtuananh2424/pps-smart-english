import type { Bank } from '../practice/types'
import { partLabel, type NavState } from './parts'
import type { Part } from './types'

interface Props {
  skill: Bank['skill']
  parts: Part[]
  states: Record<number, NavState>
  onBack: () => void
  onGo: (questionId: number) => void
  onConfirm: () => void
}

const tone: Record<NavState, string> = {
  answered: 'bg-brand-soft border-line-brand',
  flagged: 'bg-amber-100 border-amber-400',
  empty: 'bg-surface border-line',
}

// Figma 00.02 · Xem lại trước khi nộp: chọn số câu để quay lại bài làm, "Xác nhận nộp" mở hộp xác nhận kết thúc.
export function ReviewScreen({ skill, parts, states, onBack, onGo, onConfirm }: Props) {
  const all = parts.flatMap((p) => p.questions)
  const count = (s: NavState) => all.filter((q) => states[q.id] === s).length
  return (
    <div className="min-h-dvh bg-page px-4 pb-24 pt-6 md:px-10">
      <button type="button" onClick={onBack} className="min-h-11 rounded-[12px] border border-line bg-surface px-3 text-sm font-extrabold text-brand-strong">‹ Quay lại bài</button>
      <h1 className="mt-5 text-3xl font-black text-ink">Xem lại trước khi nộp</h1>
      <p className="mt-2 font-semibold text-ink-secondary">Chọn câu để quay lại bài làm.</p>
      <section aria-label="Danh sách câu hỏi" className="mt-4 flex max-w-[520px] flex-col gap-3 rounded-lg border border-line bg-surface p-4">
        <h2 className="text-base font-black text-ink">Danh sách câu hỏi</h2>
        {parts.map((p, i) => (
          <div key={p.key} className="flex flex-col gap-1.5">
            {parts.length > 1 && <p className="text-xs font-extrabold text-ink-secondary">{partLabel(skill, i + 1)}</p>}
            <div className="flex flex-wrap gap-2">
              {p.questions.map((q, j) => (
                <button key={q.id} type="button" aria-label={`Câu ${p.first + j + 1}`} onClick={() => onGo(q.id)} className={`grid size-11 place-items-center rounded-[10px] border text-sm font-extrabold text-ink ${tone[states[q.id] ?? 'empty']}`}>
                  {p.first + j + 1}
                </button>
              ))}
            </div>
          </div>
        ))}
        <p className="text-sm font-semibold text-ink-secondary">{count('answered')} Đã trả lời · {count('flagged')} Xem lại · {count('empty')} Chưa trả lời</p>
      </section>
      <div className="fixed inset-x-0 bottom-0 flex justify-end border-t border-line bg-surface px-4 py-3 md:px-10">
        <button type="button" onClick={onConfirm} className="min-h-12 rounded-full bg-gradient-to-r from-[#e8590c] to-[#ff8a3d] px-6 text-lg font-black text-white">Xác nhận nộp</button>
      </div>
    </div>
  )
}
