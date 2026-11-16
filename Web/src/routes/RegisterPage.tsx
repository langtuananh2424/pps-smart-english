import { zodResolver } from '@hookform/resolvers/zod'
import { useCallback, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useSearchParams } from 'react-router'
import { z } from 'zod'
import arrow from '../assets/icons/arrow-right.svg'
import lock from '../assets/icons/lock.svg'
import user from '../assets/icons/user.svg'
import { useAuth } from '../auth/AuthContext'
import { AuthHeading, AuthShell, cardClass, primaryBtn, ServerError } from '../auth/AuthShell'
import { applyApiError } from '../auth/formErrors'
import { GoogleSection } from '../auth/GoogleSection'
import { withNext } from '../auth/guards'
import { confirmMatches, emailSchema, passwordSchema } from '../auth/schemas'
import { TextField } from '../auth/TextField'
import { Icon } from '../components/Icon'

const schema = z
  .object({
    displayName: z.string().trim().min(1, 'Vui lòng nhập tên hiển thị').max(100, 'Tên hiển thị tối đa 100 ký tự'),
    email: emailSchema,
    password: passwordSchema,
    confirm: z.string().min(1, 'Vui lòng nhập lại mật khẩu'),
  })
  .superRefine(confirmMatches)
type Values = z.infer<typeof schema>

export function RegisterPage() {
  const { register: signUp } = useAuth()
  const [params] = useSearchParams()
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
      await signUp(v.email, v.password, v.displayName) // PublicOnly tự chuyển trang khi có phiên
    } catch (e) {
      setServerError(applyApiError(e, setError, ['email', 'password', 'displayName']))
    }
  }
  const onGoogleError = useCallback((m: string) => setServerError(m), [])

  return (
    <AuthShell>
      <form onSubmit={handleSubmit(onSubmit)} noValidate className={cardClass}>
        <AuthHeading title="Tạo tài khoản">Bắt đầu hành trình học tiếng Anh cùng Cam.</AuthHeading>

        <TextField
          id="displayName"
          label="Tên hiển thị"
          icon={user}
          autoComplete="nickname"
          placeholder="vd: Lan Anh"
          error={errors.displayName?.message}
          {...register('displayName')}
        />
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
          autoComplete="new-password"
          placeholder="Tối thiểu 8 ký tự"
          error={errors.password?.message}
          {...register('password')}
        />
        <TextField
          id="confirm"
          label="Nhập lại mật khẩu"
          icon={lock}
          secret
          autoComplete="new-password"
          placeholder="Nhập lại mật khẩu"
          error={errors.confirm?.message}
          {...register('confirm')}
        />

        <ServerError text={serverError} />

        <button type="submit" disabled={isSubmitting} className={primaryBtn}>
          {isSubmitting ? 'Đang tạo tài khoản...' : 'Đăng ký'}
          {!isSubmitting && <Icon src={arrow} />}
        </button>

        <GoogleSection onError={onGoogleError} />

        <p className="text-center text-[15px] font-bold text-ink-secondary">
          Đã có tài khoản?{' '}
          <Link to={withNext('/dang-nhap', params)} className="font-black text-brand-strong">Đăng nhập</Link>
        </p>
      </form>
    </AuthShell>
  )
}
