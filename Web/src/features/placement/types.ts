import type { Answers, Question } from '../practice/types'

// Khớp PlacementDtos của backend (FR-TEST-01/02). Đề không kèm đáp án đúng.
export interface PlacementQuestion extends Question {
  skill: string
}
export interface Started {
  attemptId: number
  guestToken?: string
  title: string
  startedAt: string
  deadlineAt: string
  timeLimitSec: number
  remainingSec: number
  draftAnswers?: { questionId: number; answer: Record<string, unknown> }[]
  questions: PlacementQuestion[]
}
export interface SkillResult { correct: number; total: number; percent: number; band: number }
export interface CourseRef { courseId: number; slug: string; title: string }
export interface PlacementResult {
  attemptId: number
  submittedAt: string
  skills: Record<string, SkillResult>
  overallBand: number
  recommendedCourse: CourseRef | null
}
export interface Submitted { submitted: boolean; requiresLogin: boolean; result?: PlacementResult }

// Bản lưu trong localStorage (D-89). Khách giữ guestToken để tiếp tục lượt khi tải lại trang.
// Đã nộp thì chỉ còn attemptId + guestToken (chờ claim sau khi đăng nhập).
export interface Saved {
  attemptId: number
  guestToken?: string
  submitted: boolean
  started?: Started
  answers: Answers
  // Mốc hết giờ theo đồng hồ máy, suy từ remainingSec của server để không phụ thuộc lệch giờ.
  deadlineMs: number
}
