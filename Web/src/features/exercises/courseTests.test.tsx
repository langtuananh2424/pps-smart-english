import { screen, within } from '@testing-library/react'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import { retakesLabel } from './testsView'
import type { CourseTest } from './types'

const test1 = (extra: Partial<CourseTest> = {}): CourseTest => ({
  kind: 'CHAPTER', topicId: 11, title: 'Kiểm tra Unit 1', bankId: 5, unlocked: true, reason: null, attemptsUsed: 0, attemptsAllowed: 2,
  inProgressAttemptId: null, lastResult: null, ...extra,
})
const final = (extra: Partial<CourseTest> = {}) => test1({ kind: 'FINAL', topicId: null, title: 'Kiểm tra cuối khóa', bankId: 9, ...extra })
const myCourse = { courseId: 7, slug: 'ae1', title: 'AE1', targetType: 'IELTS', status: 'ACTIVE', expiresAt: null, lastActivityAt: null, completedLessons: 0, totalLessons: 4, answeredQuestions: 0, correctQuestions: 0 }
const exam = { bankId: 9, progressPercent: 40, requiredPercent: 70, unlocked: false, reason: 'PROGRESS', attemptsUsed: 0, attemptsAllowed: 2, inProgressAttemptId: null, lastResult: null }

const open = (tests: CourseTest[]) => {
  mockApi({
    '/api/auth/refresh': () => json(200, auth),
    '/api/me': () => json(200, { displayName: 'Lan' }),
    '/api/me/courses': () => json(200, [myCourse]),
    '/api/courses/7/tests': () => json(200, tests),
    '/api/courses/7/exam': () => json(200, exam),
  })
  renderAt('/bai-tap/khoa/7/kiem-tra')
}
const chapter = () => screen.findByRole('region', { name: 'Kiểm tra chương' })
const finalCard = () => screen.findByRole('region', { name: 'Kiểm tra cuối khóa' })

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

test('retakesLabel: allowed gồm lượt đầu; 0 lần làm lại hiện "Không làm lại"', () => {
  expect(retakesLabel(test1({ attemptsAllowed: 1 }))).toBe('Không làm lại')
  expect(retakesLabel(test1({ attemptsUsed: 0, attemptsAllowed: 3 }))).toBe('0/2')
  expect(retakesLabel(test1({ attemptsUsed: 2, attemptsAllowed: 3 }))).toBe('1/2')
})

test('chưa làm: Làm bài dẫn tới màn làm bài; hiện số lần làm lại', async () => {
  open([test1(), final({ unlocked: false, reason: 'PROGRESS' })])
  const c = within(await chapter())
  expect(c.getByText('Kiểm tra Unit 1')).toBeInTheDocument()
  expect(c.getByText('Chưa làm')).toBeInTheDocument()
  expect(c.getByText('0/1')).toBeInTheDocument()
  expect(c.getByRole('link', { name: 'Làm bài' })).toHaveAttribute('href', '/bai-tap/5?khoa=7')
})

test('đã làm: Đã làm · Xem điểm, điểm, Xem kết quả và Làm lại khi còn lượt', async () => {
  open([test1({ attemptsUsed: 1, lastResult: { attemptId: 21, score: 8, maxScore: 10, submittedAt: '2026-11-01T00:00:00Z' } }), final({ reason: 'PROGRESS', unlocked: false })])
  const c = within(await chapter())
  expect(c.getByText('Đã làm · Xem điểm')).toBeInTheDocument()
  expect(c.getByText('Điểm 8/10')).toBeInTheDocument()
  expect(c.getByRole('link', { name: 'Xem kết quả' })).toHaveAttribute('href', '/bai-tap/khoa/7/kiem-tra/21/ket-qua')
  expect(c.getByRole('link', { name: 'Làm lại' })).toHaveAttribute('href', '/bai-tap/5?khoa=7')
})

test('đang làm dở: Làm tiếp', async () => {
  open([test1({ attemptsUsed: 1, inProgressAttemptId: 33 }), final({ unlocked: false, reason: 'PROGRESS' })])
  const c = within(await chapter())
  expect(c.getByText('Đang làm')).toBeInTheDocument()
  expect(c.getByRole('link', { name: 'Làm tiếp' })).toBeInTheDocument()
})

test('hết lượt: báo hết lượt, giữ Xem kết quả, không có Làm lại', async () => {
  open([test1({ unlocked: false, reason: 'ATTEMPTS_EXHAUSTED', attemptsUsed: 2, lastResult: { attemptId: 8, score: 5, maxScore: 10, submittedAt: 'x' } }), final({ unlocked: false, reason: 'PROGRESS' })])
  const c = within(await chapter())
  expect(c.getByText('Hết lượt làm lại')).toBeInTheDocument()
  expect(c.getByRole('link', { name: 'Xem kết quả' })).toHaveAttribute('href', '/bai-tap/khoa/7/kiem-tra/8/ket-qua')
  expect(c.queryByRole('link', { name: 'Làm lại' })).not.toBeInTheDocument()
})

test('Unit chưa mở: link tới màn điều kiện mở Unit', async () => {
  open([test1({ unlocked: false, reason: 'UNIT_LOCKED' }), final({ unlocked: false, reason: 'PROGRESS' })])
  const c = within(await chapter())
  expect(c.getByText('Hoàn thành Unit trước')).toBeInTheDocument()
  expect(c.getByRole('link', { name: 'Xem điều kiện' })).toHaveAttribute('href', '/bai-giang/khoa/7/unit/11')
})

test('cuối khóa chưa đủ tiến độ: hiện % yêu cầu từ /exam, nút Làm bài bị khóa', async () => {
  open([final({ unlocked: false, reason: 'PROGRESS' })])
  const f = within(await finalCard())
  expect(await f.findByText('Khóa · Cần hoàn thành 70% khóa')).toBeInTheDocument()
  expect(f.getByRole('button', { name: 'Làm bài' })).toBeDisabled()
})

test('chưa cấu hình: báo chưa cấu hình, không có link làm bài', async () => {
  open([final({ unlocked: false, reason: 'NOT_CONFIGURED', bankId: null })])
  const f = within(await finalCard())
  expect(f.getByText('Chưa cấu hình')).toBeInTheDocument()
  expect(f.queryByRole('link')).not.toBeInTheDocument()
})

test('chưa mua (học thử): chỉ có CTA mua, không có link vào màn làm bài', async () => {
  open([test1({ unlocked: false, reason: 'ENROLLMENT_REQUIRED' }), final({ unlocked: false, reason: 'ENROLLMENT_REQUIRED' })])
  const c = within(await chapter())
  expect(c.getByText('Chỉ dành cho học viên đã mua')).toBeInTheDocument()
  expect(c.getByRole('link', { name: 'Mua khóa' })).toHaveAttribute('href', '/thanh-toan?khoa=ae1')
  expect(document.querySelector('a[href^="/bai-tap/5"], a[href^="/bai-tap/9"]')).toBeNull()
})
