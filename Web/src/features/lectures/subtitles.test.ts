import { findCueIndex, makeSplitter } from './subtitles'

const cues = [
  { startMs: 1000, endMs: 3000, text: 'a' },
  { startMs: 3000, endMs: 4000, text: 'b' },
  { startMs: 6000, endMs: 7000, text: 'c' },
]
const g = (phrase: string) => ({ phrase, partOfSpeech: null, meaningVi: 'x', example: null, source: 'MANUAL' as const })

describe('findCueIndex', () => {
  test('tìm cue theo thời gian, biên cuối loại trừ, khoảng trống là -1', () => {
    expect(findCueIndex(cues, 0)).toBe(-1)
    expect(findCueIndex(cues, 1000)).toBe(0)
    expect(findCueIndex(cues, 2999)).toBe(0)
    expect(findCueIndex(cues, 3000)).toBe(1)
    expect(findCueIndex(cues, 4500)).toBe(-1)
    expect(findCueIndex(cues, 6500)).toBe(2)
    expect(findCueIndex(cues, 9000)).toBe(-1)
    expect(findCueIndex([], 10)).toBe(-1)
  })
})

describe('makeSplitter', () => {
  const texts = (s: ReturnType<ReturnType<typeof makeSplitter>>) => s.map((x) => (x.entry ? `[${x.text}]` : x.text)).join('')

  test('không phân biệt hoa thường, giữ nguyên chữ gốc', () => {
    expect(texts(makeSplitter([g('environment')])('We protect the Environment.'))).toBe('We protect the [Environment].')
  })
  test('đúng ranh giới từ', () => {
    expect(texts(makeSplitter([g('bar')])('barn bar, rebar'))).toBe('barn [bar], rebar')
  })
  test('cụm dài được ưu tiên, hỗ trợ nhiều từ và khoảng trắng thừa', () => {
    const s = makeSplitter([g('as well'), g('as well as'), g('as')])
    expect(texts(s('Tea as  well as coffee, as fine'))).toBe('Tea [as  well as] coffee, [as] fine')
  })
  test('ký tự đặc biệt của regex trong cụm không gây lỗi', () => {
    expect(texts(makeSplitter([g('a.b (c)')])('x a.b (c) y axb (c)'))).toBe('x [a.b (c)] y axb (c)')
  })
  test('không có glossary trả nguyên câu', () => {
    expect(makeSplitter([])('hello')).toEqual([{ text: 'hello' }])
  })
})
