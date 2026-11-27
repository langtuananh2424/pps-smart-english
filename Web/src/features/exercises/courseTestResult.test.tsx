import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'

const attempt = {
  id: 21, bankId: 9, score: 8, maxScore: 10,
  results: [{ questionId: 1, isCorrect: true }, { questionId: 2, isCorrect: false, explanation: 'Vì sao' }],
  breakdown: { correct: 8, wrong: 1, ungraded: 1, bySkill: [{ key: 'LISTENING', label: 'Nghe', answered: 5, correct: 4, accuracy: 80, ungraded: 0 }] },
}
const bands = {
  skills: { LISTENING: { correct: 4, total: 5, percent: 80, band: 6.5 }, READING: { correct: 4, total: 5, percent: 84.5, band: 7 }, WRITING: { correct: 0.6, total: 1, percent: 60, band: null }, SPEAKING: { correct: 0, total: 1, percent: 0, band: null } },
  overall: 6.5,
  gradingPending: true,
}
const bank = { id: 9, title: 'B', mode: 'EXAM', timeLimitSec: null, questions: [{ id: 2, type: 'CONNECTIVE_MCQ', prompt: 'Q', payload: {}, choices: [{ id: 22, label: 'B', content: 'However' }] }] }
const open = (lastAttemptId: number, withBands = true, chapterAttemptId?: number, att: object = attempt) => {
  mockApi({
    '/api/auth/refresh': () => json(200, auth),
    '/api/me': () => json(200, { displayName: 'Lan' }),
    '/api/me/courses': () => json(200, []),
    '/api/attempts/21': () => json(200, att),
    '/api/banks/9': () => json(200, bank),
    '/api/courses/7/tests': () => json(200, chapterAttemptId ? [{ kind: 'CHAPTER', topicId: 3, title: 'Chương 1', bankId: 5, unlocked: true, reason: null, attemptsUsed: 1, attemptsAllowed: 2, inProgressAttemptId: null, lastResult: { attemptId: chapterAttemptId, score: 8, maxScore: 10, submittedAt: 'x', bands } }] : []),
    '/api/courses/7/exam': () => json(200, { bankId: 9, progressPercent: 100, requiredPercent: 70, unlocked: true, reason: null, attemptsUsed: 1, attemptsAllowed: 2, inProgressAttemptId: null, lastResult: { attemptId: lastAttemptId, score: 8, maxScore: 10, submittedAt: 'x', bands: withBands ? bands : null } }),
  })
  renderAt('/bai-tap/khoa/7/kiem-tra/21/ket-qua')
}

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

test('lượt gần nhất: band tổng, % và band từng kỹ năng, Viết/Nói có % nhưng chưa band', async () => {
  open(21)
  expect(await screen.findByText('Đã nộp')).toBeInTheDocument()
  expect(screen.getByText('6.5', { selector: 'p' })).toBeInTheDocument() // band tổng
  expect(screen.getByText('80% · Band 6.5')).toBeInTheDocument()
  expect(screen.getByText('84.5% · Band 7.0')).toBeInTheDocument()
  expect(screen.getByText('60% · Đang chấm')).toBeInTheDocument()
  expect(screen.getByText('0% · Đang chấm')).toBeInTheDocument()
  expect(screen.getAllByText('Đang chấm').length).toBeGreaterThanOrEqual(1)
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

test('câu sai có correctAnswer thì hiện đáp án đúng bằng chữ', async () => {
  const withKey = { ...attempt, results: [{ questionId: 2, isCorrect: false, correctAnswer: { choiceId: 22 } }] }
  open(30, false, undefined, withKey)
  expect(await screen.findByText('Đáp án đúng: B. However')).toBeInTheDocument()
})

test('bands null trên đúng lượt này: vẫn ẩn band', async () => {
  open(21, false)
  expect(await screen.findByText('8/10 điểm')).toBeInTheDocument()
  expect(screen.getByText('4/5 câu đúng')).toBeInTheDocument()
})

test('kiểm tra chương: band lấy từ danh sách /tests', async () => {
  open(30, false, 21)
  expect(await screen.findByText('Đã nộp')).toBeInTheDocument()
  expect(screen.getByText('80% · Band 6.5')).toBeInTheDocument()
  expect(screen.getByText('84.5% · Band 7.0')).toBeInTheDocument()
})
