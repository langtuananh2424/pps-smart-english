import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import { gradeSchema } from './GradeForm'
import { asg, cls, detail, row } from './fixtures'

// Tester #61/#62: dữ liệu giả theo đúng hợp đồng backend (AssignmentView, AssignableBank) thay cho fixture rút gọn.
// Backend: bài giao câu Viết/Nói vẫn có bankId và bankTitle của bộ chứa câu (AssignmentService.views); AssignableBank.kind = mode của bộ (PRACTICE|EXAM).
const err = (status: number, code: string, message: string) => json(status, { error: { code, message } })
const aiAsg = (type: 'WRITING' | 'SPEAKING', o = {}) => asg({ id: 5, bankId: 41, bankTitle: 'Bộ Unit 1', questionId: 77, questionType: type, mode: null, ...o })
const realTree = { courseId: 9, topics: [
  { topicId: 1, title: 'Unit 1', position: 0, banks: [{ bankId: 41, title: 'Bộ luyện Unit 1', kind: 'PRACTICE', questionCount: 10 }, { bankId: 42, title: 'Kiểm tra chương 1', kind: 'EXAM', questionCount: 20 }], aiQuestions: [{ questionId: 77, type: 'WRITING', title: 'Viết thư' }] },
] }
const base = {
  '/api/auth/refresh': () => json(200, auth),
  '/api/classes/3': () => json(200, detail),
  '/api/classes/3/assignable': () => json(200, realTree),
  '/api/classes/3/assignments/5/progress': () => json(200, [row()]),
  '/api/classes/3/assignments/5/attempts/91/grades': () => json(200, []),
}

beforeEach(() => setAccessToken('t'))
afterEach(() => vi.unstubAllGlobals())

describe('hợp đồng backend thật', () => {
  test('bài giao câu Viết (bankId khác null): không hiện chọn chế độ (backend 400 "Chỉ đặt chế độ khi giao cả bộ")', async () => {
    mockApi({ ...base, '/api/classes/3/assignments': () => json(200, [aiAsg('WRITING')]) })
    renderAt('/giang-day/lop/3/bai-giao/5')
    expect(await screen.findByText('Đã nộp')).toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'Chế độ làm bài' })).not.toBeInTheDocument()
  })

  test('bài giao câu Viết hiện là bài Viết, không trùng tên với bài giao cả bộ', async () => {
    mockApi({ ...base, '/api/classes/3/assignments': () => json(200, [aiAsg('WRITING'), asg({ id: 6 })]) })
    renderAt('/giang-day/lop/3/bai-giao')
    const rows = await screen.findAllByRole('row')
    const titles = rows.slice(1).map((r) => within(r).getAllByRole('cell')[0].textContent)
    expect(new Set(titles).size).toBe(2)
  })

  test('bước 1: nhãn loại bộ là tiếng Việt, không lộ mã PRACTICE/EXAM', async () => {
    mockApi({ ...base, '/api/classes/3/assignments': () => json(200, []) })
    renderAt('/giang-day/lop/3/giao-bai')
    await screen.findByRole('radio', { name: /Bộ luyện Unit 1/ })
    expect(document.body.textContent).not.toMatch(/PRACTICE|EXAM/)
  })

  test('chọn bộ thi, không đổi chế độ: không gửi mode PRACTICE (backend 400 "Bộ thi không giao ở chế độ Luyện tập được")', async () => {
    let body: any
    mockApi({ ...base, '/api/classes/3/assignments': (b, i) => (i?.method === 'POST' ? ((body = b), json(201, asg())) : json(200, [])) })
    const user = userEvent.setup()
    renderAt('/giang-day/lop/3/giao-bai')
    await user.click(await screen.findByRole('radio', { name: /Kiểm tra chương 1/ }))
    await user.click(screen.getByRole('button', { name: 'Tiếp tục' }))
    fireEvent.change(await screen.findByLabelText('Hạn nộp (ngày)'), { target: { value: '2099-01-05' } })
    await user.click(screen.getByRole('button', { name: 'Xem lại' }))
    await user.click(screen.getByRole('button', { name: 'Giao bài' }))
    await waitFor(() => expect(body).toBeDefined())
    expect(body.mode).not.toBe('PRACTICE')
  })
})

describe('giao bài: chặn gửi trùng', () => {
  test('đang gửi thì nút Giao bài bị khóa', async () => {
    let posts = 0
    mockApi({ ...base, '/api/classes/3/assignments': (_b, i) => (i?.method === 'POST' ? (posts++, new Promise(() => {})) : json(200, [])) as any })
    const user = userEvent.setup()
    renderAt('/giang-day/lop/3/giao-bai')
    await user.click(await screen.findByRole('radio', { name: /Bộ luyện Unit 1/ }))
    await user.click(screen.getByRole('button', { name: 'Tiếp tục' }))
    fireEvent.change(await screen.findByLabelText('Hạn nộp (ngày)'), { target: { value: '2099-01-05' } })
    await user.click(screen.getByRole('button', { name: 'Xem lại' }))
    const send = screen.getByRole('button', { name: 'Giao bài' })
    await user.click(send)
    await waitFor(() => expect(send).toBeDisabled())
    await user.click(send)
    expect(posts).toBe(1)
  })

  test('chọn học viên mà chưa tick ai: không qua được bước 2', async () => {
    mockApi({ ...base, '/api/classes/3/assignments': () => json(200, []) })
    const user = userEvent.setup()
    renderAt('/giang-day/lop/3/giao-bai')
    await user.click(await screen.findByRole('radio', { name: /Bộ luyện Unit 1/ }))
    await user.click(screen.getByRole('button', { name: 'Tiếp tục' }))
    fireEvent.change(await screen.findByLabelText('Hạn nộp (ngày)'), { target: { value: '2099-01-05' } })
    await user.click(screen.getByRole('radio', { name: 'Chọn học viên' }))
    expect(screen.getByRole('button', { name: 'Xem lại' })).toBeDisabled()
  })
})

