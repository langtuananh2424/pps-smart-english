// Kiểu dữ liệu khớp DTO backend (CmsQuestionDtos, QuestionGroupDtos, QuestionMediaDtos, ListeningDtos, CmsDtos).
// payload/answerKey là JSON tự do theo từng dạng nên dùng Obj.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type Obj = Record<string, any>

export const QUESTION_TYPES = [
  ['CONNECTIVE_MCQ', 'Trắc nghiệm một đáp án'],
  ['CHOICE_MULTI', 'Trắc nghiệm nhiều đáp án'],
  ['TRUE_FALSE', 'Đúng / Sai / Không có thông tin'],
  ['FILL_TEXT', 'Gõ chữ vào ô'],
  ['WORD_BANK', 'Chọn từ trong hộp từ'],
  ['MATCH_OPTIONS', 'Chọn mục từ danh sách chung'],
  ['CONNECTIVE_MATCH', 'Ghép hai vế'],
  ['CONNECTIVE_CLOZE', 'Điền từ vào chỗ trống'],
  ['CONNECTIVE_REWRITE', 'Viết lại với từ nối'],
  ['GRAMMAR_IDENTIFY', 'Nhận diện cấu trúc'],
  ['GRAMMAR_SPOT_FIX', 'Tìm và sửa lỗi'],
  ['GRAMMAR_BUILD', 'Xếp khối thành câu'],
  ['GRAMMAR_REWRITE', 'Viết lại theo cấu trúc'],
  ['WRITING', 'Viết tự luận'],
  ['SPEAKING', 'Nói'],
] as const
// D-154: kiểu Luyện tập PPS mới.
export const PPS_TYPES = [
  ['LETTER_ORDER', 'Sắp xếp chữ cái'],
  ['DICTATION', 'Chép chính tả'],
  ['LISTEN_FORM', 'Nghe điền phiếu thông tin'],
  ['LISTEN_RECORD', 'Nghe rồi nộp bản ghi'],
  ['VIDEO_CONNECTIVE', 'Video từ nối'],
  ['VIDEO_SHORT', 'Video phản xạ · Câu ngắn'],
  ['VIDEO_CUE', 'Video phản xạ · Cue card'],
  ['VIDEO_DISCUSS', 'Video phản xạ · Thảo luận'],
  ['VIDEO_PICTURE', 'Video phản xạ · Tả tranh'],
] as const
// D-157: mỗi dạng IELTS Reading/Listening một kiểu; phần chung nằm ở layout của nhóm câu.
export const IELTS_TYPES = [
  ['IELTS_TFNG', 'True / False / Not Given'],
  ['IELTS_YNNG', 'Yes / No / Not Given'],
  ['IELTS_MATCH_INFO', 'Matching information'],
  ['IELTS_MATCH_HEADINGS', 'Matching headings'],
  ['IELTS_MATCH_FEATURES', 'Matching features'],
  ['IELTS_MATCH_ENDINGS', 'Matching sentence endings'],
  ['IELTS_MATCHING', 'Matching (Listening)'],
  ['IELTS_SENTENCE', 'Sentence completion'],
  ['IELTS_SUMMARY', 'Summary completion (gõ chữ)'],
  ['IELTS_SUMMARY_BANK', 'Summary completion (hộp từ)'],
  ['IELTS_NOTE', 'Note completion'],
  ['IELTS_TABLE', 'Table completion'],
  ['IELTS_FLOWCHART', 'Flow-chart completion'],
  ['IELTS_FORM', 'Form completion'],
  ['IELTS_DIAGRAM', 'Diagram label completion'],
  ['IELTS_MAP', 'Plan / Map labelling'],
  ['IELTS_SHORT', 'Short-answer questions'],
] as const
export type QuestionType = (typeof QUESTION_TYPES | typeof PPS_TYPES | typeof IELTS_TYPES)[number][0]
export const typeLabel = (t: string) => [...QUESTION_TYPES, ...PPS_TYPES, ...IELTS_TYPES].find(([c]) => c === t)?.[1] ?? t
export const usesChoices = (t: QuestionType) => t === 'CONNECTIVE_MCQ' || t === 'CHOICE_MULTI' || t === 'VIDEO_CONNECTIVE'

