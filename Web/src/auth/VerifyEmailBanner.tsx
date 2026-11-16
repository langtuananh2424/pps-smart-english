import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import { ResendVerification } from './ResendVerification'

// Dải nhắc trong khung app; không chặn dùng app. Dùng chung query ['me'] với trang chủ.
export function VerifyEmailBanner() {
  const me = useQuery({ queryKey: ['me'], queryFn: () => api<{ displayName: string; emailVerified?: boolean }>('/me') })
  if (me.data?.emailVerified !== false) return null
  return (
    <div role="status" className="mb-5 flex flex-wrap items-center gap-x-4 gap-y-2 rounded-md bg-brand-soft px-4 py-3 text-sm font-bold text-brand-ink">
      <span className="min-w-0 flex-1">Xác thực email để bảo vệ tài khoản và nhận thông báo từ PPS.</span>
      <ResendVerification className="min-h-10 rounded-sm border-2 border-brand px-4 font-extrabold text-brand-strong" />
    </div>
  )
}
