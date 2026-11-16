import { useQuery } from '@tanstack/react-query'
import { api } from '../../lib/api'
import type { CamMessage, MeStats, MyCourse, Quota, Roadmap, Today } from './types'

// Mỗi khối một query riêng để lỗi một API không kéo theo cả trang.
export const useMeStats = () => useQuery({ queryKey: ['me', 'stats'], queryFn: () => api<MeStats>('/me/stats') })

export const useUnreadCount = () =>
  useQuery({ queryKey: ['me', 'unread'], queryFn: () => api<{ count: number }>('/me/notifications/unread-count') })

export const useMyCourses = () => useQuery({ queryKey: ['me', 'courses'], queryFn: () => api<MyCourse[]>('/me/courses') })

export const useRoadmap = (courseId?: number) =>
  useQuery({
    queryKey: ['me', 'roadmap', courseId],
    queryFn: () => api<Roadmap>(`/me/courses/${courseId}/roadmap`),
    enabled: courseId != null,
  })

export const useToday = (courseId?: number) =>
  useQuery({
    queryKey: ['me', 'today', courseId],
    queryFn: () => api<Today>(`/me/today?courseId=${courseId}`),
    enabled: courseId != null,
  })

export const useQuota = () => useQuery({ queryKey: ['me', 'quota'], queryFn: () => api<Quota>('/me/quota') })

export const useCamMessages = () =>
  useQuery({ queryKey: ['me', 'cam'], queryFn: () => api<{ messages: CamMessage[] }>('/me/cam-messages') })
