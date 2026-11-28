import { act, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import { board, cls, detail } from './fixtures'

// Tester: biên của bộ lọc tụt lại đọc từ URL (?loc=tut-lai) ở trang tiến độ lớp.
const base = {
  '/api/auth/refresh': () => json(200, auth),
  '/api/teaching/classes': () => json(200, [cls]),
  '/api/classes/3': () => json(200, detail),
  '/api/classes/3/progress': () => json(200, board),
}
const allBtn = () => screen.getByRole('button', { name: 'Tất cả học viên' })
const lagBtn = () => screen.getByRole('button', { name: 'Chỉ học viên tụt lại' })

beforeEach(() => setAccessToken('t'))
afterEach(() => vi.unstubAllGlobals())

test.each(['?loc=abc', '?loc=', '?loc=TUT-LAI', '?loc=tut-lai%00', '?loc=%E0%A4%A'])('giá trị lạ/hỏng %s: không lọc, không lỗi', async (q) => {
  mockApi(base)
  renderAt('/giang-day/lop/3' + q)
  expect(await screen.findByRole('link', { name: 'Minh' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Nga' })).toBeInTheDocument()
  expect(allBtn()).toHaveAttribute('aria-pressed', 'true')
  expect(lagBtn()).toHaveAttribute('aria-pressed', 'false')
})

test('loc lặp lại: lấy giá trị đầu', async () => {
  mockApi(base)
  renderAt('/giang-day/lop/3?loc=tut-lai&loc=abc')
  expect(await screen.findByRole('link', { name: 'Nga' })).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Minh' })).not.toBeInTheDocument()
})

test('đổi bộ lọc giữ các tham số URL khác', async () => {
  mockApi(base)
  const router = renderAt('/giang-day/lop/3?tab=x&loc=tut-lai')
  expect(await screen.findByRole('link', { name: 'Nga' })).toBeInTheDocument()
  await userEvent.click(allBtn())
  expect(new URLSearchParams(router.state.location.search).get('tab')).toBe('x')
  await userEvent.click(lagBtn())
  const sp = new URLSearchParams(router.state.location.search)
  expect(sp.get('tab')).toBe('x')
  expect(sp.get('loc')).toBe('tut-lai')
})

test('back/forward: bộ lọc theo URL, đổi bộ lọc không thêm mục lịch sử', async () => {
  const calls = mockApi(base)
  const router = renderAt('/giang-day')
  expect(await screen.findByText('Lớp A1')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('link', { name: '2 học viên cần hỗ trợ' }))
  expect(await screen.findByRole('link', { name: 'Nga' })).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Minh' })).not.toBeInTheDocument()
  await userEvent.click(allBtn())
  await userEvent.click(lagBtn())
  await userEvent.click(allBtn())
  expect(router.state.location.search).toBe('')
  await act(() => router.navigate(-1))
  expect(router.state.location.pathname).toBe('/giang-day')
  expect(await screen.findByText('Lớp A1')).toBeInTheDocument()
  await act(() => router.navigate(1))
  expect(router.state.location.search).toBe('')
  expect(await screen.findByRole('link', { name: 'Minh' })).toBeInTheDocument()
  // Đổi bộ lọc không gọi lại API tiến độ (không vòng lặp render/refetch).
  expect(calls.filter((u) => u === '/api/classes/3/progress').length).toBeLessThanOrEqual(2)
})
