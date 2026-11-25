import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'

const base = (list: string[]) => ({ '/api/auth/refresh': () => json(200, auth), '/api/me/permissions': () => json(200, { permissions: list }) })
const err = (status: number, code: string, message: string) => json(status, { error: { code, message } })
const SEARCH = (q: string) => `/api/admin/support/students?q=${q}`

const student = (over = {}) => ({
  id: 3,
  email: 'hv@example.com',
  displayName: 'Nguyễn Văn A',
  status: 'ACTIVE',
  emailVerified: true,
  courses: ['IELTS Foundation', 'Giao tiếp'],
  ...over,
})
const detail = () => ({
  student: student({ emailVerified: false }),
  createdAt: '2026-10-01T02:00:00Z',
  enrollments: [{ courseId: 1, courseTitle: 'IELTS Foundation', status: 'ACTIVE', startsAt: '2026-11-01T00:00:00Z', expiresAt: '2027-05-01T00:00:00Z' }],
  orders: [{ code: 'ORD-9', status: 'EXPIRED', totalVnd: 490000, paidAt: null, createdAt: '2026-11-20T02:00:00Z' }],
  quota: { pool: 7, courses: [{ courseId: 1, courseTitle: 'IELTS Foundation', quota: 30, used: 5, remaining: 25 }] },
})

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

describe('quyền student.view', () => {
  test('menu có Học viên khi có quyền', async () => {
    mockApi(base(['student.view']))
    renderAt('/quan-tri/hoc-vien')
    expect(await screen.findByRole('heading', { name: 'Tra cứu học viên' })).toBeInTheDocument()
    const menu = screen.getByRole('navigation', { name: 'Điều hướng quản trị' })
    expect(within(menu).getByRole('link', { name: 'Học viên' })).toHaveAttribute('href', '/quan-tri/hoc-vien')
  })

  test.each(['/quan-tri/hoc-vien', '/quan-tri/hoc-vien/3'])('thiếu quyền: %s bị chặn và không gọi API học viên', async (path) => {
    const calls = mockApi(base(['order.view']))
    renderAt(path)
    expect(await screen.findByRole('heading', { name: 'Không có quyền truy cập' })).toBeInTheDocument()
    expect(calls.some((c) => c.startsWith('/api/admin/support'))).toBe(false)
  })
})

