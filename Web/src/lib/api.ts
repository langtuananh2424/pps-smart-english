// Access token chỉ giữ trong bộ nhớ; refresh token là cookie httpOnly do backend đặt.
export interface SessionUser {
  id: number
  displayName: string
  role: string
}
export interface AuthResponse {
  accessToken: string
  expiresIn: number
  user: SessionUser
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly details?: unknown,
  ) {
    super(message)
  }
}

const BASE = '/api'
let accessToken: string | null = null
let onSessionLost: () => void = () => {}

export const setAccessToken = (t: string | null) => {
  accessToken = t
}
export const setSessionLostHandler = (fn: () => void) => {
  onSessionLost = fn
}

async function toApiError(res: Response): Promise<ApiError> {
  const body = await res.json().catch(() => null)
  const e = body?.error
  return new ApiError(
    res.status,
    e?.code ?? 'UNKNOWN',
    e?.message ?? 'Có lỗi xảy ra, vui lòng thử lại.',
    e?.details,
  )
}

function send(path: string, init: RequestInit = {}) {
  const headers = new Headers(init.headers)
  if (init.body) headers.set('Content-Type', 'application/json')
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  return fetch(BASE + path, { ...init, headers, credentials: 'include' })
}

async function parse<T>(res: Response): Promise<T> {
  if (!res.ok) throw await toApiError(res)
  return (res.status === 204 ? undefined : await res.json()) as T
}

// Gộp các lần refresh đồng thời: refresh token xoay vòng, gọi hai lần sẽ làm hỏng phiên.
let refreshing: Promise<AuthResponse> | null = null
export function refreshSession(): Promise<AuthResponse> {
  refreshing ??= send('/auth/refresh', { method: 'POST' })
    .then((res) => parse<AuthResponse>(res))
    .then((auth) => {
      accessToken = auth.accessToken
      return auth
    })
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  let res = await send(path, init)
  if (res.status === 401 && !path.startsWith('/auth/')) {
    try {
      await refreshSession()
    } catch {
      accessToken = null
      onSessionLost()
      throw await toApiError(res)
    }
    res = await send(path, init)
  }
  return parse<T>(res)
}

export const postJson = <T>(path: string, body: unknown) =>
  api<T>(path, { method: 'POST', body: JSON.stringify(body) })
