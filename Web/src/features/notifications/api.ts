import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../lib/api'

// payload.url là đường dẫn web do backend tính (D-92); thông báo cũ có thể thiếu.
export interface NotificationItem {
  id: number
  type: 'STREAK_REMINDER' | 'WRITING_GRADED'
  payload: { title?: string; body?: string; url?: string }
  createdAt: string
  readAt: string | null
}

// Một khóa chung cho chuông ở trang chủ và màn Thông báo.
const unreadKey = ['me', 'unread'] as const
const listKey = ['me', 'notifications'] as const

export const useUnreadCount = () =>
  useQuery({ queryKey: unreadKey, queryFn: () => api<{ count: number }>('/me/notifications/unread-count') })

export const useNotifications = () =>
  useInfiniteQuery({
    queryKey: listKey,
    queryFn: ({ pageParam }) =>
      api<{ data: NotificationItem[]; nextCursor: string | null }>(
        `/me/notifications?limit=20${pageParam ? `&cursor=${pageParam}` : ''}`,
      ),
    initialPageParam: '',
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  })

function useReadMutation<V>(fn: (v: V) => Promise<unknown>) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => Promise.all([qc.invalidateQueries({ queryKey: listKey }), qc.invalidateQueries({ queryKey: unreadKey })]),
  })
}

export const useMarkRead = () => useReadMutation((id: number) => api(`/me/notifications/${id}/read`, { method: 'POST' }))
export const useMarkAllRead = () => useReadMutation(() => api('/me/notifications/read-all', { method: 'POST' }))
