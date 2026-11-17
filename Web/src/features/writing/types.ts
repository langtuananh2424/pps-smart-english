// Khớp WritingDtos / JobView của backend (FR-AI-01, FR-AI-02).
export interface RootQuestion { id: number; prompt: string; payload?: { requiredWords?: number; task?: string } | null }
export interface Turn { role: 'CAM' | 'STUDENT'; text: string }
export type SessionStatus = 'ACTIVE' | 'READY' | 'SUBMITTED'

export interface Connectives { total: number; types: number; correct: number; misused: string[]; note: string }
export type ElementStatus = 'USED_WELL' | 'USED_WRONG' | 'SUGGESTED'
export interface GrammarUse { code: string; status: ElementStatus; quote?: string | null; upgrade?: string | null }
export interface WritingGrade {
  taskResponse: number
  coherence: number
  lexical: number
  grammar: number
  scorePercent: number
  correctedAnswer: string
  markedAnswer?: string
  connectives?: Connectives
  grammarElements?: GrammarUse[]
  gateNote?: string | null
  feedback?: string
  wordCount?: number
  copiedWords?: number
}

export interface SessionView {
  sessionId: number
  status: SessionStatus
  question: RootQuestion
  turns: Turn[]
  pendingJobId?: number
  draft?: string
  finalText?: string
  gradeJobId?: number
  grade?: WritingGrade
  createdAt: string
}
export interface SessionSummary { sessionId: number; questionId: number; status: SessionStatus; scorePercent?: number; createdAt: string }
export interface Created { sessionId: number; question: RootQuestion }
export interface AnswerAccepted { jobId: number | null; sessionStatus: SessionStatus }
export interface SubmitAccepted { jobId: number; status: string; quotaRemaining: number }

export interface JobView<R = unknown> { id: number; kind: string; status: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'; result?: R; error?: string; quotaRefunded: boolean }
export interface Followup { question: string; done: boolean }
