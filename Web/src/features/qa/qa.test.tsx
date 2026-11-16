import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../../lib/api'
import { auth, json, mockApi, renderAt } from '../../testUtils'

const answers = { selfLevel: 'MAT_GOC', goals: ['IELTS_CERT', 'STUDY_ABROAD'], targetBand: 6.5, weeklyHours: 'H3_5', skills: ['SPEAKING'] }
const base = {
  '/api/catalog/courses': () => json(200, []),
  '/api/catalog/course-suggestions': () => json(200, { basis: 'NONE', currentBand: null, targetBand: 6.5, primary: null, path: [] }),
  '/api/me': () => json(200, { displayName: 'Lan' }),
}

beforeEach(() => {
  setAccessToken(null)
  localStorage.clear()
})
afterEach(() => vi.unstubAllGlobals())

const btn = (name: RegExp | string) => screen.getByRole('button', { name })

async function fillAll(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByText(/Câu 1 \/ 4/) // khách: chờ refresh phiên xong
  expect(btn('Tiếp tục')).toBeDisabled() // chưa chọn gì
  await user.click(btn(/^Mất gốc/))
  await user.click(btn('Tiếp tục'))
  await user.click(btn(/^Thi chứng chỉ IELTS/))
  await user.click(btn(/^Du học/))
  await user.click(btn(/^Công việc/)) // mục thứ 3 bị chặn
  expect(screen.getByRole('status')).toHaveTextContent('tối đa 2 mục')
  expect(btn(/^Công việc/)).toHaveAttribute('aria-pressed', 'false')
  await user.click(btn('Tiếp tục'))
  await user.click(btn('6.5'))
  await user.click(btn('Tiếp tục'))
  expect(btn('Xem gợi ý')).toBeDisabled() // chưa chọn thời gian
  await user.click(btn(/^3–5 giờ/))
  await user.click(btn(/^Nói/))
  await user.click(btn('Xem gợi ý'))
}

test('đăng nhập: đi đủ 4 bước rồi PUT đúng body và về /khoa-hoc', async () => {
  let put: unknown
  mockApi({ ...base, '/api/auth/refresh': () => json(200, auth), '/api/me/qa': (b) => (b ? ((put = b), json(204)) : json(204)), '/api/me/courses': () => json(200, []) })
  const router = renderAt('/goi-y-khoa')
  expect(await screen.findByText(/Câu 1 \/ 4/)).toBeInTheDocument()
  await fillAll(userEvent.setup())
  await waitFor(() => expect(router.state.location.pathname).toBe('/khoa-hoc'))
  expect(put).toEqual(answers)
})

test('khách: lưu localStorage thay vì gọi PUT', async () => {
  const calls = mockApi(base)
  const router = renderAt('/goi-y-khoa')
  await fillAll(userEvent.setup())
  await waitFor(() => expect(router.state.location.pathname).toBe('/khoa-hoc'))
  expect(JSON.parse(localStorage.getItem('pps.qa')!)).toEqual(answers)
  expect(calls).not.toContain('/api/me/qa')
})

test('làm lại: điền sẵn câu trả lời từ server', async () => {
  mockApi({ ...base, '/api/auth/refresh': () => json(200, auth), '/api/me/qa': () => json(200, { ...answers, selfLevel: 'CO_BAN', updatedAt: 'x' }) })
  renderAt('/goi-y-khoa')
  expect(await screen.findByRole('button', { name: /^Cơ bản/ })).toHaveAttribute('aria-pressed', 'true')
  expect(btn('Tiếp tục')).toBeEnabled()
})

test('khách làm lại: điền sẵn từ localStorage', async () => {
  localStorage.setItem('pps.qa', JSON.stringify(answers))
  mockApi(base)
  renderAt('/goi-y-khoa')
  expect(await screen.findByRole('button', { name: /^Mất gốc/ })).toHaveAttribute('aria-pressed', 'true')
})

async function register(user: ReturnType<typeof userEvent.setup>) {
  await user.type(await screen.findByLabelText('Tên hiển thị'), 'Lan')
  await user.type(screen.getByLabelText('Email'), 'lan@example.com')
  await user.type(screen.getByLabelText('Mật khẩu'), 'matkhau123')
  await user.type(screen.getByLabelText('Nhập lại mật khẩu'), 'matkhau123')
  await user.click(screen.getByRole('button', { name: 'Đăng ký' }))
}

test('sau đăng ký: server chưa có Q&A (204) thì PUT rồi xóa localStorage', async () => {
  localStorage.setItem('pps.qa', JSON.stringify(answers))
  let put: unknown
  mockApi({ '/api/auth/register': () => json(201, auth), '/api/me/qa': (b) => (b ? ((put = b), json(204)) : json(204)) })
  renderAt('/dang-ky')
  await register(userEvent.setup())
  await waitFor(() => expect(put).toEqual(answers))
  expect(localStorage.getItem('pps.qa')).toBeNull()
})

test('sau đăng ký: server đã có Q&A thì chỉ xóa localStorage, không ghi đè', async () => {
  localStorage.setItem('pps.qa', JSON.stringify(answers))
  let put = false
  mockApi({ '/api/auth/register': () => json(201, auth), '/api/me/qa': (b) => ((put ||= !!b), json(200, { ...answers, updatedAt: 'x' })) })
  renderAt('/dang-ky')
  await register(userEvent.setup())
  await waitFor(() => expect(localStorage.getItem('pps.qa')).toBeNull())
  expect(put).toBe(false)
})

test('lỗi khi đồng bộ không chặn đăng nhập và giữ localStorage', async () => {
  localStorage.setItem('pps.qa', JSON.stringify(answers))
  mockApi({ '/api/auth/register': () => json(201, auth), '/api/me/qa': () => json(500, { error: { code: 'X', message: 'lỗi' } }) })
  const router = renderAt('/dang-ky')
  await register(userEvent.setup())
  await waitFor(() => expect(router.state.location.pathname).toBe('/'))
  expect(localStorage.getItem('pps.qa')).not.toBeNull()
})
