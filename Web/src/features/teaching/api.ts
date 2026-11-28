import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, postJson } from '../../lib/api'

// Khớp ClassDtos, ProgressDtos, AssignmentDtos của backend (FR-CLS-05/06, FR-ASG-01..04).
export interface TeachClass {
  id: number
  courseId: number
  courseTitle: string
  name: string
  startsOn: string
  endsOn: string
  mode: 'ONLINE' | 'OFFLINE'
  capacity: number
  status: string
  activeMembers: number
}
export interface Member {
  userId: number
  displayName: string
  email: string
  status: string
}
export interface StaffMember {
  userId: number
  displayName: string
  role: string
}
export interface ClassDetail {
  info: TeachClass
  staff: StaffMember[]
  members: Member[] | null
}

export interface AssignmentCounts {
  submitted: number
  late: number
  overdue: number
  pending: number
}
export interface StudentProgress {
  userId: number
  displayName: string
  lessonPercent: number
  unitIndex: number
  totalUnits: number
  currentTopicTitle: string | null
  assignments: AssignmentCounts
  avgPercent: number | null
  chapterTests: { topicId: number; score: number | null; maxScore: number | null; percent: number | null }[]
  attendance: { present: number; late: number; absent: number; excused: number; ratePercent: number | null }
  lagging: boolean
  lagReasons: string[]
}
export interface ProgressBoard {
  classId: number
  students: StudentProgress[]
}

export interface Unlock {
  id: number
  topicId: number
  userId: number | null
  grantedBy: number
  grantedByName: string | null
  grantedAt: string
  revokedAt: string | null
}

// Cây nội dung giao được (GET /classes/{id}/assignable).
export interface AssignableBank {
  bankId: number
  title: string
  kind: AsgMode // chế độ của bộ
  questionCount: number
}
export interface AssignableAi {
  questionId: number
  type: 'WRITING' | 'SPEAKING'
  title: string
}
export interface AssignableTopic {
  topicId: number
  title: string
  position: number
  banks: AssignableBank[]
  aiQuestions: AssignableAi[]
}
export interface Assignable {
  courseId: number
  topics: AssignableTopic[]
}

export type AsgMode = 'PRACTICE' | 'EXAM'
export interface TeachAssignment {
  id: number
  classId: number
  bankId: number | null
  bankTitle: string | null
  questionId: number | null
  questionType: 'WRITING' | 'SPEAKING' | null
  dueAt: string
  status: string
  wholeClass: boolean
  mode: AsgMode | null
  recipients: number | null
  submitted: number | null
}
export interface AsgProgressRow {
  userId: number
  displayName: string
  status: 'NOT_SUBMITTED' | 'SUBMITTED' | 'LATE' | 'OVERDUE'
  attemptId: number | null
  submittedAt: string | null
  aiScore: number | null
  maxScore: number | null
  finalScore: number | null
  teacherComment: string | null
  forced?: unknown // Nghe ép (D-142): có forced thì không có điểm
}
export interface GradeEntry {
  id: number
  attemptId: number
  score: number | null
  comment: string | null
  gradedBy: number
  gradedAt: string
}
export interface CreateAssignmentBody {
  bankId?: number
  questionId?: number
  dueAt: string
  studentIds: number[]
  mode?: AsgMode
}

const KEY = 'teaching'
const asg = (classId: number, id: number) => `/classes/${classId}/assignments/${id}`

export const useTeachingClasses = () => useQuery({ queryKey: [KEY, 'classes'], queryFn: () => api<TeachClass[]>('/teaching/classes') })
export const useClassDetail = (classId: number) =>
  useQuery({ queryKey: [KEY, 'class', classId], queryFn: () => api<ClassDetail>(`/classes/${classId}`) })
export const useBoard = (classId: number) =>
  useQuery({ queryKey: [KEY, 'board', classId], queryFn: () => api<ProgressBoard>(`/classes/${classId}/progress`) })
export const useAssignable = (classId: number) =>
  useQuery({ queryKey: [KEY, 'assignable', classId], queryFn: () => api<Assignable>(`/classes/${classId}/assignable`) })
export const useUnlocks = (classId: number) =>
  useQuery({ queryKey: [KEY, 'unlocks', classId], queryFn: () => api<Unlock[]>(`/classes/${classId}/unlocks`) })
export const useClassAssignments = (classId: number) =>
  useQuery({ queryKey: [KEY, 'assignments', classId], queryFn: () => api<TeachAssignment[]>(`/classes/${classId}/assignments`) })
export const useAssignmentProgress = (classId: number, id: number) =>
  useQuery({ queryKey: [KEY, 'asg-progress', classId, id], queryFn: () => api<AsgProgressRow[]>(`${asg(classId, id)}/progress`) })
export const useGrades = (classId: number, id: number, attemptId: number) =>
  useQuery({
    queryKey: [KEY, 'grades', classId, id, attemptId],
    queryFn: () => api<GradeEntry[]>(`${asg(classId, id)}/attempts/${attemptId}/grades`),
  })

// Ghi xong thì làm mới mọi dữ liệu giảng dạy: bảng tiến độ, đếm nộp bài và lịch sử chấm cùng đổi.
function useTeachWrite<V, R>(call: (v: V) => Promise<R>) {
  const qc = useQueryClient()
  return useMutation({ mutationFn: call, onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }) })
}

export const useCreateUnlock = (classId: number) =>
  useTeachWrite((b: { topicId: number; studentIds: number[] }) => postJson<Unlock[]>(`/classes/${classId}/unlocks`, b))
export const useRevokeUnlock = (classId: number) =>
  useTeachWrite((id: number) => api<Unlock>(`/classes/${classId}/unlocks/${id}`, { method: 'DELETE' }))
export const useCreateAssignment = (classId: number) =>
  useTeachWrite((b: CreateAssignmentBody) => postJson<TeachAssignment>(`/classes/${classId}/assignments`, b))
export const useCancelAssignment = (classId: number, id: number) =>
  useTeachWrite(() => postJson<TeachAssignment>(`${asg(classId, id)}/cancel`, {}))
export const useSetMode = (classId: number, id: number) =>
  useTeachWrite((mode: AsgMode) => api<TeachAssignment>(asg(classId, id), { method: 'PATCH', body: JSON.stringify({ mode }) }))
export const useGrade = (classId: number, id: number, attemptId: number) =>
  useTeachWrite((b: { score?: number; comment?: string }) =>
    api<GradeEntry>(`${asg(classId, id)}/attempts/${attemptId}/grade`, { method: 'PUT', body: JSON.stringify(b) }),
  )
