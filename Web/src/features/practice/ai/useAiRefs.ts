import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo } from 'react'
import { fetchAiSessions } from '../api'
import type { AiSession } from '../types'

const POLL_MS = 5000

// Phiên Viết / bản nộp Nói mới nhất của từng câu trong lượt, lấy từ máy chủ (D-145): tải lại trang
// hay đổi máy không làm mất bài đã gửi. Còn câu đang chấm thì hỏi lại định kỳ để hộp nộp bài đếm đúng.
export function useAiRefs(attemptId: number) {
  const qc = useQueryClient()
  const key = ['attempt', attemptId, 'ai-sessions']
  const q = useQuery({
    queryKey: key,
    queryFn: () => fetchAiSessions(attemptId),
    retry: false,
    staleTime: 0,
    refetchInterval: (query) => (query.state.data?.some((s) => s.status === 'GRADING') ? POLL_MS : false),
  })
  const refs = useMemo(() => Object.fromEntries((q.data ?? []).map((s) => [s.questionId, s])), [q.data])
  // Phiên vừa gửi: ghi tạm vào cache, lần hỏi lại sau đó cho trạng thái thật.
  const set = (questionId: number, id: number, kind: AiSession['kind'], status: AiSession['status']) =>
    qc.setQueryData<AiSession[]>(key, (old) => [...(old ?? []).filter((s) => s.questionId !== questionId), { questionId, kind, id, status }])
  return { refs, ready: q.isSuccess, failed: q.isError, set, refresh: () => void q.refetch() }
}
