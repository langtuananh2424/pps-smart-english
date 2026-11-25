import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../../lib/api'
import { auth, json, mockApi, renderAt } from '../../../testUtils'

const base = (list: string[]) => ({ '/api/auth/refresh': () => json(200, auth), '/api/me/permissions': () => json(200, { permissions: list }) })
const err = (status: number, code: string, message: string, details?: unknown) => json(status, { error: { code, message, details } })

const course = {
  id: 7, slug: 'ielts', title: 'IELTS cơ bản', description: null, targetType: null, status: 'DRAFT',
  sequential: true, lessonCompletePercent: 80, examUnlockPercent: 70, examRetakes: 2, deliveryMode: 'SELF_PACED',
}
const lesson = { id: 30, topicId: 20, type: 'CONNECTIVE_VIDEO', title: 'Bài 1', durationSec: 120, sortOrder: 0, status: 'DRAFT', videoSource: 'YOUTUBE', videoRef: 'abc' }
const tree = {
  course,
  levels: [{
    level: { id: 10, courseId: 7, code: 'A1', title: 'Sơ cấp', sortOrder: 0, status: 'DRAFT' },
    topics: [{ topic: { id: 20, levelId: 10, title: 'Chào hỏi', sortOrder: 0, isFreeTrial: false, skill: 'LISTENING', status: 'DRAFT' }, lessons: [lesson, { ...lesson, id: 31, title: 'Bài 2', status: 'PUBLISHED' }] }],
  }],
}

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

