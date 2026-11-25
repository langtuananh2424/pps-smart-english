import { useQuery } from '@tanstack/react-query'
import { api, postJson } from '../../lib/api'
import type { CourseDetail } from '../courses/types'

export interface LessonVideo {
  source: 'STORAGE' | 'YOUTUBE'
  url?: string
  ref?: string
  expiresAt?: string
}

export interface LessonDetail {
  id: number
  title: string
  type: string
  durationSec: number | null
  video: LessonVideo | null // null: bài chưa tải video lên
  courseId: number
  courseSlug: string
  courseTitle: string
  topicId: number
  topicTitle: string
  levelId: number
}

export interface LessonProgress {
  lessonId: number
  watchedSec: number
  maxPositionSec: number
  completed: boolean
  completedAt: string | null
}

export interface ProgressBody {
  positionSec: number
  watchedDeltaSec: number
}

// URL ký hết hạn sau 120 phút: không tự refetch (đổi src sẽ làm video tải lại), chỉ refetch khi media lỗi.
export const useLesson = (id: number) =>
  useQuery({
    queryKey: ['lesson', id],
    queryFn: () => api<LessonDetail>(`/lessons/${id}`),
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  })

export const lessonProgressKey = (id: number) => ['lesson', id, 'progress']

export const useLessonProgress = (id: number, enabled: boolean) =>
  useQuery({ queryKey: lessonProgressKey(id), queryFn: () => api<LessonProgress>(`/lessons/${id}/progress`), enabled })

export const postLessonProgress = (id: number, body: ProgressBody) => postJson<unknown>(`/lessons/${id}/progress`, body)

// Cùng khóa cache với useTrial để không gọi lại.
export const useCatalogDetail = (slug: string | undefined, enabled: boolean) =>
  useQuery({
    queryKey: ['catalog', 'course', slug],
    queryFn: () => api<CourseDetail>(`/catalog/courses/${encodeURIComponent(slug ?? "")}`),
    enabled: enabled && !!slug,
  })

// Điều kiện mở unit (FR-LMS-04); hàng nào null thì unit không có điều kiện đó.
export interface UnitGate {
  topicId: number
  locked: boolean
  openedEarly: boolean
  blockingTopicId: number | null
  blockingTitle: string | null
  lectures: { completed: number; total: number; requiredPercent: number; currentPercent: number } | null
  exercises: { completed: number; total: number } | null
  chapterTest: { submitted: boolean } | null
}

export const useUnitGate = (courseId: number, topicId: number) =>
  useQuery({
    queryKey: ['me', 'unit-gate', courseId, topicId],
    queryFn: () => api<UnitGate>(`/me/courses/${courseId}/units/${topicId}/gate`),
    retry: false,
  })
