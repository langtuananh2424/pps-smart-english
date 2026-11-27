import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { setAccessToken } from '../../../../lib/api'
import { makeQueryClient } from '../../../../lib/queryClient'
import { apiError, json, mockApi } from '../../../../testUtils'
import { bankRoutes } from './routes'
import { uploadImage } from './upload'

// TipTap có test riêng; ở đây thay bằng ô chữ để kiểm biểu mẫu.
vi.mock('./RichText', () => ({
  RichText: ({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) => (
    <textarea aria-label={label} defaultValue={value} onChange={(e) => onChange(e.target.value)} />
  ),
}))

const C = '/api/admin/cms'
const bank = { id: 5, topicId: 2, lessonId: null, title: 'Bộ A', isTrial: false, status: 'DRAFT', questionCount: 2, timeLimitSec: null, mode: 'PRACTICE', purpose: 'COURSE', playbackMode: 'FREE', playLimit: null }
const choice = (id: number, label: string, ok: boolean) => ({ id, label, content: `nd ${label}`, contentHtml: null, isCorrect: ok, explanation: null })
const q = (id: number, sortOrder: number, extra: object = {}) => ({
  id, bankId: 5, type: 'CONNECTIVE_MCQ', prompt: `Đề ${id}`, payload: null, answerKey: null, choices: [choice(1, 'A', true), choice(2, 'B', false)],
  explanation: null, sortOrder, grammarElementCodes: [], answerVersion: 1, regradeRequired: false, skill: null, instruction: null, media: [], groupId: null,
  promptHtml: null, transcriptExcerpt: null, ...extra,
})
const detail = (over: object = {}, questions = [q(11, 0, { regradeRequired: true }), q(12, 1)]) => ({ bank: { ...bank, ...over }, questions, groups: [] })

function renderAt(path: string) {
  const router = createMemoryRouter(bankRoutes.map((r) => ({ ...r, path: `/${r.path}` })), { initialEntries: [path] })
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())
vi.spyOn(window, 'confirm').mockReturnValue(true)

describe('nhóm dạng IELTS theo cờ khóa (D-161)', () => {
  const open = async (over: object) => {
    mockApi({ [`${C}/banks/5`]: () => json(200, detail(over)), [`${C}/grammar-elements`]: () => json(200, []) })
    renderAt('/ngan-hang/5')
    await userEvent.click(await screen.findByRole('button', { name: '+ Thêm câu' }))
    return screen.getByLabelText('Dạng câu')
  }
  test('bộ khóa IELTS có nhóm IELTS', async () => {
    const sel = await open({ ielts: true, skill: 'READING' })
    expect(within(sel).getByRole('group', { name: 'IELTS' })).toBeInTheDocument()
  })
  test('bộ thường ẩn nhóm IELTS', async () => {
    const sel = await open({ ielts: false })
    expect(within(sel).queryByRole('group', { name: 'IELTS' })).toBeNull()
    expect(within(sel).getByRole('group', { name: 'Luyện tập PPS' })).toBeInTheDocument()
  })
})

describe('màn soạn bộ câu hỏi', () => {
  test('hiện cảnh báo chấm lại, không hiện luật Nghe ép khi bộ không phải LOCKED', async () => {
    const calls = mockApi({ [`${C}/banks/5`]: () => json(200, detail()), [`${C}/grammar-elements`]: () => json(200, []) })
    renderAt('/ngan-hang/5')
    expect(await screen.findByText('Đã có học viên làm câu này. Đổi đáp án cần chấm lại.')).toBeInTheDocument()
    expect(screen.getByLabelText('Cần chấm lại')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Nhập hàng loạt' })).toHaveAttribute('href', '/quan-tri/cms/ngan-hang/5/nhap-hang-loat')
    expect(screen.queryByText('Luật Nghe ép')).not.toBeInTheDocument()
    expect(calls.some((c) => c.includes('forced-listening'))).toBe(false)
  })

  test('bộ LOCKED mở luật Nghe ép; lưu theo cả bài gửi passPctTotal và groupThresholds rỗng', async () => {
    let put: unknown
    mockApi({
      [`${C}/banks/5`]: () => json(200, detail({ playbackMode: 'LOCKED' })),
      [`${C}/grammar-elements`]: () => json(200, []),
      [`${C}/banks/5/forced-listening`]: (body) => {
        if (body) put = body
        return json(200, { ruleId: null, playLimit: 3, passPctTotal: 70, hintEveryN: 3, wrongThreshold: 3, groupThresholds: [], thresholdMode: 'TOTAL' })
      },
    })
    renderAt('/ngan-hang/5')
    const total = await screen.findByLabelText('Ngưỡng đạt cả bài (%)')
    await userEvent.clear(total)
    await userEvent.type(total, '80')
    await userEvent.click(screen.getByRole('button', { name: 'Lưu luật Nghe ép' }))
    await waitFor(() => expect(put).toEqual({ playLimit: 3, hintEveryN: 3, wrongThreshold: 3, passPctTotal: 80, groupThresholds: [] }))
  })

  test('thêm câu: lỗi từ backend hiện ở ô, sửa xong gửi đúng body và chọn đáp án đúng', async () => {
    const bodies: Record<string, unknown>[] = []
    mockApi({
      [`${C}/banks/5`]: () => json(200, detail()),
      [`${C}/grammar-elements`]: () => json(200, []),
      [`${C}/banks/5/questions`]: (body) => {
        bodies.push(body as Record<string, unknown>)
        return bodies.length === 1 ? apiError(400, 'Dữ liệu không hợp lệ', { choices: 'Phải có đúng 1 lựa chọn đúng' }) : json(201, q(13, 2))
      },
    })
    renderAt('/ngan-hang/5')
    await userEvent.click(await screen.findByRole('button', { name: '+ Thêm câu' }))
    await userEvent.type(screen.getByLabelText('Đề bài'), 'She ___ tired')
    await userEvent.type(screen.getByLabelText('Nội dung lựa chọn 1'), 'is')
    await userEvent.type(screen.getByLabelText('Nội dung lựa chọn 2'), 'are')
    // Xem trước cập nhật theo biểu mẫu.
    expect(within(screen.getByLabelText('Xem trước')).getByText('She ___ tired')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Lưu câu hỏi' }))
    expect(await screen.findByText('Phải có đúng 1 lựa chọn đúng')).toBeInTheDocument()

    await userEvent.click(screen.getAllByRole('radio', { name: 'Đúng' })[1])
    await userEvent.click(screen.getByRole('button', { name: 'Lưu câu hỏi' }))
    await waitFor(() => expect(bodies).toHaveLength(2))
    expect(bodies[1]).toMatchObject({
      type: 'CONNECTIVE_MCQ',
      promptHtml: 'She ___ tired',
      sortOrder: 2,
      payload: null,
      answerKey: null,
      groupId: null,
      choices: [
        { label: 'A', contentHtml: 'is', isCorrect: false },
        { label: 'B', contentHtml: 'are', isCorrect: true },
      ],
    })
    expect(bodies[1]).not.toHaveProperty('skill')
  })

  test('đổi sang dạng điền từ: chỗ trống sinh ô đáp án và payload đúng dạng backend', async () => {
    let body: Record<string, unknown> = {}
    mockApi({
      [`${C}/banks/5`]: () => json(200, detail()),
      [`${C}/grammar-elements`]: () => json(200, []),
      [`${C}/banks/5/questions`]: (b) => {
        body = b as Record<string, unknown>
        return json(201, q(13, 2))
      },
    })
    renderAt('/ngan-hang/5')
    await userEvent.click(await screen.findByRole('button', { name: '+ Thêm câu' }))
    await userEvent.selectOptions(screen.getByLabelText('Dạng câu'), 'CONNECTIVE_CLOZE')
    await userEvent.type(screen.getByLabelText('Đề bài'), 'Điền từ')
    await userEvent.type(screen.getByLabelText('Văn bản'), 'He stayed {{1} it rained')
    await userEvent.type(await screen.findByLabelText('Đáp án chấp nhận cho chỗ trống {1}'), 'because{enter}since')
    await userEvent.click(screen.getByRole('button', { name: 'Lưu câu hỏi' }))
    await waitFor(() => expect(body.type).toBe('CONNECTIVE_CLOZE'))
    expect(body).toMatchObject({
      payload: { text: 'He stayed {1} it rained', blanks: ['1'] },
      answerKey: { blanks: { '1': ['because', 'since'] } },
      choices: [],
    })
  })

  test('ảnh thiếu mô tả: chặn lưu, không gọi API ghi', async () => {
    const withImg = q(11, 0, { media: [{ assetId: 9, url: 'http://img/9', width: 10, height: 10, role: 'INLINE', displaySize: 'MD', altText: '' }] })
    const calls = mockApi({ [`${C}/banks/5`]: () => json(200, detail({}, [withImg])), [`${C}/grammar-elements`]: () => json(200, []) })
    renderAt('/ngan-hang/5')
    await userEvent.click(await screen.findByRole('button', { name: 'Lưu câu hỏi' }))
    expect(await screen.findByText('Cần mô tả (alt) cho mọi ảnh')).toBeInTheDocument()
    expect(calls.some((c) => c.includes('/questions/11'))).toBe(false)
  })

  test('ngưỡng đạt: gửi passPct, lỗi 400 hiện dưới ô', async () => {
    const bodies: unknown[] = []
    mockApi({
      [`${C}/banks/5`]: (body) => {
        if (!body) return json(200, detail({ passPct: 70 }))
        bodies.push(body)
        return bodies.length === 1 ? apiError(400, 'Dữ liệu không hợp lệ', { passPct: 'Ngưỡng đạt từ 1 đến 100' }) : json(200, { ...bank, passPct: 85 })
      },
      [`${C}/grammar-elements`]: () => json(200, []),
    })
    renderAt('/ngan-hang/5')
    await userEvent.click(await screen.findByRole('button', { name: 'Thuộc tính bộ' }))
    const input = screen.getByLabelText('Ngưỡng đạt (%)')
    expect(input).toHaveValue(70)
    await userEvent.clear(input)
    await userEvent.type(input, '85')
    await userEvent.click(screen.getByRole('button', { name: 'Lưu thuộc tính' }))
    expect(await screen.findByText('Ngưỡng đạt từ 1 đến 100')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Lưu thuộc tính' }))
    await waitFor(() => expect(bodies).toHaveLength(2))
    expect(bodies[1]).toMatchObject({ passPct: 85 })
  })

  test('đổi thứ tự câu: PATCH sortOrder của hai câu hoán đổi cho nhau', async () => {
    const patches: Record<string, unknown> = {}
    mockApi({
      [`${C}/banks/5`]: () => json(200, detail()),
      [`${C}/grammar-elements`]: () => json(200, []),
      [`${C}/questions/11`]: (b, init) => ((patches[`${init?.method} 11`] = b), json(200, q(11, 1))),
      [`${C}/questions/12`]: (b, init) => ((patches[`${init?.method} 12`] = b), json(200, q(12, 0))),
    })
    renderAt('/ngan-hang/5')
    await userEvent.click(await screen.findByRole('button', { name: 'Đưa câu 2 lên' }))
    await waitFor(() => expect(patches).toEqual({ 'PATCH 12': { sortOrder: 0 }, 'PATCH 11': { sortOrder: 1 } }))
  })
})

describe('danh sách bộ câu hỏi', () => {
  const tree = { course: { id: 1, title: 'IELTS' }, levels: [{ level: { id: 1, title: 'Level 1' }, topics: [{ topic: { id: 2, title: 'Topic X' }, lessons: [{ id: 7, title: 'Bài 1' }] }] }] }

  test('chọn khóa và topic rồi tạo bộ; bỏ trống ngưỡng đạt thì không gửi', async () => {
    let created: Record<string, unknown> | undefined
    mockApi({
      [`${C}/courses?limit=100`]: () => json(200, { data: [{ id: 1, title: 'IELTS' }], nextCursor: null }),
      [`${C}/courses/1`]: () => json(200, tree),
      [`${C}/topics/2/banks`]: (b) => {
        if (b) {
          created = b as Record<string, unknown>
          return json(201, bank)
        }
        return json(200, [bank])
      },
    })
    renderAt('/ngan-hang')
    await screen.findByRole('option', { name: 'IELTS' })
    await userEvent.selectOptions(screen.getByLabelText('Khóa học'), '1')
    await screen.findByRole('option', { name: 'Level 1 › Topic X' })
    await userEvent.selectOptions(screen.getByLabelText('Topic'), '2')
    expect(await screen.findByText('Bộ A')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Chỉnh sửa' })).toHaveAttribute('href', '/quan-tri/cms/ngan-hang/5')

    await userEvent.click(screen.getByRole('button', { name: 'Tạo bộ câu hỏi' }))
    expect(screen.getByLabelText('Ngưỡng đạt (%)')).toHaveValue(null)
    await userEvent.click(screen.getByRole('button', { name: 'Tạo bộ' }))
    expect(await screen.findByText('Vui lòng nhập tên bộ câu hỏi')).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Tên bộ câu hỏi'), 'Bộ mới')
    await userEvent.selectOptions(screen.getByLabelText('Bài giảng gắn kèm'), '7')
    await userEvent.click(screen.getByRole('button', { name: 'Tạo bộ' }))
    await waitFor(() => expect(created).toEqual({ title: 'Bộ mới', isTrial: false, mode: 'PRACTICE', playbackMode: 'FREE', lessonId: 7 }))
  })
})

describe('tải ảnh lên', () => {
  test('xin quyền, POST multipart với field file cuối cùng, rồi gọi complete', async () => {
    const seen: string[] = []
    let form: FormData | undefined
    vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
      seen.push(`${init?.method} ${url}`)
      if (url === '/api/admin/cms/media/images') return json(201, { assetId: 8, url: 'https://minio/up', fields: { key: 'tmp/1', policy: 'p' }, maxBytes: 1024 })
      if (url === 'https://minio/up') {
        form = init?.body as FormData
        return json(204)
      }
      return json(200, { id: 8, url: 'https://img/8', width: 4, height: 3 })
    })
    const img = await uploadImage(new File(['x'], 'a.png', { type: 'image/png' }))
    expect(img.id).toBe(8)
    expect(seen).toEqual(['POST /api/admin/cms/media/images', 'POST https://minio/up', 'POST /api/admin/cms/media/8/complete'])
    expect([...form!.keys()]).toEqual(['key', 'policy', 'file'])
  })

  test('tệp quá cỡ thì không gửi lên kho', async () => {
    const seen: string[] = []
    vi.stubGlobal('fetch', async (url: string) => {
      seen.push(url)
      return json(201, { assetId: 8, url: 'https://minio/up', fields: {}, maxBytes: 1 })
    })
    await expect(uploadImage(new File(['xx'], 'a.png', { type: 'image/png' }))).rejects.toThrow('vượt quá')
    expect(seen).toEqual(['/api/admin/cms/media/images'])
  })
})
