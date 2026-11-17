import type { Answer, Id, Question } from './types'

const same = (a: Id, b: Id) => String(a) === String(b)
type Block = { id: Id; text: string }

// Đổi đáp án (của học viên hoặc đáp án đúng) thành chữ để hiện ở màn kết quả; null = không có gì để hiện.
export function describeAnswer(q: Question, a: Answer | null | undefined, elementName: (code: string) => string = (c) => c): string | null {
  if (!a) return null
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
    default:
      return typeof a.text === 'string' ? a.text : null
  }
}
