import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'

const base = (list: string[]) => ({ '/api/auth/refresh': () => json(200, auth), '/api/me/permissions': () => json(200, { permissions: list }) })
const err = (status: number, code: string, message: string) => json(status, { error: { code, message } })

const order = (id: number, over = {}) => ({
  id,
  code: `ORD-${id}`,
  status: 'PAID',
  totalVnd: 490000,
  paidAt: '2026-11-20T03:00:00Z',
  createdAt: '2026-11-20T02:00:00Z',
  email: `khach${id}@example.com`,
  displayName: 'Nguyễn Văn A',
  provider: 'fake',
  ...over,
})
const LIST = '/api/admin/orders?limit=20'
const empty = () => json(200, { data: [], nextCursor: null })
const detail = (over = {}) => ({
  id: 7,
  code: 'ORD-7',
  status: 'PAID',
  totalVnd: 490000,
  provider: 'fake',
  createdAt: '2026-11-20T02:00:00Z',
  expiresAt: '2026-11-20T02:30:00Z',
  paidAt: '2026-11-20T03:00:00Z',
  buyer: { id: 3, email: 'a@example.com', displayName: 'Nguyễn Văn A' },
  items: [
    { productName: 'Gói IELTS', productType: 'LEVEL', courseTitle: 'IELTS Foundation', priceVnd: 450000 },
    { productName: 'Thêm 10 lượt', productType: 'AI_ADDON', courseTitle: null, priceVnd: 40000 },
  ],
  transactions: [{ receivedAt: '2026-11-20T03:00:00Z', provider: 'fake', providerRef: 'TX-1', amountVnd: 490000, status: 'APPLIED' }],
  ...over,
})

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

describe('quyền order.view', () => {
  test('menu có Đơn hàng khi có quyền', async () => {
    mockApi({ ...base(['order.view']), [LIST]: empty })
    renderAt('/quan-tri/don-hang')
    expect(await screen.findByRole('heading', { name: 'Danh sách đơn hàng' })).toBeInTheDocument()
    const menu = screen.getByRole('navigation', { name: 'Điều hướng quản trị' })
    expect(within(menu).getByRole('link', { name: 'Đơn hàng' })).toHaveAttribute('href', '/quan-tri/don-hang')
  })

  test.each(['/quan-tri/don-hang', '/quan-tri/don-hang/7'])('thiếu quyền: %s bị chặn và không gọi API đơn', async (path) => {
    const calls = mockApi(base(['audit.view']))
    renderAt(path)
    expect(await screen.findByRole('heading', { name: 'Không có quyền truy cập' })).toBeInTheDocument()
    expect(calls.some((c) => c.startsWith('/api/admin/orders'))).toBe(false)
  })
})

describe('danh sách đơn', () => {
  test('hiện dòng đơn, link chi tiết, tải thêm bằng cursor', async () => {
    mockApi({
      ...base(['order.view']),
      [LIST]: () => json(200, { data: [order(2)], nextCursor: 'c1' }),
      [`${LIST}&cursor=c1`]: () => json(200, { data: [order(1, { status: 'PENDING', paidAt: null, provider: null })], nextCursor: null }),
    })
    renderAt('/quan-tri/don-hang')
    const link = await screen.findByRole('link', { name: 'ORD-2' })
    expect(link).toHaveAttribute('href', '/quan-tri/don-hang/2')
    const row = link.closest('li')!
    expect(row).toHaveTextContent('Đã thanh toán')
    expect(row).toHaveTextContent('khach2@example.com')
    expect(row).toHaveTextContent('Nguyễn Văn A')
    expect(screen.getByText('Khoảng ngày tạo đơn tính cả ngày bắt đầu và ngày kết thúc.')).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Tải thêm' }))
    const second = (await screen.findByRole('link', { name: 'ORD-1' })).closest('li')!
    expect(second).toHaveTextContent('Chờ thanh toán')
    expect(second).toHaveTextContent('—')
    expect(screen.queryByRole('button', { name: 'Tải thêm' })).not.toBeInTheDocument()
  })

  test('lọc trạng thái, ngày và email gửi đúng tham số', async () => {
    const calls = mockApi({ ...base(['order.view']), [LIST]: empty })
    renderAt('/quan-tri/don-hang')
    expect(await screen.findByText('Không có đơn hàng nào khớp bộ lọc.')).toBeInTheDocument()
    const user = userEvent.setup()
    await user.selectOptions(screen.getByLabelText('Trạng thái'), 'PAID')
    await user.type(screen.getByLabelText('Từ ngày tạo'), '2026-11-01')
    await user.type(screen.getByLabelText('Đến ngày tạo'), '2026-11-30')
    await user.type(screen.getByLabelText('Email người mua'), 'abc')
    await waitFor(() => expect(calls).toContain('/api/admin/orders?status=PAID&from=2026-11-01&to=2026-11-30&email=abc&limit=20'))
  })

  test('lỗi mạng có Thử lại', async () => {
    let fail = true
    mockApi({ ...base(['order.view']), [LIST]: () => (fail ? err(500, 'x', 'x') : json(200, { data: [order(1)], nextCursor: null })) })
    renderAt('/quan-tri/don-hang')
    expect(await screen.findByText('Không thể tải đơn hàng.', {}, { timeout: 5000 })).toBeInTheDocument()
    fail = false
    await userEvent.setup().click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByRole('link', { name: 'ORD-1' })).toBeInTheDocument()
  })

  test('API trả 403 hiện không có quyền', async () => {
    mockApi({ ...base(['order.view']), [LIST]: () => err(403, 'forbidden', 'x') })
    renderAt('/quan-tri/don-hang')
    expect(await screen.findByText('Bạn không có quyền xem nội dung này.')).toBeInTheDocument()
  })
})

