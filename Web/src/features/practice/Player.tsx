import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../../lib/api'
import { pillPrimary } from './questions/ui'
import { isAiType, labelOf } from '../exercises/format'
import { AiAnswer } from './ai/AiAnswer'
import { isSent, writingKey, type SendAction } from './ai/answer'
import { useAiRefs } from './ai/useAiRefs'
import { fetchProgress, fromItems, pauseAttempt, resumeAttempt, submitOrFetch } from './api'
import { BottomBar } from './BottomBar'
import { CheckBar } from './CheckBar'
import { CamHint } from './CamHint'
import { ConfirmDialog } from './ConfirmDialog'
import { GroupPassage } from './GroupPassage'
import { GroupAudio } from './listening/GroupAudio'
import { PlayHeader } from './PlayHeader'
import { QuestionStem } from './QuestionStem'
import { ErrorCard } from './StatusCard'
import { isAnswerable, isAnswered, rendererOf } from './questions/registry'
import type { AiSession, Answer, Answers, AttemptResult, Bank, Progress } from './types'
import { clearStoredText } from '../writing/useStoredText'
import { useCheck, isCheckable, isLocked } from './useCheck'
import { useCountdown } from './useCountdown'
import { useDraft } from './useDraft'

interface Props {
  bank: Bank
  attemptId: number
  onDone: (result: AttemptResult, answers: Answers) => void
}

const SYNC_MS = 60_000

// Nạp tiến độ (nháp + đồng hồ) của lượt rồi mới dựng phiên làm bài.
export function Player(props: Props) {
  const q = useQuery({
    queryKey: ['attempt', props.attemptId, 'progress'],
    queryFn: () => fetchProgress(props.attemptId),
    refetchInterval: SYNC_MS, // đồng bộ đồng hồ; đổi tab quay lại thì react-query tự refetch
    retry: false,
  })
  if (q.isError && !q.data) return <ErrorCard error={q.error} onRetry={() => q.refetch()} />
  if (!q.data) return <p role="status" className="p-8 text-center font-bold text-ink-secondary">Đang tải…</p>
  return <Session {...props} progress={q.data} syncedAt={q.dataUpdatedAt} />
}

