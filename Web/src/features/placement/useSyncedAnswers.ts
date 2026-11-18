import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../../lib/api'
import type { Answers } from '../practice/types'
import { DRAFT_DELAY_MS, type DraftStatus } from '../practice/useDraft'
import { isNetworkError, saveDraft } from './api'
import { saveLocal } from './store'
import type { Saved } from './types'

export const RETRY_MS = 10_000

// D-89: mỗi thay đổi lưu ngay vào localStorage; gửi nháp lên server sau 2 giây không đổi và khi có mạng lại.
// Mất mạng thì vẫn làm tiếp, trạng thái 'offline'. onClosed: server báo lượt đã hết giờ hoặc đã nộp.
export function useSyncedAnswers(saved: Saved, onClosed: () => void) {
  const [answers, setAnswersState] = useState<Answers>(saved.answers)
  const [status, setStatus] = useState<DraftStatus>('idle')
  const [deadlineMs, setDeadlineMs] = useState(saved.deadlineMs)
  const latest = useRef({ answers, deadlineMs })
  const dirty = useRef(false)
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined)
  const closed = useRef(onClosed)
  closed.current = onClosed

  const flush = useCallback(async () => {
    clearTimeout(timer.current)
    if (!dirty.current) return
    if (!navigator.onLine) return setStatus('offline') // sự kiện 'online' sẽ gọi lại
    dirty.current = false
    setStatus('saving')
    try {
      const { remainingSec } = await saveDraft(saved.attemptId, latest.current.answers, saved.guestToken)
      const ms = Date.now() + remainingSec * 1000
      latest.current.deadlineMs = ms
      setDeadlineMs(ms)
      saveLocal({ ...saved, answers: latest.current.answers, deadlineMs: ms })
      setStatus(dirty.current ? 'saving' : 'saved')
    } catch (e) {
      if (e instanceof ApiError && (e.code === 'attempt_time_up' || e.code === 'attempt_already_submitted')) return closed.current()
      dirty.current = true
      setStatus(isNetworkError(e) ? 'offline' : 'failed')
      timer.current = setTimeout(() => void flush(), RETRY_MS)
    }
  }, [saved])

  const setAnswers = useCallback(
    (next: Answers) => {
      latest.current.answers = next
      setAnswersState(next)
      dirty.current = true
      saveLocal({ ...saved, answers: next, deadlineMs: latest.current.deadlineMs })
      clearTimeout(timer.current)
      timer.current = setTimeout(() => void flush(), DRAFT_DELAY_MS)
    },
    [saved, flush],
  )

  useEffect(() => {
    const online = () => void flush()
    window.addEventListener('online', online)
    return () => window.removeEventListener('online', online)
  }, [flush])
  useEffect(() => () => clearTimeout(timer.current), [])

  // Sau khi nộp: không gửi thêm nháp vào lượt đã đóng.
  const stop = useCallback(() => {
    dirty.current = false
    clearTimeout(timer.current)
  }, [])

  const getAnswers = useCallback(() => latest.current.answers, [])

  return { answers, setAnswers, getAnswers, status, deadlineMs, stop }
}