describe('xuất CSV', () => {
  const submit = (user: ReturnType<typeof userEvent.setup>) => user.click(within(screen.getByRole('dialog')).getAllByRole('button', { name: 'Xuất CSV' })[0])
  async function open(handlers: Record<string, () => Response> = {}) {
    const calls = mockApi({ ...base(['order.view']), [LIST]: empty, ...handlers })
    renderAt('/quan-tri/don-hang')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Xuất CSV' }))
    return { user, calls }
  }
  const fill = async (user: ReturnType<typeof userEvent.setup>, from: string, to: string) => {
    if (from) await user.type(screen.getByLabelText('Từ ngày'), from)
    if (to) await user.type(screen.getByLabelText('Đến ngày'), to)
  }

  test('hộp thoại có 9 cột, cảnh báo nhật ký và không có ô lý do', async () => {
    await open()
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getAllByRole('listitem')).toHaveLength(9)
    expect(within(dialog).getByText('Mỗi lần xuất được ghi với thao tác EXPORT.')).toBeInTheDocument()
    expect(within(dialog).queryByLabelText(/Lý do/)).not.toBeInTheDocument()
  })

  test('thiếu ngày hoặc quá 366 ngày thì không gọi API', async () => {
    const { user, calls } = await open()
    await submit(user)
    expect(await screen.findByText('Vui lòng chọn ngày bắt đầu')).toBeInTheDocument()
    expect(screen.getByText('Vui lòng chọn ngày kết thúc')).toBeInTheDocument()
    await fill(user, '2025-01-01', '2026-01-02') // 367 ngày
    await submit(user)
    expect(await screen.findByText('Khoảng xuất tối đa 366 ngày')).toBeInTheDocument()
    expect(calls.some((c) => c.includes('export.csv'))).toBe(false)
  })

  test('đúng 366 ngày thì xuất và tải file', async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    URL.createObjectURL = vi.fn(() => 'blob:x')
    URL.revokeObjectURL = vi.fn()
    const { user } = await open({
      '/api/admin/orders/export.csv?from=2025-01-01&to=2026-01-01': () => new Response('Mã đơn\r\n', { status: 200, headers: { 'Content-Type': 'text/csv' } }),
    })
    await fill(user, '2025-01-01', '2026-01-01')
    await submit(user)
    expect(await screen.findByText('Đã xuất CSV')).toBeInTheDocument()
    expect(screen.getByText('Trình duyệt đã bắt đầu tải file về.')).toBeInTheDocument()
    expect(screen.queryByLabelText('Từ ngày')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Các cột trong file')).not.toBeInTheDocument()
    expect(screen.queryByText('Sẽ ghi nhật ký')).not.toBeInTheDocument()
    expect(click).toHaveBeenCalledOnce()
    click.mockRestore()
  })

  test.each([
    [403, 'cấm', 'Bạn không có quyền xuất đơn hàng.'],
    [400, 'Khoảng xuất tối đa 366 ngày', 'Khoảng xuất tối đa 366 ngày'],
  ])('lỗi %i từ máy chủ hiện thông báo', async (status, message, shown) => {
    const { user } = await open({ '/api/admin/orders/export.csv?from=2026-01-01&to=2026-01-02': () => err(status, 'x', message) })
    await fill(user, '2026-01-01', '2026-01-02')
    await submit(user)
    expect(await screen.findByText(shown)).toBeInTheDocument()
  })

  test('lỗi mạng hiện thông báo, Hủy đóng hộp thoại', async () => {
    const { user } = await open()
    const f = globalThis.fetch
    vi.stubGlobal('fetch', (u: string, i?: RequestInit) => (u.includes('export.csv') ? Promise.reject(new TypeError('net')) : f(u, i)))
    await fill(user, '2026-01-01', '2026-01-02')
    await submit(user)
    expect(await screen.findByText(/Không thể xuất file/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Hủy' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})

describe('chi tiết đơn', () => {
  const D = '/api/admin/orders/7'

  test('đơn đã thanh toán: đủ khối, nhãn kết quả giao dịch, không có nút thao tác hay raw_payload', async () => {
    const tx = (ref: string, status: string) => ({ receivedAt: '2026-11-20T03:05:00Z', provider: 'fake', providerRef: ref, amountVnd: 1, status })
    mockApi({
      ...base(['order.view', 'student.view']),
      [D]: () => json(200, detail({ transactions: [tx('TX-1', 'APPLIED'), tx('TX-2', 'MISMATCH'), tx('TX-3', 'ORDER_ALREADY_PAID'), tx('TX-4', 'ORDER_NOT_FOUND'), tx('TX-5', 'ORDER_CANCELLED')] })),
    })
    renderAt('/quan-tri/don-hang/7')
    expect(await screen.findByText('ORD-7')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Chi tiết đơn hàng' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Danh sách đơn hàng' })).toHaveAttribute('href', '/quan-tri/don-hang')
    expect(screen.getByText('Gói khóa học (LEVEL)')).toBeInTheDocument()
    expect(screen.getByText('Gói lượt AI (AI_ADDON)')).toBeInTheDocument()
    expect(screen.getByText('IELTS Foundation')).toBeInTheDocument()
    expect(screen.getByText('—')).toBeInTheDocument() // khóa học kèm theo của gói AI
    for (const t of ['Đã ghi nhận', 'Sai số tiền', 'Đơn đã thanh toán trước đó', 'Không tìm thấy đơn', 'Đơn đã hủy']) expect(screen.getByText(t)).toBeInTheDocument()
    expect(screen.getByText('Các lần cổng thanh toán gửi kết quả cho đơn này.')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByText(/raw|payload/i)).not.toBeInTheDocument()
  })

  test.each([
    ['PENDING', 'Chờ thanh toán'],
    ['EXPIRED', 'Hết hạn'],
    ['CANCELLED', 'Đã hủy'],
  ])('đơn %s: chưa thanh toán, không có giao dịch', async (status, label) => {
    mockApi({ ...base(['order.view']), [D]: () => json(200, detail({ status, paidAt: null, transactions: [] })) })
    renderAt('/quan-tri/don-hang/7')
    expect(await screen.findByText(label)).toBeInTheDocument()
    expect(screen.getByText('Chưa có giao dịch nào.')).toBeInTheDocument()
    expect(screen.getByText('Thời điểm thanh toán').nextElementSibling).toHaveTextContent(/^$/)
  })

  test('không tìm thấy đơn', async () => {
    mockApi({ ...base(['order.view']), [D]: () => err(404, 'order_not_found', 'x') })
    renderAt('/quan-tri/don-hang/7')
    expect(await screen.findByText('Không tìm thấy đơn hàng.')).toBeInTheDocument()
  })

  test('lỗi mạng có Thử lại', async () => {
    let fail = true
    mockApi({ ...base(['order.view']), [D]: () => (fail ? err(500, 'x', 'x') : json(200, detail())) })
    renderAt('/quan-tri/don-hang/7')
    expect(await screen.findByText('Không thể tải đơn hàng.')).toBeInTheDocument()
    fail = false
    await userEvent.setup().click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('ORD-7')).toBeInTheDocument()
  })

  test('API trả 403 báo không có quyền', async () => {
    mockApi({ ...base(['order.view']), [D]: () => err(403, 'forbidden', 'x') })
    renderAt('/quan-tri/don-hang/7')
    expect(await screen.findByText('Bạn không có quyền xem nội dung này.')).toBeInTheDocument()
  })
})