function Session({ bank, attemptId, onDone, progress, syncedAt }: Props & { progress: Progress; syncedAt: number }) {
  const qc = useQueryClient()
  const [answers, setAnswers] = useState<Answers>(() => fromItems(progress.answers))
  const [index, setIndex] = useState(0)
  const [confirm, setConfirm] = useState(false)
  const [busy, setBusy] = useState(false)
  const [send, setSend] = useState<SendAction | null>(null) // "Gửi chấm" của câu Viết/Nói đang mở
  const [error, setError] = useState<string | null>(null)
  const submitting = useRef(false)
  const uploads = useRef(new Set<AbortController>())
  const [uploading, setUploading] = useState(false)
  const track = useCallback((ctl: AbortController) => {
    uploads.current.add(ctl)
    setUploading(true)
    return () => { uploads.current.delete(ctl); setUploading(uploads.current.size > 0) }
  }, [])
  useEffect(() => () => uploads.current.forEach((c) => c.abort()), []) // rời Player thì hủy tải lên đang dở
  const ai = useAiRefs(attemptId)
  const question = bank.questions[index]
  const paused = progress.paused
  const canPause = bank.mode === 'PRACTICE' && bank.timeLimitSec != null // D-67

  const submit = useCallback(async () => {
    if (submitting.current) return
    submitting.current = true
    setBusy(true)
    setError(null)
    try {
      draft.discard()
      const result = await submitOrFetch(attemptId, answersRef.current)
      bank.questions.forEach((x) => clearStoredText(writingKey(attemptId, x.id)))
      onDone(result, answersRef.current)
    } catch (e) {
      submitting.current = false
      setBusy(false)
      // 429/503: lượt vẫn mở, cho nộp lại.
      setError(e instanceof ApiError && (e.status === 429 || e.status === 503) ? 'Hệ thống đang bận, vui lòng nộp lại sau ít giây.' : e instanceof ApiError ? e.message : 'Không nộp được, kiểm tra kết nối rồi nộp lại.')
    }
  }, [attemptId, bank.questions, onDone])
  const answersRef = useRef(answers)
  answersRef.current = answers
  const draft = useDraft(attemptId, answers, !paused, () => void submit())

  const retry = useCheck(attemptId, progress.retry, () => void submit())
  const checkState = retry.checks[question.id]
  const checkable = retry.enabled && isCheckable(question.type)
  const locked = checkable && isLocked(checkState)
  const runCheck = async () => {
    setError(null)
    const msg = await retry.check(question.id, answers[question.id] ?? {})
    if (msg) setError(msg)
  }

  const secondsLeft = useCountdown(progress.remainingSec, paused, syncedAt, () => void submit())

  const setProgress = (p: Progress) => qc.setQueryData(['attempt', attemptId, 'progress'], p)
  const toggle = async (fn: typeof pauseAttempt) => {
    try {
      setProgress(await fn(attemptId))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Không thực hiện được, thử lại.')
    }
  }

  // D-147: câu Viết/Nói chỉ tính là đã làm khi đã gửi chấm; bản mở mà chưa gửi (NOT_SUBMITTED, AWAITING_UPLOAD) hay chấm lỗi tính 0 điểm.
  const answered = bank.questions.map((x) => (isAiType(x.type) ? isSent(ai.refs[x.id]) : isAnswered(x.type, answers[x.id])))
  const pending = bank.questions.flatMap((x, i) => (!answered[i] && (isAiType(x.type) || isAnswerable(x.type)) ? [i] : []))
  const zeroAi = pending.filter((i) => isAiType(bank.questions[i].type)).length
  const grading = Object.values(ai.refs).filter((s) => s.status === 'GRADING').length
  const aiQ = isAiType(question.type)
  const last = index === bank.questions.length - 1
  const { Component } = rendererOf(question.type)
  const group = question.groupId != null ? bank.groups?.find((g) => g.id === question.groupId) : undefined
  const twoCol = !!group?.passageHtml // Figma 10.15: bài đọc bên trái, thẻ câu bên phải từ md
  const openConfirm = () => {
    ai.refresh() // số câu đang chấm theo trạng thái mới nhất
    setConfirm(true)
  }
  const onChange = (v: Answer | undefined) => setAnswers((a) => ({ ...a, [question.id]: v }))

  return (
    <div className="min-h-dvh bg-page pb-[124px] md:pb-[72px]">
      <PlayHeader title={bank.title} secondsLeft={secondsLeft} draft={draft.status} canPause={canPause} paused={paused} onPause={() => toggle(pauseAttempt)} />
      <main className={`relative mx-auto px-4 py-3 md:py-3 ${twoCol ? 'max-w-[1200px]' : 'max-w-[900px]'}`}>
        <h2 className="mb-2.5 text-lg font-black leading-6 text-ink md:hidden">{bank.title}</h2>
        {error && !confirm && <p role="alert" className="mb-3 rounded-md bg-danger-bg px-4 py-2 text-sm font-bold text-danger">{error}</p>}
        <div className={twoCol || aiQ ? 'grid items-start gap-3 md:grid-cols-2' : 'flex flex-col gap-3'}>
          {group && twoCol && <GroupPassage group={group} />}
          <div className="flex flex-col gap-3">
            {group && !twoCol && <GroupPassage group={group} />}
            {group?.hasAudio && <GroupAudio key={group.id} attemptId={attemptId} group={group} mode={bank.playbackMode ?? 'FREE'} defaultLimit={bank.playLimit} disabled={paused || busy} />}
        <article aria-label={`Câu ${index + 1}`} className="flex flex-col gap-3 rounded-lg border-[1.5px] border-line bg-surface p-4 md:p-6">
          <p className="text-[13px] font-extrabold leading-[18px] text-brand-strong">
            Câu {index + 1}/{bank.questions.length} · {question.instruction ?? labelOf(question.type)}
          </p>
          <QuestionStem question={question} className="text-lg font-black leading-6 text-ink md:text-2xl md:leading-[30px]" />
          {!aiQ && <Component key={question.id} question={question} value={answers[question.id]} onChange={onChange} disabled={paused || busy || locked} />}
          {checkable && <CheckBar state={checkState} canCheck={!paused && !busy && isAnswered(question.type, answers[question.id])} pending={retry.pending} onCheck={() => void runCheck()} />}
        </article>
          </div>
          {aiQ && (
            <div className="flex flex-col gap-3">
              <AiAnswer
                key={question.id} question={question} attemptId={attemptId} session={ai.refs[question.id]} loaded={ai.ready} disabled={paused || busy}
                onRef={(id, status) => ai.set(question.id, id, question.type as AiSession['kind'], status)} onSend={setSend} track={track}
                onSkip={() => (last ? openConfirm() : setIndex(index + 1))}
              />
            </div>
          )}
        </div>
        {paused && (
          <div className="absolute inset-0 z-10 grid place-items-center rounded-lg bg-page/95 px-4 backdrop-blur-sm">
            <div className="flex flex-col items-center gap-3 text-center">
              <p className="text-xl font-black text-ink">Đang tạm dừng</p>
              <button type="button" onClick={() => toggle(resumeAttempt)} className={`${pillPrimary} md:!px-8`}>
                Tiếp tục
              </button>
            </div>
          </div>
        )}
      </main>
      <CamHint hints={paused ? [] : (question.hints ?? [])} notice={checkable && !locked && retry.wrong?.questionId === question.id ? 'Cần sửa, thử lại nhé' : null} openKey={retry.wrong?.questionId === question.id ? retry.wrong.n : undefined} />
      <BottomBar count={bank.questions.length} index={index} answered={answered} onGo={setIndex} onSubmit={openConfirm} disabled={paused || busy} submitDisabled={uploading} send={aiQ ? send : null} />
      {confirm && <ConfirmDialog pending={pending} zeroAi={zeroAi} grading={grading} busy={busy} error={error} onGo={(i) => { setIndex(i); setConfirm(false) }} onConfirm={() => void submit()} onCancel={() => setConfirm(false)} />}
    </div>
  )
}
