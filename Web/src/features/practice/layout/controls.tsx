import { useId } from 'react'
import { wordCount } from '../questions/FillText'
import { field } from '../questions/ui'
import type { Answer, Answers, GroupLayout, Question } from '../types'

// Một câu gắn vào bố cục: n là số thứ tự hiển thị (toàn bài ở khung IELTS, trong bộ ở màn luyện tập).
export interface LayoutItem { question: Question; n: number; disabled?: boolean; flagged?: boolean }
export const slotOf = (q: Question) => String(q.payload?.slot ?? '')

export interface PickOption { id: string; text: string }
export const pickKey = (q: Pick<Question, 'type'>) => (q.type === 'IELTS_SUMMARY_BANK' ? 'word' : 'option')
export const pickAnswered = (v: Answer | undefined) => ['option', 'word'].some((k) => typeof v?.[k] === 'string' && v[k] !== '')

// Lựa chọn của câu chọn từ bố cục nhóm: A–H, i–x, từ trong hộp, điểm A–F trên bản đồ.
export function pickOptions(layout?: GroupLayout | null): PickOption[] {
  if (!layout) return []
  if (layout.kind === 'OPTIONS') return layout.options.map((o) => ({ id: o.id, text: `${o.id}. ${o.text}` }))
  if (layout.kind === 'TEXT_BANK') return layout.words
  if (layout.kind === 'MAP') return layout.options.map((o) => ({ id: o.id, text: o.id }))
  return []
}

const cellCls = 'h-9 w-32 max-w-full rounded-sm border-[1.5px] border-line-input bg-input px-2 text-[15px] font-semibold text-ink outline-none focus:border-brand disabled:opacity-60 aria-[invalid=true]:border-red-500'

export function PickSelect({ label, value, options, disabled, compact, onChange }: { label: string; value?: string; options: PickOption[]; disabled?: boolean; compact?: boolean; onChange: (v: string | undefined) => void }) {
  return (
    <select aria-label={label} value={value ?? ''} disabled={disabled} onChange={(e) => onChange(e.target.value || undefined)} className={compact ? cellCls : field}>
      <option value="">Chọn…</option>
      {options.map((o) => (
        <option key={o.id} value={o.id}>{o.text}</option>
      ))}
    </select>
  )
}

interface CellProps { item: LayoutItem; layout: GroupLayout; answers: Answers; onChange: (id: number, v: Answer | undefined) => void; onFocus?: (id: number) => void; anchor?: (id: number) => string }

// Ô của một câu trong bố cục: số câu + ô nhập (hoặc ô chọn từ ở hộp từ). Nhãn "Câu N" cho trình đọc màn hình.
export function SlotCell({ item, layout, answers, onChange, onFocus, anchor }: CellProps) {
  const q = item.question
  const label = `Câu ${item.n}`
  const hint = useId()
  const text = String(answers[q.id]?.text ?? '')
  const max = q.payload?.maxWords as number | undefined
  const over = max != null && wordCount(text) > max
  const body =
    layout.kind === 'TEXT_BANK' ? (
      <PickSelect compact label={label} value={answers[q.id]?.word as string | undefined} options={layout.words} disabled={item.disabled} onChange={(v) => onChange(q.id, v ? { word: v } : undefined)} />
    ) : (
      <input aria-label={label} aria-invalid={over || undefined} aria-describedby={max != null ? hint : undefined} value={text} disabled={item.disabled} maxLength={200} onChange={(e) => onChange(q.id, e.target.value.trim() ? { text: e.target.value } : undefined)} className={cellCls} />
    )
  return (
    <span id={anchor?.(q.id)} onFocusCapture={() => onFocus?.(q.id)} onPointerDown={() => onFocus?.(q.id)} className={`inline-flex items-center gap-1 align-middle ${item.flagged ? 'rounded-sm ring-2 ring-amber-400' : ''}`}>
      <span aria-hidden className="grid size-6 shrink-0 place-items-center rounded-sm bg-subtle text-xs font-extrabold text-brand-strong">{item.n}</span>
      {body}
      {max != null && <span id={hint} className={`sr-only`}>Tối đa {max} từ</span>}
    </span>
  )
}
