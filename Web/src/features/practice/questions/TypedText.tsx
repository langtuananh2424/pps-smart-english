import type { Answer, QuestionProps } from '../types'
import { field, fieldLabel } from './ui'
import { wordCount } from './FillText'

const textOf = (v: Answer | undefined) => String(v?.text ?? '')
export const typedAnswered = (v: Answer | undefined) => textOf(v).trim() !== ''
const emit = (onChange: QuestionProps['onChange'], s: string) => onChange(s.trim() ? { text: s } : undefined)

function Limit({ max, text }: { max?: number; text: string }) {
  if (max == null) return null
  const over = wordCount(text) > max
  return <span role={over ? 'alert' : undefined} className={`font-semibold ${over ? 'text-red-600' : 'text-ink-muted'}`}>Tối đa {max} từ{over ? ` · bạn đã nhập ${wordCount(text)} từ` : ''}</span>
}

// DICTATION (Figma 10.34): ô nhiều dòng để chép lại đoạn nghe được.
export function Dictation({ question, value, onChange, disabled }: QuestionProps) {
  const text = textOf(value)
  return (
    <label className={`${fieldLabel} flex flex-col gap-1.5`}>
      Chép lại những gì bạn nghe được
      <textarea value={text} rows={4} maxLength={1000} disabled={disabled} onChange={(e) => emit(onChange, e.target.value)} className={`${field} resize-y`} />
      <Limit max={question.payload?.maxWords} text={text} />
    </label>
  )
}

// IELTS_SENTENCE: nửa câu trước, ô nhập, nửa câu sau.
export function Sentence({ question, value, onChange, disabled }: QuestionProps) {
  const { before, after, maxWords } = (question.payload ?? {}) as { before?: string; after?: string; maxWords?: number }
  const text = textOf(value)
  return (
    <div className="flex flex-col gap-1.5">
      <p className="flex flex-wrap items-center gap-2 text-base font-semibold leading-8 text-ink">
        {before}
        <input aria-label="Câu trả lời" value={text} maxLength={200} disabled={disabled} onChange={(e) => emit(onChange, e.target.value)} className="h-10 w-44 max-w-full rounded-sm border-[1.5px] border-line-input bg-input px-2 font-semibold outline-none focus:border-brand disabled:opacity-60" />
        {after}
      </p>
      <Limit max={maxWords} text={text} />
    </div>
  )
}
