import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { apiError, auth, json, mockApi, renderAt } from '../../testUtils'
import { initials } from './Overview'

const me = { id: 1, email: 'minh.anh@example.com', emailVerified: true, role: 'STUDENT', displayName: 'Minh Anh', selfLevel: 'TRUNG_CAP' }
const stats = { xp: 1240, streakCurrent: 12, streakBest: 20, lastActiveDate: null, title: 'x', nextTitle: null, nextTitleXp: null, activeToday: false, weekDays: [] }
const course = { courseId: 1, slug: 'ielts', title: 'IELTS Intermediate', targetType: 'IELTS', status: 'ACTIVE', expiresAt: null }
const qa = { selfLevel: 'TRUNG_CAP', goals: ['IELTS_CERT'], targetBand: 7, weeklyHours: 'H3_5', skills: [], updatedAt: 'x' }

const base = {
  '/api/auth/refresh': () => json(200, auth),
  '/api/me': () => json(200, me),
  '/api/me/stats': () => json(200, stats),
  '/api/me/courses': () => json(200, [course]),
  '/api/me/qa': () => json(200, qa),
  '/api/me/goals': () => json(200, []),
}

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

test('initials lấy hai từ cuối, rơi về email', () => {
  expect(initials('Minh Anh', 'a@b.c')).toBe('MA')
  expect(initials('Nguyễn Văn An', 'a@b.c')).toBe('VA')
  expect(initials('  ', 'zed@b.c')).toBe('ZE')
})

test('tổng quan hiện đủ dữ liệu và link đích', async () => {
  mockApi(base)
  renderAt('/ho-so')
  expect(await screen.findByRole('heading', { name: 'Hồ sơ', level: 1 })).toBeInTheDocument()
  expect(screen.getByText('minh.anh@example.com')).toBeInTheDocument()
  expect(await screen.findByText('IELTS Intermediate')).toBeInTheDocument()
  expect(await screen.findByText('IELTS 7.0')).toBeInTheDocument()
  expect(screen.getByText('Trung cấp')).toBeInTheDocument()
  expect(await screen.findByText('1.240')).toBeInTheDocument()
  expect(screen.getByText('12 ngày')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Chỉnh sửa mục tiêu' })).toHaveAttribute('href', '/muc-tieu')
  expect(screen.getByRole('link', { name: 'Xem chuỗi học' })).toHaveAttribute('href', '/chuoi-hoc')
  expect(screen.getByRole('link', { name: 'Xem hồ sơ năng lực' })).toHaveAttribute('href', '/ho-so/nang-luc')
  expect(screen.getByRole('link', { name: 'Đơn hàng' })).toHaveAttribute('href', '/don-hang')
})

test('chưa có khóa và chưa làm Q&A; stats lỗi chỉ thẻ đó hiện —', async () => {
  mockApi({ ...base, '/api/me/courses': () => json(200, []), '/api/me/qa': () => json(204), '/api/me/stats': () => apiError(404, 'x') })
  renderAt('/ho-so')
  expect(await screen.findByText(/Chưa đăng ký/)).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Xem khóa học' })).toHaveAttribute('href', '/khoa-hoc')
  expect(await screen.findByText('Chưa đặt')).toBeInTheDocument()
  await waitFor(() => expect(screen.getAllByText('—')).toHaveLength(2))
  expect(screen.getByRole('button', { name: 'Chỉnh sửa hồ sơ' })).toBeInTheDocument()
})

test('sửa và lưu: PATCH đúng body, banner đã lưu, tên mới hiện', async () => {
  let body: unknown
  let current = me
  mockApi({
    ...base,
    '/api/me': (b) => (b ? ((body = b), (current = { ...me, ...(b as object) }), json(200, current)) : json(200, current)),
  })
  renderAt('/ho-so')
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Chỉnh sửa hồ sơ' }))
  expect(screen.getByLabelText('Email')).toHaveAttribute('readonly')
  expect(screen.getByText('Email dùng để đăng nhập, chưa đổi được.')).toBeInTheDocument()
  const name = screen.getByLabelText('Tên hiển thị')
  await user.clear(name)
  await user.type(name, '  Lan Anh ')
  await user.selectOptions(screen.getByLabelText('Trình độ tự đánh giá'), 'Khá')
  await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))
  expect(await screen.findByText('Đã lưu thông tin hồ sơ.')).toBeInTheDocument()
  expect(body).toEqual({ displayName: 'Lan Anh', selfLevel: 'KHA' })
  expect(await screen.findByText('Lan Anh')).toBeInTheDocument()
})

test('tên trống bị chặn; lỗi validation server hiện dưới ô; Hủy về tổng quan', async () => {
  mockApi({ ...base, '/api/me': (b) => (b ? apiError(400, 'Dữ liệu không hợp lệ', { displayName: 'Tên không hợp lệ' }) : json(200, me)) })
  renderAt('/ho-so')
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Chỉnh sửa hồ sơ' }))
  const name = screen.getByLabelText('Tên hiển thị')
  await user.clear(name)
  await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))
  expect(await screen.findByText('Vui lòng nhập tên hiển thị')).toBeInTheDocument()
  await user.type(name, 'Tên')
  await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))
  expect(await screen.findByText('Tên không hợp lệ')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Hủy' }))
  expect(await screen.findByRole('heading', { name: 'Hồ sơ', level: 1 })).toBeInTheDocument()
})

test('band hồ sơ ưu tiên mục tiêu IELTS, kèm ngày mục tiêu', async () => {
  const goal = { id: 1, courseId: null, targetType: 'IELTS', targetValue: '6.5', targetDate: '2027-03-01' }
  mockApi({ ...base, '/api/me/goals': () => json(200, [goal]) })
  renderAt('/ho-so')
  expect(await screen.findByText('IELTS 6.5')).toBeInTheDocument()
  expect(screen.getByText('01/03/2027')).toBeInTheDocument()
})
