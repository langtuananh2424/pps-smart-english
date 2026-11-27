import { act, renderHook } from '@testing-library/react'
import { useDraft } from './useDraft'

const saveDraft = vi.fn()
vi.mock('./api', () => ({ saveDraft: (...a: unknown[]) => saveDraft(...a), saveDraftOnLeave: vi.fn() }))

afterEach(() => { vi.useRealTimers(); saveDraft.mockReset() })

test('lưu nháp lỗi thì tự thử lại có backoff rồi báo đã lưu', async () => {
  vi.useFakeTimers()
  saveDraft.mockRejectedValueOnce(new Error('net')).mockResolvedValue({})
  const { result, rerender } = renderHook(({ a }) => useDraft(1, a, true), { initialProps: { a: {} as never } })
  rerender({ a: { 1: { choiceId: 2 } } as never })
  await act(() => vi.advanceTimersByTimeAsync(2000))
  expect(result.current.status).toBe('failed')
  await act(() => vi.advanceTimersByTimeAsync(2000)) // thử lại sau 2s
  expect(saveDraft).toHaveBeenCalledTimes(2)
  expect(result.current.status).toBe('saved')
})

test('đã dừng thử lại mà còn nháp chưa lưu thì lưu lại khi có mạng', async () => {
  vi.useFakeTimers()
  saveDraft.mockRejectedValue(new Error('net'))
  const { result, rerender } = renderHook(({ a }) => useDraft(1, a, true), { initialProps: { a: {} as never } })
  rerender({ a: { 1: { choiceId: 2 } } as never })
  await act(() => vi.advanceTimersByTimeAsync(2000 + 2000 + 4000 + 8000 + 16000))
  expect(saveDraft).toHaveBeenCalledTimes(5) // lần đầu + 4 lần thử lại
  await act(() => vi.advanceTimersByTimeAsync(60000))
  expect(saveDraft).toHaveBeenCalledTimes(5) // đã dừng
  saveDraft.mockResolvedValue({})
  await act(async () => { window.dispatchEvent(new Event('online')) })
  expect(saveDraft).toHaveBeenCalledTimes(6)
  expect(result.current.status).toBe('saved')
})
