import { expect, test, type Page } from '@playwright/test'

const ok = (body: unknown, status = 200) => ({ status, contentType: 'application/json', body: JSON.stringify(body) })
const fail = (status: number, code: string) => ({ status, contentType: 'application/json', body: JSON.stringify({ error: { code, message: code } }) })

test.use({ viewport: process.env.SHOTS && process.env.VW ? { width: Number(process.env.VW), height: 844 } : { width: 1280, height: 720 } })

// Chụp ảnh khi đặt SHOTS=<thư mục> (kiểm giao diện thủ công); bình thường bỏ qua.
async function shot(page: Page, name: string) {
  if (process.env.SHOTS) await page.screenshot({ path: `${process.env.SHOTS}/${name}-${page.viewportSize()?.width}.png`, fullPage: true })
}

const questions = [
  { id: 11, skill: 'READING', type: 'CONNECTIVE_MCQ', prompt: 'Đọc đoạn văn rồi chọn đáp án đúng.\n\nMai usually walks to school because it is only ten minutes from her house.\n\nWhy does Mai usually walk?', payload: null,
    choices: [{ id: 1, label: 'A', content: 'She likes the rain' }, { id: 2, label: 'B', content: 'Her school is close' }, { id: 3, label: 'C', content: 'Her father is busy' }] },
  { id: 12, skill: 'GRAMMAR', type: 'CONNECTIVE_CLOZE', prompt: 'Điền dạng đúng của động từ trong ngoặc.',
    payload: { text: 'She suggested {b1} (go) by taxi.', blanks: ['b1'], labels: { b1: 'Dạng động từ sau suggest' } }, choices: [] },
]
const started = (guest: boolean) => ({ attemptId: 7, ...(guest ? { guestToken: 'tok-guest' } : {}), title: 'Kiểm tra đầu vào (đề mẫu)', startedAt: 'x', deadlineAt: 'y', timeLimitSec: 900, remainingSec: 900, questions })
const result = (attemptId: number, band: number) => ({
  attemptId, submittedAt: '2026-11-18T01:00:00Z', overallBand: band,
  skills: { READING: { correct: 3, total: 4, percent: 75, band: 5 }, GRAMMAR: { correct: 1.5, total: 4, percent: 37.5, band: 3 } },
  recommendedCourse: { courseId: 3, slug: 'pre-ielts', title: 'Pre IELTS' },
})

interface Opts { loggedIn: boolean; old?: boolean }
async function mockPlacement(page: Page, opts: Opts) {
  const sent = { starts: 0, drafts: [] as unknown[], submits: [] as unknown[], claims: [] as (string | null)[], tokens: [] as (string | null)[] }
  await page.route('**/api/**', async (route) => {
    const req = route.request()
    const { pathname } = new URL(req.url())
    if (pathname === '/api/auth/refresh') return route.fulfill(opts.loggedIn ? ok({ accessToken: 't', expiresIn: 900, user: { id: 1, displayName: 'Lan', role: 'STUDENT' } }) : fail(401, 'no'))
    if (pathname === '/api/me/placement') return route.fulfill(ok({ result: opts.old ? result(2, 3) : null }))
    if (pathname === '/api/placement/attempts') { sent.starts++; return route.fulfill(ok(started(!opts.loggedIn), 201)) }
    if (pathname === '/api/placement/attempts/7/draft') { sent.drafts.push(req.postDataJSON()); return route.fulfill(ok({ remainingSec: 850 })) }
    if (pathname === '/api/placement/attempts/7/submit') {
      sent.submits.push(req.postDataJSON())
      sent.tokens.push(req.headers()['x-placement-token'] ?? null)
      return route.fulfill(ok(opts.loggedIn ? { submitted: true, requiresLogin: false, result: result(7, 4.5) } : { submitted: true, requiresLogin: true }))
    }
    if (pathname === '/api/placement/attempts/7/claim') { sent.claims.push(req.headers()['x-placement-token'] ?? null); return route.fulfill(ok(result(7, 4.5))) }
    return route.fulfill(fail(404, 'x'))
  })
  return sent
}

