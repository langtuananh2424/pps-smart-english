import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, postJson, refreshSession, setAccessToken, setSessionLostHandler, type AuthResponse, type SessionUser } from '../lib/api'

interface AuthState {
  user: SessionUser | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
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

  const login = useCallback(async (email: string, password: string) => {
    const a = await postJson<AuthResponse>('/auth/login', { identifier: email, password })
    setAccessToken(a.accessToken)
    setUser(a.user)
  }, [])

  const logout = useCallback(async () => {
    // Dù server lỗi vẫn xóa phiên phía client.
    await api('/auth/logout', { method: 'POST' }).catch(() => {})
    setAccessToken(null)
    setUser(null)
  }, [])

  const value = useMemo(() => ({ user, loading, login, logout }), [user, loading, login, logout])
  return <AuthContext value={value}>{children}</AuthContext>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth phải nằm trong AuthProvider')
  return ctx
}
