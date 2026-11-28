import { useQuery } from '@tanstack/react-query'
import { api } from '../../lib/api'
import type { Answer, AttemptResult, Bank } from '../practice/types'

// GET /classes/{c}/assignments/{a}/attempts/{attemptId}/result (D-174): bài làm của học viên, chỉ đọc.
export interface ReviewAnswer { questionId: number; answer?: Answer | null; isCorrect?: boolean | null; credit?: number | null }
export interface AttemptReview { attempt: AttemptResult; bank: Bank; answers: ReviewAnswer[] }

export const useAttemptReview = (classId: number, id: number, attemptId: number, enabled: boolean) =>
  useQuery({
    queryKey: ['teaching', 'attempt-review', classId, id, attemptId],
    queryFn: () => api<AttemptReview>(`/classes/${classId}/assignments/${id}/attempts/${attemptId}/result`),
    enabled,
    retry: false,
  })
