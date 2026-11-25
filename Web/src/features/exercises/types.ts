// Kiểu dữ liệu khớp JSON backend: thống kê bài tập (D-62, D-63) và bài thi khóa (FR-EXAM-02).
export type BandSource = 'EXAM' | 'PLACEMENT'

export interface TypeStat {
  type: string
  questionCount: number
  answered: number
  correct: number
  accuracy: number | null
}

export interface ExerciseStats {
  answered: number
  correct: number
  accuracy: number | null
  typesPracticed: number
  byType: TypeStat[]
  weakest: { type: string; accuracy: number; answered: number } | null
  band: { value: number; source: BandSource; at: string } | null
}

// Band quy đổi của lượt thi khóa; null khi đề chưa có bảng quy đổi.
export interface ExamBands {
  skills: Record<string, { correct: number; total: number; band: number }>
  overall: number
  gradingPending: boolean
}
export interface LastResult {
  attemptId: number
  score: number | null
  maxScore: number | null
  submittedAt: string
  bands?: ExamBands | null
}

// GET /api/attempts/{id} của lượt đã nộp (FR-EX-08); lượt Nghe ép không có score/results/breakdown.
export interface Metric { key: string; label: string; answered: number; correct: number; accuracy: number | null; ungraded: number }
export interface AttemptDetail {
  id: number
  bankId: number
  score?: number
  maxScore?: number
  results?: { questionId: number; isCorrect: boolean | null; explanation?: string | null }[]
  breakdown?: { correct: number; wrong: number; ungraded: number; bySkill: Metric[] }
  forced?: { correctCount: number; totalCount: number }
}

export interface ExamStatus {
  bankId: number | null
  progressPercent: number
  requiredPercent: number
  unlocked: boolean
  reason: 'ENROLLMENT_REQUIRED' | 'PROGRESS' | 'ATTEMPTS_EXHAUSTED' | 'NOT_CONFIGURED' | null
  attemptsUsed: number
  attemptsAllowed: number
  inProgressAttemptId: number | null
  lastResult: LastResult | null
}

// D-118: một bài kiểm tra của khóa (chương hoặc cuối khóa).
export type TestReason = 'ENROLLMENT_REQUIRED' | 'UNIT_LOCKED' | 'PROGRESS' | 'ATTEMPTS_EXHAUSTED' | 'NOT_CONFIGURED'
export interface CourseTest {
  kind: 'CHAPTER' | 'FINAL'
  topicId: number | null
  title: string
  bankId: number | null
  unlocked: boolean
  reason: TestReason | null
  attemptsUsed: number
  attemptsAllowed: number // tổng lượt = 1 + số lần làm lại (backend ExamService)
  inProgressAttemptId: number | null
  lastResult: LastResult | null
}
