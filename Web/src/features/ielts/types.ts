import type { AttemptResult, BankGroup, Hint, Question } from '../practice/types'

export type IeltsMode = 'PRACTICE' | 'EXAM'

// FR-EXAM-03: band quy đổi của lượt đã nộp; vắng khi bộ chưa có bảng quy đổi hoặc còn câu AI chưa chốt.
export interface AttemptBands {
  skills: Record<string, { correct: number; total: number; percent: number; band: number | null }>
  overall: number | null
  gradingPending: boolean
}
export type IeltsResult = AttemptResult & { bands?: AttemptBands | null }

// GET /attempts/{id}/hints: chỉ lượt Luyện tập (D-151).
export interface QuestionHints { questionId: number; hints: Hint[] }

// Một phần thi (Passage / Part): nhóm câu cùng đoạn đọc hoặc audio.
export interface Part {
  key: string
  group?: BankGroup
  questions: Question[]
  first: number // chỉ số (từ 0) của câu đầu phần trong toàn bài
}
