import { expect, test, type Page } from '@playwright/test'

// FR-CLS-05, D-176: báo cáo số buổi dạy, quyền report.teaching. Giả API ở tầng mạng.
const json = (body: unknown, status = 200) => ({ status, contentType: 'application/json', body: JSON.stringify(body) })

const report = {
  from: '2026-11-01', to: '2026-11-30', lastExportAt: '2026-11-20T03:00:00Z',
  teachers: [
    { userId: 7, displayName: 'Cô Hoa', role: 'TEACHER', groupSessions: 8, groupHours: 12, tutoringSessions: 2, tutoringHours: 1.5, totalSessions: 10, totalHours: 13.5 },
    { userId: 8, displayName: 'Thầy Nam', role: 'ASSISTANT', groupSessions: 4, groupHours: 6, tutoringSessions: 0, tutoringHours: 0, totalSessions: 4, totalHours: 6 },
  ],
  editedSessions: [{ sessionId: 31, classId: 3, className: 'Lớp A1', teacherId: 7, teacherName: 'Cô Hoa', startsAt: '2026-11-15T01:00:00Z', edits: 2, lastEditedAt: '2026-11-22T05:00:00Z' }],
}

async function mock(page: Page, permissions: string[]) {
  const queries: URLSearchParams[] = []
  const exports: { method: string; params: URLSearchParams }[] = []
  await page.route('**/api/**', async (route) => {
    const req = route.request()
    const url = new URL(req.url())
    const p = url.pathname
    if (p === '/api/auth/refresh') return route.fulfill(json({ accessToken: 't', expiresIn: 900, user: { id: 1, displayName: 'Quản lý', role: 'STAFF' } }))
    if (p === '/api/me/permissions') return route.fulfill(json({ permissions }))
    if (p === '/api/me') return route.fulfill(json({ displayName: 'Quản lý' }))
    if (p === '/api/admin/reports/teaching/classes') return route.fulfill(json([{ id: 3, name: 'Lớp A1', courseTitle: 'IELTS Foundation', status: 'OPEN' }]))
    if (p === '/api/admin/reports/teaching/export') {
      exports.push({ method: req.method(), params: url.searchParams })
      return route.fulfill({ status: 200, contentType: 'text/csv', body: 'nguoi_day,so_buoi\nCô Hoa,10\n' })
    }
    if (p === '/api/admin/reports/teaching') {
      queries.push(url.searchParams)
      return route.fulfill(json(report))
    }
    return route.fulfill(json({ error: { code: 'x', message: 'x' } }, 404))
  })
  return { queries, exports }
}

test('lọc ngày, vai trò, lớp gửi đúng query; bảng và khối buổi bị sửa điểm danh', async ({ page }) => {
  const { queries } = await mock(page, ['report.teaching'])
  await page.goto('/quan-tri/buoi-day')
  await expect(page.getByRole('heading', { name: 'Báo cáo số buổi dạy' })).toBeVisible()

  const table = page.getByRole('table', { name: 'Tổng hợp theo người dạy' })
  await expect(table.getByRole('cell', { name: 'Cô Hoa' })).toBeVisible()
  await expect(table.getByRole('cell', { name: 'Trợ giảng' })).toBeVisible()
  await expect(table.getByRole('cell', { name: '13,5 giờ' })).toBeVisible()
  const edited = page.getByRole('region', { name: 'Buổi bị sửa điểm danh' })
  await expect(edited).toContainText('Lớp A1')
  await expect(edited).toContainText('Sửa 2 lần')

  await page.getByLabel('Từ ngày').fill('2026-11-01')
  await page.getByLabel('Đến ngày').fill('2026-11-30')
  await page.getByLabel('Vai trò').selectOption('TEACHER')
  await page.getByLabel('Lớp', { exact: true }).selectOption('3')
  await expect.poll(() => Object.fromEntries(queries.at(-1) ?? [])).toEqual({ from: '2026-11-01', to: '2026-11-30', role: 'TEACHER', classId: '3' })
})

test('Xuất file tải về .csv theo khoảng ngày đang lọc', async ({ page }) => {
  const { exports } = await mock(page, ['report.teaching'])
  await page.goto('/quan-tri/buoi-day')
  await page.getByLabel('Vai trò').selectOption('ASSISTANT')
  await page.getByLabel('Lớp', { exact: true }).selectOption('3')
  await page.getByLabel('Từ ngày').fill('2026-11-01')
  await page.getByLabel('Đến ngày').fill('2026-11-30')
  await expect(page.getByRole('button', { name: 'Xuất file' })).toBeEnabled()
  const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Xuất file' }).click()])
  expect(download.suggestedFilename()).toBe('buoi-day_2026-11-01_2026-11-30.csv')
  expect(exports).toHaveLength(1)
  expect(exports[0].method).toBe('POST')
  expect(Object.fromEntries(exports[0].params)).toEqual({ from: '2026-11-01', to: '2026-11-30', role: 'ASSISTANT', classId: '3' })
})

test('không có quyền: menu không có Buổi dạy, vào thẳng URL bị chặn', async ({ page }) => {
  await mock(page, ['order.view'])
  await page.goto('/quan-tri/don-hang')
  const nav = page.getByRole('navigation', { name: 'Điều hướng quản trị' })
  await expect(nav.getByRole('link', { name: 'Đơn hàng' })).toBeVisible()
  await expect(nav.getByRole('link', { name: 'Buổi dạy' })).toHaveCount(0)
  await page.goto('/quan-tri/buoi-day')
  await expect(page.getByRole('heading', { name: 'Không có quyền truy cập' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Báo cáo số buổi dạy' })).toHaveCount(0)
})

test('có quyền: menu hiện Buổi dạy', async ({ page }) => {
  await mock(page, ['report.teaching'])
  await page.goto('/quan-tri/buoi-day')
  await expect(page.getByRole('navigation', { name: 'Điều hướng quản trị' }).getByRole('link', { name: 'Buổi dạy' })).toBeVisible()
})
