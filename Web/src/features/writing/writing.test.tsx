import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { apiError, auth, json, renderAt } from '../../testUtils'
import { splitMarked } from './marked'
import { JobTimeout, parseSse, waitForJob } from '../../lib/sse'

const sse = (events: [string, unknown][]) =>
  new Response(events.map(([e, d]) => `event: ${e}\ndata: ${JSON.stringify(d)}\n\n`).join(''), { status: 200, headers: { 'Content-Type': 'text/event-stream' } })
const job = (over = {}) => ({ id: 5, kind: 'WRITING_GRADE', status: 'DONE', quotaRefunded: false, ...over })

describe('parseSse', () => {
  test('tách event/data, bỏ keepalive, giữ phần dở dang', () => {
    const r = parseSse(': ping\n\nevent: progress\ndata: {"a":1}\n\nevent: result\ndata: {"b"')
    expect(r.events).toEqual([{ event: 'progress', data: '{"a":1}' }])
    expect(r.rest).toBe('event: result\ndata: {"b"')
    expect(parseSse('event: result\r\ndata: x\r\n\r\n').events[0]).toEqual({ event: 'result', data: 'x' })
  })
})

describe('waitForJob', () => {
  test('lấy job ở event result', async () => {
    vi.stubGlobal('fetch', async () => sse([['progress', job({ status: 'RUNNING' })], ['result', job({ result: { ok: 1 } })]]))
    expect((await waitForJob(5, new AbortController().signal)).result).toEqual({ ok: 1 })
  })

  test('luồng đứt thì GET job rồi mở lại', async () => {
    const urls: string[] = []
    vi.stubGlobal('fetch', async (u: string) => {
      urls.push(u)
      if (u.endsWith('/stream')) return urls.filter((x) => x.endsWith('/stream')).length === 1 ? sse([]) : sse([['result', job({ result: 'x' })]])
      return json(200, job({ status: 'RUNNING' }))
    })
    expect((await waitForJob(5, new AbortController().signal, 10_000, 1)).result).toBe('x')
    expect(urls).toEqual(['/api/ai/jobs/5/stream', '/api/ai/jobs/5', '/api/ai/jobs/5/stream'])
  })

  test('quá thời hạn thì ném JobTimeout', async () => {
    vi.stubGlobal('fetch', (_u: string, init?: RequestInit) => new Promise<Response>((_, rej) => init?.signal?.addEventListener('abort', () => rej(new DOMException('a', 'AbortError')))))
    await expect(waitForJob(5, new AbortController().signal, 20)).rejects.toBeInstanceOf(JobTimeout)
  })
})

describe('splitMarked', () => {
  test('tách đoạn đánh dấu', () => {
    expect(splitMarked('A {{PASSIVE|was done}} b')).toEqual([{ text: 'A ' }, { code: 'PASSIVE', text: 'was done' }, { text: ' b' }])
  })
})

