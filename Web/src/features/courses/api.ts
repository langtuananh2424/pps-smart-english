import { useQuery } from '@tanstack/react-query'
import { api, postJson } from '../../lib/api'
import type { QaAnswers } from '../qa/schema'
import type { CourseSummary, Progress, Suggestions } from './types'

export const useCatalog = () => useQuery({ queryKey: ['catalog', 'courses'], queryFn: () => api<CourseSummary[]>('/catalog/courses') })

export const useMySuggestions = (enabled: boolean) =>
  useQuery({ queryKey: ['me', 'suggestions'], queryFn: () => api<Suggestions>('/me/course-suggestions'), enabled })

// Khách: gợi ý theo câu trả lời trong trình duyệt, không lưu server.
export const useGuestSuggestions = (answers: QaAnswers | null) =>
  useQuery({
    queryKey: ['catalog', 'suggestions', answers],
    queryFn: () => postJson<Suggestions>('/catalog/course-suggestions', answers),
    enabled: answers != null,
  })

export const useProgress = (courseId: number) =>
  useQuery({ queryKey: ['me', 'progress', courseId], queryFn: () => api<Progress>(`/me/courses/${courseId}/progress`) })
