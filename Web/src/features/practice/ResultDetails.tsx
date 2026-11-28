import { useGrammarElements } from './elements'
import { panel } from './questions/ui'
import { spansOf, spanText, SpanTokens, type Span } from './questions/Identify'
import type { Answer, Id, Question } from './types'

const key = (s: Span) => `${s.start}:${s.end}:${s.element}`
const same = (a: Id, b: Id) => String(a) === String(b)
const h3 = 'text-[19px] font-black text-ink'
const tag = 'text-[13px] font-bold text-danger-fg'

// Kết quả nhận diện cấu trúc (Figma 415:7861): câu có gạch chân đáp án, bài làm chia Đúng/Sai nhãn/Thừa, cụm đúng kèm Bị bỏ sót.
export function IdentifyDetail({ question, mine, correct, mineLabel = 'Bài làm của bạn' }: { question: Question; mine?: Answer; correct: Answer; mineLabel?: string }) {
  const { name: elementName, color: elementColor } = useGrammarElements()
  const tokens = question.payload.tokens as string[]
  const right = spansOf(correct)
  const given = mine ? spansOf(mine) : null
  const rightKeys = new Set(right.map(key))
  const givenKeys = new Set((given ?? []).map(key))
  const verdict = (s: Span) => (rightKeys.has(key(s)) ? 'Đúng' : right.some((r) => r.start === s.start && r.end === s.end) ? 'Sai nhãn' : 'Thừa')
  const line = (s: Span) => `${spanText(tokens, s)} → ${elementName(s.element)}`
  return (
    <div className="flex flex-col gap-4">
      <section aria-label="Đáp án" className={panel}>
        <h3 className="text-lg font-black text-ink">Đáp án</h3>
        <SpanTokens tokens={tokens} spans={right} size="h-[39px]" />
      </section>
      <div className="grid gap-4 md:grid-cols-2">
        {given && (
          <section aria-label={mineLabel} className={panel}>
            <h3 className={h3}>{mineLabel}</h3>
            {given.length === 0 && <p className="text-[15px] text-ink">Chưa chọn cụm nào.</p>}
            {given.map((s, i) => {
              const v = verdict(s)
              return (
                <div key={i} className="flex flex-col gap-1">
                  <p className={v === 'Đúng' ? 'text-[13px] font-bold text-[#166534]' : tag}>{v}</p>
                  <p className="text-[15px] text-ink">{line(s)}</p>
                </div>
              )
            })}
          </section>
        )}
        <section aria-label="Các cụm đúng" className={panel}>
          <h3 className={h3}>Các cụm đúng</h3>
          {right.map((s, i) => (
            <div key={i} className="flex flex-col gap-1">
              {given && !givenKeys.has(key(s)) && <p className={tag}>Bị bỏ sót</p>}
              <p className="text-[15px] font-bold" style={{ color: elementColor(s.element) }}>{line(s)}</p>
            </div>
          ))}
        </section>
      </div>
    </div>
  )
}

// Kết quả xếp khối (Figma 415:8122): khối của bạn tô xanh/đỏ theo từng vị trí, rồi câu đúng.
export function BuildDetail({ question, mine, correct, mineLabel = 'Bài làm của bạn' }: { question: Question; mine?: Answer; correct: Answer; mineLabel?: string }) {
  const blocks = question.payload.blocks as { id: Id; text: string }[]
  const text = (id: Id) => blocks.find((b) => same(b.id, id))?.text ?? String(id)
  const right = (correct.order ?? []) as Id[]
  const given = mine ? ((mine.order ?? []) as Id[]) : null
  const wrong = given ? right.map((id, i) => i).filter((i) => given[i] == null || !same(given[i], right[i])).map((i) => i + 1) : []
  return (
    <div className="flex flex-col gap-4">
      {given && (
        <section aria-label={mineLabel} className={panel}>
          <h3 className={h3}>{mineLabel}</h3>
          <ol className="flex flex-col gap-3">
            {right.map((_, i) => (
              <li key={i} className={`rounded-[12px] p-3 text-[15px] font-bold text-ink md:max-w-[510px] ${given[i] != null && same(given[i], right[i]) ? 'bg-success-bg' : 'bg-danger-bg'}`}>
                {i + 1}. {given[i] != null ? text(given[i]) : 'Ô trống'}
              </li>
            ))}
          </ol>
        </section>
      )}
      <section aria-label="Câu đúng" className={panel}>
        <h3 className={h3}>Câu đúng</h3>
        <p className="text-[17px] font-bold text-ink">{right.map(text).join(' ')}</p>
        {wrong.length > 0 && <p className="text-sm font-bold text-danger-fg">{wrong.length === 1 ? `Khối ${wrong[0]} đang sai vị trí.` : `Khối ${wrong.slice(0, -1).join(', ')} và ${wrong[wrong.length - 1]} đang sai vị trí.`}</p>}
      </section>
    </div>
  )
}
