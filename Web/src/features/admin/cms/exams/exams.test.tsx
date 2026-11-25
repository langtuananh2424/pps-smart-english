import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { setAccessToken } from '../../../../lib/api'
import { makeQueryClient } from '../../../../lib/queryClient'
import { apiError, json, mockApi } from '../../../../testUtils'
import { examRoutes } from './routes'
import { publishBlockers, validateRules } from './rules'

const B = '/api/admin/cms'
const renderAt = (path: string) => {
  const router = createMemoryRouter(examRoutes, { initialEntries: [path] })
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}
const bank = (over: object = {}) => ({ id: 1, topicId: null, lessonId: null, title: 'Đầu vào A', status: 'DRAFT', questionCount: 2, timeLimitSec: 1800, mode: 'EXAM', purpose: 'PLACEMENT', ...over })
const q = (id: number, skill: string) => ({ id, type: 'MCQ', prompt: `Câu ${id}`, skill, sortOrder: id })
const method = (init?: RequestInit) => init?.method ?? 'GET'

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

describe('logic thuần', () => {
  test('bảng quy đổi: kiểm khoảng, bước band và trùng mốc', () => {
    const row = (skill: string, minPercent: string, band: string) => ({ skill, minPercent, band })
    expect(validateRules([row('READING', '50', '5.5'), row('READING', '70', '6')]).errors).toEqual([])
    expect(validateRules([row('READING', '101', '5')]).errors).toHaveLength(1)
    expect(validateRules([row('READING', '50', '5.3')]).errors).toHaveLength(1)
    expect(validateRules([row('READING', '50', '5'), row('READING', '50', '6')]).errors[0]).toMatch(/trùng/)
    expect(validateRules([row('LISTENING', '50', '5'), row('READING', '50', '6')]).errors).toEqual([])
  })

  test('điều kiện xuất bản: cần câu hỏi, đề đầu vào cần bảng quy đổi từng kỹ năng', () => {
    const rules = [{ skill: 'READING', minPercent: 0, band: 1 }]
    expect(publishBlockers(0, false, [], [])).toHaveLength(1)
    expect(publishBlockers(2, false, [q(1, 'LISTENING')], [])).toEqual([])
    expect(publishBlockers(2, true, [q(1, 'READING'), q(2, 'LISTENING')], rules)).toEqual(['Thiếu bảng quy đổi band cho kỹ năng LISTENING.'])
  })
})

