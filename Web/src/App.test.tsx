import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { AuthProvider } from './auth/AuthContext'
import { setAccessToken } from './lib/api'
import { makeQueryClient } from './lib/queryClient'
import { routes } from './routes'

const json = (status: number, body?: unknown) =>
  new Response(body === undefined ? null : JSON.stringify(body), { status })
const auth = { accessToken: 't', expiresIn: 900, user: { id: 1, displayName: 'Lan', role: 'STUDENT' } }

function mockServer(loginResponse: () => Response) {
  vi.stubGlobal('fetch', async (url: string) => {
    if (url === '/api/auth/refresh') return json(401, { error: { code: 'X', message: 'no' } })
    if (url === '/api/auth/login') return loginResponse()
    if (url === '/api/me') return json(200, { id: 1, displayName: 'Lan' })
    return json(404)
  })
}

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>,
  )
  return router
}

async function fillAndSubmit(user: ReturnType<typeof userEvent.setup>, pw: string) {
  await user.type(await screen.findByLabelText('Email'), 'lan@example.com')
  await user.type(screen.getByLabelText('Mật khẩu'), pw)
  await user.click(screen.getByRole('button', { name: 'Đăng nhập' }))
}

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

test('chưa đăng nhập vào route bảo vệ bị chuyển về đăng nhập, giữ next', async () => {
  mockServer(() => json(200, auth))
  const router = renderAt('/bai-tap')
  expect(await screen.findByRole('heading', { name: 'Chào mừng trở lại!' })).toBeInTheDocument()
  expect(router.state.location.search).toBe('?next=%2Fbai-tap')
})

test('đăng nhập thành công vào trang chủ thấy lời chào', async () => {
  mockServer(() => json(200, auth))
  const router = renderAt('/')
  await fillAndSubmit(userEvent.setup(), 'matkhau1')
  expect(await screen.findByRole('heading', { name: 'Xin chào, Lan!' })).toBeInTheDocument()
  expect(router.state.location.pathname).toBe('/')
  // jsdom không có CSS nên cả nhãn mobile và desktop cùng hiện; tìm theo href
  const nav = screen.getByRole('navigation', { name: 'Điều hướng chính' })
  expect(nav.querySelector('a[href="/"]')).toHaveAttribute('aria-current', 'page')
})

test('sai mật khẩu hiện message từ backend', async () => {
  mockServer(() => json(401, { error: { code: 'BAD_CREDENTIALS', message: 'Sai email hoặc mật khẩu' } }))
  renderAt('/dang-nhap')
  await fillAndSubmit(userEvent.setup(), 'sai')
  expect(await screen.findByText('Sai email hoặc mật khẩu')).toBeInTheDocument()
})

test('validate form hiển thị lỗi tiếng Việt', async () => {
  mockServer(() => json(200, auth))
  const user = userEvent.setup()
  renderAt('/dang-nhap')
  await user.click(await screen.findByRole('button', { name: 'Đăng nhập' }))
  expect(await screen.findByText('Vui lòng nhập email')).toBeInTheDocument()
  expect(screen.getByText('Vui lòng nhập mật khẩu')).toBeInTheDocument()
  await user.type(screen.getByLabelText('Email'), 'abc')
  await user.click(screen.getByRole('button', { name: 'Đăng nhập' }))
  await waitFor(() => expect(screen.getByText('Email không hợp lệ')).toBeInTheDocument())
})

test('nút hiện mật khẩu đổi kiểu input', async () => {
  mockServer(() => json(200, auth))
  const user = userEvent.setup()
  renderAt('/dang-nhap')
  const pw = await screen.findByLabelText('Mật khẩu')
  expect(pw).toHaveAttribute('type', 'password')
  await user.click(screen.getByRole('button', { name: 'Hiện mật khẩu' }))
  expect(pw).toHaveAttribute('type', 'text')
})
