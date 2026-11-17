import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { apiError, auth, json, mockApi, renderAt } from '../../testUtils'

const stats = { xp: 1240, streakCurrent: 12, streakBest: 20, lastActiveDate: null, title: 'x', nextTitle: null, nextTitleXp: null, activeToday: false, weekDays: [] }
const course = { courseId: 1, slug: 'ielts', title: 'IELTS Intermediate', targetType: 'IELTS', status: 'ACTIVE', expiresAt: null }
const roadmap = { levels: [], resume: { lessonId: 55, lessonTitle: 'L', lessonOrder: 1, topicId: 1, topicTitle: 'T', levelId: 1, levelCode: 'A', levelTitle: 'A', completedInTopic: 0, totalInTopic: 1, positionSec: 0, durationSec: null } }
const qa = { selfLevel: 'TRUNG_CAP', goals: ['IELTS_CERT'], targetBand: 7, weeklyHours: 'H3_5', skills: [], updatedAt: 'x' }
const goal = { id: 1, courseId: null, targetType: 'IELTS', targetValue: '6.5', targetDate: null }
const future = '2999-01-01'
const dateLabel = 'Ngày mục tiêu · Không bắt buộc'

const base = {
  '/api/auth/refresh': () => json(200, auth),
  '/api/me/stats': () => json(200, stats),
  '/api/me/courses': () => json(200, [course]),
  '/api/me/courses/1/roadmap': () => json(200, roadmap),
  '/api/me/qa': () => json(200, qa),
  '/api/me/goals': () => json(200, [goal]),
}

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

test('chuỗi học: số liệu, câu trạng thái và đích Học tiếp ngay như Trang chủ', async () => {
  mockApi(base)
  renderAt('/chuoi-hoc')
  expect(await screen.findByText('12 ngày liên tiếp')).toBeInTheDocument()
  expect(screen.getByText('Hôm nay chưa ghi nhận hoạt động học.')).toBeInTheDocument()
  expect(screen.getByText('20 ngày')).toBeInTheDocument()
  expect(screen.getByText('1.240 XP')).toBeInTheDocument()
  await screen.findByRole('link', { name: 'Học tiếp ngay' })
  await vi.waitFor(() => expect(screen.getByRole('link', { name: 'Học tiếp ngay' })).toHaveAttribute('href', '/bai-giang/55'))
  expect(screen.getByRole('link', { name: 'Về hồ sơ' })).toHaveAttribute('href', '/ho-so')
})

test('chuỗi học: chuỗi 0 hiện câu khích lệ, không bịa số', async () => {
  mockApi({ ...base, '/api/me/stats': () => json(200, { ...stats, streakCurrent: 0, streakBest: 0, xp: 0 }) })
  renderAt('/chuoi-hoc')
  expect(await screen.findByText('Chưa có chuỗi')).toBeInTheDocument()
  expect(screen.getByText('Học một bài hôm nay để bắt đầu chuỗi mới.')).toBeInTheDocument()
  expect(screen.queryByText(/ngày liên tiếp/)).not.toBeInTheDocument()
})

test('chuỗi học: activeToday hiện Hôm nay đã học', async () => {
  mockApi({ ...base, '/api/me/stats': () => json(200, { ...stats, activeToday: true }) })
  renderAt('/chuoi-hoc')
  expect(await screen.findByText('Hôm nay đã học')).toBeInTheDocument()
})

test('mục tiêu: điền sẵn band, lưu đúng body, hiện trạng thái đã lưu', async () => {
  let body: unknown
  mockApi({ ...base, '/api/me/goals/ielts': (b) => ((body = b), json(200, goal)) })
  renderAt('/muc-tieu')
  const band = await screen.findByLabelText('Band mục tiêu')
  expect(band).toHaveValue('6.5')
  expect(await screen.findByText('IELTS Intermediate')).toBeInTheDocument()
  const user = userEvent.setup()
  await user.selectOptions(band, '7.5')
  await user.type(screen.getByLabelText(dateLabel), future)
  await user.click(screen.getByRole('button', { name: 'Lưu mục tiêu' }))
  expect(await screen.findByText('Đã lưu mục tiêu')).toBeInTheDocument()
  expect(body).toEqual({ targetBand: 7.5, targetDate: future })
  expect(screen.getByText('IELTS 7.5')).toBeInTheDocument()
  expect(screen.getByText('01/01/2999')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Về hồ sơ' })).toHaveAttribute('href', '/ho-so')
})

test('mục tiêu: chưa có mục tiêu IELTS thì lấy band Q&A; không gửi ngày khi để trống', async () => {
  let body: unknown
  mockApi({ ...base, '/api/me/goals': () => json(200, []), '/api/me/goals/ielts': (b) => ((body = b), json(200, goal)) })
  renderAt('/muc-tieu')
  expect(await screen.findByLabelText('Band mục tiêu')).toHaveValue('7.0')
  await userEvent.setup().click(screen.getByRole('button', { name: 'Lưu mục tiêu' }))
  expect(await screen.findByText('Đã lưu mục tiêu')).toBeInTheDocument()
  expect(body).toEqual({ targetBand: 7 })
})

test('mục tiêu: không có band nào thì để trống và bắt buộc chọn; ngày quá khứ bị chặn', async () => {
  mockApi({ ...base, '/api/me/goals': () => json(200, []), '/api/me/qa': () => json(204) })
  renderAt('/muc-tieu')
  const user = userEvent.setup()
  expect(await screen.findByLabelText('Band mục tiêu')).toHaveValue('')
  await user.click(screen.getByRole('button', { name: 'Lưu mục tiêu' }))
  expect(await screen.findByText('Vui lòng chọn band mục tiêu')).toBeInTheDocument()
  await user.selectOptions(screen.getByLabelText('Band mục tiêu'), '6.0')
  await user.type(screen.getByLabelText(dateLabel), '2020-01-01')
  await user.click(screen.getByRole('button', { name: 'Lưu mục tiêu' }))
  expect(await screen.findByText('Ngày mục tiêu phải sau hôm nay')).toBeInTheDocument()
})

test('mục tiêu: lỗi validation của server hiện dưới ô; Hủy về hồ sơ', async () => {
  mockApi({
    ...base,
    '/api/me': () => json(200, { id: 1, email: 'a@b.c', emailVerified: true, role: 'STUDENT', displayName: 'Lan', selfLevel: null }),
    '/api/me/goals/ielts': () => apiError(400, 'Dữ liệu không hợp lệ', { targetDate: 'Ngày mục tiêu phải sau hôm nay' }),
  })
  renderAt('/muc-tieu')
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText(dateLabel), future)
  await user.click(screen.getByRole('button', { name: 'Lưu mục tiêu' }))
  expect(await screen.findByText('Ngày mục tiêu phải sau hôm nay')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Hủy' }))
  expect(await screen.findByRole('heading', { name: 'Hồ sơ', level: 1 })).toBeInTheDocument()
})
