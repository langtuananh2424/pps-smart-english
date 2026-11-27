import { useState } from 'react'
import { Labeled, smInput } from '../fields'
import type { EditorProps } from './props'

// "1:30" hoặc "90" → giây; sai dạng thì null.
export function parseClock(s: string): number | null {
  const m = /^(\d+)(?::([0-5]?\d))?$/.exec(s.trim())
  if (!m) return null
  return m[2] === undefined ? Number(m[1]) : Number(m[1]) * 60 + Number(m[2])
}
export const fmtClock = (sec: number) => `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}`

const EXTRA: Record<string, { key: 'cue' | 'instruction'; label: string }> = {
  VIDEO_CUE: { key: 'cue', label: 'Cue card (gợi ý nói)' },
  VIDEO_DISCUSS: { key: 'instruction', label: 'Hướng dẫn thảo luận' },
}

// D-154 mục 5, D-158: một câu là một mốc trong video của nhóm. Mốc nhập mm:ss, lưu bằng giây. Ảnh của Tả tranh chèn vào phần đề (media của câu).
export function VideoReflexEditor({ draft, patch }: EditorProps) {
  const p = draft.payload
  const extra = EXTRA[draft.type]
  const [clock, setClock] = useState(typeof p.at === 'number' ? fmtClock(p.at) : '')
  const set = (o: Record<string, unknown>) => {
    const next = { ...p, ...o }
    if (next.maxSeconds === '' || next.maxSeconds == null) delete next.maxSeconds
    patch({ payload: next })
  }
  const bad = clock !== '' && parseClock(clock) === null
  return (
    <div className="flex flex-col gap-3">
      <Labeled label="Mốc dừng (mm:ss)" htmlFor="vr-at" hint={bad ? 'Nhập dạng 1:30 hoặc số giây' : undefined}>
        <input
          id="vr-at" value={clock} inputMode="numeric" placeholder="0:45" className={smInput}
          onChange={(e) => {
            setClock(e.target.value)
            const s = parseClock(e.target.value)
            const { at: _drop, ...rest } = p
            patch({ payload: s === null ? rest : { ...p, at: s } })
          }}
        />
      </Labeled>
      <Labeled label="Câu hỏi" htmlFor="vr-prompt">
        <textarea id="vr-prompt" rows={2} value={p.prompt ?? ''} onChange={(e) => set({ prompt: e.target.value })} className={`${smInput} py-2`} />
      </Labeled>
      {extra && (
        <Labeled label={extra.label} htmlFor="vr-extra">
          <textarea id="vr-extra" rows={3} value={p[extra.key] ?? ''} onChange={(e) => set({ [extra.key]: e.target.value })} className={`${smInput} py-2`} />
        </Labeled>
      )}
      <Labeled label="Thời gian ghi tối đa (giây, 10–180, để trống = mặc định)" htmlFor="vr-seconds">
        <input id="vr-seconds" type="number" value={p.maxSeconds ?? ''} onChange={(e) => set({ maxSeconds: e.target.value === '' ? '' : Number(e.target.value) })} className={smInput} />
      </Labeled>
      <p className="text-sm font-semibold text-ink-secondary">
        Nhóm câu phải có video; mốc tăng dần theo thứ tự câu và nhỏ hơn thời lượng video.
        {draft.type === 'VIDEO_PICTURE' && ' Chèn ảnh cần tả vào phần đề của câu.'}
        {draft.type === 'VIDEO_SHORT' && ' Học viên viết câu trả lời trước rồi mới nói.'}
      </p>
    </div>
  )
}
