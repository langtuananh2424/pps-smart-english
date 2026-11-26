import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { apiError, auth, json, mockApi, renderAt } from '../../testUtils'

// FR-RET-03, D-149, D-92: bong bóng Cam báo bài đã chấm xong.
const notice = (url: string) => ({
  id: 5, type: 'EXERCISE_GRADED', createdAt: 'x', readAt: null,
  payload: { title: 'Bài "Bộ 1" đã chấm xong', body: 'Mở để xem kết quả.', url },
})
const bank = { id: 7, title: 'Bộ 1', lessonId: 1, isTrial: false, mode: 'PRACTICE', timeLimitSec: null, questions: [{ id: 1, type: 'WRITING', prompt: 'Viết', payload: {} }] }
const attempt = (extra: object) => ({ id: 9, bankId: 7, submittedAt: 'x', maxScore: 1, ...extra })

function setup(url: string, extra: Record<string, () => Response> = {}) {
  setAccessToken('t')
  return mockApi({
    '/api/auth/refresh': () => json(200, auth),
    '/api/me/notifications?limit=20': () => json(200, { data: [notice(url)], nextCursor: null }),
    '/api/me/notifications/5/read': () => json(204),
    '/api/me/notifications/unread-count': () => json(200, { count: 1 }),
    ...extra,
  })
}

describe('bong bóng Cam báo chấm xong', () => {
  test('Xem kết quả: đánh dấu đã đọc rồi mở kết quả lượt', async () => {
    const calls = setup('/bai-tap/7/ket-qua/9', { '/api/banks/7': () => json(200, bank), '/api/attempts/9': () => json(200, attempt({ pendingJobs: [3] })) })
    const router = renderAt('/thong-bao')
    expect(await screen.findByText('Bài "Bộ 1" đã chấm xong')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Xem kết quả/ }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/bai-tap/7/ket-qua/9'))
    expect(calls).toContain('/api/me/notifications/5/read')
  })

  test('nút X: đánh dấu đã đọc, không chuyển trang', async () => {
    const calls = setup('/bai-tap/7/ket-qua/9')
    const router = renderAt('/thong-bao')
    await userEvent.click(await screen.findByRole('button', { name: 'Đóng thông báo của Cam' }))
    await waitFor(() => expect(calls).toContain('/api/me/notifications/5/read'))
    expect(router.state.location.pathname).toBe('/thong-bao')
  })

  test.each(['https://evil.example/x', '//evil.example/x', '/\\evil.example'])('url ngoài %s bị bỏ: chỉ đánh dấu đọc', async (url) => {
    const calls = setup(url)
    const router = renderAt('/thong-bao')
    await userEvent.click(await screen.findByRole('button', { name: /Xem kết quả/ }))
    await waitFor(() => expect(calls).toContain('/api/me/notifications/5/read'))
    expect(router.state.location.pathname).toBe('/thong-bao')
  })

  test('không hiện trên màn làm bài', async () => {
    setup('/bai-tap/7/ket-qua/9', { '/api/banks/7': () => json(200, bank), '/api/me/attempts?bankId=7&open=true&limit=1': () => json(200, { data: [] }) })
    renderAt('/bai-tap/7')
    await screen.findByRole('button', { name: /Bắt đầu/ })
    expect(screen.queryByLabelText('Cam báo kết quả')).not.toBeInTheDocument()
  })

  test('đã đọc rồi thì không hiện', async () => {
    setup('/x', { '/api/me/notifications?limit=20': () => json(200, { data: [{ ...notice('/x'), readAt: 'y' }], nextCursor: null }) })
    renderAt('/thong-bao')
    await screen.findByRole('heading', { name: 'Thông báo' })
    expect(screen.queryByLabelText('Cam báo kết quả')).not.toBeInTheDocument()
  })
})

describe('route kết quả theo mã lượt', () => {
  test('đang chấm: thẻ Chờ chấm', async () => {
    setup('/x', { '/api/banks/7': () => json(200, bank), '/api/attempts/9': () => json(200, attempt({ pendingJobs: [3] })), '/api/attempts/9/ai-sessions': () => json(200, { data: [] }) })
    renderAt('/bai-tap/7/ket-qua/9')
    expect(await screen.findByText('Bạn đã nộp bài!')).toBeInTheDocument()
  })

  test('đã chấm xong: hiện điểm', async () => {
    setup('/x', {
      '/api/banks/7': () => json(200, bank),
      '/api/attempts/9': () => json(200, attempt({ score: 1, pendingJobs: [], passed: true, passPct: 70, results: [{ questionId: 1, isCorrect: null }] })),
      '/api/attempts/9/ai-sessions': () => json(200, { data: [] }),
    })
    renderAt('/bai-tap/7/ket-qua/9')
    await waitFor(() => expect(screen.queryByText('Bạn đã nộp bài!')).not.toBeInTheDocument())
    expect(await screen.findByLabelText('Kết quả từng câu')).toBeInTheDocument()
  })

  test('lượt của người khác hoặc không tồn tại: báo lỗi thân thiện', async () => {
    setup('/x', { '/api/banks/7': () => json(200, bank), '/api/attempts/9': () => apiError(404, 'Không tìm thấy lượt làm bài') })
    renderAt('/bai-tap/7/ket-qua/9')
    expect(await screen.findByText('Không tìm thấy lượt làm bài')).toBeInTheDocument()
  })

  test('mã bộ lệch lượt: coi như không có lượt', async () => {
    setup('/x', { '/api/banks/8': () => json(200, { ...bank, id: 8 }), '/api/attempts/9': () => json(200, attempt({ pendingJobs: [3] })) })
    renderAt('/bai-tap/8/ket-qua/9')
    expect(await screen.findByText('Không tìm thấy lượt làm bài')).toBeInTheDocument()
  })
})