async function doTest(page: Page) {
  await page.getByRole('radio', { name: /Her school is close/ }).click()
  await shot(page, '3-lam-bai-mcq')
  await page.getByRole('button', { name: 'Câu tiếp' }).click()
  await page.getByLabel('Dạng động từ sau suggest').fill('going')
  await shot(page, '4-lam-bai-dien-tu')
  await page.getByRole('button', { name: 'Nộp bài' }).click()
  await shot(page, '5-xac-nhan')
  await page.getByRole('alertdialog').getByRole('button', { name: 'Nộp bài' }).click()
}

test('khách: làm bài → đăng nhập để xem → quay lại tự nhận kết quả', async ({ page }) => {
  const opts: Opts = { loggedIn: false }
  const sent = await mockPlacement(page, opts)
  await page.goto('/kiem-tra-dau-vao') // công khai, không bị chuyển sang đăng nhập
  await expect(page.getByRole('heading', { name: 'Kiểm tra đầu vào' })).toBeVisible()
  await expect(page.getByText('Không tạm dừng được')).toBeVisible()
  await shot(page, '1-gioi-thieu-khach')
  expect(sent.starts).toBe(0)
  await page.getByRole('button', { name: 'Bắt đầu' }).click()
  await expect(page.getByText(/Câu 1\/2 · Đọc/)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Tạm dừng' })).toHaveCount(0)
  await shot(page, '2-lam-bai-bat-dau')

  // Tải lại trang giữa chừng: lượt khách vẫn tiếp tục được nhờ localStorage.
  await page.getByRole('radio', { name: /Her school is close/ }).click()
  await page.reload()
  await page.getByRole('button', { name: 'Tiếp tục bài đang làm' }).click()
  await expect(page.getByRole('radio', { name: /Her school is close/ })).toHaveAttribute('aria-checked', 'true')
  expect(sent.starts).toBe(1)

  await doTest(page)
  await expect(page.getByRole('heading', { name: 'Đăng ký hoặc đăng nhập để xem kết quả' })).toBeVisible()
  await shot(page, '6-cho-dang-nhap')
  expect(sent.tokens).toEqual(['tok-guest'])
  await expect(page.getByRole('link', { name: 'Đăng nhập' })).toHaveAttribute('href', '/dang-nhap?next=%2Fkiem-tra-dau-vao')

  // Đăng nhập xong quay lại: tự claim bằng token trên máy.
  opts.loggedIn = true
  await page.goto('/kiem-tra-dau-vao')
  await expect(page.getByLabel('Band tổng')).toHaveText('4.5')
  await expect(page.getByText('Đúng 3/4 câu · 75%')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Mua gói học' })).toHaveAttribute('href', '/thanh-toan?khoa=pre-ielts')
  await shot(page, '7-ket-qua-claim')
  expect(sent.claims).toEqual(['tok-guest'])
  expect(await page.evaluate(() => localStorage.length)).toBe(0)
})

test('học viên: thấy kết quả cũ, làm lại, nhận kết quả ngay; mất mạng vẫn làm tiếp', async ({ page, context }) => {
  const sent = await mockPlacement(page, { loggedIn: true, old: true })
  await page.goto('/kiem-tra-dau-vao')
  await expect(page.getByLabel('Band tổng')).toHaveText('3.0')
  await shot(page, '1-gioi-thieu-hoc-vien')
  await page.getByRole('button', { name: 'Làm lại' }).click()
  await expect(page.getByText(/Câu 1\/2 · Đọc/)).toBeVisible()

  await context.setOffline(true)
  await page.getByRole('radio', { name: /Her school is close/ }).click()
  await expect(page.getByText('Đang lưu trên máy, sẽ gửi khi có mạng. Bạn vẫn làm bài tiếp được.')).toBeVisible({ timeout: 6000 })
  await shot(page, '2b-mat-mang')
  expect(sent.drafts).toHaveLength(0)
  await context.setOffline(false)
  await expect.poll(() => sent.drafts.length, { timeout: 15_000 }).toBeGreaterThan(0)

  await doTest(page)
  await expect(page.getByLabel('Band tổng')).toHaveText('4.5')
  await shot(page, '7-ket-qua-hoc-vien')
  expect(sent.submits).toHaveLength(1)
  expect(await page.evaluate(() => localStorage.length)).toBe(0)
})
