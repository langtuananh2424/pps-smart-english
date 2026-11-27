import { Labeled, smBtn, smInput } from '../fields'
import type { Obj } from '../types'
import type { EditorProps } from './props'

interface Letter {
  id: string
  ch: string
}
const MAX = 30

// Xáo tới khi khác thứ tự gốc (nếu có thể) để câu không bị giải sẵn.
export function shuffle(xs: Letter[]): Letter[] {
  const out = [...xs]
  for (let t = 0; t < 8; t++) {
    for (let i = out.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1))
      ;[out[i], out[j]] = [out[j], out[i]]
    }
    if (out.some((l, i) => l.ch !== xs[i].ch)) break
  }
  return out
}

// Id ngẫu nhiên, không mang thứ tự; server cũng sinh lại khi lưu.
const randomId = () => Math.random().toString(36).slice(2, 8).padEnd(6, '0')

const lettersOf = (word: string): Letter[] => [...word.replace(/\s+/g, '')].slice(0, MAX).map((ch) => ({ id: randomId(), ch }))

// Nhập từ -> khối chữ đã xáo; người soạn xáo lại hoặc đổi thứ tự hiển thị. answerKey {word} luôn là từ đúng.
export function LetterOrderEditor({ draft, patch }: EditorProps) {
  const word: string = draft.answerKey.word ?? ''
  const letters: Letter[] = draft.payload.letters ?? []
  const apply = (l: Letter[], payload: Obj = draft.payload) => patch({ payload: { ...payload, letters: l } })
  const move = (i: number, d: number) => {
    const next = [...letters]
    ;[next[i], next[i + d]] = [next[i + d], next[i]]
    apply(next)
  }
  return (
    <div className="flex flex-col gap-3">
      <Labeled label="Từ đúng" htmlFor="lo-word" hint="Các khối chữ được sinh từ từ này (bỏ khoảng trắng, tối đa 30 chữ).">
        <input
          id="lo-word"
          className={smInput}
          value={word}
          maxLength={MAX}
          onChange={(e) => {
            const w = e.target.value.replace(/\s+/g, '')
            patch({ answerKey: { word: w }, payload: { letters: shuffle(lettersOf(w)) } })
          }}
        />
      </Labeled>
      <div className="flex flex-col gap-2">
        <span className="text-sm font-extrabold text-ink">Thứ tự khối chữ học viên thấy</span>
        <div className="flex flex-wrap gap-2" aria-label="Khối chữ">
          {letters.map((l, i) => (
            <div key={l.id} className="flex items-center gap-1 rounded-sm border border-line px-2 py-1 font-black text-ink">
              <button type="button" className={smBtn} aria-label={`Đưa khối ${i + 1} sang trái`} disabled={i === 0} onClick={() => move(i, -1)}>
                ←
              </button>
              <span aria-label={`Khối ${i + 1}`}>{l.ch}</span>
              <button type="button" className={smBtn} aria-label={`Đưa khối ${i + 1} sang phải`} disabled={i === letters.length - 1} onClick={() => move(i, 1)}>
                →
              </button>
            </div>
          ))}
        </div>
        <button type="button" className={`${smBtn} self-start`} disabled={letters.length < 2} onClick={() => apply(shuffle(letters))}>
          Xáo
        </button>
      </div>
    </div>
  )
}
