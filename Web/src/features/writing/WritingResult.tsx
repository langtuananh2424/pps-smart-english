import { useNavigate } from 'react-router'
import cam from '../../assets/exercises/cam-kinh-lup.webp'
import { useGrammarElements } from '../practice/elements'
import { pillNeutral, pillPrimary } from '../practice/questions/ui'
import { card, Frame, PromptCard } from './Frame'
import { splitMarked } from './marked'
import type { ElementStatus, SessionView, WritingGrade } from './types'

const CRITERIA: [keyof WritingGrade, string][] = [
  ['taskResponse', 'Đáp ứng đề'],
  ['coherence', 'Mạch lạc'],
  ['lexical', 'Từ vựng'],
  ['grammar', 'Ngữ pháp'],
]
const STATUS: Record<ElementStatus, [string, string]> = {
  USED_WELL: ['Dùng tốt', 'bg-success-bg text-success-fg'],
  USED_WRONG: ['Dùng chưa đúng', 'bg-danger-bg text-danger-fg'],
  SUGGESTED: ['Nên thử', 'bg-ai-bg text-ai'],
}

// Kết quả Viết luyện tập (D-81): điểm % theo backend, không quy đổi band. Bố cục đối chiếu theo Figma 294:15245.
export function WritingResult({ s, onRewrite }: { s: SessionView; onRewrite: () => void }) {
  const g = s.grade as WritingGrade
  const navigate = useNavigate()
  const actions = (
    <>
      <button type="button" onClick={onRewrite} className={pillNeutral}>Viết lại</button>
      <button type="button" onClick={() => navigate(-1)} className={`${pillPrimary} w-[220px]`}>Hoàn thành →</button>
    </>
  )
  return (
    <Frame left={<PromptCard question={s.question} />} actions={actions}>
      <section aria-label="Điểm" className={`${card} items-center text-center`}>
        <img src={cam} alt="" width={96} height={96} />
        <p className="text-5xl font-black text-brand-strong">{Math.round(g.scorePercent)}<span className="text-2xl text-ink-muted">%</span></p>
        <ul className="flex w-full flex-col gap-3 text-left">
          {CRITERIA.map(([key, label]) => <Bar key={key} label={label} value={Number(g[key])} />)}
        </ul>
      </section>

      <section aria-label="Đối chiếu" className={card}>
        <div className="grid gap-3 md:grid-cols-2">
          <article className="flex flex-col gap-3 rounded-lg border-[1.5px] border-line bg-subtle p-4">
            <h2 className="text-[13px] font-extrabold leading-[18px] text-brand-strong">Bản gốc</h2>
            <p className="whitespace-pre-wrap text-base font-semibold text-ink">{s.finalText}</p>
          </article>
          <article className="flex flex-col gap-3 rounded-lg border-[1.5px] border-line bg-ai-bg p-4">
            <h2 className="text-[13px] font-extrabold leading-[18px] text-ai">Bản sửa · Gợi ý diễn đạt</h2>
            <p className="whitespace-pre-wrap text-base font-semibold text-ink">{g.correctedAnswer}</p>
          </article>
        </div>
        {g.markedAnswer && <Marked src={g.markedAnswer} />}
      </section>

      {g.connectives && (
        <section aria-label="Từ nối" className={card}>
          <h2 className="text-lg font-black text-ink">Từ nối</h2>
          <p className="text-sm font-extrabold text-ink-secondary">
            Tổng {g.connectives.total} · Đúng {g.connectives.correct} · {g.connectives.types} loại
          </p>
          {g.connectives.misused.length > 0 && <p className="text-sm font-bold text-danger-fg">Dùng sai: {g.connectives.misused.join(', ')}</p>}
          {g.connectives.note && <p className="text-base font-semibold text-ink">{g.connectives.note}</p>}
        </section>
      )}

      {g.grammarElements && g.grammarElements.length > 0 && <Grammar items={g.grammarElements} />}

      {g.gateNote && <p role="note" className="rounded-md bg-reward-bg p-4 text-sm font-bold text-reward-fg">{g.gateNote}</p>}
      {g.feedback && (
        <section aria-label="Nhận xét" className={card}>
          <h2 className="text-lg font-black text-ink">Nhận xét</h2>
          <p className="whitespace-pre-wrap text-base font-semibold text-ink">{g.feedback}</p>
        </section>
      )}
    </Frame>
  )
}

function Bar({ label, value }: { label: string; value: number }) {
  const pct = Math.max(0, Math.min(100, Math.round(value)))
  return (
    <li>
      <div className="flex justify-between text-sm font-extrabold text-ink"><span>{label}</span><span>{pct}%</span></div>
      <div role="progressbar" aria-label={label} aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} className="mt-1 h-2.5 overflow-hidden rounded-full bg-muted">
        <div className="h-full rounded-full bg-brand" style={{ width: `${pct}%` }} />
      </div>
    </li>
  )
}

// Tô đoạn {{code|text}} bằng màu nhãn ngữ pháp (D-69).
function Marked({ src }: { src: string }) {
  const { color, name } = useGrammarElements()
  return (
    <p aria-label="Bài đã đánh dấu" className="whitespace-pre-wrap text-base font-semibold leading-7 text-ink">
      {splitMarked(src).map((p, i) => p.code ? (
        <mark key={i} title={name(p.code)} className="rounded-sm px-1 text-ink" style={{ backgroundColor: `${color(p.code)}33`, borderBottom: `2px solid ${color(p.code)}` }}>
          {p.text}<sup className="ml-0.5 text-[10px] font-extrabold" style={{ color: color(p.code) }}>{name(p.code)}</sup>
        </mark>
      ) : <span key={i}>{p.text}</span>)}
    </p>
  )
}

function Grammar({ items }: { items: NonNullable<WritingGrade['grammarElements']> }) {
  const { name, color } = useGrammarElements()
  return (
    <section aria-label="Cấu trúc ngữ pháp" className={card}>
      <h2 className="text-lg font-black text-ink">Cấu trúc ngữ pháp</h2>
      <ul className="flex flex-col gap-3">
        {items.map((it, i) => (
          <li key={i} className="flex flex-col gap-1 rounded-md border border-line p-3">
            <div className="flex items-center justify-between gap-2">
              <span className="text-sm font-black" style={{ color: color(it.code) }}>{name(it.code)}</span>
              <span className={`rounded-full px-2.5 py-1 text-xs font-extrabold ${STATUS[it.status][1]}`}>{STATUS[it.status][0]}</span>
            </div>
            {it.quote && <p className="text-sm font-semibold text-ink-secondary">“{it.quote}”</p>}
            {it.upgrade && <p className="text-sm font-semibold text-ink">Gợi ý: {it.upgrade}</p>}
          </li>
        ))}
      </ul>
    </section>
  )
}
