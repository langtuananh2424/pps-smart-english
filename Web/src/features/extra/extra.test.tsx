import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { apiError, auth, json, mockApi, renderAt } from '../../testUtils'
import type { ExtraCard, ExtraOverview } from './types'
import { capped } from './view'

// FR-EX-07, D-152: màn Luyện thêm theo Topic.
const av = (auto: number, writing = 0, speaking = 0) => ({ AUTO: auto, WRITING: writing, SPEAKING: speaking })
const card = (id: number, title: string, extra: Partial<ExtraCard> = {}): ExtraCard => ({
  topicId: id, title, levelId: 1, levelTitle: 'Level 1', levelNo: 1, topicNo: id, state: 'OPEN', trialOnly: false, available: av(20), ...extra,
})
const overview = (extra: Partial<ExtraOverview> = {}): ExtraOverview => ({
  courseId: 1, courseSlug: 'ielts', courseTitle: 'IELTS A', reviewSize: 10, challengeSize: 12, maxRatio: 0.15,
  levels: [{ id: 1, title: 'Level 1' }, { id: 2, title: 'Level 2' }],
  review: [
    card(1, 'Greetings', { state: 'COMPLETED', accuracy: 92, questions: 18, available: av(40, 0, 10) }),
    card(2, 'Daily Life', { state: 'COMPLETED', levelId: 2, levelTitle: 'Level 2', accuracy: 86, questions: 24, available: av(20) }),
  ],
  challenge: [
    card(3, 'Technology', { available: av(100) }),
    card(4, 'Consumerism', { state: 'PAID', available: undefined }),
    card(5, 'Health', { state: 'LOCKED', available: undefined, blockedByTitle: 'Technology', blockedByTopicId: 3 }),
  ],
  ...extra,
})
const member = { '/api/auth/refresh': () => json(200, auth), '/api/me': () => json(200, { displayName: 'Lan' }) }
const roadmap = { levels: [{ id: 1, code: 'L1', title: 'L1', topics: [{ id: 1, title: 'Greetings', banks: [{ id: 41, title: 'B', lessonId: 1, questionCount: 5 }] }] }], resume: null }

describe('trần số câu', () => {
  test('min(N, 15% tổng, tối thiểu 1)', () => {
    expect(capped(12, 20, 0.15)).toBe(3)
    expect(capped(12, 100, 0.15)).toBe(12)
    expect(capped(12, 2, 0.15)).toBe(1)
    expect(capped(10, 40, 0.15)).toBe(6)
  })
})

