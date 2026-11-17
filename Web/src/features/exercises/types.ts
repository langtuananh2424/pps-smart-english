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

export interface ExamStatus {
  bankId: number | null
  progressPercent: number
  requiredPercent: number
  unlocked: boolean
  reason: 'ENROLLMENT_REQUIRED' | 'PROGRESS' | 'ATTEMPTS_EXHAUSTED' | 'NOT_CONFIGURED' | null
  attemptsUsed: number
  attemptsAllowed: number
  inProgressAttemptId: number | null
  lastResult: { score: number; maxScore: number | null; submittedAt: string } | null
}
