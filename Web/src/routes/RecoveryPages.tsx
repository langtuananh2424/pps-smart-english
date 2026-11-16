import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { z } from 'zod'
import arrow from '../assets/icons/arrow-right.svg'
import lock from '../assets/icons/lock.svg'
import user from '../assets/icons/user.svg'
import { useAuth } from '../auth/AuthContext'
import { AuthHeading, AuthShell, cardClass, primaryBtn, ServerError } from '../auth/AuthShell'
import { NETWORK_ERROR } from '../auth/formErrors'
import { ResendVerification } from '../auth/ResendVerification'
import { confirmMatches, emailSchema, passwordSchema } from '../auth/schemas'
import { TextField } from '../auth/TextField'
import { Icon } from '../components/Icon'
import { ApiError, postJson } from '../lib/api'

const linkBtn = `${primaryBtn} px-6 text-center`
const msgOf = (e: unknown) => (e instanceof ApiError ? e.message : NETWORK_ERROR)

// ---- Quên mật khẩu ----
const forgotSchema = z.object({ email: emailSchema })

export function ForgotPasswordPage() {
  const [sent, setSent] = useState(false)
  const [serverError, setServerError] = useState('')
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof forgotSchema>>({ resolver: zodResolver(forgotSchema) })

  const onSubmit = async (v: z.infer<typeof forgotSchema>) => {
    setServerError('')
    try {
      await postJson('/auth/forgot-password', v)
      setSent(true) // backend luôn trả thành công chung, không lộ email có tồn tại không
    } catch (e) {
      setServerError(msgOf(e))
    }
  }

  if (sent)
    return (
      <AuthShell>
        <div className={cardClass}>
          <AuthHeading title="Kiểm tra email của bạn">
            Nếu email tồn tại, chúng tôi đã gửi hướng dẫn đặt lại mật khẩu. Link có hiệu lực trong thời gian ngắn.
          </AuthHeading>
          <Link to="/dang-nhap" className={linkBtn}>Về trang đăng nhập</Link>
        </div>
      </AuthShell>
    )

  return (
    <AuthShell>
      <form onSubmit={handleSubmit(onSubmit)} noValidate className={cardClass}>
        <AuthHeading title="Quên mật khẩu?">Nhập email đăng ký, chúng tôi sẽ gửi link đặt lại mật khẩu.</AuthHeading>
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
        <ServerError text={serverError} />
        <button type="submit" disabled={isSubmitting} className={primaryBtn}>
          {isSubmitting ? 'Đang gửi...' : 'Gửi hướng dẫn'}
          {!isSubmitting && <Icon src={arrow} />}
        </button>
        <p className="text-center text-[15px] font-bold text-ink-secondary">
          <Link to="/dang-nhap" className="font-black text-brand-strong">Quay lại đăng nhập</Link>
        </p>
      </form>
    </AuthShell>
  )
}

// ---- Đặt lại mật khẩu (link trong email: /reset-password?token=...) ----
const resetSchema = z.object({ password: passwordSchema, confirm: z.string().min(1, 'Vui lòng nhập lại mật khẩu') }).superRefine(confirmMatches)
type ResetValues = z.infer<typeof resetSchema>

export function ResetPasswordPage() {
  const token = useSearchParams()[0].get('token') ?? ''
  const navigate = useNavigate()
  const [failure, setFailure] = useState(token ? '' : 'Link đặt lại mật khẩu không hợp lệ.')
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ResetValues>({ resolver: zodResolver(resetSchema) })

  const onSubmit = async (v: ResetValues) => {
    try {
      await postJson('/auth/reset-password', { token, newPassword: v.password })
      navigate('/dang-nhap', { replace: true, state: { notice: 'Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới.' } })
    } catch (e) {
      setFailure(msgOf(e))
    }
  }

  if (failure)
    return (
      <AuthShell>
        <div className={cardClass}>
          <AuthHeading title="Không đặt lại được mật khẩu">{failure}</AuthHeading>
          <Link to="/quen-mat-khau" className={linkBtn}>Gửi lại link</Link>
        </div>
      </AuthShell>
    )

  return (
    <AuthShell>
      <form onSubmit={handleSubmit(onSubmit)} noValidate className={cardClass}>
        <AuthHeading title="Đặt lại mật khẩu">Nhập mật khẩu mới cho tài khoản của bạn.</AuthHeading>
        <TextField
          id="password"
          label="Mật khẩu mới"
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
        <button type="submit" disabled={isSubmitting} className={primaryBtn}>
          {isSubmitting ? 'Đang lưu...' : 'Đặt lại mật khẩu'}
          {!isSubmitting && <Icon src={arrow} />}
        </button>
      </form>
    </AuthShell>
  )
}

// ---- Xác thực email (link trong email: /verify-email?token=...) ----
export function VerifyEmailPage() {
  const token = useSearchParams()[0].get('token') ?? ''
  const { user } = useAuth()
  const qc = useQueryClient()
  // useQuery khử trùng lặp (StrictMode) và không tự gọi lại: token chỉ dùng được một lần.
  const verify = useQuery({
    queryKey: ['verify-email', token],
    enabled: !!token,
    retry: false,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    queryFn: async () => {
      await postJson('/auth/verify-email', { token })
      qc.invalidateQueries({ queryKey: ['me'] }) // tắt dải nhắc xác thực
      return true
    },
  })

  let body
  if (verify.isSuccess)
    body = (
      <>
        <AuthHeading title="Đã xác thực email">Tài khoản của bạn đã được xác thực.</AuthHeading>
        <Link to={user ? '/' : '/dang-nhap'} className={linkBtn}>{user ? 'Vào trang chủ' : 'Đăng nhập'}</Link>
      </>
    )
  else if (verify.isError || !token)
    body = (
      <>
        <AuthHeading title="Không xác thực được">
          {verify.isError ? msgOf(verify.error) : 'Link xác thực không hợp lệ.'} Link có thể đã hết hạn hoặc đã được dùng.
        </AuthHeading>
        {user ? (
          <div className="flex flex-col items-center gap-2">
            <ResendVerification className="min-h-14 w-full rounded-md bg-brand text-lg font-black text-white shadow-edge-primary" />
          </div>
        ) : (
          <Link to="/dang-nhap" className={linkBtn}>Đăng nhập để gửi lại email</Link>
        )}
      </>
    )
  else
    body = (
      <div role="status">
        <AuthHeading title="Đang xác thực email..." />
      </div>
    )

  return (
    <AuthShell>
      <div className={cardClass}>{body}</div>
    </AuthShell>
  )
}
