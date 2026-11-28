import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'

const base = (list: string[]) => ({ '/api/auth/refresh': () => json(200, auth), '/api/me/permissions': () => json(200, { permissions: list }) })
const row = (o = {}) => ({ userId: 5, displayName: 'Cô Hoa', role: 'TEACHER', groupSessions: 4, groupHours: 6, tutoringSessions: 1, tutoringHours: 1, totalSessions: 5, totalHours: 7.5, ...o })
const edited = { sessionId: 9, classId: 3, className: 'Lớp A1', teacherId: 5, teacherName: 'Cô Hoa', startsAt: '2026-11-10T02:00:00Z', edits: 2, lastEditedAt: '2026-11-12T02:00:00Z' }
const report = (o = {}) => ({ from: '2026-11-01', to: '2026-11-28', lastExportAt: null, teachers: [row(), row({ userId: 6, displayName: 'Thầy Nam', role: 'ASSISTANT' })], editedSessions: [], ...o })
const classes = [{ id: 3, name: 'Lớp A1', courseTitle: 'IELTS', status: 'OPEN' }]
const isReport = (c: string) => c.startsWith('/api/admin/reports/teaching?')

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

function mock(handler: (url: string) => Response, extra: Record<string, () => Response> = {}) {
  const calls: string[] = []
  const inner = mockApi({ ...base(['report.teaching']), '/api/admin/reports/teaching/classes': () => json(200, classes), ...extra })
  const f = globalThis.fetch
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    if (isReport(url)) {
      calls.push(url)
      return handler(url)
    }
    return f(url, init)
  })
  return { calls, inner }
}

test('menu chỉ hiện Buổi dạy khi có report.teaching', async () => {
  mockApi({ ...base(['order.view']), '/api/admin/orders?limit=20': () => json(200, { data: [], nextCursor: null }) })
  renderAt('/quan-tri/don-hang')
  const menu = await screen.findByRole('navigation', { name: 'Điều hướng quản trị' })
  await screen.findByRole('link', { name: 'Đơn hàng' })
  expect(within(menu).queryByRole('link', { name: 'Buổi dạy' })).not.toBeInTheDocument()
})

test('thiếu quyền: trang bị chặn, không gọi báo cáo', async () => {
  const { calls } = mock(() => json(200, report()))
  vi.unstubAllGlobals()
  const c = mockApi(base(['order.view']))
  renderAt('/quan-tri/buoi-day')
  expect(await screen.findByRole('heading', { name: 'Không có quyền truy cập' })).toBeInTheDocument()
  expect(c.some((x) => x.includes('/reports/teaching'))).toBe(false)
  expect(calls).toHaveLength(0)
})

test('bảng tổng hợp, lọc gửi đúng query', async () => {
  const { calls } = mock(() => json(200, report()))
  renderAt('/quan-tri/buoi-day')
  expect(await screen.findByText('Chỉ tính các buổi đã điểm danh. Hệ thống không tính lương.')).toBeInTheDocument()
  expect(await screen.findByText('Cô Hoa')).toBeInTheDocument()
  expect(screen.getByText('Thầy Nam')).toBeInTheDocument()
  expect(screen.getAllByText(/7,5 giờ/).length).toBeGreaterThan(0)
  expect(screen.getAllByText('Trợ giảng').length).toBeGreaterThan(0)
  expect(within(screen.getByRole('navigation', { name: 'Điều hướng quản trị' })).getByRole('link', { name: 'Buổi dạy' })).toBeInTheDocument()
  const user = userEvent.setup()
  await screen.findByRole('option', { name: 'Lớp A1 · IELTS' })
  await user.selectOptions(screen.getByLabelText('Vai trò'), 'ASSISTANT')
  await user.selectOptions(screen.getByLabelText('Lớp'), '3')
  fireEvent.change(screen.getByLabelText('Từ ngày'), { target: { value: '2026-10-01' } })
  fireEvent.change(screen.getByLabelText('Đến ngày'), { target: { value: '2026-10-31' } })
  await waitFor(() => expect(calls).toContain('/api/admin/reports/teaching?from=2026-10-01&to=2026-10-31&role=ASSISTANT&classId=3'))
})

