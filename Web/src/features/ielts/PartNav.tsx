import type { Bank } from '../practice/types'
import { partLabel, type NavState } from './parts'
import type { Part } from './types'

interface Props {
  skill: Bank['skill']
  parts: Part[]
  currentId: number
  states: Record<number, NavState>
  flaggedNow: boolean
  onGo: (questionId: number) => void
  onFlag: () => void
  onSubmit: () => void
  disabled: boolean
}

const tone: Record<NavState, string> = {
  answered: 'bg-brand-soft text-ink',
  flagged: 'border-amber-400 bg-amber-100 text-ink',
  empty: 'bg-surface text-ink-secondary',
}

// Thanh điều hướng câu (Figma 01.01): số câu theo phần, đánh dấu xem lại, Nộp bài.
export function PartNav({ skill, parts, currentId, states, flaggedNow, onGo, onFlag, onSubmit, disabled }: Props) {
  return (
    <nav aria-label="Điều hướng câu" className="fixed inset-x-0 bottom-0 z-10 flex items-center justify-between gap-3 border-t border-line bg-surface px-3 py-3 md:px-7">
      <div className="flex min-w-0 flex-1 items-center gap-4 overflow-x-auto">
        {parts.map((p, i) => (
          <div key={p.key} className="flex shrink-0 items-center gap-1.5">
            <span className="text-xs font-extrabold text-ink-secondary">{partLabel(skill, i + 1)}</span>
            {p.questions.map((q, j) => (
              <button
                key={q.id}
                type="button"
                disabled={disabled}
                aria-label={`Câu ${p.first + j + 1}`}
                aria-current={q.id === currentId ? 'step' : undefined}
                onClick={() => onGo(q.id)}
                className={`grid size-10 place-items-center rounded-[10px] border text-sm font-extrabold ${tone[states[q.id] ?? 'empty']} ${q.id === currentId ? 'border-2 border-brand' : 'border-line'}`}
              >
                {p.first + j + 1}
              </button>
            ))}
          </div>
        ))}
      </div>
      <div className="flex shrink-0 items-center gap-2">
        <button type="button" aria-pressed={flaggedNow} disabled={disabled} onClick={onFlag} className="min-h-11 rounded-full bg-amber-100 px-3 text-sm font-extrabold text-ink disabled:opacity-60">
          {flaggedNow ? 'Câu đã đánh dấu' : 'Đánh dấu xem lại'}
        </button>
        <button type="button" disabled={disabled} onClick={onSubmit} className="min-h-11 rounded-full bg-gradient-to-r from-[#e8590c] to-[#ff8a3d] px-5 text-base font-black text-white disabled:opacity-45">
          Nộp bài
        </button>
      </div>
    </nav>
  )
}
