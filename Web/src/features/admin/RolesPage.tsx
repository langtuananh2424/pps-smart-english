import { Link } from 'react-router'
import { useRoles } from './api'
import { btnPrimary, headCls, Loading, PageHead, RetryError, rowCls, Section } from './ui'

// FR-ADM-04: danh sách vai trò (Figma 482:15642 / 482:15717).
export function RolesPage() {
  const q = useRoles()
  return (
    <>
      <PageHead title="Vai trò và quyền" sub="Quản lý vai trò của nhân sự." />
      <Section title="Vai trò">
        {q.isPending ? (
          <Loading />
        ) : q.isError ? (
          <RetryError error={q.error} onRetry={() => q.refetch()} />
        ) : (
          <>
            <div className={headCls}>
              <span>Vai trò</span>
              <span>Số quyền</span>
              <span>Số người</span>
            </div>
            <ul>
              {q.data.map((r) => (
                <li key={r.id} className={rowCls}>
                  <Link to={`/quan-tri/vai-tro/${r.id}`} className="min-h-11 break-words py-2.5 font-extrabold text-ink hover:text-brand-strong md:py-0">
                    {r.name} <span className="font-semibold text-ink-secondary">({r.code})</span>
                  </Link>
                  <span className="font-semibold text-ink-secondary">
                    <span className="md:hidden">Số quyền · </span>
                    {r.permissions.length}
                  </span>
                  <span className="font-semibold text-ink-secondary">
                    <span className="md:hidden">Số người · </span>
                    {r.memberCount}
                  </span>
                </li>
              ))}
            </ul>
          </>
        )}
      </Section>
      <Link to="/quan-tri/vai-tro/moi" className={`${btnPrimary} self-start`}>
        Tạo vai trò
      </Link>
    </>
  )
}