export const SKILLS = ['LISTENING', 'READING', 'GRAMMAR'] as const
export const DISPLAY_SIZES = [
  ['SM', 'Nhỏ (33%)'],
  ['MD', 'Vừa (50%)'],
  ['LG', 'Lớn (75%)'],
  ['FULL', 'Toàn khung (100%)'],
] as const
export const SIZE_PCT: Record<string, number> = { SM: 33, MD: 50, LG: 75, FULL: 100 }

export interface CourseView {
  id: number
  title: string
}
export interface LessonView {
  id: number
  title: string
}
export interface CourseTree {
  course: CourseView
  levels: { level: { id: number; title: string }; topics: { topic: { id: number; title: string }; lessons: LessonView[] }[] }[]
}

export interface BankView {
  id: number
  topicId: number
  lessonId: number | null
  title: string
  isTrial: boolean
  status: 'DRAFT' | 'PUBLISHED'
  questionCount: number
  timeLimitSec: number | null
  mode: 'PRACTICE' | 'EXAM'
  purpose: 'COURSE' | 'PLACEMENT'
  playbackMode: 'FREE' | 'EXAM' | 'LOCKED'
  playLimit: number | null
  // Ngưỡng đạt % của bộ bài (D-145); backend có thể chưa trả.
  passPct?: number | null
  // D-161: bộ thuộc khóa IELTS (cho soạn kiểu IELTS_*) và kỹ năng của topic chứa bộ.
  ielts?: boolean
  skill?: string | null
}
export interface ChoiceView {
  id: number
  label: string
  content: string
  contentHtml: string | null
  isCorrect: boolean
  explanation: string | null
}
export interface MediaView {
  assetId: number
  url: string
  width: number
  height: number
  role: 'INLINE' | 'DIAGRAM'
  displaySize: string
  altText: string
}
export interface QuestionDetail {
  id: number
  bankId: number
  type: QuestionType
  prompt: string
  payload: Obj | null
  answerKey: Obj | null
  choices: ChoiceView[]
  explanation: string | null
  sortOrder: number
  grammarElementCodes: string[]
  answerVersion: number
  regradeRequired: boolean
  skill: string | null
  instruction: string | null
  media: MediaView[]
  groupId: number | null
  promptHtml: string | null
  transcriptExcerpt: string | null
}
export interface GroupView {
  id: number
  bankId: number
  sortOrder: number
  title: string | null
  instruction: string | null
  passageHtml: string | null
  audioAssetId: number | null
  audioDurationMs: number | null
  transcriptHtml: string | null
  videoAssetId: number | null
  videoYoutubeId: string | null
  videoDurationSec?: number | null // D-159: thời lượng video YouTube do CMS nhập
  // D-154: bố cục dùng chung của dạng IELTS/Nghe; kind xem layout/layoutModel.ts.
  layout?: Obj | null
}
export interface BankDetail {
  bank: BankView
  questions: QuestionDetail[]
  groups: GroupView[]
}
export interface GrammarElement {
  id: number
  code: string
  name: string
  colorHex: string
}
export interface RuleView {
  ruleId: number | null
  playLimit: number
  passPctTotal: number
  hintEveryN: number
  wrongThreshold: number
  groupThresholds: { groupId: number; passPct: number }[]
  thresholdMode: 'TOTAL' | 'PART'
}

export interface ChoiceDraft {
  uid: string
  label: string
  contentHtml: string
  isCorrect: boolean
  explanation: string
}
export interface MediaDraft {
  assetId: number
  url: string
  role: 'INLINE' | 'DIAGRAM'
  displaySize: string
  altText: string
}
// Trạng thái soạn một câu; payload/answerKey giữ đúng dạng JSON gửi lên backend.
export interface Draft {
  type: QuestionType
  promptHtml: string
  instruction: string
  explanation: string
  groupId: number | null
  skill: string
  transcriptExcerpt: string
  grammarCodes: string[]
  choices: ChoiceDraft[]
  payload: Obj
  answerKey: Obj
  media: MediaDraft[]
}
