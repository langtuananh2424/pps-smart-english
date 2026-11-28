import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import { board, cls, detail } from './fixtures'

const base = { '/api/auth/refresh': () => json(200, auth), '/api/classes/3': () => json(200, detail), '/api/classes/3/progress': () => json(200, board) }

beforeEach(() => setAccessToken('t'))
afterEach(() => vi.unstubAllGlobals())

test('danh sách lớp: tên lớp, khóa, sĩ số, mở tiến độ', async () => {
  mockApi({ ...base, '/api/teaching/classes': () => json(200, [cls]) })
  renderAt('/giang-day')
  expect(await screen.findByText('Lớp A1')).toBeInTheDocument()
  expect(screen.getByText('IELTS Foundation')).toBeInTheDocument()
  expect(screen.getByText('12/30')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('link', { name: /Xem tiến độ lớp Lớp A1/ }))
  expect(await screen.findByRole('heading', { name: 'Tiến độ lớp' })).toBeInTheDocument()
})

test('danh sách lớp: tiến độ TB và theo dõi', async () => {
  mockApi({ ...base, '/api/teaching/classes': () => json(200, [cls, { ...cls, id: 4, name: 'Lớp B', avgLessonPercent: null, laggingCount: 0 }]) })
  renderAt('/giang-day')
  expect(await screen.findByText('62%')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '2 học viên cần hỗ trợ' })).toHaveAttribute('href', '/giang-day/lop/3?loc=tut-lai')
  expect(screen.getByText('—')).toBeInTheDocument()
  expect(screen.getByText('Ổn định')).toBeInTheDocument()
})

test('không có lớp: trạng thái trống', async () => {
  mockApi({ ...base, '/api/teaching/classes': () => json(200, []) })
  renderAt('/giang-day')
  expect(await screen.findByText('Bạn chưa được phân công lớp nào.')).toBeInTheDocument()
})

test('tiến độ: lọc tụt lại, nhãn lý do, tìm theo tên', async () => {
  mockApi(base)
  renderAt('/giang-day/lop/3')
  expect(await screen.findByRole('link', { name: 'Minh' })).toBeInTheDocument()
  expect(screen.getByText('Quá hạn nộp')).toBeInTheDocument()
  expect(screen.getByText('Điểm thấp')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Chỉ học viên tụt lại' }))
  expect(screen.queryByRole('link', { name: 'Minh' })).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Nga' })).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Tất cả học viên' }))
  await userEvent.type(screen.getByLabelText('Tên học viên'), 'min')
  expect(screen.queryByRole('link', { name: 'Nga' })).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Minh' })).toBeInTheDocument()
})

test('tiến độ: ?loc=tut-lai bật sẵn bộ lọc, đổi bộ lọc cập nhật URL', async () => {
  mockApi(base)
  const router = renderAt('/giang-day/lop/3?loc=tut-lai')
  expect(await screen.findByRole('link', { name: 'Nga' })).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Minh' })).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Chỉ học viên tụt lại' })).toHaveAttribute('aria-pressed', 'true')
  await userEvent.click(screen.getByRole('button', { name: 'Tất cả học viên' }))
  expect(await screen.findByRole('link', { name: 'Minh' })).toBeInTheDocument()
  expect(router.state.location.search).toBe('')
  await userEvent.click(screen.getByRole('button', { name: 'Chỉ học viên tụt lại' }))
  expect(router.state.location.search).toBe('?loc=tut-lai')
})

test('tiến độ: điểm TB null hiện dấu gạch, học viên ngoài lớp nhận lỗi không tìm thấy', async () => {
  mockApi({ ...base, '/api/classes/4/progress': () => json(404, { error: { code: 'class_not_found', message: 'Không tìm thấy lớp' } }) })
  renderAt('/giang-day/lop/4')
  expect(await screen.findByRole('alert')).toHaveTextContent('Không tìm thấy lớp')
})

test('tên học viên mở hồ sơ năng lực, có nút về tiến độ lớp', async () => {
  const profile = { answered: 0, accuracy: null, bySkill: [], byType: [], byGrammar: [], byTopic: [], weeklyTrend: [], improvements: [] }
  mockApi({ ...base, '/api/classes/3/students/21/learning-profile': () => json(200, profile) })
  renderAt('/giang-day/lop/3')
  await userEvent.click(await screen.findByRole('link', { name: 'Minh' }))
  expect(await screen.findByRole('heading', { name: 'Hồ sơ năng lực · Minh' })).toBeInTheDocument()
  expect(screen.getByText('Học viên chưa làm bài nào.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Về tiến độ lớp' })).toHaveAttribute('href', '/giang-day/lop/3')
})

test.each([[['class.teach'], true], [[], false]])('mục Giảng dạy ở điều hướng học viên theo quyền %j', async (permissions, shown) => {
  mockApi({ '/api/auth/refresh': () => json(200, auth), '/api/me/permissions': () => json(200, { permissions }), '/api/me': () => json(200, { displayName: 'Lan' }) })
  renderAt('/ho-so')
  const nav = await screen.findByRole('navigation', { name: 'Điều hướng chính' })
  if (shown) await waitFor(() => expect(nav.querySelector('a[href="/giang-day"]')).not.toBeNull())
  else expect(nav.querySelector('a[href="/giang-day"]')).toBeNull()
})
