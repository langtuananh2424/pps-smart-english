import type { Answer, Id, QuestionProps } from '../types'
import { field, fieldLabel } from './ui'

type Pairs = Record<string, Id>
const pairsOf = (v: Answer | undefined) => (v?.pairs ?? {}) as Pairs

export const matchAnswered = (v: Answer | undefined) => Object.keys(pairsOf(v)).length > 0

// Mỗi vế trái một ô chọn "Vế còn lại" (Figma 10.36); một vế phải chỉ ghép được một vế trái.
export function Match({ question, value, onChange, disabled }: QuestionProps) {
  const pairs = pairsOf(value)
  const { left: lefts = [], right: rights = [] } = question.payload as { left: { id: Id; text: string }[]; right: { id: Id; text: string }[] }
  const set = (leftId: Id, rightId: string) => {
    const { [String(leftId)]: _old, ...rest } = pairs
    const next = rightId === '' ? rest : { ...rest, [String(leftId)]: rights.find((r) => String(r.id) === rightId)!.id }
    onChange(Object.keys(next).length ? { pairs: next } : undefined)
  }
  const takenBy = (rightId: Id) => Object.entries(pairs).find(([, r]) => String(r) === String(rightId))?.[0]

  return (
    <div className="flex flex-col gap-5">
      {lefts.map((l) => (
        <div key={l.id} className="flex flex-col gap-1.5">
          <p className="text-lg font-black leading-6 text-ink">{l.text}</p>
          <label className={`${fieldLabel} flex flex-col gap-1.5`}>
            Vế còn lại
            <span className="relative block">
              <select aria-label={`Vế còn lại của: ${l.text}`} value={pairs[String(l.id)] != null ? String(pairs[String(l.id)]) : ''} disabled={disabled} onChange={(e) => set(l.id, e.target.value)} className={`${field} appearance-none pr-10`}>
                <option value="">Chọn vế câu</option>
                {rights.map((r) => {
                  const owner = takenBy(r.id)
                  return (
                    <option key={r.id} value={String(r.id)} disabled={owner != null && owner !== String(l.id)}>
                      {r.text}
                    </option>
                  )
                })}
              </select>
              <span aria-hidden="true" className="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-ink-secondary">⌄</span>
            </span>
          </label>
        </div>
      ))}
    </div>
  )
}