describe('tra cứu học viên', () => {
  test('dưới 3 ký tự không gọi API; đủ ký tự hiện bảng và link hồ sơ', async () => {
    const calls = mockApi({ ...base(['student.view']), [SEARCH('abc')]: () => json(200, [student()]) })
    renderAt('/quan-tri/hoc-vien')
    const user = userEvent.setup()
    expect(await screen.findByText('Nhân viên hỗ trợ')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Tiêu chí'), 'ab')
    expect(screen.getByText('Nhập ít nhất 3 ký tự để tìm.')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Tiêu chí'), 'c')
    const link = await screen.findByRole('link', { name: 'Xem hồ sơ' })
    expect(link).toHaveAttribute('href', '/quan-tri/hoc-vien/3')
    const row = link.closest('li')!
    expect(row).toHaveTextContent('hv@example.com')
    expect(row).toHaveTextContent('IELTS Foundation, Giao tiếp')
    expect(calls.filter((c) => c.startsWith('/api/admin/support')).every((c) => c === SEARCH('abc'))).toBe(true)
  })

  test('không có kết quả', async () => {
    mockApi({ ...base(['student.view']), [SEARCH('zzz')]: () => json(200, []) })
    renderAt('/quan-tri/hoc-vien')
    await userEvent.setup().type(await screen.findByLabelText('Tiêu chí'), 'zzz')
    expect(await screen.findByText('Không tìm thấy học viên nào.')).toBeInTheDocument()
  })

  test('đang tải', async () => {
    mockApi({ ...base(['student.view']), [SEARCH('abc')]: () => new Promise<Response>(() => {}) as never })
    renderAt('/quan-tri/hoc-vien')
    await userEvent.setup().type(await screen.findByLabelText('Tiêu chí'), 'abc')
    expect(await screen.findByText('Đang tìm học viên…')).toBeInTheDocument()
  })

  test('lỗi mạng có Thử lại', async () => {
    let fail = true
    mockApi({ ...base(['student.view']), [SEARCH('abc')]: () => (fail ? err(500, 'x', 'x') : json(200, [student()])) })
    renderAt('/quan-tri/hoc-vien')
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Tiêu chí'), 'abc')
    expect(await screen.findByText('Không thể tra cứu.', {}, { timeout: 5000 })).toBeInTheDocument()
    fail = false
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByRole('link', { name: 'Xem hồ sơ' })).toBeInTheDocument()
  })

  test('API trả 403 hiện không có quyền', async () => {
    mockApi({ ...base(['student.view']), [SEARCH('abc')]: () => err(403, 'forbidden', 'x') })
    renderAt('/quan-tri/hoc-vien')
    await userEvent.setup().type(await screen.findByLabelText('Tiêu chí'), 'abc')
    expect(await screen.findByText('Bạn không có quyền xem nội dung này.')).toBeInTheDocument()
  })
})

describe('hồ sơ học viên', () => {
  const URL = '/api/admin/support/students/3'

  test('hiện tài khoản, khóa, đơn hết hạn, ví AI và không có nút thao tác', async () => {
    mockApi({ ...base(['student.view']), [URL]: () => json(200, detail()) })
    renderAt('/quan-tri/hoc-vien/3')
    expect(await screen.findByRole('heading', { name: 'Hồ sơ học viên' })).toBeInTheDocument()
    expect(await screen.findByText('hv@example.com')).toBeInTheDocument()
    expect(screen.getByText('Chưa xác thực')).toBeInTheDocument()
    expect(screen.getByText('Còn hiệu lực')).toBeInTheDocument()
    expect(screen.getByText('ORD-9').closest('li')).toHaveTextContent('Hết hạn')
    expect(screen.getByText('Lượt mua thêm còn lại: 7')).toBeInTheDocument()
    expect(screen.getByText('Còn 25/30 lượt')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Kích hoạt|lượt bù|Mở khóa|Gửi lại/ })).not.toBeInTheDocument()
  })

  test('404 hiện không tìm thấy', async () => {
    mockApi({ ...base(['student.view']), [URL]: () => err(404, 'student_not_found', 'Không tìm thấy học viên') })
    renderAt('/quan-tri/hoc-vien/3')
    expect(await screen.findByText('Không tìm thấy học viên.')).toBeInTheDocument()
  })

  test('đang tải, lỗi mạng và 403', async () => {
    mockApi({ ...base(['student.view']), [URL]: () => new Promise<Response>(() => {}) as never })
    renderAt('/quan-tri/hoc-vien/3')
    expect(await screen.findByText('Đang tải hồ sơ…')).toBeInTheDocument()
  })

  test('lỗi mạng có Thử lại', async () => {
    let fail = true
    mockApi({ ...base(['student.view']), [URL]: () => (fail ? err(500, 'x', 'x') : json(200, detail())) })
    renderAt('/quan-tri/hoc-vien/3')
    expect(await screen.findByText('Không thể tải hồ sơ.', {}, { timeout: 5000 })).toBeInTheDocument()
    fail = false
    await userEvent.setup().click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('hv@example.com')).toBeInTheDocument()
  })

  test('403 hiện không có quyền', async () => {
    mockApi({ ...base(['student.view']), [URL]: () => err(403, 'forbidden', 'x') })
    renderAt('/quan-tri/hoc-vien/3')
    expect(await screen.findByText('Bạn không có quyền xem nội dung này.')).toBeInTheDocument()
  })
})

describe('link từ chi tiết đơn', () => {
  const ORDER = {
    id: 7,
    code: 'ORD-7',
    status: 'EXPIRED',
    totalVnd: 1,
    provider: 'fake',
    createdAt: '2026-11-20T02:00:00Z',
    expiresAt: '2026-11-20T02:30:00Z',
    paidAt: null,
    buyer: { id: 3, email: 'a@example.com', displayName: 'A' },
    items: [],
    transactions: [],
  }

  test('có student.view thì link tới đúng hồ sơ người mua', async () => {
    mockApi({ ...base(['order.view', 'student.view']), '/api/admin/orders/7': () => json(200, ORDER) })
    renderAt('/quan-tri/don-hang/7')
    expect(await screen.findByRole('link', { name: 'Tra cứu học viên' })).toHaveAttribute('href', '/quan-tri/hoc-vien/3')
  })

  test('không có student.view thì không hiện link', async () => {
    mockApi({ ...base(['order.view']), '/api/admin/orders/7': () => json(200, ORDER) })
    renderAt('/quan-tri/don-hang/7')
    expect(await screen.findByText('a@example.com')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Tra cứu học viên' })).not.toBeInTheDocument()
  })
})
