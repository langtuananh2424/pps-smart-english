import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useParams } from 'react-router'
import { rbacWrites, useRbacWrite, usePermissionCatalog, useRoles, useUserAccess, type UserAccess } from './api'
import { btnOutline, inputCls, isLastAdmin, LastAdminBlock, labelCls, Loading, PageHead, ReasonSection, reasonSchema, RetryError, Section, WriteError } from './ui'

const fmtExpiry = (iso: string | null) => (iso ? new Date(iso).toLocaleDateString('vi-VN') : 'Không hết hạn')
// Hết hạn cuối ngày theo giờ máy người dùng.
const endOfDay = (date: string) => (date ? new Date(`${date}T23:59:59`).toISOString() : null)

// FR-ADM-04: quyền theo nhân sự (Figma 482:16011): role, ngoại lệ GRANT/REVOKE có hạn, quyền hiệu lực. Một lý do dùng cho thao tác kế tiếp.
export function StaffAccessPage() {
  const { id = '' } = useParams()
  const q = useUserAccess(id)
  if (q.isPending) return <Loading />
  if (q.isError) return <RetryError error={q.error} onRetry={() => q.refetch()} />
  return <AccessEditor access={q.data} />
}

function AccessEditor({ access }: { access: UserAccess }) {
  const roles = useRoles()
  const catalog = usePermissionCatalog()
  const write = useRbacWrite()
  const form = useForm({ resolver: zodResolver(reasonSchema), defaultValues: { reason: '' } })
  const [roleCode, setRoleCode] = useState('')
  const [permission, setPermission] = useState('')
  const [effect, setEffect] = useState<'GRANT' | 'REVOKE'>('GRANT')
  const [date, setDate] = useState('')

  // Mọi thao tác đều qua kiểm tra lý do bắt buộc trước khi gửi.
  const act = (call: (reason: string) => Promise<unknown>) => () =>
    void form.handleSubmit(({ reason }) => write.mutate({ reason, call }, { onSuccess: () => form.reset() }))()

  const userId = access.userId
  const assignable = roles.data?.filter((r) => !access.roles.includes(r.code)) ?? []
  const roleName = (code: string) => roles.data?.find((r) => r.code === code)?.name ?? code

  if (isLastAdmin(write.error)) return <LastAdminBlock onBack={write.reset} />
  return (
    <div className="flex flex-col gap-4">
      <PageHead title="Quyền của nhân sự" sub="Quyền hiệu lực được tính từ vai trò và các ngoại lệ cá nhân." />
      <Section title="Vai trò được gán">
        {access.roles.length === 0 && <p className="font-semibold text-ink-secondary">Chưa có vai trò nào.</p>}
        <ul>
          {access.roles.map((code) => (
            <li key={code} className="flex items-center justify-between gap-3 border-b border-line-brand py-1.5 last:border-b-0">
              <span className="font-extrabold text-ink">{roleName(code)}</span>
              <button type="button" onClick={act((r) => rbacWrites.removeRole(userId, code, r))} aria-label={`Gỡ vai trò ${roleName(code)}`} className={btnOutline}>
                Gỡ
              </button>
            </li>
          ))}
        </ul>
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex min-w-48 flex-1 flex-col gap-2">
            <label htmlFor="assign-role" className={labelCls}>
              Gán thêm vai trò
            </label>
            <select id="assign-role" value={roleCode} onChange={(e) => setRoleCode(e.target.value)} className={inputCls}>
              <option value="">Chọn vai trò</option>
              {assignable.map((r) => (
                <option key={r.id} value={r.code}>
                  {r.name}
                </option>
              ))}
            </select>
          </div>
          <button type="button" disabled={!roleCode} onClick={act((r) => rbacWrites.assignRole(userId, roleCode, r))} className={btnOutline}>
            Gán vai trò
          </button>
        </div>
      </Section>

      <Section title="Ngoại lệ">
        {access.overrides.length === 0 ? (
          <p className="font-semibold text-ink-secondary">Chưa có ngoại lệ.</p>
        ) : (
          <ul>
            {access.overrides.map((o) => (
              <li key={o.permission} className="grid gap-1 border-b border-line-brand py-2 last:border-b-0 md:grid-cols-4 md:items-center md:gap-4">
                <span className="break-all font-extrabold text-ink">{o.permission}</span>
                <span className="font-semibold text-ink-secondary">{o.effect}</span>
                <span className="font-semibold text-ink-secondary">{fmtExpiry(o.expiresAt)}</span>
                <button type="button" onClick={act((r) => rbacWrites.removeOverride(userId, o.permission, r))} aria-label={`Gỡ ngoại lệ ${o.permission}`} className={`${btnOutline} md:justify-self-end`}>
                  Gỡ
                </button>
              </li>
            ))}
          </ul>
        )}
      </Section>

      <Section title="Chỉnh ngoại lệ">
        <div className="grid gap-3 md:grid-cols-3">
          <div className="flex flex-col gap-2">
            <label htmlFor="ov-permission" className={labelCls}>
              Quyền
            </label>
            <select id="ov-permission" value={permission} onChange={(e) => setPermission(e.target.value)} className={inputCls}>
              <option value="">Chọn quyền</option>
              {catalog.data?.map((p) => (
                <option key={p.code} value={p.code}>
                  {p.code}
                </option>
              ))}
            </select>
          </div>
          <div className="flex flex-col gap-2">
            <label htmlFor="ov-effect" className={labelCls}>
              Hiệu lực
            </label>
            <select id="ov-effect" value={effect} onChange={(e) => setEffect(e.target.value as 'GRANT' | 'REVOKE')} className={inputCls}>
              <option value="GRANT">GRANT</option>
              <option value="REVOKE">REVOKE</option>
            </select>
          </div>
          <div className="flex flex-col gap-2">
            <label htmlFor="ov-date" className={labelCls}>
              Ngày hết hạn (tùy chọn)
            </label>
            <input id="ov-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} className={inputCls} />
          </div>
        </div>
        <button type="button" disabled={!permission} onClick={act((r) => rbacWrites.setOverride(userId, permission, effect, endOfDay(date), r))} className={`${btnOutline} self-start`}>
          Đặt ngoại lệ
        </button>
      </Section>

      <Section title="Quyền hiệu lực" tone="brand">
        <p className="text-sm font-semibold text-ink-secondary">Hợp quyền từ vai trò, áp dụng REVOKE và GRANT theo ngoại lệ còn hiệu lực.</p>
        {access.effective.length === 0 ? (
          <p className="font-semibold text-ink-secondary">Không có quyền nào.</p>
        ) : (
          <ul className="flex flex-wrap gap-2">
            {access.effective.map((p) => (
              <li key={p} className="rounded-full bg-surface px-3 py-1 text-sm font-extrabold text-ink">
                {p}
              </li>
            ))}
          </ul>
        )}
      </Section>

      <ReasonSection reg={form.register('reason')} error={form.formState.errors.reason} />
      {write.isError && <WriteError error={write.error} />}
      {write.isSuccess && (
        <p role="status" className="font-bold text-success-fg">
          Đã lưu quyền.
        </p>
      )}
      <p className="text-sm font-semibold text-ink-secondary">Nhập lý do rồi bấm thao tác muốn thực hiện ở trên; lý do được ghi cho thao tác đó.</p>
    </div>
  )
}
