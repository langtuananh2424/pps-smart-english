import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../../lib/api'
import { auth, json, mockApi, renderAt } from '../../../testUtils'
import type { Bank } from '../types'

// Tester đợt #53: điểm ghép Player ↔ thẻ Viết/Nói, gửi trùng, Idempotency-Key, rò rỉ object URL/micro, lộ điểm tạm (D-147).
const uploads = vi.hoisted(() => ({ run: vi.fn() }))
vi.mock('../../speaking/api', async (orig) => ({ ...(await orig<object>()), uploadAudioWithProgress: uploads.run }))
vi.mock('../../speaking/wav', async (orig) => ({ ...(await orig<object>()), toWav16k: vi.fn(async () => ({ wav: new Blob(['w']), seconds: 8 })) }))

const bank: Bank = {
  id: 7, title: 'Bộ 1', lessonId: 1, isTrial: false, mode: 'PRACTICE', timeLimitSec: null,
  questions: [
    { id: 1, type: 'CONNECTIVE_MCQ', prompt: 'Chọn', payload: {}, choices: [{ id: 5, label: 'A', content: 'So' }] },
    { id: 2, type: 'WRITING', prompt: 'Viết bài', payload: {} },
    { id: 3, type: 'SPEAKING', prompt: 'Nói đi', payload: {} },
  ],
}
const quota = { pool: 0, courses: [{ courseId: 1, courseTitle: 'K', period: 'WEEK', quota: 5, used: 2, remaining: 3, resetsAt: '2026-11-30T00:00:00+07:00' }] }

type H = (b: unknown, i?: RequestInit) => Response
const posts: { url: string; body: unknown; key?: string | null }[] = []
let nextSession = 100
function wire(over: Record<string, H> = {}, aiSessions: unknown[] = []) {
  posts.length = 0
  nextSession = 100
  const rec = (url: string, res: (b: unknown) => Response): H => (b, i) => (posts.push({ url, body: b, key: new Headers(i?.headers).get('Idempotency-Key') }), res(b))
  const submits: Record<string, H> = {}
  for (let id = 101; id < 106; id++) submits[`/api/writing/sessions/${id}/submit`] = rec(`submit:${id}`, () => json(202, { jobId: id, status: 'QUEUED', quotaRemaining: 2 }))
  return mockApi({
    '/api/auth/refresh': () => json(200, auth),
    '/api/banks/7': () => json(200, bank),
    '/api/me/attempts?bankId=7&open=true&limit=1': () => json(200, { data: [] }),
    '/api/banks/7/attempts': () => json(201, { id: 9, bankId: 7, startedAt: 'x', timeLimitSec: null }),
    '/api/attempts/9/progress': () => json(200, { attemptId: 9, paused: false }),
    '/api/attempts/9/ai-sessions': () => json(200, { data: aiSessions }),
    '/api/me/quota': () => json(200, quota),
    '/api/writing/sessions': rec('create', () => json(201, { sessionId: ++nextSession, question: { id: 2, prompt: 'x' } })),
    ...submits,
    '/api/speaking/questions/3?attemptId=9': () => json(200, { id: 3, part: 1, prompt: 'Nói đi', cueCard: [], maxSeconds: 180 }),
    '/api/speaking/submissions': rec('speak', () => json(201, { submissionId: 31, url: 'http://minio/u', fields: {}, maxBytes: 9, contentType: 'audio/wav', maxSeconds: 180, expiresAt: 'x' })),
    '/api/speaking/submissions/31/complete': rec('complete', () => json(200, { submissionId: 31, status: 'GRADING', jobId: 8, quotaRemaining: 2 })),
    ...over,
  })
}
const go = async (n: number) => {
  renderAt('/bai-tap/7')
  await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
  await userEvent.click(await screen.findByRole('button', { name: new RegExp(`^Câu ${n}`) }))
}
const sendBtn = () => screen.getByRole('button', { name: 'Gửi chấm' })
const pick = async (name = 'ban-ghi.m4a') => userEvent.upload(await screen.findByLabelText('Chọn tệp audio'), new File(['x'], name, { type: 'audio/mp4' }))

