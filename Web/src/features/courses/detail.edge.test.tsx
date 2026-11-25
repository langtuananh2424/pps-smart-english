import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import { isExpiring } from './access'

// Ca biên, lỗi và an toàn URL cho trang chi tiết khóa và ?goi ở thanh toán (tester).
const DAY = 86_400_000
const detail = (extra = {}) => ({
  courseId: 7, slug: 'ae1', title: 'Academic English 1', description: 'Mô tả khóa', targetType: 'IELTS', bandFrom: 5.5, bandTo: 6, trialLessonCount: 3,
  priceFromVnd: 1500000, levelCount: 1, lessonCount: 20, deliveryMode: 'SELF_PACED', sequential: true, lessonCompletePercent: 80,
  levels: [{ id: 1, code: 'L1', title: 'Level 1', topics: [{ id: 11, title: 'Topic 1', skill: null, isFreeTrial: true, lessons: [{ id: 99, title: 'Bài 1', type: 'VIDEO', durationSec: 60 }] }] }],
  products: [{ id: 1, name: '3 tháng', priceVnd: 1500000, accessDays: 90 }, { id: 2, name: '6 tháng', priceVnd: 2400000, accessDays: 180 }],
  ...extra,
})
const me = { id: 1, email: 'lan@example.com', emailVerified: true, role: 'STUDENT', displayName: 'Lan', selfLevel: null }
const logged = {
  '/api/auth/refresh': () => json(200, auth),
  '/api/me': () => json(200, me),
  '/api/catalog/courses/ae1': () => json(200, detail()),
}
const active = [{ courseId: 7, slug: 'ae1', title: 'Academic English 1', targetType: 'IELTS', status: 'ACTIVE', expiresAt: null, lastActivityAt: null, completedLessons: 0, totalLessons: 20, answeredQuestions: 0, correctQuestions: 0 }]

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

test('isExpiring: biên 30 ngày, ngày đã qua, chuỗi ngày hỏng', () => {
  const now = Date.UTC(2026, 10, 25)
  expect(isExpiring(new Date(now + 30 * DAY).toISOString(), now)).toBe(true)
  expect(isExpiring(new Date(now + 30 * DAY + 1000).toISOString(), now)).toBe(false)
  expect(isExpiring(new Date(now - DAY).toISOString(), now)).toBe(true)
  expect(isExpiring('khong-phai-ngay', now)).toBe(false)
})

test('đang học, khóa không có ngày hết hạn: không banner, không Gia hạn', async () => {
  mockApi({ ...logged, '/api/me/courses': () => json(200, active) })
  renderAt('/khoa-hoc/ae1')
  expect(await screen.findByRole('link', { name: 'Vào học' })).toBeInTheDocument()
  expect(screen.queryByText(/Khóa sắp hết hạn/)).not.toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Gia hạn' })).not.toBeInTheDocument()
})

/**
 * Đã đăng nhập mà /me/courses lỗi: trang không được coi như "chưa mua" (đang hiện Mua khóa cho người đã sở hữu khóa,
 * dễ mua trùng). Kỳ vọng: báo lỗi/cho thử lại, không hiện CTA mua.
 */
test('đã đăng nhập, /me/courses lỗi 500: không hiện Mua khóa như người chưa mua', async () => {
  mockApi({ ...logged, '/api/me/courses': () => json(500, { error: { code: 'internal', message: 'x' } }) })
  renderAt('/khoa-hoc/ae1')
  await screen.findByRole('heading', { name: 'Academic English 1' }, { timeout: 8000 }) // chờ hết retry 5xx
  expect(screen.queryByRole('link', { name: 'Mua khóa' })).not.toBeInTheDocument()
}, 15000)

test('slug lạ trong URL không đi ra ngoài /api/catalog/courses (path traversal phía client)', async () => {
  const calls = mockApi({})
  renderAt('/khoa-hoc/..%2F..%2Fme%2Fcourses')
  await screen.findByRole('heading', { name: /Không tìm thấy khóa học|Không tải được khóa học/ })
  const catalog = calls.filter((c) => c.includes('catalog') || c.includes('/me'))
  console.log('CALLS', JSON.stringify(calls))
  expect(catalog.every((c) => c.startsWith('/api/catalog/courses/') && !c.includes('../'))).toBe(true)
})

test('khách bấm Mua khóa: đăng nhập kèm next giữ cả khoa và goi', async () => {
  mockApi({ '/api/catalog/courses/ae1': () => json(200, detail()) })
  const r = renderAt('/khoa-hoc/ae1')
  await userEvent.click(await screen.findByRole('radio', { name: /6 tháng/ }))
  await userEvent.click(screen.getByRole('link', { name: 'Mua khóa' }))
  await waitFor(() => expect(r.state.location.pathname).toBe('/dang-nhap'))
  expect(new URLSearchParams(r.state.location.search).get('next')).toBe('/thanh-toan?khoa=ae1&goi=2')
})

describe('thanh toán ?goi', () => {
  const order = () => {
    let body: unknown
    mockApi({ ...logged, '/api/orders': (b) => ((body = b), json(201, { code: 'ORD-1', status: 'PENDING', items: [] })) })
    return () => body
  }

  test('goi hợp lệ: chọn sẵn gói đó và tạo đơn với đúng productId', async () => {
    const body = order()
    renderAt('/thanh-toan?khoa=ae1&goi=2')
    expect(await screen.findByRole('radio', { name: /^6 tháng/ })).toBeChecked()
    await userEvent.click(screen.getByRole('button', { name: 'Tạo đơn và thanh toán' }))
    await waitFor(() => expect(body()).toEqual({ productIds: [2] }))
  })

  test.each(['abc', '999', '-1', '0', '1e3', '2abc', ''])('goi=%s không thuộc khóa: về gói ngắn nhất, đơn chỉ chứa gói của khóa', async (goi) => {
    const body = order()
    renderAt(`/thanh-toan?khoa=ae1&goi=${encodeURIComponent(goi)}`)
    expect(await screen.findByRole('radio', { name: /^90 ngày/ })).toBeChecked()
    await userEvent.click(screen.getByRole('button', { name: 'Tạo đơn và thanh toán' }))
    await waitFor(() => expect(body()).toEqual({ productIds: [1] }))
  })
})
