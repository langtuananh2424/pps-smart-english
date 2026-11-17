import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { apiError, auth, json, mockApi, renderAt } from '../../testUtils'

const toDataURL = vi.hoisted(() => vi.fn(async () => 'data:image/png;base64,AAAA'))
vi.mock('qrcode', () => ({ default: { toDataURL } }))

const me = { id: 1, email: 'minh.anh@example.com', emailVerified: true, role: 'STUDENT', displayName: 'Minh Anh', selfLevel: null }
const detail = {
  slug: 'ielts',
  title: 'IELTS Intermediate',
  products: [
    { id: 12, name: 'Gói 12 tháng', priceVnd: 1_790_000, accessDays: 365 },
    { id: 6, name: 'Gói 6 tháng', priceVnd: 990_000, accessDays: 180 },
  ],
}
const future = () => new Date(Date.now() + 25 * 60_000).toISOString()
const course = { id: 7, slug: 'ielts', title: 'IELTS Intermediate' }
const item = { productId: 6, name: 'Gói 6 tháng', priceVnd: 990_000, course }
const pending = (over = {}) => ({
  code: 'ORD-ABC123',
  status: 'PENDING',
  totalVnd: 990_000,
  expiresAt: future(),
  items: [item],
  payment: { qrContent: 'FAKEQR|ORD-ABC123|990000', expiresAt: future(), simulatable: true },
  transferContent: 'Thanh toan ORD-ABC123',
  ...over,
})
const paid = { ...pending(), status: 'PAID', paidAt: new Date().toISOString(), payment: undefined, transferContent: undefined }
const expired = { ...pending(), status: 'EXPIRED', payment: undefined, transferContent: undefined }

const base = {
  '/api/auth/refresh': () => json(200, auth),
  '/api/me': () => json(200, me),
  '/api/catalog/courses/ielts': () => json(200, detail),
}

beforeEach(() => {
  setAccessToken(null)
  toDataURL.mockClear()
})
afterEach(() => vi.unstubAllGlobals())

