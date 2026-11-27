import { Labeled, LinesField, smInput } from '../fields'
import { layoutSlots } from '../layout/layoutModel'
import type { Obj, QuestionType } from '../types'
import type { EditorProps } from './props'

// [khóa payload, nhãn, tùy chọn?]
type Field = readonly [string, string, boolean?]
interface Cfg {
  slot?: boolean
  fields?: Field[]
  hint?: string
}

// Các dạng gõ chữ: answerKey {accepted}, payload gồm slot (nếu có), chữ riêng của dạng và maxWords tùy chọn.
const CFG: Partial<Record<QuestionType, Cfg>> = {
  DICTATION: { hint: 'Audio gắn ở nhóm câu (bắt buộc có audio). Khi so, dấu câu cuối và dấu câu thường bị bỏ qua.' },
  LISTEN_FORM: { slot: true, hint: 'Nhóm câu cần có audio và bố cục Phiếu thông tin.' },
  IELTS_SENTENCE: { fields: [['before', 'Phần trước chỗ trống', true], ['after', 'Phần sau chỗ trống', true]], hint: 'Cần ít nhất một trong hai phần.' },
  IELTS_SUMMARY: { slot: true },
  IELTS_NOTE: { slot: true },
  IELTS_TABLE: { slot: true },
  IELTS_FLOWCHART: { slot: true },
  IELTS_FORM: { slot: true },
  IELTS_DIAGRAM: { slot: true, fields: [['description', 'Mô tả vị trí nhãn']] },
  IELTS_SHORT: { fields: [['question', 'Câu hỏi']] },
}

export function TypedTextEditor({ draft, patch, layout }: EditorProps) {
  const cfg = CFG[draft.type] ?? {}
  const p: Obj = draft.payload
  const set = (key: string, value: string | number | undefined) => {
    const next: Obj = { ...p, [key]: value }
    if (value === undefined || value === '') delete next[key]
    patch({ payload: next })
  }
  const slots = layoutSlots(layout)
  return (
    <div className="flex flex-col gap-3">
      {cfg.hint && <p className="text-sm font-semibold text-ink-secondary">{cfg.hint}</p>}
      {cfg.slot && (
        <Labeled label="Chỗ trống của câu" htmlFor="q-slot" hint={slots.length ? undefined : 'Chọn nhóm câu có bố cục chứa chỗ trống trước.'}>
          <select id="q-slot" className={smInput} value={p.slot ?? ''} onChange={(e) => set('slot', e.target.value)}>
            <option value="">Chọn chỗ trống</option>
            {slots.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </Labeled>
      )}
      {cfg.fields?.map(([key, label, optional]) => (
        <Labeled key={key} label={label + (optional ? ' (không bắt buộc)' : '')} htmlFor={`q-${key}`}>
          <input id={`q-${key}`} className={smInput} value={p[key] ?? ''} onChange={(e) => set(key, e.target.value)} />
        </Labeled>
      ))}
      <LinesField id="accepted" label="Đáp án chấp nhận" hint="Mỗi dòng một đáp án; không phân biệt hoa thường." value={draft.answerKey.accepted ?? []} onChange={(accepted) => patch({ answerKey: { accepted } })} />
      <Labeled label="Số từ tối đa (không bắt buộc)" htmlFor="q-maxWords">
        <input id="q-maxWords" type="number" min={1} className={`${smInput} max-w-40`} value={p.maxWords ?? ''} onChange={(e) => set('maxWords', e.target.value === '' ? undefined : Number(e.target.value))} />
      </Labeled>
    </div>
  )
}
