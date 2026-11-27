// D-159: video từ nối. Khớp VideoQuizDtos của backend.
export interface QuizOutcome { questionId: number; correct: boolean; correctAnswer?: { choiceIds: number[] }; explanation?: string }
export interface VideoGroupState {
  groupId: number
  watchedSec: number
  durationSec?: number | null
  watched: boolean
  submits: number
  maxSubmits: number
  finalized: boolean
  passed: boolean
  answers?: { questionId: number; answer: { choiceIds: number[] } }[]
  results?: QuizOutcome[]
}
export interface VideoQuizState { attemptNo: number; passedAttempts: number; requiredAttempts: number; groups: VideoGroupState[] }
export interface WatchProgress { groupId: number; watchedSec: number; durationSec?: number | null; watched: boolean }
