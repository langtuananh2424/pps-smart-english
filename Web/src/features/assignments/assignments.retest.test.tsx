import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import type { Assignment } from './types'

// Tester kiểm lại #61: màn làm bài bài giao (404 về danh sách, Nghe ép "Làm lại" mở thẳng lượt mới).
const inH = (h: number) => new Date(Date.now() + h * 3600_000).toISOString()
const asg: Assignment = { id: 1, classId: 3, bankId: 41, bankTitle: 'Bộ nghe', questionId: null, questionType: null, dueAt: inH(72), status: 'OPEN', wholeClass: true, myStatus: 'NOT_SUBMITTED' }
const bank = { id: 41, title: 'Bộ nghe', lessonId: null, isTrial: false, timeLimitSec: null,
  questions: [{ id: 1, type: 'CONNECTIVE_MCQ', prompt: 'Câu hỏi một', payload: {}, choices: [{ id: 11, label: 'A', content: 'Yes' }, { id: 12, label: 'B', content: 'No' }] }] }
const forced = { id: 5, bankId: 41, forced: { correctCount: 0, totalCount: 1, percent: 0, passed: false, parts: [], attemptNo: 1, hintEveryN: 3 } }
const base = {
  '/api/auth/refresh': () => json(200, auth),
  '/api/me': () => json(200, { displayName: 'Lan' }),
  '/api/me/assignments/1': () => json(200, asg),
  '/api/banks/41': () => json(200, bank),
}

beforeEach(() => setAccessToken('t'))
afterEach(() => vi.unstubAllGlobals())

describe('màn làm bài bài giao', () => {
  test('lượt trả 404 (của người khác hoặc không còn): về /bai-giao, không hiện thẻ lỗi', async () => {
    mockApi({ ...base, '/api/attempts/5': () => json(404, { error: { code: 'attempt_not_found', message: 'x' } }) })
    const router = renderAt('/lop/3/bai-giao/1/lam-bai/5')
    await waitFor(() => expect(router.state.location.pathname).toBe('/bai-giao'))
  })

  test('Nghe ép: nộp xong xem kết quả tại chỗ, Làm lại gọi /start một lần và mở thẳng lượt mới', async () => {
    let submitted = false
    const calls = mockApi({
      ...base,
      '/api/attempts/5': () => json(200, submitted ? { ...forced, submittedAt: inH(0) } : { id: 5, bankId: 41, startedAt: inH(0) }),
      '/api/attempts/5/progress': () => json(200, { attemptId: 5, paused: false }),
      '/api/attempts/5/submit': () => { submitted = true; return json(200, forced) },
      '/api/classes/3/assignments/1/start': () => json(200, { id: 6, bankId: 41, startedAt: inH(0), timeLimitSec: null }),
      '/api/attempts/6': () => json(200, { id: 6, bankId: 41, startedAt: inH(0) }),
      '/api/attempts/6/progress': () => json(200, { attemptId: 6, paused: false }),
    })
    const router = renderAt('/lop/3/bai-giao/1/lam-bai/5')
    await userEvent.click(await screen.findByRole('radio', { name: /Yes/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Nộp bài' }))
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Nộp bài' }))
    expect(await screen.findByText('Chưa đạt yêu cầu')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/lop/3/bai-giao/1/lam-bai/5')

    await userEvent.click(screen.getByRole('button', { name: 'Làm lại' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/lop/3/bai-giao/1/lam-bai/6'))
    expect(await screen.findByRole('radio', { name: /Yes/ })).toBeInTheDocument()
    expect(screen.queryByText('Chưa đạt yêu cầu')).not.toBeInTheDocument()
    expect(calls.filter((c) => c === '/api/classes/3/assignments/1/start')).toHaveLength(1)
  })
})
