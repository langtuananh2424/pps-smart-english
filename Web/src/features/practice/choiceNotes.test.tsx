import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { setAccessToken } from '../../lib/api'
import { makeQueryClient } from '../../lib/queryClient'
import { json, mockApi } from '../../testUtils'
import { CheckBar } from './CheckBar'
import { ResultView } from './ResultView'
import type { AttemptResult, Bank, ChoiceNote, Question } from './types'

// FR-EX-03: giải thích từng phương án hiện ở màn kết quả và ở câu khóa sai.
const notes: ChoiceNote[] = [
  { label: 'A', correct: false, explanation: 'A sai vì thiếu liên từ' },
  { label: 'B', correct: true, explanation: 'B đúng vì chỉ tương phản' },
]
const question = { id: 1, type: 'CONNECTIVE_MCQ', prompt: 'Chọn', payload: {}, choices: [{ id: 5, label: 'B', content: 'But' }] } as Question

const wrap = (ui: React.ReactNode) => (
  <QueryClientProvider client={makeQueryClient()}>
    <MemoryRouter>{ui}</MemoryRouter>
  </QueryClientProvider>
)

describe('giải thích từng phương án', () => {
  beforeEach(() => {
    setAccessToken('t')
    mockApi({ '/api/grammar-elements': () => json(200, []) })
  })

  test('màn kết quả hiện từng phương án dưới giải thích câu', () => {
    const bank: Bank = { id: 7, title: 'Bộ', lessonId: 1, isTrial: false, mode: 'PRACTICE', timeLimitSec: null, questions: [question] }
    const r: AttemptResult = { id: 9, bankId: 7, score: 0, maxScore: 1, pendingJobs: [], results: [{ questionId: 1, isCorrect: false, correctAnswer: { choiceId: 5 }, explanation: 'Vì B', choiceNotes: notes }] }
    mockApi({ '/api/grammar-elements': () => json(200, []), '/api/attempts/9': () => json(200, r) })
    render(wrap(<ResultView bank={bank} initial={r} onRetry={() => {}} />))
    expect(screen.getByLabelText('Giải thích từng phương án')).toHaveTextContent('A.A sai vì thiếu liên từB.B đúng vì chỉ tương phản')
  })

  test('câu khóa sai hiện giải thích từng phương án; chưa khóa thì không', () => {
    const state = { questionId: 1, correct: false, checksLeft: 0, wrongCount: 3, correctAnswer: { choiceId: 5 }, choiceNotes: notes }
    const { unmount } = render(wrap(<CheckBar state={state} question={question} canCheck pending={false} onCheck={() => {}} />))
    expect(screen.getByText('B đúng vì chỉ tương phản')).toBeInTheDocument()
    unmount()
    render(wrap(<CheckBar state={{ ...state, checksLeft: 1, correctAnswer: undefined, choiceNotes: undefined }} question={question} canCheck pending={false} onCheck={() => {}} />))
    expect(screen.queryByLabelText('Giải thích từng phương án')).not.toBeInTheDocument()
  })
})
