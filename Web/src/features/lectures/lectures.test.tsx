import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { apiError, auth, json, mockApi, renderAt } from '../../testUtils'
import type { RoadmapLesson, RoadmapTopic } from '../home/types'
import { ProgressTracker } from './ProgressTracker'
import { filterTopics, formatClock, lessonStatus, remainingSec, skillsIn, topicBadge } from './status'

const lesson = (id: number, title: string, extra: Partial<RoadmapLesson> = {}): RoadmapLesson => ({
  id, title, type: 'VIDEO', durationSec: 600, order: id % 100, completed: false, watchedSec: 0, maxPositionSec: null, lastWatchedAt: null, ...extra,
})
const topic = (id: number, title: string, extra: Partial<RoadmapTopic> = {}): RoadmapTopic => ({
  id, title, skill: 'WRITING', isFreeTrial: false, locked: false, completedLessons: 0, totalLessons: 0, xpEarned: 0, status: 'NOT_STARTED', lessons: [], banks: [], ...extra,
})

describe('trạng thái bài giảng (D-59)', () => {
  test('chưa xem / đang xem / đã xem', () => {
    expect(lessonStatus(lesson(1, 'a'))).toBe('NEW')
    expect(lessonStatus(lesson(1, 'a', { watchedSec: 30, maxPositionSec: 30 }))).toBe('WATCHING')
    expect(lessonStatus(lesson(1, 'a', { completed: true, watchedSec: 500 }))).toBe('DONE')
  })
  test('còn mm:ss = thời lượng - vị trí đã dừng', () => {
    expect(remainingSec(1110, 588)).toBe(522)
    expect(formatClock(522)).toBe('08:42')
    expect(formatClock(3725)).toBe('1:02:05')
    expect(remainingSec(null, 5)).toBeNull()
    expect(remainingSec(600, null)).toBeNull()
  })
  test('badge topic', () => {
    expect(topicBadge(topic(1, 'a', { status: 'DONE', completedLessons: 3, totalLessons: 3 })).text).toBe('Hoàn thành')
    expect(topicBadge(topic(1, 'a', { status: 'IN_PROGRESS', completedLessons: 2, totalLessons: 5 })).text).toBe('Đã xem 2/5')
    expect(topicBadge(topic(1, 'a')).text).toBe('Chưa bắt đầu')
    expect(topicBadge(topic(1, 'a', { locked: true, status: 'LOCKED' })).text).toBe('Đã khóa')
  })
})

describe('bộ lọc', () => {
  const topics = [
    topic(1, 'Listening', { skill: 'LISTENING', lessons: [lesson(1, 'Nghe hiểu 4 phần', { completed: true })] }),
    topic(2, 'Writing', { skill: 'WRITING', lessons: [lesson(2, 'Mở bài trong 3 câu', { watchedSec: 9, maxPositionSec: 9 }), lesson(3, 'Từ nối')] }),
    topic(3, 'Khác', { skill: null }),
  ]
  const none = { query: '', skill: null, status: null }
  test('tìm theo tên không phân biệt dấu', () => {
    const r = filterTopics(topics, { ...none, query: 'mo bai' })
    expect(r.map((x) => x.topic.id)).toEqual([2])
    expect(r[0].lessons.map((l) => l.id)).toEqual([2])
  })
  test('lọc trạng thái và kỹ năng', () => {
    expect(filterTopics(topics, { ...none, status: 'DONE' }).map((x) => x.topic.id)).toEqual([1])
    expect(filterTopics(topics, { ...none, skill: 'WRITING' }).map((x) => x.topic.id)).toEqual([2])
    expect(filterTopics(topics, none)).toHaveLength(3)
  })
  test('chip kỹ năng chỉ gồm kỹ năng có trong khóa', () => {
    expect(skillsIn([{ id: 1, code: 'L1', title: 'L', topics }])).toEqual(['LISTENING', 'WRITING'])
  })
})

