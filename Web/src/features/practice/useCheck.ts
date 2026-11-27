import { useState } from 'react'
import { ApiError } from '../../lib/api'
import { isAiType } from '../exercises/format'
import { checkQuestion, fetchProgress } from './api'
import { isAnswerable } from './questions/registry'
import type { Answer, CheckState, Progress } from './types'

export const CLOSED_CODES = ['attempt_time_up', 'attempt_already_submitted']
const REWRITE = ['CONNECTIVE_REWRITE', 'GRAMMAR_REWRITE'] // chấm bằng AI khi nộp: máy chủ không kiểm tra từng câu

// D-150: dạng tự chấm mới có nút Kiểm tra.
export const isCheckable = (type: string) => isAnswerable(type) && !isAiType(type) && !REWRITE.includes(type)
export const isLocked = (s?: CheckState) => !!s && (s.correct || s.checksLeft <= 0)

// Trạng thái kiểm tra từng câu (FR-EX-04). enabled = lượt áp dụng thử lại (máy chủ trả progress.retry).
export function useCheck(attemptId: number, retry: Progress['retry'], onClosed: () => void) {
  const [checks, setChecks] = useState<Record<number, CheckState>>(() => Object.fromEntries((retry?.checks ?? []).map((c) => [c.questionId, c])))
  const [pending, setPending] = useState(false)
  // D-163: từ lần sai thứ hintAfterWrong (còn lượt) mỗi lần sai tăng n để Cam tự mở lại, kể cả khi sai liên tiếp ở cùng một câu.
  const [wrong, setWrong] = useState<{ questionId: number; n: number } | null>(null)

  // Trả thông báo lỗi (nếu có) để màn làm bài hiện.
  const check = async (questionId: number, answer: Answer): Promise<string | null> => {
    setPending(true)
    try {
      const r = await checkQuestion(attemptId, questionId, answer)
      setChecks((c) => ({ ...c, [questionId]: r }))
      if (!r.correct && r.checksLeft > 0 && r.wrongCount >= (retry?.hintAfterWrong ?? 2)) setWrong((w) => ({ questionId, n: (w?.n ?? 0) + 1 }))
      return null
    } catch (e) {
      const code = e instanceof ApiError ? e.code : undefined
      if (code === 'retry_exhausted') {
        // Lấy lại trạng thái từ máy chủ để câu khóa sai có đáp án và giải thích; lỗi thì chỉ khóa.
        const fresh = await fetchProgress(attemptId).then((p) => p.retry?.checks.find((c) => c.questionId === questionId)).catch(() => undefined)
        setChecks((c) => ({ ...c, [questionId]: fresh ?? { questionId, correct: false, checksLeft: 0, wrongCount: c[questionId]?.wrongCount ?? 0 } }))
      }
      if (code && CLOSED_CODES.includes(code)) onClosed()
      return e instanceof ApiError ? e.message : 'Không kiểm tra được, kiểm tra kết nối rồi thử lại.'
    } finally {
      setPending(false)
    }
  }

  return { enabled: retry != null, checks, pending, wrong, check }
}
