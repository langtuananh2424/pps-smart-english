import { act, renderHook } from '@testing-library/react'
import { useDraft } from './useDraft'

const saveDraft = vi.fn()
vi.mock('./api', () => ({ saveDraft: (...a: unknown[]) => saveDraft(...a), saveDraftOnLeave: vi.fn() }))

afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
  saveDraft.mockReset()
})

const start = (onClosed?: (code: string) => void) => {
  const h = renderHook(({ a }) => useDraft(1, a, true, onClosed), { initialProps: { a: {} as never } })
  h.rerender({ a: { 1: { choiceId: 2 } } as never })
  return h
}

test('lỗi mạng liên tục: thử lại đúng 4 lần sau 2s, 4s, 8s, 16s rồi dừng', async () => {
  vi.useFakeTimers()
  saveDraft.mockRejectedValue(new Error('net'))
  start()
  await act(() => vi.advanceTimersByTimeAsync(2000)) // lần lưu đầu
  expect(saveDraft).toHaveBeenCalledTimes(1)
  for (const [wait, calls] of [[2000, 2], [4000, 3], [8000, 4], [16000, 5]] as const) {
    await act(() => vi.advanceTimersByTimeAsync(wait - 1))
    expect(saveDraft).toHaveBeenCalledTimes(calls - 1)
    await act(() => vi.advanceTimersByTimeAsync(1))
    expect(saveDraft).toHaveBeenCalledTimes(calls)
  }
  await act(() => vi.advanceTimersByTimeAsync(120_000))
  expect(saveDraft).toHaveBeenCalledTimes(5)
})

test('mất mạng thì trạng thái là offline', async () => {
  vi.useFakeTimers()
  vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
  saveDraft.mockRejectedValue(new Error('net'))
  const { result } = start()
  await act(() => vi.advanceTimersByTimeAsync(2000))
  expect(result.current.status).toBe('offline')
})

test('hết giờ hoặc đã nộp: báo đóng lượt và không thử lại', async () => {
  vi.useFakeTimers()
  const closed = vi.fn()
  saveDraft.mockRejectedValue(Object.assign(new Error('x'), { code: 'attempt_time_up' }))
  start(closed)
  await act(() => vi.advanceTimersByTimeAsync(2000))
  expect(closed).toHaveBeenCalledWith('attempt_time_up')
  await act(() => vi.advanceTimersByTimeAsync(60_000))
  expect(saveDraft).toHaveBeenCalledTimes(1)
})

test('online khi còn hẹn thử lại: lưu ngay một lần, hủy hẹn cũ (không lưu trùng)', async () => {
  vi.useFakeTimers()
  saveDraft.mockRejectedValueOnce(new Error('net')).mockResolvedValue({})
  const { result } = start()
  await act(() => vi.advanceTimersByTimeAsync(2000))
  expect(saveDraft).toHaveBeenCalledTimes(1) // đang hẹn thử lại sau 2s
  await act(async () => { window.dispatchEvent(new Event('online')) })
  expect(saveDraft).toHaveBeenCalledTimes(2)
  expect(result.current.status).toBe('saved')
  await act(() => vi.advanceTimersByTimeAsync(60_000))
  expect(saveDraft).toHaveBeenCalledTimes(2)
})

test('online khi không còn nháp chưa lưu: không gọi lưu', async () => {
  vi.useFakeTimers()
  saveDraft.mockResolvedValue({})
  start()
  await act(() => vi.advanceTimersByTimeAsync(2000))
  expect(saveDraft).toHaveBeenCalledTimes(1)
  await act(async () => { window.dispatchEvent(new Event('online')) })
  expect(saveDraft).toHaveBeenCalledTimes(1)
})

test('rời màn rồi có mạng: listener đã gỡ, không lưu nữa', async () => {
  vi.useFakeTimers()
  saveDraft.mockRejectedValue(new Error('net'))
  const h = start()
  await act(() => vi.advanceTimersByTimeAsync(2000))
  h.unmount()
  saveDraft.mockClear()
  await act(async () => { window.dispatchEvent(new Event('online')) })
  await act(() => vi.advanceTimersByTimeAsync(60_000))
  expect(saveDraft).not.toHaveBeenCalled()
})

test('lượt đã đóng (hết giờ): có mạng lại không gửi nháp vào lượt đã đóng', async () => {
  vi.useFakeTimers()
  const closed = vi.fn()
  saveDraft.mockRejectedValue(Object.assign(new Error('x'), { code: 'attempt_time_up' }))
  start(closed)
  await act(() => vi.advanceTimersByTimeAsync(2000))
  expect(saveDraft).toHaveBeenCalledTimes(1)
  await act(async () => { window.dispatchEvent(new Event('online')) })
  expect(saveDraft).toHaveBeenCalledTimes(1)
  expect(closed).toHaveBeenCalledTimes(1)
})
