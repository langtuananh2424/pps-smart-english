import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'

const attempt = {
  id: 21, bankId: 9, score: 8, maxScore: 10,
  results: [{ questionId: 1, isCorrect: true }, { questionId: 2, isCorrect: false, explanation: 'Vì sao' }],
  breakdown: { correct: 8, wrong: 1, ungraded: 1, bySkill: [{ key: 'LISTENING', label: 'Nghe', answered: 5, correct: 4, accuracy: 80, ungraded: 0 }] },
}
const bands = { skills: { LISTENING: { correct: 4, total: 5, band: 6.5 }, READING: { correct: 4, total: 5, band: 7 } }, overall: 6.5, gradingPending: true }
const open = (lastAttemptId: number, withBands = true, chapterAttemptId?: number) => {
  mockApi({
    '/api/auth/refresh': () => json(200, auth),
    '/api/me': () => json(200, { displayName: 'Lan' }),
    '/api/me/courses': () => json(200, []),
    '/api/attempts/21': () => json(200, attempt),
    '/api/courses/7/tests': () => json(200, chapterAttemptId ? [{ kind: 'CHAPTER', topicId: 3, title: 'Chương 1', bankId: 5, unlocked: true, reason: null, attemptsUsed: 1, attemptsAllowed: 2, inProgressAttemptId: null, lastResult: { attemptId: chapterAttemptId, score: 8, maxScore: 10, submittedAt: 'x', bands } }] : []),
    '/api/courses/7/exam': () => json(200, { bankId: 9, progressPercent: 100, requiredPercent: 70, unlocked: true, reason: null, attemptsUsed: 1, attemptsAllowed: 2, inProgressAttemptId: null, lastResult: { attemptId: lastAttemptId, score: 8, maxScore: 10, submittedAt: 'x', bands: withBands ? bands : null } }),
  })
  renderAt('/bai-tap/khoa/7/kiem-tra/21/ket-qua')
}

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

test('lượt gần nhất: band tổng, band từng kỹ năng, Viết/Nói đang chấm', async () => {
  open(21)
  expect(await screen.findByText('Đã nộp')).toBeInTheDocument()
  expect(screen.getByText('Band 6.5', { selector: 'span' })).toBeInTheDocument()
  expect(screen.getByText('Band 7.0')).toBeInTheDocument()
  expect(screen.getAllByText('Đang chấm').length).toBeGreaterThanOrEqual(3)
  expect(screen.queryByText('Ngữ pháp')).toBeNull()
  expect(screen.getByText('Xem chi tiết theo kỹ năng')).toBeInTheDocument()
  expect(screen.getByText('8/10')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Về bài kiểm tra' })).toHaveAttribute('href', '/bai-tap/khoa/7/kiem-tra')
})

test('bands null: không có band, số câu đúng lấy từ breakdown, bảng theo bySkill; đáp án hiện Đúng/Sai', async () => {
  open(30)
  expect(await screen.findByText('8/10 điểm')).toBeInTheDocument()
  expect(screen.queryByText(/Band 7/)).toBeNull()
  expect(screen.getByText('8/10')).toBeInTheDocument()
  expect(screen.getByText('4/5 câu đúng')).toBeInTheDocument()
  expect(screen.getByText('Câu 1 · Đúng')).toBeInTheDocument()
  expect(screen.getByText('Câu 2 · Sai')).toBeInTheDocument()
})

test('bands null trên đúng lượt này: vẫn ẩn band', async () => {
  open(21, false)
  expect(await screen.findByText('8/10 điểm')).toBeInTheDocument()
  expect(screen.getByText('4/5 câu đúng')).toBeInTheDocument()
})

test('kiểm tra chương: band lấy từ danh sách /tests', async () => {
  open(30, false, 21)
  expect(await screen.findByText('Đã nộp')).toBeInTheDocument()
  expect(screen.getByText('Band 6.5', { selector: 'span' })).toBeInTheDocument()
  expect(screen.getByText('Band 7.0')).toBeInTheDocument()
})
