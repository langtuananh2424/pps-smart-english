import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useRef, useState } from 'react'
import { ApiError } from '../../lib/api'
import { pillPrimary } from './questions/ui'
import { isAiType, labelOf } from '../exercises/format'
import { AiQuestion } from './ai/AiQuestion'
import { useAiRefs } from './ai/useAiRefs'
import { fetchProgress, fromItems, pauseAttempt, resumeAttempt, submitOrFetch } from './api'
import { BottomBar } from './BottomBar'
import { CamHint } from './CamHint'
import { ConfirmDialog } from './ConfirmDialog'
import { GroupPassage } from './GroupPassage'
import { GroupAudio } from './listening/GroupAudio'
import { PlayHeader } from './PlayHeader'
import { ErrorCard } from './StatusCard'
import { isAnswerable, isAnswered, rendererOf } from './questions/registry'
import type { AiSession, Answer, Answers, AttemptResult, Bank, Progress } from './types'
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
  const [error, setError] = useState<string | null>(null)
  const submitting = useRef(false)
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
      onDone(await submitOrFetch(attemptId, answersRef.current), answersRef.current)
    } catch (e) {
      submitting.current = false
      setBusy(false)
      // 429/503: lượt vẫn mở, cho nộp lại.
      setError(e instanceof ApiError && (e.status === 429 || e.status === 503) ? 'Hệ thống đang bận, vui lòng nộp lại sau ít giây.' : e instanceof ApiError ? e.message : 'Không nộp được, kiểm tra kết nối rồi nộp lại.')
    }
  }, [attemptId, onDone])
  const answersRef = useRef(answers)
  answersRef.current = answers
  const draft = useDraft(attemptId, answers, !paused, () => void submit())

  const secondsLeft = useCountdown(progress.remainingSec, paused, syncedAt, () => void submit())

  const setProgress = (p: Progress) => qc.setQueryData(['attempt', attemptId, 'progress'], p)
  const toggle = async (fn: typeof pauseAttempt) => {
    try {
      setProgress(await fn(attemptId))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Không thực hiện được, thử lại.')
    }
  }

  const doable = bank.questions.filter((x) => isAnswerable(x.type))
  // D-147: phiên mở mà chưa nộp chấm (NOT_SUBMITTED) tính 0 điểm nên cũng là chưa làm, như chưa mở phiên.
  const aiDone = (id: number) => ai.refs[id] != null && ai.refs[id].status !== 'NOT_SUBMITTED'
  const answered = bank.questions.map((x) => (isAiType(x.type) ? aiDone(x.id) : isAnswered(x.type, answers[x.id])))
  const unanswered = doable.filter((x) => !isAnswered(x.type, answers[x.id])).length + bank.questions.filter((x) => isAiType(x.type) && !aiDone(x.id)).length
  const { Component } = rendererOf(question.type)
  const group = question.groupId != null ? bank.groups?.find((g) => g.id === question.groupId) : undefined
  const twoCol = !!group?.passageHtml // Figma 10.15: bài đọc bên trái, thẻ câu bên phải từ md
  const onChange = (v: Answer | undefined) => setAnswers((a) => ({ ...a, [question.id]: v }))

  return (
    <div className="min-h-dvh bg-page pb-[72px]">
      <PlayHeader title={bank.title} secondsLeft={secondsLeft} draft={draft.status} canPause={canPause} paused={paused} onPause={() => toggle(pauseAttempt)} />
      <main className={`relative mx-auto px-4 py-3 md:py-3 ${twoCol ? 'max-w-[1200px]' : 'max-w-[900px]'}`}>
        <h2 className="mb-2.5 text-lg font-black leading-6 text-ink md:hidden">{bank.title}</h2>
        {error && !confirm && <p role="alert" className="mb-3 rounded-md bg-danger-bg px-4 py-2 text-sm font-bold text-danger">{error}</p>}
        <div className={twoCol ? 'grid items-start gap-3 md:grid-cols-2' : 'flex flex-col gap-3'}>
          {group && twoCol && <GroupPassage group={group} />}
          <div className="flex flex-col gap-3">
            {group && !twoCol && <GroupPassage group={group} />}
            {group?.hasAudio && <GroupAudio key={group.id} attemptId={attemptId} group={group} mode={bank.playbackMode ?? 'FREE'} defaultLimit={bank.playLimit} disabled={paused || busy} />}
        <article aria-label={`Câu ${index + 1}`} className="flex flex-col gap-3 rounded-lg border-[1.5px] border-line bg-surface p-4 md:p-6">
          <p className="text-[13px] font-extrabold leading-[18px] text-brand-strong">
            Câu {index + 1}/{bank.questions.length} · {question.instruction ?? labelOf(question.type)}
          </p>
          <p className="text-lg font-black leading-6 text-ink md:text-2xl md:leading-[30px]">{question.prompt}</p>
          {isAiType(question.type)
            ? <AiQuestion key={question.id} question={question} attemptId={attemptId} session={ai.refs[question.id]} onRef={(id, status) => ai.set(question.id, id, question.type as AiSession['kind'], status)} onClose={ai.refresh} loaded={ai.ready} disabled={paused || busy} />
            : <Component key={question.id} question={question} value={answers[question.id]} onChange={onChange} disabled={paused || busy} />}
        </article>
          </div>
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
      <CamHint hints={paused ? [] : (question.hints ?? [])} />
      <BottomBar count={bank.questions.length} index={index} answered={answered} onGo={setIndex} onSubmit={() => setConfirm(true)} disabled={paused || busy} />
      {confirm && <ConfirmDialog unanswered={unanswered} busy={busy} error={error} onConfirm={() => void submit()} onCancel={() => setConfirm(false)} />}
    </div>
  )
}
