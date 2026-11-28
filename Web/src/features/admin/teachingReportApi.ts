import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError, rawFetch, refreshSession } from '../../lib/api'

// #62, D-176: báo cáo số buổi dạy, quyền report.teaching.
export interface TeacherRow {
  userId: number
  displayName: string
  role: 'TEACHER' | 'ASSISTANT'
  groupSessions: number
  groupHours: number
  tutoringSessions: number
  tutoringHours: number
  totalSessions: number
  totalHours: number
}
export interface EditedSession {
  sessionId: number
  classId: number
  className: string
  teacherId: number
  teacherName: string
  startsAt: string
  edits: number
  lastEditedAt: string
}
export interface TeachingReport {
  from: string
  to: string
  lastExportAt: string | null
  teachers: TeacherRow[]
  editedSessions: EditedSession[]
}
export interface ReportClass {
  id: number
  name: string
  courseTitle: string
  status: string
}
export interface ReportFilters {
  from: string
  to: string
  role: string
  classId: string
}

const qs = (f: ReportFilters) => {
  const p = new URLSearchParams({ from: f.from, to: f.to })
  if (f.role) p.set('role', f.role)
  if (f.classId) p.set('classId', f.classId)
  return p.toString()
}

export const useTeachingReport = (f: ReportFilters, enabled: boolean) =>
  useQuery({ queryKey: ['admin', 'teaching-report', f], queryFn: () => api<TeachingReport>(`/admin/reports/teaching?${qs(f)}`), enabled, retry: false })

export const useReportClasses = () =>
  useQuery({ queryKey: ['admin', 'teaching-report-classes'], queryFn: () => api<ReportClass[]>('/admin/reports/teaching/classes') })

// Xuất CSV theo đúng bộ lọc đang xem; lỗi 4xx dựng lại ApiError từ JSON của backend.
export async function exportTeachingCsv(f: ReportFilters): Promise<{ blob: Blob; filename: string }> {
  const path = `/admin/reports/teaching/export?${qs(f)}`
  let res = await rawFetch(path, { method: 'POST' })
  if (res.status === 401) {
    await refreshSession()
    res = await rawFetch(path, { method: 'POST' })
  }
  if (!res.ok) {
    const e = (await res.json().catch(() => null))?.error
    throw new ApiError(res.status, e?.code ?? 'UNKNOWN', e?.message ?? 'Có lỗi xảy ra, vui lòng thử lại.')
  }
  return { blob: await res.blob(), filename: `buoi-day_${f.from}_${f.to}.csv` }
}

export const useRefreshReport = () => {
  const qc = useQueryClient()
  return () => qc.invalidateQueries({ queryKey: ['admin', 'teaching-report'] })
}
