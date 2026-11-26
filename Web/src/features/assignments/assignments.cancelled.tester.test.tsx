import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import type { Assignment } from './types'

// Tester D-153: bài giao đã hủy, phía học viên (màn làm bài, bắt đầu khi bài vừa bị hủy, xem lại bài Viết đã nộp).
const inH = (h: number) => new Date(Date.now() + h * 3600_000).toISOString()
const asg = (extra: Partial<Assignment> = {}): Assignment => ({
  id: 1, classId: 3, bankId: 41, bankTitle: 'Bộ 1', questionId: null, questionType: null, dueAt: inH(72), status: 'ACTIVE', wholeClass: true, myStatus: 'NOT_SUBMITTED', ...extra,
})
const bank = { id: 41, title: 'Bộ 1', lessonId: null, isTrial: false, timeLimitSec: null,
  questions: [{ id: 1, type: 'CONNECTIVE_MCQ', prompt: 'Câu hỏi một', payload: {}, choices: [{ id: 11, label: 'A', content: 'Yes' }, { id: 12, label: 'B', content: 'No' }] }] }
const member = { '/api/auth/refresh': () => json(200, auth), '/api/me': () => json(200, { displayName: 'Lan' }) }
const cancelled409 = () => json(409, { error: { code: 'assignment_cancelled', message: 'Bài này đã được giáo viên hủy' } })
const NOTE = 'Bài này đã được giáo viên hủy'

beforeEach(() => setAccessToken('t'))
afterEach(() => vi.unstubAllGlobals())

