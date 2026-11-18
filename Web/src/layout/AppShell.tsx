import { NavLink, Outlet, useLocation, useMatch } from 'react-router'
import logo from '../assets/logo-pps.png'
import { VerifyEmailBanner } from '../auth/VerifyEmailBanner'
import { Icon } from '../components/Icon'
import { inPath, navItems } from './navItems'

// Desktop: sidebar trái; mobile: thanh tab dưới cùng.
export function AppShell() {
  // Q&A gợi ý khóa (Figma mobile) không có thanh tab dưới.
  const qa = !!useMatch('/goi-y-khoa')
  const { pathname } = useLocation()
  return (
    <div className="min-h-dvh bg-page md:flex md:p-4">
      <nav
        aria-label="Điều hướng chính"
        className={`fixed inset-x-0 bottom-0 z-10 flex justify-around border-t-2 border-line bg-surface px-2 py-1 md:static md:w-28 md:shrink-0 md:flex-col md:justify-start md:gap-7 md:border-0 md:bg-transparent md:px-3 md:py-5 ${qa ? 'max-md:hidden' : ''}`}
      >
        <img src={logo} alt="PPS Smart English" className="hidden h-11 w-auto self-center md:block" />
        <ul className="flex flex-1 justify-around md:flex-col md:justify-start md:gap-2">
          {navItems.map((item) => (
            <li key={item.to} className="flex-1 md:flex-none">
              <NavLink
                to={item.to}
                end={item.to === '/'}
                className={({ isActive }) =>
                  `flex min-h-12 flex-col items-center justify-center gap-1 rounded-[28px] px-1 py-2 text-xs font-extrabold leading-4 md:py-3 ${
                    isActive || item.also?.some((b) => inPath(pathname, b)) ? 'bg-brand-soft text-brand-strong' : 'text-ink-secondary hover:bg-subtle'
                  }`
                }
              >
                <Icon src={item.icon} size={22} />
                <span className="md:hidden">{item.mobileLabel ?? item.label}</span>
                <span className="hidden md:inline">{item.label}</span>
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <main className={`min-h-dvh flex-1 bg-surface px-4 pt-6 ${qa ? 'pb-4' : 'pb-24'} md:min-h-[calc(100dvh-2rem)] md:rounded-xl md:px-9 md:py-8`}>
        <VerifyEmailBanner />
        <Outlet />
      </main>
    </div>
  )
}
