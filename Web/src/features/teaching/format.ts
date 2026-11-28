import { ApiError } from '../../lib/api'
import type { AsgProgressRow, TeachAssignment } from './api'

export const KICKER = 'Giáo viên và trợ giảng'
export const base = '/giang-day'
export const classPath = (id: number, sub = '') => `${base}/lop/${id}${sub}`

export const LAG_LABEL: Record<string, string> = {
  OVERDUE_ASSIGNMENT: 'Quá hạn nộp',
  ABSENCES: 'Vắng nhiều',
  BEHIND_UNIT: 'Chậm hơn lớp',
  LOW_SCORE: 'Điểm thấp',
}
export const PROGRESS_LABEL: Record<AsgProgressRow['status'], string> = {
  SUBMITTED: 'Đã nộp',
  LATE: 'Nộp trễ',
  OVERDUE: 'Quá hạn',
  NOT_SUBMITTED: 'Chưa nộp',
}
export const CLASS_STATUS: Record<string, string> = { OPEN: 'Đang mở', CLOSED: 'Đã đóng' }
export const MODE_LABEL = { ONLINE: 'Trực tuyến', OFFLINE: 'Tại lớp' } as const
export const ASG_MODE_LABEL = { PRACTICE: 'Luyện tập', EXAM: 'Thi' } as const

// Bài giao câu Viết/Nói vẫn mang bankTitle của bộ chứa câu: gọi theo loại câu, tên bộ chỉ là phụ.
export const asgTitle = (a: Pick<TeachAssignment, 'bankTitle' | 'questionType'>) => {
  if (!a.questionType) return a.bankTitle ?? 'Bài đã giao'
  const kind = a.questionType === 'SPEAKING' ? 'Bài Nói' : 'Bài Viết'
  return a.bankTitle ? `${kind} · ${a.bankTitle}` : kind
}

// Trạng thái bài giao do web suy ra: backend chỉ có ACTIVE/CANCELLED, hết hạn tính theo dueAt.
export function asgState(a: Pick<TeachAssignment, 'status' | 'dueAt'>, now = Date.now()) {
  if (a.status === 'CANCELLED') return { label: 'Đã hủy', danger: true }
  return new Date(a.dueAt).getTime() < now ? { label: 'Đã hết hạn', danger: true } : { label: 'Đang mở', danger: false }
}

export const num = (v: number | null | undefined) => (v == null ? '—' : String(v))
export const score = (v: number | null, max: number | null) => (v == null ? '—' : max ? `${v}/${max}` : String(v))
export const fmtTime = (iso: string) =>
  new Date(iso).toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric' })

// Giá trị của ô datetime-local là giờ địa phương không kèm múi giờ; Date hiểu đúng theo máy người dùng.
export const localToIso = (v: string) => new Date(v).toISOString()

export const errCode = (e: unknown) => (e instanceof ApiError ? `${e.status}:${e.code}` : '')
