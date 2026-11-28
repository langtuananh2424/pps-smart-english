import { expect, test, type Page } from '@playwright/test'

// FR-CLS-05/06, FR-ASG-01/03/04: khu giảng dạy của giáo viên có quyền class.teach. Giả API ở tầng mạng.
const json = (body: unknown, status = 200) => ({ status, contentType: 'application/json', body: JSON.stringify(body) })
const future = (h: number) => new Date(Date.now() + h * 3600_000).toISOString()

const cls = { id: 3, courseId: 9, courseTitle: 'IELTS Foundation', name: 'Lớp A1', startsOn: '2026-12-01', endsOn: '2027-03-01', mode: 'ONLINE', capacity: 30, status: 'OPEN', activeMembers: 12, avgLessonPercent: 62, laggingCount: 1 }
const stable = { ...cls, id: 4, name: 'Lớp B2', avgLessonPercent: null, laggingCount: 0 }
const detail = {
  info: cls,
  staff: [{ userId: 7, displayName: 'Cô Hoa', role: 'TEACHER' }],
  members: [{ userId: 21, displayName: 'Minh', email: 'm@x.vn', status: 'ACTIVE' }, { userId: 22, displayName: 'Nga', email: 'n@x.vn', status: 'ACTIVE' }],
}
const counts = { submitted: 1, late: 0, overdue: 0, pending: 0 }
const att = { present: 3, late: 0, absent: 0, excused: 0, ratePercent: 100 }
const student = (o: object) => ({ userId: 21, displayName: 'Minh', lessonPercent: 80, unitIndex: 2, totalUnits: 5, currentTopicTitle: 'Unit 2', assignments: counts, avgPercent: 75, chapterTests: [], attendance: att, lagging: false, lagReasons: [], ...o })
const board = { classId: 3, students: [student({}), student({ userId: 22, displayName: 'Nga', lagging: true, lagReasons: ['OVERDUE_ASSIGNMENT'], assignments: { ...counts, overdue: 2 } })] }
const tree = { courseId: 9, topics: [
  { topicId: 1, title: 'Unit 1', position: 1, banks: [{ bankId: 41, title: 'Bộ Unit 1', kind: 'PRACTICE', questionCount: 10 }], aiQuestions: [] },
  { topicId: 2, title: 'Unit 2', position: 2, banks: [], aiQuestions: [] },
] }
const asg = { id: 5, classId: 3, bankId: null, bankTitle: null, questionId: 77, questionType: 'WRITING', dueAt: future(48), status: 'ACTIVE', wholeClass: true, mode: null, recipients: 2, submitted: 1 }
const progress = [{ userId: 21, displayName: 'Minh', status: 'SUBMITTED', attemptId: 91, submittedAt: future(-1), aiScore: 70, maxScore: 100, finalScore: 70, teacherComment: null }]
const submission = { assignmentId: 5, userId: 21, questionType: 'WRITING', submittedAt: future(-1), writing: { sessionId: 1, status: 'SUBMITTED', question: { id: 77, prompt: 'Viết thư' }, turns: [], finalText: 'Dear sir, hello', grade: { scorePercent: 70, feedback: 'Cần mạch lạc hơn' }, createdAt: '' } }

async function mockTeaching(page: Page) {
  const sent = { unlock: [] as unknown[], assign: [] as unknown[], grade: [] as unknown[] }
  const unlocks: unknown[] = []
  const grades: unknown[] = []
  await page.route('**/api/**', async (route) => {
    const req = route.request()
    const { pathname: p } = new URL(req.url())
    const m = req.method()
    if (p === '/api/auth/refresh') return route.fulfill(json({ accessToken: 't', expiresIn: 900, user: { id: 7, displayName: 'Cô Hoa', role: 'STUDENT' } }))
    if (p === '/api/me/permissions') return route.fulfill(json({ permissions: ['class.teach'] }))
    if (p === '/api/me') return route.fulfill(json({ displayName: 'Cô Hoa' }))
    if (p === '/api/teaching/classes') return route.fulfill(json([cls, stable]))
    if (p === '/api/classes/3') return route.fulfill(json(detail))
    if (p === '/api/classes/3/progress') return route.fulfill(json(board))
    if (p === '/api/classes/3/assignable') return route.fulfill(json(tree))
    if (p === '/api/classes/3/unlocks') {
      if (m === 'POST') {
        sent.unlock.push(req.postDataJSON())
        unlocks.push({ id: 1, topicId: 2, userId: null, grantedBy: 7, grantedByName: 'Cô Hoa', grantedAt: new Date().toISOString(), revokedAt: null })
        return route.fulfill(json(unlocks, 201))
      }
      return route.fulfill(json(unlocks))
    }
    if (p === '/api/classes/3/assignments') {
      if (m === 'POST') {
        sent.assign.push(req.postDataJSON())
        return route.fulfill(json(asg, 201))
      }
      return route.fulfill(json([asg]))
    }
    if (p === '/api/classes/3/assignments/5/progress') return route.fulfill(json(progress))
    if (p === '/api/classes/3/assignments/5/submissions/91') return route.fulfill(json(submission))
    if (p === '/api/classes/3/assignments/5/attempts/91/grades') return route.fulfill(json(grades))
    if (p === '/api/classes/3/assignments/5/attempts/91/grade') {
      const b = req.postDataJSON()
      sent.grade.push(b)
      grades.push({ id: 1, attemptId: 91, score: b.score ?? null, comment: b.comment ?? null, gradedBy: 7, gradedAt: new Date().toISOString() })
      return route.fulfill(json(grades[0], 201))
    }
    return route.fulfill(json({ error: { code: 'x', message: 'x' } }, 404))
  })
  return sent
}

