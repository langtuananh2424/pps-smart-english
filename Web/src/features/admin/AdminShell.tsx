import { Navigate, NavLink, Outlet, useLocation } from 'react-router'
import logo from '../../assets/logo-pps.png'
import { Icon } from '../../components/Icon'
import { inPath } from '../../layout/navItems'
import { useMyPermissions } from './api'
import { Forbidden } from './guards'
import { visibleMenu } from './menu'
import { Loading, RetryError } from './ui'

// Cùng kiểu AppShell: sidebar desktop, thanh tab dưới ở mobile; menu lọc theo quyền hiệu lực.
export function AdminShell() {
  const q = useMyPermissions()
  const { pathname } = useLocation()
  const menu = q.data ? visibleMenu(q.data) : []
  return (
    <div className="min-h-dvh bg-page md:flex md:p-4">
      <nav
        aria-label="Điều hướng quản trị"
        className="fixed inset-x-0 bottom-0 z-10 flex justify-around border-t-2 border-line bg-surface px-2 py-1 md:static md:w-28 md:shrink-0 md:flex-col md:justify-start md:gap-7 md:border-0 md:bg-transparent md:px-3 md:py-5"
      >
        <img src={logo} alt="PPS Smart English" className="hidden h-11 w-auto self-center md:block" />
        <ul className="flex flex-1 justify-around md:flex-col md:justify-start md:gap-2">
          {menu.map((item) => (
            <li key={item.label} className="flex-1 md:flex-none">
              <NavLink
                to={item.to}
                className={`flex min-h-12 flex-col items-center justify-center gap-1 rounded-[28px] px-1 py-2 text-xs font-extrabold leading-4 md:py-3 ${
                  item.prefixes.some((p) => inPath(pathname, p)) ? 'bg-brand-soft text-brand-strong' : 'text-ink-secondary hover:bg-subtle'
                }`}
              >
                <Icon src={item.icon} size={22} />
                <span>{item.label}</span>
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <main className="flex min-h-dvh flex-1 flex-col gap-5 bg-surface px-4 pb-24 pt-5 md:min-h-[calc(100dvh-2rem)] md:rounded-xl md:px-9 md:py-8">
        <Outlet />
      </main>
    </div>
  )
}

// /quan-tri chuyển tới mục menu đầu tiên có quyền.
export function AdminIndex() {
  const q = useMyPermissions()
  if (q.isPending) return <Loading />
  if (q.isError) return <RetryError error={q.error} onRetry={() => q.refetch()} />
  const first = visibleMenu(q.data)[0]
  return first ? <Navigate to={first.to} replace /> : <Forbidden />
}
