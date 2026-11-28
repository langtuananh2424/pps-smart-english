import type { Cue, GlossaryEntry } from './api'

export type SubtitleMode = 'both' | 'en' | 'off'
const MODE_KEY = 'pps.subtitleMode'

export const loadMode = (): SubtitleMode => {
  const v = localStorage.getItem(MODE_KEY)
  return v === 'en' || v === 'off' ? v : 'both'
}
export const saveMode = (m: SubtitleMode) => localStorage.setItem(MODE_KEY, m)

// Chỉ số cue đang hiện tại thời điểm ms, -1 nếu không có. Duyệt tuyến tính (tối đa ~5000 cue) nên đúng cả khi cue chồng nhau hoặc chưa sắp; cue bắt đầu muộn nhất thắng.
export function findCueIndex(cues: Cue[], ms: number): number {
  let at = -1
  for (let i = 0; i < cues.length; i++) {
    const c = cues[i]
    if (c.startMs <= ms && ms < c.endMs && (at < 0 || c.startMs >= cues[at].startMs)) at = i
  }
  return at
}

export interface Segment {
  text: string
  entry?: GlossaryEntry
}

// Nháy cong thành nháy thẳng (cùng độ dài nên vị trí khớp vẫn đúng với chữ gốc).
const quotes = (s: string) => s.replace(/[‘’]/g, "'")
const norm = (s: string) => quotes(s).toLowerCase().replace(/\s+/g, ' ')
const esc = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/\s+/g, '\\s+')

// Tách câu thành đoạn thường và đoạn là cụm trong glossary: không phân biệt hoa thường, đúng ranh giới từ, cụm dài được thử trước.
export function makeSplitter(entries: GlossaryEntry[]): (text: string) => Segment[] {
  const byPhrase = new Map(entries.map((e) => [norm(e.phrase.trim()), e]))
  if (byPhrase.size === 0) return (text) => [{ text }]
  const alt = [...byPhrase.keys()].sort((a, b) => b.length - a.length).map(esc).join('|')
  const re = new RegExp(`(?<![\\p{L}\\p{N}_])(?:${alt})(?![\\p{L}\\p{N}_])`, 'giu')
  return (text) => {
    const out: Segment[] = []
    let last = 0
    for (const m of quotes(text).matchAll(re)) {
      if (m.index > last) out.push({ text: text.slice(last, m.index) })
      out.push({ text: text.slice(m.index, m.index + m[0].length), entry: byPhrase.get(norm(m[0])) })
      last = m.index + m[0].length
    }
    if (last < text.length) out.push({ text: text.slice(last) })
    return out
  }
}
