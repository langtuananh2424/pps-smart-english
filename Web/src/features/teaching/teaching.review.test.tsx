import { screen } from '@testing-library/react'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import { asg, detail, row } from './fixtures'

const URL = '/giang-day/lop/3/bai-giao/5/bai-nop/91'
const REVIEW = '/api/classes/3/assignments/5/attempts/91/result'
const review = {
  attempt: {
    id: 91, bankId: 41, submittedAt: '2026-11-21T03:00:00Z', score: 1, maxScore: 2,
    results: [
      { questionId: 1, isCorrect: true, correctAnswer: { choiceId: 11 }, explanation: 'Although chỉ sự tương phản' },
      { questionId: 2, isCorrect: false, correctAnswer: { accepted: ['however'] } },
    ],
  },
  bank: {
    id: 41, title: 'Bộ Unit 1', lessonId: null, isTrial: false, mode: 'PRACTICE', timeLimitSec: null,
    questions: [
      { id: 1, type: 'CONNECTIVE_MCQ', prompt: 'Chọn từ nối cho câu một', payload: {}, choices: [{ id: 11, label: 'A', content: 'although' }, { id: 12, label: 'B', content: 'so' }] },
      { id: 2, type: 'FILL_TEXT', prompt: 'Điền từ nối cho câu hai', payload: {} },
    ],
  },
  answers: [{ questionId: 1, answer: { choiceId: 12 }, isCorrect: true, credit: 1 }, { questionId: 2, answer: { text: 'banana' }, isCorrect: false, credit: 0 }],
}
const base = {
  '/api/auth/refresh': () => json(200, auth),
  '/api/classes/3': () => json(200, detail),
  '/api/classes/3/assignments': () => json(200, [asg()]),
  '/api/classes/3/assignments/5/progress': () => json(200, [row()]),
  '/api/classes/3/assignments/5/attempts/91/grades': () => json(200, []),
  '/grammar-elements': () => json(200, []),
  '/api/grammar-elements': () => json(200, []),
}

beforeEach(() => setAccessToken('t'))
afterEach(() => vi.unstubAllGlobals())

describe('xem bài làm bộ câu (D-174)', () => {
  test('hiện đề, bài làm của học viên và đáp án đúng cho trắc nghiệm và điền từ; không có nút làm lại', async () => {
    mockApi({ ...base, [REVIEW]: () => json(200, review) })
    renderAt(URL)
    expect(await screen.findByText('Chọn từ nối cho câu một')).toBeInTheDocument()
    expect(screen.getByText('Điền từ nối cho câu hai')).toBeInTheDocument()
    expect(screen.getByText('B. so')).toBeInTheDocument() // bài làm của học viên (trắc nghiệm)
    expect(screen.getByText('A. although')).toBeInTheDocument() // đáp án đúng
    expect(screen.getByText('banana')).toBeInTheDocument() // bài làm điền từ
    expect(screen.getByText('however')).toBeInTheDocument() // đáp án đúng điền từ
    expect(screen.getByText('Although chỉ sự tương phản')).toBeInTheDocument()
    expect(screen.getByText('1 điểm')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /làm lại|luyện thêm/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /làm lại|luyện thêm/i })).not.toBeInTheDocument()
  })

  test('404 từ máy chủ: báo lỗi, không dựng câu nào', async () => {
    mockApi({ ...base, [REVIEW]: () => json(404, { error: { code: 'attempt_not_found', message: 'Không tìm thấy lượt đã nộp của bài này' } }) })
    renderAt(URL)
    expect(await screen.findByText(/Không tìm thấy lượt đã nộp của bài này/)).toBeInTheDocument()
    expect(screen.queryByText('Chọn từ nối cho câu một')).not.toBeInTheDocument()
  })
})
