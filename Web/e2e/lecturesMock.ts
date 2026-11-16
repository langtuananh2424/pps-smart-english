import type { Page } from '@playwright/test'

const ok = (body: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })

const lesson = (id: number, title: string, durationSec: number, extra = {}) => ({
  id, title, type: 'VIDEO', durationSec, order: id % 100, completed: false, watchedSec: 0, maxPositionSec: 0, lastWatchedAt: null, ...extra,
})
const topic = (id: number, title: string, skill: string, extra = {}) => ({
  id, title, skill, isFreeTrial: false, locked: false, completedLessons: 0, totalLessons: 0, xpEarned: 0, status: 'NOT_STARTED', lessons: [], banks: [], ...extra,
})

export const courses = [
  { courseId: 1, slug: 'ielts-inter', title: 'IELTS Intermediate · 5.0 → 7.0', targetType: 'IELTS', status: 'ACTIVE', expiresAt: null, lastActivityAt: null, completedLessons: 12, totalLessons: 31 },
  { courseId: 2, slug: 'giao-tiep', title: 'Giao tiếp phản xạ', targetType: 'Giao tiếp', status: 'ACTIVE', expiresAt: null, lastActivityAt: null, completedLessons: 4, totalLessons: 9 },
  { courseId: 3, slug: 'foundation', title: 'IELTS Foundation · Mất gốc → 4.5', targetType: 'IELTS', status: 'ACTIVE', expiresAt: null, lastActivityAt: null, completedLessons: 20, totalLessons: 20 },
  { courseId: 4, slug: 'cu', title: 'Khóa đã hết hạn', targetType: 'IELTS', status: 'EXPIRED', expiresAt: null, lastActivityAt: null, completedLessons: 5, totalLessons: 5 },
]

const writing = topic(10, 'Writing Task 2', 'WRITING', {
  status: 'IN_PROGRESS', completedLessons: 2, totalLessons: 5,
  lessons: [
    lesson(101, 'Phân tích đề & lập dàn ý', 860, { completed: true, watchedSec: 860, maxPositionSec: 860 }),
    lesson(102, 'Mở bài trong 3 câu', 665, { completed: true, watchedSec: 665, maxPositionSec: 665 }),
    lesson(103, 'Triển khai luận điểm', 1110, { watchedSec: 588, maxPositionSec: 588 }),
    lesson(104, 'Từ nối & mạch lạc', 750),
    lesson(105, 'Kết bài & tự sửa lỗi', 610),
  ],
  banks: [{ id: 7, title: 'Bộ 1', lessonId: 101, questionCount: 5 }, { id: 8, title: 'Bộ 2', lessonId: 102, questionCount: 5 }],
})
const roadmap = {
  levels: [
    {
      id: 1, code: 'L1', title: 'Level 1',
      topics: [
        topic(9, 'Listening', 'LISTENING', { status: 'DONE', completedLessons: 6, totalLessons: 6, lessons: [lesson(90, 'Nghe hiểu 4 phần', 600, { completed: true, watchedSec: 600, maxPositionSec: 600 })] }),
        topic(11, 'Reading', 'READING', { status: 'IN_PROGRESS', completedLessons: 4, totalLessons: 6, lessons: [lesson(110, 'Skimming', 600, { completed: true, watchedSec: 600, maxPositionSec: 600 })] }),
        writing,
        topic(12, 'Speaking', 'SPEAKING', { locked: true, status: 'LOCKED', totalLessons: 6 }),
        topic(13, 'Ngữ pháp & Từ vựng học thuật', 'GRAMMAR_VOCAB', { totalLessons: 8, lessons: [lesson(130, 'Câu phức', 600)] }),
      ],
    },
  ],
  resume: { lessonId: 103, lessonTitle: 'Triển khai luận điểm', lessonOrder: 3, topicId: 10, topicTitle: 'Writing Task 2', levelId: 1, levelCode: 'L1', levelTitle: 'Level 1', completedInTopic: 2, totalInTopic: 5, positionSec: 588, durationSec: 1110 },
}

// Một video rỗng hợp lệ để thẻ <video> không báo lỗi; trình duyệt không cần phát được.
const lessonBody = (id: number) => {
  const l = writing.lessons.find((x) => x.id === id)!
  return { id, title: l.title, type: 'VIDEO', durationSec: l.durationSec, video: { source: 'STORAGE', url: '/lectures-mock.mp4' }, courseId: 1, courseSlug: 'ielts-inter', courseTitle: courses[0].title, topicId: 10, topicTitle: 'Writing Task 2', levelId: 1 }
}

// Giả backend ở tầng mạng của trình duyệt; trả mảng thân các POST tiến độ để test kiểm.
export async function mockLectures(page: Page) {
  const posted: unknown[] = []
  await page.route('**/api/**', async (route) => {
    const req = route.request()
    const { pathname } = new URL(req.url())
    const lessonId = Number(/\/api\/lessons\/(\d+)/.exec(pathname)?.[1])
    if (pathname === '/api/auth/refresh') return route.fulfill(ok({ accessToken: 't', expiresIn: 900, user: { id: 1, displayName: 'Lan', role: 'STUDENT' } }))
    if (pathname === '/api/me') return route.fulfill(ok({ displayName: 'Lan' }))
    if (pathname === '/api/me/courses') return route.fulfill(ok(courses))
    if (pathname === '/api/me/courses/1/roadmap') return route.fulfill(ok(roadmap))
    if (pathname.endsWith('/progress') && req.method() === 'POST') {
      posted.push(req.postDataJSON())
      return route.fulfill({ status: 204 })
    }
    if (pathname.endsWith('/progress')) {
      const l = writing.lessons.find((x) => x.id === lessonId)
      return route.fulfill(ok({ lessonId, watchedSec: l?.watchedSec ?? 0, maxPositionSec: l?.maxPositionSec ?? 0, completed: !!l?.completed, completedAt: null }))
    }
    if (lessonId) return route.fulfill(ok(lessonBody(lessonId)))
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' })
  })
  await page.route('**/lectures-mock.mp4', (route) => route.fulfill({ status: 404 }))
  return posted
}
