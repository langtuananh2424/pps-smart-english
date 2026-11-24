import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { z } from 'zod'
import { applyApiError } from '../../auth/formErrors'
import { rbacWrites, useRbacWrite, type Role } from './api'
import { PermissionMatrix, toggled } from './PermissionMatrix'
import { btnPrimary, FieldText, isLastAdmin, LastAdminBlock, PageHead, ReasonSection, reasonSchema, Section } from './ui'

const schema = reasonSchema.extend({
  code: z.string().regex(/^[A-Z][A-Z_]{1,49}$/, 'Mã dạng IN_HOA_GACH_DUOI, 2 đến 50 ký tự'),
  name: z.string().trim().min(1, 'Vui lòng nhập tên vai trò').max(100, 'Tên tối đa 100 ký tự'),
})
type Values = z.infer<typeof schema>

// FR-ADM-04: tạo vai trò mới; 409 role_taken hiện tại ô mã.
export function RoleCreatePage() {
  const navigate = useNavigate()
  const [selected, setSelected] = useState(new Set<string>())
  const [formError, setFormError] = useState('')
  const write = useRbacWrite<Role>()
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { code: '', name: '', reason: '' } })
  const { errors } = form.formState

  const submit = form.handleSubmit(async ({ code, name, reason }) => {
    setFormError('')
    try {
      const role = await write.mutateAsync({ reason, call: (r) => rbacWrites.createRole({ code, name, permissions: [...selected] }, r) })
      navigate(`/quan-tri/vai-tro/${role.id}`)
    } catch (e) {
      if (isLastAdmin(e)) return
      const msg = applyApiError(e, form.setError, ['code', 'name'])
      if (msg) setFormError(msg)
    }
  })

  if (isLastAdmin(write.error)) return <LastAdminBlock onBack={write.reset} />
  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      <PageHead title="Tạo vai trò" sub="Đặt mã, tên và bật quyền theo module." />
      <Section title="Thông tin vai trò">
        <FieldText id="code" label="Mã vai trò" reg={form.register('code')} error={errors.code} placeholder="VD: SUPPORT_LEAD" />
        <FieldText id="name" label="Tên vai trò" reg={form.register('name')} error={errors.name} placeholder="Nhập tên vai trò" />
      </Section>
      <PermissionMatrix selected={selected} onToggle={(c) => setSelected((s) => toggled(s, c))} />
      <ReasonSection reg={form.register('reason')} error={errors.reason} />
      {formError && (
        <p role="alert" className="font-bold text-danger-fg">
          {formError}
        </p>
      )}
      <button type="submit" disabled={form.formState.isSubmitting} className={`${btnPrimary} self-start`}>
        Tạo vai trò
      </button>
    </form>
  )
}
