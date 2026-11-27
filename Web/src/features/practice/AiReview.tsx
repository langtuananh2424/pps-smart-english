import { isReflexType, labelOf } from '../exercises/format'
import logo from '../../assets/logo-pps.png'
import { QuestionStem } from './QuestionStem'
import { pillPrimary } from './questions/ui'
import { hasReview, type AiOutcome } from './useAiOutcomes'
import type { Bank } from './types'

const card = 'flex flex-col gap-3 rounded-[20px] border border-line bg-surface p-5 md:p-6'
const chipCls = 'rounded-full bg-subtle px-3 py-1 text-sm font-extrabold text-brand-strong'

interface Props {
  bank: Bank
  questionId: number
  outcomes: Map<number, AiOutcome>
  onGo: (questionId: number) => void
  onBack: () => void
}

// Xem nhận xét sau chấm (Figma 608-18065, 608-18183, 608-18343): đề bên trái, nhận xét bên phải, thanh dưới mốc câu.
export function AiReview({ bank, questionId, outcomes, onGo, onBack }: Props) {
  const index = bank.questions.findIndex((q) => q.id === questionId)
  const question = bank.questions[index]
  const o = outcomes.get(questionId)
  const writing = question.type === 'WRITING'
  return (
    <div className="min-h-dvh bg-page pb-[88px]">
      <header className="sticky top-0 z-20 flex h-[68px] items-center gap-3 border-b border-line bg-surface px-4 md:h-[72px] md:px-7">
        <button type="button" onClick={onBack} aria-label="Quay lại" className="grid size-11 shrink-0 place-items-center rounded-[10px] border border-line bg-input text-2xl font-extrabold leading-none text-brand-strong">‹</button>
        <img src={logo} alt="PPS Smart English" className="h-[22px] w-auto md:h-[29px]" />
        <h1 className="truncate text-lg font-black text-ink">{labelOf(question.type)}</h1>
      </header>
      <main className="mx-auto grid max-w-[1384px] items-start gap-5 px-4 py-3 md:px-7 lg:grid-cols-2">
        <section aria-label={writing ? 'Đề viết' : 'Đề nói'} className={card}>
          <p className="text-[13px] font-extrabold text-brand-strong">Câu {index + 1} · {writing ? 'Đề viết' : 'Đề nói'}</p>
          {question.instruction && <p className="text-base font-bold text-brand-strong">{question.instruction}</p>}
          <QuestionStem question={question} className="text-2xl font-black leading-[30px] text-ink" />
        </section>
        {writing ? <WritingFeedback o={o} /> : <SpeakingFeedback o={o} criteria={isReflexType(question.type)} />}
      </main>
      <nav aria-label="Câu có nhận xét" className="fixed inset-x-0 bottom-0 z-20 flex h-[72px] items-center justify-between gap-3 border-t border-line bg-surface px-4 md:px-7">
        <ol className="flex min-w-0 gap-1 overflow-x-auto">
          {bank.questions.map((q, i) => {
            const open = hasReview(outcomes.get(q.id))
            return (
              <li key={q.id}>
                <button type="button" disabled={!open} aria-label={`Câu ${i + 1}`} aria-current={q.id === questionId ? 'step' : undefined} onClick={() => onGo(q.id)} className="grid size-11 shrink-0 place-items-center disabled:cursor-default">
                  <span className={`grid size-9 place-items-center rounded-[10px] border text-[15px] font-extrabold ${q.id === questionId ? 'border-success bg-page text-success-fg' : open ? 'border-success bg-success-bg text-success-fg' : 'border-line bg-muted text-ink-secondary'}`}>{i + 1}</span>
                </button>
              </li>
            )
          })}
        </ol>
        <button type="button" onClick={onBack} className={pillPrimary}>Về kết quả</button>
      </nav>
    </div>
  )
}

