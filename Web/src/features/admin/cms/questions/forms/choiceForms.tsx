import { labelCls } from '../../../ui'
import { Err, LinesField, Labeled, smBtn, smInput } from '../fields'
import { RichText } from '../RichText'
import type { ChoiceDraft, Obj } from '../types'
import { uid } from '../util'
import type { EditorProps } from './props'

export const newChoice = (i: number): ChoiceDraft => ({ uid: uid(), label: String.fromCharCode(65 + i), contentHtml: '', isCorrect: false, explanation: '' })

export const ScoringSelect = ({ id, value, onChange }: { id: string; value?: string; onChange: (v: string) => void }) => (
  <Labeled label="Cách chấm" htmlFor={id}>
    <select id={id} className={smInput} value={value ?? 'PARTIAL'} onChange={(e) => onChange(e.target.value)}>
      <option value="PARTIAL">Chấm từng phần</option>
      <option value="ALL">Chấm cả câu (đúng hết mới có điểm)</option>
    </select>
  </Labeled>
)

// Trắc nghiệm một đáp án (radio) hoặc nhiều đáp án (checkbox); đáp án nằm ở choices.
export function ChoiceEditor({ draft, patch, errors }: EditorProps) {
  const multi = draft.type === 'CHOICE_MULTI'
  const rows = draft.choices
  const set = (i: number, p: Partial<ChoiceDraft>) =>
    patch({ choices: rows.map((c, j) => (j === i ? { ...c, ...p } : !multi && p.isCorrect ? { ...c, isCorrect: false } : c)) })
  return (
    <div className="flex flex-col gap-3">
      {multi && <ScoringSelect id="scoring" value={draft.payload.scoring} onChange={(scoring) => patch({ payload: { ...draft.payload, scoring } })} />}
      <span className={labelCls}>Lựa chọn · tích vào đáp án đúng</span>
      {rows.map((c, i) => (
        <div key={c.uid} className="flex flex-col gap-2 rounded-sm border border-line-subtle p-3">
          <div className="flex items-center gap-2">
            <input aria-label={`Nhãn ${i + 1}`} value={c.label} maxLength={10} onChange={(e) => set(i, { label: e.target.value })} className={`${smInput} max-w-24`} />
            <label className="flex min-h-10 items-center gap-2 font-extrabold text-ink">
              <input type={multi ? 'checkbox' : 'radio'} name="correct" checked={c.isCorrect} onChange={(e) => set(i, { isCorrect: multi ? e.target.checked : true })} />
              Đúng
            </label>
            <button type="button" className={`${smBtn} ml-auto`} aria-label={`Xóa lựa chọn ${i + 1}`} disabled={rows.length <= 2} onClick={() => patch({ choices: rows.filter((_, j) => j !== i) })}>
              Xóa
            </button>
          </div>
          <RichText compact label={`Nội dung lựa chọn ${i + 1}`} value={c.contentHtml} onChange={(contentHtml) => set(i, { contentHtml })} />
          <input aria-label={`Giải thích lựa chọn ${i + 1}`} placeholder="Giải thích riêng (không bắt buộc)" value={c.explanation} onChange={(e) => set(i, { explanation: e.target.value })} className={smInput} />
          <Err list={Object.entries(errors).filter(([k]) => k.startsWith(`choices[${i}]`)).map(([, v]) => v)} />
        </div>
      ))}
      <button type="button" className={`${smBtn} self-start`} disabled={rows.length >= 10} onClick={() => patch({ choices: [...rows, newChoice(rows.length)] })}>
        Thêm lựa chọn
      </button>
      <Err list={errors.choices ? [errors.choices] : []} />
    </div>
  )
}

const TF_SETS: Record<string, string[]> = {
  TF: ['TRUE', 'FALSE'],
  TFNG: ['TRUE', 'FALSE', 'NOT_GIVEN'],
  YNNG: ['YES', 'NO', 'NOT_GIVEN'],
}
const TF_LABEL: Record<string, string> = { TRUE: 'Đúng', FALSE: 'Sai', YES: 'Có', NO: 'Không', NOT_GIVEN: 'Không có thông tin' }

export function TrueFalseEditor({ draft, patch }: EditorProps) {
  const options: string[] = draft.payload.options ?? TF_SETS.TF
  const setKey = Object.keys(TF_SETS).find((k) => TF_SETS[k].join() === options.join()) ?? 'TF'
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <Labeled label="Bộ lựa chọn" htmlFor="tf-set">
        <select id="tf-set" className={smInput} value={setKey} onChange={(e) => patch({ payload: { options: TF_SETS[e.target.value] }, answerKey: { answer: TF_SETS[e.target.value][0] } })}>
          <option value="TF">Đúng / Sai</option>
          <option value="TFNG">Đúng / Sai / Không có thông tin</option>
          <option value="YNNG">Có / Không / Không có thông tin</option>
        </select>
      </Labeled>
      <Labeled label="Đáp án đúng" htmlFor="tf-answer">
        <select id="tf-answer" className={smInput} value={draft.answerKey.answer ?? ''} onChange={(e) => patch({ answerKey: { answer: e.target.value } })}>
          {options.map((o) => (
            <option key={o} value={o}>
              {TF_LABEL[o]}
            </option>
          ))}
        </select>
      </Labeled>
    </div>
  )
}

export function FillTextEditor({ draft, patch }: EditorProps) {
  const maxWords = draft.payload.maxWords as number | undefined
  const setMax = (v: string) => {
    const payload: Obj = { ...draft.payload }
    if (v === '') delete payload.maxWords
    else payload.maxWords = Number(v)
    patch({ payload })
  }
  return (
    <div className="flex flex-col gap-3">
      <LinesField id="accepted" label="Đáp án chấp nhận" hint="Mỗi dòng một đáp án; không phân biệt hoa thường." value={draft.answerKey.accepted ?? []} onChange={(accepted) => patch({ answerKey: { accepted } })} />
      <Labeled label="Số từ tối đa (không bắt buộc)" htmlFor="maxWords">
        <input id="maxWords" type="number" min={1} className={`${smInput} max-w-40`} value={maxWords ?? ''} onChange={(e) => setMax(e.target.value)} />
      </Labeled>
    </div>
  )
}
