// Khớp SpeakingDtos / SpeakingResult của backend (FR-AI-03).
export interface SpeakingQuestion { id: number; part: 1 | 2 | 3; prompt: string; cueCard: string[]; maxSeconds: number }

export interface UploadPermit {
  submissionId: number
  url: string
  fields: Record<string, string>
  maxBytes: number
  contentType: string
  maxSeconds: number
  expiresAt: string
}
export interface Completed { submissionId: number; status: string; jobId: number | null; quotaRemaining: number | null }

export type SubmissionStatus = 'AWAITING_UPLOAD' | 'GRADING' | 'GRADED' | 'UNCLEAR' | 'FAILED' | 'EXPIRED'
export type UnclearReason = 'too_short' | 'too_few_words' | 'low_confidence' | 'too_slow'

export interface Fluency { wordsPerMinute: number; longPauses: number; pausesPerMinute: number; speechRatio: number; band: number }
export interface WrongWord { word: string; score: number; issues: string[]; expectedIpa?: string | null; heardIpa?: string | null }
export interface Suggestion { original: string; improved: string; reason: string }
export interface SpeakingResult {
  status: 'GRADED' | 'UNCLEAR'
  reason?: UnclearReason
  transcript?: string
  bands?: { pronunciation: number; fluency: number; lexical: number; grammar: number }
  overall?: number
  fluency?: Fluency
  pronunciationScore?: number
  mispronounced?: WrongWord[]
  feedback?: string
  suggestions?: Suggestion[]
}

export interface SubmissionView {
  id: number
  questionId: number
  status: SubmissionStatus
  result?: SpeakingResult | null
  audioUrl?: string | null
  durationSec?: number | null
  createdAt: string
}
