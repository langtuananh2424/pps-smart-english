import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { ApiError } from '../../lib/api'
import { labelOf } from '../exercises/format'
import { BottomBar } from '../practice/BottomBar'
import { ConfirmDialog } from '../practice/ConfirmDialog'
import { PlayHeader } from '../practice/PlayHeader'
import { QuestionStem } from '../practice/QuestionStem'
import { isAnswerable, isAnswered, rendererOf } from '../practice/questions/registry'
import type { Answer } from '../practice/types'
import { useCountdown } from '../practice/useCountdown'
import { isNetworkError, submitPlacement } from './api'
import { inlineMessage } from './errors'
import { SKILL_LABEL } from './labels'
import type { Saved, Started, Submitted } from './types'
import { useSyncedAnswers } from './useSyncedAnswers'

const RETRY_SUBMIT_MS = 5000

interface Props {
  saved: Saved & { started: Started }
  onDone: (r: Submitted) => void
}

// Chờ sự kiện 'online' hoặc một nhịp ngắn (navigator.onLine không tin cậy hoàn toàn).
const untilOnline = () =>
  new Promise<void>((resolve) => {
    const t = setTimeout(done, RETRY_SUBMIT_MS)
    function done() {
      clearTimeout(t)
      window.removeEventListener('online', done)
      resolve()
    }
    window.addEventListener('online', done)
  })

// Khung làm bài của kiểm tra đầu vào (D-87): không tạm dừng, không gợi ý Cam, mỗi trang một câu.
export function Session({ saved, onDone }: Props) {
  const { started } = saved
  const [index, setIndex] = useState(0)
  const [confirm, setConfirm] = useState(false)
  const [busy, setBusy] = useState(false)
  const [waiting, setWaiting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const submitting = useRef(false)
  const alive = useRef(true)
  useEffect(() => {
    alive.current = true
    return () => { alive.current = false }
  }, [])

  const submitRef = useRef<() => void>(() => {})
  const sync = useSyncedAnswers(saved, () => submitRef.current())
  const { stop, getAnswers } = sync

  const submit = useCallback(async () => {
    if (submitting.current) return
    submitting.current = true
    setBusy(true)
    setError(null)
    stop()
    // Mất mạng: giữ lượt, chờ có mạng rồi gửi lại (câu trả lời đã nằm trên máy).
    while (alive.current) {
      try {
        return onDone(await submitPlacement(saved.attemptId, getAnswers(), saved.guestToken))
      } catch (e) {
        // Lượt đã được nộp (hết giờ tự nộp hoặc tab khác): trang cha lấy kết quả có sẵn.
        if (e instanceof ApiError && e.code === 'attempt_already_submitted') return onDone({ submitted: true, requiresLogin: !!saved.guestToken })
        if (!isNetworkError(e)) {
          submitting.current = false
          setBusy(false)
          setError(inlineMessage(e))
          return
        }
        setWaiting(true)
        await untilOnline()
      }
    }
  }, [saved, onDone, stop, getAnswers])
  submitRef.current = () => void submit()

  const initialLeft = useMemo(() => Math.max(0, Math.ceil((sync.deadlineMs - Date.now()) / 1000)), [sync.deadlineMs])
  const secondsLeft = useCountdown(initialLeft, false, sync.deadlineMs, () => void submit())

  const qs = started.questions
  const question = qs[index]
  const { Component } = rendererOf(question.type)
  const answered = qs.map((q) => isAnswered(q.type, sync.answers[q.id]))
  const pending = qs.flatMap((q, i) => (isAnswerable(q.type) && !answered[i] ? [i] : []))
  const onChange = (v: Answer | undefined) => sync.setAnswers({ ...sync.answers, [question.id]: v })

  return (
    <div className="min-h-dvh bg-page pb-[124px] md:pb-[72px]">
      <PlayHeader title={started.title} secondsLeft={secondsLeft} draft={sync.status} canPause={false} paused={false} onPause={() => {}} back={{ to: '/khoa-hoc', label: 'Rời bài kiểm tra, bài làm được giữ trên máy' }} />
      <main className="mx-auto max-w-[900px] px-4 py-3">
        <h2 className="mb-2.5 text-lg font-black leading-6 text-ink md:hidden">{started.title}</h2>
        {(sync.status === 'offline' || waiting) && (
          <p role="status" className="mb-3 rounded-md bg-subtle px-4 py-2 text-sm font-bold text-brand-ink">
            {waiting ? 'Mất mạng: bài sẽ được nộp ngay khi có mạng. Bạn không cần làm lại.' : 'Đang lưu trên máy, sẽ gửi khi có mạng. Bạn vẫn làm bài tiếp được.'}
          </p>
        )}
        {error && !confirm && <p role="alert" className="mb-3 rounded-md bg-danger-bg px-4 py-2 text-sm font-bold text-danger">{error}</p>}
        <article aria-label={`Câu ${index + 1}`} className="flex flex-col gap-3 rounded-lg border-[1.5px] border-line bg-surface p-4 md:p-6">
          <p className="text-[13px] font-extrabold leading-[18px] text-brand-strong">
            Câu {index + 1}/{qs.length} · {SKILL_LABEL[question.skill] ?? question.skill} · {labelOf(question.type)}
          </p>
          <QuestionStem question={question} className="whitespace-pre-line text-lg font-black leading-6 text-ink md:text-2xl md:leading-[30px]" />
          <Component key={question.id} question={question} value={sync.answers[question.id]} onChange={onChange} disabled={busy} />
        </article>
      </main>
      <BottomBar count={qs.length} index={index} answered={answered} onGo={setIndex} onSubmit={() => setConfirm(true)} disabled={busy} />
      {confirm && <ConfirmDialog pending={pending} zeroAi={0} grading={0} busy={busy} error={error} onGo={(i) => { setIndex(i); setConfirm(false) }} onConfirm={() => void submit()} onCancel={() => setConfirm(false)} />}
    </div>
  )
}
