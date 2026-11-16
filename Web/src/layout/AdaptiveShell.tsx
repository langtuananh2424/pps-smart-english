import { Link, Outlet, useLocation } from 'react-router'
import logo from '../assets/logo-pps.png'
import { useAuth } from '../auth/AuthContext'
import { FullScreenLoading } from '../auth/guards'
import { AppShell } from './AppShell'

// Màn công khai (US-201): học viên thấy trong AppShell, khách thấy khung gọn.
export function AdaptiveShell() {
  const { user, loading } = useAuth()
  const loc = useLocation()
  if (loading) return <FullScreenLoading />
  if (user) return <AppShell />
  const next = encodeURIComponent(loc.pathname + loc.search)
  return (
    <div className="min-h-dvh bg-page">
      <header className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3 md:px-9">
        <Link to="/khoa-hoc">
          <img src={logo} alt="PPS Smart English" className="h-10 w-auto" />
        </Link>
        <nav aria-label="Tài khoản" className="flex items-center gap-2">
          <Link to={`/dang-nhap?next=${next}`} className="flex min-h-11 items-center rounded-md border-2 border-line-input bg-surface px-4 font-extrabold text-ink">
            Đăng nhập
          </Link>
          <Link to={`/dang-ky?next=${next}`} className="flex min-h-11 items-center rounded-md bg-brand px-4 font-extrabold text-white shadow-edge-primary">
            Đăng ký
          </Link>
        </nav>
      </header>
      <main className="mx-auto max-w-6xl bg-surface px-4 pb-16 pt-6 md:rounded-xl md:px-9 md:py-8">
        <Outlet />
      </main>
    </div>
  )
}