test('trạng thái rỗng', async () => {
  mock(() => json(200, report({ teachers: [] })))
  renderAt('/quan-tri/buoi-day')
  expect(await screen.findByText('Không có buổi dạy nào đã điểm danh trong khoảng này.')).toBeInTheDocument()
})

test('khoảng quá 366 ngày: báo lỗi và không gọi báo cáo', async () => {
  const { calls } = mock(() => json(200, report()))
  renderAt('/quan-tri/buoi-day')
  await screen.findByText('Cô Hoa')
  const n = calls.length
  fireEvent.change(screen.getByLabelText('Từ ngày'), { target: { value: '2025-01-01' } })
  fireEvent.change(screen.getByLabelText('Đến ngày'), { target: { value: '2026-01-02' } })
  expect(await screen.findByText('Khoảng xuất tối đa 366 ngày')).toBeInTheDocument()
  expect(calls).toHaveLength(n)
})

test('khối buổi bị sửa điểm danh và lần xuất trước', async () => {
  mock(() => json(200, report({ editedSessions: [edited], lastExportAt: '2026-11-11T02:00:00Z' })))
  renderAt('/quan-tri/buoi-day')
  const box = await screen.findByRole('region', { name: 'Buổi bị sửa điểm danh' })
  expect(box).toHaveTextContent('Lớp A1')
  expect(box).toHaveTextContent('Sửa 2 lần')
  expect(box).toHaveTextContent('Lần xuất trước: ')
  expect(box).not.toHaveTextContent('Chưa từng xuất')
})

test('chưa từng xuất và không có buổi sửa: không hiện khối', async () => {
  mock(() => json(200, report()))
  renderAt('/quan-tri/buoi-day')
  await screen.findByText('Cô Hoa')
  expect(screen.queryByRole('region', { name: 'Buổi bị sửa điểm danh' })).not.toBeInTheDocument()
})

test('xuất file gọi POST đúng tham số rồi làm mới báo cáo', async () => {
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: () => 'blob:x', revokeObjectURL: () => {} }))
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  let n = 0
  const methods: string[] = []
  const { calls } = mock(() => json(200, report({ lastExportAt: n++ ? '2026-11-28T01:00:00Z' : null })))
  const f = globalThis.fetch
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    if (url.startsWith('/api/admin/reports/teaching/export')) {
      methods.push(`${init?.method} ${url}`)
      return new Response('csv', { status: 200 })
    }
    return f(url, init)
  })
  renderAt('/quan-tri/buoi-day')
  await screen.findByText('Cô Hoa')
  const before = calls.length
  await userEvent.setup().click(screen.getByRole('button', { name: 'Xuất file' }))
  await waitFor(() => expect(methods).toHaveLength(1))
  expect(methods[0]).toMatch(/^POST \/api\/admin\/reports\/teaching\/export\?from=\d{4}-\d{2}-01&to=\d{4}-\d{2}-\d{2}$/)
  await waitFor(() => expect(calls.length).toBeGreaterThan(before))
})

test('xuất lỗi 4xx hiện thông điệp', async () => {
  mock(() => json(200, report()))
  const f = globalThis.fetch
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) =>
    url.startsWith('/api/admin/reports/teaching/export') ? json(400, { error: { code: 'x', message: 'Khoảng ngày không hợp lệ' } }) : f(url, init),
  )
  renderAt('/quan-tri/buoi-day')
  await screen.findByText('Cô Hoa')
  await userEvent.setup().click(screen.getByRole('button', { name: 'Xuất file' }))
  expect(await screen.findByText('Khoảng ngày không hợp lệ')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Xuất file' })).toBeEnabled()
})