describe('CMS khóa học', () => {
  test('không có quyền cms.content thì bị chặn, không gọi API', async () => {
    const calls = mockApi(base(['report.view']))
    renderAt('/quan-tri/cms')
    expect(await screen.findByRole('heading', { name: 'Không có quyền truy cập' })).toBeInTheDocument()
    expect(calls.some((c) => c.includes('/admin/cms'))).toBe(false)
  })

  test('danh sách khóa học hiện trạng thái và link tạo mới', async () => {
    mockApi({ ...base(['cms.content']), '/api/admin/cms/courses?limit=20': () => json(200, { data: [course], nextCursor: null }) })
    renderAt('/quan-tri/cms')
    expect(await screen.findByRole('link', { name: 'Mở nội dung IELTS cơ bản' })).toHaveAttribute('href', '/quan-tri/cms/khoa-hoc/7')
    expect(screen.getByText('Bản nháp')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Tạo khóa học' })).toHaveAttribute('href', '/quan-tri/cms/khoa-hoc/moi')
  })

  test('lỗi mạng khi tải danh sách có nút Thử lại', async () => {
    let n = 0
    mockApi({
      ...base(['cms.content']),
      '/api/admin/cms/courses?limit=20': () => {
        if (n++ < 3) throw new TypeError('network')
        return json(200, { data: [course], nextCursor: null })
      },
    })
    renderAt('/quan-tri/cms')
    await userEvent.click(await screen.findByRole("button", { name: "Thử lại" }, { timeout: 5000 }))
    expect(await screen.findByRole('link', { name: 'Mở nội dung IELTS cơ bản' })).toBeInTheDocument()
  })

  test('tạo khóa: kiểm tra slug phía trước, 409 hiện tại ô, thành công hiện bản nháp', async () => {
    let status = 409
    const bodies: unknown[] = []
    mockApi({
      ...base(['cms.content']),
      '/api/admin/cms/courses': (b) => {
        bodies.push(b)
        return status === 409 ? err(409, 'slug_taken', 'Slug đã tồn tại', { slug: 'Slug đã tồn tại' }) : json(201, course)
      },
    })
    renderAt('/quan-tri/cms/khoa-hoc/moi')
    await userEvent.type(await screen.findByLabelText('Tên khóa học *'), 'IELTS cơ bản')
    await userEvent.type(screen.getByLabelText('Slug *'), 'Sai Slug')
    await userEvent.click(screen.getByRole('button', { name: 'Tạo bản nháp' }))
    expect(await screen.findByText('Chỉ gồm chữ thường, số và dấu gạch ngang')).toBeInTheDocument()
    expect(bodies).toHaveLength(0)

    await userEvent.clear(screen.getByLabelText('Slug *'))
    await userEvent.type(screen.getByLabelText('Slug *'), 'ielts')
    await userEvent.click(screen.getByRole('button', { name: 'Tạo bản nháp' }))
    expect(await screen.findByText('Slug đã tồn tại')).toBeInTheDocument()

    status = 201
    await userEvent.click(screen.getByRole('button', { name: 'Tạo bản nháp' }))
    expect(await screen.findByRole('heading', { name: 'Đã tạo khóa học' })).toBeInTheDocument()
    expect(bodies.at(-1)).toEqual({ slug: 'ielts', title: 'IELTS cơ bản' })
    expect(screen.getByRole('link', { name: 'Soạn nội dung' })).toHaveAttribute('href', '/quan-tri/cms/khoa-hoc/7')
  })

  test('cây nội dung: thêm cấp độ, xuất bản bài, xóa cần xác nhận, 403 báo không có quyền', async () => {
    const posts: Record<string, unknown> = {}
    let deleteStatus = 204
    mockApi({
      ...base(['cms.content']),
      '/api/admin/cms/courses/7': () => json(200, tree),
      '/api/admin/cms/courses/7/levels': (b) => ((posts.level = b), json(201, {})),
      '/api/admin/cms/lessons/30/publish': () => ((posts.published = true), json(200, {})),
      '/api/admin/cms/lessons/30': () => (deleteStatus === 204 ? json(204) : err(403, 'forbidden', 'x')),
    })
    renderAt('/quan-tri/cms/khoa-hoc/7')
    expect(await screen.findByText('A1 · Sơ cấp')).toBeInTheDocument()
    expect(screen.getByText('Bài 1')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Thêm cấp độ' }))
    await userEvent.type(screen.getByLabelText('Mã cấp độ'), 'A2')
    await userEvent.type(screen.getByLabelText('Tên cấp độ'), 'Cơ bản')
    await userEvent.click(screen.getAllByRole('button', { name: 'Thêm cấp độ' }).at(-1)!)
    await waitFor(() => expect(posts.level).toEqual({ code: 'A2', title: 'Cơ bản' }))

    const row = screen.getByText('Bài 1').closest('div')!.parentElement!
    await userEvent.click(row.querySelector('button')!)
    await waitFor(() => expect(posts.published).toBe(true))

    deleteStatus = 403
    await userEvent.click(row.querySelectorAll('button')[1])
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận xóa' }))
    expect(await screen.findByText('Bạn không có quyền thực hiện thao tác này.')).toBeInTheDocument()
  })

  test('chỉnh sửa bài giảng: PATCH một phần theo thẻ, xuất bản chuyển sang màn kết quả', async () => {
    const bodies: unknown[] = []
    let published = false
    mockApi({
      ...base(['cms.content']),
      '/api/admin/cms/courses/7': () => json(200, tree),
      '/api/admin/cms/lessons/30': (b) => (bodies.push(b), json(200, lesson)),
      '/api/admin/cms/lessons/30/publish': () => ((published = true), json(200, {})),
    })
    const router = renderAt('/quan-tri/cms/khoa-hoc/7/bai-giang/30')
    expect(await screen.findByText('IELTS cơ bản / Sơ cấp / Chào hỏi')).toBeInTheDocument()
    const title = screen.getByLabelText('Tiêu đề')
    await userEvent.clear(title)
    await userEvent.type(title, 'Bài một')
    await userEvent.click(screen.getByRole('button', { name: 'Lưu bài giảng' }))
    await waitFor(() => expect(bodies[0]).toEqual({ title: 'Bài một', type: 'CONNECTIVE_VIDEO', durationSec: 120 }))
    await userEvent.click(screen.getByRole('button', { name: 'Lưu video' }))
    await waitFor(() => expect(bodies[1]).toEqual({ videoRef: 'abc' }))

    await userEvent.click(screen.getByRole('button', { name: 'Xuất bản bài giảng' }))
    expect(await screen.findByRole('heading', { name: 'Đã xuất bản bài giảng' })).toBeInTheDocument()
    expect(published).toBe(true)
    expect(router.state.location.pathname).toBe('/quan-tri/cms/khoa-hoc/7/bai-giang/30/da-xuat-ban')
    expect(screen.getByRole('link', { name: 'Về nội dung khóa' })).toHaveAttribute('href', '/quan-tri/cms/khoa-hoc/7')
  })

  test('cấu hình học tuần tự và bài thi: kiểm tra phía trước, PATCH đúng body, 400 hiện thông báo', async () => {
    const bodies: unknown[] = []
    let fail = true
    mockApi({
      ...base(['cms.content']),
      '/api/admin/cms/courses/7': (b, init) => (init?.method === 'PATCH' ? ((bodies.push(b)), fail ? err(400, 'validation', 'Dữ liệu không hợp lệ') : json(200, course)) : json(200, tree)),
    })
    renderAt('/quan-tri/cms/khoa-hoc/7/cau-hinh')
    const pct = await screen.findByLabelText('Ngưỡng mở kiểm tra cuối khóa')
    await userEvent.clear(pct)
    await userEvent.type(pct, '150')
    await userEvent.click(screen.getByRole('button', { name: 'Lưu cấu hình' }))
    expect(await screen.findByText('Ngưỡng mở kiểm tra cuối khóa từ 0 đến 100')).toBeInTheDocument()
    expect(bodies).toHaveLength(0)

    await userEvent.clear(pct)
    await userEvent.type(pct, '60')
    await userEvent.click(screen.getByRole('button', { name: 'Lưu cấu hình' }))
    expect(await screen.findByText('Dữ liệu không hợp lệ')).toBeInTheDocument()

    fail = false
    await userEvent.click(screen.getByRole('button', { name: 'Lưu cấu hình' }))
    expect(await screen.findByText('Đã lưu cấu hình.')).toBeInTheDocument()
    expect(bodies.at(-1)).toEqual({ examUnlockPercent: 60 })
  })
})
