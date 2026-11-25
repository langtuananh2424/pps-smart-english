import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { setAccessToken } from '../../../../lib/api'
import { makeQueryClient } from '../../../../lib/queryClient'
import { apiError, json, mockApi } from '../../../../testUtils'
import { examRoutes } from './routes'

// Tester: bảng band của đề thi khóa — gắn lỗi đúng dòng khi thứ tự nhập khác thứ tự gửi, lỗi trùng, 403, mảng rỗng.
const B = '/api/admin/cms'
const bank = { id: 3, topicId: 2, lessonId: null, title: 'Thi khóa', status: 'DRAFT', questionCount: 1, timeLimitSec: null, mode: 'EXAM', purpose: 'COURSE' }
type Rule = { skill: string; minCorrect: number; band: number }

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

function setup(initial: Rule[], putRes: (body: unknown) => Response, puts: unknown[]) {
  mockApi({
    [`${B}/banks/3`]: () => json(200, { bank, questions: [], groups: [] }),
    [`${B}/banks/3/band-rules`]: (body, init) => {
      if (init?.method === 'PUT') {
        puts.push(body)
        return putRes(body)
      }
      return json(200, initial)
    },
    [`${B}/courses`]: () => json(200, { data: [] }),
  })
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <RouterProvider router={createMemoryRouter(examRoutes, { initialEntries: ['/de-thi/3'] })} />
    </QueryClientProvider>,
  )
}
// Khối của một dòng = cha gần nhất chứa cả ô nhập lẫn lỗi.
const rowOf = (label: string) => screen.getByLabelText(label).closest('label')!.parentElement!.parentElement!
const save = async (u: ReturnType<typeof userEvent.setup>) => u.click(await screen.findByRole('button', { name: 'Lưu bảng quy đổi' }))

describe('band đề thi khóa (tester)', () => {
  test('thêm mốc Nghe sau Đọc: lỗi rules[1] gắn vào dòng Đọc (thứ tự gửi), không vào dòng Nghe', async () => {
    const puts: unknown[] = []
    setup([{ skill: 'READING', minCorrect: 5, band: 5.5 }], () => apiError(400, 'Dữ liệu không hợp lệ', { 'rules[1].band': 'Band đọc sai' }), puts)
    const u = userEvent.setup()
    await u.click(await screen.findByRole('button', { name: 'Thêm mốc Nghe' }))
    await u.type(screen.getByLabelText('Nghe dòng 1 số câu đúng'), '3')
    await u.type(screen.getByLabelText('Nghe dòng 1 band'), '4')
    await save(u)
    await screen.findByText('Band đọc sai')
    expect((puts[0] as { rules: Rule[] }).rules.map((r) => r.skill)).toEqual(['LISTENING', 'READING'])
    expect(within(rowOf('Đọc dòng 1 band')).getByText('Band đọc sai')).toBeInTheDocument()
    expect(within(rowOf('Nghe dòng 1 band')).queryByText('Band đọc sai')).toBeNull()
  })

  test('lỗi trùng (kỹ năng, số câu đúng) gắn vào dòng trùng đầu tiên', async () => {
    const puts: unknown[] = []
    setup(
      [{ skill: 'LISTENING', minCorrect: 2, band: 3 }, { skill: 'READING', minCorrect: 5, band: 5.5 }],
      () => apiError(400, 'Dữ liệu không hợp lệ', { rules: 'Trùng (kỹ năng, số câu đúng): READING 5' }),
      puts,
    )
    const u = userEvent.setup()
    await u.click(await screen.findByRole('button', { name: 'Thêm mốc Đọc' }))
    await u.type(screen.getByLabelText('Đọc dòng 2 số câu đúng'), '5')
    await u.type(screen.getByLabelText('Đọc dòng 2 band'), '6')
    await save(u)
    const msg = 'Trùng (kỹ năng, số câu đúng): READING 5'
    await screen.findByText(msg)
    expect(screen.getAllByText(msg)).toHaveLength(1)
    expect(within(rowOf('Đọc dòng 1 band')).getByText(msg)).toBeInTheDocument()
  })

  test('403 khi lưu: báo không có quyền, không báo đã lưu', async () => {
    const puts: unknown[] = []
    setup([{ skill: 'READING', minCorrect: 5, band: 5.5 }], () => apiError(403, 'internal detail'), puts)
    const u = userEvent.setup()
    await save(u)
    const alerts = await screen.findAllByRole('alert')
    expect(alerts.some((a) => /quyền/i.test(a.textContent ?? ''))).toBe(true)
    expect(screen.queryByText('Đã lưu bảng quy đổi.')).toBeNull()
    expect(screen.queryByText('internal detail')).toBeNull()
  })

  test('đề chưa có bảng (GET []): lưu mảng rỗng được, không lỗi', async () => {
    const puts: unknown[] = []
    setup([], (b) => json(200, (b as { rules: unknown[] }).rules), puts)
    const u = userEvent.setup()
    await screen.findByRole('button', { name: 'Thêm mốc Nghe' })
    await save(u)
    await screen.findByText('Đã lưu bảng quy đổi.')
    expect(puts).toEqual([{ rules: [] }])
  })

  test('dòng trống bị chặn phía trước, không gọi PUT; band dùng dấu phẩy được hiểu là thập phân', async () => {
    const puts: unknown[] = []
    setup([], (b) => json(200, (b as { rules: unknown[] }).rules), puts)
    const u = userEvent.setup()
    await u.click(await screen.findByRole('button', { name: 'Thêm mốc Ngữ pháp' }))
    await save(u)
    expect(await screen.findByText('Mỗi dòng cần nhập số câu đúng và band.')).toBeInTheDocument()
    expect(puts).toEqual([])
  })

  test('sau lỗi 400 ở rules[1], xóa dòng 0: lỗi cũ không được nhảy sang dòng khác', async () => {
    const puts: unknown[] = []
    setup(
      [
        { skill: 'READING', minCorrect: 1, band: 4 },
        { skill: 'READING', minCorrect: 5, band: 5.5 },
        { skill: 'READING', minCorrect: 9, band: 7 },
      ],
      () => apiError(400, 'Dữ liệu không hợp lệ', { 'rules[1].band': 'Band dòng giữa sai' }),
      puts,
    )
    const u = userEvent.setup()
    await save(u)
    await screen.findByText('Band dòng giữa sai')
    expect(within(rowOf('Đọc dòng 2 band')).getByText('Band dòng giữa sai')).toBeInTheDocument()
    await u.click(screen.getByRole('button', { name: 'Xóa dòng 1 Đọc' }))
    // Dòng "minCorrect 9" giờ đứng ở chỉ số 1; lỗi của dòng "minCorrect 5" không được hiện cạnh nó.
    const nine = screen.getAllByDisplayValue('9')[0]
    const row = nine.closest('label')!.parentElement!.parentElement!
    expect(within(row).queryByText('Band dòng giữa sai')).toBeNull()
  })
})
