import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { apiError, auth, json, mockApi, renderAt } from '../../testUtils'
import { pickLevel, vnToday, weekDates } from './format'
import type { Roadmap } from './types'

const topic = (id: number, status: string, extra = {}) => ({
  id, title: `Chủ đề ${id}`, isFreeTrial: false, locked: status === 'LOCKED', completedLessons: 0, totalLessons: 5, xpEarned: 0, status, lessons: [], banks: [], ...extra,
})
const roadmap = {
  levels: [
    {
      id: 1, code: 'L2', title: 'Level 2',
      topics: [topic(1, 'DONE', { xpEarned: 60, completedLessons: 5 }), topic(2, 'IN_PROGRESS', { completedLessons: 3 }), topic(3, 'NOT_STARTED'), topic(4, 'LOCKED')],
    },
  ],
  resume: { lessonId: 11, lessonTitle: 'Triển khai luận điểm', lessonOrder: 3, topicId: 2, topicTitle: 'Environment', levelId: 1, levelCode: 'L2', levelTitle: 'Level 2', completedInTopic: 3, totalInTopic: 5 },
}
const stats = { xp: 1240, streakCurrent: 12, streakBest: 20, lastActiveDate: '2026-11-20', title: 'A', nextTitle: 'B', nextTitleXp: 2000, activeToday: false, weekDays: ['2026-11-16', '2026-11-17', '2026-11-18', '2026-11-19', '2026-11-20'] }
const today = {
  courseId: 7, doneCount: 0, total: 3,
  tasks: [
    { kind: 'VIDEO', title: 'Because of, Due to', subtitle: null, lessonId: 11, bankId: null, questionId: null, remainingSec: 522, questionCount: null, doneToday: false },
    { kind: 'EXERCISE', title: 'Trắc nghiệm ngữ cảnh', subtitle: null, lessonId: null, bankId: 5, questionId: null, remainingSec: null, questionCount: 8, doneToday: false },
    { kind: 'WRITING', title: 'Viết', subtitle: 'Trả lời câu hỏi dẫn dắt', lessonId: null, bankId: null, questionId: 9, remainingSec: null, questionCount: null, doneToday: false },
  ],
}
const quota = { pool: 2, courses: [{ courseId: 7, courseTitle: 'IELTS', period: 'MONTH', quota: 10, used: 4, remaining: 6, resetsAt: '2026-12-01T00:00:00Z' }] }
const cam = { messages: [{ kind: 'STREAK', text: 'Giữ chuỗi nhé!', action: { label: 'Học ngay', path: '/bai-giang/11' } }, { kind: 'GREETING', text: 'Chào bạn', action: null }] }

const full = {
  '/api/auth/refresh': () => json(200, auth),
  '/api/me': () => json(200, { displayName: 'Lan' }),
  '/api/me/stats': () => json(200, stats),
  '/api/me/notifications/unread-count': () => json(200, { count: 3 }),
  '/api/me/courses': () => json(200, [{ courseId: 7, slug: 'ielts-7', title: 'IELTS', targetType: 'IELTS', status: 'ACTIVE', expiresAt: null, lastActivityAt: null }]),
  '/api/me/courses/7/roadmap': () => json(200, roadmap),
  '/api/me/today?courseId=7': () => json(200, today),
  '/api/me/quota': () => json(200, quota),
  '/api/me/cam-messages': () => json(200, cam),
}

beforeEach(() => {
  setAccessToken(null)
  sessionStorage.clear()
  // Thứ Bảy 21/11/2026 12:00 giờ VN
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-11-21T05:00:00Z'))
})
afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

