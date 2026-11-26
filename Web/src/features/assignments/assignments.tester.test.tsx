import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import { createSubmission, fetchQuestion } from '../speaking/api'
import type { Assignment, MyResult } from './types'

// Tester đợt #61/#53: điểm ghép bài giao ↔ Viết/Nói, 409 khi bắt đầu, Nghe ép, phân quyền theo URL, XSS.
const inH = (h: number) => new Date(Date.now() + h * 3600_000).toISOString()
const asg = (id: number, extra: Partial<Assignment> = {}): Assignment => ({
  id, classId: 3, bankId: 40 + id, bankTitle: `Bộ ${id}`, questionId: null, questionType: null, dueAt: inH(72), status: 'OPEN', wholeClass: true, myStatus: 'NOT_SUBMITTED', ...extra,
})
const member = { '/api/auth/refresh': () => json(200, auth), '/api/me': () => json(200, { displayName: 'Lan' }) }
const result = (extra: Partial<MyResult> = {}): MyResult => ({
  assignmentId: 1, status: 'SUBMITTED', attemptId: 99, submittedAt: inH(-1), aiScore: 7, maxScore: 10, finalScore: 7, teacherComment: null, ...extra,
})
const conflict = (code: string, details?: unknown) => () => json(409, { error: { code, message: `msg ${code}`, details } })
const one = (a: Assignment) => ({ [`/api/me/assignments/${a.id}`]: () => json(200, a) })
const settle = () => new Promise((r) => setTimeout(r, 80))
const grade = { taskResponse: 80, coherence: 70, lexical: 60, grammar: 50, scorePercent: 65, correctedAnswer: 'Better', feedback: 'Tốt' }
const wsession = (id: number, over = {}) => ({ sessionId: id, status: 'ACTIVE', question: { id: 7, prompt: 'Online learning?' }, createdAt: 'x', turns: [{ role: 'CAM', text: 'Online learning?' }], ...over })

beforeEach(() => setAccessToken('t'))
afterEach(() => vi.unstubAllGlobals())

describe('bắt đầu bài bộ câu hỏi: lỗi 409', () => {
  test('assignment_submitted: báo lỗi, không chuyển màn, tải lại danh sách', async () => {
    const calls = mockApi({ ...member, ...one(asg(1)), '/api/classes/3/assignments/1/start': conflict('assignment_submitted') })
    const router = renderAt('/lop/3/bai-giao/1')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu làm bài' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('msg assignment_submitted')
    expect(router.state.location.pathname).toBe('/lop/3/bai-giao/1')
    await waitFor(() => expect(calls.filter((c) => c === '/api/me/assignments/1').length).toBeGreaterThan(1))
  })

  test('assignment_not_attemptable: báo lỗi, không chuyển màn', async () => {
    mockApi({ ...member, ...one(asg(1)), '/api/classes/3/assignments/1/start': conflict('assignment_not_attemptable') })
    const router = renderAt('/lop/3/bai-giao/1')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu làm bài' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('msg assignment_not_attemptable')
    expect(router.state.location.pathname).toBe('/lop/3/bai-giao/1')
  })

  test('attempt_in_progress thiếu attemptId: báo lỗi chứ không chuyển sang lam-bai/undefined', async () => {
    mockApi({ ...member, ...one(asg(1)), '/api/classes/3/assignments/1/start': conflict('attempt_in_progress') })
    const router = renderAt('/lop/3/bai-giao/1')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu làm bài' }))
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/lop/3/bai-giao/1')
  })
})

