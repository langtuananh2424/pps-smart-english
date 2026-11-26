import { labelOf } from '../exercises/format'
import { CheckBar } from '../practice/CheckBar'
import { isAnswered, rendererOf } from '../practice/questions/registry'
import type { Answer, Answers, CheckState } from '../practice/types'
import { isCheckable, isLocked } from '../practice/useCheck'
import type { Part } from './types'

interface Props {
  part: Part
  answers: Answers
  flagged: Set<number>
  currentId: number
  disabled: boolean
  onChange: (questionId: number, v: Answer | undefined) => void
  onFocus: (questionId: number) => void
  retry?: { checks: Record<number, CheckState>; pending: boolean; onCheck: (questionId: number) => void } // D-150: có ở lượt Luyện tập
}

// Cột câu hỏi (Figma 01.01): cả nhóm câu của phần đang mở, mỗi câu có số thứ tự toàn bài và renderer dùng chung với màn luyện tập.
export function QuestionsPane({ part, answers, flagged, currentId, disabled, onChange, onFocus, retry }: Props) {
  const last = part.first + part.questions.length
  return (
    <section aria-label="Câu hỏi" className="flex flex-col gap-4 rounded-lg border-[1.5px] border-line bg-surface p-4 md:p-6">
      <div>
        <p className="text-xs font-extrabold uppercase text-brand-strong">Questions {part.first + 1}–{last}</p>
        {part.group?.instruction && <p className="mt-1 text-lg font-black text-ink">{part.group.instruction}</p>}
      </div>
      {part.questions.map((q, j) => {
        const { Component } = rendererOf(q.type)
        const checkable = !!retry && isCheckable(q.type)
        const state = retry?.checks[q.id]
        return (
          <article
            key={q.id}
            id={`ielts-q-${q.id}`}
            aria-label={`Câu ${part.first + j + 1}`}
            onFocusCapture={() => onFocus(q.id)}
            onPointerDown={() => onFocus(q.id)}
            className={`flex flex-col gap-2 rounded-md border-[1.5px] p-3 ${q.id === currentId ? 'border-line-brand' : 'border-transparent'}`}
          >
            <p className="flex items-start gap-2 text-base font-black text-ink">
              <span className="grid size-8 shrink-0 place-items-center rounded-sm bg-subtle text-[13px] font-extrabold text-brand-strong">{part.first + j + 1}</span>
              <span>{q.prompt}</span>
              {flagged.has(q.id) && <span className="ml-auto text-xs font-extrabold text-amber-700">Xem lại</span>}
            </p>
            {!q.prompt && <p className="text-sm font-semibold text-ink-secondary">{q.instruction ?? labelOf(q.type)}</p>}
            <Component question={q} value={answers[q.id]} onChange={(v) => onChange(q.id, v)} disabled={disabled || (checkable && isLocked(state))} />
            {checkable && <CheckBar state={state} canCheck={!disabled && isAnswered(q.type, answers[q.id])} pending={retry.pending} onCheck={() => retry.onCheck(q.id)} />}
          </article>
        )
      })}
    </section>
  )
}
