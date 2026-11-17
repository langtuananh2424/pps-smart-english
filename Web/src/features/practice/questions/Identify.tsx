import { useState } from 'react'
import { useGrammarElements } from '../elements'
import type { Answer, QuestionProps } from '../types'
import { panel, panelTitle, token } from './ui'

export interface Span { start: number; end: number; element: string }
export const spansOf = (v: Answer | null | undefined) => (v?.spans ?? []) as Span[]
export const identifyAnswered = (v: Answer | undefined) => spansOf(v).length > 0
export const spanText = (tokens: string[], s: { start: number; end: number }) => tokens.slice(s.start, s.end + 1).join(' ')

// Dòng từ với gạch chân màu theo nhãn; cụm lồng nhau thì mỗi nhãn một gạch xếp chồng (Figma 415:7696, 415:8162).
export function SpanTokens({ tokens, spans, selected, onPick, disabled, size = 'h-11' }: {
  tokens: string[]; spans: Span[]; selected?: (i: number) => boolean; onPick?: (i: number) => void; disabled?: boolean; size?: string
}) {
  const { color: elementColor } = useGrammarElements()
  return (
    <div role="group" aria-label="Các từ trong câu" className="flex flex-wrap gap-1">
      {tokens.map((t, i) => {
        const marks = spans.filter((s) => i >= s.start && i <= s.end)
        const cls = `${token(!!selected?.(i) || (!onPick && marks.length > 0))} ${size} flex min-w-11 flex-col items-center justify-center gap-0.5`
        const inner = (
          <>
            {t}
            {marks.map((m, k) => <span key={k} aria-hidden="true" className="h-0.5 w-full rounded-[1px]" style={{ background: elementColor(m.element) }} />)}
          </>
        )
        return onPick ? (
          <button key={i} type="button" disabled={disabled} aria-pressed={!!selected?.(i)} onClick={() => onPick(i)} className={cls}>{inner}</button>
        ) : (
          <span key={i} className={cls}>{inner}</span>
        )
      })}
    </div>
  )
}

// Chạm từ đầu rồi từ cuối để chọn cụm (chạm một từ hai lần = cụm một từ), rồi chọn nhãn yếu tố.
export function Identify({ question, value, onChange, disabled }: QuestionProps) {
  const { list: grammarElements, name: elementName, color: elementColor } = useGrammarElements()
  const tokens = question.payload.tokens as string[]
  const spanCount = question.payload.spanCount as number | undefined
  const spans = spansOf(value)
  const [anchor, setAnchor] = useState<number | null>(null)
  const [range, setRange] = useState<[number, number] | null>(null)
  const emit = (s: Span[]) => onChange(s.length ? { spans: s } : undefined)

  const click = (i: number) => {
    if (anchor == null || range) {
      setAnchor(i)
      setRange(null)
    } else {
      setRange([Math.min(anchor, i), Math.max(anchor, i)])
      setAnchor(null)
    }
  }
  const selected = (i: number) => (range ? i >= range[0] && i <= range[1] : i === anchor)
  const assign = (element: string) => {
    if (!range) return
    emit([...spans, { start: range[0], end: range[1], element }])
    setRange(null)
  }
  return (
    <div className="flex flex-col gap-4">
      <section aria-label="Câu cần gắn nhãn" className={panel}>
        <SpanTokens tokens={tokens} spans={spans} selected={selected} onPick={click} disabled={disabled} />
        <p className="text-sm font-bold text-ink-secondary">
          {range ? `Cụm đang chọn: ${spanText(tokens, { start: range[0], end: range[1] })}` : spanCount != null ? `Tìm ${spanCount} cụm. Chạm từ đầu, rồi chạm từ cuối của cụm.` : 'Chạm từ đầu, rồi chạm từ cuối của cụm.'}
        </p>
      </section>
      {range && (
        <section aria-label="Gắn nhãn cho cụm" className={panel}>
          <h3 className="text-lg font-black text-ink">Gắn nhãn cho cụm</h3>
          <div className="flex flex-wrap gap-2 md:flex-col md:items-start">
            {grammarElements.map((e) => (
              <button key={e.code} type="button" onClick={() => assign(e.code)} style={{ color: e.colorHex ?? elementColor(e.code) }} className="min-h-11 rounded-full bg-muted px-5 text-[13px] font-bold hover:bg-brand-soft">
                {e.name}
              </button>
            ))}
          </div>
        </section>
      )}
      <section aria-label="Các cụm đã chọn" className={panel}>
        <h3 className={panelTitle}>Cụm đã chọn</h3>
        {spans.length === 0 ? (
          <p className="text-sm text-ink-secondary">Chưa có cụm nào.</p>
        ) : (
          <ul className="flex flex-col gap-3">
            {spans.map((s, i) => (
              <li key={i} className="flex flex-col items-start gap-1">
                <p className="w-full rounded-[12px] bg-muted p-3 text-[15px] font-bold text-ink">
                  {spanText(tokens, s)} → {elementName(s.element)}
                </p>
                <button type="button" disabled={disabled} aria-label={`Xóa cụm ${spanText(tokens, s)}`} onClick={() => emit(spans.filter((_, j) => j !== i))} className="min-h-11 rounded-full px-5 text-[13px] font-bold text-danger-fg">
                  Xóa cụm
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
