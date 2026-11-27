import type { ReactNode } from 'react'
import type { Answer, Answers, GroupLayout } from '../types'
import { panel, panelTitle } from '../questions/ui'
import { SlotCell, slotOf, type LayoutItem } from './controls'
import { ImageStage } from './ImageStage'
import { HtmlWithSlots, SlotText, slotsIn } from './slots'

type Inline = Exclude<GroupLayout, { kind: 'OPTIONS' | 'MAP' }>
export const isInlineLayout = (l?: GroupLayout | null): l is Inline => !!l && l.kind !== 'OPTIONS' && l.kind !== 'MAP'

interface Props {
  layout: Inline
  items: LayoutItem[]
  answers: Answers
  onChange: (questionId: number, v: Answer | undefined) => void
  onFocus?: (questionId: number) => void
  anchor?: (questionId: number) => string
}

const html = 'break-words text-base leading-8 text-ink [&_img]:h-auto [&_img]:max-w-full [&_p]:my-1 [&_ul]:list-disc [&_ul]:pl-5'

// Slot có trong bố cục (để câu không có chỗ vẫn được hiện ở cuối, không bị mất).
function layoutSlots(l: Inline): string[] {
  switch (l.kind) {
    case 'TEXT':
    case 'TEXT_BANK': return slotsIn(l.html)
    case 'NOTE': return l.lines.flatMap((x) => slotsIn(x.html))
    case 'TABLE': return [...l.header, ...l.rows.flat()].flatMap(slotsIn)
    case 'FLOW': return l.steps.flatMap(slotsIn)
    case 'FORM': return l.rows.flatMap((r) => (r.slot ? [r.slot] : []))
    case 'DIAGRAM': return l.points.map((p) => p.slot)
  }
}

// D-154: dựng bảng, ghi chú, sơ đồ khối, phiếu, đoạn tóm tắt, sơ đồ có ô nhập đặt đúng chỗ {slot}; mỗi ô gắn một câu qua payload.slot.
export function GroupLayoutView({ layout, items, ...rest }: Props) {
  const bySlot = new Map(items.map((i) => [slotOf(i.question), i]))
  const cell = (slot: string): ReactNode => {
    const item = bySlot.get(slot)
    return item ? <SlotCell item={item} layout={layout} answers={rest.answers} onChange={rest.onChange} onFocus={rest.onFocus} anchor={rest.anchor} /> : null
  }
  const placed = new Set(layoutSlots(layout))
  const orphans = layout.kind === 'DIAGRAM' ? [] : items.filter((i) => !placed.has(slotOf(i.question)))
  const max = [...new Set(items.map((i) => i.question.payload?.maxWords as number | undefined))]
  const title = 'title' in layout ? layout.title : undefined
  return (
    <section aria-label={title ?? 'Bố cục câu hỏi'} className={panel}>
      {title && <h3 className={panelTitle}>{title}</h3>}
      {layout.kind === 'TEXT_BANK' && <WordBox words={layout.words} />}
      {max.length === 1 && max[0] != null && <p className="text-sm font-semibold text-ink-secondary">Tối đa {max[0]} từ cho mỗi chỗ trống.</p>}
      <Body layout={layout} cell={cell} items={items} />
      {orphans.length > 0 && <div className="flex flex-col gap-2">{orphans.map((i) => <div key={i.question.id}><SlotCell item={i} layout={layout} {...rest} /></div>)}</div>}
    </section>
  )
}

function WordBox({ words }: { words: { id: string; text: string }[] }) {
  return (
    <ul aria-label="Hộp từ" className="flex flex-wrap gap-2 rounded-md bg-muted p-2">
      {words.map((w) => <li key={w.id} className="rounded-sm bg-surface px-2.5 py-1 text-[15px] font-bold text-ink">{w.text}</li>)}
    </ul>
  )
}

function Body({ layout, cell, items }: { layout: Inline; cell: (slot: string) => ReactNode; items: LayoutItem[] }) {
  switch (layout.kind) {
    case 'TEXT':
    case 'TEXT_BANK':
      return <HtmlWithSlots html={layout.html} className={html} cell={cell} />
    case 'NOTE':
      return <ul className="flex flex-col gap-1">{layout.lines.map((l, i) => <li key={i}><HtmlWithSlots html={l.html} className={html} cell={cell} /></li>)}</ul>
    case 'TABLE':
      return (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-left text-base text-ink">
            <thead>
              <tr>{layout.header.map((h, i) => <th key={i} scope="col" className="border border-line bg-muted px-3 py-2 font-extrabold"><SlotText text={h} cell={cell} /></th>)}</tr>
            </thead>
            <tbody>
              {layout.rows.map((row, r) => (
                <tr key={r}>{row.map((c, i) => <td key={i} className="border border-line px-3 py-2 align-middle font-semibold"><SlotText text={c} cell={cell} /></td>)}</tr>
              ))}
            </tbody>
          </table>
        </div>
      )
    case 'FLOW':
      return (
        <ol className="flex flex-col items-stretch gap-1">
          {layout.steps.map((s, i) => (
            <li key={i} className="flex flex-col items-center gap-1">
              <p className="w-full rounded-md border-[1.5px] border-line-brand bg-surface px-3 py-2 text-base font-semibold leading-8 text-ink"><SlotText text={s} cell={cell} /></p>
              {i < layout.steps.length - 1 && <span aria-hidden className="font-black text-brand-strong">↓</span>}
            </li>
          ))}
        </ol>
      )
    case 'FORM':
      return (
        <dl className="grid grid-cols-[minmax(0,2fr)_minmax(0,3fr)] items-center gap-x-3 gap-y-2">
          {layout.rows.map((r, i) => (
            <div key={i} className="contents">
              <dt className="text-base font-extrabold text-ink-secondary">{r.label}</dt>
              <dd className="text-base font-semibold text-ink">{r.slot ? cell(r.slot) : r.value}</dd>
            </div>
          ))}
        </dl>
      )
    case 'DIAGRAM':
      return (
        <div className="flex flex-col gap-3">
          <ImageStage image={layout.image} alt={layout.title ?? 'Sơ đồ'} markers={layout.points.flatMap((p) => { const it = items.find((i) => slotOf(i.question) === p.slot); return it ? [{ key: p.slot, label: String(it.n), x: p.x, y: p.y }] : [] })} />
          <ul className="flex flex-col gap-2">
            {items.map((i) => (
              <li key={i.question.id} className="flex flex-wrap items-center gap-2 text-base font-semibold text-ink">
                {cell(slotOf(i.question))}
                {typeof i.question.payload?.description === 'string' && <span>{i.question.payload.description}</span>}
              </li>
            ))}
          </ul>
        </div>
      )
  }
}
