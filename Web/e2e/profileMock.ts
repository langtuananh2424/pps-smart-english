import type { Page } from '@playwright/test'

const ok = (body: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })

const me = { id: 1, email: 'minh.anh@example.com', emailVerified: true, role: 'STUDENT', displayName: 'Minh Anh', selfLevel: 'TRUNG_CAP' }
const stats = { xp: 1240, streakCurrent: 12, streakBest: 20, lastActiveDate: null, title: 'x', nextTitle: null, nextTitleXp: null, activeToday: false, weekDays: [] }
const course = { courseId: 1, slug: 'ielts-inter', title: 'IELTS Intermediate', targetType: 'IELTS', status: 'ACTIVE', expiresAt: null, lastActivityAt: null, completedLessons: 0, totalLessons: 10, answeredQuestions: 0, correctQuestions: 0 }
const qa = { selfLevel: 'TRUNG_CAP', goals: ['IELTS_CERT'], targetBand: 7, weeklyHours: 'H3_5', skills: ['SPEAKING'], updatedAt: '2026-11-01T00:00:00Z' }

// Giả backend ở tầng mạng; trả mảng thân các PATCH /api/me để test kiểm.
export async function mockProfile(page: Page) {
  const patched: unknown[] = []
  let current = { ...me }
  await page.route('**/api/**', async (route) => {
    const req = route.request()
    const { pathname } = new URL(req.url())
    if (pathname === '/api/auth/refresh') return route.fulfill(ok({ accessToken: 't', expiresIn: 900, user: { id: 1, displayName: current.displayName, role: 'STUDENT' } }))
    if (pathname === '/api/me' && req.method() === 'PATCH') {
      const body = req.postDataJSON()
      patched.push(body)
      current = { ...current, ...body }
      return route.fulfill(ok(current))
    }
    if (pathname === '/api/me') return route.fulfill(ok(current))
    if (pathname === '/api/me/stats') return route.fulfill(ok(stats))
    if (pathname === '/api/me/courses') return route.fulfill(ok([course]))
    if (pathname === '/api/me/goals') return route.fulfill(ok([]))
    if (pathname === '/api/me/qa') return route.fulfill(ok(qa))
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{"error":{"code":"x","message":"x"}}' })
  })
  return patched
}
