import { ChoiceNotes } from './ChoiceNotes'
import { useGrammarElements } from './elements'
import { describeAnswer } from './describe'
import { pillPrimary } from './questions/ui'
import { Line } from './ResultView'
import type { CheckState, GroupLayout, Question } from './types'
import { isLocked } from './useCheck'

interface Props {
  state?: CheckState
  question: Question
  layout?: GroupLayout | null
  canCheck: boolean // đã có câu trả lời và lượt không tạm dừng
  pending: boolean
  onCheck: () => void
}

// D-150, D-163: nút Kiểm tra và kết quả của câu; chỉ khi hết lượt mà vẫn sai mới hiện đáp án đúng và giải thích.
export function CheckBar({ state, question, layout, canCheck, pending, onCheck }: Props) {
  const locked = isLocked(state)
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p role="status" className={`text-sm font-extrabold ${state?.correct ? 'text-success-fg' : 'text-danger-fg'}`}>
          {state?.correct ? 'Đúng rồi!' : state && locked ? 'Cần sửa' : state ? 'Cần sửa, thử lại nhé' : ''}
        </p>
        {!locked && (
          <button type="button" onClick={onCheck} disabled={!canCheck || pending} className={`${pillPrimary} !h-11 !text-base`}>
            Kiểm tra
          </button>
        )}
      </div>
      {state && !state.correct && locked && <RevealedAnswer state={state} question={question} layout={layout} />}
    </div>
  )
}

function RevealedAnswer({ state, question, layout }: { state: CheckState; question: Question; layout?: GroupLayout | null }) {
  const elements = useGrammarElements()
  const correct = describeAnswer(question, state.correctAnswer, elements.name, layout)
  if (!correct && !state.explanation && !state.choiceNotes?.length) return null
  return (
    <>
      <dl className="flex flex-col gap-1.5 text-sm font-semibold">
        {correct && <Line label="Đáp án đúng" text={correct} />}
        {state.explanation && <Line label="Giải thích" text={state.explanation} />}
      </dl>
      <ChoiceNotes notes={state.choiceNotes} />
    </>
  )
}
