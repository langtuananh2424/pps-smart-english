import { useEffect, useRef } from 'react'
import type { AiSession, Question } from '../types'

// Nút "Gửi chấm" nằm ở thanh dưới của Player nhưng bài làm nằm trong thẻ câu: thẻ báo lên Player qua đây.
export interface SendAction { enabled: boolean; run: () => void; label?: string }

export interface AnswerProps {
  question: Question
  attemptId: number
  session?: AiSession // phiên Viết / bản nộp Nói mới nhất của câu trong lượt, từ máy chủ
  loaded: boolean // chưa biết lượt đã có phiên nào thì chưa cho gửi
  disabled?: boolean
  onRef: (id: number, status: AiSession['status']) => void
  onSend: (a: SendAction | null) => void
  onSkip: () => void
  track?: (ctl: AbortController) => () => void // đăng ký tải lên đang chạy với Player (khóa Nộp bài, hủy khi rời Player); trả hàm gỡ
}

export const writingKey = (attemptId: number, questionId: number) => `ai:writing:${attemptId}:${questionId}`

// Đã gửi chấm: bản nộp đang chấm hoặc đã chấm. NOT_SUBMITTED (kể cả bản Nói AWAITING_UPLOAD) và FAILED là chưa gửi, tính 0 điểm.
export const isSent = (s?: AiSession) => s?.status === 'GRADING' || s?.status === 'GRADED'

// enabled = null: câu không còn gì để gửi (đã gửi chấm). Gỡ nút khi rời câu.
export function usePublishSend(onSend: (a: SendAction | null) => void, enabled: boolean | null, run: () => void, label?: string) {
  const latest = useRef(run)
  latest.current = run
  useEffect(() => onSend(enabled == null ? null : { enabled, run: () => latest.current(), label }), [enabled, onSend, label])
  useEffect(() => () => onSend(null), [onSend])
}
