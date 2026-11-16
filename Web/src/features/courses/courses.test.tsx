import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import { fold, formatBand } from './format'

const course = (slug: string, title: string, bandFrom: number, bandTo: number, trial = 0, extra = {}) => ({
  slug, title, description: `Mô tả ${title}`, targetType: 'IELTS', bandFrom, bandTo, trialLessonCount: trial, priceFromVnd: 1500000, levelCount: 2, lessonCount: 20, ...extra,
})
const catalog = [
  course('begin', 'Begin', 0, 3, 3),
  course('ae1', 'Academic English 1', 3, 4, 3),
  course('ae2', 'Academic English 2', 4, 5, 0),
  course('pre', 'Pre IELTS', 5, 5.5, 3),
  course('found', 'IELTS Foundation', 5.5, 6, 0),
]
const card = (c: ReturnType<typeof course>, id: number) => ({ courseId: id, slug: c.slug, title: c.title, bandFrom: c.bandFrom, bandTo: c.bandTo, trialLessonCount: c.trialLessonCount, priceFromVnd: c.priceFromVnd })
const qa = { selfLevel: 'CO_BAN', goals: ['IELTS_CERT', 'WORK'], targetBand: 7, weeklyHours: 'H3_5', skills: [], updatedAt: 'x' }
const sugg = {
  basis: 'SELF_LEVEL', currentBand: 3, targetBand: 7, primary: card(catalog[1], 2), path: [card(catalog[2], 3), card(catalog[3], 4)],
}
const detail = { ...catalog[1], products: [], levels: [{ id: 1, code: 'L1', title: 'L1', topics: [{ id: 1, title: 'T', isFreeTrial: true, lessons: [{ id: 99, title: 'Bài 1', type: 'VIDEO', durationSec: 60 }] }] }] }
const mineCourse = (slug: string, id: number, status = 'ACTIVE') => ({ courseId: id, slug, title: slug, targetType: 'IELTS', status, expiresAt: null, lastActivityAt: null })

const pub = {
  '/api/catalog/courses': () => json(200, catalog),
  '/api/catalog/courses/ae1': () => json(200, detail),
}
const member = {
  ...pub,
  '/api/auth/refresh': () => json(200, auth),
  '/api/me': () => json(200, { displayName: 'Lan' }),
  '/api/me/courses': () => json(200, []),
  '/api/me/qa': () => json(204),
}

beforeEach(() => {
  setAccessToken(null)
  localStorage.clear()
})
afterEach(() => vi.unstubAllGlobals())

// Nút "Học thử" nằm trong thẻ khóa (khác chip lọc cùng tên).
const cardTrialButtons = () => screen.getAllByRole('button', { name: 'Học thử' }).filter((b) => b.closest('article'))

test('fold bỏ dấu; band 0 hiện "0"', () => {
  expect(fold('Giao tiếp Đầu vào')).toBe('giao tiep dau vao')
  expect(formatBand(0)).toBe('0')
  expect(formatBand(5)).toBe('5.0')
})

test('có Q&A: khối gợi ý có chip, khóa chính, lộ trình; thẻ khóa gợi ý nhãn "Phù hợp với bạn"', async () => {
  mockApi({ ...member, '/api/me/qa': () => json(200, qa), '/api/me/course-suggestions': () => json(200, sugg) })
  renderAt('/khoa-hoc')
  const block = await screen.findByRole('region', { name: 'Gợi ý khóa học' })
  expect(await within(block).findByText('Phù hợp nhất')).toBeInTheDocument()
  expect(within(block).getByText('Thi chứng chỉ IELTS')).toBeInTheDocument()
  expect(within(block).getByText('Công việc')).toBeInTheDocument()
  expect(within(block).getByText('Hiện tại ~3.0')).toBeInTheDocument()
  expect(within(block).getByText('Mục tiêu 7.0')).toBeInTheDocument()
  expect(within(block).getByRole('heading', { name: 'Academic English 1' })).toBeInTheDocument()
  expect(within(block).getByText('Từ ~3.0 lên 7.0, đúng mục tiêu của bạn.')).toBeInTheDocument()
  expect(within(block).getByText('Lộ trình tiếp theo: Academic English 2 → Pre IELTS')).toBeInTheDocument()
  expect(within(block).getByRole('link', { name: 'Mua gói học' })).toHaveAttribute('href', '/khoa-hoc/ae1')
  expect(screen.getAllByText('Phù hợp với bạn')).toHaveLength(3)
})

