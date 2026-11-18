import { act, renderHook, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import { clearLocal, loadLocal, saveLocal } from './store'
import type { PlacementResult, Saved, Started } from './types'
import { RETRY_MS, useSyncedAnswers } from './useSyncedAnswers'

const err = (status: number, code: string) => json(status, { error: { code, message: `lỗi ${code}` } })
const question = { id: 11, skill: 'READING', type: 'CONNECTIVE_MCQ', prompt: 'Chọn đáp án đúng', payload: null, choices: [{ id: 1, label: 'A', content: 'Alpha' }, { id: 2, label: 'B', content: 'Beta' }] }
const started = (over: Partial<Started> = {}): Started => ({ attemptId: 7, guestToken: 'tok', title: 'Kiểm tra đầu vào', startedAt: 'x', deadlineAt: 'y', timeLimitSec: 900, remainingSec: 900, questions: [question], ...over })
const result: PlacementResult = {
  attemptId: 7, submittedAt: 'x', overallBand: 4.5, recommendedCourse: { courseId: 3, slug: 'pre-ielts', title: 'Pre IELTS' },
  skills: { READING: { correct: 2, total: 3, percent: 66.67, band: 5 }, GRAMMAR: { correct: 1, total: 3, percent: 33.33, band: 3.5 } },
}
const saved = (over: Partial<Saved> = {}): Saved => ({ attemptId: 7, guestToken: 'tok', submitted: false, started: started(), answers: {}, deadlineMs: Date.now() + 900_000, ...over })

beforeEach(() => {
  setAccessToken(null)
  localStorage.clear()
})

describe('store localStorage', () => {
  test('lưu, khôi phục và dọn theo attemptId', () => {
    saveLocal(saved({ answers: { 11: { choiceId: 1 } } }))
    expect(loadLocal()?.answers).toEqual({ 11: { choiceId: 1 } })
    expect(loadLocal()?.guestToken).toBe('tok')
    clearLocal(7)
    expect(loadLocal()).toBeNull()
    expect(localStorage.length).toBe(0)
  })

  test('dữ liệu hỏng hoặc storage lỗi thì không ném', () => {
    localStorage.setItem('pps.placement.current', '7')
    localStorage.setItem('pps.placement.7', '{hỏng')
    expect(loadLocal()).toBeNull()
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('full') })
    expect(() => saveLocal(saved())).not.toThrow()
    vi.restoreAllMocks()
  })
})

describe('useSyncedAnswers', () => {
  const online = (v: boolean) => vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(v)
  afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks() })

  test('lưu máy ngay, gửi nháp sau 2 giây không đổi', async () => {
    vi.useFakeTimers()
    const calls: { url: string; headers: Headers; body: string }[] = []
    vi.stubGlobal('fetch', async (url: string, init: RequestInit) => {
      calls.push({ url, headers: new Headers(init.headers), body: String(init.body) })
      return json(200, { remainingSec: 800 })
    })
    const s = saved()
    const { result: r } = renderHook(() => useSyncedAnswers(s, () => {}))
    act(() => r.current.setAnswers({ 11: { choiceId: 1 } }))
    expect(loadLocal()?.answers).toEqual({ 11: { choiceId: 1 } }) // chưa gửi server mà đã lưu máy
    expect(calls).toHaveLength(0)
    await act(() => vi.advanceTimersByTimeAsync(2000))
    expect(calls).toHaveLength(1)
    expect(calls[0].url).toBe('/api/placement/attempts/7/draft')
    expect(calls[0].headers.get('X-Placement-Token')).toBe('tok')
    expect(JSON.parse(calls[0].body)).toEqual({ answers: [{ questionId: 11, answer: { choiceId: 1 } }] })
    expect(r.current.status).toBe('saved')
  })

  test('mất mạng thì giữ trên máy, có mạng lại (sự kiện online) thì gửi', async () => {
    vi.useFakeTimers()
    const spy = online(false)
    const fetchMock = vi.fn(async () => json(200, { remainingSec: 700 }))
    vi.stubGlobal('fetch', fetchMock)
    const { result: r } = renderHook(() => useSyncedAnswers(saved(), () => {}))
    act(() => r.current.setAnswers({ 11: { choiceId: 2 } }))
    await act(() => vi.advanceTimersByTimeAsync(2000))
    expect(fetchMock).not.toHaveBeenCalled()
    expect(r.current.status).toBe('offline')
    expect(loadLocal()?.answers).toEqual({ 11: { choiceId: 2 } })
    spy.mockReturnValue(true)
    await act(async () => { window.dispatchEvent(new Event('online')) })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(r.current.status).toBe('saved')
  })

  test('lỗi mạng khi gửi thì thử lại sau một nhịp', async () => {
    vi.useFakeTimers()
    let n = 0
    vi.stubGlobal('fetch', async () => { if (n++ === 0) throw new TypeError('Failed to fetch'); return json(200, { remainingSec: 600 }) })
    const { result: r } = renderHook(() => useSyncedAnswers(saved(), () => {}))
    act(() => r.current.setAnswers({ 11: { choiceId: 1 } }))
    await act(() => vi.advanceTimersByTimeAsync(2000))
    expect(r.current.status).toBe('offline')
    await act(() => vi.advanceTimersByTimeAsync(RETRY_MS))
    expect(r.current.status).toBe('saved')
  })

  test('server báo hết giờ thì gọi onClosed', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('fetch', async () => err(409, 'attempt_time_up'))
    const closed = vi.fn()
    const { result: r } = renderHook(() => useSyncedAnswers(saved(), closed))
    act(() => r.current.setAnswers({ 11: { choiceId: 1 } }))
    await act(() => vi.advanceTimersByTimeAsync(2000))
    expect(closed).toHaveBeenCalled()
  })
})

