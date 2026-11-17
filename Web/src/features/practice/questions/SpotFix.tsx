import type { Answer, QuestionProps } from '../types'
import { field, fieldLabel, option, optionCode } from './ui'

export const spotFixAnswered = (v: Answer | undefined) => v?.position != null && typeof v.correction === 'string' && v.correction.trim() !== ''

// Câu hiện nguyên văn, mỗi từ một dòng chọn có mã A, B, C…; chọn từ nào thì hiện ô "Sửa từ đã chọn" (Figma 10.38).
export function SpotFix({ question, value, onChange, disabled }: QuestionProps) {
  const tokens = question.payload.tokens as string[]
  const position = value?.position as number | undefined
  const correction = typeof value?.correction === 'string' ? value.correction : ''
  return (
    <div className="flex flex-col gap-3">
      <p className="text-lg font-semibold leading-7 text-ink">{tokens.join(' ')}</p>
      <div role="radiogroup" aria-label="Chọn từ sai" className="flex flex-col gap-3">
        {tokens.map((t, i) => (
          <button key={i} type="button" role="radio" aria-checked={position === i} disabled={disabled} onClick={() => onChange({ position: i, correction: position === i ? correction : '' })} className={option(position === i)}>
            <span className={optionCode}>{String.fromCharCode(65 + (i % 26))}</span>
            <span>{t}</span>
          </button>
        ))}
      </div>
      {position != null && (
        <label className={`${fieldLabel} flex flex-col gap-1.5`}>
          Sửa từ đã chọn
          <input value={correction} maxLength={100} disabled={disabled} placeholder={`Sửa “${tokens[position]}” thành…`} onChange={(e) => onChange({ position, correction: e.target.value })} className={field} />
        </label>
      )}
    </div>
  )
}
