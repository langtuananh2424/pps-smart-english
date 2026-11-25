import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import { accessOf, formatDate, isExpiring } from './access'

const DAY = 86_400_000
const inDays = (n: number) => new Date(Date.now() + n * DAY).toISOString()

const detail = (extra = {}) => ({
  courseId: 7, slug: 'ae1', title: 'Academic English 1', description: 'Mô tả khóa', targetType: 'IELTS', bandFrom: 5.5, bandTo: 6, trialLessonCount: 3,
  priceFromVnd: 1500000, levelCount: 1, lessonCount: 20, deliveryMode: 'SELF_PACED', sequential: true, lessonCompletePercent: 80,
  levels: [{ id: 1, code: 'L1', title: 'Level 1', topics: [
    { id: 11, title: 'Topic 1', skill: null, isFreeTrial: true, lessons: [{ id: 99, title: 'Bài 1', type: 'VIDEO', durationSec: 60 }] },
    { id: 12, title: 'Topic 2', skill: null, isFreeTrial: false, lessons: [] },
  ] }],
  products: [{ id: 1, name: '3 tháng', priceVnd: 1500000, accessDays: 90 }, { id: 2, name: '6 tháng', priceVnd: 2400000, accessDays: 180 }],
  ...extra,
})
const mine = (status: string, expiresAt: string | null) => [{ courseId: 7, slug: 'ae1', title: 'Academic English 1', targetType: 'IELTS', status, expiresAt, lastActivityAt: null, completedLessons: 0, totalLessons: 20, answeredQuestions: 0, correctQuestions: 0 }]

const guest = (d = detail()) => ({ '/api/catalog/courses/ae1': () => json(200, d) })
const member = (list: unknown, d = detail()) => ({
  ...guest(d),
  '/api/auth/refresh': () => json(200, auth),
  '/api/me': () => json(200, { displayName: 'Lan' }),
  '/api/me/courses': () => json(200, list),
})
const here = (r: ReturnType<typeof renderAt>) => r.state.location.pathname + r.state.location.search

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

test('accessOf và isExpiring', () => {
  expect(accessOf(false, undefined)).toBe('GUEST')
  expect(accessOf(true, undefined)).toBe('NONE')
  expect(accessOf(true, mine('TRIAL', null)[0] as never)).toBe('NONE')
  expect(accessOf(true, mine('ACTIVE', null)[0] as never)).toBe('ACTIVE')
  expect(accessOf(true, mine('EXPIRED', null)[0] as never)).toBe('EXPIRED')
  expect(isExpiring(inDays(30))).toBe(true)
  expect(isExpiring(inDays(31))).toBe(false)
  expect(isExpiring(null)).toBe(false)
})

test('khách, khóa tự học: tab, thẻ band, lộ trình, chọn gói; không gọi API /me', async () => {
  const calls = mockApi(guest())
  const r = renderAt('/khoa-hoc/ae1')
  expect(await screen.findByRole('heading', { name: 'Academic English 1' })).toBeInTheDocument()
  expect(screen.getByText('Tự học')).toBeInTheDocument()
  expect(screen.getAllByRole('tab').map((t) => t.textContent)).toEqual(['Tổng quan', 'Lộ trình', 'Bài kiểm tra'])
  expect(screen.getByText('Band đầu vào 5.5 → 6.0')).toBeInTheDocument()
  expect(screen.getByText('Level 1 · Topic 1')).toBeInTheDocument()
  expect(screen.getByText('Học thử', { selector: 'dd' })).toBeInTheDocument()
  expect(screen.getByText('Topic tiếp theo')).toBeInTheDocument()
  expect(screen.getByText('Mở khi mua khóa')).toBeInTheDocument()
  expect(screen.getByText('Dành cho học viên đã mua')).toBeInTheDocument()
  expect(screen.getByText('Mở khi đạt tiến độ yêu cầu')).toBeInTheDocument()

  const buy = screen.getByRole('link', { name: 'Mua khóa' })
  expect(buy).toHaveAttribute('href', '/thanh-toan?khoa=ae1&goi=1')
  expect(screen.getByText('1.500.000 ₫')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('radio', { name: /6 tháng/ }))
  expect(screen.getByText('2.400.000 ₫')).toBeInTheDocument()
  expect(buy).toHaveAttribute('href', '/thanh-toan?khoa=ae1&goi=2')
  expect(calls.some((c) => c.startsWith('/api/me/courses'))).toBe(false)
  expect(here(r)).toBe('/khoa-hoc/ae1')
})

