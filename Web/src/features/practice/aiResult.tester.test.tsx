import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { setAccessToken } from '../../lib/api'
import { makeQueryClient } from '../../lib/queryClient'
import { json, mockApi } from '../../testUtils'
import { ResultView } from './ResultView'
import type { AttemptResult, Bank } from './types'

// Tester đợt #53: D-147 không gọi GET chi tiết phiên khi lượt chưa mở khóa; nhận xét AI hiển thị dạng chữ (XSS).
const bank: Bank = {
  id: 7, title: 'Bộ 1', lessonId: 1, isTrial: false, mode: 'PRACTICE', timeLimitSec: null,
  questions: [
    { id: 2, type: 'WRITING', prompt: 'Viết', payload: {} },
    { id: 3, type: 'SPEAKING', prompt: 'Nói', payload: {} },
  ],
}
const XSS = '<img src=x onerror="window.__pwned=1">'
const sessions = [
  { questionId: 2, kind: 'WRITING', id: 21, status: 'GRADED' },
  { questionId: 3, kind: 'SPEAKING', id: 31, status: 'GRADED' },
]
const done: AttemptResult = { id: 9, bankId: 7, score: 1, maxScore: 2, pendingJobs: [], passed: false, passPct: 70, results: [{ questionId: 2, isCorrect: null }, { questionId: 3, isCorrect: null }] }

function show(initial: AttemptResult, fetched: AttemptResult = initial) {
  const calls = mockApi({
    '/api/grammar-elements': () => json(200, []),
    '/api/attempts/9': () => json(200, fetched),
    '/api/attempts/9/ai-sessions': () => json(200, { data: sessions }),
    '/api/writing/sessions/21': () => json(200, { sessionId: 21, status: 'SUBMITTED', question: { id: 2, prompt: 'Viết' }, turns: [], finalText: XSS, grade: { scorePercent: 50, correctedAnswer: XSS, feedback: XSS, taskResponse: 1, coherence: 1, lexical: 1, grammar: 1 }, createdAt: 'x' }),
    '/api/speaking/submissions/31': () => json(200, { id: 31, questionId: 3, status: 'GRADED', createdAt: 'x', audioUrl: 'javascript:alert(1)', result: { status: 'GRADED', overall: 4.5, bands: { pronunciation: 4, fluency: 5, lexical: 4, grammar: 5 }, feedback: XSS, transcript: XSS, mispronounced: [{ word: XSS, score: 1, issues: [XSS], expectedIpa: XSS }] } }),
  })
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <MemoryRouter>
        <ResultView bank={bank} initial={initial} onRetry={() => {}} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return calls
}
const detail = (calls: string[]) => calls.filter((c) => c.startsWith('/api/writing/sessions/') || c.startsWith('/api/speaking/submissions/'))

describe('D-147: chưa mở khóa thì không hỏi chi tiết phiên', () => {
  beforeEach(() => setAccessToken('t'))

  test('còn job đang chấm: màn Chờ chấm, không gọi GET phiên Viết/Nói, không hiện điểm', async () => {
    const calls = show({ id: 9, bankId: 7, maxScore: 2, pendingJobs: [5], passPct: 70 })
    expect(await screen.findByText('Bạn đã nộp bài!')).toBeInTheDocument()
    await waitFor(() => expect(calls).toContain('/api/attempts/9/ai-sessions'))
    await new Promise((r) => setTimeout(r, 50))
    expect(detail(calls)).toEqual([])
    expect(screen.queryByText(/50%|Band/)).not.toBeInTheDocument()
  })

  test('hết lượt AI, không có job chờ, API bỏ score: không gọi GET phiên, không dựng danh sách câu', async () => {
    const calls = show({ id: 9, bankId: 7, maxScore: 2, pendingJobs: [], aiQuotaRequired: true, passPct: 70 })
    await waitFor(() => expect(calls).toContain('/api/attempts/9/ai-sessions'))
    await new Promise((r) => setTimeout(r, 50))
    expect(detail(calls)).toEqual([])
    expect(screen.queryByLabelText('Kết quả từng câu')).not.toBeInTheDocument()
  })

  test('chờ chấm rồi chấm xong: chỉ sau khi có điểm mới gọi GET phiên và hiện %', async () => {
    const calls = show({ id: 9, bankId: 7, maxScore: 2, pendingJobs: [5], passPct: 70 }, done)
    // initialData đang chờ; lần hỏi lại đầu tiên (staleTime 0) đã trả kết quả chốt.
    expect(await screen.findByText('50%', {}, { timeout: 4000 })).toBeInTheDocument()
    const firstDetail = calls.findIndex((c) => c === '/api/writing/sessions/21')
    const firstDone = calls.indexOf('/api/attempts/9')
    expect(firstDetail).toBeGreaterThan(firstDone)
  })
})

describe('nhận xét AI là dữ liệu không tin cậy', () => {
  beforeEach(() => setAccessToken('t'))

  test('Viết: bài gốc, bản sửa, nhận xét chứa HTML hiện nguyên văn, không tạo phần tử', async () => {
    show(done)
    await userEvent.click(await screen.findByRole('button', { name: 'Xem nhận xét câu 1' }))
    const sec = screen.getByRole('region', { name: 'Đối chiếu bài viết' })
    expect(within(sec).getAllByText(XSS).length).toBe(3)
    expect(sec.querySelector('img[src="x"]')).toBeNull()
    expect((window as unknown as { __pwned?: number }).__pwned).toBeUndefined()
  })

  test('Nói: từ, IPA, lỗi, nhận xét, bản chép lời chứa HTML hiện nguyên văn', async () => {
    show(done)
    await userEvent.click(await screen.findByRole('button', { name: 'Xem nhận xét câu 2' }))
    const sec = screen.getByRole('region', { name: 'Nhận xét phát âm' })
    expect(sec.querySelector('img[src="x"]')).toBeNull()
    expect(within(sec).getByText(XSS, { selector: 'b' })).toBeInTheDocument()
    expect(within(sec).getAllByText(XSS, { selector: 'p' }).length).toBe(2)
  })
})

describe('kết quả từng câu: biên', () => {
  beforeEach(() => setAccessToken('t'))

  test('câu chấm lỗi mà chưa hoàn lượt (bài giao, D-147 mục 9): có lý do, không ghi "đã hoàn"', async () => {
    show({ ...done, results: [{ questionId: 2, isCorrect: null, aiFailure: 'lỗi hệ thống', aiRefunded: false }, { questionId: 3, isCorrect: null }] })
    const list = await screen.findByLabelText('Kết quả từng câu')
    expect(within(list).getByText('Lỗi hệ thống.')).toBeInTheDocument()
    expect(list).not.toHaveTextContent('hoàn')
    expect(within(list).getByText('0% · Không chấm được')).toBeInTheDocument()
    expect(within(list).queryByRole('button', { name: 'Xem nhận xét câu 1' })).not.toBeInTheDocument()
  })
})