describe('màn Kiểm tra đầu vào', () => {
  const answerAndSubmit = async (u: ReturnType<typeof userEvent.setup>) => {
    await u.click(await screen.findByRole('radio', { name: /Alpha/ }))
    await u.click(screen.getByRole('button', { name: 'Nộp bài' }))
    await u.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Nộp bài' }))
  }

  test('khách: bắt đầu → làm → nộp → đăng nhập/đăng ký để xem; token và attemptId nằm trên máy', async () => {
    const u = userEvent.setup()
    let submitHeader: string | null = null
    mockApi({
      '/api/placement/attempts': () => json(201, started()),
      '/api/placement/attempts/7/draft': () => json(200, { remainingSec: 880 }),
      '/api/placement/attempts/7/submit': (_b, init) => {
        submitHeader = new Headers(init?.headers).get('X-Placement-Token')
        return json(200, { submitted: true, requiresLogin: true })
      },
    })
    renderAt('/kiem-tra-dau-vao')
    expect(await screen.findByText('Không tạm dừng được')).toBeInTheDocument()
    await u.click(screen.getByRole('button', { name: 'Bắt đầu' }))
    expect(await screen.findByText(/Câu 1\/1 · Đọc/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Tạm dừng' })).not.toBeInTheDocument()
    expect(loadLocal()).toMatchObject({ attemptId: 7, guestToken: 'tok', submitted: false })
    await answerAndSubmit(u)
    expect(await screen.findByRole('heading', { name: 'Đăng ký hoặc đăng nhập để xem kết quả' })).toBeInTheDocument()
    expect(submitHeader).toBe('tok')
    expect(screen.getByRole('link', { name: 'Đăng ký' })).toHaveAttribute('href', '/dang-ky?next=%2Fkiem-tra-dau-vao')
    expect(screen.getByRole('link', { name: 'Đăng nhập' })).toHaveAttribute('href', '/dang-nhap?next=%2Fkiem-tra-dau-vao')
    expect(loadLocal()).toMatchObject({ attemptId: 7, guestToken: 'tok', submitted: true, answers: {} })
  })

  test('quay lại sau đăng nhập thì tự claim bằng token trên máy, hiện kết quả và dọn storage', async () => {
    saveLocal({ attemptId: 7, guestToken: 'tok', submitted: true, answers: {}, deadlineMs: 0 })
    let token: string | null = null
    mockApi({
      '/api/auth/refresh': () => json(200, auth),
      '/api/me/placement': () => json(200, { result: null }),
      '/api/placement/attempts/7/claim': (_b, init) => {
        token = new Headers(init?.headers).get('X-Placement-Token')
        return json(200, result)
      },
    })
    renderAt('/kiem-tra-dau-vao')
    expect(await screen.findByLabelText('Band tổng')).toHaveTextContent('4.5')
    expect(token).toBe('tok')
    expect(screen.getByText('Band 5.0')).toBeInTheDocument()
    expect(screen.getByText(/Đúng 2\/3 câu · 67%/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Mua gói học' })).toHaveAttribute('href', '/thanh-toan?khoa=pre-ielts')
    expect(screen.queryByText(/đáp án/i)).not.toBeInTheDocument()
    expect(localStorage.length).toBe(0)
  })

  test('claim 409: báo đã hết lượt, kèm kết quả cũ của tài khoản', async () => {
    saveLocal({ attemptId: 7, guestToken: 'tok', submitted: true, answers: {}, deadlineMs: 0 })
    mockApi({
      '/api/auth/refresh': () => json(200, auth),
      '/api/me/placement': () => json(200, { result: { ...result, attemptId: 2, overallBand: 3 } }),
      '/api/placement/attempts/7/claim': () => err(409, 'placement_retakes_exhausted'),
    })
    renderAt('/kiem-tra-dau-vao')
    expect(await screen.findByText(/hết lượt làm kiểm tra đầu vào/)).toBeInTheDocument()
    expect(screen.getByLabelText('Band tổng')).toHaveTextContent('3.0')
    expect(localStorage.length).toBe(0)
  })

  test('học viên: có kết quả cũ thì hiện kèm nút làm lại; nộp xong nhận kết quả ngay', async () => {
    const u = userEvent.setup()
    mockApi({
      '/api/auth/refresh': () => json(200, auth),
      '/api/me/placement': () => json(200, { result: { ...result, attemptId: 2, overallBand: 3 } }),
      '/api/placement/attempts': () => json(201, started({ guestToken: undefined })),
      '/api/placement/attempts/7/submit': () => json(200, { submitted: true, requiresLogin: false, result }),
    })
    renderAt('/kiem-tra-dau-vao')
    expect(await screen.findByLabelText('Band tổng')).toHaveTextContent('3.0')
    await u.click(screen.getByRole('button', { name: 'Làm lại' }))
    await answerAndSubmit(u)
    await waitFor(() => expect(screen.getByLabelText('Band tổng')).toHaveTextContent('4.5'))
    expect(localStorage.length).toBe(0)
  })

  test('học viên hết lượt (409 lúc bắt đầu) thì hiện kết quả cũ', async () => {
    const u = userEvent.setup()
    mockApi({
      '/api/auth/refresh': () => json(200, auth),
      '/api/me/placement': () => json(200, { result }),
      '/api/placement/attempts': () => err(409, 'placement_retakes_exhausted'),
    })
    renderAt('/kiem-tra-dau-vao')
    await u.click(await screen.findByRole('button', { name: 'Làm lại' }))
    expect(await screen.findByText(/hết lượt làm lại/)).toBeInTheDocument()
    expect(screen.getByLabelText('Band tổng')).toHaveTextContent('4.5')
  })

  test('lỗi bắt đầu: 404 placement_unavailable hiện Chưa có đề kiểm tra', async () => {
    const u = userEvent.setup()
    let next = () => err(404, 'placement_unavailable')
    mockApi({ '/api/placement/attempts': () => next() })
    renderAt('/kiem-tra-dau-vao')
    await u.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    expect(await screen.findByRole('heading', { name: 'Chưa có đề kiểm tra' })).toBeInTheDocument()
  })

  test('429 nhắc thử lại sau, mạng đứt báo kết nối', async () => {
    const u = userEvent.setup()
    let n = 0
    vi.stubGlobal('fetch', async (url: string) => {
      if (url === '/api/auth/refresh') return err(401, 'no')
      if (n++ === 0) return err(429, 'rate_limited')
      throw new TypeError('Failed to fetch')
    })
    renderAt('/kiem-tra-dau-vao')
    await u.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    expect(await screen.findByText(/thử lại sau/)).toBeInTheDocument()
    await u.click(screen.getByRole('button', { name: 'Bắt đầu' }))
    expect(await screen.findByText(/Mất kết nối/)).toBeInTheDocument()
  })

  test('tải lại trang: khách tiếp tục lượt từ localStorage, không gọi POST mới', async () => {
    const u = userEvent.setup()
    saveLocal(saved({ answers: { 11: { choiceId: 2 } } }))
    const calls = mockApi({})
    renderAt('/kiem-tra-dau-vao')
    await u.click(await screen.findByRole('button', { name: 'Tiếp tục bài đang làm' }))
    expect(await screen.findByRole('radio', { name: /Beta/ })).toHaveAttribute('aria-checked', 'true')
    expect(calls).not.toContain('/api/placement/attempts')
  })

  test('nộp khi mất mạng: chờ có mạng rồi gửi, kèm thông báo', async () => {
    const u = userEvent.setup()
    saveLocal(saved())
    let online = false
    const submit = vi.fn()
    vi.stubGlobal('fetch', async (url: string) => {
      if (url === '/api/auth/refresh') return err(401, 'no')
      if (url.endsWith('/draft')) return json(200, { remainingSec: 800 })
      if (!online) throw new TypeError('Failed to fetch')
      submit()
      return json(200, { submitted: true, requiresLogin: true })
    })
    renderAt('/kiem-tra-dau-vao')
    await u.click(await screen.findByRole('button', { name: 'Tiếp tục bài đang làm' }))
    await u.click(await screen.findByRole('radio', { name: /Alpha/ }))
    await u.click(screen.getByRole('button', { name: 'Nộp bài' }))
    await u.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Nộp bài' }))
    expect(await screen.findByText(/sẽ được nộp ngay khi có mạng/)).toBeInTheDocument()
    online = true
    await act(async () => { window.dispatchEvent(new Event('online')) })
    expect(await screen.findByRole('heading', { name: 'Đăng ký hoặc đăng nhập để xem kết quả' })).toBeInTheDocument()
    expect(submit).toHaveBeenCalledTimes(1)
  })
})
