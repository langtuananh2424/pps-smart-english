import { fold } from '../courses/format'
import type { ExamStatus, TypeStat } from './types'

// Tên dạng câu theo SRS FR-EX-01/02 và docs/ba (UC-10: "Ghép hai vế", "Tìm và sửa lỗi"); D-44 cho nhận diện/xếp khối.
export const typeLabel: Record<string, string> = {
  CONNECTIVE_MCQ: 'Trắc nghiệm từ nối',
  CONNECTIVE_MATCH: 'Ghép hai vế bằng từ nối',
  CONNECTIVE_CLOZE: 'Điền từ nối vào đoạn văn',
  CONNECTIVE_REWRITE: 'Viết lại với từ nối',
  GRAMMAR_IDENTIFY: 'Nhận diện cấu trúc',
  GRAMMAR_SPOT_FIX: 'Tìm và sửa lỗi',
  GRAMMAR_BUILD: 'Xếp khối tạo câu',
  GRAMMAR_REWRITE: 'Viết lại câu theo cấu trúc',
  CHOICE_MULTI: 'Chọn nhiều đáp án',
  TRUE_FALSE: 'Đúng hoặc sai',
  FILL_TEXT: 'Điền từ',
  WORD_BANK: 'Chọn từ trong hộp',
  MATCH_OPTIONS: 'Chọn mục từ đáp án chung',
  LETTER_ORDER: 'Sắp xếp chữ cái thành từ',
  DICTATION: 'Chép chính tả',
  LISTEN_FORM: 'Nghe và điền phiếu',
  LISTEN_RECORD: 'Nghe và nộp bản ghi',
  VIDEO_CONNECTIVE: 'Xem video và chọn từ nối',
  VIDEO_SHORT: 'Video phản xạ · Câu ngắn',
  VIDEO_CUE: 'Video phản xạ · Cue card',
  VIDEO_DISCUSS: 'Video phản xạ · Thảo luận',
  VIDEO_PICTURE: 'Video phản xạ · Tả tranh',
  IELTS_TFNG: 'True / False / Not Given',
  IELTS_YNNG: 'Yes / No / Not Given',
  IELTS_MATCH_INFO: 'Nối thông tin với đoạn',
  IELTS_MATCH_HEADINGS: 'Nối tiêu đề với đoạn',
  IELTS_MATCH_FEATURES: 'Nối đặc điểm',
  IELTS_MATCH_ENDINGS: 'Nối nửa câu',
  IELTS_MATCHING: 'Nối đáp án',
  IELTS_SENTENCE: 'Hoàn thành câu',
  IELTS_SUMMARY: 'Hoàn thành đoạn tóm tắt',
  IELTS_SUMMARY_BANK: 'Điền tóm tắt từ hộp từ',
  IELTS_NOTE: 'Hoàn thành ghi chú',
  IELTS_TABLE: 'Hoàn thành bảng',
  IELTS_FLOWCHART: 'Hoàn thành sơ đồ khối',
  IELTS_FORM: 'Hoàn thành phiếu',
  IELTS_DIAGRAM: 'Gắn nhãn sơ đồ',
  IELTS_MAP: 'Gắn nhãn bản đồ',
  IELTS_SHORT: 'Trả lời ngắn',
  WRITING: 'Viết tự luận',
  SPEAKING: 'Luyện nói',
}
export const labelOf = (type: string) => typeLabel[type] ?? type

export type Family = 'CONNECTIVE' | 'GRAMMAR' | 'WRITING' | 'SPEAKING'
export const familyLabel: Record<Family, string> = { CONNECTIVE: 'Từ nối', GRAMMAR: 'Ngữ pháp', WRITING: 'Viết', SPEAKING: 'Nói' }
const familyOrder = Object.keys(familyLabel) as Family[]

export const familyOf = (type: string): Family | null =>
  type.startsWith('CONNECTIVE_') ? 'CONNECTIVE' : type.startsWith('GRAMMAR_') ? 'GRAMMAR' : type === 'WRITING' || type === 'SPEAKING' ? type : null

// Viết/Nói do AI chấm: không có độ chính xác đúng/sai.
// Video phản xạ (D-158): câu nói theo mốc video của nhóm, chấm như Nói gắn lượt.
export const isReflexType = (type: string) => type === 'VIDEO_SHORT' || type === 'VIDEO_CUE' || type === 'VIDEO_DISCUSS' || type === 'VIDEO_PICTURE'
export const isAiType = (type: string) => type === 'WRITING' || type === 'SPEAKING' || type === 'LISTEN_RECORD' || isReflexType(type)

export const percent = (n: number | null | undefined) => (n == null ? '—' : `${Math.round(n)}%`)

// Độ chính xác của khóa từ MyCourse; chưa làm câu nào → null (không hiện huy hiệu).
export const courseAccuracy = (answered?: number, correct?: number) =>
  answered ? Math.round(((correct ?? 0) * 100) / answered) : null

export interface TypeGroup {
  family: Family
  label: string
  types: TypeStat[]
}

// Nhóm theo họ dạng câu, giữ thứ tự cố định; bỏ họ không có dạng nào; lọc theo tên (không phân biệt dấu).
export function groupTypes(byType: TypeStat[], family: Family | null, query: string): TypeGroup[] {
  const q = fold(query.trim())
  return familyOrder
    .filter((f) => !family || f === family)
    .map((f) => ({
      family: f,
      label: familyLabel[f],
      types: byType.filter((t) => familyOf(t.type) === f && (!q || fold(labelOf(t.type)).includes(q))),
    }))
    .filter((g) => g.types.length > 0)
}

export const familiesIn = (byType: TypeStat[]): Family[] => familyOrder.filter((f) => byType.some((t) => familyOf(t.type) === f))

export type ExamView =
  | { kind: 'hidden' }
  | { kind: 'locked'; subtitle: string }
  | { kind: 'open'; subtitle: string; label: 'Làm bài thi' | 'Làm tiếp'; bankId: number }

// Thẻ bài thi khóa: ẩn khi không có đề; khóa hiện lý do; mở thì "Làm tiếp" nếu có lượt đang dở.
export function examView(s: ExamStatus | null | undefined): ExamView {
  if (!s || s.bankId == null) return { kind: 'hidden' }
  const attempts = `${s.attemptsUsed}/${s.attemptsAllowed} lượt`
  if (s.unlocked) {
    const inProgress = s.inProgressAttemptId != null
    return { kind: 'open', bankId: s.bankId, label: inProgress ? 'Làm tiếp' : 'Làm bài thi', subtitle: inProgress ? `Đang làm dở · ${attempts}` : `Đã thi ${attempts}` }
  }
  const subtitle =
    s.reason === 'PROGRESS'
      ? `Hoàn thành ≥ ${s.requiredPercent}% bài học để mở · hiện ${s.progressPercent}%`
      : s.reason === 'ATTEMPTS_EXHAUSTED'
        ? `Đã dùng hết ${attempts}`
        : 'Cần đăng ký khóa học để làm bài thi'
  return { kind: 'locked', subtitle }
}

export type BandView = { kind: 'value'; text: string } | { kind: 'none' }
export const bandView = (band: { value: number } | null | undefined): BandView =>
  band ? { kind: 'value', text: band.value === 0 ? '0' : band.value.toFixed(1) } : { kind: 'none' }

export const quotaPeriod: Record<string, string> = { WEEK: 'tuần này', MONTH: 'tháng này' }

export const bandSourceLabel: Record<string, string> = { EXAM: 'từ bài thi khóa', PLACEMENT: 'từ kiểm tra đầu vào' }
