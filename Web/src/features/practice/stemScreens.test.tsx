import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { makeQueryClient } from '../../lib/queryClient'
import { IeltsResult } from '../ielts/IeltsResult'
import { Session } from '../placement/Session'
import { AiReview } from './AiReview'
import { Unsupported } from './questions/Unsupported'
import type { Bank, Question, QuestionMedia } from './types'

vi.mock('../ielts/api', () => ({
  fetchIeltsResult: async () => ({ id: 1, bankId: 1, score: 1, maxScore: 1, results: [{ questionId: 1, isCorrect: true }], bands: null }),
}))

const media: QuestionMedia = { assetId: 7, url: 'http://img/7', width: 10, height: 10, role: 'DIAGRAM', displaySize: 'LG', altText: 'Sơ đồ' }
const evil = '<p>Xem <b>đây</b></p><script>window.__pwned = 1</script>'
const q = (extra: Partial<Question> = {}): Question => ({ id: 1, type: 'WRITING', prompt: 'Plain', payload: {}, promptHtml: '<p>Xem <b>đây</b></p>', media: [media], ...extra })

// Máy chủ đã làm sạch HTML; jsdom không chạy script chèn qua innerHTML nên __pwned phải vắng.
const expectStem = (container: HTMLElement) => {
  expect(container.querySelector('b')?.textContent).toBe('đây')
  expect(screen.getByRole('img', { name: 'Sơ đồ' })).toHaveAttribute('src', 'http://img/7')
  expect((window as unknown as { __pwned?: number }).__pwned).toBeUndefined()
}

test('AiReview hiện HTML và ảnh của đề', () => {
  const bank = { id: 1, title: 'B', questions: [q({ promptHtml: evil })] } as unknown as Bank
  const { container } = render(<AiReview bank={bank} questionId={1} outcomes={new Map()} onGo={() => {}} onBack={() => {}} />)
  expectStem(container)
})

test('IeltsResult hiện HTML và ảnh của câu', async () => {
  const bank = { id: 1, title: 'B', skill: 'READING', questions: [q({ type: 'CONNECTIVE_MCQ', promptHtml: evil })] } as unknown as Bank
  const { container } = render(
    <QueryClientProvider client={makeQueryClient()}><MemoryRouter><IeltsResult bank={bank} attemptId={1} onRetry={() => {}} /></MemoryRouter></QueryClientProvider>,
  )
  await waitFor(() => expect(container.querySelector('b')).not.toBeNull())
  expectStem(container)
})

test('placement Session hiện HTML và ảnh của câu', () => {
  const question = { ...q({ type: 'CONNECTIVE_MCQ', promptHtml: evil }), skill: 'READING' }
  const started = { attemptId: 7, guestToken: 't', title: 'KT', startedAt: 'x', deadlineAt: 'y', timeLimitSec: 900, remainingSec: 900, questions: [question] }
  const saved = { attemptId: 7, guestToken: 't', submitted: false, started, answers: {}, deadlineMs: Date.now() + 900_000 }
  const { container } = render(<MemoryRouter><Session saved={saved} onDone={() => {}} /></MemoryRouter>)
  expectStem(container)
})

test('Unsupported báo trung tính, không có link', () => {
  render(<MemoryRouter><Unsupported question={q()} value={undefined} onChange={() => {}} /></MemoryRouter>)
  expect(screen.getByText('Dạng câu này chưa hỗ trợ ở màn này')).toBeInTheDocument()
  expect(screen.queryByRole('link')).toBeNull()
})
