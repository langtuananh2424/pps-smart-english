import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { setAccessToken } from '../../lib/api'
import { makeQueryClient } from '../../lib/queryClient'
import { json, mockApi } from '../../testUtils'
import { ResultView } from './ResultView'
import type { AttemptResult, Bank } from './types'

// FR-EX-06, FR-AI-02/03, D-145/D-147: màn chờ chấm, danh sách kết quả từng câu và xem nhận xét.
const bank: Bank = {
  id: 7, title: 'Bộ 1', lessonId: 1, isTrial: false, mode: 'PRACTICE', timeLimitSec: null,
  questions: [
    { id: 1, type: 'CONNECTIVE_MCQ', prompt: 'Chọn', payload: {}, choices: [{ id: 5, label: 'A', content: 'So' }] },
    { id: 2, type: 'WRITING', prompt: 'Viết đoạn văn', payload: {} },
    { id: 3, type: 'SPEAKING', prompt: 'Tell us', payload: {} },
    { id: 4, type: 'SPEAKING', prompt: 'Lỗi', payload: {} },
    { id: 5, type: 'WRITING', prompt: 'Bỏ qua', payload: {} },
  ],
}
const done: AttemptResult = {
  id: 9, bankId: 7, score: 2, maxScore: 4, pendingJobs: [], passed: false, passPct: 70,
  results: [
    { questionId: 1, isCorrect: true },
    { questionId: 2, isCorrect: null },
    { questionId: 3, isCorrect: null },
    { questionId: 4, isCorrect: null, aiFailure: 'bản ghi âm không rõ', aiRefunded: true },
    { questionId: 5, isCorrect: null },
  ],
}
const sessions = [
  { questionId: 2, kind: 'WRITING', id: 21, status: 'GRADED' },
  { questionId: 3, kind: 'SPEAKING', id: 31, status: 'GRADED' },
  { questionId: 4, kind: 'SPEAKING', id: 41, status: 'FAILED' },
]

function show(initial: AttemptResult, fetched = initial) {
  mockApi({
    '/api/grammar-elements': () => json(200, []),
    '/api/attempts/9': () => json(200, fetched),
    '/api/attempts/9/ai-sessions': () => json(200, { data: sessions }),
    '/api/writing/sessions/21': () => json(200, { sessionId: 21, status: 'SUBMITTED', question: { id: 2, prompt: 'Viết đoạn văn' }, turns: [], finalText: 'My text', grade: { scorePercent: 72, correctedAnswer: 'Better text', taskResponse: 1, coherence: 1, lexical: 1, grammar: 1 }, createdAt: 'x' }),
    '/api/speaking/submissions/31': () => json(200, { id: 31, questionId: 3, status: 'GRADED', createdAt: 'x', result: { status: 'GRADED', overall: 6, bands: { pronunciation: 6, fluency: 6, lexical: 6, grammar: 6 }, transcript: 'I took the bus.', mispronounced: [{ word: 'school', score: 1, issues: ['vowel'], expectedIpa: '/sku:l/' }] } }),
  })
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <MemoryRouter>
        <ResultView bank={bank} initial={initial} onRetry={() => {}} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('kết quả bộ có Viết/Nói', () => {
  beforeEach(() => setAccessToken('t'))

  test('còn câu đang chấm: màn Chờ chấm, không điểm, có Về danh mục và Cam', async () => {
    const pending: AttemptResult = { id: 9, bankId: 7, maxScore: 4, pendingJobs: [11, 12], passPct: 70 }
    show(pending)
    expect(await screen.findByText('Bạn đã nộp bài!')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Còn 2 câu Viết/Nói đang chấm')
    expect(await screen.findByText('Đang chấm 1/3')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Về danh mục/ })).toHaveAttribute('href', '/bai-tap')
    expect(screen.getByText('Có kết quả mình sẽ báo bạn nhé!')).toBeInTheDocument()
    expect(screen.queryByLabelText('Kết quả từng câu')).not.toBeInTheDocument()
  })

  test('danh sách từng câu: Đúng, %, Band, không chấm được kèm lý do, bỏ qua; tóm tắt câu lỗi', async () => {
    show(done)
    const list = await screen.findByLabelText('Kết quả từng câu')
    expect(await within(list).findByText('72%')).toBeInTheDocument()
    expect(within(list).getByText('Đúng')).toBeInTheDocument()
    expect(within(list).getByText('Band 6.0')).toBeInTheDocument()
    expect(within(list).getByText('0% · Không chấm được')).toBeInTheDocument()
    expect(within(list).getByText('Bản ghi âm không rõ. Đã hoàn 1 lượt AI.')).toBeInTheDocument()
    expect(within(list).getByText('0% · Bỏ qua')).toBeInTheDocument()
    expect(screen.getByText('1 câu không chấm được, đã tính 0 điểm.')).toBeInTheDocument()
    expect(within(list).getAllByRole('button', { name: /Xem nhận xét/ })).toHaveLength(2)
  })

  test('Xem nhận xét: Viết đối chiếu bản gốc/bản sửa, chuyển sang Nói, Về kết quả', async () => {
    show(done)
    await userEvent.click(await screen.findByRole('button', { name: 'Xem nhận xét câu 2' }))
    expect(screen.getByText('My text')).toBeInTheDocument()
    expect(screen.getByText('Better text')).toBeInTheDocument()
    expect(screen.getByText('72%')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Câu 4' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Câu 3' }))
    expect(screen.getByText('Band 6.0')).toBeInTheDocument()
    expect(screen.getByText('I took the bus.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Về kết quả' }))
    expect(await screen.findByLabelText('Kết quả từng câu')).toBeInTheDocument()
  })
})
