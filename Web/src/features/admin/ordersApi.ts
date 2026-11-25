import { useQuery } from '@tanstack/react-query'
import { api, ApiError, rawFetch, refreshSession } from '../../lib/api'
import type { OrderStatus } from '../checkout/api'
import { useCursorList } from './api'

export interface AdminOrder {
  id: number
  code: string
  status: OrderStatus
  totalVnd: number
  paidAt: string | null
  createdAt: string
  email: string
  displayName: string | null
  provider: string | null
}
export interface AdminOrderDetail {
  id: number
  code: string
  status: OrderStatus
  totalVnd: number
  provider: string | null
  createdAt: string
  expiresAt: string
  paidAt: string | null
  buyer: { id: number; email: string; displayName: string | null }
  items: { productName: string; productType: 'LEVEL' | 'AI_ADDON'; courseTitle: string | null; priceVnd: number }[]
  transactions: { receivedAt: string; provider: string; providerRef: string; amountVnd: number; status: string }[]
}
export type OrderFilters = { status: string; from: string; to: string; email: string }

// FR-ADM-03, D-38: quyền order.view.
export const useAdminOrders = (f: OrderFilters) => useCursorList<AdminOrder>('orders', '/admin/orders', f, 20)

export const useAdminOrder = (id: string) =>
  useQuery({ queryKey: ['admin', 'order', id], queryFn: () => api<AdminOrderDetail>(`/admin/orders/${id}`), retry: false })

// Tải CSV có Bearer: link <a> thường không gửi được header. Lỗi 4xx dựng lại ApiError từ JSON của backend.
export async function exportOrdersCsv(from: string, to: string): Promise<{ blob: Blob; filename: string }> {
  const path = `/admin/orders/export.csv?from=${from}&to=${to}`
  let res = await rawFetch(path)
  if (res.status === 401) {
    await refreshSession()
    res = await rawFetch(path)
  }
  if (!res.ok) {
    const e = (await res.json().catch(() => null))?.error
    throw new ApiError(res.status, e?.code ?? 'UNKNOWN', e?.message ?? 'Có lỗi xảy ra, vui lòng thử lại.')
  }
  return { blob: await res.blob(), filename: `orders_${from}_${to}.csv` }
}
