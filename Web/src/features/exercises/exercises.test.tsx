import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import { CamCard } from './CamCard'
import { ExamCard } from './ExamCard'
import { bandView, courseAccuracy, examView, familiesIn, groupTypes, percent } from './format'
import { StatTiles } from './StatTiles'
import type { ExamStatus, ExerciseStats, TypeStat } from './types'

const ts = (type: string, accuracy: number | null, answered = 10): TypeStat => ({ type, questionCount: 20, answered, correct: 0, accuracy })
const byType = [ts('CONNECTIVE_MCQ', 80), ts('GRAMMAR_IDENTIFY', 40), ts('GRAMMAR_BUILD', null, 0), ts('WRITING', null, 3), ts('SPEAKING', null, 0)]
const exam = (extra: Partial<ExamStatus> = {}): ExamStatus => ({
  bankId: 50, progressPercent: 40, requiredPercent: 80, unlocked: false, reason: 'PROGRESS', attemptsUsed: 0, attemptsAllowed: 2, inProgressAttemptId: null, lastResult: null, ...extra,
})
const stats = (extra: Partial<ExerciseStats> = {}): ExerciseStats => ({
  answered: 60, correct: 40, accuracy: 67, typesPracticed: 2, byType, weakest: { type: 'GRAMMAR_IDENTIFY', accuracy: 40, answered: 10 }, band: null, ...extra,
})
const inRouter = (ui: React.ReactElement) => render(<MemoryRouter>{ui}</MemoryRouter>)

describe('định dạng thống kê', () => {
  test('phần trăm và độ chính xác khóa', () => {
    expect(percent(72.4)).toBe('72%')
    expect(percent(null)).toBe('—')
    expect(courseAccuracy(312, 225)).toBe(72)
    expect(courseAccuracy(0, 0)).toBeNull()
    expect(courseAccuracy(undefined, undefined)).toBeNull()
  })
  test('band: có giá trị hoặc chưa có', () => {
    expect(bandView({ value: 6 })).toEqual({ kind: 'value', text: '6.0' })
    expect(bandView(null)).toEqual({ kind: 'none' })
  })
})

describe('nhóm dạng câu (D-62)', () => {
  test('chỉ hiện họ có dạng, đúng thứ tự', () => {
    expect(familiesIn(byType)).toEqual(['CONNECTIVE', 'GRAMMAR', 'WRITING', 'SPEAKING'])
    expect(familiesIn([ts('WRITING', null)])).toEqual(['WRITING'])
    expect(groupTypes(byType, null, '').map((g) => g.label)).toEqual(['Từ nối', 'Ngữ pháp', 'Viết', 'Nói'])
  })
  test('lọc theo họ và theo tên không phân biệt dấu', () => {
    expect(groupTypes(byType, 'GRAMMAR', '').map((g) => g.types.length)).toEqual([2])
    expect(groupTypes(byType, null, 'sua loi')).toEqual([])
    expect(groupTypes(byType, null, 'nhan dien')[0].types[0].type).toBe('GRAMMAR_IDENTIFY')
  })
})

describe('thẻ Cam gợi ý', () => {
  test('hiện dạng yếu nhất, không có nút luyện 10 câu', () => {
    inRouter(<CamCard weakest={{ type: 'GRAMMAR_IDENTIFY', accuracy: 40, answered: 10 }} />)
    expect(screen.getByRole('heading', { name: 'Nhận diện cấu trúc' })).toBeInTheDocument()
    expect(screen.getByText('40%')).toBeInTheDocument()
    expect(screen.queryByText(/Luyện 10 câu/)).toBeNull()
  })
  test('weakest null thì ẩn', () => {
    const { container } = inRouter(<CamCard weakest={null} />)
    expect(container).toBeEmptyDOMElement()
  })
})

