import { QueryClientProvider } from '@tanstack/react-query'
import { render, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { AuthProvider } from '../auth/AuthContext'
import { setAccessToken } from '../lib/api'
import { makeQueryClient } from '../lib/queryClient'
import { routes } from '.'

const json = (status: number, body?: unknown) => new Response(body === undefined ? null : JSON.stringify(body), { status })
const auth = { accessToken: 't', expiresIn: 900, user: { id: 1, displayName: 'Lan', role: 'STUDENT' } }

beforeEach(() => setAccessToken(null))
afterEach(() => vi.unstubAllGlobals())

test('link cũ /tro-ly-ai chuyển sang /tro-ly-cam (đã đăng nhập), không còn chữ AI trên màn', async () => {
  vi.stubGlobal('fetch', async (url: string) => {
    if (url === '/api/auth/refresh') return json(200, auth)
    if (url === '/api/me') return json(200, { id: 1, displayName: 'Lan' })
    return json(404)
  })
  const router = createMemoryRouter(routes, { initialEntries: ['/tro-ly-ai'] })
  const { container } = render(
    <QueryClientProvider client={makeQueryClient()}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>,
  )
  await waitFor(() => expect(router.state.location.pathname).toBe('/tro-ly-cam'))
  await waitFor(() => expect(container.textContent).toContain('Trợ lý Cam'))
  expect(container.textContent).not.toMatch(/\bAI\b/)
})