describe('màn Luyện thêm', () => {
  beforeEach(() => setAccessToken('t'))
  afterEach(() => vi.unstubAllGlobals())
  const wire = (o: ExtraOverview | Response = overview()) =>
    mockApi({ ...member, '/api/me/courses/1/extra-practice': () => (o instanceof Response ? o : json(200, o)), '/api/me/courses/1/roadmap': () => json(200, roadmap) })

  test('hiện Ôn lại và Thử thách theo trạng thái từng Topic', async () => {
    wire()
    renderAt('/bai-tap/khoa/1/luyen-them')
    const review = await screen.findByRole('region', { name: 'Ôn lại' })
    expect(within(review).getByText('Đúng 92% · 18 câu')).toBeInTheDocument()
    await waitFor(() => expect(within(review).getAllByRole('link', { name: 'Ôn lại →' })[0]).toHaveAttribute('href', '/bai-tap/41'))
    const ch = screen.getByRole('region', { name: 'Thử thách' })
    expect(within(ch).getByRole('button', { name: 'Thử sức ngay →' })).toBeEnabled()
    expect(within(ch).getByText('Khó hơn · Thử sức 12 câu')).toBeInTheDocument()
    expect(within(ch).getByRole('link', { name: 'Mở khóa →' })).toHaveAttribute('href', '/khoa-hoc/ielts')
    expect(within(ch).getByText('Mở sau khi hoàn thành Technology')).toBeInTheDocument()
    expect(within(ch).getByRole('link', { name: 'Xem trước' })).toHaveAttribute('href', '/bai-giang/khoa/1/unit/5')
  })

  test('lọc Level; lọc dạng cho nút Luyện N câu theo trần 15%', async () => {
    wire()
    renderAt('/bai-tap/khoa/1/luyen-them')
    await screen.findByRole('region', { name: 'Ôn lại' })
    expect(screen.queryByRole('button', { name: /^Luyện \d+ câu/ })).toBeNull() // chưa lọc dạng thì chưa có nút
    await userEvent.click(screen.getByRole('button', { name: 'Tự chấm' }))
    expect(screen.getByRole('button', { name: 'Luyện 9 câu Tự chấm' })).toBeInTheDocument() // 15% của 60 = 9
    await userEvent.click(screen.getByRole('button', { name: 'Level 2' }))
    expect(screen.queryByRole('heading', { name: 'Greetings' })).toBeNull()
    expect(screen.getByRole('button', { name: 'Luyện 3 câu Tự chấm' })).toBeInTheDocument() // 15% của 20 = 3
    await userEvent.click(screen.getByRole('button', { name: 'Viết' }))
    expect(screen.getByRole('button', { name: 'Chưa có câu dạng này' })).toBeDisabled()
  })

  test('Thử sức tạo lượt rồi chuyển sang màn làm bài', async () => {
    const calls: unknown[] = []
    mockApi({
      ...member,
      '/api/me/courses/1/extra-practice': () => json(200, overview()),
      '/api/me/courses/1/roadmap': () => json(200, roadmap),
      '/api/me/courses/1/extra-practice/attempts': (body) => { calls.push(body); return json(201, { id: 77, bankId: 1, startedAt: 'x', timeLimitSec: null }) },
      '/api/me/extra-practice/attempts/77': () => json(500),
    })
    const router = renderAt('/bai-tap/khoa/1/luyen-them')
    await userEvent.click(await screen.findByRole('button', { name: 'Thử sức ngay →' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/bai-tap/luyen-them/77'))
    expect(calls).toEqual([{ scope: 'CHALLENGE', topicId: 3 }])
  })

  test('còn lượt dở thì làm tiếp lượt đó; lỗi khác báo trên thẻ', async () => {
    mockApi({
      ...member,
      '/api/me/courses/1/extra-practice': () => json(200, overview()),
      '/api/me/courses/1/roadmap': () => json(200, roadmap),
      '/api/me/courses/1/extra-practice/attempts': () => json(409, { error: { code: 'attempt_in_progress', message: 'x', details: { attemptId: 55 } } }),
    })
    const router = renderAt('/bai-tap/khoa/1/luyen-them')
    await userEvent.click(await screen.findByRole('button', { name: 'Thử sức ngay →' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/bai-tap/luyen-them/55'))
  })

  test('có lượt Luyện thêm chưa nộp thì nhắc nộp kèm nút Tiếp tục; không có thì không hiện', async () => {
    wire(overview({ openAttemptId: 31 }))
    renderAt('/bai-tap/khoa/1/luyen-them')
    const note = (await screen.findByText(/Kết quả Viết\/Nói của các câu trong lượt sẽ hiện lại sau khi bạn nộp/)).closest('[role="status"]') as HTMLElement
    expect(within(note).getByRole('link', { name: 'Tiếp tục' })).toHaveAttribute('href', '/bai-tap/luyen-them/31')
  })

  test('không có lượt mở thì không có ghi chú', async () => {
    wire()
    renderAt('/bai-tap/khoa/1/luyen-them')
    await screen.findByRole('region', { name: 'Ôn lại' })
    expect(screen.queryByRole('status')).toBeNull()
  })

  test('trạng thái trống và lỗi', async () => {
    wire(overview({ review: [], challenge: [] }))
    renderAt('/bai-tap/khoa/1/luyen-them')
    expect(await screen.findByText(/Chưa có Topic nào hoàn thành/)).toBeInTheDocument()
    expect(screen.getByText('Không có Topic nào để thử thách.')).toBeInTheDocument()
  })

  test('lỗi tải có nút thử lại; khóa không tồn tại báo không tìm thấy', async () => {
    wire(apiError(500, 'x'))
    renderAt('/bai-tap/khoa/1/luyen-them')
    expect(await screen.findByRole('button', { name: 'Thử lại' })).toBeInTheDocument()
    vi.unstubAllGlobals()
    wire(json(404, { error: { code: 'course_not_found', message: 'x' } }))
    renderAt('/bai-tap/khoa/1/luyen-them')
    expect(await screen.findAllByRole('heading', { name: 'Không tìm thấy khóa học' })).not.toHaveLength(0)
  })
})
