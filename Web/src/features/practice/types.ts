export type Id = number | string

export interface Choice { id: number; label: string; content: string }
export interface Hint { id: number; content: string; detail?: string | null }
export interface Question {
  id: number
  type: string
  instruction?: string
  prompt: string
  // eslint-disable-next-line @typescript-eslint/no-explicit-any -- payload khác nhau theo dạng, mỗi renderer tự đọc
  payload: any
  choices?: Choice[]
  hints?: Hint[]
}
export interface Bank {
  id: number
  title: string
  lessonId: number | null
  isTrial: boolean
  mode: 'PRACTICE' | 'EXAM'
  timeLimitSec: number | null
  questions: Question[]
}

// Đáp án một câu theo từng dạng (D-66); WRITING/SPEAKING không có.
export type Answer = Record<string, unknown>
export type Answers = Record<number, Answer | undefined>
export interface AnswerItem { questionId: number; answer: Answer }

export interface AttemptSummary { id: number; bankId: number; startedAt: string; submittedAt: string | null; score: number | null; maxScore: number | null }
export interface Started { id: number; bankId: number; startedAt: string; timeLimitSec: number | null }
export interface Progress { attemptId: number; timeLimitSec?: number; remainingSec?: number; paused: boolean; answers?: AnswerItem[] }

export interface QuestionResult {
  questionId: number
  isCorrect: boolean | null
  correctAnswer?: Answer | null
  explanation?: string | null
  feedback?: string | null
}
export interface AttemptResult {
  id: number
  bankId: number
  submittedAt?: string
  score?: number
  maxScore?: number
  results?: QuestionResult[]
  pendingJobs?: number[]
  aiQuotaRequired?: boolean
  products?: { id: number; name: string }[]
}

export interface QuestionProps {
  question: Question
  value: Answer | undefined
  onChange: (v: Answer | undefined) => void
  disabled?: boolean
}
