import { expect, test, type Page } from '@playwright/test'

// Tester US-508 / FR-EX-05: IELTS Reading ở điện thoại xoay ngang (screen land), dọc và desktop. Backend giả ở tầng mạng.
const ok = (body: unknown, status = 200) => ({ status, contentType: 'application/json', body: JSON.stringify(body) })
const para = (i: number) => `<p>Paragraph ${i}. The history of tea in Asia spans thousands of years, from ancient rituals to modern daily life across many regions.</p>`
const mcq = (id: number) => ({ id, type: 'CONNECTIVE_MCQ', prompt: `Question ${id}: which statement matches the passage?`, payload: {}, groupId: 1,
  choices: [{ id: id * 10 + 1, label: 'A', content: 'True' }, { id: id * 10 + 2, label: 'B', content: 'False' }, { id: id * 10 + 3, label: 'C', content: 'Not given' }] })
const bank = {
  id: 7, title: 'Reading 1', ielts: true, lessonId: null, isTrial: false, mode: 'PRACTICE', timeLimitSec: 3600, skill: 'READING',
  questions: Array.from({ length: 8 }, (_, i) => mcq(i + 1)),
  groups: [{ id: 1, title: 'Passage 1', instruction: 'Choose TRUE, FALSE or NOT GIVEN', passageHtml: Array.from({ length: 30 }, (_, i) => para(i + 1)).join(''), sortOrder: 0, hasAudio: false, audioDurationMs: null }],
}

async function mock(page: Page) {
  await page.route('**/api/**', (route) => {
    const { pathname } = new URL(route.request().url())
    const m = route.request().method()
    if (pathname === '/api/auth/refresh') return route.fulfill(ok({ accessToken: 't', expiresIn: 900, user: { id: 1, displayName: 'Lan', role: 'STUDENT' } }))
    if (pathname === '/api/me') return route.fulfill(ok({ displayName: 'Lan' }))
    if (pathname === '/api/banks/7') return route.fulfill(ok(bank))
    if (pathname === '/api/me/attempts') return route.fulfill(ok({ data: [], nextCursor: null }))
    if (pathname === '/api/banks/7/attempts' && m === 'POST') return route.fulfill(ok({ id: 9, bankId: 7, startedAt: 'x', timeLimitSec: 3600, mode: 'PRACTICE' }, 201))
    if (pathname === '/api/attempts/9/progress') return route.fulfill(ok({ attemptId: 9, paused: false, remainingSec: 3600, mode: 'PRACTICE' }))
    if (pathname === '/api/attempts/9/hints') return route.fulfill(ok([{ questionId: 1, hints: [{ id: 1, content: 'Tìm từ khóa trong đoạn 3 rồi so với câu hỏi.', detail: null }] }]))
    if (pathname === '/api/attempts/9/draft') return route.fulfill(ok({ attemptId: 9, paused: false, mode: 'PRACTICE' }))
    return route.fulfill(ok({ error: { code: 'x', message: 'x' } }, 404))
  })
}

async function open(page: Page) {
  await mock(page)
  await page.goto('/ielts/7')
  // D-155: không còn màn chọn chế độ; chưa có lượt dở thì bấm "Bắt đầu" mới tạo lượt
  await page.getByRole('button', { name: 'Bắt đầu' }).click()
  await expect(page.getByRole('article', { name: 'Câu 1' })).toBeVisible()
}

const box = async (page: Page, sel: ReturnType<Page['locator']>) => (await sel.boundingBox())!
const overlap = (a: { x: number; y: number; width: number; height: number }, b: typeof a) =>
  a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height

