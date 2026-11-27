import { useState } from 'react'
import { AiAnswer } from '../practice/ai/AiAnswer'
import type { SendAction } from '../practice/ai/answer'
import { pillPrimary } from '../practice/questions/ui'
import { PromptText, QuestionMediaList } from '../practice/QuestionStem'
import type { Bank, Question } from '../practice/types'
import type { AiStageState } from './useAiStage'

interface Props {
  kind: 'WRITING' | 'SPEAKING'
  bank: Bank
  attemptId: number
  currentId: number
  ai: AiStageState
  disabled: boolean
  onFocus: (questionId: number) => void
  onSkip: (questionId: number) => void
}

const tab = (on: boolean) => `min-h-11 flex-1 rounded-full border px-4 text-sm font-extrabold ${on ? 'border-2 border-brand bg-brand-soft text-ink' : 'border-line bg-surface text-ink-secondary'}`

// Thẻ trả lời của một câu: dùng lại logic phiên Viết/Nói của Player; nút gửi hiện ngay dưới bài làm.
function AiCard({ q, attemptId, ai, disabled, onFocus, onSkip }: { q: Question } & Pick<Props, 'attemptId' | 'ai' | 'disabled' | 'onFocus'> & { onSkip: () => void }) {
  const [send, setSend] = useState<SendAction | null>(null)
  return (
    <div className="flex flex-col gap-3" onFocusCapture={() => onFocus(q.id)} onPointerDown={() => onFocus(q.id)}>
      <AiAnswer
        question={q} attemptId={attemptId} session={ai.refs[q.id]} loaded={ai.ready} disabled={disabled}
        onRef={(id, status) => ai.set(q.id, id, q.type === 'WRITING' ? 'WRITING' : 'SPEAKING', status)} onSend={setSend} onSkip={onSkip} track={ai.track}
      />
      {send && <button type="button" disabled={disabled || !send.enabled} onClick={send.run} className={`${pillPrimary} self-start`}>{send.label ?? 'Gửi chấm'}</button>}
    </div>
  )
}

const box = 'flex flex-col gap-3 rounded-lg border-[1.5px] border-line bg-surface p-4 md:p-6'

// Viết (Figma 283:5271, 272:224): hai cột đề | bài viết; mobile hai tab. Mỗi câu WRITING là một task (chọn ở thanh đáy).
function WritingStage({ bank, attemptId, currentId, ai, disabled, onFocus, onSkip }: Omit<Props, 'kind'>) {
  const [side, setSide] = useState<'prompt' | 'text'>('prompt')
  const idx = Math.max(0, bank.questions.findIndex((q) => q.id === currentId))
  const q = bank.questions[idx]
  const task: string | undefined = q.payload?.task
  return (
    <div className="flex flex-col gap-3 land:h-full">
      <div role="tablist" aria-label="Đề bài và bài viết" className="flex gap-2 md:hidden">
        <button type="button" role="tab" aria-selected={side === 'prompt'} onClick={() => setSide('prompt')} className={tab(side === 'prompt')}>Đề bài</button>
        <button type="button" role="tab" aria-selected={side === 'text'} onClick={() => setSide('text')} className={tab(side === 'text')}>Bài viết</button>
      </div>
      <div className="grid items-start gap-4 md:grid-cols-2 land:min-h-0 land:flex-1 land:grid-cols-2">
        <section aria-label="Đề bài" className={`${box} ${side === 'prompt' ? '' : 'hidden md:flex'}`}>
          <p className="text-xs font-extrabold uppercase text-brand-strong">Task {idx + 1}</p>
          <PromptText question={q} className="text-base font-black text-ink" />
          {task && task !== q.prompt && <p className="text-base font-semibold text-ink">{task}</p>}
          <QuestionMediaList question={q} />
        </section>
        <div className={side === 'text' ? '' : 'hidden md:block'}>
          <AiCard key={q.id} q={q} attemptId={attemptId} ai={ai} disabled={disabled} onFocus={onFocus} onSkip={() => onSkip(q.id)} />
        </div>
      </div>
    </div>
  )
}

const partOf = (q: Question): number => q.payload?.part ?? 1

// Nói (Figma 283:5527, 272:456): một cột, tab Part theo payload.part, trong part liệt kê câu; Part 2 có cue card.
function SpeakingStage({ bank, attemptId, currentId, ai, disabled, onFocus, onSkip }: Omit<Props, 'kind'>) {
  const parts = [...new Set(bank.questions.map(partOf))].sort()
  const cur = bank.questions.find((q) => q.id === currentId)
  const [part, setPart] = useState(cur ? partOf(cur) : parts[0])
  const list = bank.questions.filter((q) => partOf(q) === part)
  const pick = (p: number) => {
    setPart(p)
    const first = bank.questions.find((q) => partOf(q) === p)
    if (first) onFocus(first.id)
  }
  return (
    <div className="mx-auto flex max-w-[900px] flex-col gap-3">
      <div role="tablist" aria-label="Part" className="flex gap-2">
        {parts.map((p) => <button key={p} type="button" role="tab" aria-selected={p === part} onClick={() => pick(p)} className={tab(p === part)}>Part {p}</button>)}
      </div>
      {part === 2 && <p className="text-sm font-extrabold text-ink-secondary">Chuẩn bị 1 phút · Nói 1–2 phút</p>}
      {list.map((q) => (
        <article key={q.id} aria-label={`Câu ${bank.questions.indexOf(q) + 1}`} className={`${box} ${q.id === currentId ? 'border-line-brand' : ''}`}>
          <PromptText question={q} className="text-base font-black text-ink" />
          {q.payload?.prompt && q.payload.prompt !== q.prompt && <p className="text-base font-semibold text-ink">{q.payload.prompt}</p>}
          <QuestionMediaList question={q} />
          <AiCard q={q} attemptId={attemptId} ai={ai} disabled={disabled} onFocus={onFocus} onSkip={() => onSkip(q.id)} />
        </article>
      ))}
    </div>
  )
}

export const AiStage = ({ kind, ...p }: Props) => (kind === 'WRITING' ? <WritingStage {...p} /> : <SpeakingStage {...p} />)
