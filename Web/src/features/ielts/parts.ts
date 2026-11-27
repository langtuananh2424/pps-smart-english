import type { Bank, Question } from '../practice/types'
import { isAiType } from '../exercises/format'
import type { Part } from './types'

// D-161: bộ thuộc khóa có cờ "Khóa IELTS" mở khung thi (kỹ năng theo topic); bộ khác dùng Player thường.
// Điều kiện nhận biết bộ IELTS nằm MỘT chỗ này.
export const isIeltsBank = (b: Pick<Bank, 'skill'> & { ielts?: boolean }) =>
  b.ielts === true && (b.skill === 'READING' || b.skill === 'LISTENING' || b.skill === 'WRITING' || b.skill === 'SPEAKING')

// Khung Viết/Nói (FR-EX-05): suy từ loại câu, không đọc lại kỹ năng. null = khung đọc/nghe.
export const aiStageOf = (b: Pick<Bank, 'questions'>): 'WRITING' | 'SPEAKING' | null => {
  const ts = b.questions.map((q) => q.type)
  if (ts.length && ts.every((t) => t === 'WRITING')) return 'WRITING'
  return ts.length && ts.every((t) => isAiType(t)) ? 'SPEAKING' : null
}

// Khung Viết/Nói: mỗi câu là một task/câu độc lập, không chia theo nhóm.
export const flatParts = (bank: Bank): { parts: Part[]; ordered: Question[] } => ({ parts: [{ key: 'all', questions: bank.questions, first: 0 }], ordered: bank.questions })

// Chia câu theo nhóm (đoạn đọc/audio) theo sortOrder; câu không thuộc nhóm nào gom thành một phần cuối.
export function buildParts(bank: Bank): { parts: Part[]; ordered: Question[] } {
  const groups = [...(bank.groups ?? [])].sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
  const parts: Part[] = []
  let first = 0
  const push = (key: string, questions: Question[], group?: Part['group']) => {
    if (questions.length === 0) return
    parts.push({ key, group, questions, first })
    first += questions.length
  }
  for (const g of groups) push(`g${g.id}`, bank.questions.filter((q) => q.groupId === g.id), g)
  const known = new Set(groups.map((g) => g.id))
  push('rest', bank.questions.filter((q) => q.groupId == null || !known.has(q.groupId)))
  return { parts, ordered: parts.flatMap((p) => p.questions) }
}

export const partLabel = (skill: Bank['skill'], n: number) => `${skill === 'LISTENING' ? 'Part' : 'Passage'} ${n}`

export type NavState = 'answered' | 'flagged' | 'empty'
// Đánh dấu xem lại ưu tiên hơn đã trả lời (Figma 00.02: Đã trả lời / Xem lại / Chưa trả lời).
export const navState = (answered: boolean, flagged: boolean): NavState => (flagged ? 'flagged' : answered ? 'answered' : 'empty')