describe('ProgressTracker', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())
  const make = () => {
    const posts: { positionSec: number; watchedDeltaSec: number; final: boolean }[] = []
    let pos = 0
    const t = new ProgressTracker((b, final) => posts.push({ ...b, final }), () => pos)
    return { t, posts, setPos: (p: number) => (pos = p) }
  }

  test('gửi mỗi 15 giây khi đang phát, delta là giây phát thật', () => {
    const { t, posts, setPos } = make()
    t.play()
    setPos(15)
    vi.advanceTimersByTime(15_000)
    setPos(30)
    vi.advanceTimersByTime(15_000)
    expect(posts).toEqual([
      { positionSec: 15, watchedDeltaSec: 15, final: false },
      { positionSec: 30, watchedDeltaSec: 15, final: false },
    ])
  })

  test('tạm dừng gửi nốt phần còn lại và dừng hẹn giờ', () => {
    const { t, posts, setPos } = make()
    t.play()
    vi.advanceTimersByTime(7_000)
    setPos(7)
    t.pause()
    expect(posts).toEqual([{ positionSec: 7, watchedDeltaSec: 7, final: true }])
    vi.advanceTimersByTime(60_000)
    expect(posts).toHaveLength(1)
  })

  test('delta tối đa 20 giây dù tab bị trình duyệt làm chậm', () => {
    const { t, posts } = make()
    t.play()
    vi.setSystemTime(Date.now() + 90_000)
    t.flush(false)
    expect(posts[0].watchedDeltaSec).toBe(20)
  })

  test('không gửi lặp khi chưa phát và vị trí không đổi', () => {
    const { t, posts } = make()
    t.pause()
    expect(posts).toHaveLength(0)
    t.flush(false)
    t.flush(false)
    expect(posts).toHaveLength(1)
  })

  test('đổi tab gửi ngay nhưng vẫn đếm tiếp', () => {
    const { t, posts, setPos } = make()
    t.play()
    vi.advanceTimersByTime(5_000)
    setPos(5)
    t.flush(false)
    vi.advanceTimersByTime(4_000)
    setPos(9)
    t.pause()
    expect(posts.map((p) => p.watchedDeltaSec)).toEqual([5, 4])
  })
})

