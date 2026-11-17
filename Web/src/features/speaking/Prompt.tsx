import type { ReactNode } from 'react'
import type { SpeakingQuestion } from './types'

const minutes = (s: number) => (s % 60 === 0 ? `${s / 60} phút` : `${s} giây`)

// Thẻ đề nói (Figma 283:5548): nhãn PART, đề, cue card (part 2), thời lượng tối đa; children là khung ghi âm.
export function Prompt({ question, children }: { question: SpeakingQuestion; children?: ReactNode }) {
  return (
    <section aria-label="Đề nói" className="flex flex-col gap-3.5 rounded-[20px] border border-line bg-surface p-5 md:p-7">
      <p className="text-[13px] font-extrabold leading-[18px] text-brand-strong">PART {question.part}</p>
      <p className="text-2xl font-black leading-[30px] text-ink">{question.prompt}</p>
      {question.cueCard.length > 0 && (
        <div>
          <p className="text-sm font-extrabold text-ink-secondary">You should say:</p>
          <ul className="mt-1 flex flex-col gap-1.5">
            {question.cueCard.map((c) => <li key={c} className="text-base font-semibold text-ink">• {c}</li>)}
          </ul>
        </div>
      )}
      <p className="w-fit rounded-full bg-subtle px-3 py-1.5 text-[13px] font-extrabold text-brand-ink">Nói tối đa {minutes(question.maxSeconds)}</p>
      {children}
    </section>
  )
}
