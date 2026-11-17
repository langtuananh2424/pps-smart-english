import { useCallback, useEffect, useRef, useState } from 'react'
import { saveDraft, saveDraftOnLeave } from './api'
import type { Answers } from './types'

export type DraftStatus = 'idle' | 'saving' | 'saved' | 'failed'
export const DRAFT_DELAY_MS = 2000

// Tự lưu nháp sau 2 giây không đổi, và lúc rời trang. Lỗi lưu không chặn làm bài.
export function useDraft(attemptId: number, answers: Answers, enabled: boolean, onClosed?: (code: string) => void) {
  const [status, setStatus] = useState<DraftStatus>('idle')
  const latest = useRef(answers)
  const dirty = useRef(false)
  const first = useRef(true)
  const closed = useRef(onClosed)
  closed.current = onClosed

  const flush = useCallback(async () => {
    if (!dirty.current) return
    dirty.current = false
    setStatus('saving')
    try {
      await saveDraft(attemptId, latest.current)
      setStatus('saved')
    } catch (e) {
      const code = (e as { code?: string }).code
      if (code === 'attempt_time_up' || code === 'attempt_already_submitted') closed.current?.(code)
      dirty.current = true
      setStatus('failed')
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
    const t = setTimeout(flush, DRAFT_DELAY_MS)
    return () => clearTimeout(t)
  }, [answers, enabled, flush])

  useEffect(() => {
    const leave = () => {
      if (dirty.current) saveDraftOnLeave(attemptId, latest.current)
    }
    window.addEventListener('pagehide', leave)
    return () => {
      window.removeEventListener('pagehide', leave)
      leave() // rời màn trong ứng dụng (đổi route)
    }
  }, [attemptId])

  // Sau khi nộp: bỏ nháp chưa lưu để không gửi vào lượt đã đóng.
  const discard = useCallback(() => {
    dirty.current = false
  }, [])

  return { status, flush, discard }
}
