import type { Assignment, MyStatus } from './types'

// shortcut: "sắp đến hạn" là mốc cố định 24 giờ, chưa cấu hình được; nâng cấp khi có cài đặt hệ thống.
export const DUE_SOON_MS = 24 * 60 * 60 * 1000

export type Tone = 'danger' | 'plain'
export interface StatusView { label: string; tone: Tone }

export const TYPE_LABEL = { WRITING: 'Viết', SPEAKING: 'Nói' } as const
export const typeOf = (a: Assignment) => (a.questionType ? TYPE_LABEL[a.questionType] : 'Bài tập')
export const titleOf = (a: Assignment) => a.bankTitle ?? (a.questionType === 'SPEAKING' ? 'Bài nói được giao' : 'Bài viết được giao')

// D-153: bài giáo viên đã hủy, học viên chỉ xem lại (status của bài, khác myStatus).
export const isCancelled = (a: Assignment) => a.status === 'CANCELLED'
export const CANCELLED_NOTE = 'Bài này đã được giáo viên hủy'

export const isTodo = (s: MyStatus) => s === 'NOT_SUBMITTED' || s === 'OVERDUE'

export function statusOf(a: Assignment, now = Date.now()): StatusView {
  switch (a.myStatus) {
    case 'OVERDUE': return { label: 'Quá hạn', tone: 'danger' }
    case 'SUBMITTED': return { label: 'Đã nộp', tone: 'plain' }
    case 'LATE': return { label: 'Nộp trễ', tone: 'danger' }
    default: return { label: new Date(a.dueAt).getTime() - now < DUE_SOON_MS ? 'Sắp đến hạn' : 'Chưa làm', tone: 'plain' }
  }
}

export const formatDue = (iso: string) =>
  new Date(iso).toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric' })

// Đường vào làm bài: câu Viết/Nói sang màn riêng kèm assignmentId để không trừ lượt AI tuần (D-130).
export const aiPath = (a: Assignment) => `/${a.questionType === 'WRITING' ? 'viet' : 'noi'}/${a.questionId}?assignmentId=${a.id}`
export const detailPath = (a: Pick<Assignment, 'classId' | 'id'>) => `/lop/${a.classId}/bai-giao/${a.id}`