beforeEach(() => {
  setAccessToken('t')
  sessionStorage.clear()
  uploads.run.mockReset()
  URL.createObjectURL = vi.fn(() => 'blob:picked')
  URL.revokeObjectURL = vi.fn()
})

describe('Viết: gửi trùng và Idempotency-Key', () => {
  // Máy chủ băm (sessionId, finalText) theo key: cùng key cho phiên mới là 409 idempotency_key_reused.
  test('Viết lại rồi gửi lại nguyên văn: phiên mới phải dùng Idempotency-Key mới', async () => {
    wire()
    await go(2)
    await userEvent.type(await screen.findByLabelText('Nội dung bài viết'), 'Same text')
    await userEvent.click(sendBtn())
    await screen.findByText(/Đã gửi chấm/)
    await userEvent.click(screen.getByRole('button', { name: 'Viết lại' }))
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Viết lại' }))
    await userEvent.click(sendBtn())
    await waitFor(() => expect(posts.map((p) => p.url)).toEqual(['create', 'submit:101', 'create', 'submit:102']))
    expect(posts[3].key).not.toBe(posts[1].key)
  })

  test('bấm Gửi chấm hai lần liền (trước khi kịp vẽ lại) chỉ tạo một phiên, nộp một lần', async () => {
    wire()
    await go(2)
    await userEvent.type(await screen.findByLabelText('Nội dung bài viết'), 'Hello world')
    const btn = sendBtn()
    act(() => { btn.click(); btn.click() })
    await screen.findByText(/Đã gửi chấm/)
    expect(posts.filter((p) => p.url === 'create')).toHaveLength(1)
    expect(posts.filter((p) => p.url.startsWith('submit'))).toHaveLength(1)
  })

  test('lỗi mạng khi nộp: gửi lại dùng lại phiên và cùng key (không trừ lượt hai lần)', async () => {
    let fail = true
    wire({ '/api/writing/sessions/101/submit': (b, i) => (posts.push({ url: 'submit:101', body: b, key: new Headers(i?.headers).get('Idempotency-Key') }), fail ? json(503) : json(202, { jobId: 1, status: 'QUEUED', quotaRemaining: 1 })) })
    await go(2)
    await userEvent.type(await screen.findByLabelText('Nội dung bài viết'), 'Hello')
    await userEvent.click(sendBtn())
    await screen.findByRole('alert')
    expect(screen.getByLabelText('Nội dung bài viết')).toHaveValue('Hello')
    fail = false
    await userEvent.click(sendBtn())
    await screen.findByText(/Đã gửi chấm/)
    expect(posts.map((p) => p.url)).toEqual(['create', 'submit:101', 'submit:101'])
    expect(posts[1].key).toBe(posts[2].key)
  })
})

