import type { Answer, QuestionProps } from '../types'
import { option, optionCode } from './ui'

const idsOf = (v: Answer | undefined) => (v?.choiceIds ?? []) as number[]

export const choiceMultiAnswered = (v: Answer | undefined) => idsOf(v).length > 0

// Bật/tắt từng lựa chọn; bỏ hết thì không còn bài làm.
export function ChoiceMulti({ question, value, onChange, disabled }: QuestionProps) {
  const ids = idsOf(value)
  const toggle = (id: number) => {
    const next = ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]
    onChange(next.length ? { choiceIds: next } : undefined)
  }
  return (
    <div role="group" aria-label="Các lựa chọn" className="flex flex-col gap-3">
      {(question.choices ?? []).map((c) => {
        const active = ids.includes(c.id)
        return (
          <button key={c.id} type="button" role="checkbox" aria-checked={active} disabled={disabled} onClick={() => toggle(c.id)} className={option(active)}>
            <span className={optionCode}>{c.label}</span>
            {/* contentHtml do máy chủ đã làm sạch */}
            {c.contentHtml ? <span dangerouslySetInnerHTML={{ __html: c.contentHtml }} /> : <span>{c.content}</span>}
          </button>
        )
      })}
    </div>
  )
}
