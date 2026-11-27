import { describeNewType } from './describeNew'
import { tfLabel } from './questions/TrueFalse'
import type { Answer, GroupLayout, Id, Question } from './types'

const same = (a: Id, b: Id) => String(a) === String(b)
type Block = { id: Id; text: string }

// Đổi đáp án (của học viên hoặc đáp án đúng) thành chữ để hiện ở màn kết quả; null = không có gì để hiện.
export function describeAnswer(q: Question, a: Answer | null | undefined, elementName: (code: string) => string = (c) => c, layout?: GroupLayout | null): string | null {
  if (!a) return null
  const added = describeNewType(q, a, layout)
  if (added !== undefined) return added
  const p = q.payload ?? {}
  switch (q.type) {
    case 'CONNECTIVE_MCQ': {
      const c = q.choices?.find((x) => x.id === a.choiceId)
      return c ? `${c.label}. ${c.content}` : null
    }
    case 'CONNECTIVE_MATCH': {
      const left = (p.left ?? []) as Block[]
      const right = (p.right ?? []) as Block[]
      return Object.entries((a.pairs ?? {}) as Record<string, Id>)
        .map(([l, r]) => `${left.find((x) => same(x.id, l))?.text ?? l} → ${right.find((x) => same(x.id, r))?.text ?? r}`)
        .join('; ')
    }
    case 'CONNECTIVE_CLOZE':
      // Đáp án đúng có thể là danh sách đáp án chấp nhận cho mỗi chỗ trống.
      return Object.entries((a.blanks ?? {}) as Record<string, string | string[]>)
        .map(([id, v]) => `${id}: ${Array.isArray(v) ? v.join(' / ') : v}`)
        .join('; ')
    case 'GRAMMAR_IDENTIFY': {
      const tokens = (p.tokens ?? []) as string[]
      return ((a.spans ?? []) as { start: number; end: number; element: string }[])
        .map((s) => `${tokens.slice(s.start, s.end + 1).join(' ')} (${elementName(s.element)})`)
        .join('; ')
    }
    case 'GRAMMAR_SPOT_FIX': {
      const tokens = (p.tokens ?? []) as string[]
      const fix = a.corrections ? (a.corrections as string[]).join(' / ') : a.correction
      return `${tokens[a.position as number] ?? '?'} → ${fix ?? ''}`
    }
    case 'GRAMMAR_BUILD': {
      const blocks = (p.blocks ?? []) as Block[]
      return ((a.order ?? []) as Id[]).map((id) => blocks.find((b) => same(b.id, id))?.text ?? id).join(' ')
    }
    case 'CHOICE_MULTI':
    case 'VIDEO_CONNECTIVE':
      return (
        ((a.choiceIds ?? []) as number[])
          .map((id) => q.choices?.find((x) => x.id === id))
          .filter((c) => c)
          .map((c) => `${c!.label}. ${c!.content}`)
          .join('; ') || null
      )
    case 'TRUE_FALSE': {
      const v = a.value ?? a.answer
      return typeof v === 'string' ? tfLabel(v) : null
    }
    case 'FILL_TEXT':
      return Array.isArray(a.accepted) ? a.accepted.join(' / ') : typeof a.text === 'string' ? a.text : null
    case 'WORD_BANK':
      return (
        Object.entries((a.blanks ?? {}) as Record<string, string>)
          .map(([id, v]) => `${id}: ${v}`)
          .join('; ') || null
      )
    case 'MATCH_OPTIONS': {
      const items = (p.items ?? []) as Block[]
      const options = (p.options ?? []) as Block[]
      const opt = (id: Id) => {
        const i = options.findIndex((x) => same(x.id, id))
        return i < 0 ? String(id) : `${String.fromCharCode(65 + i)}. ${options[i].text}`
      }
      return (
        Object.entries((a.matches ?? {}) as Record<string, Id>)
          .map(([i, o]) => `${items.find((x) => same(x.id, i))?.text ?? i} → ${opt(o)}`)
          .join('; ') || null
      )
    }
    default:
      return typeof a.text === 'string' ? a.text : null
  }
}
