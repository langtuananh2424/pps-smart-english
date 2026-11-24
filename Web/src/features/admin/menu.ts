import cms from '../../assets/icons/nav-exercises.svg'
import products from '../../assets/icons/nav-courses.svg'
import overview from '../../assets/icons/nav-home.svg'
import links from '../../assets/icons/play.svg'
import staff from '../../assets/icons/nav-profile.svg'

export interface AdminTab {
  to: string
  label: string
  perm: string
}
export interface AdminItem {
  label: string
  icon: string
  to: string
  any: string[]
  tabs?: AdminTab[]
}

export const STAFF_TABS: AdminTab[] = [
  { to: '/quan-tri/vai-tro', label: 'Vai trò', perm: 'rbac.manage' },
  { to: '/quan-tri/nhan-su', label: 'Nhân sự', perm: 'rbac.manage' },
  { to: '/quan-tri/nhat-ky', label: 'Nhật ký', perm: 'audit.view' },
]

// Một chỗ duy nhất khai báo menu và quyền cần có.
export const adminMenu: AdminItem[] = [
  { label: 'Tổng quan', icon: overview, to: '/quan-tri/tong-quan', any: ['report.view'] },
  { label: 'Gói bán', icon: products, to: '/quan-tri/goi-ban', any: ['product.manage'] },
  { label: 'Link kích hoạt', icon: links, to: '/quan-tri/link-kich-hoat', any: ['activation.manage'] },
  { label: 'CMS', icon: cms, to: '/quan-tri/cms', any: ['cms.content'] },
  { label: 'Nhân sự', icon: staff, to: '/quan-tri/nhan-su', any: STAFF_TABS.map((t) => t.perm), tabs: STAFF_TABS },
]

export const hasAny = (perms: Set<string>, any: string[]) => any.some((p) => perms.has(p))

// Mục có tab thì dẫn vào tab đầu tiên người dùng có quyền.
export const visibleMenu = (perms: Set<string>) =>
  adminMenu.filter((i) => hasAny(perms, i.any)).map((i) => ({ ...i, to: i.tabs?.find((t) => perms.has(t.perm))?.to ?? i.to, prefixes: i.tabs?.map((t) => t.to) ?? [i.to] }))
