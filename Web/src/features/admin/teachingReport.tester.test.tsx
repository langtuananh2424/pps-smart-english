import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'

// Tester #62, D-176: xuất theo bộ lọc, khóa nút khi đang xuất, lỗi mạng, truy cập, không chữ "AI".
const base = { '/api/auth/refresh': () => json(200, auth), '/api/me/permissions': () => json(200, { permissions: ['report.teaching'] }) }
const row = (o = {}) => ({ userId: 5, displayName: 'Cô Hoa', role: 'TEACHER', groupSessions: 4, groupHours: 6, tutoringSessions: 1, tutoringHours: 1.5, totalSessions: 5, totalHours: 7.5, ...o })
const report = (o = {}) => ({ from: '2026-11-01', to: '2026-11-28', lastExportAt: null, teachers: [row(), row({ role: 'ASSISTANT', totalSessions: 1 })], editedSessions: [], ...o })
const classes = [{ id: 3, name: 'Lớp A1', courseTitle: 'IELTS', status: 'OPEN' }]

beforeEach(() => setAccessToken(null))
afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

/** fetch giả: báo cáo, danh sách lớp, xuất file theo hàm truyền vào; ghi lại mọi lời gọi. */
function stub(exportFn: (url: string, init?: RequestInit) => Promise<Response> | Response, opts: { report?: () => Response; classes?: () => Response } = {}) {
  const calls: string[] = []
  mockApi(base)
  const f = globalThis.fetch
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    calls.push(`${init?.method ?? 'GET'} ${url}`)
    if (url.startsWith('/api/admin/reports/teaching/export')) return exportFn(url, init)
    if (url === '/api/admin/reports/teaching/classes') return (opts.classes ?? (() => json(200, classes)))()
    if (url.startsWith('/api/admin/reports/teaching?')) return (opts.report ?? (() => json(200, report())))()
    return f(url, init)
  })
  return calls
}

function noDownload() {
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  URL.createObjectURL = vi.fn(() => 'blob:x')
  URL.revokeObjectURL = vi.fn()
}

test('xuất file gửi đúng bộ lọc vai trò và lớp đang chọn', async () => {
  noDownload()
  const calls = stub(() => new Response('csv', { status: 200 }))
  renderAt('/quan-tri/buoi-day')
  await screen.findAllByText('Cô Hoa')
  const user = userEvent.setup()
  await screen.findByRole('option', { name: 'Lớp A1 · IELTS' })
  await user.selectOptions(screen.getByLabelText('Vai trò'), 'TEACHER')
  await user.selectOptions(screen.getByLabelText('Lớp'), '3')
  await waitFor(() => expect(calls.some((c) => c.includes('role=TEACHER&classId=3') && c.startsWith('GET'))).toBe(true))
  await user.click(await screen.findByRole('button', { name: 'Xuất file' }))
  await waitFor(() => expect(calls.filter((c) => c.startsWith('POST /api/admin/reports/teaching/export'))).toHaveLength(1))
  expect(calls.find((c) => c.startsWith('POST /api/admin/reports/teaching/export'))).toMatch(/^POST \/api\/admin\/reports\/teaching\/export\?from=[\d-]+&to=[\d-]+&role=TEACHER&classId=3$/)
})

test('đang xuất: nút bị khóa, bấm nhiều lần chỉ gửi một POST', async () => {
  noDownload()
  let release: (r: Response) => void = () => {}
  const calls = stub(() => new Promise<Response>((r) => (release = r)))
  renderAt('/quan-tri/buoi-day')
  await screen.findAllByText('Cô Hoa')
  const user = userEvent.setup()
  await user.click(screen.getByRole('button', { name: 'Xuất file' }))
  const busy = await screen.findByRole('button', { name: 'Đang xuất…' })
  expect(busy).toBeDisabled()
  await user.click(busy)
  await user.click(busy)
  expect(calls.filter((c) => c.startsWith('POST /api/admin/reports/teaching/export'))).toHaveLength(1)
  release(new Response('csv', { status: 200 }))
  expect(await screen.findByRole('button', { name: 'Xuất file' })).toBeEnabled()
})

test('mất mạng khi xuất: báo lỗi kết nối, nút mở lại', async () => {
  stub(() => Promise.reject(new TypeError('Failed to fetch')))
  renderAt('/quan-tri/buoi-day')
  await screen.findAllByText('Cô Hoa')
  await userEvent.setup().click(screen.getByRole('button', { name: 'Xuất file' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Không thể xuất file')
  expect(screen.getByRole('button', { name: 'Xuất file' })).toBeEnabled()
})

test('báo cáo lỗi 500: hiện thử lại, không hiện nút xuất; danh sách lớp lỗi vẫn lọc được Tất cả lớp', async () => {
  stub(() => new Response('csv'), { report: () => json(500, { error: { code: 'internal_error', message: 'Đã có lỗi' } }), classes: () => json(500, {}) })
  renderAt('/quan-tri/buoi-day')
  expect(await screen.findByRole('button', { name: /Thử lại/ })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Xuất file' })).not.toBeInTheDocument()
  expect(within(screen.getByLabelText('Lớp')).getAllByRole('option').map((o) => o.textContent)).toEqual(['Tất cả lớp'])
})

test('một người hai vai hiện hai dòng; bảng có caption, tiêu đề cột; không có chữ AI', async () => {
  stub(() => new Response('csv'))
  renderAt('/quan-tri/buoi-day')
  const table = await screen.findByRole('table', { name: 'Tổng hợp theo người dạy' })
  expect(within(table).getAllByRole('row')).toHaveLength(3)
  expect(within(table).getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Người dạy', 'Vai trò', 'Buổi nhóm', 'Buổi kèm 1-1', 'Tổng giờ'])
  expect(within(table).getByText('Giáo viên')).toBeInTheDocument()
  expect(within(table).getByText('Trợ giảng')).toBeInTheDocument()
  for (const l of ['Từ ngày', 'Đến ngày', 'Vai trò', 'Lớp']) expect(screen.getByLabelText(l)).toBeInTheDocument()
  expect(document.body.textContent).not.toMatch(/\bAI\b/)
})