describe('bảng quy đổi điểm', () => {
  const handlers = (puts: unknown[]) => ({
    [`${B}/placement/band-rules`]: (body: unknown, init?: RequestInit) => {
      if (method(init) === 'PUT') {
        puts.push(body)
        return json(200, (body as { rules: unknown[] }).rules)
      }
      return json(200, [{ skill: 'READING', minPercent: 50, band: 5.5 }])
    },
    [`${B}/placement/config`]: () => json(200, { maxRetakes: 2 }),
  })

  test('mốc trùng bị chặn, không gọi PUT', async () => {
    const puts: unknown[] = []
    mockApi(handlers(puts))
    renderAt('/de-thi/quy-doi')
    const user = userEvent.setup()
    await screen.findByText('Từ 50% đúng')
    await user.selectOptions(screen.getByLabelText('Kỹ năng'), 'READING')
    await user.type(screen.getByLabelText('Từ % câu đúng'), '50')
    await user.type(screen.getByLabelText('Band'), '6')
    await user.click(screen.getByRole('button', { name: 'Thêm mốc' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('trùng mốc READING 50%')
    expect(screen.getAllByText('Từ 50% đúng')).toHaveLength(1)
    expect(puts).toEqual([])
  })

  test('thêm mốc rồi lưu thay toàn bộ bảng', async () => {
    const puts: unknown[] = []
    mockApi(handlers(puts))
    renderAt('/de-thi/quy-doi')
    const user = userEvent.setup()
    await screen.findByText('Từ 50% đúng')
    await user.type(screen.getByLabelText('Từ % câu đúng'), '80')
    await user.type(screen.getByLabelText('Band'), '7.5')
    await user.click(screen.getByRole('button', { name: 'Thêm mốc' }))
    await user.click(screen.getByRole('button', { name: 'Lưu bảng quy đổi' }))
    await screen.findByText('Đã lưu bảng quy đổi.')
    expect(puts).toEqual([{ rules: [{ skill: 'READING', minPercent: 50, band: 5.5 }, { skill: 'LISTENING', minPercent: 80, band: 7.5 }] }])
  })
})

describe('xuất bản đề', () => {
  const setup = (opts: { rules: unknown[]; published?: unknown[]; publish?: () => Response }) => {
    const posts: string[] = []
    mockApi({
      [`${B}/banks/1`]: () => json(200, { bank: bank(), questions: [q(1, 'READING'), q(2, 'LISTENING')], groups: [] }),
      [`${B}/placement/band-rules`]: () => json(200, opts.rules),
      [`${B}/placement/banks`]: () => json(200, [bank(), ...(opts.published ?? [])]),
      [`${B}/placement/config`]: () => json(200, { maxRetakes: null }),
      [`${B}/placement/banks/1/publish`]: () => {
        posts.push('publish')
        return opts.publish ? opts.publish() : json(200, bank({ status: 'PUBLISHED' }))
      },
    })
    return posts
  }
  const both = [{ skill: 'READING', minPercent: 0, band: 1 }, { skill: 'LISTENING', minPercent: 0, band: 1 }]

  test('thiếu bảng quy đổi cho một kỹ năng thì nút Xuất bản bị khóa', async () => {
    const posts = setup({ rules: [both[0]] })
    renderAt('/de-thi/1')
    expect(await screen.findByText('Thiếu bảng quy đổi band cho kỹ năng LISTENING.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Xuất bản' })).toBeDisabled()
    expect(posts).toEqual([])
  })

  test('đề đầu vào khác đang xuất bản: phải xác nhận trước khi gọi xuất bản', async () => {
    const posts = setup({ rules: both, published: [bank({ id: 9, title: 'Đề cũ', status: 'PUBLISHED' })] })
    renderAt('/de-thi/1')
    const user = userEvent.setup()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Xuất bản' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: 'Xuất bản' }))
    expect(screen.getByRole('alertdialog')).toHaveTextContent('Đề cũ')
    expect(posts).toEqual([])
    await user.click(screen.getByRole('button', { name: 'Xác nhận xuất bản' }))
    await waitFor(() => expect(posts).toEqual(['publish']))
  })

  test('lỗi từ backend hiện thông điệp chi tiết', async () => {
    setup({ rules: both, publish: () => apiError(400, 'Dữ liệu không hợp lệ', { questions: 'Cần ít nhất 1 câu hỏi trước khi xuất bản' }) })
    renderAt('/de-thi/1')
    const user = userEvent.setup()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Xuất bản' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: 'Xuất bản' }))
    expect(await screen.findByText('Cần ít nhất 1 câu hỏi trước khi xuất bản')).toBeInTheDocument()
  })
})

describe('gắn đề vào khóa', () => {
  test('gắn đề thi cuối khóa gửi examBankId và cấu hình', async () => {
    const patches: unknown[] = []
    mockApi({
      [`${B}/banks/5`]: () => json(200, { bank: bank({ id: 5, topicId: 7, purpose: 'COURSE', title: 'Thi khóa' }), questions: [q(1, 'READING')], groups: [] }),
      [`${B}/courses?limit=100`]: () => json(200, { data: [{ id: 3, title: 'IELTS 6.0', examBankId: null, examUnlockPercent: 80, examRetakes: 1 }], nextCursor: null }),
      [`${B}/courses/3`]: (body, init) => {
        if (method(init) === 'PATCH') {
          patches.push(body)
          return json(200, {})
        }
        return json(200, {
          course: { id: 3, title: 'IELTS 6.0', examBankId: null, examUnlockPercent: 80, examRetakes: 1 },
          levels: [{ topics: [{ topic: { id: 7, title: 'Chương 1', chapterTestBankId: null, chapterTestRetakes: 0 } }] }],
        })
      },
    })
    renderAt('/de-thi/5')
    const user = userEvent.setup()
    await screen.findByRole('option', { name: 'IELTS 6.0' })
    await user.selectOptions(screen.getByLabelText('Khóa học'), '3')
    await user.click(await screen.findByRole('button', { name: 'Gắn làm đề thi cuối khóa' }))
    expect(patches).toEqual([{ examBankId: 5, examUnlockPercent: 80, examRetakes: 1 }])
  })
})
