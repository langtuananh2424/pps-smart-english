import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { setAccessToken } from '../../../../lib/api'
import { makeQueryClient } from '../../../../lib/queryClient'
import { json } from '../../../../testUtils'
import { importRoutes } from './routes'

const BASE = '/api/admin/cms/banks/5'
const q = { type: 'CONNECTIVE_MCQ', prompt: 'x' }
const row = (line: number, ok: boolean, extra = {}) => ({
  sheet: 'Trắc nghiệm', line, type: 'CONNECTIVE_MCQ', prompt: `Câu ${line}`, valid: ok,
  errors: ok ? [] : [{ field: 'Đáp án đúng', message: 'Thiếu đáp án đúng.' }], duplicates: [], question: q, ...extra,
})
const preview = {
  source: 'PASTE', total: 3, valid: 2, invalid: 1, warnings: [],
  rows: [row(1, true, { duplicates: [{ scope: 'BANK', questionId: 9, bankId: 5, bankTitle: 'B', line: null }] }), row(4, false), row(7, true)],
}

// Giả backend: ghi lại lời gọi ghi (method + body) để test kiểm tra.
function stub(history: unknown[] = []) {
  const writes: { url: string; body: unknown }[] = []
  vi.stubGlobal('fetch', async (url: string, init: RequestInit = {}) => {
    const method = init.method ?? 'GET'
    if (method !== 'GET') writes.push({ url, body: init.body instanceof FormData ? init.body : init.body && JSON.parse(String(init.body)) })
    if (url === `${BASE}/imports` && method === 'GET') return json(200, history)
    if (url === `${BASE}/imports/preview`) return json(200, preview)
    if (url === `${BASE}/imports`) return json(201, { batchId: 3, imported: 1, skipped: [{ line: 4, errors: [{ field: null, message: 'Lỗi X' }] }] })
    if (url === '/api/admin/cms/imports/2/undo') return json(200, { batchId: 2, deleted: 4, archived: 1 })
    if (url === '/api/admin/cms/import/guide') return new Response('# Hướng dẫn AI')
    return json(404)
  })
  return writes
}

function mount() {
  const router = createMemoryRouter([{ path: '/quan-tri/cms', children: importRoutes }], { initialEntries: ['/quan-tri/cms/ngan-hang/5/nhap-hang-loat'] })
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

async function previewPasted() {
  await userEvent.click(await screen.findByRole('button', { name: 'Chèn ví dụ' }))
  await userEvent.click(screen.getByRole('button', { name: 'Kiểm tra dữ liệu' }))
  await screen.findByText('2 hợp lệ, 1 lỗi')
}

describe('nhập hàng loạt', () => {
  test('xem hướng dẫn từ backend', async () => {
    stub()
    mount()
    await userEvent.click(await screen.findByRole('button', { name: 'Xem hướng dẫn' }))
    expect(await screen.findByText('# Hướng dẫn AI')).toBeInTheDocument()
  })

  test('preview có lỗi và trùng: lọc dòng lỗi, ALL_OR_NOTHING bị chặn tới khi bỏ dòng lỗi', async () => {
    const writes = stub()
    mount()
    await previewPasted()
    expect(writes[0].body).toBeInstanceOf(FormData)
    expect((writes[0].body as FormData).get('text')).toContain('# Trắc nghiệm')
    expect(screen.getByText('Cảnh báo: Trùng câu #9 trong bộ này')).toBeInTheDocument()
    expect(screen.getByText('Đáp án đúng: Thiếu đáp án đúng.')).toBeInTheDocument()

    await userEvent.click(screen.getByLabelText('Chỉ dòng lỗi'))
    expect(screen.queryByText('Câu 1')).not.toBeInTheDocument()
    expect(screen.getByText('Câu 4')).toBeInTheDocument()

    const go = screen.getByRole('button', { name: 'Nhập 2 câu hợp lệ' })
    expect(go).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Bỏ dòng 4' }))
    expect(screen.getByText('2 hợp lệ, 0 lỗi')).toBeInTheDocument()
    expect(go).toBeEnabled()
  })

  test('SKIP_INVALID gửi chỉ dòng hợp lệ chưa bỏ rồi hiện kết quả và dòng bị bỏ', async () => {
    const writes = stub()
    mount()
    await previewPasted()
    await userEvent.click(screen.getByRole('button', { name: 'Bỏ dòng 7' }))
    await userEvent.click(screen.getByLabelText('Bỏ qua dòng lỗi'))
    await userEvent.click(screen.getByRole('button', { name: 'Nhập 1 câu hợp lệ' }))

    expect(await screen.findByText('Đã thêm 1 câu.')).toBeInTheDocument()
    expect(screen.getByText('Dòng 4: Lỗi X')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Mở bộ câu hỏi' })).toHaveAttribute('href', '/quan-tri/cms/ngan-hang/5')
    const confirm = writes.find((w) => w.url === `${BASE}/imports`)!
    expect(confirm.body).toEqual({ source: 'PASTE', mode: 'SKIP_INVALID', rows: [{ line: 1, question: q }] })
  })

  test('hoàn tác lô trong lịch sử báo số xóa và ẩn', async () => {
    stub([{ id: 2, bankId: 5, userId: 1, source: 'EXCEL', status: 'DONE', questionCount: 5, createdAt: '2026-11-20T01:00:00Z', undoneAt: null }])
    mount()
    await userEvent.click(await screen.findByRole('button', { name: 'Hoàn tác lần nhập 2' }))
    await waitFor(() => expect(screen.getByText(/xóa 4 câu, ẩn 1 câu/)).toBeInTheDocument())
  })
})
