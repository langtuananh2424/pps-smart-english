import { render, screen } from '@testing-library/react'
import { GroupVideo } from './GroupVideo'
import type { BankGroup } from './types'

const base: BankGroup = { id: 1, title: null, instruction: null, passageHtml: null, sortOrder: 0, hasAudio: false, audioDurationMs: null }

describe('GroupVideo', () => {
  test('file: video có nodownload và chặn menu chuột phải', () => {
    render(<GroupVideo group={{ ...base, videoUrl: 'https://cdn/v.mp4?sig=1' }} />)
    const v = screen.getByLabelText('Video đề')
    expect(v).toHaveAttribute('src', 'https://cdn/v.mp4?sig=1')
    expect(v).toHaveAttribute('controlsList', 'nodownload')
    expect(v.dispatchEvent(new MouseEvent('contextmenu', { bubbles: true, cancelable: true }))).toBe(false)
  })

  test('YouTube: nhúng youtube-nocookie', () => {
    render(<GroupVideo group={{ ...base, videoYoutubeId: 'dQw4w9WgXcQ' }} />)
    expect(screen.getByTitle('Video đề').getAttribute('src')).toContain('https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ')
  })

  test('không có video thì không hiện gì', () => {
    const { container } = render(<GroupVideo group={base} />)
    expect(container).toBeEmptyDOMElement()
  })
})