test('danh sách lớp có tiến độ TB, theo dõi; link cần hỗ trợ mở tiến độ đã lọc tụt lại', async ({ page }) => {
  await mockTeaching(page)
  await page.goto('/giang-day')
  const table = page.getByRole('table', { name: 'Lớp của tôi' })
  await expect(table.getByRole('columnheader', { name: 'Tiến độ TB' })).toBeVisible()
  await expect(table.getByRole('columnheader', { name: 'Theo dõi' })).toBeVisible()
  await expect(table.getByText('62%')).toBeVisible()
  await expect(table.getByText('Ổn định')).toBeVisible()

  await table.getByRole('link', { name: '1 học viên cần hỗ trợ' }).click()
  await expect(page).toHaveURL(/\/giang-day\/lop\/3\?loc=tut-lai$/)
  await expect(page.getByRole('heading', { name: 'Tiến độ lớp' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Nga' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Minh' })).toHaveCount(0)
  await expect(page.getByText('Quá hạn nộp')).toBeVisible()

  await page.getByRole('button', { name: 'Tất cả học viên' }).click()
  await expect(page.getByRole('link', { name: 'Minh' })).toBeVisible()
  await expect(page).toHaveURL(/\/giang-day\/lop\/3$/)
})

test('mở sớm Unit cho cả lớp', async ({ page }) => {
  const sent = await mockTeaching(page)
  await page.goto('/giang-day/lop/3')
  await page.getByRole('link', { name: 'Mở sớm Unit' }).click()
  await expect(page.getByRole('heading', { name: 'Mở sớm Unit' })).toBeVisible()
  await expect(page.getByText('Chưa mở sớm Unit nào.')).toBeVisible()
  await page.getByLabel('Unit', { exact: true }).selectOption({ label: 'Unit 2' })
  await page.getByRole('button', { name: 'Mở Unit' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Mở Unit' }).click()
  await expect(page.getByRole('table', { name: 'Các lần mở sớm' }).getByText('Cả lớp')).toBeVisible()
  expect(sent.unlock).toEqual([{ topicId: 2, studentIds: [] }])
})

test('giao bài 3 bước rồi sang tiến độ nộp bài', async ({ page }) => {
  const sent = await mockTeaching(page)
  await page.goto('/giang-day/lop/3')
  await page.getByRole('link', { name: 'Giao bài' }).click()
  await expect(page.getByRole('heading', { name: 'Giao bài' })).toBeVisible()
  await page.getByRole('radio', { name: /Bộ Unit 1/ }).check()
  await page.getByRole('button', { name: 'Tiếp tục' }).click()
  await page.getByLabel('Hạn nộp (ngày)').fill(future(48).slice(0, 10))
  await page.getByRole('button', { name: 'Xem lại' }).click()
  await expect(page.getByRole('heading', { name: 'Xác nhận giao bài' })).toBeVisible()
  await expect(page.getByText('Cả lớp')).toBeVisible()
  await page.getByRole('button', { name: 'Giao bài' }).click()
  await expect(page).toHaveURL(/\/giang-day\/lop\/3\/bai-giao\/5$/)
  await expect(page.getByRole('table', { name: 'Tiến độ nộp bài' }).getByText('Minh')).toBeVisible()
  expect(sent.assign).toHaveLength(1)
  expect(sent.assign[0]).toMatchObject({ bankId: 41, studentIds: [], mode: 'PRACTICE' })
})

test('chấm bài: mở bài nộp, nhập điểm và nhận xét, lưu gửi đúng nội dung', async ({ page }) => {
  const sent = await mockTeaching(page)
  await page.goto('/giang-day/lop/3/bai-giao/5')
  await page.getByRole('link', { name: 'Mở bài nộp của Minh' }).click()
  await expect(page).toHaveURL(/\/bai-nop\/91$/)
  await expect(page.getByText('Dear sir, hello')).toBeVisible()
  await page.getByLabel('Điểm mới').fill('85')
  await page.getByLabel('Nhận xét').fill('Tốt lắm')
  await page.getByRole('button', { name: 'Lưu nhận xét' }).click()
  await expect(page.getByText('Đã lưu nhận xét.')).toBeVisible()
  await expect(page.getByRole('cell', { name: 'Bản hiện tại' })).toBeVisible()
  expect(sent.grade).toEqual([{ score: 85, comment: 'Tốt lắm' }])
})
