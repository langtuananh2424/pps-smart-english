import { useQuery } from '@tanstack/react-query'
import { api } from '../../lib/api'
import type { SubmissionView } from '../speaking/types'
import type { SessionView } from '../writing/types'

// GET /classes/{c}/assignments/{a}/submissions/{attemptId} (D-141); đúng một trong writing hoặc speaking.
export interface Submission {
  assignmentId: number
  userId: number
  questionType: 'WRITING' | 'SPEAKING'
  submittedAt: string
  writing?: SessionView
  speaking?: SubmissionView
  audioDeleted?: boolean
}

export const useSubmission = (classId: number, id: number, attemptId: number, enabled: boolean) =>
  useQuery({
    queryKey: ['teaching', 'submission', classId, id, attemptId],
    queryFn: () => api<Submission>(`/classes/${classId}/assignments/${id}/submissions/${attemptId}`),
    enabled,
    retry: false,
  })
