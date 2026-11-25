import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../lib/api'

export interface PermissionDef {
  code: string
  module: string
  description: string
}
export interface Role {
  id: number
  code: string
  name: string
  permissions: string[]
  memberCount: number
}
export interface StaffUser {
  id: number
  email: string
  displayName: string
  roles: string[]
}
export interface Override {
  permission: string
  effect: 'GRANT' | 'REVOKE'
  expiresAt: string | null
  grantedBy: number
}
export interface UserAccess {
  userId: number
  roles: string[]
  overrides: Override[]
  effective: string[]
}
export interface AuditEntry {
  id: number
  actorId: number
  actorEmail: string | null
  actorName: string | null
  action: string
  targetType: string
  targetId: string
  before: unknown
  after: unknown
  reason: string | null
  createdAt: string
}
export interface Page<T> {
  data: T[]
  nextCursor: string | null
}

export const ME_PERMISSIONS_KEY = ['me', 'permissions'] as const

// FR-ADM-04: quyền hiệu lực của chính người đăng nhập (menu và route guard cùng đọc).
export const useMyPermissions = () =>
  useQuery({
    queryKey: ME_PERMISSIONS_KEY,
    queryFn: () => api<{ permissions: string[] }>('/me/permissions'),
    select: (d) => new Set(d.permissions),
  })

export const usePermissionCatalog = () =>
  useQuery({ queryKey: ['admin', 'permissions'], queryFn: () => api<PermissionDef[]>('/admin/permissions') })

export const useRoles = () => useQuery({ queryKey: ['admin', 'roles'], queryFn: () => api<Role[]>('/admin/roles') })

export const useUserAccess = (id: string) =>
  useQuery({ queryKey: ['admin', 'access', id], queryFn: () => api<UserAccess>(`/admin/users/${id}/access`) })

const query = (params: Record<string, string>, cursor: string) => {
  const p = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) if (v) p.set(k, v)
  if (cursor) p.set('cursor', cursor)
  return p.toString()
}

export function useCursorList<T>(key: string, path: string, params: Record<string, string>, limit: number, enabled = true) {
  return useInfiniteQuery({
    queryKey: ['admin', key, params],
    queryFn: ({ pageParam }) => api<Page<T>>(`${path}?${query({ ...params, limit: String(limit) }, pageParam)}`),
    initialPageParam: '',
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    enabled,
  })
}

export const useStaff = (params: { q: string; role: string }) => useCursorList<StaffUser>('users', '/admin/users', params, 20)

export const useStaffOptions = (enabled: boolean) => useCursorList<StaffUser>('user-options', '/admin/users', {}, 100, enabled)

export type AuditFilters = { actorId: string; targetType: string; targetId: string; from: string; to: string }
export const useAuditLogs = (filters: AuditFilters) => useCursorList<AuditEntry>('audit', '/admin/audit-logs', filters, 50)

// Header HTTP không chứa được tiếng Việt thô nên lý do luôn được mã hóa phần trăm.
const write = <T>(method: string, path: string, reason: string, body?: unknown) =>
  api<T>(path, {
    method,
    headers: { 'X-Audit-Reason': encodeURIComponent(reason) },
    body: body === undefined ? undefined : JSON.stringify(body),
  })

export const rbacWrites = {
  createRole: (body: { code: string; name: string; permissions: string[] }, reason: string) => write<Role>('POST', '/admin/roles', reason, body),
  setRolePermissions: (id: number, permissions: string[], reason: string) =>
    write<Role>('PUT', `/admin/roles/${id}/permissions`, reason, { permissions }),
  assignRole: (userId: number, roleCode: string, reason: string) => write<UserAccess>('POST', `/admin/users/${userId}/roles`, reason, { roleCode }),
  removeRole: (userId: number, roleCode: string, reason: string) => write<UserAccess>('DELETE', `/admin/users/${userId}/roles/${roleCode}`, reason),
  setOverride: (userId: number, permission: string, effect: 'GRANT' | 'REVOKE', expiresAt: string | null, reason: string) =>
    write<UserAccess>('PUT', `/admin/users/${userId}/permissions/${permission}`, reason, { effect, expiresAt }),
  removeOverride: (userId: number, permission: string, reason: string) =>
    write<UserAccess>('DELETE', `/admin/users/${userId}/permissions/${permission}`, reason),
}

// Mọi lệnh ghi RBAC đi qua đây: xong thì làm mới dữ liệu quản trị và quyền của chính mình (có thể vừa tự đổi quyền).
export function useRbacWrite<T = unknown>() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ reason, call }: { reason: string; call: (reason: string) => Promise<T> }) => call(reason),
    onSuccess: () => Promise.all([qc.invalidateQueries({ queryKey: ['admin'] }), qc.invalidateQueries({ queryKey: ME_PERMISSIONS_KEY })]),
  })
}
