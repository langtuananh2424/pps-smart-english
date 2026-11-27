import { useCallback, useEffect, useRef, useState } from 'react'
import { saveDraft, saveDraftOnLeave } from './api'
import type { Answers } from './types'

export type DraftStatus = 'idle' | 'saving' | 'saved' | 'failed' | 'offline'
export const DRAFT_DELAY_MS = 2000
const MAX_RETRIES = 4 // thử lại 2s, 4s, 8s, 16s rồi chờ lần đổi đáp án kế tiếp

// Tự lưu nháp sau 2 giây không đổi, và lúc rời trang. Lỗi lưu không chặn làm bài.
export function useDraft(attemptId: number, answers: Answers, enabled: boolean, onClosed?: (code: string) => void) {
  const [status, setStatus] = useState<DraftStatus>('idle')
  const latest = useRef(answers)
  const dirty = useRef(false)
  const first = useRef(true)
  const fails = useRef(0)
  const over = useRef(false) // lượt đã đóng (hết giờ/đã nộp): không lưu nữa
  const retryTimer = useRef<ReturnType<typeof setTimeout>>(undefined)
  const closed = useRef(onClosed)
  closed.current = onClosed

  const flush = useCallback(async () => {
    if (!dirty.current || over.current) return
    dirty.current = false
    clearTimeout(retryTimer.current)
    setStatus('saving')
    try {
      await saveDraft(attemptId, latest.current)
      fails.current = 0
      setStatus('saved')
    } catch (e) {
      const code = (e as { code?: string }).code
      const closedNow = code === 'attempt_time_up' || code === 'attempt_already_submitted'
      if (closedNow) {
        over.current = true
        closed.current?.(code)
      } else dirty.current = true
      setStatus(navigator.onLine ? 'failed' : 'offline')
      if (!closedNow && fails.current < MAX_RETRIES) retryTimer.current = setTimeout(flush, DRAFT_DELAY_MS * 2 ** fails.current++)
    }
  }, [attemptId])

  useEffect(() => {
    latest.current = answers
    // Lần render đầu là nháp vừa nạp từ server, chưa có gì để lưu.
    if (first.current) {
      first.current = false
      return
    }
    if (!enabled) return
    dirty.current = true
    fails.current = 0
    const t = setTimeout(flush, DRAFT_DELAY_MS)
    return () => clearTimeout(t)
  }, [answers, enabled, flush])

  useEffect(() => {
    const leave = () => {
      if (dirty.current && !over.current) saveDraftOnLeave(attemptId, latest.current)
    }
    window.addEventListener('pagehide', leave)
    return () => {
      clearTimeout(retryTimer.current)
      window.removeEventListener('pagehide', leave)
      leave() // rời màn trong ứng dụng (đổi route)
    }
  }, [attemptId])

  // Đã dừng thử lại hoặc đang offline: có mạng lại thì lưu ngay.
  useEffect(() => {
    const online = () => {
      if (!dirty.current) return
      fails.current = 0
      void flush()
    }
    window.addEventListener('online', online)
    return () => window.removeEventListener('online', online)
  }, [flush])

  // Sau khi nộp: bỏ nháp chưa lưu để không gửi vào lượt đã đóng.
  const discard = useCallback(() => {
    dirty.current = false
  }, [])

  return { status, flush, discard }
}
