import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { useMyQa } from '../qa/api'

// GET /api/me/goals; với IELTS, targetValue là band dạng "7.0".
export interface Goal {
  id: number
  courseId: number | null
  targetType: string
  targetValue: string
  targetDate: string | null
}

export const useMyGoals = () => useQuery({ queryKey: ['me', 'goals'], queryFn: () => api<Goal[]>('/me/goals') })

export const BANDS = Array.from({ length: 9 }, (_, i) => (4 + i / 2).toFixed(1)) // 4.0 … 8.0 (D-55)

export const formatDate = (d: string) => d.split('-').reverse().join('/')

// D-80: band lấy từ mục tiêu IELTS; chưa có thì từ câu trả lời Q&A. Hồ sơ và màn mục tiêu cùng dùng hook này.
export function useGoalTarget() {
  const goals = useMyGoals()
  const qa = useMyQa()
  const ielts = goals.data?.find((g) => g.targetType === 'IELTS')
  const n = Number(ielts?.targetValue)
  const band = ielts && Number.isFinite(n) ? n : qa.data?.targetBand
  return {
    isPending: goals.isPending || (band == null && qa.isPending),
    isError: goals.isError || (band == null && qa.isError),
    band,
    date: ielts?.targetDate ?? null,
  }
}

export interface IeltsGoalBody {
  targetBand: number
  targetDate?: string
}

export function useSaveIeltsGoal() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (b: IeltsGoalBody) => api<Goal>('/me/goals/ielts', { method: 'PUT', body: JSON.stringify(b) }),
    // Backend đồng bộ luôn targetBand trong Q&A nên làm mới cả gợi ý khóa.
    onSuccess: () =>
      Promise.all(['goals', 'qa', 'suggestions'].map((k) => qc.invalidateQueries({ queryKey: ['me', k] }))),
  })
}
