import type { MyCourse } from '../home/types'

// Quyền của người xem với một khóa: khách không gọi API /me; TRIAL coi như chưa mua.
// UNKNOWN: đã đăng nhập nhưng không tải được danh sách khóa của mình, không đoán là chưa mua.
export type Access = 'GUEST' | 'NONE' | 'ACTIVE' | 'EXPIRED' | 'UNKNOWN'

export const EXPIRING_DAYS = 30

export function accessOf(loggedIn: boolean, mine: MyCourse | undefined): Access {
  if (!loggedIn) return 'GUEST'
  if (mine?.status === 'ACTIVE') return 'ACTIVE'
  if (mine?.status === 'EXPIRED') return 'EXPIRED'
  return 'NONE'
}

// Còn ≤30 ngày (kể cả đã quá hạn) thì được gia hạn; không có ngày hết hạn thì không bao giờ sắp hết.
export function isExpiring(expiresAt: string | null, now = Date.now()) {
  if (!expiresAt) return false
  return (new Date(expiresAt).getTime() - now) / 86_400_000 <= EXPIRING_DAYS
}

export const formatDate = (iso: string) => new Date(iso).toLocaleDateString('vi-VN')
