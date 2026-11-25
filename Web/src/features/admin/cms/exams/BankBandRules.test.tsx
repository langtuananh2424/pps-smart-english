import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { setAccessToken } from '../../../../lib/api'
import { makeQueryClient } from '../../../../lib/queryClient'
import { apiError, json, mockApi } from '../../../../testUtils'
import { examRoutes } from './routes'

const B = '/api/admin/cms'
const bank = { id: 3, topicId: 2, lessonId: null, title: 'Thi khóa', status: 'DRAFT', questionCount: 1, timeLimitSec: null, mode: 'EXAM', purpose: 'COURSE' }

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

function setup(putRes: (body: unknown) => Response, puts: unknown[]) {
  mockApi({
    [`${B}/banks/3`]: () => json(200, { bank, questions: [], groups: [] }),
    [`${B}/banks/3/band-rules`]: (body, init) => {
      if (init?.method === 'PUT') {
        puts.push(body)
        return putRes(body)
      }
      return json(200, [{ skill: 'READING', minCorrect: 5, band: 5.5 }])
    },
    [`${B}/courses`]: () => json(200, { data: [] }),
  })
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <RouterProvider router={createMemoryRouter(examRoutes, { initialEntries: ['/de-thi/3'] })} />
    </QueryClientProvider>,
  )
}

describe('bảng quy đổi band đề thi khóa', () => {
  test('thêm mốc rồi lưu thay toàn bộ bảng', async () => {
    const puts: unknown[] = []
    setup((b) => json(200, (b as { rules: unknown[] }).rules), puts)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Thêm mốc Nghe' }))
    await user.type(screen.getByLabelText('Nghe dòng 1 số câu đúng'), '8')
    await user.type(screen.getByLabelText('Nghe dòng 1 band'), '7')
    await user.click(screen.getByRole('button', { name: 'Lưu bảng quy đổi' }))
    await screen.findByText('Đã lưu bảng quy đổi.')
    expect(puts).toEqual([{ rules: [{ skill: 'LISTENING', minCorrect: 8, band: 7 }, { skill: 'READING', minCorrect: 5, band: 5.5 }] }])
  })

  test('lỗi 400 hiện đúng ở dòng tương ứng', async () => {
    const puts: unknown[] = []
    setup(() => apiError(400, 'Dữ liệu không hợp lệ', { 'rules[0].band': 'Band quá lớn' }), puts)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Lưu bảng quy đổi' }))
    expect(await screen.findByText('Band quá lớn')).toBeInTheDocument()
  })

  test('xóa hết dòng rồi lưu gửi mảng rỗng', async () => {
    const puts: unknown[] = []
    setup((b) => json(200, (b as { rules: unknown[] }).rules), puts)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Xóa dòng 1 Đọc' }))
    await user.click(screen.getByRole('button', { name: 'Lưu bảng quy đổi' }))
    await screen.findByText('Đã lưu bảng quy đổi.')
    expect(puts).toEqual([{ rules: [] }])
  })
})
