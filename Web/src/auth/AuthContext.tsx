import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, postJson, refreshSession, setAccessToken, setSessionLostHandler, type AuthResponse, type SessionUser } from '../lib/api'

interface AuthState {
  user: SessionUser | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, displayName: string) => Promise<void>
  loginWithGoogle: (idToken: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setSessionLostHandler(() => setUser(null))
    refreshSession()
      .then((a) => setUser(a.user))
      .catch(() => setUser(null))
      .finally(() => setLoading(false))
  }, [])

  // Đăng nhập, đăng ký và Google cùng trả AuthResponse.
  const start = useCallback(async (path: string, body: unknown) => {
    const a = await postJson<AuthResponse>(path, body)
    setAccessToken(a.accessToken)
    setUser(a.user)
  }, [])
  const login = useCallback((email: string, password: string) => start('/auth/login', { identifier: email, password }), [start])
  const register = useCallback(
    (email: string, password: string, displayName: string) => start('/auth/register', { email, password, displayName }),
    [start],
  )
  const loginWithGoogle = useCallback((idToken: string) => start('/auth/oauth/google', { idToken }), [start])

  const logout = useCallback(async () => {
    // Dù server lỗi vẫn xóa phiên phía client.
    await api('/auth/logout', { method: 'POST' }).catch(() => {})
    setAccessToken(null)
    setUser(null)
  }, [])

  const value = useMemo(() => ({ user, loading, login, register, loginWithGoogle, logout }), [user, loading, login, register, loginWithGoogle, logout])
  return <AuthContext value={value}>{children}</AuthContext>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth phải nằm trong AuthProvider')
  return ctx
}
