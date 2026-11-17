import type { OrderStatus } from './api'

export const formatVnd = (n: number) => `${n.toLocaleString('vi-VN')} ₫`

// 180 ngày → "6 tháng", 365 ngày → "12 tháng"; số khác hiện theo ngày.
const MONTHS: Record<number, number> = { 180: 6, 365: 12 }
export const formatDays = (days: number) => (MONTHS[days] ? `${MONTHS[days]} tháng` : `${days} ngày`)

export const formatCountdown = (ms: number) => {
  const s = Math.max(0, Math.floor(ms / 1000))
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}

export const STATUS_BADGE: Record<OrderStatus, { label: string; cls: string }> = {
  PAID: { label: 'Đã thanh toán', cls: 'bg-success-bg text-success-fg' },
  PENDING: { label: 'Chờ thanh toán', cls: 'bg-reward-bg text-ink' },
  EXPIRED: { label: 'Hết hạn', cls: 'bg-subtle text-danger' },
  CANCELLED: { label: 'Đã hủy', cls: 'bg-muted text-ink-secondary' },
}
