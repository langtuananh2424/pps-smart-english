import type { AsgProgressRow, ClassDetail, StudentProgress, TeachAssignment, TeachClass } from './api'

export const cls: TeachClass = { id: 3, courseId: 9, courseTitle: 'IELTS Foundation', name: 'Lớp A1', startsOn: '2026-12-01', endsOn: '2027-03-01', mode: 'ONLINE', capacity: 30, status: 'OPEN', activeMembers: 12 }

export const detail: ClassDetail = {
  info: cls,
  staff: [{ userId: 7, displayName: 'Cô Hoa', role: 'TEACHER' }],
  members: [
    { userId: 21, displayName: 'Minh', email: 'm@x.vn', status: 'ACTIVE' },
    { userId: 22, displayName: 'Nga', email: 'n@x.vn', status: 'ACTIVE' },
    { userId: 23, displayName: 'Bình đã rời', email: 'b@x.vn', status: 'LEFT' },
  ],
}

const counts = { submitted: 1, late: 0, overdue: 0, pending: 0 }
const att = { present: 3, late: 0, absent: 0, excused: 0, ratePercent: 100 }
export const student = (o: Partial<StudentProgress>): StudentProgress => ({
  userId: 21, displayName: 'Minh', lessonPercent: 80, unitIndex: 2, totalUnits: 5, currentTopicTitle: 'Unit 2', assignments: counts,
  avgPercent: 75, chapterTests: [], attendance: att, lagging: false, lagReasons: [], ...o,
})
export const board = { classId: 3, students: [
  student({}),
  student({ userId: 22, displayName: 'Nga', lagging: true, lagReasons: ['OVERDUE_ASSIGNMENT', 'LOW_SCORE'], avgPercent: null, assignments: { ...counts, overdue: 2 } }),
] }

export const inH = (h: number) => new Date(Date.now() + h * 3600_000).toISOString()
export const asg = (o: Partial<TeachAssignment> = {}): TeachAssignment => ({
  id: 5, classId: 3, bankId: 41, bankTitle: 'Bộ Unit 1', questionId: null, questionType: null, dueAt: inH(48), status: 'ACTIVE', wholeClass: true,
  mode: 'PRACTICE', recipients: 12, submitted: 4, ...o,
})
export const row = (o: Partial<AsgProgressRow> = {}): AsgProgressRow => ({
  userId: 21, displayName: 'Minh', status: 'SUBMITTED', attemptId: 91, submittedAt: inH(-1), aiScore: 70, maxScore: 100, finalScore: 70, teacherComment: null, ...o,
})

export const tree = { courseId: 9, topics: [
  { topicId: 1, title: 'Unit 1', position: 1, banks: [{ bankId: 41, title: 'Bộ Unit 1', kind: 'PRACTICE', questionCount: 10 }], aiQuestions: [{ questionId: 77, type: 'WRITING', title: 'Viết thư' }] },
  { topicId: 2, title: 'Unit 2', position: 2, banks: [], aiQuestions: [] },
] }
