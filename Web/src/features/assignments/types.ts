export type MyStatus = 'NOT_SUBMITTED' | 'SUBMITTED' | 'LATE' | 'OVERDUE'

// FR-ASG-02: bài giao cho học viên; questionType chỉ có khi giao một câu Viết/Nói (D-130).
export interface Assignment {
  id: number
  classId: number
  bankId: number | null
  bankTitle: string | null
  questionId: number | null
  questionType: 'WRITING' | 'SPEAKING' | null
  dueAt: string
  status: string
  wholeClass: boolean
  myStatus: MyStatus
}

// FR-ASG-03: điểm AI hiện ngay, điểm giáo viên sửa thay thế khi có; null khi còn câu AI chấm dở (D-147) hoặc Nghe ép (D-142).
export interface MyResult {
  assignmentId: number
  status: MyStatus
  attemptId: number | null
  submittedAt: string | null
  aiScore: number | null
  maxScore: number | null
  finalScore: number | null
  teacherComment: string | null
}
