import type { Page } from '@playwright/test'
import { mockLectures } from './lecturesMock'

const ok = (body: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
const t = (type: string, questionCount: number, answered: number, correct: number) => ({
  type, questionCount, answered, correct, accuracy: answered ? Math.round((correct * 100) / answered) : null,
})

export const stats = {
  answered: 312, correct: 225, accuracy: 72, typesPracticed: 5,
  byType: [
    t('CONNECTIVE_MCQ', 40, 30, 25), t('CONNECTIVE_MATCH', 32, 20, 17), t('CONNECTIVE_CLOZE', 24, 0, 0),
    t('GRAMMAR_IDENTIFY', 36, 24, 13), t('GRAMMAR_SPOT_FIX', 28, 21, 16), t('GRAMMAR_BUILD', 30, 20, 13),
    t('WRITING', 8, 3, 0), t('SPEAKING', 12, 0, 0),
  ],
  weakest: { type: 'GRAMMAR_IDENTIFY', accuracy: 54, answered: 24 },
  band: { value: 5.5, source: 'PLACEMENT', at: '2026-11-01T00:00:00Z' },
}
const exam = { bankId: 50, progressPercent: 39, requiredPercent: 80, unlocked: false, reason: 'PROGRESS', attemptsUsed: 0, attemptsAllowed: 2, inProgressAttemptId: null, lastResult: null }
const quota = { pool: 0, courses: [{ courseId: 1, courseTitle: 'IELTS', period: 'WEEK', quota: 5, used: 2, remaining: 3, resetsAt: '2026-12-01T00:00:00Z' }] }

// Dùng lại mock bài giảng (khóa, roadmap) rồi thêm thống kê bài tập, bài thi khóa và lượt AI; route đăng ký sau được ưu tiên.
export async function mockExercises(page: Page, opts: { examStatus?: number; examBody?: unknown } = {}) {
  await mockLectures(page)
  await page.route('**/api/me/courses/1/exercise-stats', (r) => r.fulfill(ok(stats)))
  await page.route('**/api/courses/1/exam', (r) =>
    opts.examStatus ? r.fulfill({ status: opts.examStatus, contentType: 'application/json', body: '{"error":{"code":"x","message":"x"}}' }) : r.fulfill(ok(opts.examBody ?? exam)),
  )
  await page.route('**/api/me/quota', (r) => r.fulfill(ok(quota)))
}
