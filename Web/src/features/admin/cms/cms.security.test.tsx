import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../../lib/api'
import { auth, json, renderAt } from '../../../testUtils'

// Tester #17 / #63: route guard từng màn CMS, luồng lỗi, XSS, double-submit, PATCH cấu hình.
const err = (status: number, code: string, message: string, details?: unknown) => json(status, { error: { code, message, details } })
const NET = 'Không kết nối được máy chủ, vui lòng thử lại.'
const NO_PERM = 'Bạn không có quyền thực hiện thao tác này.'

const course = {
  id: 7, slug: 'ielts', title: 'IELTS cơ bản', description: null, targetType: null, status: 'DRAFT',
  sequential: true, lessonCompletePercent: 80, examUnlockPercent: 70, examRetakes: 2, deliveryMode: 'CLASS_BASED',
}
const lesson = { id: 30, topicId: 20, type: 'CONNECTIVE_VIDEO', title: 'Bài 1', durationSec: 120, sortOrder: 0, status: 'DRAFT', videoSource: 'YOUTUBE', videoRef: 'dQw4w9WgXcQ' }
const topic = { id: 20, levelId: 10, title: 'Chào hỏi', sortOrder: 0, isFreeTrial: false, skill: 'LISTENING', status: 'DRAFT' }
const level = { id: 10, courseId: 7, code: 'A1', title: 'Sơ cấp', sortOrder: 0, status: 'PUBLISHED' }
const tree = (over: Partial<typeof lesson> = {}) => ({
  course,
  levels: [{
    level,
    topics: [
      { topic, lessons: [{ ...lesson, ...over }] },
      { topic: { ...topic, id: 21, title: 'Gia đình', status: 'PUBLISHED' }, lessons: [] },
    ],
  }],
})

type Call = { url: string; method: string; body: unknown }
type Handler = (body: unknown, method: string) => Response | Promise<Response>

// Giả backend: đã đăng nhập, có/không quyền; ghi lại mọi lời gọi /api/admin/cms kèm method + body.
function mock(perms: string[], handlers: Record<string, Handler> = {}) {
  const calls: Call[] = []
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET'
    const body = init?.body ? JSON.parse(String(init.body)) : undefined
    if (url === '/api/auth/refresh') return json(200, auth)
    if (url === '/api/me/permissions') return json(200, { permissions: perms })
    calls.push({ url, method, body })
    const h = handlers[`${method} ${url}`] ?? handlers[url]
    return h ? h(body, method) : json(404)
  })
  return calls
}
const writes = (calls: Call[]) => calls.filter((c) => c.method !== 'GET')

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

describe('route guard cms.content: gõ thẳng URL mọi màn CMS', () => {
  const PATHS = [
    '/quan-tri/cms',
    '/quan-tri/cms/khoa-hoc/moi',
    '/quan-tri/cms/khoa-hoc/7',
    '/quan-tri/cms/khoa-hoc/7/cau-hinh',
    '/quan-tri/cms/khoa-hoc/7/bai-giang/30',
    '/quan-tri/cms/khoa-hoc/7/bai-giang/30/da-xuat-ban',
  ]
  test.each(PATHS)('thiếu quyền → %s hiện Không có quyền, không gọi API CMS', async (path) => {
    const calls = mock(['rbac.manage', 'audit.view', 'report.view'])
    renderAt(path)
    expect(await screen.findByRole('heading', { name: 'Không có quyền truy cập' })).toBeInTheDocument()
    expect(calls.filter((c) => c.url.startsWith('/api/admin/cms'))).toEqual([])
  })

  test('có cms.content → vào được màn cấu hình', async () => {
    mock(['cms.content'], { '/api/admin/cms/courses/7': () => json(200, tree()) })
    renderAt('/quan-tri/cms/khoa-hoc/7/cau-hinh')
    expect(await screen.findByRole('heading', { name: 'Cấu hình học và kiểm tra' })).toBeInTheDocument()
  })

  test('GET cây trả 403 (quyền bị thu hồi giữa chừng) → báo không có quyền, không lộ nội dung', async () => {
    mock(['cms.content'], { '/api/admin/cms/courses/7': () => err(403, 'forbidden', 'secret internal') })
    renderAt('/quan-tri/cms/khoa-hoc/7')
    expect(await screen.findByText('Bạn không có quyền xem nội dung này.')).toBeInTheDocument()
    expect(screen.queryByText('secret internal')).toBeNull()
  })
})