test('chưa làm Q&A: ẩn khối gợi ý, hiện lời mời', async () => {
  mockApi(member)
  renderAt('/khoa-hoc')
  expect(await screen.findByRole('link', { name: /Trả lời 4 câu/ })).toHaveAttribute('href', '/goi-y-khoa')
  expect(screen.queryByRole('region', { name: 'Gợi ý khóa học' })).not.toBeInTheDocument()
})

test('primary null: báo chưa có khóa phù hợp kèm nút kiểm tra đầu vào', async () => {
  mockApi({ ...member, '/api/me/qa': () => json(200, qa), '/api/me/course-suggestions': () => json(200, { ...sugg, primary: null, path: [] }) })
  renderAt('/khoa-hoc')
  expect(await screen.findByText('Chưa có khóa phù hợp, hãy làm kiểm tra đầu vào')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Kiểm tra đầu vào' })).toHaveAttribute('href', '/kiem-tra-dau-vao')
})

test('khách có pps.qa: dùng POST công khai, không gọi /me', async () => {
  localStorage.setItem('pps.qa', JSON.stringify({ selfLevel: 'CO_BAN', goals: ['WORK'], targetBand: 7, weeklyHours: 'H3_5', skills: [] }))
  let posted: unknown
  const calls = mockApi({ ...pub, '/api/catalog/course-suggestions': (b) => ((posted = b), json(200, sugg)) })
  renderAt('/khoa-hoc')
  expect(await screen.findByText('Phù hợp nhất')).toBeInTheDocument()
  expect(posted).toMatchObject({ selfLevel: 'CO_BAN', targetBand: 7 })
  expect(calls.filter((c) => c.startsWith('/api/me') && !c.includes('refresh'))).toEqual([])
  expect(screen.queryByRole('button', { name: 'Đang học' })).not.toBeInTheDocument() // khách ẩn chip Đang học
  expect(screen.getByRole('link', { name: 'Đăng nhập' })).toHaveAttribute('href', '/dang-nhap?next=%2Fkhoa-hoc')
})

test('nhãn/nút theo trạng thái ghi danh và lọc 3 chip', async () => {
  mockApi({
    ...member,
    '/api/me/courses': () => json(200, [mineCourse('begin', 1), mineCourse('ae1', 2), mineCourse('ae2', 3, 'EXPIRED')]),
    '/api/me/courses/1/progress': () => json(200, { completedLessons: 10, totalLessons: 10, percent: 100 }),
    '/api/me/courses/2/progress': () => json(200, { completedLessons: 4, totalLessons: 10, percent: 40 }),
    '/api/me/courses/3/progress': () => json(200, { completedLessons: 1, totalLessons: 10, percent: 10 }),
    '/api/me/courses/2/roadmap': () => json(200, { levels: [], resume: { lessonId: 55 } }),
  })
  renderAt('/khoa-hoc')
  expect(await screen.findByText('Đã hoàn thành')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Ôn lại' })).toHaveAttribute('href', '/khoa-hoc/begin')
  expect(await screen.findByText('Đang học · 40%')).toBeInTheDocument()
  expect(await screen.findByRole('link', { name: 'Học tiếp' })).toHaveAttribute('href', '/bai-giang/55')
  expect(screen.getByRole('progressbar', { name: /Academic English 1/ })).toHaveAttribute('aria-valuenow', '40')
  expect(screen.getByText('Hết hạn')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Gia hạn' })).toHaveAttribute('href', '/khoa-hoc/ae2')
  expect(screen.getByText('Học thử miễn phí 3 bài')).toBeInTheDocument() // Pre IELTS
  expect(screen.getByRole('link', { name: 'Xem khóa học' })).toHaveAttribute('href', '/khoa-hoc/found')

  const user = userEvent.setup()
  await user.click(screen.getByRole('button', { name: 'Đang học' }))
  expect(screen.getByRole('heading', { name: 'Academic English 1' })).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Pre IELTS' })).not.toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Academic English 2' })).not.toBeInTheDocument() // hết hạn
  await user.click(screen.getByRole('button', { name: 'Học thử' }))
  expect(screen.getByRole('heading', { name: 'Pre IELTS' })).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Begin' })).not.toBeInTheDocument() // đã ghi danh
})

