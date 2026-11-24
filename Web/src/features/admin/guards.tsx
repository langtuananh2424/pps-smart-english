import { Outlet } from 'react-router'
import { useMyPermissions } from './api'
import { hasAny } from './menu'
import { Loading, PageHead, RetryError } from './ui'

export const Forbidden = () => <PageHead title="Không có quyền truy cập" sub="Tài khoản của bạn chưa được cấp quyền xem trang này." />

// Thiếu quyền thì hiện trang 403 tại chỗ (không chuyển hướng); đang tải quyền thì chờ.
export function RequirePermission({ any }: { any: string[] }) {
  const q = useMyPermissions()
  if (q.isPending) return <Loading />
  if (q.isError) return <RetryError error={q.error} onRetry={() => q.refetch()} />
  return hasAny(q.data, any) ? <Outlet /> : <Forbidden />
}