describe('màn hình', () => {
  beforeEach(() => {
    setAccessToken(null)
    localStorage.clear()
  })
  afterEach(() => vi.unstubAllGlobals())

  const mine = (id: number, status: string, completed: number, total: number, title = `Khóa ${id}`) => ({
    courseId: id, slug: `khoa-${id}`, title, targetType: 'IELTS', status, expiresAt: null, lastActivityAt: null, completedLessons: completed, totalLessons: total,
  })
  const member = { '/api/auth/refresh': () => json(200, auth), '/api/me': () => json(200, { displayName: 'Lan' }) }

  test('chọn khóa: khóa hết hạn bị khóa + Gia hạn, khóa học thử có nhãn, thẻ Muốn học thêm', async () => {
    mockApi({
      ...member,
      '/api/me/courses': () => json(200, [mine(1, 'ACTIVE', 2, 5, 'Giao tiếp'), mine(2, 'EXPIRED', 5, 5, 'Cũ'), mine(3, 'TRIAL', 1, 20, 'Thử')]),
    })
    renderAt('/bai-giang')
    expect(await screen.findByRole('heading', { name: 'Giao tiếp' })).toBeInTheDocument()
    const expired = screen.getByRole('heading', { name: 'Cũ' }).closest('article')!
    expect(within(expired).getByRole('link', { name: 'Gia hạn' })).toHaveAttribute('href', '/thanh-toan?khoa=khoa-2')
    expect(within(expired).queryByRole('link', { name: /Xem bài giảng/ })).toBeNull()
    const trial = screen.getByRole('heading', { name: 'Thử' }).closest('article')!
    expect(within(trial).getByText('Học thử')).toBeInTheDocument()
    expect(within(screen.getByRole('heading', { name: 'Giao tiếp' }).closest('article')!).getByRole('link', { name: 'Xem bài giảng →' })).toHaveAttribute('href', '/bai-giang/khoa/1')
    expect(screen.getByRole('link', { name: 'Đến trang Khóa học →' })).toHaveAttribute('href', '/khoa-hoc')
  })

  const roadmap = {
    levels: [
      {
        id: 1, code: 'L1', title: 'Level 1',
        topics: [
          topic(10, 'Writing Task 2', {
            skill: 'WRITING', status: 'IN_PROGRESS', completedLessons: 1, totalLessons: 2,
            lessons: [lesson(101, 'Phân tích đề', { completed: true, watchedSec: 500, maxPositionSec: 500 }), lesson(102, 'Mở bài trong 3 câu', { watchedSec: 60, maxPositionSec: 60 })],
            banks: [{ id: 7, title: 'Bộ 1', lessonId: 101, questionCount: 5 }],
          }),
          topic(11, 'Speaking Part 1', { skill: 'SPEAKING', locked: true, status: 'LOCKED', totalLessons: 4 }),
        ],
      },
    ],
    resume: { lessonId: 102, lessonTitle: 'Mở bài trong 3 câu', lessonOrder: 2, topicId: 10, topicTitle: 'Writing Task 2', levelId: 1, levelCode: 'L1', levelTitle: 'Level 1', completedInTopic: 1, totalInTopic: 2, positionSec: 60, durationSec: 600 },
  }
  const courseApi = { ...member, '/api/me/courses': () => json(200, [mine(1, 'ACTIVE', 1, 6, 'IELTS')]), '/api/me/courses/1/roadmap': () => json(200, roadmap) }

  test('hệ thống bài giảng: Xem tiếp, chip kỹ năng, topic khóa dẫn tới trang mua, tìm bài', async () => {
    mockApi(courseApi)
    renderAt('/bai-giang/khoa/1')
    const hero = await screen.findByRole('region', { name: 'Học tiếp' })
    expect(within(hero).getByRole('link', { name: 'Xem tiếp' })).toHaveAttribute('href', '/bai-giang/102')
    expect(within(hero).getAllByText(/còn 09:00/).length).toBeGreaterThan(0)
    const skills = screen.getByRole('group', { name: 'Lọc theo kỹ năng' })
    expect(within(skills).getAllByRole('button').map((b) => b.textContent)).toEqual(['Mọi kỹ năng', 'Writing', 'Speaking'])
    expect(screen.getByRole('link', { name: 'Mua khóa học' })).toHaveAttribute('href', '/thanh-toan?khoa=khoa-1')
    // topic đang học mở sẵn, thẻ bài có nút bài tập khớp lessonId
    expect(screen.getByRole('link', { name: 'Làm bài tập →' })).toHaveAttribute('href', '/bai-tap/7')
    expect(screen.getAllByText('Đã xem').length).toBeGreaterThan(0)

    await userEvent.type(screen.getByRole('searchbox', { name: 'Tìm bài giảng' }), 'mo bai')
    expect(screen.getByRole('link', { name: /Mở bài trong 3 câu/ })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Phân tích đề/ })).toBeNull()
    expect(screen.queryByText('Speaking Part 1')).toBeNull()
  })

  const lessonBody = {
    id: 101, title: 'Phân tích đề', type: 'VIDEO', durationSec: 600, video: { source: 'STORAGE', url: 'https://cdn.test/v.mp4' },
    courseId: 1, courseSlug: 'khoa-1', courseTitle: 'IELTS', topicId: 10, topicTitle: 'Writing Task 2', levelId: 1,
  }

  test('xem bài: video có nodownload, đã xem xong hiện thẻ làm bài tập, gửi tiến độ khi kết thúc', async () => {
    const posted: unknown[] = []
    mockApi({
      ...courseApi,
      '/api/lessons/101': () => json(200, lessonBody),
      '/api/lessons/101/progress': (body) => {
        if (body) posted.push(body)
        return json(200, { lessonId: 101, watchedSec: 0, maxPositionSec: 0, completed: false, completedAt: null })
      },
    })
    renderAt('/bai-giang/101')
    const video = await waitFor(() => {
      const v = document.querySelector('video')
      expect(v).not.toBeNull()
      return v as HTMLVideoElement
    })
    expect(video).toHaveAttribute('controlsList', 'nodownload')
    expect(video.src).toBe('https://cdn.test/v.mp4')
    expect(screen.queryByRole('region', { name: 'Xem xong bài' })).toBeNull()
    // sidebar cùng topic
    await screen.findByRole('link', { name: /Bài 2 · Mở bài trong 3 câu/ })

    fireEvent.play(video)
    fireEvent.ended(video)
    const card = await screen.findByRole('region', { name: 'Xem xong bài' })
    expect(within(card).getByRole('link', { name: /Làm bài tập Bài 1/ })).toHaveAttribute('href', '/bai-tap/7')
    expect(within(card).getByRole('link', { name: 'Danh sách bài' })).toHaveAttribute('href', '/bai-giang/khoa/1')
    await waitFor(() => expect(posted.length).toBeGreaterThan(0))
  })

  test('403 lesson_locked: hiện trạng thái khóa với link mua', async () => {
    mockApi({
      ...member,
      '/api/lessons/101': () => json(403, { error: { code: 'lesson_locked', message: 'x' } }),
      '/api/lessons/101/progress': () => apiError(403, 'x'),
    })
    renderAt('/bai-giang/101')
    expect(await screen.findByRole('heading', { name: 'Bài giảng chưa mở khóa' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Mua khóa học' })).toHaveAttribute('href', '/khoa-hoc')
  })

  test('khách xem bài thử: không gọi tiến độ, sidebar lấy từ danh mục công khai', async () => {
    const calls = mockApi({
      '/api/lessons/101': () => json(200, lessonBody),
      '/api/catalog/courses/khoa-1': () =>
        json(200, { levels: [{ id: 1, code: 'L1', title: 'L', topics: [{ id: 10, title: 'Writing Task 2', skill: 'WRITING', isFreeTrial: true, lessons: [{ id: 101, title: 'Phân tích đề', type: 'VIDEO', durationSec: 600 }, { id: 102, title: 'Mở bài', type: 'VIDEO', durationSec: 300 }] }] }], products: [] }),
    })
    renderAt('/bai-giang/101')
    expect(await screen.findByRole('link', { name: /Bài 2 · Mở bài/ })).toBeInTheDocument()
    const video = document.querySelector('video') as HTMLVideoElement
    fireEvent.play(video)
    fireEvent.pause(video)
    expect(calls.some((c) => c.includes('/progress'))).toBe(false)
  })

  test('URL ký hết hạn: lỗi media làm tải lại bài đúng một lần', async () => {
    let n = 0
    mockApi({
      ...member,
      '/api/lessons/101': () => json(200, { ...lessonBody, video: { source: 'STORAGE', url: `https://cdn.test/v${++n}.mp4` } }),
      '/api/lessons/101/progress': () => json(200, { lessonId: 101, watchedSec: 0, maxPositionSec: 0, completed: false, completedAt: null }),
      '/api/me/courses/1/roadmap': () => json(200, roadmap),
    })
    renderAt('/bai-giang/101')
    const first = await waitFor(() => {
      expect(document.querySelector('video')).not.toBeNull()
      return document.querySelector('video') as HTMLVideoElement
    })
    act(() => void fireEvent.error(first))
    await waitFor(() => expect((document.querySelector('video') as HTMLVideoElement).src).toBe('https://cdn.test/v2.mp4'))
    fireEvent.error(document.querySelector('video') as HTMLVideoElement)
    expect(await screen.findByRole('alert')).toHaveTextContent('Không phát được video')
    expect(n).toBe(2)
  })
})

test('bài chưa có video (video: null): hiện trạng thái trống, không báo tiến độ, phần còn lại vẫn chạy', async () => {
  setAccessToken(null)
  const calls = mockApi({
    '/api/auth/refresh': () => json(200, auth),
    '/api/me': () => json(200, { displayName: 'Lan' }),
    '/api/lessons/10': () => json(200, { id: 10, title: 'Chưa có video', type: 'VIDEO', durationSec: null, video: null, courseId: 1, courseSlug: 'k', courseTitle: 'K', topicId: 5, topicTitle: 'Chủ đề', levelId: 1 }),
    '/api/lessons/10/progress': () => json(200, { lessonId: 10, watchedSec: 0, maxPositionSec: 0, completed: false, completedAt: null }),
    '/api/me/courses/1/roadmap': () => json(200, { levels: [], resume: null }),
  })
  renderAt('/bai-giang/10')
  expect(await screen.findByText('Bài giảng chưa có video.')).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Chưa có video' })).toBeInTheDocument()
  expect(document.querySelector('video')).toBeNull()
  expect(screen.queryByRole('region', { name: 'Xem xong bài' })).toBeNull()
  expect(calls.filter((c) => c.endsWith('/progress') && c !== '/api/lessons/10/progress')).toHaveLength(0)
  vi.unstubAllGlobals()
})