describe('thẻ bài thi khóa', () => {
  test('khóa: lý do theo tiến độ, nút vô hiệu', () => {
    expect(examView(exam())).toEqual({ kind: 'locked', subtitle: 'Hoàn thành ≥ 80% bài học để mở · hiện 40%' })
    inRouter(<ExamCard status={exam()} />)
    expect(screen.getByText('Hoàn thành ≥ 80% bài học để mở · hiện 40%')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Bài thi khóa chưa mở' })).toBeDisabled()
  })
  test('mở: Làm bài thi / Làm tiếp dẫn tới bộ đề', () => {
    const { unmount } = inRouter(<ExamCard status={exam({ unlocked: true, reason: null })} />)
    expect(screen.getByRole('link', { name: 'Làm bài thi' })).toHaveAttribute('href', '/bai-tap/50')
    unmount()
    inRouter(<ExamCard status={exam({ unlocked: true, reason: null, inProgressAttemptId: 9 })} />)
    expect(screen.getByRole('link', { name: 'Làm tiếp' })).toHaveAttribute('href', '/bai-tap/50')
  })
  test('ẩn khi không có đề hoặc endpoint trả lỗi (null)', () => {
    expect(examView(null)).toEqual({ kind: 'hidden' })
    expect(examView(exam({ bankId: null, reason: 'NOT_CONFIGURED' }))).toEqual({ kind: 'hidden' })
    const { container } = inRouter(<ExamCard status={null} />)
    expect(container).toBeEmptyDOMElement()
  })
})

describe('ô số liệu và band (D-63)', () => {
  test('có band thì hiện giá trị; chưa có thì "Chưa có" kèm link kiểm tra đầu vào', () => {
    const { unmount } = inRouter(<StatTiles stats={stats({ band: { value: 5.5, source: 'EXAM', at: '2026-11-01T00:00:00Z' } })} exam={exam()} />)
    expect(screen.getByText('5.5')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Làm kiểm tra đầu vào' })).toBeNull()
    expect(screen.getByText('0/2 lượt')).toBeInTheDocument()
    unmount()
    inRouter(<StatTiles stats={stats({ accuracy: null })} exam={null} />)
    expect(screen.getByText('Chưa có')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Làm kiểm tra đầu vào' })).toHaveAttribute('href', '/kiem-tra-dau-vao')
    expect(screen.getAllByText('—')).toHaveLength(2) // độ chính xác và bài thi
  })
})

describe('màn hình', () => {
  beforeEach(() => {
    setAccessToken(null)
    localStorage.clear()
  })
  afterEach(() => vi.unstubAllGlobals())

  const course = (id: number, status: string, answered: number, correct: number, title: string) => ({
    courseId: id, slug: `k-${id}`, title, targetType: 'IELTS', status, expiresAt: null, lastActivityAt: null, completedLessons: 1, totalLessons: 4, answeredQuestions: answered, correctQuestions: correct,
  })
  const member = { '/api/auth/refresh': () => json(200, auth), '/api/me': () => json(200, { displayName: 'Lan' }) }

  test('chọn khóa: độ chính xác, số câu, khóa hết hạn có Gia hạn', async () => {
    mockApi({ ...member, '/api/me/courses': () => json(200, [course(1, 'ACTIVE', 312, 225, 'IELTS A'), course(2, 'ACTIVE', 0, 0, 'Giao tiếp'), course(3, 'EXPIRED', 5, 5, 'Cũ')]) })
    renderAt('/bai-tap')
    const a = (await screen.findByRole('heading', { name: 'IELTS A' })).closest('article')!
    expect(within(a).getByText('Độ chính xác · 72%')).toBeInTheDocument()
    expect(within(a).getByText('312 câu đã làm')).toBeInTheDocument()
    expect(within(a).getByRole('link', { name: 'Làm bài tập →' })).toHaveAttribute('href', '/bai-tap/khoa/1')
    const b = screen.getByRole('heading', { name: 'Giao tiếp' }).closest('article')!
    expect(within(b).queryByText(/Độ chính xác/)).toBeNull()
    expect(within(screen.getByRole('heading', { name: 'Cũ' }).closest('article')!).getByRole('link', { name: 'Gia hạn' })).toHaveAttribute('href', '/thanh-toan?khoa=k-3')
  })

  const lesson = (id: number, title: string, order: number, extra = {}) => ({ id, title, type: 'VIDEO', durationSec: 600, order, completed: false, watchedSec: 0, maxPositionSec: 0, lastWatchedAt: null, ...extra })
  const topic = (id: number, title: string, extra = {}) => ({ id, title, skill: 'WRITING', isFreeTrial: false, locked: false, completedLessons: 0, totalLessons: 2, xpEarned: 0, status: 'IN_PROGRESS', lessons: [], banks: [], ...extra })
  const roadmap = {
    levels: [{ id: 1, code: 'L1', title: 'L1', topics: [
      topic(9, 'Reading', { skill: 'READING', lessons: [lesson(90, 'Skimming', 1)], banks: [{ id: 5, title: 'B', lessonId: 90, questionCount: 4 }] }),
      topic(10, 'Writing Task 2', { lessons: [lesson(101, 'Phân tích đề', 1, { completed: true }), lesson(102, 'Mở bài', 2, { watchedSec: 9 })], banks: [{ id: 7, title: 'Bộ 1', lessonId: 101, questionCount: 5 }, { id: 8, title: 'Bộ 2', lessonId: 102, questionCount: 5 }] }),
    ] }],
    resume: { lessonId: 102, lessonTitle: 'Mở bài', lessonOrder: 2, topicId: 10, topicTitle: 'Writing Task 2', levelId: 1, levelCode: 'L1', levelTitle: 'L1', completedInTopic: 1, totalInTopic: 2, positionSec: 9, durationSec: 600 },
  }
  const quota = { pool: 0, courses: [{ courseId: 1, courseTitle: 'IELTS A', period: 'WEEK', quota: 5, used: 2, remaining: 3, resetsAt: '2026-12-01T00:00:00Z' }] }
  const listApi = {
    ...member,
    '/api/me/courses': () => json(200, [course(1, 'ACTIVE', 60, 40, 'IELTS A')]),
    '/api/me/courses/1/exercise-stats': () => json(200, stats()),
    '/api/courses/1/exam': () => json(403, { error: { code: 'exam_requires_enrollment', message: 'x' } }),
    '/api/me/courses/1/roadmap': () => json(200, roadmap),
    '/api/me/quota': () => json(200, quota),
  }

  test('danh sách bài tập: Cam, bài của topic đang học, nhóm dạng, lượt AI; thẻ thi ẩn khi 403', async () => {
    mockApi(listApi)
    renderAt('/bai-tap/khoa/1')
    expect(await screen.findByRole('region', { name: 'Cam gợi ý' })).toHaveTextContent('Nhận diện cấu trúc')
    expect(screen.queryByRole('region', { name: 'Bài thi khóa' })).toBeNull()
    const list = await screen.findByRole('region', { name: 'Làm theo bài giảng' })
    expect(within(list).getByRole('link', { name: 'Làm bài tập Bài 1' })).toHaveAttribute('href', '/bai-tap/7')
    expect(within(list).queryByText(/Skimming/)).toBeNull()
    await userEvent.click(within(list).getByRole('button', { name: 'Reading' }))
    expect(within(list).getByRole('link', { name: 'Làm bài tập Bài 1' })).toHaveAttribute('href', '/bai-tap/5')
    expect(await screen.findByText('Lượt chấm AI: 3/5 tuần này')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sắp có' })).toBeDisabled()
    await userEvent.type(screen.getByRole('searchbox'), 'xep khoi')
    expect(screen.getByRole('heading', { name: 'Xếp khối tạo câu' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Trắc nghiệm từ nối' })).toBeNull()
  })

  test('khóa chưa có bài tập: hiện thẻ trống dẫn tới bài giảng, ẩn các nhóm', async () => {
    const empty = { levels: [{ id: 1, code: 'L1', title: 'L1', topics: [topic(10, 'Writing', { lessons: [lesson(101, 'A', 1)] })] }], resume: null }
    mockApi({ ...listApi, '/api/me/courses/1/exercise-stats': () => json(200, stats({ byType: [], weakest: null })), '/api/me/courses/1/roadmap': () => json(200, empty) })
    renderAt('/bai-tap/khoa/1')
    const card = await screen.findByRole('region', { name: 'Chưa có bài tập' })
    expect(card).toHaveTextContent('Khóa học này chưa có bài tập.')
    expect(within(card).getByRole('link', { name: 'Xem bài giảng →' })).toHaveAttribute('href', '/bai-giang/khoa/1')
    expect(screen.queryByRole('region', { name: 'Dạng bài' })).toBeNull()
  })
})
