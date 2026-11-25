import { useQuery } from '@tanstack/react-query'
import { api } from '../../lib/api'
import type { OrderStatus } from '../checkout/api'

export interface StudentRow {
  id: number
  email: string
  displayName: string | null
  status: string
  emailVerified: boolean
  courses: string[]
}
export interface StudentDetail {
  student: StudentRow
  createdAt: string | null
  enrollments: { courseId: number; courseTitle: string; status: string; startsAt: string | null; expiresAt: string | null }[]
  orders: { code: string; status: OrderStatus; totalVnd: number; paidAt: string | null; createdAt: string }[]
  quota: { pool: number; courses: { courseId: number; courseTitle: string; quota: number; used: number; remaining: number }[] }
}

export const MIN_QUERY = 3

// FR-SUP-03: quyền student.view. Backend bắt buộc q tối thiểu 3 ký tự nên dưới ngưỡng thì không gọi.
export const useStudentSearch = (q: string) =>
  useQuery({
    queryKey: ['admin', 'students', q],
    queryFn: () => api<StudentRow[]>(`/admin/support/students?q=${encodeURIComponent(q)}`),
    enabled: q.length >= MIN_QUERY,
    retry: false,
  })

export const useStudent = (id: string) =>
  useQuery({ queryKey: ['admin', 'student', id], queryFn: () => api<StudentDetail>(`/admin/support/students/${encodeURIComponent(id)}`), retry: false })
