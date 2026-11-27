import { tfLabel } from './questions/TrueFalse'
import { pickOptions } from './layout/controls'
import type { Answer, GroupLayout, Id, Question } from './types'

const same = (a: Id, b: Id) => String(a).toLowerCase() === String(b).toLowerCase()

// D-154: chữ hiển thị của đáp án (học viên hoặc đáp án đúng) các kiểu mới; undefined = không phải kiểu mới.
export function describeNewType(q: Question, a: Answer, layout?: GroupLayout | null): string | null | undefined {
  switch (q.type) {
    case 'LETTER_ORDER': {
      if (typeof a.word === 'string') return a.word
      const letters = (q.payload?.letters ?? []) as { id: Id; ch: string }[]
      return ((a.order ?? []) as Id[]).map((id) => letters.find((l) => String(l.id) === String(id))?.ch ?? '').join('') || null
    }
    case 'IELTS_TFNG':
    case 'IELTS_YNNG':
      return typeof a.value === 'string' ? tfLabel(a.value) : null
    case 'IELTS_SUMMARY_BANK': {
      const id = a.word
      return typeof id === 'string' ? (pickOptions(layout).find((o) => same(o.id, id))?.text ?? id) : null
    }
    case 'IELTS_MATCH_INFO': case 'IELTS_MATCH_HEADINGS': case 'IELTS_MATCH_FEATURES': case 'IELTS_MATCH_ENDINGS': case 'IELTS_MATCHING': case 'IELTS_MAP': {
      const id = a.option
      return typeof id === 'string' ? (pickOptions(layout).find((o) => same(o.id, id))?.text ?? id) : null
    }
    case 'DICTATION': case 'LISTEN_FORM': case 'IELTS_SENTENCE': case 'IELTS_SUMMARY': case 'IELTS_NOTE': case 'IELTS_TABLE':
    case 'IELTS_FLOWCHART': case 'IELTS_FORM': case 'IELTS_DIAGRAM': case 'IELTS_SHORT':
      return Array.isArray(a.accepted) ? String(a.accepted[0] ?? '') || null : typeof a.text === 'string' ? a.text : null
    default:
      return undefined
  }
}
