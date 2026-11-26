import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { makeQueryClient } from '../../../lib/queryClient'
import { json, mockApi } from '../../../testUtils'
import { ResultView } from '../ResultView'
import type { Bank } from '../types'

const bank: Bank = {
  id: 7, title: 'Bộ 1', lessonId: 1, isTrial: false, mode: 'PRACTICE', timeLimitSec: null,
  questions: [
    { id: 1, type: 'CONNECTIVE_MCQ', prompt: 'Chọn', payload: {}, choices: [{ id: 5, label: 'A', content: 'So' }] },
    { id: 2, type: 'WRITING', prompt: 'Viết bài', payload: {} },
  ],
}

test('ResultView của bộ có Viết hiện trạng thái đang chấm', () => {
  mockApi({ '/api/grammar-elements': () => json(200, []) })
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <MemoryRouter>
        <ResultView bank={bank} initial={{ id: 9, bankId: 7, score: 1, maxScore: 3, results: [], pendingJobs: [11] }} onRetry={() => {}} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  expect(screen.getByRole('status')).toHaveTextContent('đang chấm')
  expect(screen.queryByText(/\/3/)).not.toBeInTheDocument()
})
