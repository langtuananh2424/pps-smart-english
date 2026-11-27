import type { KeyboardEvent } from 'react'
import type { Answer, Id, QuestionProps } from '../types'
import { panel, panelTitle } from './ui'

type Letter = { id: Id; ch: string }
const orderOf = (v: Answer | undefined) => (v?.order ?? []) as Id[]
export const letterOrderAnswered = (v: Answer | undefined) => orderOf(v).length > 0

const tile = 'grid size-12 place-items-center rounded-md text-xl font-black uppercase text-ink disabled:opacity-60'

// D-154 (Figma 10.11): chạm chữ ở kho để xếp vào vùng ghép, chạm chữ đã xếp (ở vùng hoặc ở kho) để gỡ. Phím Backspace gỡ chữ cuối.
export function LetterOrder({ question, value, onChange, disabled }: QuestionProps) {
  const letters = (question.payload?.letters ?? []) as Letter[]
  const order = orderOf(value)
  const ch = (id: Id) => letters.find((l) => String(l.id) === String(id))?.ch ?? ''
  const emit = (o: Id[]) => onChange(o.length ? { order: o } : undefined)
  const toggle = (id: Id) => emit(order.some((x) => String(x) === String(id)) ? order.filter((x) => String(x) !== String(id)) : [...order, id])
  const onKey = (e: KeyboardEvent) => {
    if (e.key === 'Backspace' && !disabled && order.length) emit(order.slice(0, -1))
  }
  return (
    <div className="flex flex-col gap-4" onKeyDown={onKey}>
      <section aria-label="Vùng ghép chữ" className={panel}>
        <h3 className={panelTitle}>Từ của bạn</h3>
        <ol className="flex flex-wrap gap-2">
          {letters.map((_, i) => (
            <li key={i}>
              {order[i] != null ? (
                <button type="button" disabled={disabled} aria-label={`Bỏ chữ ${ch(order[i])} ở vị trí ${i + 1}`} onClick={() => toggle(order[i])} className={`${tile} bg-brand-soft`}>{ch(order[i])}</button>
              ) : (
                <span aria-label={`Ô trống ${i + 1}`} className={`${tile} border-[1.5px] border-dashed border-line-dashed`} />
              )}
            </li>
          ))}
        </ol>
      </section>
      <section aria-label="Kho chữ" className={panel}>
        <h3 className={panelTitle}>Kho chữ</h3>
        <div className="flex flex-wrap gap-2">
          {letters.map((l) => {
            const used = order.some((x) => String(x) === String(l.id))
            return (
              <button key={l.id} type="button" disabled={disabled} aria-pressed={used} aria-label={`Chữ ${l.ch}`} onClick={() => toggle(l.id)} className={`${tile} ${used ? 'bg-muted opacity-50' : 'bg-brand-soft'}`}>{l.ch}</button>
            )
          })}
        </div>
        <p className="text-sm text-ink-secondary">Chạm chữ để xếp, chạm lại để gỡ.</p>
      </section>
    </div>
  )
}
