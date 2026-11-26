import { useQuery } from '@tanstack/react-query'
import { api, postJson } from '../../lib/api'
import type { Bank } from '../practice/types'
import type { ExtraOverview, StartExtra, Started } from './types'

export const useExtraOverview = (courseId: number) =>
  useQuery({ queryKey: ['me', 'extra-practice', courseId], queryFn: () => api<ExtraOverview>(`/me/courses/${courseId}/extra-practice`), retry: false })

export const startExtra = (courseId: number, body: StartExtra) =>
  postJson<Started>(`/me/courses/${courseId}/extra-practice/attempts`, body)

// Đề của lượt Luyện thêm dưới dạng "bộ" để Player và ResultView dùng lại.
export const fetchExtraBank = (attemptId: number) => api<Bank>(`/me/extra-practice/attempts/${attemptId}`)
