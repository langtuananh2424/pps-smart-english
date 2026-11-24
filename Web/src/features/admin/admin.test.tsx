import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'

const ADMIN = ['rbac.manage', 'audit.view', 'report.view', 'product.manage', 'activation.manage', 'cms.content']
const perms = (list: string[]) => () => json(200, { permissions: list })
const base = (list: string[]) => ({ '/api/auth/refresh': () => json(200, auth), '/api/me/permissions': perms(list) })
const err = (status: number, code: string, message: string) => json(status, { error: { code, message } })

const catalog = [
  { code: 'cms.content', module: 'CMS', description: 'Soạn nội dung khóa học' },
  { code: 'report.view', module: 'CMS', description: 'Xem dashboard' },
]
const editor = { id: 2, code: 'EDITOR', name: 'Biên tập viên', permissions: ['cms.content'], memberCount: 3 }
const adminRole = { id: 1, code: 'ADMIN', name: 'Admin', permissions: ['rbac.manage', 'cms.content'], memberCount: 1 }

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

const menu = () => screen.queryByRole('navigation', { name: 'Điều hướng quản trị' })
const reasonBox = () => screen.getByLabelText('Lý do · Bắt buộc')

describe('khung quản trị và quyền', () => {
  test('admin thấy đủ menu, index chuyển tới mục đầu có quyền', async () => {
    mockApi(base(ADMIN))
    const router = renderAt('/quan-tri')
    expect(await screen.findByRole('heading', { name: 'Tổng quan' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/quan-tri/tong-quan')
    const links = within(menu()!).getAllByRole('link').map((a) => a.textContent)
    expect(links).toEqual(['Tổng quan', 'Gói bán', 'Link kích hoạt', 'CMS', 'Nhân sự'])
    expect(within(menu()!).getByRole('link', { name: 'Nhân sự' })).toHaveAttribute('href', '/quan-tri/vai-tro')
  })

  test('giám đốc chỉ thấy Nhân sự và tab Nhật ký; gõ thẳng URL vai trò bị chặn mà không gọi API', async () => {
    const calls = mockApi({ ...base(['audit.view']), '/api/admin/audit-logs?limit=50': () => json(200, { data: [], nextCursor: null }) })
    const router = renderAt('/quan-tri')
    expect(await screen.findByRole('heading', { name: 'Nhật ký thao tác' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/quan-tri/nhat-ky')
    expect(within(menu()!).getAllByRole('link').map((a) => a.textContent)).toEqual(['Nhân sự'])
    expect(screen.getByRole('navigation', { name: 'Nhân sự' }).textContent).toBe('Nhật ký')

    await act(() => router.navigate('/quan-tri/vai-tro'))
    expect(await screen.findByRole('heading', { name: 'Không có quyền truy cập' })).toBeInTheDocument()
    expect(calls.some((c) => c.startsWith('/api/admin/roles'))).toBe(false)
    expect(router.state.location.pathname).toBe('/quan-tri/vai-tro')
  })

  test('học viên không có quyền nào: menu trống và trang 403', async () => {
    mockApi(base([]))
    renderAt('/quan-tri')
    expect(await screen.findByRole('heading', { name: 'Không có quyền truy cập' })).toBeInTheDocument()
    expect(within(menu()!).queryAllByRole('link')).toHaveLength(0)
  })
})

describe('vai trò', () => {
  test('danh sách vai trò hiện số quyền và số người', async () => {
    mockApi({ ...base(ADMIN), '/api/admin/roles': () => json(200, [adminRole, editor]) })
    renderAt('/quan-tri/vai-tro')
    const link = await screen.findByRole('link', { name: /Biên tập viên/ })
    expect(link).toHaveAttribute('href', '/quan-tri/vai-tro/2')
    const row = link.closest('li')!
    expect(row).toHaveTextContent('Số quyền · 1')
    expect(row).toHaveTextContent('Số người · 3')
    expect(screen.getByRole('link', { name: 'Tạo vai trò' })).toHaveAttribute('href', '/quan-tri/vai-tro/moi')
  })

  test('lưu quyền vai trò gửi đúng body và header lý do; thiếu lý do thì không gửi', async () => {
    let body: unknown
    let reason: string | null = null
    let puts = 0
    mockApi({
      ...base(ADMIN),
      '/api/admin/roles': () => json(200, [editor]),
      '/api/admin/permissions': () => json(200, catalog),
      '/api/admin/roles/2/permissions': (b, init) => {
        puts++
        body = b
        reason = new Headers(init?.headers).get('X-Audit-Reason')
        return json(200, { ...editor, permissions: ['cms.content', 'report.view'] })
      },
    })
    renderAt('/quan-tri/vai-tro/2')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('switch', { name: 'report.view' }))
    await user.click(screen.getByRole('button', { name: 'Lưu vai trò' }))
    expect(await screen.findByText('Vui lòng nhập lý do thay đổi')).toBeInTheDocument()
    expect(puts).toBe(0)

    await user.type(reasonBox(), 'Cấp quyền xem báo cáo')
    await user.click(screen.getByRole('button', { name: 'Lưu vai trò' }))
    expect(await screen.findByText('Đã lưu vai trò.')).toBeInTheDocument()
    expect(body).toEqual({ permissions: ['cms.content', 'report.view'] })
    expect(decodeURIComponent(reason!)).toBe('Cấp quyền xem báo cáo')
    expect(reason).not.toMatch(/[^\x20-\x7e]/)
  })

  test('409 last_admin hiện khối Quyền quản trị cuối cùng, quay lại thì về form', async () => {
    mockApi({
      ...base(ADMIN),
      '/api/admin/roles': () => json(200, [adminRole]),
      '/api/admin/permissions': () => json(200, catalog),
      '/api/admin/roles/1/permissions': () => err(409, 'last_admin', 'x'),
    })
    renderAt('/quan-tri/vai-tro/1')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('switch', { name: 'cms.content' }))
    await user.type(reasonBox(), 'Thử gỡ')
    await user.click(screen.getByRole('button', { name: 'Lưu vai trò' }))
    expect(await screen.findByRole('heading', { name: 'Không thể gỡ quyền quản trị cuối cùng' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Quay lại phân quyền' }))
    expect(await screen.findByRole('button', { name: 'Lưu vai trò' })).toBeInTheDocument()
  })

  test('tạo vai trò: 409 role_taken hiện thông báo tại form', async () => {
    mockApi({
      ...base(ADMIN),
      '/api/admin/permissions': () => json(200, catalog),
      '/api/admin/roles': () => err(409, 'role_taken', 'Mã role đã được dùng'),
    })
    renderAt('/quan-tri/vai-tro/moi')
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Mã vai trò'), 'EDITOR')
    await user.type(screen.getByLabelText('Tên vai trò'), 'Biên tập')
    await user.type(reasonBox(), 'Tạo mới')
    await user.click(screen.getByRole('button', { name: 'Tạo vai trò' }))
    expect(await screen.findByText('Mã role đã được dùng')).toBeInTheDocument()
  })
})

describe('quyền theo nhân sự', () => {
  test('đặt ngoại lệ GRANT không hết hạn gửi đúng body', async () => {
    let body: unknown
    mockApi({
      ...base(ADMIN),
      '/api/admin/users/5/access': () => json(200, { userId: 5, roles: ['EDITOR'], overrides: [], effective: ['cms.content'] }),
      '/api/admin/roles': () => json(200, [editor]),
      '/api/admin/permissions': () => json(200, catalog),
      '/api/admin/users/5/permissions/report.view': (b) => ((body = b), json(200, { userId: 5, roles: ['EDITOR'], overrides: [], effective: [] })),
    })
    renderAt('/quan-tri/nhan-su/5')
    const user = userEvent.setup()
    expect(await screen.findByText('Biên tập viên')).toBeInTheDocument()
    await user.selectOptions(await screen.findByLabelText('Quyền'), 'report.view')
    await user.click(screen.getByRole('button', { name: 'Đặt ngoại lệ' }))
    expect(await screen.findByText('Vui lòng nhập lý do thay đổi')).toBeInTheDocument()
    expect(body).toBeUndefined()
    await user.type(reasonBox(), 'Hỗ trợ tạm')
    await user.click(screen.getByRole('button', { name: 'Đặt ngoại lệ' }))
    await waitFor(() => expect(body).toEqual({ effect: 'GRANT', expiresAt: null }))
  })
})

describe('nhật ký', () => {
  const entry = (id: number, extra = {}) => ({
    id,
    actorId: 1,
    actorEmail: 'a@x.vn',
    actorName: 'Lan',
    action: 'UPDATE',
    targetType: 'ROLE',
    targetId: '2',
    before: { permissions: ['a'] },
    after: { permissions: ['a', 'b'] },
    reason: 'Đổi quyền',
    createdAt: '2026-11-20T03:00:00Z',
    ...extra,
  })

  test('gửi đúng tham số lọc, tải thêm theo cursor và xem Trước/Sau', async () => {
    const calls = mockApi({
      ...base(ADMIN),
      '/api/admin/users?limit=100': () => json(200, { data: [{ id: 1, email: 'a@x.vn', displayName: 'Lan', roles: ['ADMIN'] }], nextCursor: null }),
      '/api/admin/audit-logs?limit=50': () => json(200, { data: [entry(10)], nextCursor: 'c1' }),
      '/api/admin/audit-logs?limit=50&cursor=c1': () => json(200, { data: [entry(9, { action: 'CREATE' })], nextCursor: null }),
      '/api/admin/audit-logs?actorId=1&targetType=ROLE&targetId=2&from=2026-11-01&to=2026-11-30&limit=50': () => json(200, { data: [], nextCursor: null }),
    })
    renderAt('/quan-tri/nhat-ky')
    const user = userEvent.setup()
    expect(await screen.findByText('UPDATE')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /UPDATE/ }))
    expect(screen.getByRole('region', { name: 'Sau' })).toHaveTextContent('"b"')
    expect(screen.getByRole('region', { name: 'Trước' })).not.toHaveTextContent('"b"')

    await user.click(screen.getByRole('button', { name: 'Tải thêm' }))
    expect(await screen.findByText('CREATE')).toBeInTheDocument()

    await user.selectOptions(await screen.findByLabelText('Người thực hiện'), '1')
    await user.type(screen.getByLabelText('Loại đối tượng'), 'ROLE')
    await user.type(screen.getByLabelText('Mã đối tượng'), '2')
    await user.type(screen.getByLabelText('Từ ngày'), '2026-11-01')
    await user.type(screen.getByLabelText('Đến ngày'), '2026-11-30')
    expect(await screen.findByText('Chưa có bản ghi nào.')).toBeInTheDocument()
    expect(calls).toContain('/api/admin/audit-logs?actorId=1&targetType=ROLE&targetId=2&from=2026-11-01&to=2026-11-30&limit=50')
  })
})
