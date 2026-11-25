import { useQuery } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'
import type { AttemptDetail, CourseTest, ExamStatus, ExerciseStats } from './types'

export const useExerciseStats = (courseId: number) =>
  useQuery({
    queryKey: ['me', 'exercise-stats', courseId],
    queryFn: () => api<ExerciseStats>(`/me/courses/${courseId}/exercise-stats`),
  })

// 403/404 (khóa thử, khóa không có bài thi): trả null để ẩn thẻ thay vì báo lỗi.
export const useExamStatus = (courseId: number) =>
  useQuery({
    queryKey: ['course', courseId, 'exam'],
    queryFn: async () => {
      try {
        return await api<ExamStatus>(`/courses/${courseId}/exam`)
      } catch (e) {
        if (e instanceof ApiError && (e.status === 403 || e.status === 404)) return null
        throw e
      }
    },
    retry: false,
  })

export const useCourseTests = (courseId: number) =>
  useQuery({ queryKey: ['course', courseId, 'tests'], queryFn: () => api<CourseTest[]>(`/courses/${courseId}/tests`) })

export const useAttemptDetail = (id: number) =>
  useQuery({ queryKey: ['attempt', id, 'detail'], queryFn: () => api<AttemptDetail>(`/attempts/${id}`) })
