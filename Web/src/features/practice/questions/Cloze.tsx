import type { Answer, QuestionProps } from '../types'
import { field, fieldLabel } from './ui'

type Blanks = Record<string, string>
const blanksOf = (v: Answer | undefined) => (v?.blanks ?? {}) as Blanks

export const clozeAnswered = (v: Answer | undefined) => Object.values(blanksOf(v)).some((s) => s.trim())

// Đoạn văn hiện chỗ trống dạng (id) ____, mỗi chỗ trống một ô nhập riêng bên dưới (Figma 10.09).
export function Cloze({ question, value, onChange, disabled }: QuestionProps) {
  const blanks = blanksOf(value)
  const ids = question.payload.blanks as string[]
  const labels = (question.payload.labels ?? {}) as Record<string, string> // D-70, tùy chọn
  const text = String(question.payload.text).replace(/\{([^}]+)\}/g, '($1) ____')
  const set = (id: string, v: string) => {
    const next = { ...blanks, [id]: v }
    onChange(Object.values(next).some((s) => s.trim()) ? { blanks: next } : undefined)
  }
  return (
    <div className="flex flex-col gap-4">
      <p className="text-lg font-semibold leading-7 text-ink">{text}</p>
      {ids.map((id) => (
        <label key={id} className={`${fieldLabel} flex flex-col gap-1.5`}>
          {labels[id] ?? `Chỗ trống ${id}`}
          <input value={blanks[id] ?? ''} disabled={disabled} maxLength={100} placeholder="Nhập câu trả lời…" onChange={(e) => set(id, e.target.value)} className={field} />
        </label>
      ))}
    </div>
  )
}
