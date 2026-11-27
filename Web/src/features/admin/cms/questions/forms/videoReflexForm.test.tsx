import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { fmtClock, parseClock, VideoReflexEditor } from './videoReflexForm'
import type { Draft } from '../types'

test('đổi mm:ss sang giây và ngược lại', () => {
  expect(parseClock('1:30')).toBe(90)
  expect(parseClock('45')).toBe(45)
  expect(parseClock('1:75')).toBeNull()
  expect(parseClock('abc')).toBeNull()
  expect(fmtClock(65)).toBe('1:05')
})

test('nhập mốc mm:ss lưu bằng giây; Cue card có ô cue; để trống thời gian thì bỏ khỏi payload', async () => {
  const patch = vi.fn()
  const draft = { type: 'VIDEO_CUE', payload: { prompt: 'a', cue: 'c', maxSeconds: 60 }, answerKey: {}, choices: [] } as unknown as Draft
  render(<VideoReflexEditor draft={draft} patch={patch} errors={{}} elements={[]} layout={null} />)
  await userEvent.type(screen.getByLabelText(/Mốc dừng/), '1:05')
  expect(patch).toHaveBeenLastCalledWith({ payload: { prompt: 'a', cue: 'c', maxSeconds: 60, at: 65 } })
  expect(screen.getByLabelText('Cue card (gợi ý nói)')).toBeInTheDocument()
  await userEvent.clear(screen.getByLabelText(/Thời gian ghi tối đa/))
  expect(patch).toHaveBeenLastCalledWith({ payload: { prompt: 'a', cue: 'c' } })
})