describe('bài giao đã hủy (D-153)', () => {
  // Đợt trước LỖI, đã sửa: AssignmentRunPage hiện CancelledCard thay Player.
  test('màn làm bài của bài đã hủy: không mở Player, hiện chú thích hủy', async () => {
    mockApi({
      ...member,
      '/api/me/assignments/1': () => json(200, asg({ status: 'CANCELLED' })),
      '/api/banks/41': () => json(200, bank),
      '/api/attempts/5': () => json(200, { id: 5, bankId: 41, startedAt: inH(0) }),
      '/api/attempts/5/progress': () => json(200, { attemptId: 5, paused: false }),
    })
    renderAt('/lop/3/bai-giao/1/lam-bai/5')
    expect(await screen.findByText(NOTE)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Nộp bài' })).not.toBeInTheDocument()
  })

  test('Nghe ép: Làm lại khi bài vừa bị hủy (409 assignment_cancelled) báo lỗi, ở lại màn kết quả', async () => {
    const forced = { id: 5, bankId: 41, forced: { correctCount: 0, totalCount: 1, percent: 0, passed: false, parts: [], attemptNo: 1, hintEveryN: 3 } }
    let submitted = false
    mockApi({
      ...member,
      '/api/me/assignments/1': () => json(200, asg()),
      '/api/banks/41': () => json(200, bank),
      '/api/attempts/5': () => json(200, submitted ? { ...forced, submittedAt: inH(0) } : { id: 5, bankId: 41, startedAt: inH(0) }),
      '/api/attempts/5/progress': () => json(200, { attemptId: 5, paused: false }),
      '/api/attempts/5/submit': () => { submitted = true; return json(200, forced) },
      '/api/classes/3/assignments/1/start': cancelled409,
    })
    const router = renderAt('/lop/3/bai-giao/1/lam-bai/5')
    await userEvent.click(await screen.findByRole('radio', { name: /Yes/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Nộp bài' }))
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Nộp bài' }))
    expect(await screen.findByText('Chưa đạt yêu cầu')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Làm lại' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(NOTE)
    expect(router.state.location.pathname).toBe('/lop/3/bai-giao/1/lam-bai/5')
  })

  // Đợt trước LỖI (thấp), đã sửa: useStart tải lại cả khi assignment_cancelled.
  test('bắt đầu đúng lúc giáo viên hủy: chuyển sang thẻ đã hủy, không còn nút Bắt đầu', async () => {
    let gone = false
    mockApi({
      ...member,
      '/api/me/assignments/1': () => json(200, asg(gone ? { status: 'CANCELLED' } : {})),
      '/api/classes/3/assignments/1/start': () => { gone = true; return cancelled409() },
    })
    renderAt('/lop/3/bai-giao/1')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu làm bài' }))
    expect(await screen.findAllByText(NOTE)).not.toHaveLength(0)
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Bắt đầu làm bài' })).not.toBeInTheDocument())
  })

  // Đợt trước LỖI, đã sửa: lỗi khi viết lại báo tại chỗ, không thay màn kết quả.
  test('Viết đã nộp của bài đã hủy: bấm Viết lại không làm mất bài đã nộp', async () => {
    const grade = { taskResponse: 80, coherence: 70, lexical: 60, grammar: 50, scorePercent: 65, correctedAnswer: 'Better', feedback: 'Tốt' }
    mockApi({
      ...member,
      '/api/grammar-elements': () => json(200, []),
      '/api/me/writing?questionId=7&limit=1&assignmentId=3': () => json(200, { data: [{ sessionId: 60, questionId: 7, status: 'SUBMITTED', createdAt: 'x', assignmentId: 3 }] }),
      '/api/writing/sessions/60': () => json(200, { sessionId: 60, status: 'SUBMITTED', question: { id: 7, prompt: 'Online learning?' }, createdAt: 'x',
        turns: [{ role: 'CAM', text: 'Online learning?' }], finalText: 'Bài của tôi', gradeJobId: 9, grade }),
      '/api/writing/sessions': () => json(404, { error: { code: 'assignment_not_found', message: 'Không tìm thấy bài được giao' } }),
    })
    renderAt('/viet/7?assignmentId=3')
    const redo = await screen.findByRole('button', { name: 'Viết lại' })
    await userEvent.click(redo)
    await new Promise((r) => setTimeout(r, 80))
    expect(screen.getByText('Bài của tôi')).toBeInTheDocument()
  })

  test('Nói đã nộp của bài đã hủy: không có Nói lại, hiện chú thích hủy', async () => {
    const sub = { id: 5, questionId: 8, status: 'GRADED', createdAt: 'x', audioUrl: 'http://minio.test/a.wav', durationSec: 12,
      result: { status: 'GRADED', transcript: 'Hi', bands: { pronunciation: 6, fluency: 6, lexical: 6, grammar: 6 }, overall: 6, feedback: 'Ok', suggestions: [], mispronounced: [] } }
    mockApi({
      ...member,
      '/api/me/assignments/3': () => json(200, asg({ id: 3, questionId: 8, questionType: 'SPEAKING', status: 'CANCELLED' })),
      '/api/speaking/questions/8?assignmentId=3': () => json(200, { id: 8, part: 2, prompt: 'Describe a trip.', cueCard: ['where'], maxSeconds: 60 }),
      '/api/me/speaking?questionId=8&limit=1&assignmentId=3': () => json(200, { data: [sub] }),
      '/api/speaking/submissions/5': () => json(200, sub),
    })
    renderAt('/noi/8?assignmentId=3')
    expect(await screen.findByText(NOTE)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Nói lại' })).not.toBeInTheDocument()
  })

  test('Viết đã nộp của bài đã hủy: không có Viết lại, hiện chú thích hủy', async () => {
    const grade = { taskResponse: 80, coherence: 70, lexical: 60, grammar: 50, scorePercent: 65, correctedAnswer: 'Better', feedback: 'Tốt' }
    let creates = 0
    mockApi({
      ...member,
      '/api/grammar-elements': () => json(200, []),
      '/api/me/assignments/3': () => json(200, asg({ id: 3, bankId: null, questionId: 7, questionType: 'WRITING', status: 'CANCELLED' })),
      '/api/me/writing?questionId=7&limit=1&assignmentId=3': () => json(200, { data: [{ sessionId: 60, questionId: 7, status: 'SUBMITTED', createdAt: 'x', assignmentId: 3 }] }),
      '/api/writing/sessions/60': () => json(200, { sessionId: 60, status: 'SUBMITTED', question: { id: 7, prompt: 'Online learning?' }, createdAt: 'x',
        turns: [{ role: 'CAM', text: 'Online learning?' }], finalText: 'Bài của tôi', gradeJobId: 9, grade }),
      '/api/writing/sessions': () => { creates++; return json(404, { error: { code: 'assignment_not_found', message: 'x' } }) },
    })
    renderAt('/viet/7?assignmentId=3')
    expect(await screen.findByText(NOTE)).toBeInTheDocument()
    expect(screen.getByText('Bài của tôi')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Viết lại' })).not.toBeInTheDocument()
    expect(creates).toBe(0)
  })
})
