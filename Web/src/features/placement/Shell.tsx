import type { ReactNode } from 'react'
import { Link } from 'react-router'
import logo from '../../assets/logo-pps.png'
import { useAuth } from '../../auth/AuthContext'

// Khung chung cho các màn ngoài lúc làm bài (giới thiệu, chờ đăng nhập, kết quả); khách không vào được trang chủ.
export function Shell({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  return (
    <div className="min-h-dvh bg-page px-4 pb-10">
      <header className="mx-auto flex h-[68px] max-w-[900px] items-center">
        <Link to={user ? '/' : '/khoa-hoc'} aria-label="PPS Smart English">
          <img src={logo} alt="PPS Smart English" className="h-[26px] w-auto md:h-[29px]" />
        </Link>
      </header>
      <main className="mx-auto max-w-[900px]">{children}</main>
    </div>
  )
}

export const panelCard = 'mx-auto flex max-w-lg flex-col items-center gap-4 rounded-lg border border-line-subtle bg-surface p-6 text-center shadow-edge-card sm:p-8'