describe('Nghe ép (D-143)', () => {
  test('lượt chỉ đếm câu đúng: không điểm, có Làm lại gọi /start', async () => {
    const calls = mockApi({
      ...member,
      ...one(asg(1, { myStatus: 'SUBMITTED' })),
      '/api/classes/3/assignments/1/mine': () => json(200, result({ aiScore: null, maxScore: null, finalScore: null })),
      '/api/classes/3/assignments/1/start': () => json(200, { id: 77, bankId: 41, startedAt: inH(0), timeLimitSec: null }),
    })
    const router = renderAt('/lop/3/bai-giao/1')
    expect(await screen.findByText('Bài này không tính điểm.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Làm lại' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/lop/3/bai-giao/1/lam-bai/77'))
    expect(calls).toContain('/api/classes/3/assignments/1/start')
  })

  test('bài thường đã nộp có điểm: không có Làm lại', async () => {
    mockApi({ ...member, ...one(asg(1, { myStatus: 'SUBMITTED' })), '/api/classes/3/assignments/1/mine': () => json(200, result()) })
    renderAt('/lop/3/bai-giao/1')
    expect(await screen.findByText('7/10')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Làm lại' })).not.toBeInTheDocument()
  })
})

describe('điểm ẩn khi AI còn chấm (D-147 mục 6)', () => {
  test('giáo viên đã cho điểm trong lúc AI chấm: hiện điểm cuối, không hiện điểm AI', async () => {
    mockApi({
      ...member,
      ...one(asg(1, { myStatus: 'SUBMITTED' })),
      '/api/classes/3/assignments/1/mine': () => json(200, result({ aiScore: null, finalScore: 9 })),
    })
    renderAt('/lop/3/bai-giao/1')
    expect(await screen.findByText('Điểm cuối')).toBeInTheDocument()
    expect(screen.getByText('9/10')).toBeInTheDocument()
    expect(screen.queryByText('Điểm AI ban đầu')).not.toBeInTheDocument()
  })

  test('đang chấm: không có con số điểm nào trên màn, không có Làm lại', async () => {
    mockApi({
      ...member,
      ...one(asg(1, { myStatus: 'SUBMITTED' })),
      '/api/classes/3/assignments/1/mine': () => json(200, result({ aiScore: null, finalScore: null })),
    })
    renderAt('/lop/3/bai-giao/1')
    expect(await screen.findByText('Đang chấm…')).toBeInTheDocument()
    expect(screen.queryByText(/\d+\/10/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Làm lại' })).not.toBeInTheDocument()
  })
})

describe('phân quyền theo URL và tham số không hợp lệ', () => {
  test('đúng id bài nhưng lớp khác: không tìm thấy, không gọi /start hay /mine', async () => {
    const calls = mockApi({ ...member, ...one(asg(1, { myStatus: 'SUBMITTED' })) })
    renderAt('/lop/9/bai-giao/1')
    expect(await screen.findByText('Không tìm thấy bài được giao')).toBeInTheDocument()
    expect(calls.some((c) => c.includes('/classes/9/'))).toBe(false)
  })

  test.each(['/lop/abc/bai-giao/1', '/lop/3/bai-giao/-1', '/lop/3/bai-giao/1.5'])('%s: không tìm thấy, không gọi API theo id', async (path) => {
    const calls = mockApi({ ...member, '/api/me/assignments': () => json(200, [asg(1)]) })
    renderAt(path)
    expect(await screen.findByText('Không tìm thấy bài được giao')).toBeInTheDocument()
    expect(calls.some((c) => c.includes('/assignments/') && c !== '/api/me/assignments')).toBe(false)
  })

  test('màn làm bài với bài không thuộc mình: về danh sách', async () => {
    // lượt 5 là của chính học viên (lượt của người khác backend trả 404 và màn hiện thẻ lỗi trước khi xét bài giao)
    mockApi({ ...member, '/api/me/assignments': () => json(200, [asg(1)]), '/api/attempts/5': () => json(200, { id: 5, bankId: 41, startedAt: inH(0) }) })
    const router = renderAt('/lop/3/bai-giao/999/lam-bai/5')
    await waitFor(() => expect(router.state.location.pathname).toBe('/bai-giao'))
  })

  test('bài cũ hoặc đã hủy (không còn trong /me/assignments) mở từ thông báo: báo không tìm thấy kèm đường về', async () => {
    mockApi({ ...member, '/api/me/assignments': () => json(200, []) })
    renderAt('/lop/3/bai-giao/1')
    expect(await screen.findByText('Không tìm thấy bài được giao')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Về bài được giao' })).toHaveAttribute('href', '/bai-giao')
  })
})

describe('XSS: dữ liệu giáo viên hiển thị dạng chữ', () => {
  const evil = '<img src=x onerror="window.__xss=1">'
  test('bankTitle ở danh sách và teacherComment ở kết quả không thành thẻ HTML', async () => {
    mockApi({
      ...member,
      '/api/me/assignments': () => json(200, [asg(1, { bankTitle: evil, myStatus: 'SUBMITTED' })]),
      ...one(asg(1, { bankTitle: evil, myStatus: 'SUBMITTED' })),
      '/api/classes/3/assignments/1/mine': () => json(200, result({ teacherComment: '<script>window.__xss=1</script>', finalScore: 8 })),
    })
    renderAt('/bai-giao')
    await userEvent.click(await screen.findByRole('tab', { name: 'Đã nộp' }))
    expect(screen.getByText(evil)).toBeInTheDocument()
    expect(document.querySelector('img[src="x"]')).toBeNull()
    await userEvent.click(screen.getByRole('link', { name: 'Mở bài' }))
    expect(await screen.findByText('<script>window.__xss=1</script>')).toBeInTheDocument()
    expect(document.querySelector('main script, body script')).toBeNull()
  })
})

describe('Viết/Nói trong bài giao: assignmentId và phiên đúng bài (D-130)', () => {
  const writing = (latest: unknown, extra: Record<string, (b: unknown) => Response> = {}) =>
    mockApi({ ...member, '/api/grammar-elements': () => json(200, []), '/api/me/writing?questionId=7&limit=1&assignmentId=3': () => json(200, { data: latest ? [latest] : [] }), ...extra })

  test('Viết lại sau khi nộp bài giao gửi kèm assignmentId; 409 assignment_submitted báo trên màn', async () => {
    let body: unknown
    writing({ sessionId: 60, questionId: 7, status: 'SUBMITTED', createdAt: 'x' }, {
      '/api/writing/sessions/60': () => json(200, wsession(60, { status: 'SUBMITTED', finalText: 'Bài của tôi', gradeJobId: 9, grade })),
      '/api/writing/sessions': (b) => { body = b; return conflict('assignment_submitted')() },
    })
    renderAt('/viet/7?assignmentId=3')
    await userEvent.click(await screen.findByRole('button', { name: 'Viết lại' }))
    await waitFor(() => expect(body).toMatchObject({ questionId: 7, assignmentId: 3 }))
    expect(await screen.findByRole('alert')).toHaveTextContent('msg assignment_submitted')
  })

  // LỖI (nghi ngờ 1): latestSession lấy phiên gần nhất theo câu, không theo bài giao. Phiên tự luyện dở (không gắn bài giao)
  // bị mở trong ngữ cảnh bài giao; nộp phiên đó trừ lượt AI tuần và bài giao vẫn "Chưa nộp".
  test('ngữ cảnh bài giao không mở phiên tự luyện dở của cùng câu', async () => {
    // Backend lọc theo assignmentId nên không trả phiên tự luyện; web phải hỏi đúng bộ lọc và không rơi về truy vấn theo câu.
    const calls = writing(null, {
      '/api/me/writing?questionId=7&limit=1': () => json(200, { data: [{ sessionId: 50, questionId: 7, status: 'ACTIVE', createdAt: 'x' }] }),
      '/api/writing/sessions/50': () => json(200, wsession(50)),
    })
    renderAt('/viet/7?assignmentId=3')
    await waitFor(() => expect(calls).toContain('/api/me/writing?questionId=7&limit=1&assignmentId=3'))
    await settle()
    expect(calls).not.toContain('/api/me/writing?questionId=7&limit=1')
    expect(calls).not.toContain('/api/writing/sessions/50')
    expect(await screen.findByRole('button', { name: 'Bắt đầu viết' })).toBeInTheDocument()
  })

  // LỖI (nghi ngờ 1, phía Nói): kết quả tự luyện cũ hiện như kết quả bài giao; học viên tưởng đã nộp.
  test('ngữ cảnh bài giao không hiện kết quả lượt nói tự luyện cũ của cùng câu', async () => {
    const calls = mockApi({
      ...member,
      '/api/speaking/questions/8?assignmentId=3': () => json(200, { id: 8, part: 2, prompt: 'Describe a trip.', cueCard: [], maxSeconds: 60 }),
      '/api/me/speaking?questionId=8&limit=1&assignmentId=3': () => json(200, { data: [] }),
      '/api/me/speaking?questionId=8&limit=1': () => json(200, { data: [{ id: 5, questionId: 8, status: 'GRADED', createdAt: 'x', result: null }] }),
      '/api/speaking/submissions/5': () => json(200, { id: 5, questionId: 8, status: 'GRADED', createdAt: 'x', result: null }),
    })
    renderAt('/noi/8?assignmentId=3')
    await waitFor(() => expect(calls).toContain('/api/me/speaking?questionId=8&limit=1&assignmentId=3'))
    await settle()
    expect(calls).not.toContain('/api/me/speaking?questionId=8&limit=1')
    expect(calls).not.toContain('/api/speaking/submissions/5')
  })

  test('màn Nói lấy câu hỏi kèm assignmentId (mở được câu ở unit chưa mở)', async () => {
    const calls = mockApi({
      ...member,
      '/api/speaking/questions/8?assignmentId=3': () => json(200, { id: 8, part: 2, prompt: 'Describe a trip.', cueCard: [], maxSeconds: 60 }),
      '/api/me/speaking?questionId=8&limit=1&assignmentId=3': () => json(200, { data: [] }),
    })
    renderAt('/noi/8?assignmentId=3')
    expect(await screen.findByText('Describe a trip.')).toBeInTheDocument()
    expect(calls).not.toContain('/api/speaking/questions/8')
  })

  test('api Nói: tạo lượt gửi assignmentId; không có thì không gửi query rỗng', async () => {
    let body: unknown
    const calls = mockApi({ '/api/speaking/submissions': (b) => { body = b; return json(201, {}) }, '/api/speaking/questions/8': () => json(200, {}) })
    await createSubmission(8, undefined, 3)
    expect(body).toMatchObject({ questionId: 8, assignmentId: 3 })
    await fetchQuestion(8)
    expect(calls).toContain('/api/speaking/questions/8')
  })

  test('assignmentId không hợp lệ trên URL bị bỏ qua (không gửi NaN)', async () => {
    let body: unknown
    mockApi({ ...member, '/api/me/writing?questionId=7&limit=1': () => json(200, { data: [] }), '/api/writing/sessions': (b) => { body = b; return json(404) } })
    renderAt('/viet/7?assignmentId=abc')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu viết' }))
    await waitFor(() => expect(body).toBeDefined())
    expect(JSON.stringify(body)).not.toContain('NaN')
  })
})
