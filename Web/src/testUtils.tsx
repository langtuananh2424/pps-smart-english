import { QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { AuthProvider } from './auth/AuthContext'
import { makeQueryClient } from './lib/queryClient'
import { routes } from './routes'

export const json = (status: number, body?: unknown) =>
  new Response(body === undefined ? null : JSON.stringify(body), { status })
export const apiError = (status: number, message: string, details?: unknown) =>
  json(status, { error: { code: 'X', message, details } })
export const auth = { accessToken: 't', expiresIn: 900, user: { id: 1, displayName: 'Lan', role: 'STUDENT' } }

type Handlers = Record<string, (body: unknown) => Response>

// Giả backend theo đường dẫn; mặc định chưa có phiên (refresh 401). Trả mảng lời gọi để test đếm.
export function mockApi(handlers: Handlers) {
  const calls: string[] = []
  vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
    calls.push(url)
    if (handlers[url]) return handlers[url](init?.body ? JSON.parse(String(init.body)) : undefined)
    if (url === '/api/auth/refresh') return apiError(401, 'no')
    return json(404)
  })
  return calls
}

export function renderAt(path: string) {
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
