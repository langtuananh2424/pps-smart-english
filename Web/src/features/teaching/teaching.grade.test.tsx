import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import { asg, detail, row } from './fixtures'

const err = (status: number, code: string, message: string) => json(status, { error: { code, message } })
const grade = { id: 1, attemptId: 91, score: 80, comment: 'Tốt', gradedBy: 7, gradedAt: '2026-11-22T03:00:00Z' }
const writing = { assignmentId: 5, userId: 21, questionType: 'WRITING', submittedAt: '2026-11-21T03:00:00Z', writing: { sessionId: 1, status: 'SUBMITTED', question: { id: 77, prompt: 'Viết thư' }, turns: [], finalText: 'Dear sir, hello', grade: { scorePercent: 70, feedback: 'Cần mạch lạc hơn' }, createdAt: '' } }
const base = {
  '/api/auth/refresh': () => json(200, auth),
  '/api/classes/3': () => json(200, detail),
  '/api/classes/3/assignments': () => json(200, [asg({ id: 5, bankId: null, bankTitle: null, questionId: 77, questionType: 'WRITING', mode: null })]),
  '/api/classes/3/assignments/5/progress': () => json(200, [row()]),
  '/api/classes/3/assignments/5/attempts/91/grades': () => json(200, [grade]),
  '/api/classes/3/assignments/5/submissions/91': () => json(200, writing),
}
const URL = '/giang-day/lop/3/bai-giao/5/bai-nop/91'

beforeEach(() => setAccessToken('t'))
afterEach(() => vi.unstubAllGlobals())

