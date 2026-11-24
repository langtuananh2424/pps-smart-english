import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useParams } from 'react-router'
import { rbacWrites, useRbacWrite, useRoles, type Role } from './api'
import { PermissionMatrix, toggled } from './PermissionMatrix'
import { btnPrimary, isLastAdmin, LastAdminBlock, Loading, PageHead, ReasonSection, reasonSchema, RetryError, Section, WriteError } from './ui'

// FR-ADM-04: chi tiết vai trò; chưa có API sửa tên nên chỉ đổi tập quyền.
export function RoleDetailPage() {
  const { id } = useParams()
  const q = useRoles()
  if (q.isPending) return <Loading />
  if (q.isError) return <RetryError error={q.error} onRetry={() => q.refetch()} />
  const role = q.data.find((r) => String(r.id) === id)
  if (!role) return <PageHead title="Không tìm thấy vai trò" sub="Vai trò này không tồn tại hoặc đã bị xóa." />
  return <RoleEditor key={role.id} role={role} />
}

function RoleEditor({ role }: { role: Role }) {
  const [selected, setSelected] = useState(() => new Set(role.permissions))
  const write = useRbacWrite()
  const form = useForm({ resolver: zodResolver(reasonSchema), defaultValues: { reason: '' } })

  const submit = form.handleSubmit(({ reason }) => {
    write.mutate({ reason, call: (r) => rbacWrites.setRolePermissions(role.id, [...selected], r) }, { onSuccess: () => form.reset() })
  })

  if (isLastAdmin(write.error)) return <LastAdminBlock onBack={write.reset} />
  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      <PageHead title="Chi tiết vai trò" sub="Bật quyền theo module. Mô tả giải thích phạm vi của từng quyền." />
      <Section title="Thông tin vai trò">
        <p className="font-extrabold text-ink">
          {role.name} <span className="font-semibold text-ink-secondary">({role.code})</span>
        </p>
        <p className="font-semibold text-ink-secondary">{role.memberCount} nhân sự đang giữ vai trò này.</p>
      </Section>
      <PermissionMatrix selected={selected} onToggle={(c) => setSelected((s) => toggled(s, c))} />
      <ReasonSection reg={form.register('reason')} error={form.formState.errors.reason} />
      {write.isError && <WriteError error={write.error} onRetry={() => void submit()} />}
      {write.isSuccess && (
        <p role="status" className="font-bold text-success-fg">
          Đã lưu vai trò.
        </p>
      )}
      <button type="submit" disabled={write.isPending} className={`${btnPrimary} self-start`}>
        Lưu vai trò
      </button>
    </form>
  )
}