describe('thang điểm giáo viên (D-166, D-168, D-170)', () => {
  const ok = (max: number | null, speaking: boolean, score: string, comment = '') => gradeSchema(max, speaking).safeParse({ score, comment }).success
  test.each([
    [100, false, '85.25', true],
    [100, false, '85,5', true],
    [100, false, '85.255', false],
    [100, false, '-1', false],
    [100, false, '1e2', false],
    [100, false, '100', true],
    [100, false, '100.01', false],
    [9, true, '8,5', true],
    [9, true, '9', true],
    [9, true, '9.5', false],
    [9, true, '6.25', false],
    [9, true, '0', true],
    [null, true, '9.5', false],
  ])('max=%s nói=%s điểm "%s" hợp lệ=%s', (max, speaking, score, valid) => {
    expect(ok(max as number | null, speaking as boolean, score as string)).toBe(valid)
  })
  test('chỉ nhận xét (không điểm) hợp lệ; nhận xét quá 4000 ký tự bị chặn', () => {
    expect(ok(100, false, '', 'Tốt')).toBe(true)
    expect(ok(100, false, '', 'x'.repeat(4001))).toBe(false)
    expect(ok(100, false, '   ', '   ')).toBe(false)
  })
})

describe('Nghe ép (D-142)', () => {
  test('lượt Nghe ép: không mời nhập điểm (backend 409 grade_not_supported cho mọi lần chấm)', async () => {
    mockApi({ ...base, '/api/classes/3/assignments': () => json(200, [asg()]), '/api/classes/3/assignments/5/progress': () => json(200, [row({ forced: { plays: 2 }, aiScore: null, finalScore: null, maxScore: null })]) })
    renderAt('/giang-day/lop/3/bai-giao/5/bai-nop/91')
    expect(await screen.findByText('Chấm của giáo viên')).toBeInTheDocument()
    expect(screen.queryByLabelText('Điểm mới')).not.toBeInTheDocument()
  })
})

describe('người không phải nhân sự', () => {
  test.each([403, 500])('/me/permissions lỗi %s: điều hướng chính vẫn hiện, không có Giảng dạy, không hiện lỗi', async (code) => {
    mockApi({ '/api/auth/refresh': () => json(200, auth), '/api/me/permissions': () => err(code, 'forbidden', 'x'), '/api/me': () => json(200, { displayName: 'Lan' }) })
    renderAt('/ho-so')
    const nav = await screen.findByRole('navigation', { name: 'Điều hướng chính' })
    expect(within(nav).getAllByRole('link').length).toBe(5)
    expect(nav.querySelector('a[href="/giang-day"]')).toBeNull()
  })

  test('học viên mở /giang-day: thấy trạng thái trống, không vỡ trang', async () => {
    mockApi({ '/api/auth/refresh': () => json(200, auth), '/api/teaching/classes': () => json(200, []) })
    renderAt('/giang-day')
    expect(await screen.findByText('Bạn chưa được phân công lớp nào.')).toBeInTheDocument()
  })

  test('học viên mở lớp không phụ trách: 404 hiện thông báo, không lộ dữ liệu', async () => {
    const nf = () => err(404, 'class_not_found', 'Không tìm thấy lớp')
    mockApi({ '/api/auth/refresh': () => json(200, auth), '/api/classes/3': nf, '/api/classes/3/assignable': nf, '/api/classes/3/assignments': nf })
    renderAt('/giang-day/lop/3/giao-bai')
    expect(await screen.findByRole('alert')).toHaveTextContent('Không tìm thấy lớp')
  })
})

describe('không có chữ AI trên giao diện (quy ước Cam)', () => {
  test.each([
    ['/giang-day/lop/3/bai-giao/5', 'WRITING'],
    ['/giang-day/lop/3/bai-giao/5/bai-nop/91', 'WRITING'],
    ['/giang-day/lop/3/bai-giao', 'SPEAKING'],
    ['/giang-day/lop/3/giao-bai', 'WRITING'],
  ])('%s', async (path, type) => {
    mockApi({ ...base, '/api/teaching/classes': () => json(200, [cls]), '/api/classes/3/assignments': () => json(200, [aiAsg(type as 'WRITING')]),
      '/api/classes/3/assignments/5/submissions/91': () => json(200, { assignmentId: 5, userId: 21, questionType: 'WRITING', submittedAt: '', writing: { sessionId: 1, status: 'SUBMITTED', question: { id: 77, prompt: 'x' }, turns: [], finalText: 'Bài', grade: null, createdAt: '' } }) })
    renderAt(path)
    await screen.findAllByRole('heading')
    await waitFor(() => expect(screen.queryByText('Đang tải...')).not.toBeInTheDocument())
    expect(document.body.textContent).not.toMatch(/\bAI\b/)
  })
})
