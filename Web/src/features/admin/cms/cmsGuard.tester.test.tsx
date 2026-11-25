import { screen } from '@testing-library/react'
import { setAccessToken } from '../../../lib/api'
import { auth, json, mockApi, renderAt } from '../../../testUtils'

// Tester #18/#24/#70: gõ thẳng URL các màn CMS ngân hàng câu hỏi, nhập hàng loạt, đề thi khi thiếu cms.content.
const base = (perms: string[]) => ({ '/api/auth/refresh': () => json(200, auth), '/api/me/permissions': () => json(200, { permissions: perms }) })
const PATHS = [
  '/quan-tri/cms/ngan-hang',
  '/quan-tri/cms/ngan-hang/5',
  '/quan-tri/cms/ngan-hang/5/nhap-hang-loat',
  '/quan-tri/cms/de-thi',
  '/quan-tri/cms/de-thi/quy-doi',
  '/quan-tri/cms/de-thi/5',
]

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

describe('route CMS chặn theo quyền', () => {
  test.each(PATHS)('%s: thiếu cms.content bị chặn, không gọi /admin/cms', async (path) => {
    const calls = mockApi(base(['report.view', 'audit.view', 'rbac.manage']))
    renderAt(path)
    expect(await screen.findByRole('heading', { name: 'Không có quyền truy cập' })).toBeInTheDocument()
    expect(calls.filter((c) => c.includes('/admin/cms'))).toEqual([])
  })

  test.each(PATHS)('%s: học viên (không có quyền quản trị nào) không gọi /admin/cms', async (path) => {
    const calls = mockApi(base([]))
    renderAt(path)
    await screen.findByText(/Không có quyền|Đăng nhập|Trang chủ/i, undefined, { timeout: 3000 }).catch(() => undefined)
    expect(calls.filter((c) => c.includes('/admin/cms'))).toEqual([])
  })
})
