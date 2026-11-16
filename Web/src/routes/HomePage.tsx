import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import { api } from '../lib/api'

export function HomePage() {
  const { user } = useAuth()
  const me = useQuery({ queryKey: ['me'], queryFn: () => api<{ displayName: string }>('/me') })
  const name = me.data?.displayName ?? user?.displayName ?? ''
  return (
    <>
      <header>
        <h1 className="text-2xl font-black leading-[30px] text-ink">Xin chào, {name}!</h1>
        <p className="font-semibold text-ink-secondary">Cùng chinh phục tiếng Anh mỗi ngày nhé.</p>
      </header>
      <p className="mt-10 rounded-lg border-2 border-dashed border-line-dashed bg-page p-8 text-center font-bold text-ink-secondary">
        Chưa có nội dung học hôm nay.
      </p>
    </>
  )
}