describe('XSS và link nguy hiểm', () => {
  const XSS = '<img src=x onerror="window.__pwned=1">'
  test('tiêu đề/mô tả/slug chứa HTML được hiện dạng chữ, không tạo phần tử', async () => {
    const evil = { ...course, title: `T${XSS}`, description: `<script>window.__pwned=1</script>` }
    mock(['cms.content'], { '/api/admin/cms/courses?limit=20': () => json(200, { data: [evil], nextCursor: null }) })
    renderAt('/quan-tri/cms')
    expect(await screen.findByText(`T${XSS}`)).toBeInTheDocument()
    expect(screen.getByText('<script>window.__pwned=1</script>')).toBeInTheDocument()
    expect(document.querySelector('img[src="x"], main script')).toBeNull()
    expect((window as unknown as { __pwned?: number }).__pwned).toBeUndefined()
  })

  test('cây và màn sửa bài: tiêu đề HTML + videoRef javascript: không thành link/phần tử', async () => {
    const t = { course: { ...course, title: XSS }, levels: [{ level: { ...level, title: XSS }, topics: [{ topic: { ...topic, title: XSS }, lessons: [{ ...lesson, title: XSS, videoRef: 'javascript:alert(1)' }] }] }] }
    mock(['cms.content'], { '/api/admin/cms/courses/7': () => json(200, t) })
    renderAt('/quan-tri/cms/khoa-hoc/7')
    expect(await screen.findByRole('heading', { name: XSS })).toBeInTheDocument()
    expect(document.querySelector('img[src="x"]')).toBeNull()
    expect([...document.querySelectorAll('a')].filter((a) => /^\s*javascript:/i.test(a.getAttribute('href') ?? ''))).toEqual([])
  })

  test('màn sửa bài: videoRef javascript: chỉ nằm trong ô nhập, không thành link/iframe', async () => {
    const t = { course, levels: [{ level, topics: [{ topic, lessons: [{ ...lesson, title: XSS, videoRef: 'javascript:alert(1)' }] }] }] }
    mock(['cms.content'], { '/api/admin/cms/courses/7': () => json(200, t) })
    renderAt('/quan-tri/cms/khoa-hoc/7/bai-giang/30')
    expect(await screen.findByDisplayValue('javascript:alert(1)')).toBeInTheDocument()
    expect(document.querySelector('img[src="x"], iframe')).toBeNull()
    expect([...document.querySelectorAll('a')].filter((a) => /^\s*javascript:/i.test(a.getAttribute('href') ?? ''))).toEqual([])
  })
})

