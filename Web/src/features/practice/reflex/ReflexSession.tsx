import { useCallback, useEffect, useRef, useState } from 'react'
import { PromptText, QuestionMediaList } from '../QuestionStem'
import { fetchReflex } from './api'
import { ReflexSpeak } from './ReflexSpeak'
import { ReflexStart } from './ReflexStart'
import { ReflexVideo, type ReflexApi } from './ReflexVideo'
import { ReflexWrite } from './ReflexWrite'
import { isSent } from '../ai/answer'
import { useQuery } from '@tanstack/react-query'
import { formatClock } from '../useCountdown'
import { pillNeutral } from '../questions/ui'
import type { AiSession, BankGroup, Question } from '../types'

interface Props {
  attemptId: number
  group: BankGroup
  questions: Question[] // Video phản xạ của nhóm, theo thứ tự câu (mốc at tăng dần)
  refs: Record<number, AiSession | undefined>
  disabled?: boolean
  onSpoken: (questionId: number, submissionId: number) => void
  track?: (ctl: AbortController) => () => void
}

type Step = 'watch' | 'write' | 'speak'
const atOf = (q: Question): number => q.payload?.at ?? 0

// Video phản xạ (Figma 10.28–10.31, 11.28–11.31, D-158): cả nhóm là một màn toàn màn hình. Video tự dừng ở mốc 'at' của câu,
// học viên (Câu ngắn: viết rồi) nói, rồi xem tiếp đến mốc sau. Không giám sát.
export function ReflexSession({ attemptId, group, questions, refs, disabled, onSpoken, track }: Props) {
  const root = useRef<HTMLDivElement>(null)
  const api = useRef<ReflexApi | null>(null)
  const [started, setStarted] = useState(false)
  const [idx, setIdx] = useState(() => Math.max(0, questions.findIndex((q) => !isSent(refs[q.id])))) // vào lại thì tiếp ở câu chưa nộp
  const [step, setStep] = useState<Step>('watch')
  const [replaying, setReplaying] = useState(false)
  const [recording, setRecording] = useState(false)
  const [finished, setFinished] = useState(() => questions.every((q) => isSent(refs[q.id])))
  const q = questions[idx]
  const from = idx > 0 ? atOf(questions[idx - 1]) : 0
  const shortQ = q?.type === 'VIDEO_SHORT'

  const progress = useQuery({ queryKey: ['attempt', attemptId, 'reflex', q?.id], queryFn: () => fetchReflex(attemptId, q.id), enabled: started && !!q && shortQ && step === 'write', retry: false, refetchInterval: (s) => (s.state.data?.writePending ? 3000 : false) })
  useEffect(() => { if (step === 'write' && progress.data?.speakOpen) setStep('speak') }, [step, progress.data?.speakOpen])

  const play = useCallback(() => api.current?.play(from, atOf(q)), [from, q])
  useEffect(() => { if (started && step === 'watch' && !finished) play() }, [started, step, idx, finished]) // eslint-disable-line react-hooks/exhaustive-deps

  const onMark = useCallback(() => {
    if (replaying) return setReplaying(false)
    setStep(shortQ ? 'write' : 'speak')
  }, [replaying, shortQ])
  const onRecording = useCallback((on: boolean) => { setRecording(on); if (on) api.current?.halt() }, [])

  const exit = () => { if (document.fullscreenElement) void document.exitFullscreen?.().catch(() => {}) }
  useEffect(() => exit, [])
  const next = () => {
    if (idx + 1 >= questions.length) { setFinished(true); exit(); return }
    setIdx(idx + 1)
    setStep('watch')
  }
  const replay = () => { setReplaying(true); api.current?.play(from, atOf(q)) }

  if (!started && !finished)
    return <div ref={root}><ReflexStart type={q.type} onStart={() => { setStarted(true); void root.current?.requestFullscreen?.().catch(() => {}) }} /></div>
  if (finished)
    return <p role="status" className="rounded-lg border-[1.5px] border-line bg-surface p-6 text-center text-lg font-black text-ink">Bạn đã làm xong các câu. Bấm Nộp bài để xem kết quả.</p>

  const label = recording ? 'ĐANG GHI ÂM · VIDEO TẠM KHÓA' : step === 'watch' || replaying ? 'ĐANG PHÁT ĐOẠN MẪU' : 'DỪNG TẠI MỐC CÂU HỎI'
  const p = q.payload ?? {}
  return (
    <div ref={root} className="flex flex-col gap-3 bg-page p-3 md:p-4">
      <p className="text-[13px] font-extrabold tracking-wide text-brand-strong">{label}</p>
      <ReflexVideo group={group} apiRef={api} onMark={onMark} expectPlaying={step === 'watch' || replaying} />
      {step === 'watch' && !replaying ? (
        <p role="status" className="text-center font-extrabold text-ink">Đang xem video. Câu hỏi sẽ mở khi video đến mốc.</p>
      ) : (
        <article aria-label={`Câu hỏi ${idx + 1}`} className="flex flex-col gap-3 rounded-lg border-[1.5px] border-line bg-surface p-4 md:p-6">
          <p className="text-[13px] font-extrabold text-brand-strong">CÂU HỎI {idx + 1}/{questions.length} · {formatClock(atOf(q))}</p>
          <p className="text-lg font-black leading-6 text-ink md:text-2xl md:leading-[30px]">{p.prompt}</p>
          {p.cue && <p className="rounded-md bg-subtle p-3 text-base font-semibold text-ink">{p.cue}</p>}
          {p.instruction && <p className="text-base font-semibold text-ink-secondary">{p.instruction}</p>}
          <PromptText question={q} className="text-base font-semibold text-ink-secondary" />
          <QuestionMediaList question={q} />
          {shortQ && <p className="text-sm font-extrabold text-ink-secondary">{step === 'write' ? '1 · Viết › 2 · Nói' : '✓ Viết › 2 · Nói'}</p>}
          <button type="button" disabled={recording || replaying || disabled} onClick={replay} className={`${pillNeutral} self-start`}>Nghe lại đoạn câu hỏi</button>
          {step === 'write' && <ReflexWrite attemptId={attemptId} questionId={q.id} disabled={disabled || replaying} />}
          {step === 'speak' && (
            <ReflexSpeak
              key={q.id} attemptId={attemptId} questionId={q.id} sent={isSent(refs[q.id])} replaying={replaying} last={idx + 1 >= questions.length}
              onRecording={onRecording} onSent={(id) => onSpoken(q.id, id)} onNext={next} track={track}
            />
          )}
        </article>
      )}
    </div>
  )
}
