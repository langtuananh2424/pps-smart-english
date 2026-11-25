import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { setAccessToken } from '../../../lib/api'
import { makeQueryClient } from '../../../lib/queryClient'
import { auth, json, mockApi, renderAt } from '../../../testUtils'
import { ResultView } from '../ResultView'
import type { Bank } from '../types'

// Tester D-145 hướng b: hộp xác nhận nộp và màn kết quả ẩn điểm tới khi chấm xong.
const bank: Bank = {
  id: 7, title: 'Bộ 1', lessonId: 1, isTrial: false, mode: 'PRACTICE', timeLimitSec: null,
  questions: [
    { id: 1, type: 'CONNECTIVE_MCQ', prompt: 'Chọn', payload: {}, choices: [{ id: 5, label: 'A', content: 'So' }] },
    { id: 2, type: 'WRITING', prompt: 'Viết bài', payload: {} },
  ],
}

describe('hộp xác nhận nộp bài', () => {
  beforeEach(() => {
    setAccessToken('t')
    sessionStorage.clear()
  })
  const wire = (aiSessions: unknown[]) =>
    mockApi({
      '/api/auth/refresh': () => json(200, auth),
      '/api/banks/7': () => json(200, bank),
      '/api/me/attempts?bankId=7&open=true&limit=1': () => json(200, { data: [] }),
      '/api/banks/7/attempts': () => json(201, { id: 9, bankId: 7, startedAt: 'x', timeLimitSec: null }),
      '/api/attempts/9/progress': () => json(200, { attemptId: 9, paused: false }),
      '/api/attempts/9/ai-sessions': () => json(200, { data: aiSessions }),
    })
  const openConfirm = async () => {
    renderAt('/bai-tap/7')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Câu 2' }))
    await userEvent.click(screen.getByRole('button', { name: 'Nộp bài' }))
  }

  test('câu Viết đã nộp chấm không còn bị đếm là chưa làm', async () => {
    wire([{ questionId: 2, kind: 'WRITING', id: 31, status: 'GRADING' }])
    await openConfirm()
    expect(await screen.findByText(/Bạn còn 1 câu chưa làm/)).toBeInTheDocument()
  })

  // Thẻ câu báo "Chưa nộp thì câu này tính 0 điểm" nên hộp xác nhận cũng phải tính phiên mở mà chưa nộp chấm là chưa làm.
  // Hiện đếm là đã làm (lỗi, xem báo cáo tester).
  test('phiên Viết mở mà chưa nộp chấm vẫn đếm là chưa làm', async () => {
    wire([{ questionId: 2, kind: 'WRITING', id: 31, status: 'NOT_SUBMITTED' }])
    await openConfirm()
    expect(await screen.findByText(/Bạn còn 2 câu chưa làm/)).toBeInTheDocument()
  })
})

describe('màn kết quả chờ chấm', () => {
  beforeEach(() => setAccessToken('t'))

  test('chấm xong (pendingJobs rỗng) thì mới hiện điểm và Đạt', async () => {
    mockApi({
      '/api/grammar-elements': () => json(200, []),
      '/api/attempts/9': () => json(200, { id: 9, bankId: 7, score: 1.8, maxScore: 2, results: [], pendingJobs: [], passed: true, passPct: 70 }),
    })
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <MemoryRouter>
          <ResultView bank={bank} initial={{ id: 9, bankId: 7, score: 1, maxScore: 2, results: [], pendingJobs: [11], passPct: 70 }} onRetry={() => {}} />
        </MemoryRouter>
      </QueryClientProvider>,
    )
    expect(screen.getByRole('status')).toHaveTextContent('Đang chấm')
    expect(screen.queryByText(/\/2/)).not.toBeInTheDocument()
    expect(await screen.findByText('Đạt yêu cầu', { exact: false })).toBeInTheDocument()
    expect(screen.getByLabelText('Điểm')).toHaveTextContent('1.8/2')
  })

  test('đang chấm thì không hiện Đạt dù API lỡ trả passed', () => {
    mockApi({ '/api/grammar-elements': () => json(200, []), '/api/attempts/9': () => new Promise<Response>(() => {}) as never })
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <MemoryRouter>
          <ResultView bank={bank} initial={{ id: 9, bankId: 7, score: 2, maxScore: 2, results: [], pendingJobs: [11], passed: true, passPct: 70 }} onRetry={() => {}} />
        </MemoryRouter>
      </QueryClientProvider>,
    )
    expect(screen.queryByText(/Đạt yêu cầu/)).not.toBeInTheDocument()
    expect(screen.queryByText(/2\/2/)).not.toBeInTheDocument()
  })
})
