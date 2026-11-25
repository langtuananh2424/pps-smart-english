import type { Answer, QuestionProps } from '../types'
import { field, fieldLabel } from './ui'

const textOf = (v: Answer | undefined) => String(v?.text ?? '')
const wordCount = (s: string) => s.trim().split(/\s+/).filter(Boolean).length

export const fillTextAnswered = (v: Answer | undefined) => textOf(v).trim() !== ''

export function FillText({ question, value, onChange, disabled }: QuestionProps) {
  const text = textOf(value)
  const max = question.payload?.maxWords as number | undefined
  const over = max != null && wordCount(text) > max
  return (
    <label className={`${fieldLabel} flex flex-col gap-1.5`}>
      Câu trả lời của bạn
      <input value={text} disabled={disabled} maxLength={200} placeholder="Nhập câu trả lời…" onChange={(e) => onChange(e.target.value.trim() ? { text: e.target.value } : undefined)} className={field} />
      {max != null && (
        <span role={over ? 'alert' : undefined} className={`font-semibold ${over ? 'text-red-600' : 'text-ink-muted'}`}>
          Tối đa {max} từ{over ? ` · bạn đã nhập ${wordCount(text)} từ, có thể bị tính sai` : ''}
        </span>
      )}
    </label>
  )
}
