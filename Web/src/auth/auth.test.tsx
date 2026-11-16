import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setAccessToken } from '../lib/api'
import { apiError, auth, json, mockApi, renderAt } from '../testUtils'

beforeEach(() => setAccessToken(null))
afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  delete window.google
})

async function fillRegister(user: ReturnType<typeof userEvent.setup>, confirm = 'matkhau123') {
  await user.type(await screen.findByLabelText('Tên hiển thị'), 'Lan')
  await user.type(screen.getByLabelText('Email'), 'lan@example.com')
  await user.type(screen.getByLabelText('Mật khẩu'), 'matkhau123')
  await user.type(screen.getByLabelText('Nhập lại mật khẩu'), confirm)
  await user.click(screen.getByRole('button', { name: 'Đăng ký' }))
}

test('đăng ký thành công vào trang chủ', async () => {
  let body: unknown
  mockApi({
    '/api/auth/register': (b) => ((body = b), json(201, auth)),
    '/api/me': () => json(200, { displayName: 'Lan', emailVerified: true }),
  })
  renderAt('/dang-ky')
  await fillRegister(userEvent.setup())
  expect(await screen.findByRole('heading', { name: 'Xin chào, Lan!' })).toBeInTheDocument()
  expect(body).toEqual({ email: 'lan@example.com', password: 'matkhau123', displayName: 'Lan' })
})

test('email trùng hiện message của backend', async () => {
  mockApi({ '/api/auth/register': () => apiError(409, 'Email đã được dùng') })
  renderAt('/dang-ky')
  await fillRegister(userEvent.setup())
  expect(await screen.findByRole('alert')).toHaveTextContent('Email đã được dùng')
})

test('lỗi field từ backend gắn vào ô tương ứng', async () => {
  mockApi({ '/api/auth/register': () => apiError(400, 'Dữ liệu không hợp lệ', { email: 'Email bị từ chối' }) })
  renderAt('/dang-ky')
  await fillRegister(userEvent.setup())
  expect(await screen.findByText('Email bị từ chối')).toHaveAttribute('id', 'email-err')
})

test('mật khẩu nhập lại không khớp thì không gọi API', async () => {
  const calls = mockApi({})
  renderAt('/dang-ky')
  await fillRegister(userEvent.setup(), 'khacnhau123')
  expect(await screen.findByText('Mật khẩu nhập lại không khớp')).toBeInTheDocument()
  expect(calls).not.toContain('/api/auth/register')
})

test('link đăng ký giữ next', async () => {
  mockApi({})
  renderAt('/dang-nhap?next=%2Fkhoa-hoc')
  expect(await screen.findByRole('link', { name: 'Đăng ký' })).toHaveAttribute('href', '/dang-ky?next=%2Fkhoa-hoc')
})

test('quên mật khẩu hiện thông báo chung', async () => {
  mockApi({ '/api/auth/forgot-password': () => json(202) })
  renderAt('/quen-mat-khau')
  await userEvent.type(await screen.findByLabelText('Email'), 'lan@example.com')
  await userEvent.click(screen.getByRole('button', { name: 'Gửi hướng dẫn' }))
  expect(await screen.findByText(/Nếu email tồn tại, chúng tôi đã gửi hướng dẫn/)).toBeInTheDocument()
})

test('đặt lại mật khẩu thành công về đăng nhập kèm thông báo', async () => {
  let body: unknown
  mockApi({ '/api/auth/reset-password': (b) => ((body = b), json(204)) })
  const router = renderAt('/reset-password?token=abc')
  await userEvent.type(await screen.findByLabelText('Mật khẩu mới'), 'matkhaumoi1')
  await userEvent.type(screen.getByLabelText('Nhập lại mật khẩu'), 'matkhaumoi1')
  await userEvent.click(screen.getByRole('button', { name: 'Đặt lại mật khẩu' }))
  expect(await screen.findByRole('heading', { name: 'Chào mừng trở lại!' })).toBeInTheDocument()
  expect(screen.getByRole('status')).toHaveTextContent('Đã đặt lại mật khẩu')
  expect(body).toEqual({ token: 'abc', newPassword: 'matkhaumoi1' })
  expect(router.state.location.pathname).toBe('/dang-nhap')
})

