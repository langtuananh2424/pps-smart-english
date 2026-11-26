import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'

// Tester D-149: XSS từ tên bộ, open redirect, lượt chưa nộp, không lộ điểm khi còn chờ chấm, không trùng Cam trang chủ.
const notice = (title: string, url: string) => ({ id: 5, type: 'EXERCISE_GRADED', createdAt: 'x', readAt: null, payload: { title, body: 'Mở để xem kết quả.', url } })
const bank = { id: 7, title: 'Bộ 1', lessonId: 1, isTrial: false, mode: 'PRACTICE', timeLimitSec: null, questions: [{ id: 1, type: 'WRITING', prompt: 'Viết', payload: {} }] }

function setup(title: string, url: string, extra: Record<string, () => Response> = {}) {
  setAccessToken('t')
  return mockApi({
    '/api/auth/refresh': () => json(200, auth),
    '/api/me/notifications?limit=20': () => json(200, { data: [notice(title, url)], nextCursor: null }),
    '/api/me/notifications/5/read': () => json(204),
    '/api/me/notifications/unread-count': () => json(200, { count: 1 }),
    ...extra,
  })
}

afterEach(() => sessionStorage.clear())

test('tên bộ chứa HTML hiện như chữ, không chèn phần tử', async () => {
  const evil = 'Bài "<img src=x onerror="window.__xss=1">" đã chấm xong'
  setup(evil, '/bai-tap/7/ket-qua/9')
  renderAt('/thong-bao')
  expect(await screen.findByText(evil)).toBeInTheDocument()
  const aside = screen.getByLabelText('Cam báo kết quả')
  expect(aside.querySelectorAll('img')).toHaveLength(1) // chỉ ảnh Cam
  expect((window as unknown as { __xss?: number }).__xss).toBeUndefined()
})

test.each(['javascript:alert(1)', 'bai-tap/7', '', ' /bai-tap/7', '\\\\evil.example'])('url %j không được điều hướng', async (url) => {
  const calls = setup('Bài "A" đã chấm xong', url)
  const router = renderAt('/thong-bao')
  await userEvent.click(await screen.findByRole('button', { name: /Xem kết quả/ }))
  await waitFor(() => expect(calls).toContain('/api/me/notifications/5/read'))
  expect(router.state.location.pathname).toBe('/thong-bao')
})

test('lượt chưa nộp: về màn làm bài', async () => {
  setup('x', '/x', {
    '/api/banks/7': () => json(200, bank),
    '/api/attempts/9': () => json(200, { id: 9, bankId: 7, submittedAt: null, maxScore: null }),
    '/api/me/attempts?bankId=7&open=true&limit=1': () => json(200, { data: [] }),
  })
  const router = renderAt('/bai-tap/7/ket-qua/9')
  await waitFor(() => expect(router.state.location.pathname).toBe('/bai-tap/7'))
})

test('còn pendingJobs: không hiện điểm hay kết quả từng câu', async () => {
  setup('x', '/x', {
    '/api/banks/7': () => json(200, bank),
    '/api/attempts/9': () => json(200, { id: 9, bankId: 7, submittedAt: 'x', maxScore: 1, pendingJobs: [3] }),
    '/api/attempts/9/ai-sessions': () => json(200, { data: [] }),
  })
  renderAt('/bai-tap/7/ket-qua/9')
  expect(await screen.findByText('Bạn đã nộp bài!')).toBeInTheDocument()
  expect(screen.queryByLabelText('Kết quả từng câu')).not.toBeInTheDocument()
  expect(screen.queryByText(/^(Đạt|Chưa đạt)$/)).not.toBeInTheDocument() // "Đang chấm 0/1" là tiến độ, không phải điểm
})

test('mã lượt không phải số: không gọi API lượt lạ, báo lỗi', async () => {
  const calls = setup('x', '/x', { '/api/banks/7': () => json(200, bank) })
  renderAt('/bai-tap/7/ket-qua/..%2F..%2Fadmin')
  await waitFor(() => expect(calls.some((c) => c.startsWith('/api/attempts/'))).toBe(true))
  expect(calls.filter((c) => c.startsWith('/api/attempts/')).every((c) => /^\/api\/attempts\/(NaN|\d+)/.test(c))).toBe(true)
})

test('trang chủ có thông báo chấm xong: chỉ một Cam (Cam gợi ý nhường)', async () => {
  setup('Bài "A" đã chấm xong', '/bai-tap/7/ket-qua/9', {
    '/api/me': () => json(200, { displayName: 'Lan' }),
    '/api/me/cam-messages': () => json(200, { messages: [{ kind: 'GREETING', text: 'Chào bạn', action: null }] }),
  })
  renderAt('/')
  expect(await screen.findByLabelText('Cam báo kết quả')).toBeInTheDocument()
  await new Promise((r) => setTimeout(r, 50))
  expect(screen.queryByRole('region', { name: 'Gợi ý từ Cam' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Mở gợi ý của Cam' })).not.toBeInTheDocument()
})
