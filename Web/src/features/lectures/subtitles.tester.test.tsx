import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createRef, type RefObject } from 'react'
import { SubtitleBlock } from './SubtitleBlock'
import { findCueIndex, makeSplitter } from './subtitles'

const g = (phrase: string) => ({ phrase, partOfSpeech: null, meaningVi: 'nghĩa ' + phrase, example: null, source: 'MANUAL' as const })
const texts = (s: ReturnType<ReturnType<typeof makeSplitter>>) => s.map((x) => (x.entry ? `[${x.text}]` : x.text)).join('')

describe('tester: findCueIndex', () => {
  test('cue dài chồng lên cue ngắn (VTT cho phép) vẫn hiện sau khi cue ngắn hết', () => {
    const cues = [
      { startMs: 0, endMs: 10000, text: 'long' },
      { startMs: 2000, endMs: 4000, text: 'short' },
    ]
    expect(findCueIndex(cues, 5000)).toBe(0)
  })
})

describe('tester: makeSplitter', () => {
  test('nhiều lần xuất hiện, dính dấu câu, trong ngoặc, sở hữu cách', () => {
    const s = makeSplitter([g('however'), g('in order to')])
    expect(texts(s('However, (however) we came in order to win; however.'))).toBe('[However], ([however]) we came [in order to] win; [however].')
  })
  test('không khớp giữa từ có dấu tiếng Việt hoặc số', () => {
    const s = makeSplitter([g('so')])
    expect(texts(s('so2 also sô so'))).toBe('so2 also sô [so]')
  })
  test('nháy cong trong phụ đề khớp cụm glossary viết nháy thẳng, giữ nguyên chữ gốc', () => {
    const segs = makeSplitter([g("don't")])('I don’t know, DON’T go')
    expect(texts(segs)).toBe('I [don’t] know, [DON’T] go')
    expect(segs.find((x) => x.entry)?.entry?.phrase).toBe("don't")
  })
  test('cụm chồng nhau: cụm bắt đầu sớm hơn thắng, không tạo đoạn rỗng', () => {
    const segs = makeSplitter([g('look up'), g('up to')])('look up to him')
    expect(texts(segs)).toBe('[look up] to him')
    expect(segs.every((x) => x.text.length > 0)).toBe(true)
  })
})

function setup(playing: boolean, en = 'We protect the environment today.') {
  const timeRef = createRef<(s: number) => void>() as RefObject<(s: number) => void>
  const control = { current: { pause: vi.fn(), play: vi.fn() } }
  const playingRef = { current: playing }
  const subtitles = { en: [{ startMs: 0, endMs: 4000, text: en }], vi: [{ startMs: 0, endMs: 4000, text: 'VI line' }] }
  const glossary = [{ phrase: 'environment', partOfSpeech: 'noun', meaningVi: 'Môi trường', example: null, source: 'MANUAL' as const }]
  render(<SubtitleBlock subtitles={subtitles} glossary={glossary} timeRef={timeRef} control={control} playingRef={playingRef} />)
  act(() => timeRef.current(1))
  return { control }
}

beforeEach(() => localStorage.clear())

describe('tester: SubtitleBlock', () => {
  test('nội dung cue dạng HTML hiện như chữ, không tạo phần tử', () => {
    setup(false, '<img src=x onerror=alert(1)> hi')
    expect(screen.getByText('<img src=x onerror=alert(1)> hi')).toBeInTheDocument()
    expect(document.querySelector('img')).toBeNull()
  })

  test('chế độ Tắt đã lưu thì mở trang không hiện phụ đề', () => {
    localStorage.setItem('pps.subtitleMode', 'off')
    setup(false)
    expect(screen.queryByText('VI line')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'environment' })).not.toBeInTheDocument()
  })

  test('bấm nền đóng popup và phát tiếp', async () => {
    const { control } = setup(true)
    await userEvent.click(screen.getByRole('button', { name: 'environment' }))
    const backdrop = screen.getByRole('dialog').parentElement!
    await userEvent.click(backdrop)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(control.current.play).toHaveBeenCalledTimes(1)
  })

  test('Esc vẫn đóng popup sau khi bấm vào chữ trong popup (focus rời nút)', async () => {
    setup(false)
    await userEvent.click(screen.getByRole('button', { name: 'environment' }))
    await userEvent.click(screen.getByRole('heading', { name: 'environment' }))
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
