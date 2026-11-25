import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { setAccessToken } from '../../../lib/api'
import { makeQueryClient } from '../../../lib/queryClient'
import { auth, json, mockApi, renderAt } from '../../../testUtils'
import { ResultView } from '../ResultView'
import type { Bank } from '../types'

const bank: Bank = {
  id: 7, title: 'Bộ 1', lessonId: 1, isTrial: false, mode: 'PRACTICE', timeLimitSec: null,
  questions: [
    { id: 1, type: 'CONNECTIVE_MCQ', prompt: 'Chọn', payload: {}, choices: [{ id: 5, label: 'A', content: 'So' }] },
    { id: 2, type: 'WRITING', prompt: 'Viết bài', payload: {} },
  ],
}
const err = (status: number, code: string) => json(status, { error: { code, message: 'Lượt làm bài này đã được nộp' } })
const session = (sessionId: number, over = {}) => ({
  sessionId, status: 'ACTIVE', question: { id: 2, prompt: 'Viết bài', payload: {} }, createdAt: 'x',
  turns: [{ role: 'CAM', text: 'Viết bài' }, { role: 'CAM', text: 'Vì sao?' }], ...over,
})

describe('câu Viết trong lượt', () => {
  beforeEach(() => {
    setAccessToken('t')
    sessionStorage.clear()
  })
  const wire = (over: Record<string, (b: unknown) => Response> = {}) =>
    mockApi({
      '/api/auth/refresh': () => json(200, auth),
      '/api/banks/7': () => json(200, bank),
      '/api/me/attempts?bankId=7&open=true&limit=1': () => json(200, { data: [] }),
      '/api/banks/7/attempts': () => json(201, { id: 9, bankId: 7, startedAt: 'x', timeLimitSec: null }),
      '/api/attempts/9/progress': () => json(200, { attemptId: 9, paused: false }),
      '/api/attempts/9/ai-sessions': () => json(200, { data: [] }),
      ...over,
    })
  const toWriting = async () => {
    renderAt('/bai-tap/7')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Câu 2' }))
  }
  const sessions = (data: unknown[]) => ({ '/api/attempts/9/ai-sessions': () => json(200, { data }) })

  test('mở phiên gửi attemptId của lượt đang mở', async () => {
    const bodies: unknown[] = []
    wire({ '/api/writing/sessions': (b) => (bodies.push(b), json(201, { sessionId: 3, question: { id: 2, prompt: 'Viết bài' } })), '/api/writing/sessions/3': () => json(200, session(3)) })
    await toWriting()
    await userEvent.click(screen.getByRole('button', { name: 'Bắt đầu viết' }))
    await screen.findByRole('dialog', { name: 'Viết tự luận' })
    expect(bodies).toEqual([{ questionId: 2, attemptId: 9 }])
  })

  test('làm lại thì tạo phiên mới và không lộ điểm khi chưa nộp bộ', async () => {
    const bodies: unknown[] = []
    const graded = session(3, { status: 'SUBMITTED', finalText: 'My text', grade: { taskResponse: 80, coherence: 70, lexical: 60, grammar: 50, scorePercent: 65, correctedAnswer: 'Better text' } })
    wire({
      '/api/writing/sessions': (b) => (bodies.push(b), json(201, { sessionId: bodies.length === 1 ? 3 : 4, question: { id: 2, prompt: 'Viết bài' } })),
      '/api/writing/sessions/3': () => json(200, graded),
      '/api/writing/sessions/4': () => json(200, session(4)),
    })
    await toWriting()
    await userEvent.click(screen.getByRole('button', { name: 'Bắt đầu viết' }))
    await screen.findByText(/Đã chấm xong/)
    expect(screen.queryByText('Better text')).not.toBeInTheDocument()
    expect(screen.queryByText('65')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Viết lại' }))
    await waitFor(() => expect(bodies).toEqual([{ questionId: 2, attemptId: 9 }, { questionId: 2, attemptId: 9 }]))
    await screen.findByLabelText('Câu trả lời của bạn')
  })

  test('trạng thái câu dựng từ máy chủ: đã chấm thì mở lại phiên đó, không tạo phiên trống', async () => {
    const bodies: unknown[] = []
    wire({
      ...sessions([{ questionId: 2, kind: 'WRITING', id: 3, status: 'GRADED' }]),
      '/api/writing/sessions': (b) => (bodies.push(b), json(201, { sessionId: 9, question: { id: 2, prompt: 'x' } })),
      '/api/writing/sessions/3': () => json(200, session(3, { status: 'SUBMITTED', finalText: 't', grade: { taskResponse: 1, coherence: 1, lexical: 1, grammar: 1, scorePercent: 65, correctedAnswer: 'Better' } })),
    })
    await toWriting()
    expect(await screen.findByText(/AI đã chấm xong/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Tiếp tục bài viết' }))
    await screen.findByText(/Đã chấm xong\. Điểm/)
    expect(bodies).toEqual([])
    expect(screen.queryByText('Better')).not.toBeInTheDocument()
  })

  test('chưa tải được trạng thái thì chưa cho mở câu Viết', async () => {
    wire({ '/api/attempts/9/ai-sessions': () => json(500, { error: { code: 'X', message: 'x' } }) })
    await toWriting()
    expect(screen.getByRole('button', { name: 'Bắt đầu viết' })).toBeDisabled()
  })

  test('hộp xác nhận đếm cả câu Viết chưa mở phiên', async () => {
    wire()
    await toWriting()
    await userEvent.click(screen.getByRole('button', { name: 'Nộp bài' }))
    expect(await screen.findByText(/Bạn còn 2 câu chưa làm/)).toBeInTheDocument()
  })

  test('lượt đã nộp (409) hiện thông báo trên câu', async () => {
    wire({ '/api/writing/sessions': () => err(409, 'attempt_already_submitted') })
    await toWriting()
    await userEvent.click(screen.getByRole('button', { name: 'Bắt đầu viết' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('đã được nộp')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})

test('ResultView của bộ có Viết hiện trạng thái đang chấm', () => {
  mockApi({ '/api/grammar-elements': () => json(200, []) })
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <MemoryRouter>
        <ResultView bank={bank} initial={{ id: 9, bankId: 7, score: 1, maxScore: 3, results: [], pendingJobs: [11] }} onRetry={() => {}} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  expect(screen.getByRole('status')).toHaveTextContent('Đang chấm câu AI')
  expect(screen.queryByLabelText('Điểm')).toHaveTextContent(/^$|Đang chấm/) // chưa hiện điểm khi còn câu AI chưa chấm xong
  expect(screen.queryByText(/\/3/)).not.toBeInTheDocument()
})
