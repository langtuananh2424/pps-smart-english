import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError } from '../../lib/api'
import { startAssignment } from './api'
import type { Assignment } from './types'

export const runPath = (a: Pick<Assignment, 'classId' | 'id'>, attemptId: number) => `/lop/${a.classId}/bai-giao/${a.id}/lam-bai/${attemptId}`

// Bắt đầu bài bộ câu hỏi; đang có lượt dở thì làm tiếp lượt đó (D-147 mục 12).
export function useStart(a: Assignment) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const start = async () => {
    setBusy(true)
    setError(null)
    try {
      navigate(runPath(a, (await startAssignment(a.classId, a.id)).id))
    } catch (e) {
      const resume = e instanceof ApiError && e.code === 'attempt_in_progress' ? (e.details as { attemptId?: number } | undefined)?.attemptId : null
      if (resume) navigate(runPath(a, resume))
      else {
        setError(e instanceof Error ? e.message : 'Không bắt đầu được, thử lại sau.')
        if (e instanceof ApiError && (e.code === 'assignment_submitted' || e.code === 'assignment_cancelled')) void qc.invalidateQueries({ queryKey: ['me', 'assignments'] })
      }
    } finally {
      setBusy(false)
    }
  }
  return { start, busy, error }
}