describe('tạo khóa: biên và lỗi', () => {
  async function fill(title: string, slug: string, target = '') {
    await userEvent.type(await screen.findByLabelText('Tên khóa học *'), title)
    await userEvent.type(screen.getByLabelText('Slug *'), slug)
    if (target) await userEvent.type(screen.getByLabelText('Mục tiêu học (không bắt buộc)'), target)
  }

  test.each([
    ['-ielts', 'Chỉ gồm chữ thường, số và dấu gạch ngang'],
    ['ielts--a', 'Chỉ gồm chữ thường, số và dấu gạch ngang'],
    ['ielts_a', 'Chỉ gồm chữ thường, số và dấu gạch ngang'],
  ])('slug %s bị chặn phía trước', async (slug, msg) => {
    const calls = mock(['cms.content'])
    renderAt('/quan-tri/cms/khoa-hoc/moi')
    await fill('IELTS', slug)
    await userEvent.click(screen.getByRole('button', { name: 'Tạo bản nháp' }))
    expect(await screen.findByText(msg)).toBeInTheDocument()
    expect(writes(calls)).toEqual([])
  })

  test('targetType 31 ký tự bị chặn, 30 ký tự gửi được; tên chỉ có khoảng trắng bị chặn', async () => {
    const calls = mock(['cms.content'], { 'POST /api/admin/cms/courses': () => json(201, course) })
    renderAt('/quan-tri/cms/khoa-hoc/moi')
    await fill('   ', 'ielts', 'x'.repeat(31))
    await userEvent.click(screen.getByRole('button', { name: 'Tạo bản nháp' }))
    expect(await screen.findByText('Tối đa 30 ký tự')).toBeInTheDocument()
    expect(screen.getByText('Vui lòng nhập tên khóa học')).toBeInTheDocument()
    expect(writes(calls)).toEqual([])

    await userEvent.type(screen.getByLabelText('Tên khóa học *'), 'IELTS')
    await userEvent.type(screen.getByLabelText('Mục tiêu học (không bắt buộc)'), '{Backspace}')
    await userEvent.click(screen.getByRole('button', { name: 'Tạo bản nháp' }))
    await waitFor(() => expect(writes(calls)).toHaveLength(1))
    expect(writes(calls)[0].body).toEqual({ slug: 'ielts', title: 'IELTS', targetType: 'x'.repeat(30) })
  })

  test.each([
    [403, NO_PERM],
    [500, 'Lỗi máy chủ'],
  ])('lỗi %i khi tạo hiện thông báo dưới form, không chuyển màn', async (status, msg) => {
    mock(['cms.content'], { 'POST /api/admin/cms/courses': () => err(status, 'x', status === 403 ? 'chi tiết nội bộ' : 'Lỗi máy chủ') })
    renderAt('/quan-tri/cms/khoa-hoc/moi')
    await fill('IELTS', 'ielts')
    await userEvent.click(screen.getByRole('button', { name: 'Tạo bản nháp' }))
    expect(await screen.findByText(msg)).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Đã tạo khóa học' })).toBeNull()
  })

  test('lỗi mạng khi tạo hiện thông báo kết nối', async () => {
    mock(['cms.content'], { 'POST /api/admin/cms/courses': () => Promise.reject(new TypeError('network')) })
    renderAt('/quan-tri/cms/khoa-hoc/moi')
    await fill('IELTS', 'ielts')
    await userEvent.click(screen.getByRole('button', { name: 'Tạo bản nháp' }))
    expect(await screen.findByText(NET)).toBeInTheDocument()
  })

  test('bấm Tạo bản nháp hai lần liên tiếp chỉ gửi một POST', async () => {
    let release!: () => void
    const calls = mock(['cms.content'], { 'POST /api/admin/cms/courses': () => new Promise((r) => (release = () => r(json(201, course)))) })
    renderAt('/quan-tri/cms/khoa-hoc/moi')
    await fill('IELTS', 'ielts')
    const btn = screen.getByRole('button', { name: 'Tạo bản nháp' })
    fireEvent.click(btn)
    fireEvent.click(btn)
    await userEvent.click(btn)
    await waitFor(() => expect(writes(calls).length).toBeGreaterThan(0))
    release()
    expect(await screen.findByRole('heading', { name: 'Đã tạo khóa học' })).toBeInTheDocument()
    expect(writes(calls)).toHaveLength(1)
  })
})

