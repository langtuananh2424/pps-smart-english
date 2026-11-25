import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'

// Tester #56: route guard theo tổ hợp quyền, danh sách nhân sự, nhánh lỗi 403 / mạng, mã hóa lý do, XSS.
const perms = (list: string[]) => () => json(200, { permissions: list })
const base = (list: string[]) => ({ '/api/auth/refresh': () => json(200, auth), '/api/me/permissions': perms(list) })
const err = (status: number, code: string, message: string) => json(status, { error: { code, message } })
const page = <T,>(data: T[], nextCursor: string | null = null) => json(200, { data, nextCursor })
const FORBIDDEN = 'Không có quyền truy cập'
const NET = 'Không kết nối được máy chủ, vui lòng thử lại.'

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

const menuLinks = () => within(screen.getByRole('navigation', { name: 'Điều hướng quản trị' })).queryAllByRole('link')

// Trả lời mọi /api/admin/* rỗng để trang được phép hiển thị được.
function mockAdmin(list: string[]) {
  const calls: string[] = []
  vi.stubGlobal('fetch', async (url: string) => {
    calls.push(url)
    if (url === '/api/auth/refresh') return json(200, auth)
    if (url === '/api/me/permissions') return json(200, { permissions: list })
    if (url.startsWith('/api/admin/users/1/access')) return json(200, { userId: 1, roles: [], overrides: [], effective: [] })
    if (url.startsWith('/api/admin/users') || url.startsWith('/api/admin/audit-logs')) return page([])
    if (url === '/api/admin/roles') return json(200, [{ id: 1, code: 'ADMIN', name: 'Admin', permissions: [], memberCount: 1 }])
    if (url === '/api/admin/permissions') return json(200, [])
    return json(404)
  })
  return calls
}

const PAGES: [string, string, string][] = [
  ['/quan-tri/tong-quan', 'report.view', 'Tổng quan'],
  ['/quan-tri/goi-ban', 'product.manage', 'Gói bán'],
  ['/quan-tri/link-kich-hoat', 'activation.manage', 'Link kích hoạt'],
  ['/quan-tri/cms', 'cms.content', 'CMS'],
  ['/quan-tri/vai-tro', 'rbac.manage', 'Vai trò và quyền'],
  ['/quan-tri/vai-tro/moi', 'rbac.manage', 'Tạo vai trò'],
  ['/quan-tri/vai-tro/1', 'rbac.manage', 'Chi tiết vai trò'],
  ['/quan-tri/nhan-su', 'rbac.manage', 'Quản lý nhân sự'],
  ['/quan-tri/nhan-su/1', 'rbac.manage', 'Quyền của nhân sự'],
  ['/quan-tri/nhat-ky', 'audit.view', 'Nhật ký thao tác'],
]
const COMBOS = [[], ['audit.view'], ['rbac.manage'], ['cms.content'], ['report.view', 'product.manage'], ['activation.manage', 'audit.view']]

describe('route guard: gõ thẳng URL với từng tổ hợp quyền', () => {
  const cases = COMBOS.flatMap((c) => PAGES.map(([path, need, heading]) => ({ c, path, need, heading })))
  test.each(cases)('$c → $path', async ({ c, path, need, heading }) => {
    const calls = mockAdmin(c)
    renderAt(path)
    if (c.includes(need)) {
      expect(await screen.findByRole('heading', { name: heading, level: 1 })).toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: FORBIDDEN })).toBeNull()
    } else {
      expect(await screen.findByRole('heading', { name: FORBIDDEN })).toBeInTheDocument()
      // trang bị chặn không được gọi API dữ liệu của nó
      expect(calls.filter((u) => u.startsWith('/api/admin/'))).toEqual([])
    }
  })
})