function WritingFeedback({ o }: { o?: AiOutcome }) {
  const s = o?.writing
  const g = s?.grade
  if (!s || !g) return null
  return (
    <section aria-label="Đối chiếu bài viết" className={card}>
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-black text-ink">Đối chiếu bài viết</h2>
        <span className={chipCls}>{Math.round(g.scorePercent)}%</span>
      </div>
      <div className="grid gap-3 md:grid-cols-2">
        <article className="flex flex-col gap-2 rounded-lg bg-subtle p-4">
          <h3 className="text-[13px] font-extrabold text-brand-strong">Bản gốc</h3>
          <p className="whitespace-pre-wrap text-base font-semibold text-ink">{s.finalText}</p>
        </article>
        <article className="flex flex-col gap-2 rounded-lg bg-ai-bg p-4">
          <h3 className="text-[13px] font-extrabold text-ai">Bản sửa · Gợi ý diễn đạt</h3>
          <p className="whitespace-pre-wrap text-base font-semibold text-ink">{g.correctedAnswer}</p>
        </article>
      </div>
      {g.feedback && <p className="whitespace-pre-wrap text-base font-semibold text-ink">{g.feedback}</p>}
    </section>
  )
}

// Tiêu chí theo Figma 11.28: chỉ hiện tiêu chí engine Nói đã trả (GV, DM chưa có nên chưa hiện).
const CRITERIA = [['P', 'Phát âm', 'pronunciation'], ['FC', 'Độ trôi chảy', 'fluency'], ['LR', 'Từ vựng', 'lexical'], ['GRA', 'Ngữ pháp', 'grammar']] as const

function SpeakingFeedback({ o, criteria }: { o?: AiOutcome; criteria?: boolean }) {
  const sub = o?.speaking
  const r = sub?.result
  if (!sub || !r?.bands) return null
  return (
    <section aria-label="Nhận xét phát âm" className={card}>
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-black text-ink">Nhận xét phát âm</h2>
        <span className={chipCls}>Band {(r.overall ?? 0).toFixed(1)}</span>
      </div>
      {sub.audioUrl && <audio aria-label="Nghe lại bản ghi" controls src={sub.audioUrl} className="w-full" />}
      {criteria && (
        <div className="flex flex-col gap-2">
          <h3 className="text-sm font-extrabold text-ink">Điểm theo từng tiêu chí</h3>
          <ul className="grid grid-cols-2 gap-2">
            {CRITERIA.filter(([, , k]) => typeof r.bands?.[k] === 'number').map(([code, name, k]) => (
              <li key={code} className="flex justify-between rounded-md bg-subtle px-3 py-2 text-sm font-extrabold text-ink"><span>{code} · {name}</span><span>{r.bands![k].toFixed(1)}</span></li>
            ))}
          </ul>
        </div>
      )}
      {r.mispronounced && r.mispronounced.length > 0 && (
        <div className="flex flex-col gap-2">
          <h3 className="text-sm font-extrabold text-ink">Phát âm từng từ</h3>
          <ul className="flex flex-col gap-2">
            {r.mispronounced.map((w, i) => (
              <li key={`${w.word}-${i}`} className="rounded-md bg-ai-bg px-3 py-2 text-sm font-semibold text-ink">
                <b className="text-brand-strong">{w.word}</b>
                {w.expectedIpa && <> {w.expectedIpa}</>}
                {w.issues.length > 0 && <span className="block text-ink-secondary">{w.issues.join(', ')}</span>}
              </li>
            ))}
          </ul>
        </div>
      )}
      {r.feedback && <p className="whitespace-pre-wrap text-sm font-semibold text-ink-secondary">{r.feedback}</p>}
      {r.transcript && (
        <div className="flex flex-col gap-1">
          <h3 className="text-sm font-extrabold text-ink">Bản chép lời</h3>
          <p className="whitespace-pre-wrap text-sm font-semibold text-ink-secondary">{r.transcript}</p>
        </div>
      )}
    </section>
  )
}
