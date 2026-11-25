import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../../lib/api'
import { auth, json, renderAt } from '../../../testUtils'

// Tester đợt 2 (#17/#63): PATCH cấu hình khóa sau lần lưu thứ nhất phải vẫn chỉ gửi trường đã đổi so với giá trị trên máy chủ.
let server = {
  id: 7, slug: 'ielts', title: 'IELTS cơ bản', description: null, targetType: null, status: 'DRAFT',
  sequential: true, lessonCompletePercent: 80, examUnlockPercent: 70, examRetakes: 2, deliveryMode: 'SELF_PACED',
}
const tree = () => ({ course: server, levels: [] })

type Call = { method: string; body: unknown }
function mock() {
  const calls: Call[] = []
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET'
    const body = init?.body ? JSON.parse(String(init.body)) : undefined
    if (url === '/api/auth/refresh') return json(200, auth)
    if (url === '/api/me/permissions') return json(200, { permissions: ['cms.content'] })
    if (url === '/api/admin/cms/courses/7' && method === 'PATCH') {
      calls.push({ method, body })
      server = { ...server, ...(body as object) }
      return json(200, server)
    }
    if (url === '/api/admin/cms/courses/7') return json(200, tree())
    return json(404)
  })
  return calls
}

beforeEach(() => {
  setAccessToken(null)
  server = { ...server, examRetakes: 2 }
})
afterEach(() => vi.unstubAllGlobals())

const set = async (label: string, v: string) => {
  const el = screen.getByLabelText(label)
  await userEvent.clear(el)
  await userEvent.type(el, v)
}
const save = () => userEvent.click(screen.getByRole('button', { name: 'Lưu cấu hình' }))
const RETAKES = 'Kiểm tra cuối khóa · Số lần thi lại'

describe('cấu hình khóa: lưu nhiều lần trên cùng màn', () => {
  test('đổi 2→3, lưu, rồi đổi 3→2, lưu: lần hai phải gửi examRetakes 2', async () => {
    const calls = mock()
    renderAt('/quan-tri/cms/khoa-hoc/7/cau-hinh')
    await screen.findByLabelText(RETAKES)
    await set(RETAKES, '3')
    await save()
    await waitFor(() => expect(calls).toHaveLength(1))
    expect(calls[0].body).toEqual({ examRetakes: 3 })
    await set(RETAKES, '2')
    await save()
    await waitFor(() => expect(calls).toHaveLength(2))
    // Lỗi: form so với defaultValues cũ (2) nên coi là "không đổi", gửi {} mà vẫn báo Đã lưu; máy chủ giữ 3.
    expect(calls[1].body).toEqual({ examRetakes: 2 })
    expect(server.examRetakes).toBe(2)
  })

  test('không đổi gì mà bấm Lưu thì không gửi PATCH rỗng', async () => {
    const calls = mock()
    renderAt('/quan-tri/cms/khoa-hoc/7/cau-hinh')
    await screen.findByLabelText(RETAKES)
    await save()
    await new Promise((r) => setTimeout(r, 30))
    expect(calls.filter((c) => JSON.stringify(c.body) === '{}')).toEqual([])
  })

  test('lưu bị 409 thì form giữ thay đổi, nút Lưu vẫn bật, thử lại gửi đúng trường', async () => {
    let fail = true
    const calls: Call[] = []
    vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      if (url === '/api/auth/refresh') return json(200, auth)
      if (url === '/api/me/permissions') return json(200, { permissions: ['cms.content'] })
      if (url === '/api/admin/cms/courses/7' && method === 'PATCH') {
        calls.push({ method, body: JSON.parse(String(init?.body)) })
        if (fail) {
          fail = false
          return json(409, { error: { code: 'conflict', message: 'Xung đột', details: {} } })
        }
        return json(200, server)
      }
      if (url === '/api/admin/cms/courses/7') return json(200, tree())
      return json(404)
    })
    renderAt('/quan-tri/cms/khoa-hoc/7/cau-hinh')
    await screen.findByLabelText(RETAKES)
    const btn = screen.getByRole('button', { name: 'Lưu cấu hình' })
    expect(btn).toBeDisabled()
    await set(RETAKES, '5')
    await save()
    expect(await screen.findByText('Xung đột')).toBeInTheDocument()
    expect(screen.getByLabelText(RETAKES)).toHaveValue('5')
    expect(btn).toBeEnabled()
    await save()
    await waitFor(() => expect(calls).toHaveLength(2))
    expect(calls[1].body).toEqual({ examRetakes: 5 })
    await screen.findByText('Đã lưu cấu hình.')
    await waitFor(() => expect(btn).toBeDisabled())
    await set(RETAKES, '6')
    expect(btn).toBeEnabled()
  })
})
