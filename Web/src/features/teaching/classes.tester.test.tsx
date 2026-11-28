import { screen, within } from '@testing-library/react'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'
import { cls } from './fixtures'

// Tester #62, D-176: cột Tiến độ TB và Theo dõi ở Lớp của tôi.
beforeEach(() => setAccessToken('t'))
afterEach(() => vi.unstubAllGlobals())

test('tiến độ 0% hiện 0%, không nhầm là chưa có dữ liệu; tiêu đề cột đủ; không có chữ AI', async () => {
  mockApi({ '/api/auth/refresh': () => json(200, auth), '/api/teaching/classes': () => json(200, [{ ...cls, avgLessonPercent: 0, laggingCount: 1 }]) })
  renderAt('/giang-day')
  expect(await screen.findByText('0%')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '1 học viên cần hỗ trợ' })).toBeInTheDocument()
  const heads = within(screen.getByRole('table')).getAllByRole('columnheader').map((h) => h.textContent)
  expect(heads).toEqual(expect.arrayContaining(['Tiến độ TB', 'Theo dõi']))
  expect(document.body.textContent).not.toMatch(/\bAI\b/)
})