describe('tiến độ nộp bài (FR-ASG-04)', () => {
  const rows = [row(), row({ userId: 22, displayName: 'Nga', status: 'SUBMITTED', attemptId: 92, aiScore: null, finalScore: null }), row({ userId: 23, displayName: 'Hà', status: 'OVERDUE', attemptId: null, aiScore: null, finalScore: null })]

  test('trạng thái, điểm Cam, đang chấm; hủy bài giao có hộp xác nhận', async () => {
    let cancelled = false
    mockApi({ ...base, '/api/classes/3/assignments': () => json(200, [asg({ status: cancelled ? 'CANCELLED' : 'ACTIVE' })]), '/api/classes/3/assignments/5/progress': () => json(200, rows),
      '/api/classes/3/assignments/5/cancel': () => ((cancelled = true), json(200, asg({ status: 'CANCELLED' }))) })
    const user = userEvent.setup()
    renderAt('/giang-day/lop/3/bai-giao/5')
    expect(await screen.findByText('Quá hạn')).toBeInTheDocument()
    expect(screen.getByText('Cam đang chấm')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Mở bài nộp của Minh' })).toHaveAttribute('href', '/giang-day/lop/3/bai-giao/5/bai-nop/91')
    await user.click(screen.getByRole('button', { name: 'Hủy bài giao' }))
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Hủy bài giao' }))
    expect(await screen.findByText('Đã hủy')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Hủy bài giao' })).not.toBeInTheDocument()
  })

  test('bộ câu: đổi chế độ khi chưa có lượt; 409 assignment_mode_locked báo lỗi', async () => {
    let body: any
    mockApi({ ...base, '/api/classes/3/assignments': () => json(200, [asg()]), '/api/classes/3/assignments/5/progress': () => json(200, [row({ attemptId: null, status: 'NOT_SUBMITTED' })]),
      '/api/classes/3/assignments/5': (b) => ((body = b), err(409, 'assignment_mode_locked', 'x')) })
    renderAt('/giang-day/lop/3/bai-giao/5')
    await userEvent.click(await screen.findByRole('radio', { name: 'Thi' }))
    expect(body).toEqual({ mode: 'EXAM' })
    expect(await screen.findByText('Đã có học viên bắt đầu làm, không đổi chế độ được.')).toBeInTheDocument()
  })
})

describe('chấm bài (FR-ASG-03)', () => {
  test('hiện bài viết, nhận xét của Cam và lịch sử; lưu gửi PUT đúng body', async () => {
    let body: any
    mockApi({ ...base, '/api/classes/3/assignments/5/attempts/91/grade': (b) => ((body = b), json(201, grade)) })
    const user = userEvent.setup()
    renderAt(URL)
    expect(await screen.findByText('Dear sir, hello')).toBeInTheDocument()
    expect(screen.getByText('Cần mạch lạc hơn')).toBeInTheDocument()
    expect(await screen.findByText('Bản hiện tại')).toBeInTheDocument()
    expect(screen.getByText('Cô Hoa · ' + new Date('2026-11-22T03:00:00Z').toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric' }))).toBeInTheDocument()
    expect(screen.getByText('Điểm Cam chấm · Kết quả ban đầu')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Điểm mới'), '85,5')
    await user.type(screen.getByLabelText('Nhận xét'), 'Khá')
    await user.click(screen.getByRole('button', { name: 'Lưu nhận xét' }))
    await waitFor(() => expect(body).toEqual({ score: 85.5, comment: 'Khá' }))
    expect(await screen.findByText('Đã lưu nhận xét.')).toBeInTheDocument()
  })

  test('trống cả điểm lẫn nhận xét, hoặc điểm quá thang: chặn, không gọi PUT', async () => {
    let puts = 0
    mockApi({ ...base, '/api/classes/3/assignments/5/attempts/91/grade': () => (puts++, json(201, grade)) })
    const user = userEvent.setup()
    renderAt(URL)
    await user.click(await screen.findByRole('button', { name: 'Lưu nhận xét' }))
    expect(await screen.findByText('Nhập điểm mới hoặc nhận xét')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Điểm mới'), '101')
    await user.click(screen.getByRole('button', { name: 'Lưu nhận xét' }))
    expect(await screen.findByText('Điểm tối đa 100')).toBeInTheDocument()
    expect(puts).toBe(0)
  })

  test('Nói: điểm theo bước 0,5, có trình phát; bản ghi đã xóa hiện thông báo', async () => {
    let puts = 0
    const speaking = { assignmentId: 5, userId: 21, questionType: 'SPEAKING', submittedAt: '', audioDeleted: true, speaking: { id: 3, questionId: 77, status: 'GRADED', result: { status: 'GRADED', transcript: 'I like tea', overall: 6, feedback: 'Phát âm tốt' }, audioUrl: null, createdAt: '' } }
    mockApi({ ...base, '/api/classes/3/assignments': () => json(200, [asg({ bankId: null, questionId: 77, questionType: 'SPEAKING', mode: null })]),
      '/api/classes/3/assignments/5/progress': () => json(200, [row({ maxScore: 9, aiScore: 6, finalScore: 6 })]),
      '/api/classes/3/assignments/5/submissions/91': () => json(200, speaking), '/api/classes/3/assignments/5/attempts/91/grade': () => (puts++, json(201, grade)) })
    const user = userEvent.setup()
    renderAt(URL)
    expect(await screen.findByText('Bản ghi đã xóa')).toBeInTheDocument()
    expect(screen.getByText('I like tea')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Điểm mới'), '6.3')
    await user.click(screen.getByRole('button', { name: 'Lưu nhận xét' }))
    expect(await screen.findByText('Điểm Nói theo bước 0,5')).toBeInTheDocument()
    expect(puts).toBe(0)
  })

  test('bài đã hủy: form chỉ đọc', async () => {
    mockApi({ ...base, '/api/classes/3/assignments': () => json(200, [asg({ status: 'CANCELLED', bankId: null, questionId: 77, questionType: 'WRITING', mode: null })]) })
    renderAt(URL)
    expect(await screen.findByText(/chỉ xem, không chấm thêm được/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Lưu nhận xét' })).not.toBeInTheDocument()
  })

  test('409 assignment_cancelled khi lưu: chuyển sang chỉ đọc; 409 grade_not_supported báo lỗi', async () => {
    let code = 'assignment_cancelled'
    mockApi({ ...base, '/api/classes/3/assignments/5/attempts/91/grade': () => err(409, code, 'x') })
    const user = userEvent.setup()
    renderAt(URL)
    await user.type(await screen.findByLabelText('Nhận xét'), 'Ok')
    await user.click(screen.getByRole('button', { name: 'Lưu nhận xét' }))
    expect(await screen.findByText(/chỉ xem, không chấm thêm được/)).toBeInTheDocument()
    expect(code).toBe('assignment_cancelled')
  })

  test('Nghe ép: grade_not_supported báo lỗi', async () => {
    mockApi({ ...base, '/api/classes/3/assignments/5/attempts/91/grade': () => err(409, 'grade_not_supported', 'x') })
    const user = userEvent.setup()
    renderAt(URL)
    await user.type(await screen.findByLabelText('Nhận xét'), 'Ok')
    await user.click(screen.getByRole('button', { name: 'Lưu nhận xét' }))
    expect(await screen.findByText('Bài giao Nghe ép không có điểm nên không sửa điểm được.')).toBeInTheDocument()
  })

  test('bộ câu: ghi chú chưa xem được câu trả lời, vẫn chấm được', async () => {
    mockApi({ ...base, '/api/classes/3/assignments': () => json(200, [asg()]) })
    renderAt(URL)
    expect(await screen.findByText('Chưa xem được từng câu trả lời ở đây.')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Lưu nhận xét' })).toBeInTheDocument()
  })
})
