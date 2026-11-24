import { NavLink, Outlet, type RouteObject } from 'react-router'
import { PlaceholderPage } from '../../routes/PlaceholderPage'
import { AdminIndex, AdminShell } from './AdminShell'
import { AuditPage } from './AuditPage'
import { RequirePermission } from './guards'
import { STAFF_TABS } from './menu'
import { useMyPermissions } from './api'
import { RoleCreatePage } from './RoleCreatePage'
import { RoleDetailPage } from './RoleDetailPage'
import { RolesPage } from './RolesPage'
import { StaffAccessPage } from './StaffAccessPage'
import { StaffPage } from './StaffPage'

// Tab của nhóm Nhân sự, chỉ hiện tab người dùng có quyền.
function StaffTabs() {
  const perms = useMyPermissions().data
  return (
    <>
      <nav aria-label="Nhân sự" className="flex flex-wrap gap-2">
        {STAFF_TABS.filter((t) => perms?.has(t.perm)).map((t) => (
          <NavLink
            key={t.to}
            to={t.to}
            className={({ isActive }) =>
              `inline-flex min-h-11 items-center rounded-md border px-3.5 font-extrabold ${isActive ? 'border-brand bg-subtle text-ink' : 'border-line-brand bg-surface text-ink-secondary'}`
            }
          >
            {t.label}
          </NavLink>
        ))}
      </nav>
      <Outlet />
    </>
  )
}

const guarded = (perm: string, children: RouteObject[]): RouteObject => ({ element: <RequirePermission any={[perm]} />, children })
const todo = (perm: string, path: string, title: string) => guarded(perm, [{ path, element: <PlaceholderPage title={title} /> }])

// FR-ADM-04/05: khung quản trị; mỗi route kiểm quyền độc lập nên gõ thẳng URL cũng bị chặn.
export const adminRoutes: RouteObject = {
  path: '/quan-tri',
  element: <AdminShell />,
  children: [
    { index: true, element: <AdminIndex /> },
    todo('report.view', 'tong-quan', 'Tổng quan'),
    todo('product.manage', 'goi-ban', 'Gói bán'),
    todo('activation.manage', 'link-kich-hoat', 'Link kích hoạt'),
    todo('cms.content', 'cms', 'CMS'),
    {
      element: <RequirePermission any={STAFF_TABS.map((t) => t.perm)} />,
      children: [
        {
          element: <StaffTabs />,
          children: [
            guarded('rbac.manage', [
              { path: 'vai-tro', element: <RolesPage /> },
              { path: 'vai-tro/moi', element: <RoleCreatePage /> },
              { path: 'vai-tro/:id', element: <RoleDetailPage /> },
              { path: 'nhan-su', element: <StaffPage /> },
              { path: 'nhan-su/:id', element: <StaffAccessPage /> },
            ]),
            guarded('audit.view', [{ path: 'nhat-ky', element: <AuditPage /> }]),
          ],
        },
      ],
    },
  ],
}
