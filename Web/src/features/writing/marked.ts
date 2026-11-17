export interface Piece { text: string; code?: string }

// markedAnswer đánh dấu cấu trúc ngữ pháp bằng {{CODE|đoạn}}; tách thành các mảnh để tô màu.
export function splitMarked(src: string): Piece[] {
  const out: Piece[] = []
  let last = 0
  for (const m of src.matchAll(/\{\{([^|{}]+)\|([^{}]*)\}\}/g)) {
    if (m.index > last) out.push({ text: src.slice(last, m.index) })
    out.push({ code: m[1].trim(), text: m[2] })
    last = m.index + m[0].length
  }
  if (last < src.length) out.push({ text: src.slice(last) })
  return out
}
