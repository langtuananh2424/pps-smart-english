import type { Answer, QuestionProps } from '../types'
import { field, fieldLabel, optionCode } from './ui'

type Item = { id: string; text: string }
const matchesOf = (v: Answer | undefined) => (v?.matches ?? {}) as Record<string, string>
const code = (i: number) => String.fromCharCode(65 + i)

export const matchOptionsAnswered = (v: Answer | undefined) => Object.values(matchesOf(v)).some(Boolean)

// Ngân hàng đáp án A, B… ở trên; mỗi mục một ô chọn đáp án.
export function MatchOptions({ question, value, onChange, disabled }: QuestionProps) {
  const { items, options } = question.payload as { items: Item[]; options: Item[] }
  const matches = matchesOf(value)
  const set = (id: string, v: string) => {
    const next = { ...matches, [id]: v }
    if (!v) delete next[id]
    onChange(Object.keys(next).length ? { matches: next } : undefined)
  }
  return (
    <div className="flex flex-col gap-4">
      <ul aria-label="Ngân hàng đáp án" className="flex flex-col gap-2 rounded-md bg-subtle p-3">
        {options.map((o, i) => (
          <li key={o.id} className="flex items-center gap-2.5 text-base font-semibold text-ink">
            <span className={optionCode}>{code(i)}</span>
            {o.text}
          </li>
        ))}
      </ul>
      {items.map((it, i) => (
        <label key={it.id} className={`${fieldLabel} flex flex-col gap-1.5`}>
          {i + 1}. {it.text}
          <select value={matches[it.id] ?? ''} disabled={disabled} onChange={(e) => set(it.id, e.target.value)} className={field}>
            <option value="">Chọn đáp án…</option>
            {options.map((o, j) => (
              <option key={o.id} value={o.id}>{code(j)}. {o.text}</option>
            ))}
          </select>
        </label>
      ))}
    </div>
  )
}