test('có khóa: header, thẻ học tiếp, lộ trình, tuần, việc hôm nay, thẻ AI', async () => {
  mockApi(full)
  renderAt('/')
  expect(await screen.findByRole('heading', { name: 'Xin chào, Lan!' })).toBeInTheDocument()
  expect(await screen.findByText('1.240 XP')).toBeInTheDocument()
  expect(screen.getByText(/^12/)).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Thông báo, 3 chưa đọc' })).toHaveAttribute('href', '/thong-bao')

  // thẻ học tiếp
  expect(await screen.findByText('Environment · Level 2')).toBeInTheDocument()
  expect(screen.getByText('Bài 3 · Triển khai luận điểm')).toBeInTheDocument()
  expect(screen.getByRole('progressbar', { name: 'Tiến độ chủ đề' })).toHaveAttribute('aria-valuenow', '3')
  expect(screen.getByRole('link', { name: /Học tiếp ngay/ })).toHaveAttribute('href', '/bai-giang/11')

  // lộ trình đủ 4 trạng thái
  expect(await screen.findByRole('heading', { name: 'Lộ trình · Level 2' })).toBeInTheDocument()
  expect(screen.getByText('Hoàn thành · +60 XP')).toBeInTheDocument()
  expect(screen.getByText('Đang học · 3/5 bài')).toBeInTheDocument()
  expect(screen.getByText('Chưa bắt đầu · 0/5 bài')).toBeInTheDocument()
  expect(screen.getByText('Mở khóa khi mua khóa học')).toBeInTheDocument()
  expect(screen.getByText('Trả phí')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /Xem chi tiết/ })).toHaveAttribute('href', '/khoa-hoc/ielts-7')

  // tuần: 5 ô đã học, hôm nay đứt nét, CN xám
  expect(await screen.findByText('Hôm nay (T7) chưa học. Làm 1 bài để giữ chuỗi 12 ngày.')).toBeInTheDocument()
  const week = screen.getByRole('list', { name: 'Các ngày trong tuần' })
  const states = within(week).getAllByRole('listitem').map((li) => li.querySelector('[data-state]')?.getAttribute('data-state'))
  expect(states).toEqual(['done', 'done', 'done', 'done', 'done', 'today', 'idle'])

  // việc hôm nay
  expect(await screen.findByText('0/3 hoàn thành')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /Xem video/ })).toHaveAttribute('href', '/bai-giang/11')
  expect(screen.getByText('Đang học · còn 08:42')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /Làm bài tập/ })).toHaveAttribute('href', '/bai-tap/5')
  expect(screen.getByText('Trắc nghiệm ngữ cảnh · 8 câu')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /Viết phát triển ý/ })).toHaveAttribute('href', '/viet/9')

  // thẻ AI
  expect(await screen.findByText('Luyện viết một đoạn ngắn về Environment')).toBeInTheDocument()
  expect(screen.getByText(/còn 6 lượt · \+2 lượt mua thêm/)).toBeInTheDocument()
  expect(screen.getByRole('progressbar', { name: 'Lượt chấm AI đã dùng' })).toHaveAttribute('aria-valuenow', '4')
  expect(screen.getByRole('link', { name: /Hỏi trợ lý AI/ })).toHaveAttribute('href', '/viet/9')
})

