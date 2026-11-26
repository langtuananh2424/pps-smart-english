import type { Bank, Question } from '../practice/types'
import type { Part } from './types'

// D-151: bộ IELTS Reading/Listening mở khung thi; bộ thi khóa (mode EXAM) và bộ khác đi đường cũ.
export const isIeltsBank = (b: Pick<Bank, 'skill' | 'mode'>) => b.mode === 'PRACTICE' && (b.skill === 'READING' || b.skill === 'LISTENING')

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
