import type { ReactNode } from 'react'
import { SIZE_PCT, typeLabel, type Draft, type GrammarElement, type Obj } from './types'

const box = 'rounded-sm border border-line bg-surface px-2.5 py-1.5 font-bold text-ink'
const Html = ({ html }: { html: string }) => <div className="font-semibold text-ink [&_table]:border [&_td]:border [&_th]:border [&_ul]:list-disc [&_ul]:pl-5" dangerouslySetInnerHTML={{ __html: html }} />

// Văn bản có {id}: thay bằng ô trống (kèm nội dung tùy chỗ gọi).
function WithBlanks({ text, render }: { text: string; render: (id: string) => ReactNode }) {
  return (
    <p className="font-semibold leading-8 text-ink">
      {text.split(/(\{[^{}]+\})/).map((part, i) => (/^\{[^{}]+\}$/.test(part) ? <span key={i}>{render(part.slice(1, -1))}</span> : part))}
    </p>
  )
}

function Body({ d, elements }: { d: Draft; elements: GrammarElement[] }) {
  const p: Obj = d.payload
  const list = (xs?: { id: string; text: string }[]) => xs ?? []
  switch (d.type) {
    case 'CONNECTIVE_MCQ':
    case 'CHOICE_MULTI':
      return (
        <ul className="flex flex-col gap-2">
          {d.choices.map((c) => (
            <li key={c.uid} className={`${box} flex gap-2`}>
              <input type={d.type === 'CHOICE_MULTI' ? 'checkbox' : 'radio'} disabled aria-label={`Lựa chọn ${c.label}`} />
              <b>{c.label}.</b>
              <span dangerouslySetInnerHTML={{ __html: c.contentHtml }} />
            </li>
          ))}
        </ul>
      )
    case 'TRUE_FALSE':
      return (
        <div className="flex flex-wrap gap-2">
          {(p.options ?? []).map((o: string) => (
            <span key={o} className={box}>
              {o}
            </span>
          ))}
        </div>
      )
    case 'FILL_TEXT':
      return <input disabled aria-label="Ô gõ chữ" className={`${box} w-full`} placeholder={p.maxWords ? `Tối đa ${p.maxWords} từ` : 'Gõ đáp án'} />
    case 'CONNECTIVE_CLOZE':
      return <WithBlanks text={p.text ?? ''} render={(id) => <input disabled aria-label={`Chỗ trống ${id}`} className="mx-1 w-24 rounded-sm border border-line-input px-1" />} />
    case 'WORD_BANK':
      return (
        <>
          <WithBlanks text={p.text ?? ''} render={(id) => <span className="mx-1 inline-block min-w-16 border-b-2 border-brand text-center">{id}</span>} />
          <div className="flex flex-wrap gap-2">
            {(p.words ?? []).map((w: string) => (
              <span key={w} className={box}>
                {w}
              </span>
            ))}
          </div>
        </>
      )
    case 'CONNECTIVE_MATCH':
    case 'MATCH_OPTIONS': {
      const [a, b] = d.type === 'CONNECTIVE_MATCH' ? [list(p.left), list(p.right)] : [list(p.items), list(p.options)]
      return (
        <div className="grid grid-cols-2 gap-2">
          <ul className="flex flex-col gap-2">{a.map((x) => <li key={x.id} className={box}>{x.text}</li>)}</ul>
          <ul className="flex flex-col gap-2">{b.map((x, i) => <li key={x.id} className={box}>{d.type === 'MATCH_OPTIONS' ? `${String.fromCharCode(65 + i)}. ` : ''}{x.text}</li>)}</ul>
        </div>
      )
    }
    case 'CONNECTIVE_REWRITE':
    case 'GRAMMAR_REWRITE':
      return (
        <>
          <p className={box}>{p.sentence}</p>
          <p className="text-sm font-bold text-ink-secondary">
            {d.type === 'CONNECTIVE_REWRITE' ? `Dùng từ: ${p.requiredWord ?? ''}` : `Cấu trúc: ${elements.find((e) => e.code === p.requiredElement)?.name ?? ''}`}
          </p>
          <textarea disabled aria-label="Câu trả lời" rows={2} className={`${box} w-full`} />
        </>
      )
    case 'GRAMMAR_IDENTIFY':
    case 'GRAMMAR_SPOT_FIX':
      return (
        <div className="flex flex-wrap gap-1.5">
          {(p.tokens ?? []).map((t: string, i: number) => (
            <span key={i} className={`${box} ${d.type === 'GRAMMAR_SPOT_FIX' && d.answerKey.position === i ? 'border-danger bg-danger-bg' : ''}`}>
              {t}
            </span>
          ))}
        </div>
      )
    case 'GRAMMAR_BUILD':
      return (
        <div className="flex flex-wrap gap-2" aria-label="Các khối đã xáo">
          {[...list(p.blocks)].reverse().map((b) => (
            <span key={b.id} className={box}>
              {b.text}
            </span>
          ))}
        </div>
      )
    case 'WRITING':
      return (
        <>
          <p className="font-semibold text-ink">{p.task}</p>
          <p className="text-sm font-bold text-ink-secondary">Yêu cầu {p.requiredWords} từ · chấm bằng AI</p>
          <textarea disabled aria-label="Bài viết" rows={4} className={`${box} w-full`} />
        </>
      )
    case 'SPEAKING':
      return (
        <>
          <p className="font-bold text-ink-secondary">Part {p.part}</p>
          <p className="font-semibold text-ink">{p.prompt}</p>
          {p.cueCard && <ul className="list-disc pl-5 font-semibold text-ink">{p.cueCard.map((c: string) => <li key={c}>{c}</li>)}</ul>}
        </>
      )
  }
}

// Xem trước đúng những gì học viên thấy (không có đáp án), cập nhật theo biểu mẫu.
export function Preview({ draft, elements }: { draft: Draft; elements: GrammarElement[] }) {
  return (
    <aside aria-label="Xem trước" className="flex flex-col gap-3 rounded-lg border border-line-brand bg-subtle p-4">
      <h2 className="text-lg font-black text-ink">Xem trước</h2>
      <p className="text-sm font-extrabold text-brand-strong">{typeLabel(draft.type)}</p>
      {draft.instruction && <p className="font-bold text-ink-secondary">{draft.instruction}</p>}
      <Html html={draft.promptHtml} />
      {draft.media.map((m) => (
        <img key={m.assetId} src={m.url} alt={m.altText} style={{ width: `${SIZE_PCT[m.displaySize] ?? 50}%` }} />
      ))}
      <Body d={draft} elements={elements} />
    </aside>
  )
}