test('tìm kiếm không phân biệt dấu; không khớp thì hiện trạng thái rỗng', async () => {
  mockApi({ ...member, '/api/catalog/courses': () => json(200, [course('gt', 'Giao tiếp phản xạ', 0, 3)]) })
  renderAt('/khoa-hoc')
  const user = userEvent.setup()
  await screen.findByRole('heading', { name: 'Giao tiếp phản xạ' })
  await user.type(screen.getByRole('searchbox'), 'giao tiep')
  expect(screen.getByRole('heading', { name: 'Giao tiếp phản xạ' })).toBeInTheDocument()
  await user.clear(screen.getByRole('searchbox'))
  await user.type(screen.getByRole('searchbox'), 'xyz')
  expect(screen.getByText('Chưa có khóa nào khớp')).toBeInTheDocument()
})

test('học thử mở đúng bài đầu của topic học thử', async () => {
  mockApi({ ...member, '/api/catalog/courses': () => json(200, [catalog[1]]), '/api/catalog/courses/ae1': () => json(200, detail) })
  const router = renderAt('/khoa-hoc')
  await screen.findByRole('article')
  await userEvent.setup().click(cardTrialButtons()[0])
  await waitFor(() => expect(router.state.location.pathname).toBe('/bai-giang/99'))
})

test('khách bấm học thử: sang đăng nhập kèm next là bài học thử', async () => {
  mockApi(pub)
  const router = renderAt('/khoa-hoc')
  const user = userEvent.setup()
  await screen.findAllByRole('article')
  await user.click(cardTrialButtons()[1]) // thẻ Academic English 1
  await waitFor(() => expect(router.state.location.pathname).toBe('/dang-nhap'))
  expect(router.state.location.search).toBe(`?next=${encodeURIComponent('/bai-giang/99')}`)
})

test('khóa học thử trong "khóa của tôi": nhãn Học thử, không phải Đang học, bị loại khỏi lọc Đang học', async () => {
  mockApi({
    ...member,
    '/api/me/courses': () => json(200, [mineCourse('ae1', 2, 'TRIAL')]),
    '/api/me/courses/2/progress': () => json(200, { completedLessons: 1, totalLessons: 10, percent: 10 }),
    '/api/me/courses/2/roadmap': () => json(200, { levels: [], resume: { lessonId: 77 } }),
  })
  renderAt('/khoa-hoc')
  const card = (await screen.findByRole('heading', { name: 'Academic English 1' })).closest('article')!
  expect(within(card).getAllByText('Học thử').length).toBeGreaterThan(0)
  expect(within(card).queryByText(/Đang học/)).toBeNull()
  expect(await within(card).findByRole('link', { name: 'Học thử' })).toHaveAttribute('href', '/bai-giang/77')
  await userEvent.setup().click(screen.getByRole('button', { name: 'Đang học' }))
  expect(screen.queryByRole('heading', { name: 'Academic English 1' })).not.toBeInTheDocument()
})
