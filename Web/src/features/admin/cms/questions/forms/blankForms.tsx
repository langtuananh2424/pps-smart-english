import { useRef } from 'react'
import { LinesField, Labeled, smBtn, smInput } from '../fields'
import type { Obj } from '../types'
import { placeholders } from '../util'
import { ScoringSelect } from './choiceForms'
import type { EditorProps } from './props'

interface BlankTextProps {
  text: string
  onChange: (text: string) => void
}

// Ô văn bản có chỗ trống {id}; nút chèn đặt {n} (số nhỏ nhất chưa dùng) tại con trỏ.
function BlankText({ text, onChange }: BlankTextProps) {
  const ref = useRef<HTMLTextAreaElement>(null)
  const insert = () => {
    const used = placeholders(text)
    let n = 1
    while (used.includes(String(n))) n++
    const el = ref.current
    const at = el?.selectionStart ?? text.length
    onChange(`${text.slice(0, at)}{${n}}${text.slice(el?.selectionEnd ?? at)}`)
  }
  return (
    <Labeled label="Văn bản" htmlFor="blank-text" hint="Chỗ trống viết dạng {1}, {2}...">
      <textarea ref={ref} id="blank-text" rows={4} value={text} onChange={(e) => onChange(e.target.value)} className={`${smInput} py-2`} />
      <button type="button" className={`${smBtn} self-start`} onClick={insert}>
        Chèn chỗ trống
      </button>
    </Labeled>
  )
}

// Cập nhật text thì suy lại danh sách chỗ trống; giữ đáp án của chỗ còn tồn tại.
const withText = (draft: EditorProps['draft'], text: string, empty: unknown) => {
  const blanks = placeholders(text)
  const old: Obj = draft.answerKey.blanks ?? {}
  return {
    payload: { ...draft.payload, text, blanks },
    answerKey: { blanks: Object.fromEntries(blanks.map((b) => [b, old[b] ?? empty])) },
  }
}

export function ClozeEditor({ draft, patch }: EditorProps) {
  const blanks: string[] = draft.payload.blanks ?? []
  const labels: Obj = draft.payload.labels ?? {}
  const answers: Obj = draft.answerKey.blanks ?? {}
  const setLabel = (id: string, v: string) => {
    const next = { ...labels, [id]: v }
    if (!v) delete next[id]
    const payload: Obj = { ...draft.payload }
    if (Object.keys(next).length) payload.labels = next
    else delete payload.labels
    patch({ payload })
  }
  return (
    <div className="flex flex-col gap-3">
      <BlankText text={draft.payload.text ?? ''} onChange={(t) => patch(withText(draft, t, []))} />
      {blanks.map((b) => (
        <div key={b} className="flex flex-col gap-2 rounded-sm border border-line-subtle p-3">
          <LinesField id={`blank-${b}`} label={`Đáp án chấp nhận cho chỗ trống {${b}}`} value={answers[b] ?? []} onChange={(v) => patch({ answerKey: { blanks: { ...answers, [b]: v } } })} />
          <input aria-label={`Tên chỗ trống {${b}}`} placeholder="Tên chỗ trống (không bắt buộc)" maxLength={100} value={labels[b] ?? ''} onChange={(e) => setLabel(b, e.target.value)} className={smInput} />
        </div>
      ))}
    </div>
  )
}

export function WordBankEditor({ draft, patch }: EditorProps) {
  const blanks: string[] = draft.payload.blanks ?? []
  const words: string[] = draft.payload.words ?? []
  const answers: Obj = draft.answerKey.blanks ?? {}
  return (
    <div className="flex flex-col gap-3">
      <BlankText text={draft.payload.text ?? ''} onChange={(t) => patch(withText(draft, t, ''))} />
      <LinesField id="words" label="Hộp từ" value={words} onChange={(w) => patch({ payload: { ...draft.payload, words: w } })} />
      <label className="flex min-h-10 items-center gap-2 font-extrabold text-ink">
        <input type="checkbox" checked={!!draft.payload.allowReuse} onChange={(e) => patch({ payload: { ...draft.payload, allowReuse: e.target.checked } })} />
        Cho dùng lại một từ ở nhiều chỗ trống
      </label>
      <ScoringSelect id="scoring" value={draft.payload.scoring} onChange={(scoring) => patch({ payload: { ...draft.payload, scoring } })} />
      {blanks.map((b) => (
        <Labeled key={b} label={`Từ đúng cho chỗ trống {${b}}`} htmlFor={`wb-${b}`}>
          <select id={`wb-${b}`} className={smInput} value={answers[b] ?? ''} onChange={(e) => patch({ answerKey: { blanks: { ...answers, [b]: e.target.value } } })}>
            <option value="">Chọn từ</option>
            {words.map((w) => (
              <option key={w} value={w}>
                {w}
              </option>
            ))}
          </select>
        </Labeled>
      ))}
    </div>
  )
}
