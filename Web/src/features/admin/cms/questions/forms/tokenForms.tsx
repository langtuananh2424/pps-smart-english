import { useState } from 'react'
import { IdTextList, Labeled, LinesField, smBtn, smInput } from '../fields'
import { nextId, tokenize } from '../util'
import type { EditorProps } from './props'

// Ô nhập câu: tách từ theo khoảng trắng; đổi số từ thì xóa đáp án cũ vì chỉ số từ không còn đúng.
function SentenceInput({ tokens, onTokens }: { tokens: string[]; onTokens: (t: string[]) => void }) {
  const [text, setText] = useState(tokens.join(' '))
  return (
    <Labeled label="Câu" htmlFor="sentence" hint="Hệ thống tự tách thành từng từ.">
      <input
        id="sentence"
        value={text}
        className={smInput}
        onChange={(e) => {
          setText(e.target.value)
          const next = tokenize(e.target.value)
          if (next.join(' ') !== tokens.join(' ')) onTokens(next)
        }}
      />
    </Labeled>
  )
}

const chip = 'min-h-9 rounded-sm border px-2.5 font-bold'

// Nhận diện cấu trúc: bấm từ đầu rồi từ cuối để chọn cụm, gán yếu tố ngữ pháp đang chọn.
export function IdentifyEditor({ draft, patch, elements }: EditorProps) {
  const tokens: string[] = draft.payload.tokens ?? []
  const spans: { start: number; end: number; element: string }[] = draft.answerKey.spans ?? []
  const [start, setStart] = useState<number | null>(null)
  const [element, setElement] = useState(elements[0]?.code ?? '')
  const color = (code: string) => elements.find((e) => e.code === code)?.colorHex
  const click = (i: number) => {
    if (start === null) return setStart(i)
    patch({ answerKey: { spans: [...spans, { start: Math.min(start, i), end: Math.max(start, i), element }] } })
    setStart(null)
  }
  const covering = (i: number) => spans.filter((s) => s.start <= i && i <= s.end).map((s) => color(s.element) ?? '#999')
  return (
    <div className="flex flex-col gap-3">
      <SentenceInput tokens={tokens} onTokens={(t) => patch({ payload: { tokens: t }, answerKey: { spans: [] } })} />
      <Labeled label="Yếu tố ngữ pháp cho cụm sắp chọn" htmlFor="el">
        <select id="el" className={smInput} value={element} onChange={(e) => setElement(e.target.value)}>
          {elements.map((e) => (
            <option key={e.code} value={e.code}>
              {e.name}
            </option>
          ))}
        </select>
      </Labeled>
      <div className="flex flex-wrap gap-1.5" role="group" aria-label="Các từ của câu">
        {tokens.map((t, i) => (
          <button
            key={i}
            type="button"
            aria-pressed={start === i}
            onClick={() => click(i)}
            className={`${chip} ${start === i ? 'border-brand bg-subtle' : 'border-line bg-surface'}`}
            style={covering(i).length ? { borderBottom: `4px solid ${covering(i).at(-1)}` } : undefined}
          >
            {t}
          </button>
        ))}
      </div>
      <p className="text-sm font-semibold text-ink-secondary">{start === null ? 'Bấm từ đầu của cụm.' : 'Bấm từ cuối của cụm.'}</p>
      <ul className="flex flex-col gap-1.5">
        {spans.map((s, i) => (
          <li key={i} className="flex items-center gap-2 font-semibold text-ink">
            <span>
              {tokens.slice(s.start, s.end + 1).join(' ')} → {elements.find((e) => e.code === s.element)?.name ?? s.element}
            </span>
            <button type="button" className={`${smBtn} ml-auto`} aria-label={`Xóa cụm ${i + 1}`} onClick={() => patch({ answerKey: { spans: spans.filter((_, j) => j !== i) } })}>
              Xóa
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

// Tìm và sửa lỗi: bấm đúng một từ sai, nhập các cách sửa chấp nhận.
export function SpotFixEditor({ draft, patch }: EditorProps) {
  const tokens: string[] = draft.payload.tokens ?? []
  const key = draft.answerKey
  const setKey = (o: object) => patch({ answerKey: { ...key, ...o } })
  return (
    <div className="flex flex-col gap-3">
      <SentenceInput tokens={tokens} onTokens={(t) => patch({ payload: { tokens: t }, answerKey: { corrections: key.corrections ?? [] } })} />
      <div className="flex flex-wrap gap-1.5" role="group" aria-label="Chọn từ sai">
        {tokens.map((t, i) => (
          <button key={i} type="button" aria-pressed={key.position === i} onClick={() => setKey({ position: i })} className={`${chip} ${key.position === i ? 'border-danger bg-danger-bg text-danger-fg' : 'border-line bg-surface'}`}>
            {t}
          </button>
        ))}
      </div>
      <LinesField id="corrections" label="Cách sửa chấp nhận" value={key.corrections ?? []} onChange={(corrections) => setKey({ corrections })} />
    </div>
  )
}

// Xếp khối: thứ tự trong danh sách chính là thứ tự đúng.
export function BuildEditor({ draft, patch }: EditorProps) {
  const blocks: { id: string; text: string }[] = draft.payload.blocks ?? []
  const [sentence, setSentence] = useState('')
  const apply = (b: typeof blocks) => patch({ payload: { blocks: b }, answerKey: { order: b.map((x) => x.id) } })
  const split = () => {
    const words = tokenize(sentence)
    const ids: string[] = []
    apply(words.map((text) => ({ id: (ids.push(nextId(ids, 'b')), ids.at(-1) as string), text })))
  }
  return (
    <div className="flex flex-col gap-3">
      <Labeled label="Câu đúng (để tách khối nhanh)" htmlFor="build-sentence">
        <div className="flex gap-2">
          <input id="build-sentence" value={sentence} onChange={(e) => setSentence(e.target.value)} className={smInput} />
          <button type="button" className={smBtn} onClick={split}>
            Tách khối
          </button>
        </div>
      </Labeled>
      <IdTextList label="Khối" prefix="b" min={2} movable items={blocks} onChange={apply} />
    </div>
  )
}
