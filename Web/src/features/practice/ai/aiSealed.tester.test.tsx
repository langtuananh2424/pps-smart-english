import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { setAccessToken } from '../../../lib/api'
import { makeQueryClient } from '../../../lib/queryClient'
import { json, mockApi } from '../../../testUtils'
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
    expect(screen.getByRole('status')).toHaveTextContent('đang chấm')
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