describe('xác nhận đơn hàng', () => {
  test('mặc định gói ngắn nhất, đổi gói, tạo đơn với Idempotency-Key rồi sang trang đơn', async () => {
    let body: unknown
    let key: string | null = null
    mockApi({
      ...base,
      '/api/orders': (b, init) => ((body = b), (key = new Headers(init?.headers).get('Idempotency-Key')), json(201, pending())),
      '/api/orders/ORD-ABC123': () => json(200, pending()),
    })
    const router = renderAt('/thanh-toan?khoa=ielts')
    expect(await screen.findByRole('heading', { name: 'IELTS Intermediate' })).toBeInTheDocument()
    expect(screen.getByText('minh.anh@example.com')).toBeInTheDocument()
    expect(screen.getByText('Chuyển khoản QR')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Quay lại khóa học' })).toHaveAttribute('href', '/khoa-hoc/ielts')
    expect(screen.getByRole('radio', { name: /^6 tháng/ })).toBeChecked()
    expect(screen.getByText('990.000 ₫', { selector: 'dd' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('radio', { name: /^12 tháng/ }))
    expect(screen.getByText('1.790.000 ₫', { selector: 'dd' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('radio', { name: /^6 tháng/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Tạo đơn và thanh toán' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/don-hang/ORD-ABC123'))
    expect(body).toEqual({ productIds: [6] })
    expect(key).toMatch(/^[0-9a-f-]{36}$/)
  })

  test('lỗi mạng rồi bấm lại: giữ nguyên Idempotency-Key', async () => {
    const keys: (string | null)[] = []
    let n = 0
    mockApi({
      ...base,
      '/api/orders': (_b, init) => {
        keys.push(new Headers(init?.headers).get('Idempotency-Key'))
        if (++n === 1) throw new TypeError('network')
        return json(201, pending())
      },
      '/api/orders/ORD-ABC123': () => json(200, pending()),
    })
    renderAt('/thanh-toan?khoa=ielts')
    const go = await screen.findByRole('button', { name: 'Tạo đơn và thanh toán' })
    await userEvent.click(go)
    expect(await screen.findByRole('alert')).toHaveTextContent('Không kết nối được máy chủ')
    await userEvent.click(screen.getByRole('button', { name: 'Tạo đơn và thanh toán' }))
    await waitFor(() => expect(keys).toHaveLength(2))
    expect(keys[0]).toBe(keys[1])
  })

  test('403 email_not_verified: báo xác minh và có nút gửi lại', async () => {
    mockApi({ ...base, '/api/orders': () => json(403, { error: { code: 'email_not_verified', message: 'x' } }) })
    renderAt('/thanh-toan?khoa=ielts')
    await userEvent.click(await screen.findByRole('button', { name: 'Tạo đơn và thanh toán' }))
    expect(await screen.findByText(/xác minh email trước khi mua/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Gửi lại email' })).toBeInTheDocument()
  })

  test('503 payment_unavailable: báo tạm thời không thanh toán được', async () => {
    mockApi({ ...base, '/api/orders': () => json(503, { error: { code: 'payment_unavailable', message: 'x' } }) })
    renderAt('/thanh-toan?khoa=ielts')
    await userEvent.click(await screen.findByRole('button', { name: 'Tạo đơn và thanh toán' }))
    expect(await screen.findByText(/tạm thời chưa thanh toán được/)).toBeInTheDocument()
  })

  test('nhãn thời hạn quy đổi từ accessDays, không lặp tên gói', async () => {
    mockApi({ ...base, '/api/catalog/courses/ielts': () => json(200, { ...detail, products: [{ id: 1, name: 'Begin - 180 ngày', priceVnd: 1, accessDays: 180 }, { id: 2, name: 'Begin - 365 ngày', priceVnd: 2, accessDays: 365 }, { id: 3, name: 'X', priceVnd: 3, accessDays: 90 }] }) })
    renderAt('/thanh-toan?khoa=ielts')
    expect(await screen.findByRole('radio', { name: /^6 tháng/ })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /^12 tháng/ })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /^90 ngày/ })).toBeInTheDocument()
    expect(screen.queryByText(/Begin/)).not.toBeInTheDocument()
  })

  test('khóa không có gói: thẻ chưa mở bán; khóa không tồn tại', async () => {
    mockApi({ ...base, '/api/catalog/courses/ielts': () => json(200, { ...detail, products: [] }) })
    renderAt('/thanh-toan?khoa=ielts')
    expect(await screen.findByText('Khóa học chưa mở bán')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Tạo đơn và thanh toán' })).not.toBeInTheDocument()
  })

  test('khách bấm mua: vào đăng nhập kèm next', async () => {
    mockApi({})
    const router = renderAt('/thanh-toan?khoa=ielts')
    await waitFor(() => expect(router.state.location.pathname).toBe('/dang-nhap'))
    expect(router.state.location.search).toContain(encodeURIComponent('/thanh-toan?khoa=ielts'))
  })
})

describe('chi tiết đơn', () => {
  test('PENDING: vẽ QR từ qrContent, nội dung chuyển khoản, đếm ngược, khung giả lập → PAID', async () => {
    let calls = 0
    mockApi({
      ...base,
      '/api/orders/ORD-ABC123': () => json(200, pending()),
      '/api/dev/payments/ORD-ABC123/pay': () => ((calls += 1), json(200, paid)),
    })
    renderAt('/don-hang/ORD-ABC123')
    expect(await screen.findByText('Đang chờ thanh toán')).toBeInTheDocument()
    expect(await screen.findByRole('img', { name: 'Mã QR thanh toán' })).toHaveAttribute('src', expect.stringContaining('data:image'))
    expect(toDataURL).toHaveBeenCalledWith('FAKEQR|ORD-ABC123|990000', expect.anything())
    expect(screen.getByText('Thanh toan ORD-ABC123')).toBeInTheDocument()
    expect(screen.getByText('990.000 ₫')).toBeInTheDocument()
    expect(screen.getByRole('timer')).toHaveTextContent(/Mã hết hạn sau 2[45]:\d\d/)

    await userEvent.click(screen.getByRole('button', { name: 'Giả lập đã thanh toán' }))
    expect(await screen.findByRole('heading', { name: 'Thanh toán thành công' })).toBeInTheDocument()
    expect(calls).toBe(1)
  })

  test('không simulatable: không có khung giả lập', async () => {
    mockApi({ ...base, '/api/orders/ORD-ABC123': () => json(200, pending({ payment: { qrContent: 'x', expiresAt: future(), simulatable: false } })) })
    renderAt('/don-hang/ORD-ABC123')
    await screen.findByText('Đang chờ thanh toán')
    expect(screen.queryByRole('button', { name: 'Giả lập đã thanh toán' })).not.toBeInTheDocument()
  })

  test('poll mỗi 3 giây: đổi sang PAID thì chuyển trạng thái và tải lại dữ liệu khóa của tôi', async () => {
    let orderCalls = 0
    let courseCalls = 0
    mockApi({
      ...base,
      '/api/me/courses': () => (courseCalls++, json(200, [])),
      '/api/orders/ORD-ABC123': () => (++orderCalls >= 2 ? json(200, paid) : json(200, pending())),
    })
    renderAt('/don-hang/ORD-ABC123')
    expect(await screen.findByText('Đang chờ thanh toán')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Thanh toán thành công' }, { timeout: 6000 })).toBeInTheDocument()
    expect(orderCalls).toBeGreaterThanOrEqual(2)
  }, 10_000)

  test('PAID: gói, tổng, phương thức, quyền học, link vào bài giảng', async () => {
    mockApi({ ...base, '/api/orders/ORD-ABC123': () => json(200, paid) })
    renderAt('/don-hang/ORD-ABC123')
    expect(await screen.findByText('Khóa học đã được mở')).toBeInTheDocument()
    expect(screen.getByText('Gói 6 tháng')).toBeInTheDocument()
    expect(screen.getByText('Đã mở khóa')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Vào bài giảng' })).toHaveAttribute('href', '/bai-giang/khoa/7')
    expect(screen.getByRole('link', { name: 'Về đơn hàng' })).toHaveAttribute('href', '/don-hang')
  })

  test('EXPIRED: Tạo đơn mới gửi lại cùng productIds rồi sang đơn mới', async () => {
    let body: unknown
    mockApi({
      ...base,
      '/api/orders/ORD-ABC123': () => json(200, expired),
      '/api/orders': (b) => ((body = b), json(201, { ...pending({ code: 'ORD-NEW999' }) })),
      '/api/orders/ORD-NEW999': () => json(200, pending({ code: 'ORD-NEW999' })),
    })
    const router = renderAt('/don-hang/ORD-ABC123')
    expect(await screen.findByRole('heading', { name: 'Mã thanh toán đã hết hạn' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Tạo đơn mới' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/don-hang/ORD-NEW999'))
    expect(body).toEqual({ productIds: [6] })
  })

  test('404: thẻ không tìm thấy đơn', async () => {
    mockApi({ ...base, '/api/orders/ORD-NOPE00': () => apiError(404, 'x') })
    renderAt('/don-hang/ORD-NOPE00')
    expect(await screen.findByText('Không tìm thấy đơn hàng')).toBeInTheDocument()
  })
})

describe('đơn hàng của tôi', () => {
  const row = (code: string, status: string, name = 'Gói 6 tháng') => ({
    code,
    status,
    totalVnd: 990_000,
    createdAt: new Date().toISOString(),
    paidAt: null,
    expiresAt: future(),
    items: [{ productId: 6, name }],
  })

  test('dòng đơn, nút theo trạng thái, tải thêm theo cursor', async () => {
    const calls = mockApi({
      ...base,
      '/api/me/orders?limit=20': () => json(200, { data: [row('ORD-1', 'PAID'), row('ORD-2', 'PENDING'), row('ORD-3', 'EXPIRED')], nextCursor: 'c1' }),
      '/api/me/orders?limit=20&cursor=c1': () => json(200, { data: [row('ORD-4', 'CANCELLED')], nextCursor: null }),
    })
    renderAt('/don-hang')
    expect(await screen.findByText('ORD-1')).toBeInTheDocument()
    const rows = within(screen.getByText('ORD-1').closest('ul')!).getAllByRole('listitem')
    expect(within(rows[0]).getByText('Đã thanh toán')).toBeInTheDocument()
    expect(within(rows[0]).getByText('Gói 6 tháng · 990.000 ₫')).toBeInTheDocument()
    expect(within(rows[0]).getByRole('link', { name: 'Xem chi tiết' })).toHaveAttribute('href', '/don-hang/ORD-1')
    expect(within(rows[1]).getByRole('link', { name: 'Tiếp tục thanh toán' })).toHaveAttribute('href', '/don-hang/ORD-2')
    expect(within(rows[2]).getByText('Hết hạn')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Về hồ sơ' })).toHaveAttribute('href', '/ho-so')

    await userEvent.click(screen.getByRole('button', { name: 'Tải thêm' }))
    expect(await screen.findByText('ORD-4')).toBeInTheDocument()
    expect(screen.getByText('Đã hủy')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Tải thêm' })).not.toBeInTheDocument()
    expect(calls).toContain('/api/me/orders?limit=20&cursor=c1')
  })

  test('rỗng: thẻ trống kèm link Khóa học', async () => {
    mockApi({ ...base, '/api/me/orders?limit=20': () => json(200, { data: [], nextCursor: null }) })
    renderAt('/don-hang')
    expect(await screen.findByText('Bạn chưa có đơn hàng nào')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Khóa học' })).toHaveAttribute('href', '/khoa-hoc')
    expect(screen.getByRole('link', { name: 'Về hồ sơ' })).toBeInTheDocument()
  })
})
