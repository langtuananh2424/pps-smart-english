import { labelOf } from '../exercises/format'
import { CheckBar } from '../practice/CheckBar'
import { GroupLayoutView, isInlineLayout, isHeadLayout, LayoutHead, type LayoutItem } from '../practice/layout'
import { PromptText, QuestionMediaList } from '../practice/QuestionStem'
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
// D-154: nhóm có bố cục (bảng, ghi chú, phiếu...) dựng một lần với ô nhập đúng chỗ; danh sách lựa chọn/bản đồ hiện ở đầu nhóm.
export function QuestionsPane({ part, answers, flagged, currentId, disabled, onChange, onFocus, retry }: Props) {
  const last = part.first + part.questions.length
  const layout = part.group?.layout
  const inline = isInlineLayout(layout) ? layout : null
  const lockedOf = (q: Part['questions'][number]) => !!retry && isCheckable(q.type) && isLocked(retry.checks[q.id])
  const items: LayoutItem[] = part.questions.map((q, j) => ({ question: q, n: part.first + j + 1, disabled: disabled || lockedOf(q), flagged: flagged.has(q.id) }))
  return (
    <section aria-label="Câu hỏi" className="flex flex-col gap-4 rounded-lg border-[1.5px] border-line bg-surface p-4 md:p-6">
      <div>
        <p className="text-xs font-extrabold uppercase text-brand-strong">Questions {part.first + 1}–{last}</p>
        {part.group?.instruction && <p className="mt-1 text-lg font-black text-ink">{part.group.instruction}</p>}
      </div>
      {isHeadLayout(layout) && <LayoutHead layout={layout} />}
      {inline && (
        <>
          <GroupLayoutView layout={inline} items={items} answers={answers} onChange={onChange} onFocus={onFocus} anchor={(id) => `ielts-q-${id}`} />
          {retry &&
            items.filter((i) => isCheckable(i.question.type)).map((i) => (
              <div key={i.question.id} className="flex items-center gap-3">
                <span className="text-sm font-extrabold text-ink-secondary">Câu {i.n}</span>
                <div className="min-w-0 flex-1"><CheckBar state={retry.checks[i.question.id]} question={i.question} layout={layout} canCheck={!disabled && isAnswered(i.question.type, answers[i.question.id])} pending={retry.pending} onCheck={() => retry.onCheck(i.question.id)} /></div>
              </div>
            ))}
        </>
      )}
      {!inline && part.questions.map((q, j) => {
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
            <div className="flex items-start gap-2 text-base font-black text-ink">
              <span className="grid size-8 shrink-0 place-items-center rounded-sm bg-subtle text-[13px] font-extrabold text-brand-strong">{part.first + j + 1}</span>
              <PromptText question={q} className="min-w-0 flex-1" />
              {flagged.has(q.id) && <span className="ml-auto text-xs font-extrabold text-amber-700">Xem lại</span>}
            </div>
            <QuestionMediaList question={q} />
            {!q.prompt && !q.promptHtml && <p className="text-sm font-semibold text-ink-secondary">{q.instruction ?? labelOf(q.type)}</p>}
            <Component question={q} layout={layout} value={answers[q.id]} onChange={(v) => onChange(q.id, v)} disabled={disabled || (checkable && isLocked(state))} />
            {checkable && <CheckBar state={state} question={q} layout={layout} canCheck={!disabled && isAnswered(q.type, answers[q.id])} pending={retry.pending} onCheck={() => retry.onCheck(q.id)} />}
          </article>
        )
      })}
    </section>
  )
}
