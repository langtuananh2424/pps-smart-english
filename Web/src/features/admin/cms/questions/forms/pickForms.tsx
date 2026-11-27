import { Labeled, smInput } from '../fields'
import { layoutChoices, layoutSlots } from '../layout/layoutModel'
import type { QuestionType } from '../types'
import type { EditorProps } from './props'

interface Cfg {
  // Trường chữ của payload (không có thì dạng dùng chỗ trống {slot}).
  field?: readonly [string, string]
  key: 'value' | 'option' | 'word'
  // Giá trị cố định (TFNG, YNNG) hoặc id lấy từ layout của nhóm.
  values?: readonly string[]
  from?: 'options' | 'words'
}

const TFNG = ['TRUE', 'FALSE', 'NOT_GIVEN']
const YNNG = ['YES', 'NO', 'NOT_GIVEN']
const STATEMENT = ['statement', 'Phát biểu'] as const

// Các dạng chọn một giá trị: answerKey {value} hoặc {option} hoặc {word}.
const CFG: Partial<Record<QuestionType, Cfg>> = {
  IELTS_TFNG: { field: STATEMENT, key: 'value', values: TFNG },
  IELTS_YNNG: { field: STATEMENT, key: 'value', values: YNNG },
  IELTS_MATCH_INFO: { field: STATEMENT, key: 'option', from: 'options' },
  IELTS_MATCH_HEADINGS: { field: ['paragraph', 'Đoạn (ví dụ Paragraph B)'], key: 'option', from: 'options' },
  IELTS_MATCH_FEATURES: { field: STATEMENT, key: 'option', from: 'options' },
  IELTS_MATCH_ENDINGS: { field: ['stem', 'Nửa đầu câu'], key: 'option', from: 'options' },
  IELTS_MATCHING: { field: STATEMENT, key: 'option', from: 'options' },
  IELTS_SUMMARY_BANK: { key: 'word', from: 'words' },
  IELTS_MAP: { field: ['label', 'Tên địa điểm (ví dụ Café)'], key: 'option', from: 'options' },
}

export function PickEditor({ draft, patch, layout }: EditorProps) {
  const cfg = CFG[draft.type]
  if (!cfg) return null
  const setPayload = (k: string, v: string) => patch({ payload: { ...draft.payload, [k]: v } })
  const chosen: string = draft.answerKey[cfg.key] ?? ''
  const slots = layoutSlots(layout)
  const choices = cfg.from ? layoutChoices(layout, cfg.from) : []
  return (
    <div className="flex flex-col gap-3">
      {cfg.field ? (
        <Labeled label={cfg.field[1]} htmlFor="q-field">
          <input id="q-field" className={smInput} value={draft.payload[cfg.field[0]] ?? ''} onChange={(e) => setPayload(cfg.field![0], e.target.value)} />
        </Labeled>
      ) : (
        <Labeled label="Chỗ trống của câu" htmlFor="q-slot" hint={slots.length ? undefined : 'Chọn nhóm câu có bố cục chứa chỗ trống trước.'}>
          <select id="q-slot" className={smInput} value={draft.payload.slot ?? ''} onChange={(e) => setPayload('slot', e.target.value)}>
            <option value="">Chọn chỗ trống</option>
            {slots.map((s) => (
              <option key={s}>{s}</option>
            ))}
          </select>
        </Labeled>
      )}
      {cfg.values ? (
        <fieldset className="flex flex-wrap gap-3">
          <legend className="text-sm font-extrabold text-ink">Đáp án đúng</legend>
          {cfg.values.map((v) => (
            <label key={v} className="flex min-h-9 items-center gap-1.5 font-bold text-ink">
              <input type="radio" name="pick" checked={chosen === v} onChange={() => patch({ answerKey: { [cfg.key]: v } })} />
              {v}
            </label>
          ))}
        </fieldset>
      ) : (
        <Labeled label="Đáp án đúng" htmlFor="q-pick" hint={choices.length ? undefined : 'Chọn nhóm câu có bố cục lựa chọn trước.'}>
          <select id="q-pick" className={smInput} value={chosen} onChange={(e) => patch({ answerKey: { [cfg.key]: e.target.value } })}>
            <option value="">Chọn đáp án</option>
            {choices.map((c) => (
              <option key={c.id} value={c.id}>
                {c.id}
                {c.text ? `. ${c.text}` : ''}
              </option>
            ))}
          </select>
        </Labeled>
      )}
    </div>
  )
}
