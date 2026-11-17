import type { Answer, Id, QuestionProps } from '../types'
import { panel, panelTitle } from './ui'

const orderOf = (v: Answer | undefined) => (v?.order ?? []) as Id[]
export const buildAnswered = (v: Answer | undefined) => orderOf(v).length > 0

const slot = 'w-full rounded-[12px] px-3 py-3 text-left text-[15px] font-bold text-ink md:max-w-[510px]'

// Vùng ghép câu (các ô đánh số) và Kho khối (Figma 415:8036): chạm khối ở kho để đặt, chạm khối đã đặt để trả về.
export function Build({ question, value, onChange, disabled }: QuestionProps) {
  const blocks = question.payload.blocks as { id: Id; text: string }[]
  const order = orderOf(value)
  const textOf = (id: Id) => blocks.find((b) => String(b.id) === String(id))?.text
  const emit = (o: Id[]) => onChange(o.length ? { order: o } : undefined)
  const placed = new Set(order.map(String))
  const pool = blocks.filter((b) => !placed.has(String(b.id)))
  return (
    <div className="flex flex-col gap-4">
      <section aria-label="Vùng ghép câu" className={panel}>
        <h3 className="text-[19px] font-black text-ink">Vùng ghép câu</h3>
        <ol className="flex flex-col gap-3">
          {blocks.map((_, i) => {
            const id = order[i]
            return (
              <li key={i}>
                {id != null ? (
                  <button type="button" disabled={disabled} aria-label={`Bỏ khối ${textOf(id)}`} onClick={() => emit(order.filter((_, j) => j !== i))} className={`${slot} bg-brand-soft`}>
                    {i + 1}. {textOf(id)}
                  </button>
                ) : (
                  <p className={`${slot} bg-muted`}>{i + 1}. Ô trống</p>
                )}
              </li>
            )
          })}
        </ol>
        <p className="text-sm text-ink-secondary">Chạm khối đã đặt để trả về kho.</p>
      </section>
      <section aria-label="Kho khối" className={panel}>
        <h3 className={panelTitle}>Kho khối</h3>
        <div className="flex flex-col gap-3 md:items-start">
          {pool.length === 0 && <p className="text-sm text-ink-secondary">Đã đặt hết các khối.</p>}
          {pool.map((b) => (
            <button key={b.id} type="button" disabled={disabled} onClick={() => emit([...order, b.id])} className={`${slot} bg-muted md:w-80`}>
              {b.text}
            </button>
          ))}
        </div>
      </section>
    </div>
  )
}