describe('D-147: không lộ điểm tạm trong lượt', () => {
  test('câu Viết đã gửi, mở lại trên máy khác: dù API lỡ trả grade cũng không hiện điểm hay bản sửa', async () => {
    wire({
      '/api/writing/sessions/90': () => json(200, { sessionId: 90, status: 'SUBMITTED', question: { id: 2, prompt: 'x' }, turns: [], finalText: 'From server', grade: { scorePercent: 88, correctedAnswer: 'LEAKED FIX', taskResponse: 1, coherence: 1, lexical: 1, grammar: 1 }, createdAt: 'x' }),
    }, [{ questionId: 2, kind: 'WRITING', id: 90, status: 'GRADING' }])
    await go(2)
    expect(await screen.findByDisplayValue('From server')).toHaveAttribute('readonly')
    expect(screen.queryByText(/88/)).not.toBeInTheDocument()
    expect(screen.queryByText(/LEAKED FIX/)).not.toBeInTheDocument()
  })

  test('FAILED là chưa gửi: không khóa, mốc chưa xanh, hộp Nộp bài tính câu này 0 điểm', async () => {
    wire({}, [{ questionId: 2, kind: 'WRITING', id: 90, status: 'FAILED' }])
    await go(2)
    expect(await screen.findByLabelText('Nội dung bài viết')).not.toHaveAttribute('readonly')
    expect(screen.getByRole('button', { name: 'Câu 2' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Câu 3' }))
    await userEvent.click(screen.getByRole('button', { name: 'Nộp bài' }))
    const dlg = await screen.findByRole('alertdialog')
    expect(dlg).toHaveTextContent('Trong đó 2 câu Viết/Nói sẽ tính 0 điểm.')
  })
})

describe('Nói: gửi trùng, đổi câu giữa chừng, nhả tài nguyên', () => {
  test('bấm Gửi chấm hai lần liền chỉ xin quyền tải một lần', async () => {
    uploads.run.mockImplementation(async (_p, _w, onProgress: (n: number) => void) => onProgress(100))
    wire()
    await go(3)
    await pick()
    await screen.findByText('ban-ghi.m4a')
    const btn = sendBtn()
    act(() => { btn.click(); btn.click() })
    await screen.findByText(/Đã gửi chấm/)
    expect(posts.map((p) => p.url)).toEqual(['speak', 'complete'])
  })

  test('đổi câu khi đang tải lên: thu hồi object URL; tải xong vẫn ghi đúng câu 3, không báo lỗi', async () => {
    let finish!: () => void
    uploads.run.mockImplementation((_p, _w, onProgress: (n: number) => void) => new Promise<void>((resolve) => { onProgress(40); finish = resolve }))
    wire()
    await go(3)
    await pick()
    await userEvent.click(await screen.findByRole('button', { name: 'Gửi chấm' }))
    await screen.findByRole('progressbar')
    await userEvent.click(screen.getByRole('button', { name: 'Câu 1' }))
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:picked')
    await act(async () => finish())
    await waitFor(() => expect(posts.map((p) => p.url)).toEqual(['speak', 'complete']))
    expect(await screen.findByRole('button', { name: 'Câu 3, đã làm' })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  test('đổi câu khi đang ghi âm: tắt micro, không giữ bản ghi', async () => {
    const stopTrack = vi.fn()
    const stream = { getTracks: () => [{ stop: stopTrack }] }
    class FakeRecorder {
      state = 'inactive'
      mimeType = 'audio/webm'
      stream = stream
      ondataavailable: ((e: { data: Blob }) => void) | null = null
      onstop: (() => void) | null = null
      start() { this.state = 'recording' }
      stop() { this.state = 'inactive'; this.onstop?.() }
    }
    vi.stubGlobal('MediaRecorder', FakeRecorder)
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: vi.fn(async () => stream) } })
    try {
      wire()
      await go(3)
      await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu ghi âm' }))
      expect(await screen.findByText('Đang ghi âm')).toBeInTheDocument()
      await userEvent.click(screen.getByRole('button', { name: 'Câu 1' }))
      expect(stopTrack).toHaveBeenCalled()
      await userEvent.click(screen.getByRole('button', { name: 'Câu 3' }))
      expect(await screen.findByRole('button', { name: 'Bắt đầu ghi âm' })).toBeInTheDocument()
    } finally {
      vi.unstubAllGlobals()
      Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: undefined })
    }
  })

  test('402 lúc xác nhận (sau khi đã tải lên): giữ tệp, hiện thẻ hết lượt, không báo lỗi chung', async () => {
    uploads.run.mockImplementation(async () => {})
    wire({ '/api/speaking/submissions/31/complete': () => json(402, { error: { code: 'quota_exhausted', message: 'Hết lượt', details: { products: [] } } }) })
    await go(3)
    await pick()
    await userEvent.click(await screen.findByRole('button', { name: 'Gửi chấm' }))
    await screen.findByRole('alert', { name: 'Hết lượt AI' })
    expect(screen.getByText('ban-ghi.m4a')).toBeInTheDocument()
    expect(URL.revokeObjectURL).not.toHaveBeenCalled()
  })
})

describe('Lần sửa: tải bản ghi gắn với Player, xóa bài viết tạm khi nộp', () => {
  const start = async (n: number) => {
    const router = renderAt('/bai-tap/7')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    await userEvent.click(await screen.findByRole('button', { name: new RegExp(`^Câu ${n}`) }))
    return router
  }
  // Tải lên treo đến khi gọi finish/fail; bị hủy thì ném AbortError như fetch/XHR thật.
  const hang = () => {
    const h: { signal?: AbortSignal; finish: () => void; fail: (e: unknown) => void } = { finish: () => {}, fail: () => {} }
    uploads.run.mockImplementation((_p, _w, onProgress: (n: number) => void, signal: AbortSignal) => new Promise<void>((resolve, reject) => {
      h.signal = signal
      onProgress(40)
      h.finish = resolve
      h.fail = reject
      signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
    }))
    return h
  }
  const submitBtn = () => screen.getByRole('button', { name: 'Nộp bài' })

  test('rời Player khi đang tải bản ghi: hủy tải lên, không gọi xác nhận', async () => {
    const h = hang()
    wire()
    const router = await start(3)
    await pick()
    await userEvent.click(await screen.findByRole('button', { name: 'Gửi chấm' }))
    await screen.findByRole('progressbar')
    expect(h.signal?.aborted).toBe(false)
    await act(async () => { await router.navigate('/') })
    expect(h.signal?.aborted).toBe(true)
    await new Promise((r) => setTimeout(r, 20))
    expect(posts.map((p) => p.url)).toEqual(['speak'])
  })

  test('đang tải bản ghi thì khóa Nộp bài, kể cả sau khi đổi câu rồi quay lại; tải xong thì mở', async () => {
    const h = hang()
    wire()
    await start(3)
    expect(submitBtn()).toBeEnabled()
    await pick()
    await userEvent.click(await screen.findByRole('button', { name: 'Gửi chấm' }))
    await screen.findByRole('progressbar')
    expect(submitBtn()).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Câu 1' }))
    await userEvent.click(screen.getByRole('button', { name: /^Câu 3/ }))
    expect(submitBtn()).toBeDisabled()
    await act(async () => h.finish())
    await waitFor(() => expect(submitBtn()).toBeEnabled())
    expect(posts.map((p) => p.url)).toEqual(['speak', 'complete'])
  })

  test('tải bản ghi lỗi mạng: mở lại Nộp bài', async () => {
    const h = hang()
    wire()
    await start(3)
    await pick()
    await userEvent.click(await screen.findByRole('button', { name: 'Gửi chấm' }))
    await screen.findByRole('progressbar')
    expect(submitBtn()).toBeDisabled()
    await act(async () => h.fail(new TypeError('Failed to fetch')))
    await waitFor(() => expect(submitBtn()).toBeEnabled())
  })

  test('nộp lượt thành công: xóa bài viết tạm của lượt này, giữ của lượt khác', async () => {
    sessionStorage.setItem('ai:writing:8:2', 'other attempt')
    wire({ '/api/attempts/9/submit': () => json(200, { id: 9, bankId: 7, score: 0, maxScore: 1, results: [], pendingJobs: [] }) })
    await start(2)
    await userEvent.type(await screen.findByLabelText('Nội dung bài viết'), 'Draft text')
    expect(sessionStorage.getItem('ai:writing:9:2')).toBe('Draft text')
    await userEvent.click(screen.getByRole('button', { name: /^Câu 3/ }))
    await userEvent.click(submitBtn())
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Nộp bài' }))
    await waitFor(() => expect(sessionStorage.getItem('ai:writing:9:2')).toBeNull())
    expect(sessionStorage.getItem('ai:writing:8:2')).toBe('other attempt')
  })

  test('nộp lượt lỗi (503): giữ bài viết tạm để nộp lại', async () => {
    wire({ '/api/attempts/9/submit': () => json(503) })
    await start(2)
    await userEvent.type(await screen.findByLabelText('Nội dung bài viết'), 'Keep me')
    await userEvent.click(screen.getByRole('button', { name: /^Câu 3/ }))
    await userEvent.click(submitBtn())
    const dlg = await screen.findByRole('alertdialog')
    await userEvent.click(within(dlg).getByRole('button', { name: 'Nộp bài' }))
    await waitFor(() => expect(dlg).toHaveTextContent('Hệ thống đang bận'))
    expect(sessionStorage.getItem('ai:writing:9:2')).toBe('Keep me')
  })
})
