import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import type { RoadmapLesson, RoadmapTopic } from '../home/types'

const lesson = (id: number, completed = false): RoadmapLesson => ({
  id, title: `Bài ${id}`, type: 'VIDEO', durationSec: 600, order: id % 100, completed, watchedSec: 0, maxPositionSec: null, lastWatchedAt: null,
})
const topic = (id: number, title: string, extra: Partial<RoadmapTopic> = {}): RoadmapTopic => ({
  id, title, skill: 'WRITING', isFreeTrial: false, locked: false, completedLessons: 0, totalLessons: 0, xpEarned: 0, status: 'NOT_STARTED', lessons: [], banks: [], ...extra,
})
const roadmap = {
  levels: [{ id: 1, code: 'L1', title: 'Level 1', topics: [
    topic(11, 'Unit 1', { lessons: [lesson(101, true), lesson(102)], totalLessons: 2 }),
    topic(12, 'Unit 2', { locked: true, status: 'LOCKED', lockReason: 'SEQUENTIAL', lessons: [lesson(201), lesson(202)], totalLessons: 2 }),
  ] }],
  resume: null,
}
const gate = (extra = {}) => ({
  topicId: 12, locked: true, openedEarly: false, blockingTopicId: 11, blockingTitle: 'Unit 1',
  lectures: { completed: 1, total: 2, requiredPercent: 80, currentPercent: 50 },
  exercises: { completed: 3, total: 3 },
  chapterTest: { submitted: false },
  ...extra,
})
const myCourse = { courseId: 7, slug: 'ae1', title: 'AE1', targetType: 'IELTS', status: 'ACTIVE', expiresAt: null, lastActivityAt: null, completedLessons: 1, totalLessons: 4, answeredQuestions: 0, correctQuestions: 0 }
const api = (g: unknown, extra = {}) => ({
  '/api/auth/refresh': () => json(200, auth),
  '/api/me': () => json(200, { displayName: 'Lan' }),
  '/api/me/courses': () => json(200, [myCourse]),
  '/api/me/courses/7/roadmap': () => json(200, roadmap),
  '/api/me/courses/7/units/12/gate': () => (typeof g === 'function' ? g() : json(200, g)),
  ...extra,
})
const here = (r: ReturnType<typeof renderAt>) => r.state.location.pathname

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

test('gate khóa: đủ ba hàng, tick theo điều kiện, Học phần còn thiếu vào bài chưa xong của unit chặn', async () => {
  mockApi(api(gate()))
  renderAt('/bai-giang/khoa/7/unit/12')
  expect(await screen.findByRole('heading', { name: 'Điều kiện mở Unit' })).toBeInTheDocument()
  expect(screen.getByText('Hoàn thành Unit trước để mở.')).toBeInTheDocument()
  expect(screen.getByText('Xem bài giảng đạt 80% · Hiện tại 50/80').closest('li')).toHaveTextContent('(chưa đạt)')
  expect(screen.getByText('Làm xong bài tập').closest('li')).toHaveTextContent('(đã đạt)')
  expect(screen.getByText('Nộp kiểm tra chương').closest('li')).toHaveTextContent('(chưa đạt)')
  expect(screen.getByRole('link', { name: 'Học phần còn thiếu' })).toHaveAttribute('href', '/bai-giang/102')
})

test('gate khóa: hàng null bị ẩn', async () => {
  mockApi(api(gate({ exercises: null, chapterTest: null })))
  renderAt('/bai-giang/khoa/7/unit/12')
  expect(await screen.findByText(/Xem bài giảng đạt/)).toBeInTheDocument()
  expect(screen.queryByText('Làm xong bài tập')).not.toBeInTheDocument()
  expect(screen.queryByText('Nộp kiểm tra chương')).not.toBeInTheDocument()
})

test('gate khóa: Đóng quay lại trang trước', async () => {
  mockApi(api(gate()))
  const r = renderAt('/bai-giang/khoa/7')
  await screen.findByRole('link', { name: 'Xem điều kiện mở' })
  await act(() => r.navigate('/bai-giang/khoa/7/unit/12'))
  await userEvent.click(await screen.findByRole('button', { name: 'Đóng' }))
  await waitFor(() => expect(here(r)).toBe('/bai-giang/khoa/7'))
})