describe('cây nội dung: 3 cấp', () => {
  test('publish/unpublish đúng endpoint cho cấp độ, chủ đề, bài giảng và khóa', async () => {
    const calls = mock(['cms.content'], {
      '/api/admin/cms/courses/7': () => json(200, tree()),
      'POST /api/admin/cms/levels/10/unpublish': () => json(200, {}),
      'POST /api/admin/cms/topics/20/publish': () => json(200, {}),
      'POST /api/admin/cms/topics/21/unpublish': () => json(200, {}),
      'POST /api/admin/cms/lessons/30/publish': () => json(200, {}),
      'POST /api/admin/cms/courses/7/publish': () => json(200, {}),
    })
    renderAt('/quan-tri/cms/khoa-hoc/7')
    const rowOf = (text: string) => screen.getByText(text).closest('div')!.parentElement!
    await screen.findByText('A1 · Sơ cấp')
    await userEvent.click(within(rowOf('A1 · Sơ cấp')).getByRole('button', { name: 'Hủy xuất bản' }))
    await userEvent.click(within(rowOf('Chào hỏi')).getByRole('button', { name: 'Xuất bản' }))
    await userEvent.click(within(rowOf('Gia đình')).getByRole('button', { name: 'Hủy xuất bản' }))
    await userEvent.click(within(rowOf('Bài 1')).getByRole('button', { name: 'Xuất bản' }))
    await userEvent.click(screen.getByRole('button', { name: 'Xuất bản khóa' }))
    await waitFor(() => expect(writes(calls)).toHaveLength(5))
    expect(writes(calls).map((c) => c.url)).toEqual([
      '/api/admin/cms/levels/10/unpublish',
      '/api/admin/cms/topics/20/publish',
      '/api/admin/cms/topics/21/unpublish',
      '/api/admin/cms/lessons/30/publish',
      '/api/admin/cms/courses/7/publish',
    ])
    // mỗi lệnh ghi xong làm mới cây
    expect(calls.filter((c) => c.url === '/api/admin/cms/courses/7' && c.method === 'GET').length).toBeGreaterThan(1)
  })

  test('sửa chủ đề gửi PATCH (bỏ chọn học thử → false), thêm bài giảng gửi POST, xóa cấp độ cần xác nhận', async () => {
    const calls = mock(['cms.content'], {
      '/api/admin/cms/courses/7': () => json(200, tree()),
      'PATCH /api/admin/cms/topics/20': () => json(200, topic),
      'POST /api/admin/cms/topics/20/lessons': () => json(201, lesson),
      'DELETE /api/admin/cms/levels/10': () => json(204),
    })
    renderAt('/quan-tri/cms/khoa-hoc/7')
    await screen.findByText('Chào hỏi')
    const topicRow = screen.getByText('Chào hỏi').closest('div')!.parentElement!
    await userEvent.click(within(topicRow).getByRole('button', { name: 'Sửa' }))
    const name = screen.getByLabelText('Tên chủ đề')
    await userEvent.clear(name)
    await userEvent.type(name, 'Chào hỏi 2')
    await userEvent.selectOptions(screen.getByLabelText('Kỹ năng'), 'READING')
    await userEvent.click(screen.getByRole('button', { name: 'Lưu chủ đề' }))
    await waitFor(() => expect(writes(calls)[0]).toEqual({ url: '/api/admin/cms/topics/20', method: 'PATCH', body: { title: 'Chào hỏi 2', skill: 'READING', isFreeTrial: false } }))

    await userEvent.click(screen.getAllByRole('button', { name: 'Thêm bài giảng' })[0])
    await userEvent.type(screen.getByLabelText('Tên bài giảng'), 'Bài mới')
    await userEvent.type(screen.getByLabelText('Video YouTube (mã hoặc URL)'), '  https://youtu.be/dQw4w9WgXcQ  ')
    await userEvent.click(within(screen.getByLabelText('Tên bài giảng').closest('form')!).getByRole('button', { name: 'Thêm bài giảng' }))
    await waitFor(() => expect(writes(calls)[1]?.body).toEqual({ title: 'Bài mới', type: 'CONNECTIVE_VIDEO', videoRef: 'https://youtu.be/dQw4w9WgXcQ' }))

    const levelRow = screen.getByText('A1 · Sơ cấp').closest('div')!.parentElement!
    await userEvent.click(within(levelRow).getByRole('button', { name: 'Xóa' }))
    expect(writes(calls)).toHaveLength(2)
    await userEvent.click(within(levelRow).getByRole('button', { name: 'Xác nhận xóa' }))
    await waitFor(() => expect(writes(calls)[2]).toEqual({ url: '/api/admin/cms/levels/10', method: 'DELETE', body: undefined }))
  })

  test('thêm cấp độ: 400 có details hiện tại ô, 409 hiện dưới form, form vẫn mở', async () => {
    let n = 0
    mock(['cms.content'], {
      '/api/admin/cms/courses/7': () => json(200, tree()),
      'POST /api/admin/cms/courses/7/levels': () =>
        n++ === 0 ? err(400, 'validation', 'Dữ liệu không hợp lệ', { code: 'Tối đa 20 ký tự' }) : err(409, 'conflict', 'Mã cấp độ đã tồn tại'),
    })
    renderAt('/quan-tri/cms/khoa-hoc/7')
    await userEvent.click(await screen.findByRole('button', { name: 'Thêm cấp độ' }))
    await userEvent.type(screen.getByLabelText('Mã cấp độ'), 'x'.repeat(21))
    await userEvent.type(screen.getByLabelText('Tên cấp độ'), 'Dài')
    await userEvent.click(screen.getAllByRole('button', { name: 'Thêm cấp độ' }).at(-1)!)
    expect(await screen.findByText('Tối đa 20 ký tự')).toBeInTheDocument()
    await userEvent.click(screen.getAllByRole('button', { name: 'Thêm cấp độ' }).at(-1)!)
    expect(await screen.findByText('Mã cấp độ đã tồn tại')).toBeInTheDocument()
    expect(screen.getByLabelText('Mã cấp độ')).toBeInTheDocument()
  })

  test('thiếu trường bắt buộc khi thêm chủ đề không gửi API', async () => {
    const calls = mock(['cms.content'], { '/api/admin/cms/courses/7': () => json(200, tree()) })
    renderAt('/quan-tri/cms/khoa-hoc/7')
    await userEvent.click(await screen.findByRole('button', { name: 'Thêm chủ đề' }))
    await userEvent.click(screen.getAllByRole('button', { name: 'Thêm chủ đề' }).at(-1)!)
    expect(await screen.findByText('Vui lòng nhập tên chủ đề')).toBeInTheDocument()
    expect(writes(calls)).toEqual([])
  })

  test('lỗi mạng khi xuất bản bài có nút Thử lại', async () => {
    mock(['cms.content'], {
      '/api/admin/cms/courses/7': () => json(200, tree()),
      'POST /api/admin/cms/lessons/30/publish': () => Promise.reject(new TypeError('network')),
    })
    renderAt('/quan-tri/cms/khoa-hoc/7')
    await screen.findByText('Bài 1')
    await userEvent.click(within(screen.getByText('Bài 1').closest('div')!.parentElement!).getByRole('button', { name: 'Xuất bản' }))
    expect(await screen.findByRole('button', { name: 'Thử lại' })).toBeInTheDocument()
  })

  test('bấm Xuất bản / Xác nhận xóa hai lần liên tiếp chỉ gửi một lệnh', async () => {
    const pending = () => new Promise<Response>(() => {})
    const calls = mock(['cms.content'], {
      '/api/admin/cms/courses/7': () => json(200, tree()),
      'POST /api/admin/cms/lessons/30/publish': pending,
      'DELETE /api/admin/cms/topics/21': pending,
      'POST /api/admin/cms/courses/7/publish': pending,
    })
    renderAt('/quan-tri/cms/khoa-hoc/7')
    await screen.findByText('Bài 1')
    const pub = within(screen.getByText('Bài 1').closest('div')!.parentElement!).getByRole('button', { name: 'Xuất bản' })
    // dblClick của user-event: hai click liền nhau như nhấp đúp thật
    await userEvent.dblClick(pub)
    await userEvent.dblClick(screen.getByRole('button', { name: 'Xuất bản khóa' }))
    const topicRow = screen.getByText('Gia đình').closest('div')!.parentElement!
    await userEvent.click(within(topicRow).getByRole('button', { name: 'Xóa' }))
    const del = within(topicRow).getByRole('button', { name: 'Xác nhận xóa' })
    await userEvent.dblClick(del)
    await waitFor(() => expect(writes(calls).length).toBeGreaterThanOrEqual(3))
    expect(writes(calls).map((c) => `${c.method} ${c.url}`).sort()).toEqual([
      'DELETE /api/admin/cms/topics/21',
      'POST /api/admin/cms/courses/7/publish',
      'POST /api/admin/cms/lessons/30/publish',
    ])
  })

  test('bấm nút gửi form thêm cấp độ hai lần liên tiếp chỉ gửi một POST', async () => {
    const calls = mock(['cms.content'], {
      '/api/admin/cms/courses/7': () => json(200, tree()),
      'POST /api/admin/cms/courses/7/levels': () => new Promise<Response>(() => {}),
    })
    renderAt('/quan-tri/cms/khoa-hoc/7')
    await userEvent.click(await screen.findByRole('button', { name: 'Thêm cấp độ' }))
    await userEvent.type(screen.getByLabelText('Mã cấp độ'), 'A2')
    await userEvent.type(screen.getByLabelText('Tên cấp độ'), 'Cơ bản')
    const btn = screen.getAllByRole('button', { name: 'Thêm cấp độ' }).at(-1)!
    fireEvent.click(btn)
    fireEvent.click(btn)
    await waitFor(() => expect(writes(calls).length).toBeGreaterThan(0))
    await new Promise((r) => setTimeout(r, 20))
    expect(writes(calls)).toHaveLength(1)
  })

  test('hai form "Thêm bài giảng" mở cùng lúc: nhãn mỗi form trỏ đúng ô của nó (id không trùng)', async () => {
    mock(['cms.content'], { '/api/admin/cms/courses/7': () => json(200, tree()) })
    renderAt('/quan-tri/cms/khoa-hoc/7')
    await screen.findByText('Gia đình')
    for (const b of screen.getAllByRole('button', { name: 'Thêm bài giảng' })) await userEvent.click(b)
    const inputs = screen.getAllByLabelText('Tên bài giảng')
    expect(inputs).toHaveLength(2)
    expect(inputs[0]).not.toBe(inputs[1])
    expect(new Set(inputs.map((i) => i.id)).size).toBe(2)
  })
})

