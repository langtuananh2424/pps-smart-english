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
export const isAiType = (type: string) => type === 'WRITING' || type === 'SPEAKING'

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
