import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { apiError, auth, json, mockApi, renderAt } from '../../testUtils'
import type { Assignment, MyResult } from './types'
import { statusOf } from './view'

// FR-ASG-02, FR-ASG-03: màn Bài được giao của học viên.
const inH = (h: number) => new Date(Date.now() + h * 3600_000).toISOString()
const asg = (id: number, extra: Partial<Assignment> = {}): Assignment => ({
  id, classId: 3, bankId: 40 + id, bankTitle: `Bộ ${id}`, questionId: null, questionType: null, dueAt: inH(72), status: 'OPEN', wholeClass: true, myStatus: 'NOT_SUBMITTED', ...extra,
})
const list = [
  asg(1, { dueAt: inH(5) }),
  asg(2, { myStatus: 'OVERDUE', dueAt: inH(-5) }),
  asg(3, { bankId: null, bankTitle: null, questionId: 7, questionType: 'WRITING' }),
  asg(4, { myStatus: 'SUBMITTED' }),
  asg(5, { myStatus: 'LATE' }),
]
const member = { '/api/auth/refresh': () => json(200, auth), '/api/me': () => json(200, { displayName: 'Lan' }) }
// Màn chi tiết lấy một bài qua /api/me/assignments/{id}
const one = (a: Assignment) => ({ [`/api/me/assignments/${a.id}`]: () => json(200, a) })
const result = (extra: Partial<MyResult> = {}): MyResult => ({
  assignmentId: 4, status: 'SUBMITTED', attemptId: 99, submittedAt: inH(-1), aiScore: 7, maxScore: 10, finalScore: 7, teacherComment: null, ...extra,
})

describe('trạng thái hiển thị', () => {
  test('sắp đến hạn khi còn dưới 24 giờ', () => {
    expect(statusOf(asg(1, { dueAt: inH(5) })).label).toBe('Sắp đến hạn')
    expect(statusOf(asg(1, { dueAt: inH(30) })).label).toBe('Chưa làm')
  })
})

describe('Bài đã hủy (D-153)', () => {
  beforeEach(() => setAccessToken('t'))
  afterEach(() => vi.unstubAllGlobals())

  test('chưa nộp: chỉ có chú thích và nút về danh sách', async () => {
    mockApi({ ...member, ...one(asg(1, { status: 'CANCELLED' })) })
    renderAt('/lop/3/bai-giao/1')
    expect(await screen.findByText('Bài này đã được giáo viên hủy')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Bắt đầu làm bài' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Về bài được giao' })).toBeInTheDocument()
  })

  test('đã nộp: vẫn xem kết quả kèm chú thích, không có Làm lại', async () => {
    mockApi({ ...member, ...one(asg(4, { status: 'CANCELLED', myStatus: 'SUBMITTED' })),
      '/api/classes/3/assignments/4/mine': () => json(200, result({ finalScore: null, aiScore: null, maxScore: null })) })
    renderAt('/lop/3/bai-giao/4')
    expect(await screen.findByText('Bài này đã được giáo viên hủy')).toBeInTheDocument()
    expect(screen.getByText('Kết quả bài được giao')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Làm lại' })).not.toBeInTheDocument()
  })
})

