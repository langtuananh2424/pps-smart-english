import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { ApiError } from '../../lib/api'
import { fetchProgress, fromItems, pauseAttempt, resumeAttempt, submitOrFetch } from '../practice/api'
import { pillPrimary } from '../practice/questions/ui'
import { isAnswered } from '../practice/questions/registry'
import { ErrorCard } from '../practice/StatusCard'
import type { Answer, Answers, Bank, Progress } from '../practice/types'
import { useCountdown } from '../practice/useCountdown'
import { useCheck } from '../practice/useCheck'
import { useDraft } from '../practice/useDraft'
import { AiNav } from './AiNav'
import { AiStage } from './AiStage'
import { EndConfirm } from './EndConfirm'
import { ExamHeader } from './ExamHeader'
import { IeltsCam } from './IeltsCam'
import { aiStageOf, buildParts, flatParts, navState, type NavState } from './parts'
import { PartNav } from './PartNav'
import { PassagePane } from './PassagePane'
import { QuestionsPane } from './QuestionsPane'
import { ReviewScreen } from './ReviewScreen'
import type { IeltsMode } from './types'
import { useAiStage, type AiStageState } from './useAiStage'
import { useFlags } from './useFlags'
import { isSent, writingKey } from '../practice/ai/answer'
import { clearStoredText } from '../writing/useStoredText'

interface Props { bank: Bank; attemptId: number; onDone: (attemptId: number) => void }

const SYNC_MS = 60_000
const FONTS = ['text-base', 'text-lg', 'text-xl']

// Nạp tiến độ (nháp, đồng hồ theo giờ máy chủ, chế độ) rồi mới dựng phiên thi.
export function ExamSession(props: Props) {
  const q = useQuery({ queryKey: ['attempt', props.attemptId, 'progress'], queryFn: () => fetchProgress(props.attemptId), refetchInterval: SYNC_MS, retry: false })
  if (q.isError && !q.data) return <ErrorCard error={q.error} onRetry={() => q.refetch()} />
  if (!q.data) return <p role="status" className="p-8 text-center font-bold text-ink-secondary">Đang tải…</p>
  // Khung Viết/Nói cần phiên chấm của lượt; khung đọc/nghe không gọi API này.
  const Host = aiStageOf(props.bank) ? AiHost : Session
  return <Host {...props} progress={q.data} syncedAt={q.dataUpdatedAt} />
}

function AiHost(p: Props & { progress: Progress; syncedAt: number }) {
  return <Session {...p} ai={useAiStage(p.attemptId)} />
}