test.describe('điện thoại ngang 844x390', () => {
  test.use({ viewport: { width: 844, height: 390 }, hasTouch: true, isMobile: true })

  test('hai cột cuộn riêng, không có nút Xem bài đọc, thanh dưới nằm trong màn hình', async ({ page }) => {
    await open(page)
    await expect(page.getByRole('button', { name: 'Xem bài đọc' })).toBeHidden()
    const passage = page.getByText('Paragraph 1.', { exact: false })
    const question = page.getByRole('article', { name: 'Câu 1' })
    await expect(passage).toBeVisible()
    const p = await box(page, passage)
    const q = await box(page, question)
    expect(p.x + p.width).toBeLessThanOrEqual(q.x + 1) // bài đọc bên trái, câu hỏi bên phải

    const header = await box(page, page.getByRole('banner'))
    expect(header.height).toBeLessThanOrEqual(48)
    const nav = await box(page, page.getByRole('navigation', { name: 'Điều hướng câu' }))
    expect(nav.y + nav.height).toBeLessThanOrEqual(390 + 0.5)
    expect(await page.evaluate(() => document.scrollingElement!.scrollHeight)).toBeLessThanOrEqual(390 + 1) // trang không cuộn, chỉ từng cột

    // cuộn cột bài đọc không làm cột câu hỏi di chuyển
    const qBefore = (await box(page, question)).y
    await passage.hover()
    await page.mouse.wheel(0, 600)
    await expect(page.getByText('Paragraph 1.', { exact: false })).not.toBeInViewport()
    expect((await box(page, question)).y).toBe(qBefore)
    await expect(page.getByRole('button', { name: 'Nộp bài' })).toBeInViewport()
    await page.screenshot({ path: 'C:/Users/LangAnh/AppData/Local/Temp/claude/1e48dda6f3666599/827a31d1-cbb7-4f1e-978b-9da13f64a812/scratchpad/ielts-land-844x390.png' })
  })

  test('Cam nổi không che Đánh dấu xem lại, Nộp bài và các ô số câu', async ({ page }) => {
    await open(page)
    await page.getByRole('radio', { name: /True/ }).first().click()
    const cam = page.getByRole('complementary', { name: 'Cam gợi ý' })
    await expect(cam).toBeVisible()
    await page.screenshot({ path: 'C:/Users/LangAnh/AppData/Local/Temp/claude/1e48dda6f3666599/827a31d1-cbb7-4f1e-978b-9da13f64a812/scratchpad/ielts-land-cam.png' })
    const c = await box(page, cam)
    for (const name of ['Đánh dấu xem lại', 'Nộp bài', 'Câu 8']) {
      const b = await box(page, page.getByRole('button', { name, exact: true }))
      expect(overlap(c, b), name).toBe(false)
    }
  })

  test('Cam thu gọn không che chữ lựa chọn, kể cả lựa chọn cuối khi cuộn hết cột', async ({ page }) => {
    await open(page)
    const cam = page.getByRole('complementary', { name: 'Cam gợi ý' })
    await expect(cam).toBeVisible()
    await expect(page.getByRole('button', { name: 'Hỏi Cam' })).toHaveAttribute('aria-expanded', 'false')
    const c = await box(page, page.getByRole('button', { name: 'Hỏi Cam' }))
    const hits: string[] = []
    const check = async () => {
      for (const r of await page.getByRole('radio').all()) {
        if (!(await r.isVisible())) continue
        // chữ của lựa chọn (và nhãn A/B/C) không được nằm dưới Cam; mép thẻ chạm vài px thì chấp nhận
        for (const t of await r.getByText(/^(A|B|C|True|False|Not given)$/).all()) {
          const b = await box(page, t)
          if (b.y + b.height > 50 && b.y < 335 && overlap(c, b)) hits.push(`${Math.round(b.y)}: ${await t.textContent()}`)
        }
      }
    }
    await check()
    await page.getByRole('article', { name: 'Câu 1' }).hover()
    await page.mouse.wheel(0, 20000)
    await page.waitForTimeout(200)
    await check()
    await page.screenshot({ path: 'C:/Users/LangAnh/AppData/Local/Temp/claude/1e48dda6f3666599/827a31d1-cbb7-4f1e-978b-9da13f64a812/scratchpad/ielts-land-cam-bottom.png' })
    expect(hits, 'lựa chọn bị Cam che').toEqual([])
  })
})

test.describe('không đổi ở dọc và desktop', () => {
  test('điện thoại dọc 390x844: có nút Xem bài đọc, thanh dưới cố định', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await open(page)
    await expect(page.getByRole('button', { name: 'Xem bài đọc' })).toBeVisible()
    await expect(page.getByText('Paragraph 1.', { exact: false })).toBeHidden()
    expect(await page.getByRole('navigation', { name: 'Điều hướng câu' }).evaluate((n) => getComputedStyle(n).position)).toBe('fixed')
    expect((await box(page, page.getByRole('banner'))).height).toBe(80)
    await page.screenshot({ path: 'C:/Users/LangAnh/AppData/Local/Temp/claude/1e48dda6f3666599/827a31d1-cbb7-4f1e-978b-9da13f64a812/scratchpad/ielts-portrait-390x844.png' })
  })

  test('desktop 1440x900: hai cột, thanh dưới cố định, header 80px', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await open(page)
    await expect(page.getByRole('button', { name: 'Xem bài đọc' })).toBeHidden()
    const p = await box(page, page.getByText('Paragraph 1.', { exact: false }))
    const q = await box(page, page.getByRole('article', { name: 'Câu 1' }))
    expect(p.x + p.width).toBeLessThanOrEqual(q.x + 1)
    expect(await page.getByRole('navigation', { name: 'Điều hướng câu' }).evaluate((n) => getComputedStyle(n).position)).toBe('fixed')
    expect((await box(page, page.getByRole('banner'))).height).toBe(80)
  })

  test('máy tính bảng ngang 1024x768 (cao hơn 500px) không dùng bố cục land', async ({ page }) => {
    await page.setViewportSize({ width: 1024, height: 768 })
    await open(page)
    expect((await box(page, page.getByRole('banner'))).height).toBe(80)
  })
})
