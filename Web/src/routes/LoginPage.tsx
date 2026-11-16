import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import cam from '../assets/cam-chao.png'
import arrow from '../assets/icons/arrow-right.svg'
import chat from '../assets/icons/chat.svg'
import eye from '../assets/icons/eye.svg'
import lock from '../assets/icons/lock.svg'
import sparkleLg from '../assets/icons/sparkle-lg.svg'
import sparkleSm from '../assets/icons/sparkle-sm.svg'
import star from '../assets/icons/star.svg'
import user from '../assets/icons/user.svg'
import logo from '../assets/logo-pps.png'
import { useAuth } from '../auth/AuthContext'
import { Icon } from '../components/Icon'
import { ApiError } from '../lib/api'

const schema = z.object({
  email: z.string().trim().min(1, 'Vui lòng nhập email').email('Email không hợp lệ'),
  password: z.string().min(1, 'Vui lòng nhập mật khẩu'),
})
type Values = z.infer<typeof schema>

const field = 'flex min-h-[52px] items-center gap-2.5 rounded-md border-2 bg-input px-4 focus-within:border-brand'
const bare = 'min-w-0 flex-1 bg-transparent px-0.5 py-px font-bold text-ink outline-none placeholder:text-[#757575]'
const border = (bad: boolean) => (bad ? 'border-danger' : 'border-line-input')

export function LoginPage() {
  const { login } = useAuth()
  const [showPw, setShowPw] = useState(false)
  const [serverError, setServerError] = useState('')
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema) })

  const onSubmit = async (v: Values) => {
    setServerError('')
    try {
      await login(v.email, v.password) // PublicOnly tự chuyển trang khi có phiên
    } catch (e) {
      setServerError(e instanceof ApiError ? e.message : 'Không kết nối được máy chủ, vui lòng thử lại.')
    }
  }

  return (
    <div className="min-h-dvh bg-page lg:flex lg:items-center lg:justify-center lg:gap-24 lg:px-14">
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
          Bài giảng video, bài tập ngữ cảnh và Trợ lý AI chấm bài viết, tất cả trong một nơi.
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

      <MobileHero />

      <div className="mx-auto w-full max-w-[460px] px-3 pb-3 lg:mx-0 lg:p-0">
        <form
          onSubmit={handleSubmit(onSubmit)}
          noValidate
          className="flex flex-col gap-[22px] rounded-lg border-2 border-line bg-surface px-[18px] py-[30px] shadow-edge-card sm:p-10 lg:rounded-xl"
        >
          <div className="text-center">
            <h1 className="text-[30px] font-black leading-[38px] text-ink">Chào mừng trở lại!</h1>
            <p className="mt-1 font-semibold text-ink-secondary lg:mt-1.5">
              <span className="lg:hidden">Đăng nhập để tiếp tục lộ trình học.</span>
              <span className="hidden lg:inline">Đăng nhập để tiếp tục lộ trình học của bạn.</span>
            </p>
          </div>

          <div className="flex flex-col gap-2">
            <label htmlFor="email" className="text-sm font-extrabold">Email</label>
            <div className={`${field} ${border(!!errors.email)}`}>
              <Icon src={user} className="text-ink-muted" />
              <input
                id="email"
                type="email"
                autoComplete="email"
                placeholder="vd: ban@example.com"
                aria-invalid={!!errors.email}
                aria-describedby={errors.email ? 'email-err' : undefined}
                className={bare}
                {...register('email')}
              />
            </div>
            {errors.email && <FieldError id="email-err" text={errors.email.message} />}
          </div>

          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <label htmlFor="password" className="text-sm font-extrabold">Mật khẩu</label>
              <Link to="/quen-mat-khau" className="text-sm font-extrabold text-brand-strong">Quên mật khẩu?</Link>
            </div>
            <div className={`${field} pr-1.5 ${border(!!errors.password)}`}>
              <Icon src={lock} className="text-ink-muted" />
              <input
                id="password"
                type={showPw ? 'text' : 'password'}
                autoComplete="current-password"
                placeholder="Nhập mật khẩu"
                aria-invalid={!!errors.password}
                aria-describedby={errors.password ? 'password-err' : undefined}
                className={bare}
                {...register('password')}
              />
              <button
                type="button"
                aria-label={showPw ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                aria-pressed={showPw}
                onClick={() => setShowPw((s) => !s)}
                className="grid size-11 place-items-center rounded-sm text-ink-muted hover:bg-subtle"
              >
                <Icon src={eye} />
              </button>
            </div>
            {errors.password && <FieldError id="password-err" text={errors.password.message} />}
          </div>

          {serverError && (
            <p role="alert" className="rounded-sm bg-danger-bg px-4 py-3 text-sm font-bold text-danger-edge">
              {serverError}
            </p>
          )}

          <button
            type="submit"
            disabled={isSubmitting}
            className="flex min-h-14 items-center justify-center gap-2 rounded-md bg-brand text-lg font-black text-white shadow-edge-primary disabled:opacity-60"
          >
            {isSubmitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
            {!isSubmitting && <Icon src={arrow} />}
          </button>

          <p className="text-center text-[15px] font-bold text-ink-secondary">
            Chưa có tài khoản?{' '}
            <Link to="/dang-ky" className="font-black text-brand-strong">Đăng ký</Link>
          </p>
        </form>
      </div>
    </div>
  )
}

const FieldError = ({ id, text }: { id: string; text?: string }) => (
  <p id={id} role="alert" className="text-sm font-bold text-danger">{text}</p>
)

const Chip = ({ className, icon, text }: { className: string; icon: string; text: string }) => (
  <div className={`absolute flex items-center gap-2 rounded-md border-2 border-line bg-surface px-4 py-2.5 shadow-edge-card ${className}`}>
    <img src={icon} alt="" className="size-[18px]" />
    <span className="whitespace-nowrap font-black text-brand-ink">{text}</span>
  </div>
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
