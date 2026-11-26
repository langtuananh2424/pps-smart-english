import { useQueries, useQuery } from '@tanstack/react-query'
import { fetchSubmission } from '../speaking/api'
import type { SubmissionView } from '../speaking/types'
import { fetchSession } from '../writing/api'
import type { SessionView } from '../writing/types'
import { fetchAiSessions } from './api'
import type { AiSession } from './types'

export interface AiOutcome { session: AiSession; writing?: SessionView; speaking?: SubmissionView }

// Phiên Viết/Nói của lượt (D-145) kèm điểm từng câu: GET phiên chỉ mở sau khi cả bộ chấm xong (D-147), nên chỉ gọi khi enabled.
export function useAiOutcomes(attemptId: number, enabled: boolean) {
  const sessions = useQuery({ queryKey: ['attempt', attemptId, 'ai-sessions'], queryFn: () => fetchAiSessions(attemptId), retry: false, staleTime: 0 })
  const graded = (sessions.data ?? []).filter((s) => s.status === 'GRADED')
  const details = useQueries({
    queries: graded.map((s) =>
      s.kind === 'WRITING'
        ? { queryKey: ['writing', 'session', s.id], queryFn: () => fetchSession(s.id), enabled, retry: false }
        : { queryKey: ['speaking', 'submission', s.id], queryFn: () => fetchSubmission(s.id), enabled, retry: false },
    ),
  })
  const outcomes = new Map<number, AiOutcome>((sessions.data ?? []).map((s) => [s.questionId, { session: s }]))
  graded.forEach((s, i) => {
    const o = outcomes.get(s.questionId)!
    if (s.kind === 'WRITING') o.writing = details[i].data as SessionView | undefined
    else o.speaking = details[i].data as SubmissionView | undefined
  })
  return { sessions: sessions.data, outcomes, ready: sessions.isSuccess }
}

// Câu có nhận xét để mở: Viết có bản chấm, Nói có kết quả chấm.
export const hasReview = (o?: AiOutcome) => !!(o?.writing?.grade || o?.speaking?.result?.bands)
