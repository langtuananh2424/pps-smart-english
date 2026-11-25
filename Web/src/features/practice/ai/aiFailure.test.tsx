import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { setAccessToken } from '../../../lib/api'
import { makeQueryClient } from '../../../lib/queryClient'
import { json, mockApi } from '../../../testUtils'
import { ResultView } from '../ResultView'
import type { AttemptResult, Bank } from '../types'

// D-147: máy chủ bỏ score/results khi còn câu AI chưa chốt; câu không chấm được hiện lý do và tính 0.
const bank: Bank = {
  id: 7, title: 'Bộ 1', lessonId: 1, isTrial: false, mode: 'PRACTICE', timeLimitSec: null,
  questions: [{ id: 2, type: 'WRITING', prompt: 'Viết bài', payload: {} }],
}

const show = (initial: AttemptResult, fetched: AttemptResult) => {
  mockApi({ '/api/grammar-elements': () => json(200, []), '/api/attempts/9': () => json(200, fetched) })
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <MemoryRouter>
        <ResultView bank={bank} initial={initial} onRetry={() => {}} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('kết quả khi API ẩn điểm tạm', () => {
  beforeEach(() => setAccessToken('t'))

  test('chưa có score (hết lượt AI, không có job chờ) thì không dựng điểm hay danh sách câu', () => {
    const r: AttemptResult = { id: 9, bankId: 7, maxScore: 1, pendingJobs: [], aiQuotaRequired: true, passPct: 70 }
    show(r, r)
    expect(screen.queryByLabelText('Điểm')?.textContent).not.toMatch(/\/1/)
    expect(screen.queryByText(/Chưa chấm/)).not.toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('hết lượt chấm AI')
  })

  test('câu chấm lỗi hiện lý do, đã hoàn lượt và tính 0', async () => {
    const r: AttemptResult = { id: 9, bankId: 7, score: 0, maxScore: 1, pendingJobs: [], passed: false, passPct: 70, results: [{ questionId: 2, isCorrect: null, aiFailure: 'AI chấm lỗi', aiRefunded: true }] }
    show(r, r)
    expect(await screen.findByText('Câu 1 không chấm được (AI chấm lỗi), đã hoàn lượt AI, tính 0 điểm.')).toBeInTheDocument()
  })

  test('bài giao không trừ lượt nên không ghi đã hoàn lượt AI', async () => {
    const r: AttemptResult = { id: 9, bankId: 7, score: 0, maxScore: 1, pendingJobs: [], passed: false, results: [{ questionId: 2, isCorrect: null, aiFailure: 'AI chấm lỗi', aiRefunded: false }] }
    show(r, r)
    expect(await screen.findByText('Câu 1 không chấm được (AI chấm lỗi), tính 0 điểm.')).toBeInTheDocument()
  })

  // Job cuối của lượt xong: màn kết quả tự hỏi lại GET lượt (không dựa vào sự kiện từng job) và hiện điểm.
  test('đang chấm thì tự hỏi lại lượt đến khi chấm xong rồi hiện điểm', async () => {
    let calls = 0
    mockApi({
      '/api/grammar-elements': () => json(200, []),
      '/api/attempts/9': () => json(200, ++calls < 2 ? { id: 9, bankId: 7, maxScore: 1, pendingJobs: [11], passPct: 70 } : { id: 9, bankId: 7, score: 0.8, maxScore: 1, pendingJobs: [], passed: true, passPct: 70, results: [] }),
    })
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <MemoryRouter>
          <ResultView bank={bank} initial={{ id: 9, bankId: 7, maxScore: 1, pendingJobs: [11], passPct: 70 }} onRetry={() => {}} />
        </MemoryRouter>
      </QueryClientProvider>,
    )
    expect(screen.getByRole('status')).toHaveTextContent('Đang chấm')
    expect(await screen.findByText('Đạt yêu cầu', { exact: false }, { timeout: 8000 })).toBeInTheDocument()
    expect(screen.getByLabelText('Điểm')).toHaveTextContent('0.8/1')
  }, 12_000)
})
