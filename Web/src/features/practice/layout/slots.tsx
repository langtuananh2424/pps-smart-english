import { useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

const SLOT = /\{([\w-]{1,32})\}/g

// Tách chuỗi chữ thuần thành mảng chữ và tên chỗ trống.
export function splitSlots(text: string): (string | { slot: string })[] {
  const out: (string | { slot: string })[] = []
  let last = 0
  for (const m of text.matchAll(SLOT)) {
    if (m.index > last) out.push(text.slice(last, m.index))
    out.push({ slot: m[1] })
    last = m.index + m[0].length
  }
  if (last < text.length) out.push(text.slice(last))
  return out
}

export const slotsIn = (text: string) => splitSlots(text).flatMap((p) => (typeof p === 'string' ? [] : [p.slot]))

// Chữ thuần: chỗ trống thành nút do `cell` dựng, phần còn lại là text (React tự thoát ký tự).
export function SlotText({ text, cell }: { text: string; cell: (slot: string) => ReactNode }) {
  return <>{splitSlots(text).map((p, i) => (typeof p === 'string' ? <span key={i}>{p}</span> : <span key={i}>{cell(p.slot)}</span>))}</>
}

// HTML đã làm sạch ở máy chủ: thay {slot} bằng thẻ rỗng rồi gắn ô nhập vào đó bằng portal, nên không chèn HTML nào từ người dùng và thẻ không bị cắt ngang.
export function HtmlWithSlots({ html, className, cell }: { html: string; className?: string; cell: (slot: string) => ReactNode }) {
  const host = useRef<HTMLDivElement>(null)
  const [targets, setTargets] = useState<Record<string, HTMLElement[]>>({})
  const marked = html.replace(SLOT, (_, s: string) => `<span data-slot="${s}"></span>`)
  useLayoutEffect(() => {
    const next: Record<string, HTMLElement[]> = {}
    host.current?.querySelectorAll<HTMLElement>('span[data-slot]').forEach((el) => (next[el.dataset.slot!] ??= []).push(el))
    setTargets(next)
  }, [marked])
  return (
    <>
      <div ref={host} className={className} dangerouslySetInnerHTML={{ __html: marked }} />
      {Object.entries(targets).flatMap(([slot, els]) => els.map((el, i) => createPortal(cell(slot), el, `${slot}-${i}`)))}
    </>
  )
}
