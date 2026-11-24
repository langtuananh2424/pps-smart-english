import { useState } from 'react'
import { Link } from 'react-router'
import { useRoles, useStaff } from './api'
import { useDebounced } from './useDebounced'
import { headCls, inputCls, labelCls, Loading, LoadMore, PageHead, RetryError, rowCls, Section } from './ui'

// FR-ADM-04: danh sách nhân sự (Figma 482:16497, chỉ tab Nhân sự); lọc tên/email + vai trò, tải thêm theo cursor.
export function StaffPage() {
  const [q, setQ] = useState('')
  const [role, setRole] = useState('')
  const roles = useRoles()
  const list = useStaff({ q: useDebounced(q.trim()), role })
  const rows = list.data?.pages.flatMap((p) => p.data) ?? []
  return (
    <>
      <PageHead title="Quản lý nhân sự" sub="Tìm nhân sự và chỉnh quyền riêng của từng người." />
      <Section title="Bộ lọc">
        <div className="grid gap-3 md:grid-cols-2">
          <div className="flex flex-col gap-2">
            <label htmlFor="q" className={labelCls}>
              Tên hoặc email
            </label>
            <input id="q" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Nhập để tìm" className={inputCls} />
          </div>
          <div className="flex flex-col gap-2">
            <label htmlFor="role" className={labelCls}>
              Vai trò
            </label>
            <select id="role" value={role} onChange={(e) => setRole(e.target.value)} className={inputCls}>
              <option value="">Tất cả vai trò</option>
              {roles.data?.map((r) => (
                <option key={r.id} value={r.code}>
                  {r.name}
                </option>
              ))}
            </select>
          </div>
        </div>
      </Section>
      <Section title="Nhân sự">
        {list.isPending ? (
          <Loading />
        ) : list.isError ? (
          <RetryError error={list.error} onRetry={() => list.refetch()} />
        ) : rows.length === 0 ? (
          <p className="font-semibold text-ink-secondary">Không có nhân sự phù hợp.</p>
        ) : (
          <>
            <div className={headCls}>
              <span>Nhân sự</span>
              <span>Vai trò</span>
              <span>Phân quyền</span>
            </div>
            <ul>
              {rows.map((u) => (
                <li key={u.id} className={rowCls}>
                  <span className="min-w-0 break-words font-extrabold text-ink">
                    {u.displayName}
                    <span className="block text-sm font-semibold text-ink-secondary">{u.email}</span>
                  </span>
                  <span className="font-semibold text-ink-secondary">{u.roles.join(', ')}</span>
                  <Link to={`/quan-tri/nhan-su/${u.id}`} aria-label={`Phân quyền ${u.displayName}`} className="min-h-11 py-2.5 font-extrabold text-brand-strong underline md:py-0">
                    Phân quyền
                  </Link>
                </li>
              ))}
            </ul>
          </>
        )}
      </Section>
      {list.hasNextPage && <LoadMore onClick={() => list.fetchNextPage()} busy={list.isFetchingNextPage} />}
    </>
  )
}
