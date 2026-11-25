import type { Answer, QuestionProps } from '../types'
import { option, optionCode } from './ui'

const labels: Record<string, string> = { TRUE: 'True', FALSE: 'False', NOT_GIVEN: 'Not given', YES: 'Yes', NO: 'No' }
export const tfLabel = (code: string) => labels[code] ?? code

export const trueFalseAnswered = (v: Answer | undefined) => typeof v?.value === 'string' && v.value !== ''

export function TrueFalse({ question, value, onChange, disabled }: QuestionProps) {
  const options = (question.payload?.options ?? []) as string[]
  return (
    <div role="radiogroup" aria-label="Các lựa chọn" className="flex flex-col gap-3">
      {options.map((o, i) => {
        const active = value?.value === o
        return (
          <button key={o} type="button" role="radio" aria-checked={active} disabled={disabled} onClick={() => onChange({ value: o })} className={option(active)}>
            <span className={optionCode}>{String.fromCharCode(65 + i)}</span>
            <span>{tfLabel(o)}</span>
          </button>
        )
      })}
    </div>
  )
}
