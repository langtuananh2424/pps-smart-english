import { Labeled, smInput } from '../fields'
import type { EditorProps } from './props'

// D-154 mục 4: đề một câu + thời lượng tối đa của bản ghi. Audio mẫu là audio của nhóm câu.
export function ListenRecordEditor({ draft, patch }: EditorProps) {
  const p = draft.payload
  const set = (o: Record<string, unknown>) => {
    const next = { ...p, ...o }
    if (next.maxSeconds === '' || next.maxSeconds == null) delete next.maxSeconds
    patch({ payload: next })
  }
  return (
    <div className="flex flex-col gap-3">
      <Labeled label="Đề" htmlFor="lr-prompt">
        <textarea id="lr-prompt" rows={3} value={p.prompt ?? ''} onChange={(e) => set({ prompt: e.target.value })} className={`${smInput} py-2`} />
      </Labeled>
      <Labeled label="Thời gian tối đa (giây, 10–180)" htmlFor="lr-seconds">
        <input id="lr-seconds" type="number" value={p.maxSeconds ?? ''} onChange={(e) => set({ maxSeconds: e.target.value === '' ? '' : Number(e.target.value) })} className={smInput} />
      </Labeled>
      <p className="text-sm font-semibold text-ink-secondary">Nhóm câu phải có audio. Học viên nghe audio rồi tải tệp bản ghi lên, không ghi trực tiếp.</p>
    </div>
  )
}
