import { useGrammarElements } from '../elements'
import type { Answer, QuestionProps } from '../types'
import { field, fieldLabel } from './ui'

export const rewriteAnswered = (v: Answer | undefined) => typeof v?.text === 'string' && v.text.trim() !== ''

// Dùng chung cho CONNECTIVE_REWRITE (requiredWord) và GRAMMAR_REWRITE (requiredElement), Figma 10.37.
export function Rewrite({ question, value, onChange, disabled }: QuestionProps) {
  const { name: elementName } = useGrammarElements()
  const { sentence, requiredWord, requiredElement } = question.payload as { sentence: string; requiredWord?: string; requiredElement?: string }
  const requirement = requiredWord ? `Dùng từ nối: ${requiredWord}` : requiredElement ? `Dùng cấu trúc: ${elementName(requiredElement)}` : null
  return (
    <div className="flex flex-col gap-3">
      <p className="text-lg font-semibold leading-7 text-ink">{sentence}</p>
      {requirement && <p className="text-[13px] font-extrabold text-brand-strong">{requirement}</p>}
      <label className={`${fieldLabel} flex flex-col gap-1.5`}>
        Câu trả lời của bạn
        <textarea
          aria-label="Câu trả lời của bạn"
          rows={2}
          maxLength={1000}
          disabled={disabled}
          placeholder="Nhập câu trả lời…"
          value={typeof value?.text === 'string' ? value.text : ''}
          onChange={(e) => onChange(e.target.value ? { text: e.target.value } : undefined)}
          className={field}
        />
      </label>
    </div>
  )
}
