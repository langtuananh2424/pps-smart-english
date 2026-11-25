import { Link } from 'react-router'
import { NETWORK_ERROR } from '../../../auth/formErrors'
import { ApiError } from '../../../lib/api'
import { Pill } from '../../checkout/ui'
import type { Status } from './api'

export const CMS = '/quan-tri/cms'
export const courseUrl = (id: number | string) => `${CMS}/khoa-hoc/${id}`

export const StatusPill = ({ status }: { status: Status }) =>
  status === 'PUBLISHED' ? <Pill className="bg-success-bg text-success-fg">Đã xuất bản</Pill> : <Pill className="bg-muted text-ink-secondary">Bản nháp</Pill>

export const Crumb = ({ to, children }: { to: string; children: string }) => (
  <Link to={to} className="inline-flex min-h-11 items-center self-start font-extrabold text-brand-strong underline">
    {children}
  </Link>
)

// 401/403 báo không có quyền; còn lại lấy thông điệp máy chủ (400/409) hoặc lỗi mạng.
export const NO_PERMISSION = 'Bạn không có quyền thực hiện thao tác này.'
export const writeMessage = (e: unknown) => (e instanceof ApiError ? (e.status === 401 || e.status === 403 ? NO_PERMISSION : e.message) : NETWORK_ERROR)

// Nút dạng viên thuốc theo Figma CMS (cao 48, chữ 14 đậm).
const pill = 'inline-flex h-12 items-center justify-center rounded-full px-5 text-sm font-bold disabled:opacity-60'
export const pillPrimary = `${pill} bg-brand-strong text-white`
export const pillOutline = `${pill} border border-line-brand bg-surface text-brand-strong`