function Session({ bank, attemptId, onDone, progress, syncedAt, ai }: Props & { progress: Progress; syncedAt: number; ai?: AiStageState }) {
  const stage = ai ? aiStageOf(bank) : null
  const qc = useQueryClient()
  const mode: IeltsMode = progress.mode ?? 'PRACTICE'
  const paused = progress.paused
  const canPause = mode === 'PRACTICE' && bank.timeLimitSec != null // D-67, D-151
  const { parts, ordered } = useMemo(() => (stage ? flatParts(bank) : buildParts(bank)), [bank, stage])
  const [answers, setAnswers] = useState<Answers>(() => fromItems(progress.answers))
  const [currentId, setCurrentId] = useState(ordered[0]?.id ?? 0)
  const [view, setView] = useState<'exam' | 'review'>('exam')
  const [confirm, setConfirm] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [font, setFont] = useState(0)
  const { flags, toggle, clear } = useFlags(attemptId)
  const submitting = useRef(false)
  const answersRef = useRef(answers)
  answersRef.current = answers

  const submit = useCallback(async () => {
    if (submitting.current) return
    submitting.current = true
    setBusy(true)
    setError(null)
    try {
      draft.discard()
      await submitOrFetch(attemptId, answersRef.current)
      if (stage) bank.questions.forEach((x) => clearStoredText(writingKey(attemptId, x.id)))
      clear()
      onDone(attemptId)
    } catch (e) {
      submitting.current = false
      setBusy(false)
      setError(e instanceof ApiError && (e.status === 429 || e.status === 503) ? 'Hệ thống đang bận, vui lòng nộp lại sau ít giây.' : e instanceof ApiError ? e.message : 'Không nộp được, kiểm tra kết nối rồi nộp lại.')
    }
  }, [attemptId, onDone, clear, stage, bank.questions])
  const draft = useDraft(attemptId, answers, !paused, () => void submit())
  // Hết giờ theo đồng hồ máy chủ: tự nộp thẳng, không qua màn xem lại.
  const secondsLeft = useCountdown(progress.remainingSec, paused, syncedAt, () => void submit())

  // D-150: thử lại chỉ có ở lượt Luyện tập (máy chủ chỉ trả progress.retry khi áp dụng)
  const retry = useCheck(attemptId, progress.retry, () => void submit())
  const runCheck = async (id: number) => {
    setError(null)
    const msg = await retry.check(id, answersRef.current[id] ?? {})
    if (msg) setError(msg)
  }

  const toggleRun = async (fn: typeof pauseAttempt) => {
    try {
      qc.setQueryData(['attempt', attemptId, 'progress'], await fn(attemptId))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Không thực hiện được, thử lại.')
    }
  }

  const states = useMemo(() => Object.fromEntries(ordered.map((x) => [x.id, navState(ai ? isSent(ai.refs[x.id]) : isAnswered(x.type, answers[x.id]), flags.has(x.id))])) as Record<number, NavState>, [ordered, answers, flags, ai])
  const partIdx = Math.max(0, parts.findIndex((p) => p.questions.some((x) => x.id === currentId)))
  const part = parts[partIdx]
  if (!part) return <ErrorCard error={new Error('Bộ bài chưa có câu hỏi.')} />

  const go = (id: number) => {
    setCurrentId(id)
    setView('exam')
    requestAnimationFrame(() => document.getElementById(`ielts-q-${id}`)?.scrollIntoView?.({ block: 'center' }))
  }
  const onChange = (id: number, v: Answer | undefined) => setAnswers((a) => ({ ...a, [id]: v }))

  const unsent = ai ? ordered.filter((x) => !isSent(ai.refs[x.id])).length : 0
  const unsentNote = unsent > 0 ? `${unsent} câu chưa gửi chấm sẽ tính 0 điểm.` : null
  if (view === 'review') {
    return (
      <>
        <ReviewScreen skill={bank.skill} parts={parts} states={states} onBack={() => setView('exam')} onGo={go} onConfirm={() => setConfirm(true)} />
        {confirm && <EndConfirm busy={busy} error={error} onBack={() => setConfirm(false)} note={unsentNote} onConfirm={() => void submit()} />}
      </>
    )
  }
  const lock = paused || busy || !!ai?.uploading
  const hasLeft = !stage && !!part.group && (!!part.group.passageHtml || !!part.group.title || part.group.hasAudio)
  return (
    <div className="min-h-dvh bg-page pb-[88px] land:flex land:h-dvh land:flex-col land:overflow-hidden land:pb-0">
      <ExamHeader title={bank.title} mode={mode} secondsLeft={secondsLeft} canPause={canPause} paused={paused} onPause={() => void toggleRun(pauseAttempt)} onFont={() => setFont((f) => (f + 1) % FONTS.length)} />
      <main className={`relative mx-auto w-full max-w-[1440px] px-4 py-4 md:px-7 land:min-h-0 land:flex-1 land:overflow-y-auto land:py-2 ${FONTS[font]}`}>
        {error && !confirm && <p role="alert" className="mb-3 rounded-md bg-danger-bg px-4 py-2 text-sm font-bold text-danger">{error}</p>}
        {stage && ai ? (
          <AiStage kind={stage} bank={bank} attemptId={attemptId} currentId={currentId} ai={ai} disabled={paused || busy} onFocus={setCurrentId} onSkip={(id) => { const n = ordered[ordered.findIndex((x) => x.id === id) + 1]; if (n) go(n.id); else setView('review') }} />
        ) : (
        <div className={hasLeft ? 'grid items-start gap-4 md:grid-cols-2 land:h-full land:grid-cols-2 land:items-stretch' : 'mx-auto max-w-[900px]'}>
          {hasLeft && part.group && <div className="land:min-h-0 land:overflow-y-auto"><PassagePane key={part.key} bank={bank} group={part.group} attemptId={attemptId} disabled={paused || busy} exam={mode === 'EXAM'} /></div>}
          <div className="land:min-h-0 land:overflow-y-auto land:pb-14"><QuestionsPane part={part} answers={answers} flagged={flags} currentId={currentId} disabled={paused || busy} onChange={onChange} onFocus={setCurrentId} retry={retry.enabled ? { checks: retry.checks, pending: retry.pending, onCheck: (id) => void runCheck(id) } : undefined} /></div>
        </div>
        )}
        {paused && (
          <div className="absolute inset-0 z-10 grid place-items-center bg-page/95 px-4 backdrop-blur-sm">
            <div className="flex flex-col items-center gap-3 text-center">
              <p className="text-xl font-black text-ink">Đang tạm dừng</p>
              <button type="button" onClick={() => void toggleRun(resumeAttempt)} className={pillPrimary}>Tiếp tục</button>
            </div>
          </div>
        )}
      </main>
      {mode === 'PRACTICE' && <IeltsCam bankId={bank.id} attemptId={attemptId} questionId={currentId} hidden={paused} notice={retry.wrong?.questionId === currentId ? 'Cần sửa, thử lại nhé' : null} openKey={retry.wrong?.questionId === currentId ? retry.wrong.n : undefined} />}
      {stage ? (
        <AiNav tabs={stage === 'WRITING' ? ordered.map((x, i) => ({ id: x.id, label: `Task ${i + 1}` })) : undefined} currentId={currentId} onGo={go} onSubmit={() => setView('review')} submitLabel={stage === 'WRITING' ? 'Nộp bài' : 'Kết thúc'} disabled={lock} />
      ) : (
        <PartNav skill={bank.skill} parts={parts} currentId={currentId} states={states} flaggedNow={flags.has(currentId)} onGo={go} onFlag={() => toggle(currentId)} onSubmit={() => setView('review')} disabled={lock} />
      )}
    </div>
  )
}