test('đặt lại mật khẩu với token hỏng hiện lỗi và link gửi lại', async () => {
  mockApi({ '/api/auth/reset-password': () => apiError(400, 'Link đã hết hạn') })
  renderAt('/reset-password?token=bad')
  await userEvent.type(await screen.findByLabelText('Mật khẩu mới'), 'matkhaumoi1')
  await userEvent.type(screen.getByLabelText('Nhập lại mật khẩu'), 'matkhaumoi1')
  await userEvent.click(screen.getByRole('button', { name: 'Đặt lại mật khẩu' }))
  expect(await screen.findByText('Link đã hết hạn')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Gửi lại link' })).toHaveAttribute('href', '/quen-mat-khau')
})

test('xác thực email thành công, chỉ gọi API một lần', async () => {
  const calls = mockApi({ '/api/auth/verify-email': () => json(204) })
  renderAt('/verify-email?token=ok')
  expect(await screen.findByRole('heading', { name: 'Đã xác thực email' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Đăng nhập' })).toHaveAttribute('href', '/dang-nhap')
  expect(calls.filter((c) => c === '/api/auth/verify-email')).toHaveLength(1)
})

test('xác thực email thất bại hiện lỗi', async () => {
  mockApi({ '/api/auth/verify-email': () => apiError(400, 'Link không hợp lệ') })
  renderAt('/verify-email?token=bad')
  expect(await screen.findByRole('heading', { name: 'Không xác thực được' })).toBeInTheDocument()
  expect(screen.getByText(/Link không hợp lệ/)).toBeInTheDocument()
})

test('chưa xác thực email: dải nhắc, gửi lại một lần rồi khóa nút', async () => {
  let sent = 0
  mockApi({
    '/api/auth/refresh': () => json(200, auth),
    '/api/me': () => json(200, { displayName: 'Lan', emailVerified: false }),
    '/api/auth/resend-verification': () => (sent++, json(204)),
  })
  renderAt('/')
  await userEvent.click(await screen.findByRole('button', { name: 'Gửi lại email' }))
  const done = await screen.findByRole('button', { name: 'Đã gửi email' })
  expect(done).toBeDisabled()
  await userEvent.click(done)
  expect(sent).toBe(1)
})

test('đã xác thực thì không có dải nhắc', async () => {
  mockApi({
    '/api/auth/refresh': () => json(200, auth),
    '/api/me': () => json(200, { displayName: 'Lan', emailVerified: true }),
  })
  renderAt('/')
  await screen.findByRole('heading', { name: 'Xin chào, Lan!' })
  expect(screen.queryByRole('button', { name: 'Gửi lại email' })).not.toBeInTheDocument()
})

test('không có client id thì ẩn nút Google', async () => {
  vi.stubEnv('VITE_GOOGLE_CLIENT_ID', '')
  mockApi({})
  renderAt('/dang-nhap')
  await screen.findByRole('heading', { name: 'Chào mừng trở lại!' })
  expect(screen.queryByTestId('google-button')).not.toBeInTheDocument()
  expect(screen.queryByText('hoặc')).not.toBeInTheDocument()
})

test('callback Google gọi /oauth/google rồi vào app', async () => {
  vi.stubEnv('VITE_GOOGLE_CLIENT_ID', 'cid.apps.googleusercontent.com')
  let cb: (r: { credential: string }) => void = () => {}
  const renderButton = vi.fn()
  window.google = { accounts: { id: { initialize: (c) => (cb = c.callback), renderButton } } }
  let body: unknown
  mockApi({
    '/api/auth/oauth/google': (b) => ((body = b), json(200, auth)),
    '/api/me': () => json(200, { displayName: 'Lan', emailVerified: true }),
  })
  renderAt('/dang-nhap')
  await waitFor(() => expect(renderButton).toHaveBeenCalled())
  cb({ credential: 'id-token' })
  expect(await screen.findByRole('heading', { name: 'Xin chào, Lan!' })).toBeInTheDocument()
  expect(body).toEqual({ idToken: 'id-token' })
})