describe('menu theo quyền', () => {
  test('chỉ rbac.manage: menu Nhân sự trỏ tới Vai trò, tab không có Nhật ký', async () => {
    mockAdmin(['rbac.manage'])
    const router = renderAt('/quan-tri')
    expect(await screen.findByRole('heading', { name: 'Vai trò và quyền' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/quan-tri/vai-tro')
    expect(menuLinks().map((a) => a.textContent)).toEqual(['Nhân sự'])
    expect(within(screen.getByRole('navigation', { name: 'Nhân sự' })).getAllByRole('link').map((a) => a.textContent)).toEqual(['Vai trò', 'Nhân sự'])
  })

  test('chỉ cms.content: index chuyển tới CMS, không có mục Nhân sự', async () => {
    mockAdmin(['cms.content'])
    const router = renderAt('/quan-tri')
    expect(await screen.findByRole('heading', { name: 'CMS' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/quan-tri/cms')
    expect(menuLinks().map((a) => a.textContent)).toEqual(['CMS'])
  })

  test('quyền lạ không mở thêm menu nào', async () => {
    mockAdmin(['order.manage', 'RBAC.MANAGE', 'rbac.manage '])
    renderAt('/quan-tri')
    expect(await screen.findByRole('heading', { name: FORBIDDEN })).toBeInTheDocument()
    expect(menuLinks()).toHaveLength(0)
  })

  test('nhật ký với người chỉ có audit.view không gọi danh sách nhân sự', async () => {
    const calls = mockAdmin(['audit.view'])
    renderAt('/quan-tri/nhat-ky')
    expect(await screen.findByText('Chưa có bản ghi nào.')).toBeInTheDocument()
    expect(calls.some((u) => u.startsWith('/api/admin/users'))).toBe(false)
  })
})

describe('danh sách nhân sự', () => {
  const lan = { id: 7, email: 'lan@x.vn', displayName: 'Nguyễn Lan', roles: ['EDITOR', 'SUPPORT'] }
  const minh = { id: 6, email: 'minh@x.vn', displayName: 'Minh', roles: ['ADMIN'] }

  test('hiện nhân sự, lọc tên (đã trim, mã hóa) + vai trò, tải thêm theo cursor', async () => {
    const calls = mockApi({
      ...base(['rbac.manage']),
      '/api/admin/roles': () => json(200, [{ id: 2, code: 'EDITOR', name: 'Biên tập viên', permissions: [], memberCount: 1 }]),
      '/api/admin/users?limit=20': () => page([lan], 'c1'),
      '/api/admin/users?limit=20&cursor=c1': () => page([minh]),
      '/api/admin/users?q=Nguy%E1%BB%85n+%26+co&role=EDITOR&limit=20': () => page([]),
    })
    renderAt('/quan-tri/nhan-su')
    const user = userEvent.setup()
    const link = await screen.findByRole('link', { name: 'Phân quyền Nguyễn Lan' })
    expect(link).toHaveAttribute('href', '/quan-tri/nhan-su/7')
    expect(link.closest('li')).toHaveTextContent('lan@x.vn')
    expect(link.closest('li')).toHaveTextContent('EDITOR, SUPPORT')

    await user.click(screen.getByRole('button', { name: 'Tải thêm' }))
    expect(await screen.findByRole('link', { name: 'Phân quyền Minh' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Tải thêm' })).toBeNull()

    await user.selectOptions(screen.getByLabelText('Vai trò'), 'EDITOR')
    await user.type(screen.getByLabelText('Tên hoặc email'), '  Nguyễn & co  ')
    expect(await screen.findByText('Không có nhân sự phù hợp.')).toBeInTheDocument()
    expect(calls).toContain('/api/admin/users?q=Nguy%E1%BB%85n+%26+co&role=EDITOR&limit=20')
  })

  test('403 từ API danh sách hiện thông báo không có quyền (không phải lỗi mạng)', async () => {
    mockApi({
      ...base(['rbac.manage']),
      '/api/admin/roles': () => json(200, []),
      '/api/admin/users?limit=20': () => err(403, 'forbidden', 'Không có quyền'),
    })
    renderAt('/quan-tri/nhan-su')
    const alert = await screen.findByRole('alert')
    expect(alert).not.toHaveTextContent(NET)
    expect(alert).toHaveTextContent(/không có quyền/i)
  })

  test('lỗi mạng: thông báo + Thử lại gọi lại được', async () => {
    let fail = true
    let hits = 0
    vi.stubGlobal('fetch', async (url: string) => {
      if (url === '/api/auth/refresh') return json(200, auth)
      if (url === '/api/me/permissions') return json(200, { permissions: ['rbac.manage'] })
      if (url === '/api/admin/roles') return json(200, [])
      if (url === '/api/admin/users?limit=20') {
        hits++
        if (fail) throw new TypeError('Failed to fetch')
        return page([lan])
      }
      return json(404)
    })
    renderAt('/quan-tri/nhan-su')
    expect(await screen.findByText(NET, {}, { timeout: 8000 })).toBeInTheDocument()
    expect(hits).toBe(3) // 1 lần + 2 lần tự thử lại
    fail = false
    await userEvent.setup().click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByRole('link', { name: 'Phân quyền Nguyễn Lan' })).toBeInTheDocument()
  }, 15000)
})

describe('nhánh lỗi chung', () => {
  test('/me/permissions lỗi mạng: guard hiện lỗi + Thử lại, không hiện trang', async () => {
    vi.stubGlobal('fetch', async (url: string) => {
      if (url === '/api/auth/refresh') return json(200, auth)
      if (url === '/api/me/permissions') throw new TypeError('Failed to fetch')
      return json(404)
    })
    renderAt('/quan-tri/vai-tro')
    expect(await screen.findAllByText(NET, {}, { timeout: 8000 })).not.toHaveLength(0)
    expect(screen.queryByRole('heading', { name: 'Vai trò và quyền' })).toBeNull()
  }, 15000)

  test('lệnh ghi lỗi mạng và 403 hiện thông báo tương ứng', async () => {
    let mode: 'net' | '403' = 'net'
    vi.stubGlobal('fetch', async (url: string) => {
      if (url === '/api/auth/refresh') return json(200, auth)
      if (url === '/api/me/permissions') return json(200, { permissions: ['rbac.manage'] })
      if (url === '/api/admin/users/5/access') return json(200, { userId: 5, roles: ['EDITOR'], overrides: [], effective: [] })
      if (url === '/api/admin/roles') return json(200, [{ id: 2, code: 'EDITOR', name: 'Biên tập viên', permissions: [], memberCount: 1 }])
      if (url === '/api/admin/permissions') return json(200, [])
      if (url === '/api/admin/users/5/roles/EDITOR') {
        if (mode === 'net') throw new TypeError('Failed to fetch')
        return err(403, 'forbidden', 'x')
      }
      return json(404)
    })
    renderAt('/quan-tri/nhan-su/5')
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Lý do · Bắt buộc'), 'Gỡ')
    await user.click(screen.getByRole('button', { name: 'Gỡ vai trò Biên tập viên' }))
    expect(await screen.findByText(NET)).toBeInTheDocument()
    mode = '403'
    await user.click(screen.getByRole('button', { name: 'Gỡ vai trò Biên tập viên' }))
    expect(await screen.findByText('Bạn không có quyền thực hiện thao tác này.')).toBeInTheDocument()
  })

  test('tạo vai trò bị 403 hiện thông báo không có quyền', async () => {
    mockApi({
      ...base(['rbac.manage']),
      '/api/admin/permissions': () => json(200, []),
      '/api/admin/roles': () => err(403, 'forbidden', 'Bạn không có quyền thực hiện thao tác này'), // thông điệp thật của SecurityErrorHandlers
    })
    renderAt('/quan-tri/vai-tro/moi')
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Mã vai trò'), 'NEW_ROLE')
    await user.type(screen.getByLabelText('Tên vai trò'), 'Mới')
    await user.type(screen.getByLabelText('Lý do · Bắt buộc'), 'Tạo')
    await user.click(screen.getByRole('button', { name: 'Tạo vai trò' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/không có quyền/i)
  })
})

describe('lý do qua header', () => {
  test('lý do tiếng Việt có ký tự đặc biệt và xuống dòng được mã hóa ASCII, giải mã ra đúng', async () => {
    const headers: string[] = []
    vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
      if (url === '/api/auth/refresh') return json(200, auth)
      if (url === '/api/me/permissions') return json(200, { permissions: ['rbac.manage'] })
      if (url === '/api/admin/users/5/access') return json(200, { userId: 5, roles: ['EDITOR'], overrides: [{ permission: 'cms.content', effect: 'REVOKE', expiresAt: null, grantedBy: 1 }], effective: [] })
      if (url === '/api/admin/roles') return json(200, [{ id: 2, code: 'EDITOR', name: 'Biên tập viên', permissions: [], memberCount: 1 }])
      if (url === '/api/admin/permissions') return json(200, [])
      if (url.startsWith('/api/admin/users/5/')) {
        headers.push(new Headers(init?.headers).get('X-Audit-Reason')!)
        return json(200, { userId: 5, roles: ['EDITOR'], overrides: [], effective: [] })
      }
      return json(404)
    })
    renderAt('/quan-tri/nhan-su/5')
    const user = userEvent.setup()
    const reason = 'Gỡ quyền 100% + a&b=c; "đặc biệt" #1'
    await user.type(await screen.findByLabelText('Lý do · Bắt buộc'), reason)
    await user.click(screen.getByRole('button', { name: 'Gỡ ngoại lệ cms.content' }))
    await waitFor(() => expect(headers).toHaveLength(1))
    expect(headers[0]).toMatch(/^[\x21-\x7e]+$/)
    expect(decodeURIComponent(headers[0])).toBe(reason)
    expect(headers[0]).not.toContain('+') // '+' phải thành %2B vì backend không đổi '+' thành dấu cách
  })
})

describe('không có XSS khi hiển thị dữ liệu từ API', () => {
  const evil = '<img src=x onerror="window.__xss=1">'
  const script = '<script>window.__xss=1</script>'

  test('nhật ký: before/after, reason, actorName, targetId hiển thị như văn bản', async () => {
    mockApi({
      ...base(['audit.view']),
      '/api/admin/audit-logs?limit=50': () =>
        page([
          {
            id: 1,
            actorId: 1,
            actorEmail: 'a@x.vn',
            actorName: evil,
            action: script,
            targetType: 'ROLE',
            targetId: evil,
            before: { name: evil },
            after: { html: script, nested: [evil] },
            reason: script,
            createdAt: '2026-11-20T03:00:00Z',
          },
        ]),
    })
    renderAt('/quan-tri/nhat-ky')
    const user = userEvent.setup()
    const row = await screen.findByRole('button', { name: /window.__xss/ })
    await user.click(row)
    expect(document.querySelector('main img, main script')).toBeNull()
    expect(screen.getByRole('region', { name: 'Sau' })).toHaveTextContent(script)
    expect(screen.getByRole('region', { name: 'Trước' }).textContent).toContain('onerror=\\"window.__xss=1\\"')
    expect((window as unknown as { __xss?: number }).__xss).toBeUndefined()
  })

  test('nhân sự và vai trò: displayName/email/role name độc hiển thị như văn bản', async () => {
    mockApi({
      ...base(['rbac.manage']),
      '/api/admin/roles': () => json(200, [{ id: 3, code: 'X', name: evil, permissions: [], memberCount: 0 }]),
      '/api/admin/users?limit=20': () => page([{ id: 9, email: script, displayName: evil, roles: [evil] }]),
    })
    renderAt('/quan-tri/nhan-su')
    expect(await screen.findByRole('link', { name: `Phân quyền ${evil}` })).toBeInTheDocument()
    expect(screen.getAllByText(evil, { exact: false }).length).toBeGreaterThan(0)
    expect(document.querySelector('main img, main script')).toBeNull()
    expect((window as unknown as { __xss?: number }).__xss).toBeUndefined()
  })
})