describe('màn Viết', () => {
  beforeEach(() => {
    setAccessToken(null)
    sessionStorage.clear()
  })
  const session = (over = {}) => ({
    sessionId: 3, status: 'ACTIVE', question: { id: 8, prompt: 'Online learning?', payload: { requiredWords: 100 } }, createdAt: 'x',
    turns: [{ role: 'CAM', text: 'Online learning?' }, { role: 'CAM', text: 'Vì sao bạn nghĩ vậy?' }], ...over,
  })
  const grade = {
    taskResponse: 80, coherence: 70, lexical: 60, grammar: 50, scorePercent: 65, correctedAnswer: 'Better text', markedAnswer: 'It {{PASSIVE|was built}} fast.',
    connectives: { total: 3, types: 2, correct: 2, misused: ['But'], note: 'Ổn' },
    grammarElements: [{ code: 'PASSIVE', status: 'USED_WELL', quote: 'was built', upgrade: 'dùng thêm' }], gateNote: 'Quá nhiều từ chép', feedback: 'Tốt lắm',
  }
  const wire = (over: Record<string, (b: unknown, i?: RequestInit) => Response>) => {
    const calls: string[] = []
    vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
      calls.push(`${init?.method ?? 'GET'} ${url}`)
      if (url === '/api/auth/refresh') return json(200, auth)
      if (url === '/api/grammar-elements') return json(200, [{ code: 'PASSIVE', name: 'Passive voice', colorHex: '#8b5308' }])
      const h = over[url]
      return h ? h(init?.body ? JSON.parse(String(init.body)) : undefined, init) : json(404)
    })
    return calls
  }
  const latest = (status: string | null) => ({
    '/api/me/writing?questionId=8&limit=1': () => json(200, { data: status ? [{ sessionId: 3, questionId: 8, status, createdAt: 'x' }] : [] }),
  })

  test('chưa có phiên: chỉ tạo phiên khi bấm Bắt đầu viết', async () => {
    const calls = wire({
      ...latest(null),
      '/api/writing/sessions': () => json(201, { sessionId: 3, question: session().question }),
      '/api/writing/sessions/3': () => json(200, session({ turns: [{ role: 'CAM', text: 'Online learning?' }] })),
    })
    renderAt('/viet/8')
    const start = await screen.findByRole('button', { name: 'Bắt đầu viết' })
    expect(calls).not.toContain('POST /api/writing/sessions')
    await userEvent.click(start)
    expect(await screen.findByText('Online learning?')).toBeInTheDocument()
    expect(calls.filter((c) => c === 'POST /api/writing/sessions')).toHaveLength(1)
  })

  test('phiên dở: trả lời rồi chờ job, nội dung gõ dở được lưu theo phiên', async () => {
    let turns = session().turns
    wire({
      ...latest('ACTIVE'),
      '/api/writing/sessions/3': () => json(200, session({ turns })),
      '/api/writing/sessions/3/answer': () => {
        turns = [...turns, { role: 'STUDENT', text: 'Vì linh hoạt' }, { role: 'CAM', text: 'Ví dụ?' }]
        return json(202, { jobId: 11, sessionStatus: 'ACTIVE' })
      },
      '/api/ai/jobs/11/stream': () => sse([['result', job({ id: 11, kind: 'SCAFFOLD_FOLLOWUP' })]]),
    })
    renderAt('/viet/8')
    const box = await screen.findByLabelText('Câu trả lời của bạn')
    await userEvent.type(box, 'Vì linh')
    expect(sessionStorage.getItem('writing:answer:3')).toBe('Vì linh')
    await userEvent.type(box, ' hoạt')
    expect(screen.getByText('3 từ · Bản nháp')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Gửi trả lời' }))
    expect(await screen.findByText('Ví dụ?')).toBeInTheDocument()
    expect(sessionStorage.getItem('writing:answer:3')).toBeNull()
  })

  test('READY: bản nháp x/yêu cầu từ, gửi chấm giữ Idempotency-Key khi lỗi mạng, rồi ra kết quả', async () => {
    const keys: (string | null)[] = []
    let graded = false
    wire({
      ...latest('READY'),
      '/api/writing/sessions/3': () => json(200, graded
        ? session({ status: 'SUBMITTED', draft: 'Draft text here', finalText: 'Draft text here', gradeJobId: 9, grade })
        : session({ status: 'READY', draft: 'Draft text here' })),
      '/api/writing/sessions/3/submit': (_b, init) => {
        keys.push(new Headers(init?.headers).get('Idempotency-Key'))
        if (keys.length === 1) throw new TypeError('network')
        graded = true
        return json(202, { jobId: 9, status: 'PENDING', quotaRemaining: 2 })
      },
      '/api/ai/jobs/9/stream': () => sse([['result', job({ id: 9, result: grade })]]),
    })
    renderAt('/viet/8')
    expect(await screen.findByText('3/100 từ')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Gửi chấm/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Mất kết nối')
    await userEvent.click(screen.getByRole('button', { name: /Gửi chấm/ }))
    expect(await screen.findByLabelText('Điểm')).toHaveTextContent('65%')
    expect(keys).toHaveLength(2)
    expect(keys[0]).toBeTruthy()
    expect(keys[1]).toBe(keys[0])
    expect(screen.getByRole('progressbar', { name: 'Từ vựng' })).toHaveAttribute('aria-valuenow', '60')
    expect(screen.getByText('Better text')).toBeInTheDocument()
    expect(screen.getByLabelText('Bài đã đánh dấu')).toHaveTextContent('was built')
    expect(screen.getByText('Dùng tốt')).toBeInTheDocument()
    expect(screen.getByText('Dùng sai: But')).toBeInTheDocument()
    expect(screen.getByText('Quá nhiều từ chép')).toBeInTheDocument()
    expect(screen.getByText('Tốt lắm')).toBeInTheDocument()
  })

  test('phiên gần nhất đã nộp: hiện kết quả, Viết lại tạo phiên mới', async () => {
    const calls = wire({
      ...latest('SUBMITTED'),
      '/api/writing/sessions/3': () => json(200, session({ status: 'SUBMITTED', finalText: 'Mine', gradeJobId: 9, grade })),
      '/api/writing/sessions': () => json(201, { sessionId: 4, question: session().question }),
      '/api/writing/sessions/4': () => json(200, session({ sessionId: 4, turns: [{ role: 'CAM', text: 'Online learning?' }] })),
    })
    renderAt('/viet/8')
    expect(await screen.findByText('Mine')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Viết lại' }))
    await waitFor(() => expect(calls).toContain('POST /api/writing/sessions'))
  })

  test('Viết lại gặp 503 hoặc mất mạng: báo lỗi ngay trên màn kết quả, bấm lại được', async () => {
    let n = 0
    wire({
      ...latest('SUBMITTED'),
      '/api/writing/sessions/3': () => json(200, session({ status: 'SUBMITTED', finalText: 'Mine', gradeJobId: 9, grade })),
      '/api/writing/sessions': () => (++n === 1 ? apiError(503, 'bận') : json(201, { sessionId: 4, question: session().question })),
      '/api/writing/sessions/4': () => json(200, session({ sessionId: 4, turns: [{ role: 'CAM', text: 'Online learning?' }] })),
    })
    renderAt('/viet/8')
    await userEvent.click(await screen.findByRole('button', { name: 'Viết lại' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Hệ thống đang bận')
    expect(screen.getByText('Mine')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Viết lại' }))
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  })

  test('402 hiện thẻ hết lượt kèm tên gói', async () => {
    wire({
      ...latest(null),
      '/api/writing/sessions': () => json(402, { error: { code: 'quota_exhausted', message: 'x', details: { products: [{ type: 'AI_ADDON', name: 'Nạp 20 lượt' }] } } }),
    })
    renderAt('/viet/8')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu viết' }))
    expect(await screen.findByRole('heading', { name: 'Bạn đã hết lượt chấm AI' })).toBeInTheDocument()
    expect(screen.getByText(/Nạp 20 lượt/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Đơn hàng của tôi' })).toHaveAttribute('href', '/don-hang')
  })

  test('429 báo thao tác nhanh, giữ nội dung đã gõ', async () => {
    wire({
      ...latest('ACTIVE'),
      '/api/writing/sessions/3': () => json(200, session()),
      '/api/writing/sessions/3/answer': () => apiError(429, 'quá nhanh'),
    })
    renderAt('/viet/8')
    await userEvent.type(await screen.findByLabelText('Câu trả lời của bạn'), 'abc')
    await userEvent.click(screen.getByRole('button', { name: 'Gửi trả lời' }))
    expect(await screen.findByText('Bạn thao tác nhanh quá, thử lại sau ít giây.')).toBeInTheDocument()
    expect(screen.getByLabelText('Câu trả lời của bạn')).toHaveValue('abc')
  })

  test.each([
    [403, 'exam_question_not_practicable', 'Câu thuộc bài thi khóa'],
    [403, 'bank_locked', 'Bộ bài tập đang khóa'],
    [404, 'session_not_found', 'Không tìm thấy bài viết'],
  ])('lỗi %s %s', async (status, code, title) => {
    wire({ ...latest(null), '/api/writing/sessions': () => json(status, { error: { code, message: 'x' } }) })
    renderAt('/viet/8')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu viết' }))
    expect(await screen.findByRole('heading', { name: title })).toBeInTheDocument()
  })
})
