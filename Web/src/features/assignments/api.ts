import { useQuery } from '@tanstack/react-query'
import { api, postJson } from '../../lib/api'
import type { Started } from '../practice/types'
import type { Assignment, MyResult } from './types'

export const useMyAssignments = () =>
  useQuery({ queryKey: ['me', 'assignments'], queryFn: () => api<Assignment[]>('/me/assignments'), retry: false })

// Một bài của tôi (404 nếu không phải người nhận); không phụ thuộc giới hạn của danh sách.
export const useMyAssignment = (classId: number, id: number) => {
  const valid = Number.isInteger(classId) && classId > 0 && Number.isInteger(id) && id > 0
  return useQuery({ queryKey: ['me', 'assignments', 'one', classId, id], queryFn: () => api<Assignment>(`/me/assignments/${id}`), enabled: valid, retry: false })
}

// Bài giao đã bị hủy chưa (D-153): màn Viết/Nói ẩn nút làm lại. Không có id hoặc chưa tải được thì coi như chưa hủy.
export const useAssignmentCancelled = (id?: number) =>
  useQuery({ queryKey: ['me', 'assignments', 'one', 'status', id], queryFn: () => api<Assignment>(`/me/assignments/${id}`), enabled: id != null, retry: false }).data?.status === 'CANCELLED'

export const useMyResult = (classId: number, id: number, enabled: boolean) =>
  useQuery({ queryKey: ['me', 'assignments', classId, id, 'mine'], queryFn: () => api<MyResult>(`/classes/${classId}/assignments/${id}/mine`), enabled, retry: false, staleTime: 0 })

// 409 attempt_in_progress kèm attemptId: người gọi làm tiếp lượt đó (D-147 mục 12).
export const startAssignment = (classId: number, id: number) =>
  postJson<Started>(`/classes/${classId}/assignments/${id}/start`, {})
