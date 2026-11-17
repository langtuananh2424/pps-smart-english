import { expect, test, type Page } from '@playwright/test'

// Đặt GOALS_SHOTS=<thư mục> để chụp ảnh đối chiếu Figma.
const shots = process.env.GOALS_SHOTS
const viewports = [
  { name: 'desktop', width: 1280, height: 800 },
  { name: 'mobile', width: 390, height: 844 },
]

const ok = (body: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
const me = { id: 1, email: 'minh.anh@example.com', emailVerified: true, role: 'STUDENT', displayName: 'Minh Anh', selfLevel: 'TRUNG_CAP' }
const stats = { xp: 1240, streakCurrent: 12, streakBest: 12, lastActiveDate: null, title: 'x', nextTitle: null, nextTitleXp: null, activeToday: false, weekDays: [] }
const course = { courseId: 1, slug: 'ielts-inter', title: 'IELTS Intermediate', targetType: 'IELTS', status: 'ACTIVE', expiresAt: null, lastActivityAt: null, completedLessons: 0, totalLessons: 10, answeredQuestions: 0, correctQuestions: 0 }
const qa = { selfLevel: 'TRUNG_CAP', goals: ['IELTS_CERT'], targetBand: 7, weeklyHours: 'H3_5', skills: [], updatedAt: '2026-11-01T00:00:00Z' }
const resume = { lessonId: 55, lessonTitle: 'Bài 1', lessonOrder: 1, topicId: 1, topicTitle: 'Chủ đề', levelId: 1, levelCode: 'A', levelTitle: 'Cấp A', completedInTopic: 0, totalInTopic: 1, positionSec: 0, durationSec: null }

// Giả backend ở tầng mạng; trả mảng thân các PUT /api/me/goals/ielts.
async function mock(page: Page) {
  const puts: unknown[] = []
  let goals: unknown[] = []
  await page.route('**/api/**', async (route) => {
    const req = route.request()
    const { pathname } = new URL(req.url())
    if (pathname === '/api/auth/refresh') return route.fulfill(ok({ accessToken: 't', expiresIn: 900, user: { id: 1, displayName: 'Minh Anh', role: 'STUDENT' } }))
    if (pathname === '/api/me/goals/ielts') {
      const b = req.postDataJSON()
      puts.push(b)
      goals = [{ id: 1, courseId: null, targetType: 'IELTS', targetValue: b.targetBand.toFixed(1), targetDate: b.targetDate ?? null }]
      return route.fulfill(ok(goals[0]))
    }
    if (pathname === '/api/me/goals') return route.fulfill(ok(goals))
    if (pathname === '/api/me') return route.fulfill(ok(me))
    if (pathname === '/api/me/stats') return route.fulfill(ok(stats))
    if (pathname === '/api/me/courses') return route.fulfill(ok([course]))
    if (pathname === '/api/me/courses/1/roadmap') return route.fulfill(ok({ levels: [], resume }))
    if (pathname === '/api/me/qa') return route.fulfill(ok(qa))
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{"error":{"code":"x","message":"x"}}' })
  })
  return puts
}

for (const vp of viewports) {
  test(`chuỗi học ${vp.name}`, async ({ page }) => {
    await page.setViewportSize({ width: vp.width, height: vp.height })
    await mock(page)
    await page.goto('/chuoi-hoc')
    await expect(page.getByRole('heading', { name: 'Chuỗi học của bạn' })).toBeVisible()
    await expect(page.getByText('12 ngày liên tiếp')).toBeVisible()
    await expect(page.getByText('1.240 XP')).toBeVisible()
    await expect(page.getByRole('link', { name: 'Học tiếp ngay' })).toHaveAttribute('href', '/bai-giang/55')
    if (shots) await page.screenshot({ path: `${shots}/streak-${vp.name}.png`, fullPage: true })
    await page.getByRole('link', { name: 'Về hồ sơ' }).click()
    await expect(page).toHaveURL(/\/ho-so$/)
  })

  test(`mục tiêu ${vp.name}: sửa, lưu, đã lưu, hồ sơ cập nhật`, async ({ page }) => {
    await page.setViewportSize({ width: vp.width, height: vp.height })
    const puts = await mock(page)
    await page.goto('/ho-so')
    await page.getByRole('link', { name: 'Chỉnh sửa mục tiêu' }).click()
    await expect(page).toHaveURL(/\/muc-tieu$/)
    await expect(page.getByRole('heading', { name: 'Chỉnh sửa mục tiêu' })).toBeVisible()
    await expect(page.getByText('IELTS Intermediate')).toBeVisible()
    await expect(page.getByLabel('Band mục tiêu')).toHaveValue('7.0') // lấy từ Q&A khi chưa có mục tiêu IELTS
    if (shots) await page.screenshot({ path: `${shots}/goal-edit-${vp.name}.png`, fullPage: true })

    await page.getByLabel('Band mục tiêu').selectOption('7.5')
    await page.getByLabel('Ngày mục tiêu · Không bắt buộc').fill('2999-01-01')
    await page.getByRole('button', { name: 'Lưu mục tiêu' }).click()
    await expect(page.getByText('Đã lưu mục tiêu')).toBeVisible()
    await expect(page.getByText('IELTS 7.5')).toBeVisible()
    expect(puts).toEqual([{ targetBand: 7.5, targetDate: '2999-01-01' }])
    if (shots) await page.screenshot({ path: `${shots}/goal-saved-${vp.name}.png`, fullPage: true })

    await page.getByRole('link', { name: 'Về hồ sơ' }).click()
    await expect(page.getByText('IELTS 7.5')).toBeVisible()
    await expect(page.getByText('01/01/2999')).toBeVisible()
  })
}
