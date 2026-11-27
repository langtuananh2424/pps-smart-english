import type { ReactNode } from 'react'
import cam from '../assets/cam-chao.png'
import chat from '../assets/icons/chat.svg'
import sparkleLg from '../assets/icons/sparkle-lg.svg'
import sparkleSm from '../assets/icons/sparkle-sm.svg'
import star from '../assets/icons/star.svg'
import logo from '../assets/logo-pps.png'

// Khung dùng chung cho mọi màn auth: hero (desktop 2 cột / mobile phía trên) + cột thẻ form.
export const cardClass =
  'flex flex-col gap-[22px] rounded-lg border-2 border-line bg-surface px-[18px] py-[30px] shadow-edge-card sm:p-10 lg:rounded-xl'
export const primaryBtn =
  'flex min-h-14 items-center justify-center gap-2 rounded-md bg-brand text-lg font-black text-white shadow-edge-primary disabled:opacity-60'

export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-dvh bg-page lg:flex lg:items-center lg:justify-center lg:gap-24 lg:px-14">
      <DesktopHero />
      <MobileHero />
      <div className="mx-auto w-full max-w-[460px] px-3 pb-3 lg:mx-0 lg:p-0">{children}</div>
    </div>
  )
}

export const AuthHeading = ({ title, children }: { title: string; children?: ReactNode }) => (
  <div className="text-center">
    <h1 className="text-[30px] font-black leading-[38px] text-ink">{title}</h1>
    {children && <p className="mt-1 font-semibold text-ink-secondary lg:mt-1.5">{children}</p>}
  </div>
)

export const FieldError = ({ id, text }: { id: string; text?: string }) => (
  <p id={id} role="alert" className="text-sm font-bold text-danger">{text}</p>
)

export const ServerError = ({ text }: { text: string }) =>
  text ? (
    <p role="alert" className="rounded-sm bg-danger-bg px-4 py-3 text-sm font-bold text-danger-edge">{text}</p>
  ) : null

export const Notice = ({ text }: { text: string }) => (
  <p role="status" className="rounded-sm bg-brand-soft px-4 py-3 text-sm font-bold text-brand-ink">{text}</p>
)

const Chip = ({ className, icon, text }: { className: string; icon: string; text: string }) => (
  <div className={`absolute flex items-center gap-2 rounded-md border-2 border-line bg-surface px-4 py-2.5 shadow-edge-card ${className}`}>
    <img src={icon} alt="" className="size-[18px]" />
    <span className="whitespace-nowrap font-black text-brand-ink">{text}</span>
  </div>
)

const DesktopHero = () => (
  <section className="hidden max-w-[600px] flex-col gap-5 lg:flex">
    <img src={logo} alt="PPS Smart English" className="h-11 w-auto self-start" />
    <span className="self-start rounded-full border-2 border-line bg-surface px-3.5 py-1.5 text-sm font-extrabold text-brand-ink">
      Trung tâm Anh ngữ PPS
    </span>
    <h2 className="text-[46px] font-black leading-[54px] text-ink">
      Học tiếng Anh mỗi ngày,
      <br />
      <span className="text-brand">tiến bộ cùng Cam!</span>
    </h2>
    <p className="max-w-[480px] text-lg font-semibold leading-7 text-ink-secondary">
      Bài giảng video, bài tập ngữ cảnh và Trợ lý Cam chấm bài viết, tất cả trong một nơi.
    </p>
    <div className="relative h-[420px] w-[600px]" aria-hidden="true">
      <div className="absolute left-[110px] top-5 size-[380px] rounded-full bg-[radial-gradient(circle,rgba(247,103,7,0.3),rgba(255,138,61,0)_70%)]" />
      <div className="absolute left-[172px] top-[82px] size-64 rounded-full bg-gradient-to-br from-white to-[#ffe8d4]" />
      <img src={cam} alt="" className="absolute left-[140px] top-[50px] size-80" />
      <Chip className="left-2.5 top-[280px]" icon={star} text="Mỗi ngày một bài nhỏ thôi" />
      <Chip className="left-[280px] top-[354px]" icon={chat} text="Rồi bạn sẽ tự tin nói tiếng Anh!" />
      <img src={sparkleLg} alt="" className="absolute left-[424px] top-[30px] size-[26px]" />
      <img src={sparkleSm} alt="" className="absolute left-[150px] top-[200px] size-4" />
    </div>
  </section>
)

// Hero mobile (<lg): logo + tiêu đề bên trái, mascot bên phải.
const MobileHero = () => (
  <section className="flex h-[236px] gap-1 px-5 pt-[22px] lg:hidden">
    <div className="flex flex-1 flex-col gap-[22px]">
      <div className="flex items-center gap-2">
        <img src={logo} alt="PPS Smart English" className="h-9 w-auto" />
        <span aria-hidden="true" className="pt-[9px] text-sm font-black uppercase leading-[14.7px] tracking-[0.42px] text-brand">
          Smart<br />English
        </span>
      </div>
      <h2 className="whitespace-nowrap text-2xl font-black leading-[30px] text-ink">
        Học mỗi ngày,
        <br />
        <span className="text-brand">tiến bộ cùng Cam!</span>
      </h2>
    </div>
    <div className="relative h-[200px] w-[158px] shrink-0" aria-hidden="true">
      <div className="absolute -left-2.5 top-1.5 size-[190px] rounded-full bg-[radial-gradient(circle,rgba(247,103,7,0.28),rgba(255,138,61,0)_70%)]" />
      <div className="absolute left-[22px] top-10 size-[126px] rounded-full bg-gradient-to-br from-white to-[#ffe8d4]" />
      <img src={cam} alt="" className="absolute -left-2.5 top-2.5 size-[190px] object-contain" />
    </div>
  </section>
)
