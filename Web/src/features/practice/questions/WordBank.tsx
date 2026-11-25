import type { Answer, QuestionProps } from '../types'
import { field, fieldLabel, token } from './ui'

type Blanks = Record<string, string>
const blanksOf = (v: Answer | undefined) => (v?.blanks ?? {}) as Blanks

export const wordBankAnswered = (v: Answer | undefined) => Object.values(blanksOf(v)).some((s) => s)

// Hộp từ ở trên, mỗi chỗ trống một ô chọn; allowReuse=false thì từ đã dùng ở chỗ khác bị khóa.
export function WordBank({ question, value, onChange, disabled }: QuestionProps) {
  const { text, blanks: ids, words, allowReuse } = question.payload as { text: string; blanks: string[]; words: string[]; allowReuse?: boolean }
  const blanks = blanksOf(value)
  const used = new Set(Object.values(blanks).filter(Boolean))
  // Mỗi chỗ trống lấy câu chứa nó làm nhãn.
  const lines = new Map<string, string>()
  for (const s of String(text).split(/\n|(?<=[.!?])\s+/)) {
    for (const m of s.matchAll(/\{([^}]+)\}/g)) lines.set(m[1], s.replace(/\{[^}]+\}/g, '____'))
  }
  const set = (id: string, v: string) => {
    const next = { ...blanks, [id]: v }
    onChange(Object.values(next).some((s) => s) ? { blanks: next } : undefined)
  }
  return (
    <div className="flex flex-col gap-4">
      <div aria-label="Hộp từ" className="flex flex-wrap gap-2 rounded-md bg-subtle p-3">
        {words.map((w) => (
          <span key={w} className={`${token(used.has(w))} grid place-items-center text-lg ${!allowReuse && used.has(w) ? 'opacity-50' : ''}`}>{w}</span>
        ))}
      </div>
      {ids.map((id, i) => (
        <label key={id} className={`${fieldLabel} flex flex-col gap-1.5`}>
          {i + 1}. {lines.get(id) ?? `Chỗ trống ${id}`}
          <select value={blanks[id] ?? ''} disabled={disabled} onChange={(e) => set(id, e.target.value)} className={field}>
            <option value="">Chọn từ…</option>
            {words.map((w) => (
              <option key={w} value={w} disabled={!allowReuse && used.has(w) && blanks[id] !== w}>{w}</option>
            ))}
          </select>
        </label>
      ))}
    </div>
  )
}
