import { NavLink, Outlet, useLocation } from 'react-router'
import logo from '../../assets/logo-pps.png'
import exercises from '../../assets/icons/nav-exercises.svg'
import user from '../../assets/icons/user.svg'
import { Icon } from '../../components/Icon'
import { base } from './format'

// Cùng kiểu AdminShell: sidebar desktop, thanh tab dưới ở mobile. "Chấm bài" đi từ tiến độ nộp bài nên không có mục riêng.
export function TeachingShell() {
  const { pathname } = useLocation()
  const given = pathname.includes('/bai-giao')
  const items = [
    { to: base, label: 'Lớp của tôi', icon: user, active: !given },
    { to: `${base}/bai-giao`, label: 'Bài đã giao', icon: exercises, active: given },
  ]
  return (
    <div className="min-h-dvh bg-page md:flex md:p-4">
      <nav
        aria-label="Điều hướng giảng dạy"
        className="fixed inset-x-0 bottom-0 z-10 flex justify-around border-t-2 border-line bg-surface px-2 py-1 md:static md:w-28 md:shrink-0 md:flex-col md:justify-start md:gap-7 md:border-0 md:bg-transparent md:px-3 md:py-5"
      >
        <img src={logo} alt="PPS Smart English" className="hidden h-11 w-auto self-center md:block" />
        <ul className="flex flex-1 justify-around md:flex-col md:justify-start md:gap-2">
          {items.map((i) => (
            <li key={i.to} className="flex-1 md:flex-none">
              <NavLink
                to={i.to}
                end
                className={`flex min-h-12 flex-col items-center justify-center gap-1 rounded-[28px] px-1 py-2 text-xs font-extrabold leading-4 md:py-3 ${i.active ? 'bg-brand-soft text-brand-strong' : 'text-ink-secondary hover:bg-subtle'}`}
              >
                <Icon src={i.icon} size={22} />
                <span>{i.label}</span>
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
