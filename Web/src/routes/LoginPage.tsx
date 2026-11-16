import { zodResolver } from '@hookform/resolvers/zod'
import { useCallback, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useSearchParams } from 'react-router'
import { z } from 'zod'
import arrow from '../assets/icons/arrow-right.svg'
import lock from '../assets/icons/lock.svg'
import user from '../assets/icons/user.svg'
import { useAuth } from '../auth/AuthContext'
import { AuthHeading, AuthShell, cardClass, Notice, primaryBtn, ServerError } from '../auth/AuthShell'
import { applyApiError } from '../auth/formErrors'
import { GoogleSection } from '../auth/GoogleSection'
import { withNext } from '../auth/guards'
import { emailSchema } from '../auth/schemas'
import { TextField } from '../auth/TextField'
import { Icon } from '../components/Icon'

const schema = z.object({
  email: emailSchema,
  password: z.string().min(1, 'Vui lòng nhập mật khẩu'),
})
type Values = z.infer<typeof schema>

export function LoginPage() {
  const { login } = useAuth()
  const [params] = useSearchParams()
  const notice = (useLocation().state as { notice?: string } | null)?.notice
  const [serverError, setServerError] = useState('')
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema) })

  const onSubmit = async (v: Values) => {
    setServerError('')
    try {
      await login(v.email, v.password) // PublicOnly tự chuyển trang khi có phiên
    } catch (e) {
      setServerError(applyApiError(e, setError, ['email', 'password']))
    }
  }
  const onGoogleError = useCallback((m: string) => setServerError(m), [])

  return (
    <AuthShell>
      <form onSubmit={handleSubmit(onSubmit)} noValidate className={cardClass}>
        <AuthHeading title="Chào mừng trở lại!">
          <span className="lg:hidden">Đăng nhập để tiếp tục lộ trình học.</span>
          <span className="hidden lg:inline">Đăng nhập để tiếp tục lộ trình học của bạn.</span>
        </AuthHeading>

        {notice && <Notice text={notice} />}

        <TextField
          id="email"
          label="Email"
          icon={user}
          type="email"
          autoComplete="email"
          placeholder="vd: ban@example.com"
          error={errors.email?.message}
          {...register('email')}
        />
        <TextField
          id="password"
          label="Mật khẩu"
          icon={lock}
          secret
          autoComplete="current-password"
          placeholder="Nhập mật khẩu"
          error={errors.password?.message}
          aside={<Link to="/quen-mat-khau" className="text-sm font-extrabold text-brand-strong">Quên mật khẩu?</Link>}
          {...register('password')}
        />

        <ServerError text={serverError} />

        <button type="submit" disabled={isSubmitting} className={primaryBtn}>
          {isSubmitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
          {!isSubmitting && <Icon src={arrow} />}
        </button>

        <GoogleSection onError={onGoogleError} />

        <p className="text-center text-[15px] font-bold text-ink-secondary">
          Chưa có tài khoản?{' '}
          <Link to={withNext('/dang-ky', params)} className="font-black text-brand-strong">Đăng ký</Link>
        </p>
      </form>
    </AuthShell>
  )
}
