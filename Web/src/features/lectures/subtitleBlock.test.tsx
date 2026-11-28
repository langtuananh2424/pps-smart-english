import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createRef, type RefObject } from 'react'
import { SubtitleBlock } from './SubtitleBlock'
import type { PlayerControl } from './VideoPlayer'

const subtitles = {
  en: [{ startMs: 1000, endMs: 4000, text: 'We protect the environment today.' }, { startMs: 5000, endMs: 6000, text: 'Next line' }],
  vi: [{ startMs: 1000, endMs: 4000, text: 'Chúng ta bảo vệ môi trường.' }],
}
const glossary = [{ phrase: 'environment', partOfSpeech: 'noun', meaningVi: 'Môi trường', example: 'Protect it.', source: 'WIKTIONARY' as const }]

function setup(playing: boolean) {
  const timeRef = createRef<(s: number) => void>() as RefObject<(s: number) => void>
  const control = { current: { pause: vi.fn(), play: vi.fn() } satisfies PlayerControl }
  const playingRef = { current: playing }
  render(<SubtitleBlock subtitles={subtitles} glossary={glossary} timeRef={timeRef} control={control} playingRef={playingRef} />)
  const at = (s: number) => act(() => timeRef.current(s))
  return { at, control }
}

beforeEach(() => localStorage.clear())

describe('SubtitleBlock', () => {
  test('hiện cue EN và VI theo thời gian', () => {
    const { at } = setup(false)
    at(2)
    expect(screen.getByText('Chúng ta bảo vệ môi trường.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'environment' })).toBeInTheDocument()
    at(4.5)
    expect(screen.queryByText('Chúng ta bảo vệ môi trường.')).not.toBeInTheDocument()
    at(5.5)
    expect(screen.getByText('Next line')).toBeInTheDocument()
  })

  test('chạm từ đang phát: dừng video, mở popup, đóng thì phát tiếp và trả focus', async () => {
    const { at, control } = setup(true)
    at(2)
    const word = screen.getByRole('button', { name: 'environment' })
    await userEvent.click(word)
    expect(control.current.pause).toHaveBeenCalled()
    const dlg = screen.getByRole('dialog')
    expect(dlg).toHaveAttribute('aria-modal', 'true')
    expect(dlg).toHaveTextContent('Môi trường')
    expect(dlg).toHaveTextContent('Nguồn: Wiktionary, CC BY-SA 4.0')
    expect(screen.queryByRole('button', { name: 'Nghe mẫu' })).not.toBeInTheDocument() // jsdom không có speechSynthesis
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(control.current.play).toHaveBeenCalled()
    expect(word).toHaveFocus()
  })

  test('video đang dừng thì đóng popup không phát', async () => {
    const { at, control } = setup(false)
    at(2)
    await userEvent.click(screen.getByRole('button', { name: 'environment' }))
    expect(control.current.pause).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Đóng' }))
    expect(control.current.play).not.toHaveBeenCalled()
  })

  test('chế độ Tiếng Anh ẩn dòng VI, Tắt ẩn hết; lưu localStorage', async () => {
    const { at } = setup(false)
    at(2)
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Phụ đề' }), 'en')
    expect(screen.queryByText('Chúng ta bảo vệ môi trường.')).not.toBeInTheDocument()
    expect(localStorage.getItem('pps.subtitleMode')).toBe('en')
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Phụ đề' }), 'off')
    expect(screen.queryByRole('button', { name: 'environment' })).not.toBeInTheDocument()
  })
})