test('khách có học thử: thẻ đăng nhập để học thử đưa tới bài học thử; Xem lộ trình mở tab Lộ trình', async () => {
  mockApi(guest())
  const r = renderAt('/khoa-hoc/ae1')
  expect(await screen.findByText('Học thử Academic English 1')).toBeInTheDocument()
  expect(screen.getByText('Topic đầu tiên')).toBeInTheDocument()
  expect(screen.getByText('Đăng nhập để lưu tiến độ ở Topic học thử.')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Xem lộ trình' }))
  expect(screen.getByRole('tab', { name: 'Lộ trình' })).toHaveAttribute('aria-selected', 'true')
  expect(screen.getByText('Topic 2')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập để học thử' }))
  await waitFor(() => expect(here(r)).toBe(`/dang-nhap?next=${encodeURIComponent('/bai-giang/99')}`))
})

test('khách, khóa không có học thử: không có thẻ học thử', async () => {
  mockApi(guest(detail({ trialLessonCount: 0 })))
  renderAt('/khoa-hoc/ae1')
  await screen.findByRole('heading', { name: 'Academic English 1' })
  expect(screen.queryByText('Đăng nhập để học thử')).not.toBeInTheDocument()
})

test('khóa có lớp, chưa mua: Cách học, lộ trình, dòng giá/lượt AI, Học thử vào bài thử', async () => {
  mockApi(member([], detail({ deliveryMode: 'CLASS_BASED' })))
  const r = renderAt('/khoa-hoc/ae1')
  expect(await screen.findByText('Có lớp')).toBeInTheDocument()
  expect(screen.getByText('Học ngay phần tự học sau khi mua. Giáo vụ xếp lớp và gửi lịch qua email hoặc thông báo.')).toBeInTheDocument()
  expect(screen.getByText('Topic đầu tiên')).toBeInTheDocument()
  expect(screen.getByText('Các Topic còn lại')).toBeInTheDocument()
  expect(screen.getByText('Theo tiến độ khóa')).toBeInTheDocument()
  expect(screen.getByText('Giá và lượt AI tuần theo gói đã chọn.')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Học thử' }))
  await waitFor(() => expect(here(r)).toBe('/bai-giang/99'))
})

test('không có học thử thì ẩn nút Học thử; TRIAL vẫn thấy Mua khóa', async () => {
  mockApi(member(mine('TRIAL', null), detail({ trialLessonCount: 0 })))
  renderAt('/khoa-hoc/ae1')
  expect(await screen.findByRole('link', { name: 'Mua khóa' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Học thử' })).not.toBeInTheDocument()
})

test('đang học, còn xa hạn: chip, quyền học, Vào học; không banner, không Gia hạn', async () => {
  const exp = inDays(120)
  mockApi(member(mine('ACTIVE', exp)))
  renderAt('/khoa-hoc/ae1')
  expect(await screen.findByText('Đang học')).toBeInTheDocument()
  expect(screen.getByText('Quyền học còn hiệu lực.')).toBeInTheDocument()
  expect(screen.getByText(formatDate(exp))).toBeInTheDocument()
  expect(screen.getByText('Tiếp tục từ bài đang học')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Vào học' })).toHaveAttribute('href', '/bai-giang/khoa/7')
  expect(screen.queryByRole('link', { name: 'Gia hạn' })).not.toBeInTheDocument()
  expect(screen.queryByText(/Khóa sắp hết hạn/)).not.toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Mua khóa' })).not.toBeInTheDocument()
})

test('đang học, còn ≤30 ngày: banner và Gia hạn vào thanh toán', async () => {
  mockApi(member(mine('ACTIVE', inDays(10))))
  renderAt('/khoa-hoc/ae1')
  expect(await screen.findByText('Khóa sắp hết hạn – Bạn có thể gia hạn khi còn 30 ngày hoặc ít hơn')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Gia hạn' })).toHaveAttribute('href', '/thanh-toan?khoa=ae1')
})

test('hết hạn: thẻ hết hạn, Gia hạn và Xem lịch sử', async () => {
  mockApi(member(mine('EXPIRED', inDays(-3))))
  renderAt('/khoa-hoc/ae1')
  expect(await screen.findByText('Khóa đã hết hạn')).toBeInTheDocument()
  expect(screen.getByText('Thời hạn học đã kết thúc.')).toBeInTheDocument()
  expect(screen.getByText('Bạn có thể xem lịch sử và kết quả đã có. Gia hạn để học tiếp.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Gia hạn' })).toHaveAttribute('href', '/thanh-toan?khoa=ae1')
  expect(screen.getByRole('link', { name: 'Xem lịch sử' })).toHaveAttribute('href', '/bai-tap/khoa/7')
  expect(screen.queryByRole('link', { name: 'Mua khóa' })).not.toBeInTheDocument()
})

test('đang học: tab Bài kiểm tra tải danh sách kiểm tra của khóa', async () => {
  const tests = [{ kind: 'FINAL', topicId: null, title: 'Kiểm tra cuối khóa', bankId: 5, unlocked: true, reason: null, attemptsUsed: 0, attemptsAllowed: 2, inProgressAttemptId: null, lastResult: null }]
  mockApi({ ...member(mine('ACTIVE', inDays(100))), '/api/courses/7/tests': () => json(200, tests), '/api/courses/7/exam': () => json(404) })
  renderAt('/khoa-hoc/ae1')
  await userEvent.click(await screen.findByRole('tab', { name: 'Bài kiểm tra' }))
  const sec = await screen.findByRole('region', { name: 'Kiểm tra cuối khóa' })
  expect(within(sec).getByRole('link', { name: 'Làm bài' })).toHaveAttribute('href', '/bai-tap/5?khoa=7')
})

test('chưa mua: tab Bài kiểm tra chỉ mô tả', async () => {
  mockApi(guest())
  renderAt('/khoa-hoc/ae1')
  await userEvent.click(await screen.findByRole('tab', { name: 'Bài kiểm tra' }))
  expect(screen.getByText('Kiểm tra chương')).toBeInTheDocument()
  expect(screen.getByText('Dành cho học viên đã mua')).toBeInTheDocument()
})

test('404: không tìm thấy khóa học', async () => {
  mockApi({ '/api/catalog/courses/none': () => json(404, { error: { code: 'course_not_found', message: 'x' } }) })
  renderAt('/khoa-hoc/none')
  expect(await screen.findByRole('heading', { name: 'Không tìm thấy khóa học' })).toBeInTheDocument()
})
