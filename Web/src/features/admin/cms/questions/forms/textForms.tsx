import { Labeled, LinesField, smInput } from '../fields'
import type { Obj } from '../types'
import type { EditorProps } from './props'

const Text = ({ id, label, value, onChange, type = 'text' }: { id: string; label: string; value: string | number; onChange: (v: string) => void; type?: string }) => (
  <Labeled label={label} htmlFor={id}>
    <input id={id} type={type} value={value} onChange={(e) => onChange(e.target.value)} className={smInput} />
  </Labeled>
)

// Hai dạng viết lại: khác nhau ở trường điều kiện (từ nối hoặc yếu tố ngữ pháp).
export function RewriteEditor({ draft, patch, elements }: EditorProps) {
  const grammar = draft.type === 'GRAMMAR_REWRITE'
  const set = (o: Obj) => patch({ payload: { ...draft.payload, ...o } })
  const accepted: string[] = draft.answerKey.acceptedAnswers ?? []
  return (
    <div className="flex flex-col gap-3">
      <Text id="sentence" label="Câu gốc" value={draft.payload.sentence ?? ''} onChange={(sentence) => set({ sentence })} />
      {grammar ? (
        <Labeled label="Yếu tố ngữ pháp bắt buộc" htmlFor="required-element">
          <select id="required-element" className={smInput} value={draft.payload.requiredElement ?? ''} onChange={(e) => set({ requiredElement: e.target.value })}>
            <option value="">Chọn yếu tố</option>
            {elements.map((e) => (
              <option key={e.code} value={e.code}>
                {e.name}
              </option>
            ))}
          </select>
        </Labeled>
      ) : (
        <Text id="required-word" label="Từ nối bắt buộc" value={draft.payload.requiredWord ?? ''} onChange={(requiredWord) => set({ requiredWord })} />
      )}
      <LinesField id="sample" label="Đáp án mẫu (không bắt buộc)" value={accepted} onChange={(v) => patch({ answerKey: v.length ? { acceptedAnswers: v } : {} })} />
      {!accepted.length && <p className="text-sm font-semibold text-ink-secondary">Không có đáp án mẫu: câu trả lời không khớp luật sẽ do AI chấm và trừ lượt.</p>}
    </div>
  )
}

export function WritingEditor({ draft, patch }: EditorProps) {
  const p = draft.payload
  const set = (o: Obj) => patch({ payload: { ...p, ...o } })
  return (
    <div className="flex flex-col gap-3">
      <Labeled label="Đề bài viết" htmlFor="task">
        <textarea id="task" rows={3} value={p.task ?? ''} onChange={(e) => set({ task: e.target.value })} className={`${smInput} py-2`} />
      </Labeled>
      <div className="grid gap-3 sm:grid-cols-2">
        <Text id="required-words" type="number" label="Số từ yêu cầu" value={p.requiredWords ?? ''} onChange={(v) => set({ requiredWords: v === '' ? '' : Number(v) })} />
        <Labeled label="IELTS" htmlFor="ielts">
          <select
            id="ielts"
            className={smInput}
            value={p.ieltsTask ?? ''}
            onChange={(e) => {
              const next = { ...p }
              if (e.target.value) next.ieltsTask = e.target.value
              else delete next.ieltsTask
              patch({ payload: next })
            }}
          >
            <option value="">Không</option>
            <option value="TASK1">Task 1 (trừ 2 lượt)</option>
            <option value="TASK2">Task 2 (trừ 3 lượt)</option>
          </select>
        </Labeled>
      </div>
      <p className="text-sm font-semibold text-ink-secondary">Chấm bằng AI, trừ lượt của học viên.</p>
    </div>
  )
}

export function SpeakingEditor({ draft, patch }: EditorProps) {
  const p = draft.payload
  const set = (o: Obj) => patch({ payload: { ...p, ...o } })
  const drop = (k: string) => {
    const next = { ...p }
    delete next[k]
    patch({ payload: next })
  }
  return (
    <div className="flex flex-col gap-3">
      <Labeled label="Part" htmlFor="part">
        <select id="part" className={smInput} value={p.part ?? 1} onChange={(e) => (Number(e.target.value) === 2 ? set({ part: 2 }) : patch({ payload: { ...withoutKey(p, 'cueCard'), part: Number(e.target.value) } }))}>
          <option value={1}>Part 1</option>
          <option value={2}>Part 2</option>
          <option value={3}>Part 3</option>
        </select>
      </Labeled>
      <Labeled label="Đề nói" htmlFor="speak-prompt">
        <textarea id="speak-prompt" rows={3} maxLength={2000} value={p.prompt ?? ''} onChange={(e) => set({ prompt: e.target.value })} className={`${smInput} py-2`} />
      </Labeled>
      {p.part === 2 && <LinesField id="cue" label="Cue card" hint="Mỗi dòng một gạch đầu dòng." value={p.cueCard ?? []} onChange={(v) => (v.length ? set({ cueCard: v }) : drop('cueCard'))} />}
      <Text id="max-seconds" type="number" label="Thời gian nói tối đa (10 đến 180 giây, để trống = mặc định theo Part)" value={p.maxSeconds ?? ''} onChange={(v) => (v === '' ? drop('maxSeconds') : set({ maxSeconds: Number(v) }))} />
    </div>
  )
}

const withoutKey = (o: Obj, k: string) => Object.fromEntries(Object.entries(o).filter(([key]) => key !== k))
