import { Navigate, Outlet, useLocation, useSearchParams } from 'react-router'
import { useAuth } from './AuthContext'

// Chỉ nhận đường dẫn nội bộ, tránh open redirect.
export const safeNext = (next: string | null) => (next && next.startsWith('/') && !next.startsWith('//') ? next : '/')

export function FullScreenLoading() {
  return (
    <div role="status" className="grid min-h-dvh place-items-center bg-page text-ink-secondary font-bold">
      Đang tải...
    </div>
  )
}

export function RequireAuth() {
  const { user, loading } = useAuth()
  const loc = useLocation()
  if (loading) return <FullScreenLoading />
  if (!user) return <Navigate to={`/dang-nhap?next=${encodeURIComponent(loc.pathname + loc.search)}`} replace />
  return <Outlet />
}

export function PublicOnly() {
  const { user, loading } = useAuth()
  const [params] = useSearchParams()
  if (loading) return <FullScreenLoading />
  if (user) return <Navigate to={safeNext(params.get('next'))} replace />
  return <Outlet />
}
