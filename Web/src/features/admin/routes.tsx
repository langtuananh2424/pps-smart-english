import { NavLink, Outlet, type RouteObject } from 'react-router'
import { PlaceholderPage } from '../../routes/PlaceholderPage'
import { AdminIndex, AdminShell } from './AdminShell'
import { AuditPage } from './AuditPage'
import { OrderDetailPage } from './OrderDetailPage'
import { OrdersPage } from './OrdersPage'
import { CmsHome } from './cms/CmsHome'
import { bankRoutes } from './cms/questions/routes'
import { examRoutes } from './cms/exams/routes'
import { importRoutes } from './cms/import/routes'
import { cmsRoutes } from './cms/routes'
import { RequirePermission } from './guards'
import { STAFF_TABS } from './menu'
import { useMyPermissions } from './api'
import { RoleCreatePage } from './RoleCreatePage'
import { RoleDetailPage } from './RoleDetailPage'
import { RolesPage } from './RolesPage'
import { StaffAccessPage } from './StaffAccessPage'
import { StaffPage } from './StaffPage'
import { StudentProfilePage } from './StudentProfilePage'
import { StudentsPage } from './StudentsPage'
import { TeachingReportPage } from './TeachingReportPage'

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
    guarded('order.view', [
      { path: 'don-hang', element: <OrdersPage /> },
      { path: 'don-hang/:id', element: <OrderDetailPage /> },
    ]),
    guarded('student.view', [
      { path: 'hoc-vien', element: <StudentsPage /> },
      { path: 'hoc-vien/:id', element: <StudentProfilePage /> },
    ]),
    // Báo cáo số buổi dạy (#62, D-176).
    guarded('report.teaching', [{ path: 'buoi-day', element: <TeachingReportPage /> }]),
    guarded('cms.content', [{ path: 'cms', element: <CmsHome /> }]),
    // Khóa học, bài giảng, học tuần tự (#17, #63): /quan-tri/cms/khoa-hoc.
    guarded('cms.content', cmsRoutes),
    // Ngân hàng câu hỏi, nhập hàng loạt, đề thi (#18, #70, #24): path con của /quan-tri/cms.
    guarded('cms.content', [{ path: 'cms', children: [...bankRoutes, ...importRoutes, ...examRoutes] }]),
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