test('được mở sớm: màn Unit đã mở với chip và Vào Unit', async () => {
  mockApi(api(gate({ locked: false, openedEarly: true, blockingTopicId: null })))
  renderAt('/bai-giang/khoa/7/unit/12')
  expect(await screen.findByRole('heading', { name: 'Unit đã mở' })).toBeInTheDocument()
  expect(screen.getByText('Được mở sớm')).toBeInTheDocument()
  expect(screen.getByText('Giáo viên đã cho phép bạn học Unit này trước.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Vào Unit' })).toHaveAttribute('href', '/bai-giang/201')
})

test('unit đã mở thường, khóa tuần tự: chuyển thẳng vào unit', async () => {
  mockApi(api(gate({ locked: false, blockingTopicId: null }), { '/api/catalog/courses/ae1': () => json(200, { sequential: true, levels: [], products: [] }) }))
  const r = renderAt('/bai-giang/khoa/7/unit/12')
  await waitFor(() => expect(here(r)).toBe('/bai-giang/201'))
})

test('khóa không bật tuần tự: màn Lộ trình khóa học với Mở Unit', async () => {
  mockApi(api(gate({ locked: false, blockingTopicId: null }), { '/api/catalog/courses/ae1': () => json(200, { sequential: false, levels: [], products: [] }) }))
  renderAt('/bai-giang/khoa/7/unit/12')
  expect(await screen.findByRole('heading', { name: 'Lộ trình khóa học' })).toBeInTheDocument()
  expect(screen.getByText('Khóa này cho phép học các Unit theo thứ tự bạn chọn.')).toBeInTheDocument()
  const open = screen.getAllByRole('link', { name: 'Mở Unit' })
  expect(open.map((a) => a.getAttribute('href'))).toEqual(['/bai-giang/102', '/bai-giang/201'])
})

test('404 unit_not_found: báo không tìm thấy Unit', async () => {
  mockApi(api(() => json(404, { error: { code: 'unit_not_found', message: 'x' } })))
  renderAt('/bai-giang/khoa/7/unit/12')
  expect(await screen.findByRole('heading', { name: 'Không tìm thấy Unit' })).toBeInTheDocument()
})

test('danh sách bài giảng: topic khóa tuần tự dẫn tới màn điều kiện, topic khóa mua vẫn dẫn tới mua', async () => {
  const rm = { ...roadmap, levels: [{ ...roadmap.levels[0], topics: [roadmap.levels[0].topics[0], roadmap.levels[0].topics[1], topic(13, 'Unit 3', { locked: true, status: 'LOCKED', lockReason: 'PAYWALL' })] }] }
  mockApi(api(gate(), { '/api/me/courses/7/roadmap': () => json(200, rm) }))
  renderAt('/bai-giang/khoa/7')
  expect(await screen.findByRole('link', { name: 'Xem điều kiện mở' })).toHaveAttribute('href', '/bai-giang/khoa/7/unit/12')
  expect(screen.getByRole('link', { name: 'Mua khóa học' })).toHaveAttribute('href', '/thanh-toan?khoa=ae1')
})

describe('403 unit_locked khi mở bài', () => {
  const locked = (details: unknown) =>
    mockApi({
      '/api/auth/refresh': () => json(200, auth),
      '/api/me': () => json(200, { displayName: 'Lan' }),
      '/api/lessons/201': () => json(403, { error: { code: 'unit_locked', message: 'x', details } }),
      '/api/lessons/201/progress': () => json(403, { error: { code: 'unit_locked', message: 'x' } }),
    })

  test('có courseId và topicId trong details: nút Xem điều kiện mở', async () => {
    locked({ blockingTopicId: 11, courseId: 7, topicId: 12 })
    renderAt('/bai-giang/201')
    expect(await screen.findByRole('heading', { name: 'Unit chưa mở' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Xem điều kiện mở' })).toHaveAttribute('href', '/bai-giang/khoa/7/unit/12')
  })

  test('chỉ có blockingTopicId: vẫn báo khóa nhưng không có nút điều kiện', async () => {
    locked({ blockingTopicId: 11 })
    renderAt('/bai-giang/201')
    expect(await screen.findByRole('heading', { name: 'Unit chưa mở' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Xem điều kiện mở' })).not.toBeInTheDocument()
  })
})
