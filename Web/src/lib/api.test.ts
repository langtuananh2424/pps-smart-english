import { api, ApiError, setAccessToken, setSessionLostHandler } from './api'

const json = (status: number, body?: unknown) =>
  new Response(body === undefined ? null : JSON.stringify(body), { status })
const auth = { accessToken: 'new', expiresIn: 900, user: { id: 1, displayName: 'An', role: 'STUDENT' } }

beforeEach(() => setAccessToken('old'))
afterEach(() => vi.unstubAllGlobals())

test('401 -> refresh một lần -> thử lại thành công', async () => {
  const f = vi
    .fn()
    .mockResolvedValueOnce(json(401, { error: { code: 'UNAUTHORIZED', message: 'x' } }))
    .mockResolvedValueOnce(json(200, auth)) // refresh
    .mockResolvedValueOnce(json(200, { ok: true }))
  vi.stubGlobal('fetch', f)
  await expect(api('/me')).resolves.toEqual({ ok: true })
  expect(f.mock.calls[1][0]).toBe('/api/auth/refresh')
  expect(new Headers(f.mock.calls[2][1].headers).get('Authorization')).toBe('Bearer new')
})

test('refresh lỗi -> xóa phiên và báo mất phiên', async () => {
  const lost = vi.fn()
  setSessionLostHandler(lost)
  const f = vi
    .fn()
    .mockResolvedValueOnce(json(401, { error: { code: 'UNAUTHORIZED', message: 'x' } }))
    .mockResolvedValueOnce(json(401, { error: { code: 'TOKEN_INVALID', message: 'hết hạn' } }))
  vi.stubGlobal('fetch', f)
  await expect(api('/me')).rejects.toBeInstanceOf(ApiError)
  expect(lost).toHaveBeenCalledOnce()
  f.mockResolvedValueOnce(json(200, {}))
  await api('/x')
  expect(new Headers(f.mock.calls[2][1].headers).has('Authorization')).toBe(false)
})

test('các request 401 đồng thời dùng chung một lần refresh', async () => {
  let refreshes = 0
  vi.stubGlobal('fetch', async (url: string, init: RequestInit) => {
    if (url === '/api/auth/refresh') {
      refreshes++
      return json(200, auth)
    }
    return new Headers(init.headers).get('Authorization') === 'Bearer new' ? json(200, {}) : json(401, {})
  })
  await Promise.all([api('/a'), api('/b')])
  expect(refreshes).toBe(1)
})

test('lỗi backend thành ApiError', async () => {
  vi.stubGlobal('fetch', async () => json(402, { error: { code: 'AI_QUOTA', message: 'Hết lượt', details: { n: 1 } } }))
  await expect(api('/ai')).rejects.toMatchObject({ status: 402, code: 'AI_QUOTA', message: 'Hết lượt', details: { n: 1 } })
})