describe('Bài được giao', () => {
  beforeEach(() => setAccessToken('t'))
  afterEach(() => vi.unstubAllGlobals())

  test('chia tab theo myStatus, chip Quá hạn và Nộp trễ', async () => {
    mockApi({ ...member, '/api/me/assignments': () => json(200, list) })
    renderAt('/bai-giao')
    expect(await screen.findByText('Bộ 1')).toBeInTheDocument()
    expect(screen.getByText('Quá hạn')).toBeInTheDocument()
    expect(screen.getByText('Sắp đến hạn')).toBeInTheDocument()
    expect(screen.getByText('Bài viết được giao')).toBeInTheDocument()
    expect(screen.queryByText('Bộ 4')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('tab', { name: 'Đã nộp' }))
    expect(screen.getByText('Bộ 4')).toBeInTheDocument()
    expect(screen.getByText('Nộp trễ')).toBeInTheDocument()
    expect(screen.queryByText('Bộ 1')).not.toBeInTheDocument()
  })

  test('danh sách trống', async () => {
    mockApi({ ...member, '/api/me/assignments': () => json(200, []) })
    renderAt('/bai-giao')
    expect(await screen.findByText('Chưa có bài nào cần làm.')).toBeInTheDocument()
  })

  test('bài bộ câu hỏi: bắt đầu gọi /start rồi sang màn làm bài', async () => {
    const calls = mockApi({ ...member, ...one(list[0]), '/api/classes/3/assignments/1/start': () => json(201, { id: 55, bankId: 41, startedAt: inH(0), timeLimitSec: null }) })
    const router = renderAt('/lop/3/bai-giao/1')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu làm bài' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/lop/3/bai-giao/1/lam-bai/55'))
    expect(calls).toContain('/api/classes/3/assignments/1/start')
  })

  test('đang có lượt dở thì làm tiếp lượt đó', async () => {
    mockApi({ ...member, ...one(list[0]), '/api/classes/3/assignments/1/start': () => json(409, { error: { code: 'attempt_in_progress', message: 'm', details: { attemptId: 70 } } }) })
    const router = renderAt('/lop/3/bai-giao/1')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu làm bài' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/lop/3/bai-giao/1/lam-bai/70'))
  })

  test('màn Viết truyền assignmentId khi tạo phiên', async () => {
    let body: unknown
    mockApi({ ...member, '/api/me/writing?questionId=7&limit=1&assignmentId=3': () => json(200, { data: [] }), '/api/writing/sessions': (b) => { body = b; return json(404) } })
    renderAt('/viet/7?assignmentId=3')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu viết' }))
    await waitFor(() => expect(body).toMatchObject({ questionId: 7, assignmentId: 3 }))
  })

  test('câu Viết chuyển sang /viet kèm assignmentId và báo không trừ lượt AI', async () => {
    mockApi({ ...member, ...one(list[2]) })
    const router = renderAt('/lop/3/bai-giao/3')
    expect(await screen.findByText('Bài này không trừ lượt AI tuần của bạn.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('link', { name: 'Bắt đầu làm bài' }))
    expect(router.state.location.pathname).toBe('/viet/7')
    expect(router.state.location.search).toBe('?assignmentId=3')
  })

  test('quá hạn vẫn mở được và báo Nộp trễ', async () => {
    mockApi({ ...member, ...one(list[1]) })
    renderAt('/lop/3/bai-giao/2')
    expect(await screen.findByText(/đánh dấu Nộp trễ/)).toBeInTheDocument()
  })

  test('kết quả có điểm cuối và nhận xét giáo viên', async () => {
    mockApi({ ...member, ...one(list[3]), '/api/classes/3/assignments/4/mine': () => json(200, result({ aiScore: 6, finalScore: 8, teacherComment: 'Làm tốt' })) })
    renderAt('/lop/3/bai-giao/4')
    expect(await screen.findByText('Điểm cuối')).toBeInTheDocument()
    expect(screen.getByText('8/10')).toBeInTheDocument()
    expect(screen.getByText('6/10')).toBeInTheDocument()
    expect(screen.getByText('Làm tốt')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Xem bài đã nộp' })).toHaveAttribute('href', '/bai-tap/44/ket-qua/99')
  })

  test('chưa có nhận xét: chờ giáo viên', async () => {
    mockApi({ ...member, ...one(list[3]), '/api/classes/3/assignments/4/mine': () => json(200, result()) })
    renderAt('/lop/3/bai-giao/4')
    expect(await screen.findByText('Chờ giáo viên nhận xét.')).toBeInTheDocument()
    expect(screen.getByText('Điểm cuối')).toBeInTheDocument() // có điểm thì luôn là Điểm cuối
  })

  test('còn câu AI chấm dở: ẩn điểm', async () => {
    mockApi({ ...member, ...one(list[3]), '/api/classes/3/assignments/4/mine': () => json(200, result({ aiScore: null, finalScore: null })) })
    renderAt('/lop/3/bai-giao/4')
    expect(await screen.findByText('Đang chấm…')).toBeInTheDocument()
  })

  test('bài không có trong danh sách: không tìm thấy', async () => {
    mockApi({ ...member, '/api/me/assignments': () => json(200, list) })
    renderAt('/lop/3/bai-giao/999')
    expect(await screen.findByText('Không tìm thấy bài được giao')).toBeInTheDocument()
  })
})