test('chưa có khóa: thẻ mời, ẩn lộ trình, việc hôm nay và thẻ AI', async () => {
  mockApi({ ...full, '/api/me/courses': () => json(200, []) })
  renderAt('/')
  expect(await screen.findByRole('heading', { name: 'Bắt đầu hành trình' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Xem khóa học' })).toHaveAttribute('href', '/khoa-hoc')
  expect(screen.getByRole('link', { name: 'Kiểm tra đầu vào' })).toHaveAttribute('href', '/kiem-tra-dau-vao')
  expect(screen.queryByRole('heading', { name: /Lộ trình/ })).not.toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Việc hôm nay' })).not.toBeInTheDocument()
  expect(screen.queryByRole('region', { name: 'AI gợi ý' })).not.toBeInTheDocument()
})

test('resume null: báo đã học xong khóa', async () => {
  mockApi({ ...full, '/api/me/courses/7/roadmap': () => json(200, { ...roadmap, resume: null }) })
  renderAt('/')
  expect(await screen.findByText('Bạn đã học xong khóa này.')).toBeInTheDocument()
})

test('Cam: hiện tin, đổi tin, link action, đóng và nhớ trong phiên', async () => {
  mockApi(full)
  const user = userEvent.setup({ advanceTimers: () => {} })
  renderAt('/')
  const bubble = await screen.findByRole('region', { name: 'Gợi ý từ Cam' })
  expect(within(bubble).getByText('Giữ chuỗi nhé!')).toBeInTheDocument()
  expect(within(bubble).getByRole('link', { name: 'Học ngay' })).toHaveAttribute('href', '/bai-giang/11')
  await user.click(within(bubble).getByRole('button', { name: 'Tin sau' }))
  expect(within(bubble).getByText('Chào bạn')).toBeInTheDocument()
  await user.click(within(bubble).getByRole('button', { name: 'Đóng gợi ý của Cam' }))
  expect(screen.queryByRole('region', { name: 'Gợi ý từ Cam' })).not.toBeInTheDocument()
  expect(sessionStorage.getItem('cam-bubble-dismissed')).toBe('1')
  await user.click(screen.getByRole('button', { name: 'Mở gợi ý của Cam' }))
  expect(screen.getByRole('region', { name: 'Gợi ý từ Cam' })).toBeInTheDocument()
})

test('Cam đã đóng trong phiên thì không tự bật lại', async () => {
  sessionStorage.setItem('cam-bubble-dismissed', '1')
  mockApi(full)
  renderAt('/')
  expect(await screen.findByRole('button', { name: 'Mở gợi ý của Cam' })).toBeInTheDocument()
  expect(screen.queryByRole('region', { name: 'Gợi ý từ Cam' })).not.toBeInTheDocument()
})

test('một API lỗi không làm hỏng cả trang', async () => {
  mockApi({ ...full, '/api/me/today?courseId=7': () => apiError(403, 'x') })
  renderAt('/')
  expect(await screen.findByText('Environment · Level 2')).toBeInTheDocument()
  const section = (await screen.findByRole('heading', { name: 'Việc hôm nay' })).closest('section')!
  expect(await within(section).findByRole('alert')).toHaveTextContent('Không tải được phần này.')
  expect(within(section).getByRole('button', { name: 'Thử lại' })).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Lộ trình · Level 2' })).toBeInTheDocument()
})

test('tuần: đã học hôm nay và chưa có chuỗi', async () => {
  mockApi({ ...full, '/api/me/stats': () => json(200, { ...stats, streakCurrent: 0, weekDays: [] }) })
  renderAt('/')
  expect(await screen.findByText('Hôm nay chưa học. Làm 1 bài để bắt đầu chuỗi ngày học.')).toBeInTheDocument()
})

test('tuần: đã học hôm nay', async () => {
  mockApi({ ...full, '/api/me/stats': () => json(200, { ...stats, activeToday: true, weekDays: [...stats.weekDays, '2026-11-21'] }) })
  renderAt('/')
  await waitFor(() => expect(screen.getByText('Hôm nay đã học. Chuỗi hiện tại 12 ngày.')).toBeInTheDocument())
})

test('helper ngày giờ Việt Nam và chọn level', () => {
  // 18:00 UTC thứ Bảy đã là Chủ nhật ở VN
  expect(vnToday(new Date('2026-11-21T18:00:00Z'))).toBe('2026-11-22')
  expect(weekDates('2026-11-22').map((d) => d.date)).toEqual(['2026-11-16', '2026-11-17', '2026-11-18', '2026-11-19', '2026-11-20', '2026-11-21', '2026-11-22'])
  const r = { resume: null, levels: [{ id: 1, topics: [{ completedLessons: 2 }] }, { id: 2, topics: [{ completedLessons: 0, status: 'NOT_STARTED' }] }] } as unknown as Roadmap
  expect(pickLevel(r)?.id).toBe(1)
})
