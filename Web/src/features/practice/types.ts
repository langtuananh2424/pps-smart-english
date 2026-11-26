export type Id = number | string

export interface Choice { id: number; label: string; content: string; contentHtml?: string | null }
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
  groupId?: number | null
  promptHtml?: string | null
}
// D-133: bài đọc/audio dùng chung; passageHtml đã được máy chủ làm sạch.
export interface BankGroup { id: number; title: string | null; instruction: string | null; passageHtml: string | null; sortOrder: number; hasAudio: boolean; audioDurationMs: number | null }
// D-135: FREE tự do · EXAM tự phát, ít lượt · LOCKED (Nghe ép) không tua, nghe lại khi audio đã hết.
export type PlaybackMode = 'FREE' | 'EXAM' | 'LOCKED'
export interface Bank {
  id: number
  title: string
  lessonId: number | null
  isTrial: boolean
  mode: 'PRACTICE' | 'EXAM'
  timeLimitSec: number | null
  questions: Question[]
  groups?: BankGroup[]
  playbackMode?: PlaybackMode
  playLimit?: number | null // mặc định của bộ, KHÔNG phải số lượt còn lại
}

// Đáp án một câu theo từng dạng (D-66); WRITING/SPEAKING không có.
export type Answer = Record<string, unknown>
export type Answers = Record<number, Answer | undefined>
export interface AnswerItem { questionId: number; answer: Answer }

// D-145: phiên Viết/Nói mới nhất của một câu trong lượt; id là sessionId (Viết) hoặc submissionId (Nói). Không có điểm.
export interface AiSession { questionId: number; kind: 'WRITING' | 'SPEAKING'; id: number; status: 'NOT_SUBMITTED' | 'GRADING' | 'GRADED' | 'FAILED' }
export interface AttemptSummary { id: number; bankId: number; startedAt: string; submittedAt: string | null; score: number | null; maxScore: number | null }
export interface Started { id: number; bankId: number; startedAt: string; timeLimitSec: number | null }
// D-150: kiểm tra từng câu. checksLeft = số lần kiểm tra còn lại; câu khóa khi đúng hoặc checksLeft = 0. Không có đáp án.
export interface CheckState { questionId: number; correct: boolean; checksLeft: number }
export interface Progress { attemptId: number; timeLimitSec?: number; remainingSec?: number; paused: boolean; answers?: AnswerItem[]; retry?: { maxRetries: number; checks: CheckState[] } }

export interface QuestionResult {
  questionId: number
  isCorrect: boolean | null
  correctAnswer?: Answer | null
  explanation?: string | null
  feedback?: string | null
  aiRefunded?: boolean // có aiFailure: lượt AI của câu đã được hoàn (bài giao không trừ lượt)
  aiFailure?: string | null // D-147: lý do câu Viết/Nói không chấm được (đã hoàn lượt AI, tính 0 điểm)
}
export interface Metric { key: string; label: string; answered: number; correct: number; credit: number; accuracy: number | null; ungraded: number; aiGraded: number; aiAverageScore: number | null }
export interface Breakdown { correct: number; wrong: number; ungraded: number; bySkill: Metric[]; byType: Metric[]; byGrammar: Metric[]; wrongQuestions: { questionId: number; wrongCount: number }[] }
// D-142: kết quả Nghe ép chỉ có số câu đúng và đạt/chưa đạt. hardQuestions chỉ có ở lượt chia hết cho hintEveryN.
export interface ForcedResult {
  correctCount: number; totalCount: number; percent: number; passPct?: number | null; passed: boolean
  parts: { groupId: number; title: string | null; correct: number; total: number; passPct?: number | null; passed?: boolean | null }[]
  attemptNo: number; hintEveryN: number
  hardQuestions?: { questionId: number; prompt: string; wrongCount: number }[]
}
export interface PlayGrant { playbackMode: PlaybackMode; playNo?: number; playLimit?: number; remaining?: number; url: string; expiresAt: string }
export interface PlayState { playNo: number; completed: boolean; playLimit?: number | null; remaining?: number | null }
export interface ScriptView { questionId: number; text?: string | null; html?: string | null }

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
  passPct?: number // D-145: ngưỡng đạt của bộ (%), mặc định 70
  passed?: boolean // vắng khi còn câu AI chấm dở, bài kiểm tra đầu vào hoặc Nghe ép
  // D-147: còn câu AI chưa chốt thì máy chủ không trả score, results, breakdown, passed (chỉ maxScore, pendingJobs)
  breakdown?: Breakdown // chỉ có ở GET lượt đã nộp, không phải Nghe ép
  forced?: ForcedResult // có thì là lượt Nghe ép: không có score/results/breakdown
}

export interface QuestionProps {
  question: Question
  value: Answer | undefined
  onChange: (v: Answer | undefined) => void
  disabled?: boolean
}