describe('sửa bài giảng và màn đã xuất bản', () => {
  test('bài không thuộc khóa trong URL → không tìm thấy, không có nút ghi', async () => {
    const calls = mock(['cms.content'], { '/api/admin/cms/courses/7': () => json(200, tree()) })
    renderAt('/quan-tri/cms/khoa-hoc/7/bai-giang/999')
    expect(await screen.findByText('Không tìm thấy bài giảng.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Lưu|Xuất bản/ })).toBeNull()
    expect(writes(calls)).toEqual([])
  })

  test('xuất bản bị 400 (chưa có video) → hiện lỗi, không chuyển sang màn đã xuất bản', async () => {
    mock(['cms.content'], {
      '/api/admin/cms/courses/7': () => json(200, tree({ videoRef: null as never })),
      'POST /api/admin/cms/lessons/30/publish': () => err(400, 'validation', 'Cần có video trước khi xuất bản', { videoRef: 'Cần có video trước khi xuất bản' }),
    })
    const router = renderAt('/quan-tri/cms/khoa-hoc/7/bai-giang/30')
    await userEvent.click(await screen.findByRole('button', { name: 'Xuất bản bài giảng' }))
    expect(await screen.findByText('Cần có video trước khi xuất bản')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/quan-tri/cms/khoa-hoc/7/bai-giang/30')
  })

  test('hủy xuất bản bài đã xuất bản: gọi unpublish, không chuyển sang màn kết quả', async () => {
    const calls = mock(['cms.content'], {
      '/api/admin/cms/courses/7': () => json(200, tree({ status: 'PUBLISHED' })),
      'POST /api/admin/cms/lessons/30/unpublish': () => json(200, {}),
    })
    const router = renderAt('/quan-tri/cms/khoa-hoc/7/bai-giang/30')
    await userEvent.click(await screen.findByRole('button', { name: 'Hủy xuất bản bài giảng' }))
    await waitFor(() => expect(writes(calls).map((c) => c.url)).toEqual(['/api/admin/cms/lessons/30/unpublish']))
    expect(router.state.location.pathname).toBe('/quan-tri/cms/khoa-hoc/7/bai-giang/30')
  })

  test('videoRef sai định dạng: 400 của máy chủ hiện tại ô video', async () => {
    mock(['cms.content'], {
      '/api/admin/cms/courses/7': () => json(200, tree()),
      'PATCH /api/admin/cms/lessons/30': () => err(400, 'validation', 'Dữ liệu không hợp lệ', { videoRef: 'Phải là mã video YouTube 11 ký tự hoặc URL YouTube hợp lệ' }),
    })
    renderAt('/quan-tri/cms/khoa-hoc/7/bai-giang/30')
    const v = await screen.findByLabelText('Video (mã hoặc URL YouTube)')
    await userEvent.clear(v)
    await userEvent.type(v, 'javascript:alert(1)')
    await userEvent.click(screen.getByRole('button', { name: 'Lưu video' }))
    expect(await screen.findByText('Phải là mã video YouTube 11 ký tự hoặc URL YouTube hợp lệ')).toBeInTheDocument()
  })

  test('thời lượng không phải số không được gửi thành null âm thầm', async () => {
    const calls = mock(['cms.content'], {
      '/api/admin/cms/courses/7': () => json(200, tree()),
      'PATCH /api/admin/cms/lessons/30': () => json(200, lesson),
    })
    renderAt('/quan-tri/cms/khoa-hoc/7/bai-giang/30')
    const d = await screen.findByLabelText('Thời lượng (giây)')
    await userEvent.clear(d)
    await userEvent.type(d, 'abc')
    await userEvent.click(screen.getByRole('button', { name: 'Lưu bài giảng' }))
    await new Promise((r) => setTimeout(r, 50))
    // Hoặc chặn phía trước (không gửi), hoặc gửi số hợp lệ; không được gửi durationSec: null.
    expect(writes(calls).filter((c) => (c.body as { durationSec?: unknown })?.durationSec === null)).toEqual([])
  })

  test('gõ thẳng URL màn "đã xuất bản" của bài còn bản nháp không được báo đã xuất bản', async () => {
    mock(['cms.content'], { '/api/admin/cms/courses/7': () => json(200, tree()) })
    renderAt('/quan-tri/cms/khoa-hoc/7/bai-giang/30/da-xuat-ban')
    await waitFor(() => expect(screen.queryByText('Đang tải...')).toBeNull())
    await screen.findByText(/Bài 1|Không tìm thấy/)
    expect(screen.queryByRole('heading', { name: 'Đã xuất bản bài giảng' })).toBeNull()
  })
})

describe('cấu hình học tuần tự và kiểm tra', () => {
  const open = async (handlers: Record<string, Handler> = {}) => {
    const calls = mock(['cms.content'], { 'GET /api/admin/cms/courses/7': () => json(200, tree()), ...handlers })
    renderAt('/quan-tri/cms/khoa-hoc/7/cau-hinh')
    await screen.findByLabelText('Tỷ lệ xem bài giảng để mở Unit')
    return calls
  }
  const set = async (label: string, v: string) => {
    const el = screen.getByLabelText(label)
    await userEvent.clear(el)
    if (v) await userEvent.type(el, v)
  }
  const save = () => userEvent.click(screen.getByRole('button', { name: 'Lưu cấu hình' }))

  test.each([
    ['Tỷ lệ xem bài giảng để mở Unit', '0', 'Tỷ lệ xem bài giảng từ 1 đến 100'],
    ['Tỷ lệ xem bài giảng để mở Unit', '101', 'Tỷ lệ xem bài giảng từ 1 đến 100'],
    ['Ngưỡng mở kiểm tra cuối khóa', '-1', 'Ngưỡng mở kiểm tra cuối khóa phải là số nguyên'],
    ['Kiểm tra cuối khóa · Số lần thi lại', '1001', 'Số lần thi lại từ 0 đến 1000'],
    ['Kiểm tra cuối khóa · Số lần thi lại', '1.5', 'Số lần thi lại phải là số nguyên'],
    ['Kiểm tra cuối khóa · Số lần thi lại', '', 'Số lần thi lại phải là số nguyên'],
  ])('%s = "%s" bị chặn phía trước', async (label, v, msg) => {
    const calls = await open()
    await set(label, v)
    await save()
    expect(await screen.findByText(msg)).toBeInTheDocument()
    expect(writes(calls)).toEqual([])
  })

  test('biên hợp lệ 1 / 0 / 1000 gửi được', async () => {
    const calls = await open({ 'PATCH /api/admin/cms/courses/7': () => json(200, course) })
    await set('Tỷ lệ xem bài giảng để mở Unit', '1')
    await set('Ngưỡng mở kiểm tra cuối khóa', '0')
    await set('Kiểm tra cuối khóa · Số lần thi lại', '1000')
    await save()
    expect(await screen.findByText('Đã lưu cấu hình.')).toBeInTheDocument()
    expect(writes(calls)[0].body).toMatchObject({ lessonCompletePercent: 1, examUnlockPercent: 0, examRetakes: 1000 })
  })

  test('PATCH chỉ gửi trường đã đổi', async () => {
    const calls = await open({ 'PATCH /api/admin/cms/courses/7': () => json(200, course) })
    await set('Kiểm tra cuối khóa · Số lần thi lại', '3')
    await save()
    expect(await screen.findByText('Đã lưu cấu hình.')).toBeInTheDocument()
    expect(writes(calls)[0].body).toEqual({ examRetakes: 3 })
  })

  test('đổi sang Tự học khi khóa đã có lớp: 409 hiện thông báo, không báo đã lưu', async () => {
    const calls = await open({
      'PATCH /api/admin/cms/courses/7': () => err(409, 'course_has_classes', 'Khóa đã có lớp, không đổi về khóa tự học được'),
    })
    await userEvent.click(screen.getByRole('radio', { name: 'Tự học' }))
    expect(screen.getByRole('radio', { name: 'Tự học' })).toHaveAttribute('aria-checked', 'true')
    await save()
    expect(await screen.findByText('Khóa đã có lớp, không đổi về khóa tự học được')).toBeInTheDocument()
    expect(screen.queryByText('Đã lưu cấu hình.')).toBeNull()
    expect(writes(calls)[0].body).toMatchObject({ deliveryMode: 'SELF_PACED' })
  })

  test('tắt học tuần tự gửi sequential false', async () => {
    const calls = await open({ 'PATCH /api/admin/cms/courses/7': () => json(200, course) })
    await userEvent.click(screen.getByRole('switch'))
    await save()
    await waitFor(() => expect(writes(calls)).toHaveLength(1))
    expect(writes(calls)[0].body).toMatchObject({ sequential: false })
  })

  test.each([
    [403, NO_PERM],
    [400, 'Tỷ lệ phải từ 1 đến 100'],
  ])('lỗi %i khi lưu', async (status, msg) => {
    await open({
      'PATCH /api/admin/cms/courses/7': () =>
        status === 400 ? err(400, 'validation', 'Dữ liệu không hợp lệ', { lessonCompletePercent: msg }) : err(403, 'forbidden', 'internal'),
    })
    await set('Kiểm tra cuối khóa · Số lần thi lại', '7')
    await save()
    expect(await screen.findByText(msg)).toBeInTheDocument()
    expect(screen.queryByText('internal')).toBeNull()
  })

  test('401 khi lưu và refresh cũng hết hạn: không báo đã lưu, rời màn quản trị hoặc báo lỗi', async () => {
    let refreshed = 0
    vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
      if (url === '/api/auth/refresh') return refreshed++ === 0 ? json(200, auth) : err(401, 'expired', 'Phiên hết hạn')
      if (url === '/api/me/permissions') return json(200, { permissions: ['cms.content'] })
      if (init?.method === 'PATCH') return err(401, 'unauthorized', 'Unauthorized')
      return json(200, tree())
    })
    const router = renderAt('/quan-tri/cms/khoa-hoc/7/cau-hinh')
    await screen.findByLabelText('Tỷ lệ xem bài giảng để mở Unit')
    await set('Kiểm tra cuối khóa · Số lần thi lại', '7')
    await save()
    await waitFor(() => expect(router.state.location.pathname !== '/quan-tri/cms/khoa-hoc/7/cau-hinh' || !!screen.queryByText(NO_PERM)).toBe(true))
    expect(screen.queryByText('Đã lưu cấu hình.')).toBeNull()
  })

  test('lỗi mạng khi lưu', async () => {
    await open({ 'PATCH /api/admin/cms/courses/7': () => Promise.reject(new TypeError('network')) })
    await set('Kiểm tra cuối khóa · Số lần thi lại', '7')
    await save()
    expect(await screen.findByText(NET)).toBeInTheDocument()
  })

  test('bấm Lưu cấu hình hai lần liên tiếp chỉ gửi một PATCH', async () => {
    const calls = await open({ 'PATCH /api/admin/cms/courses/7': () => new Promise<Response>(() => {}) })
    await set('Kiểm tra cuối khóa · Số lần thi lại', '7')
    const btn = screen.getByRole('button', { name: 'Lưu cấu hình' })
    fireEvent.click(btn)
    fireEvent.click(btn)
    await waitFor(() => expect(writes(calls).length).toBeGreaterThan(0))
    await new Promise((r) => setTimeout(r, 20))
    expect(writes(calls)).toHaveLength(1)
  })
})
