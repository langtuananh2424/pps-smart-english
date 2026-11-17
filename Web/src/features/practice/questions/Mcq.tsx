import type { Answer, QuestionProps } from '../types'
import { option, optionCode } from './ui'

export const mcqAnswered = (v: Answer | undefined) => v?.choiceId != null

export function Mcq({ question, value, onChange, disabled }: QuestionProps) {
  return (
    <div role="radiogroup" aria-label="Các lựa chọn" className="flex flex-col gap-3">
      {(question.choices ?? []).map((c) => {
        const active = value?.choiceId === c.id
        return (
          <button key={c.id} type="button" role="radio" aria-checked={active} disabled={disabled} onClick={() => onChange({ choiceId: c.id })} className={option(active)}>
            <span className={optionCode}>{c.label}</span>
            <span>{c.content}</span>
          </button>
        )
      })}
    </div>
  )
}
